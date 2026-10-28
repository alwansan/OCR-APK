package com.alwansan.crashlogocr.ocr

import android.content.Context
import android.net.Uri
import android.text.Html
import com.alwansan.crashlogocr.OcrLine
import com.alwansan.crashlogocr.OcrOutput
import com.alwansan.crashlogocr.OcrWord
import com.googlecode.tesseract.android.TessBaseAPI

/**
 * Arabic and mixed-script OCR, fully on-device. hOCR gives WORD bounding boxes and
 * text in reading order (rather than assigning imaginary rectangles to OCR lines).
 */
internal object TesseractEngine {
    private data class PixelWord(val text: String, val l: Int, val t: Int, val r: Int, val b: Int, val confidence: Int?)
    private val wordSpan = Regex("""<span\b(?=[^>]*\bocrx_word\b)[^>]*>.*?</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val bbox = Regex("""\bbbox\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)""")
    private val content = Regex(""">([^<>]*)</span>""", RegexOption.DOT_MATCHES_ALL)

    @Suppress("DEPRECATION")
    private fun extractWords(hocr: String, tileTop: Int, ownLo: Int, ownHi: Int): List<PixelWord> {
        val result = mutableListOf<PixelWord>()
        for (match in wordSpan.findAll(hocr)) {
            val bound = bbox.find(match.value) ?: continue
            val (l,t,r,b) = bound.destructured
            val top = tileTop + t.toInt()
            val bottom = tileTop + b.toInt()
            val middle = (top + bottom) / 2
            if (middle < ownLo || middle >= ownHi) continue
            val html = content.find(match.value)?.groupValues?.get(1) ?: continue
            val text = Html.fromHtml(html).toString().trim()
            if (text.isNotBlank()) result += PixelWord(text, l.toInt(), top, r.toInt(), bottom,
                wordConfidence.find(match.value)?.groupValues?.get(1)?.toIntOrNull())
        }
        return result
    }

    fun recognize(context: Context, uris: List<Uri>, language: OcrLanguage): OcrOutput {
        require(language != OcrLanguage.ENGLISH)
        TessModels.ensureBundled(context, language.requiredPacks())
        val tess = TessBaseAPI()
        if (!tess.init(context.filesDir.absolutePath, language.tessCode)) {
            tess.recycle()
            error("Could not initialise ${language.tessCode} OCR")
        }
        try {
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            val lines = mutableListOf<OcrLine>()
            val words = mutableListOf<OcrWord>()
            var firstWidth = 0
            var baseY = 0
            for ((pageIndex, uri) in uris.withIndex()) {
                val textLines = mutableListOf<String>()
                val pageWords = mutableListOf<PixelWord>()
                var imageHeight = 0
                val width = ImageTiler.forEachTile(context, uri) { tile ->
                    tess.setImage(tile.bitmap)
                    val fragment = tess.getUTF8Text().orEmpty()
                        .lines().map { it.trimEnd() }.filter { it.isNotBlank() }
                    val hocr = tess.getHOCRText(0).orEmpty()
                    pageWords += extractWords(hocr, tile.top, tile.ownLo, tile.ownHi)
                    tess.clear()
                    // Deduplicate OCR text repeated by the region overlap.
                    val limit = minOf(12, textLines.size, fragment.size)
                    var duplicate = 0
                    for (n in limit downTo 1) {
                        if (textLines.takeLast(n) == fragment.take(n)) {
                            duplicate = n
                            break
                        }
                    }
                    textLines.addAll(fragment.drop(duplicate))
                    imageHeight = maxOf(imageHeight, tile.top + tile.bitmap.height)
                }
                if (firstWidth == 0) firstWidth = width
                for ((index, line) in textLines.withIndex()) {
                    lines.add(OcrLine(line, top = baseY + index * 24))
                }
                if (width > 0 && imageHeight > 0) {
                    words += pageWords.mapNotNull { w ->
                        val word = OcrWord(
                            w.text, (w.l.toFloat()/width).coerceIn(0f, 1f),
                            (w.t.toFloat()/imageHeight).coerceIn(0f, 1f),
                            (w.r.toFloat()/width).coerceIn(0f, 1f),
                            (w.b.toFloat()/imageHeight).coerceIn(0f, 1f), pageIndex, w.confidence
                        )
                        word.takeIf { it.valid }
                    }
                }
                baseY += imageHeight + 40
            }
            return OcrOutput(lines, firstWidth, words)
        } finally {
            tess.recycle()
        }
    }
}
