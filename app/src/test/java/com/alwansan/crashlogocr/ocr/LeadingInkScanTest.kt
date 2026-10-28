package com.alwansan.crashlogocr.ocr

import org.junit.Assert.*
import org.junit.Test

class LeadingInkScanTest {
    @Test fun extendsVisibleTextMissingFromOcrBox() {
        val cols = BooleanArray(200)
        // Leftmost OCR fragment starts at 110, but actual visible word starts at 58.
        for (x in 58..121) if (x % 9 != 0) cols[x] = true
        assertEquals(58, LeadingInkScan.leftOf(cols, 110, 90, 5))
    }

    @Test fun doesNotAbsorbDistantUnrelatedText() {
        val cols = BooleanArray(200)
        for (x in 25..45) cols[x] = true
        for (x in 100..128) cols[x] = true
        assertEquals(100, LeadingInkScan.leftOf(cols, 112, 100, 8))
    }

    @Test fun retainsBoundingBoxWhenNoForegroundDetected() {
        assertEquals(90, LeadingInkScan.leftOf(BooleanArray(140), 90, 50, 6))
    }
}
