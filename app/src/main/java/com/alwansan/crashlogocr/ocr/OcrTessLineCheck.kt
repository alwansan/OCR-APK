package com.alwansan.crashlogocr.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI

/**
 * Independent local second-engine check of suspect JVM frames. Only a small
 * set of short image regions is examined, never the full tall screenshot.
 * Results may correct incomplete OCR text; original ML Kit geometry is kept.
 */
internal class OcrTessLineCheck(private val context: Context) : AutoCloseable {
    private var api: TessBaseAPI? = null
    private var unavailable = false
    private var checksOnPage = 0

    fun beginPage() { checksOnPage = 0 }

    private fun engine(): TessBaseAPI? {
        if (unavailable) return null
        api?.let { return it }
        return try {
            TessModels.ensureBundled(context, listOf("eng"))
            val candidate = TessBaseAPI()
            if (!candidate.init(context.filesDir.absolutePath, "eng")) {
                candidate.recycle()
                unavailable = true
                null
            } else {
                candidate.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
                api = candidate
                candidate
            }
        } catch (_: Exception) {
            unavailable = true
            null
        }
    }

    fun refine(
        bitmap: Bitmap,
        candidates: List<OcrPassFusion.Line>,
        darkBackground: Boolean,
        limit: Int
    ): List<OcrPassFusion.Line> {
        if (limit <= 0 || candidates.isEmpty()) return candidates
        // Hard per-page budget: tall screenshots must not trigger hundreds of
        // expensive JNI recognitions or stall the phone for an entire page.
        val pageBudget = if (limit >= 18) 24 else 8
        val remaining = (pageBudget - checksOnPage).coerceAtLeast(0)
        if (remaining == 0) return candidates
        val suspectIndices = candidates.indices
            .map { it to OcrLineVerifier.priority(candidates[it]) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(minOf(limit, remaining))
            .map { it.first }
        if (suspectIndices.isEmpty()) return candidates
        val tess = engine() ?: return candidates
        val output = candidates.toMutableList()
        for (index in suspectIndices) {
            checksOnPage++
            val source = candidates[index]
            val text = runCatching { readLine(tess, bitmap, source, darkBackground) }.getOrNull()
            if (!text.isNullOrBlank() && OcrLineVerifier.useAlternative(source.text, text)) {
                output[index] = source.copy(text = text,
                    left = OcrLineVerifier.correctedLeft(source.text, text,
                        source.left, source.right, bitmap.width))
            }
        }
        return output
    }

    private fun readLine(
        tess: TessBaseAPI,
        tile: Bitmap,
        line: OcrPassFusion.Line,
        dark: Boolean
    ): String {
        val lineH = (line.bottom - line.top).coerceAtLeast(1)
        val extraX = maxOf(28, (tile.width * 0.10f).toInt())
        val extraY = maxOf(4, lineH / 4)
        val left = (line.left - extraX).coerceIn(0, tile.width)
        val right = (line.right + 12).coerceIn(0, tile.width)
        val top = (line.top - extraY).coerceIn(0, tile.height)
        val bottom = (line.bottom + extraY).coerceIn(0, tile.height)
        if (right - left < 20 || bottom - top < 7) return ""
        val crop = Bitmap.createBitmap(tile, left, top, right - left, bottom - top)
        try {
            val contrast = OcrContrastPass.create(crop, dark)
            try {
                // ImageTiler already magnifies tiny screenshots; do not blow up RAM.
                val multiplier = if (contrast.height < 55 && contrast.width < 1000) 2 else 1
                val resized = if (multiplier > 1)
                    Bitmap.createScaledBitmap(contrast,
                        contrast.width * multiplier, contrast.height * multiplier, true)
                    else contrast
                try {
                    val border = 12
                    val padded = Bitmap.createBitmap(resized.width + border * 2,
                        resized.height + border * 2, Bitmap.Config.RGB_565)
                    try {
                        val canvas = Canvas(padded)
                        canvas.drawColor(Color.WHITE)
                        canvas.drawBitmap(resized, border.toFloat(), border.toFloat(),
                            Paint(Paint.FILTER_BITMAP_FLAG))
                        tess.setImage(padded)
                        val value = tess.getUTF8Text().orEmpty().trim()
                        return value.replace(Regex("""\s*\n\s*"""), " ")
                            .replace(Regex("""[ \t]+"""), " ")
                    } finally {
                        tess.clear()
                        padded.recycle()
                    }
                } finally {
                    if (resized !== contrast) resized.recycle()
                }
            } finally {
                contrast.recycle()
            }
        } finally {
            if (crop !== tile) crop.recycle()
        }
    }

    override fun close() {
        api?.recycle()
        api = null
    }
}
