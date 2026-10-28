package com.alwansan.crashlogocr.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrLineVerifierTest {
    @Test fun restoresOmittedAndroidPrefixFromIndependentOcr() {
        assertTrue(OcrLineVerifier.useAlternative(
            "id.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4686)",
            "android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4686)"))
    }
    @Test fun doesNotReplaceWithUnrelatedGarbage() {
        assertFalse(OcrLineVerifier.useAlternative(
            "android.app.ActivityThread.performLaunchActivity",
            "java.lang.RuntimeException: Unable to start Activity"))
    }
    @Test fun rejectsAppendedJunkEvenIfLonger() {
        assertFalse(OcrLineVerifier.useAlternative(
            "java.lang.RuntimeException: unable to start activity",
            "X" + "java.lang.RuntimeException: unable to start activity" + "x".repeat(300)))
    }
    @Test fun verifiedLineBoxIncludesRestoredPrefix() {
        val left = OcrLineVerifier.correctedLeft("id.app.ActivityThread",
            "android.app.ActivityThread", 180, 420, 1080)
        assertTrue(left < 180)
        assertTrue(left >= 0)
    }
    @Test fun prioritizesLikelyBadFrame() {
        val frame = OcrPassFusion.Line("id.app.ActivityThread.handleLaunchActivity",
            0,0,400,30,emptyList(), 0.50f)
        assertEquals(3, OcrLineVerifier.priority(frame))
    }
}
