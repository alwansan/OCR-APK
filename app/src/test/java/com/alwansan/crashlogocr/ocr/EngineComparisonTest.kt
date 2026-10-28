package com.alwansan.crashlogocr.ocr

import org.junit.Assert.*
import org.junit.Test

class EngineComparisonTest {
    @Test fun preservesMissingJavaPrefixAsDisagreement() {
        val result = EngineComparison.compare(
            "android.app.ActivityThread\nCaused by: error",
            "d.app.ActivityThread\nCaused by: error")
        assertEquals(1, result.commonLines)
        assertEquals(listOf("android.app.ActivityThread"), result.onlyFirst)
        assertEquals(listOf("d.app.ActivityThread"), result.onlySecond)
    }

    @Test fun countsDuplicateLinesOnlyOncePerCounterpart() {
        val result = EngineComparison.compare("at foo\nat foo", "at foo")
        assertEquals(1, result.commonLines)
        assertEquals(1, result.onlyFirst.size)
        assertEquals(0, result.onlySecond.size)
    }

    @Test fun neverFalselyClaimsMatchingLinesAsRecognitionAccuracy() {
        val result = EngineComparison.compare("خطأ\nمرحبا", "خطأ\nhello")
        assertEquals(1, result.commonLines)
        assertEquals("مرحبا", result.onlyFirst.single())
    }
}
