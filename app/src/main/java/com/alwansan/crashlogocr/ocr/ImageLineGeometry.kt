package com.alwansan.crashlogocr.ocr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Pixel-based geometry fallback for dark screenshots whose OCR boundingBox
 * begins inside a visible word. Only adjusts line hitboxes, not transcription.
 */
internal object ImageLineGeometry {
    private fun lum(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 +
            Color.blue(color) * 114) / 1000

    fun extendLeftOnDark(
        bitmap: Bitmap,
        left: Int, top: Int, right: Int, bottom: Int
    ): Int {
        if (left !in 1 until bitmap.width || bottom <= top) return left
        val y1 = top.coerceIn(0, bitmap.height - 1)
        val y2 = bottom.coerceIn(y1 + 1, bitmap.height)
        val rowH = y2 - y1
        // Foreground detection is deliberately restricted to dark screenshot rows.
        val baseX = min(2, bitmap.width - 1)
        val bg = listOf(y1, y1 + rowH / 2, y2 - 1)
            .map { lum(bitmap.getPixel(baseX, it)) }
            .sorted()[1]
        if (bg > 85) return left

        val reach = max(24, min((rowH * 7f).toInt(), bitmap.width / 3))
        val begin = max(0, left - reach)
        val cols = BooleanArray(left + 1)
        val stride = max(1, rowH / 28)
        val threshold = (bg + 70).coerceAtMost(225)
        for (x in begin..left) {
            var lit = 0
            var tested = 0
            var y = y1
            while (y < y2) {
                if (lum(bitmap.getPixel(x, y)) >= threshold) lit++
                tested++
                y += stride
            }
            cols[x] = lit >= max(2, tested / 12)
        }
        // A ~1-character gap is OK; a wide blank gap likely separates another UI area.
        val maxGap = max(5, (rowH * 0.38f).toInt())
        return LeadingInkScan.leftOf(cols, left, reach, maxGap)
    }
}
