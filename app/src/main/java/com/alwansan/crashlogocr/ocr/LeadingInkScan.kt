package com.alwansan.crashlogocr.ocr

/**
 * A second, OCR-independent image signal for line selection geometry.
 * Given columns with actual foreground pixels around a recognised text row,
 * extend the highlighted rectangle left while the pixels form nearby glyphs.
 * This DOES NOT claim to recognise any missing character.
 */
internal object LeadingInkScan {
    fun leftOf(
        occupied: BooleanArray,
        firstBoxX: Int,
        maxLookback: Int,
        maximumGap: Int
    ): Int {
        if (occupied.isEmpty()) return 0
        val start = firstBoxX.coerceIn(0, occupied.lastIndex)
        val minimum = (start - maxLookback.coerceAtLeast(0)).coerceAtLeast(0)
        val gapLimit = maximumGap.coerceAtLeast(1)
        var leftmost = start
        var gap = 0
        var seen = false
        for (x in start downTo minimum) {
            if (occupied[x]) {
                leftmost = x
                seen = true
                gap = 0
            } else {
                gap++
                if (gap > gapLimit) break
            }
        }
        return if (seen) leftmost else start
    }
}
