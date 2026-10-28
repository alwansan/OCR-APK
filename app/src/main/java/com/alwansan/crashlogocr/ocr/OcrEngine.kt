package com.alwansan.crashlogocr.ocr

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.alwansan.crashlogocr.OcrLine
import com.alwansan.crashlogocr.OcrOutput
import com.alwansan.crashlogocr.OcrWord
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * OCR is never run on the UI thread. English uses two bounded ML Kit readings
 * per tile when text is light-on-dark or extremely small; both readings have
 * the SAME dimensions, so geometry stays aligned with the original screenshot.
 */
object OcrEngine {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun recognize(context: Context, uris: List<Uri>, callback: (Result<OcrOutput>) -> Unit) {
        val app = context.applicationContext
        require(uris.size <= 12) { "Select up to 12 images" }
        worker.execute {
            val result = runCatching { recognizeBlocking(app, uris) }
            main.post { callback(result) }
        }
    }

    private fun candidates(text: Text): List<OcrPassFusion.Line> {
        return text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
            val bounds = line.boundingBox ?: return@mapNotNull null
            val elements = line.elements.mapNotNull elementLoop@{ element ->
                val b = element.boundingBox ?: return@elementLoop null
                OcrPassFusion.Word(element.text, b.left, b.top, b.right, b.bottom)
            }
            OcrPassFusion.Line(line.text, bounds.left, bounds.top, bounds.right, bounds.bottom,
                elements, line.confidence)
        }
    }

    private fun recognizeBlocking(context: Context, uris: List<Uri>): OcrOutput {
        val language = OcrLanguagePreferences.get(context)
        if (language == OcrLanguage.ARABIC)
            return TesseractEngine.recognize(context, uris, language)
        val latin = recognizeLatin(context, uris)
        // Default bilingual mode on Arabic phones should still recognize Java
        // crashes with ML Kit. Preserve Tesseract for ordinary Arabic documents.
        if (language == OcrLanguage.BILINGUAL && !OcrScriptRouter.isJavaTrace(latin.rawText))
            return TesseractEngine.recognize(context, uris, language)
        return latin
    }

    private fun recognizeLatin(context: Context, uris: List<Uri>): OcrOutput {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val lineChecker = OcrTessLineCheck(context)
        val accuracy = OcrAccuracyPreference.get(context)
        try {
            val lines = mutableListOf<OcrLine>()
            val words = mutableListOf<OcrWord>()
            var imageWidth = 0
            var baseY = 0

            for ((pageIndex, uri) in uris.withIndex()) {
                val pageWords = mutableListOf<OcrPassFusion.Word>()
                val pageLines = mutableListOf<OcrPassFusion.Line>()
                var pageHeight = 0
                val width = ImageTiler.forEachTile(context, uri) { tile ->
                    val initial = candidates(
                        Tasks.await(recognizer.process(InputImage.fromBitmap(tile.bitmap, 0)))
                    )
                    val dark = OcrContrastPass.isDark(tile.bitmap)
                    // The alternative pass is useful for screenshots on dark backgrounds and
                    // for thin text. Never decode a second full image into RAM.
                    val merged = if (dark || tile.bitmap.width < 850) {
                        val enhanced = OcrContrastPass.create(tile.bitmap, dark)
                        try {
                            val other = candidates(
                                Tasks.await(recognizer.process(InputImage.fromBitmap(enhanced, 0)))
                            )
                            OcrPassFusion.fuse(initial, other)
                        } finally {
                            enhanced.recycle()
                        }
                    } else initial
                    // A different OCR engine is only consulted for a capped number
                    // of suspicious Java frames. It cannot silently rewrite every line.
                    val verified = lineChecker.refine(
                        tile.bitmap, merged, dark, accuracy.maxVerifyPerTile
                    )

                    for (line in verified) {
                        val middle = tile.top + (line.top + line.bottom) / 2
                        if (middle < tile.ownLo || middle >= tile.ownHi) continue
                        lines += OcrLine(
                            line.text, line.left, baseY + tile.top + line.top,
                            line.right, baseY + tile.top + line.bottom
                        )
                        // Full-line hitbox: users can select an entire recognized stacktrace
                        // line even if the engine fails to provide all Element hitboxes.
                        pageLines += line.copy(
                            top = tile.top + line.top,
                            bottom = tile.top + line.bottom
                        )
                        val known = mutableListOf<LineBoxRecovery.Item>()
                        for (element in line.words) {
                            val wordMiddle = tile.top + (element.top + element.bottom) / 2
                            if (wordMiddle !in tile.ownLo until tile.ownHi) continue
                            pageWords += element.copy(
                                top = tile.top + element.top,
                                bottom = tile.top + element.bottom
                            )
                            known += LineBoxRecovery.Item(
                                element.text, element.left, element.right
                            )
                        }
                        for (gap in LineBoxRecovery.recover(
                            line.text, line.left, line.right, known
                        )) pageWords += OcrPassFusion.Word(
                            gap.text, gap.left, tile.top + line.top,
                            gap.right, tile.top + line.bottom
                        )
                    }
                    pageHeight = maxOf(pageHeight, tile.top + tile.bitmap.height)
                }
                if (imageWidth == 0) imageWidth = width
                if (width > 0 && pageHeight > 0) {
                    fun normalized(text: String, l: Int, t: Int, r: Int, b: Int, wholeLine: Boolean): OcrWord? {
                        val output = OcrWord(
                            text,
                            (l.toFloat() / width).coerceIn(0f, 1f),
                            (t.toFloat() / pageHeight).coerceIn(0f, 1f),
                            (r.toFloat() / width).coerceIn(0f, 1f),
                            (b.toFloat() / pageHeight).coerceIn(0f, 1f),
                            pageIndex, isLine = wholeLine
                        )
                        return output.takeIf { it.valid }
                    }
                    words += pageWords.mapNotNull {
                        normalized(it.text, it.left, it.top, it.right, it.bottom, false)
                    }
                    words += pageLines.mapNotNull {
                        normalized(it.text, it.left, it.top, it.right, it.bottom, true)
                    }
                }
                baseY += pageHeight + 40
            }
            return OcrOutput(lines.sortedWith(compareBy({ it.top }, { it.left })), imageWidth, words)
        } finally {
            lineChecker.close()
            recognizer.close()
        }
    }
}
