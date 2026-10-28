package com.alwansan.crashlogocr.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrScriptRouterTest {
    @Test fun javaCrashUsesLatinEngineEvenWhenUiArabic() {
        val text = "java.lang.RuntimeException: Cannot start\n" +
            "at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4461)\n" +
            "at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4686)"
        assertTrue(OcrScriptRouter.isJavaTrace(text))
    }

    @Test fun ordinaryArabicEnglishDocumentIsNotMisclassified() {
        assertFalse(OcrScriptRouter.isJavaTrace("النتيجة: هذا اختبار للغة العربية English"))
        assertFalse(OcrScriptRouter.isJavaTrace("This Exception does not contain a stack trace"))
    }
}
