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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/** On-device OCR. Each full-resolution tile is mapped back into image-normalized geometry. */
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

    private data class PixelWord(
        val text: String, val l: Int, val t: Int, val r: Int, val b: Int
    )

    private fun recognizeBlocking(context: Context, uris: List<Uri>): OcrOutput {
        val language = OcrLanguagePreferences.get(context)
        if (language != OcrLanguage.ENGLISH)
            return TesseractEngine.recognize(context, uris, language)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val lines = mutableListOf<OcrLine>()
            val words = mutableListOf<OcrWord>()
            var width = 0
            var baseY = 0

            for ((pageIndex, uri) in uris.withIndex()) {
                val pageWords = mutableListOf<PixelWord>()
                var pageHeight = 0
                val pageWidth = ImageTiler.forEachTile(context, uri) { tile ->
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(tile.bitmap, 0)))
                    for (block in result.textBlocks) for (line in block.lines) {
                        val box = line.boundingBox ?: continue
                        val centre = tile.top + (box.top + box.bottom) / 2
                        if (centre < tile.ownLo || centre >= tile.ownHi) continue
                        lines += OcrLine(
                            line.text, box.left, baseY + tile.top + box.top,
                            box.right, baseY + tile.top + box.bottom
                        )
                        for (element in line.elements) {
                            val bounds = element.boundingBox ?: continue
                            val mid = tile.top + (bounds.top + bounds.bottom) / 2
                            if (mid in tile.ownLo until tile.ownHi) {
                                pageWords += PixelWord(
                                    element.text, bounds.left, tile.top + bounds.top,
                                    bounds.right, tile.top + bounds.bottom
                                )
                            }
                        }
                    }
                    pageHeight = maxOf(pageHeight, tile.top + tile.bitmap.height)
                }
                if (width == 0) width = pageWidth
                if (pageWidth > 0 && pageHeight > 0) {
                    words += pageWords.mapNotNull { w ->
                        val word = OcrWord(
                            w.text,
                            (w.l.toFloat() / pageWidth).coerceIn(0f, 1f),
                            (w.t.toFloat() / pageHeight).coerceIn(0f, 1f),
                            (w.r.toFloat() / pageWidth).coerceIn(0f, 1f),
                            (w.b.toFloat() / pageHeight).coerceIn(0f, 1f),
                            pageIndex
                        )
                        word.takeIf { it.valid }
                    }
                }
                baseY += pageHeight + 40
            }
            return OcrOutput(lines.sortedWith(compareBy({ it.top }, { it.left })), width, words)
        } finally {
            recognizer.close()
        }
    }
}
