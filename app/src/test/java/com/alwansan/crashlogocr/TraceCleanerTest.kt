package com.alwansan.crashlogocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceCleanerTest {
    @Test fun stripsArabicChromeAndKeepsCause() {
        val raw = """
            32
            إرسال تعليق
            java.lang.NoClassDefFoundError: Failed resolution of: Lcom/shabakaty/downloader/j32;
            at com.shabakaty.cinemana.CinemanaApplication.attachBaseContext(CinemanaApplication.kt:2)
            Caused by: java.lang.ClassNotFoundException: com.shabakaty.downloader.j32
            ... 15 more
            إرسال
            إلغاء
        """.trimIndent()
        val cleaned = TraceCleaner.clean(raw)
        assertTrue(cleaned.startsWith("java.lang.NoClassDefFoundError:"))
        assertTrue(cleaned.contains("Caused by: java.lang.ClassNotFoundException"))
        assertTrue(cleaned.contains("... 15 more"))
        assertTrue(!cleaned.contains("إلغاء"))
    }

    @Test fun repairsWrapInsideFrame() {
        val raw = """
            java.lang.NoClassDefFoundError: missing
            at
            com.shabakaty.cinemana.CinemanaApplication.attachBas
            eContext(CinemanaApplication.kt:2)
            at android.app.LoadedAp
            k.java:1534)
        """.trimIndent()
        val cleaned = TraceCleaner.clean(raw)
        assertTrue(cleaned.contains("at com.shabakaty.cinemana.CinemanaApplication.attachBaseContext(CinemanaApplication.kt:2)"))
        assertTrue(cleaned.contains("at android.app.LoadedApk.java:1534)"))
    }

    @Test fun noErrorFallbackIsSafe() {
        assertEquals("hello world", TraceCleaner.clean("إرسال تعليق\nhello world\nإلغاء"))
        assertEquals("", TraceCleaner.clean(" \n \t"))
    }

    @Test fun keepsVerifyErrorAndFrames() {
        val raw = """
            java.lang.VerifyError: Verifier rejected class com.shabakaty.cinemana.ca1.RecoveryCapsule
            at com.shabakaty.cinemana.ca1.Queue110.scanBody(Queue110.java:63)
            at com.shabakaty.cinemana.ca1.Restart108.tick(Restart108.java:143)
        """.trimIndent()
        assertTrue(TraceCleaner.clean(raw).contains("\nat com.shabakaty.cinemana.ca1.Restart108.tick"))
    }
}
