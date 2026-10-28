package com.alwansan.crashlogocr.ocr

import android.content.Context
import android.net.Uri
import com.alwansan.crashlogocr.OcrLine
import com.alwansan.crashlogocr.OcrOutput
import com.googlecode.tesseract.android.TessBaseAPI

/** Arabic and bilingual OCR, entirely local, using Tesseract 5 trained data. */
internal object TesseractEngine {
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
            val collected = mutableListOf<OcrLine>()
            var imageWidth = 0
            var baseY = 0
            for (uri in uris) {
                val textLines = mutableListOf<String>()
                val width = ImageTiler.forEachTile(context, uri) { tile ->
                    tess.setImage(tile.bitmap)
                    val fragment = tess.getUTF8Text().orEmpty()
                        .lines().map { it.trimEnd() }.filter { it.isNotBlank() }
                    tess.clear()
                    // Overlapping tiles can contain the same text; dedupe shared whole lines.
                    val limit = minOf(12, textLines.size, fragment.size)
                    var duplicate = 0
                    for (n in limit downTo 1) {
                        if (textLines.takeLast(n) == fragment.take(n)) {
                            duplicate = n
                            break
                        }
                    }
                    textLines.addAll(fragment.drop(duplicate))
                }
                if (imageWidth == 0) imageWidth = width
                for ((index, line) in textLines.withIndex()) {
                    // No fabricated geometry for Tesseract. Only preserve textual reading order.
                    collected.add(OcrLine(line, top = baseY + index * 24))
                }
                baseY += textLines.size * 24 + 40
            }
            return OcrOutput(collected, imageWidth)
        } finally {
            tess.recycle()
        }
    }
}
