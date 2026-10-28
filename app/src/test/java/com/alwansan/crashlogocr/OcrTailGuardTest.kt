package com.alwansan.crashlogocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTailGuardTest {
    @Test fun stripsObservedNonsenseOnlyAfterJavaFrame() {
        val frame = "java.lang.RuntimeException\nat com.android.internal.os.ZygoteInit.main(ZygoteInit.java:944)"
        assertEquals(frame, OcrTailGuard.stripClearlySpuriousSuffix(frame + "July!"))
        assertEquals(frame, OcrTailGuard.stripClearlySpuriousSuffix(frame + "\nع"))
    }
    @Test fun keepsRealArabicAndNonTraceText() {
        assertEquals("صحيح\nع", OcrTailGuard.stripClearlySpuriousSuffix("صحيح\nع"))
        assertEquals("July", OcrTailGuard.stripClearlySpuriousSuffix("July"))
    }
    @Test fun neverChangesOrdinaryStacktraceFramesOrCause() {
        val text = "java.lang.NoClassDefFoundError\nat com.a.B.go(B.java:42)\nCaused by: java.lang.ClassNotFoundException"
        assertEquals(text, OcrTailGuard.stripClearlySpuriousSuffix(text))
    }
}
