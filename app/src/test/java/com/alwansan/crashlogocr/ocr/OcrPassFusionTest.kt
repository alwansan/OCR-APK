package com.alwansan.crashlogocr.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrPassFusionTest {
    private fun line(s: String, y: Int = 80) = OcrPassFusion.Line(
        s, 30, y, 540, y+22, emptyList()
    )

    @Test fun prefersMoreCompleteLineFromSecondPass() {
        val a = line("app.ActivityThread.handleLaunchActivity")
        val b = line("android.app.ActivityThread.handleLaunchActivity")
        assertEquals(b.text, OcrPassFusion.fuse(listOf(a), listOf(b)).single().text)
    }

    @Test fun doesNotDuplicateSameRowOnMinorDifferences() {
        val a = line("android.app.ActivityThread")
        val b = line("andraid.app.ActivityThread")
        assertEquals(a.text, OcrPassFusion.fuse(listOf(a), listOf(b)).single().text)
    }

    @Test fun addsGenuinelyOmittedLine() {
        val a = line("first", 70)
        val b = line("second", 140)
        assertEquals(2, OcrPassFusion.fuse(listOf(a), listOf(b)).size)
    }

    @Test fun doesNotChangeCorrectShorterCodeToUnrelatedLongGarbage() {
        val a = line("android.app.Activity")
        val b = line("ddddxxxxxxxxQQQQYYYYZZZZZZ!!!!")
        assertEquals(a.text, OcrPassFusion.fuse(listOf(a), listOf(b)).single().text)
    }
}
