package com.alwansan.crashlogocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            more 15 …
            إرسال
            إلغاء
        """.trimIndent()
        val cleaned = TraceCleaner.clean(raw)
        assertTrue(cleaned.startsWith("java.lang.NoClassDefFoundError:"))
        assertTrue(cleaned.contains("Caused by: java.lang.ClassNotFoundException"))
        assertTrue(cleaned.endsWith("... 15 more"))
        assertFalse(cleaned.contains("إلغاء"))
    }

    @Test fun gluesWrappedFramesWithoutSpaces() {
        val raw = """
            java.lang.NoClassDefFoundError: missing
            at
            com.shabakaty.cinemana.CinemanaApplication.attachBas
            eContext(CinemanaApplication.kt:2)
            at android.app.Instrumentation.newApplication(Instrumentat
            ion.java:1357)
            at
            android.app.LoadedApk.makeApplicationlnner(LoadedApk.j
            ava:1534)
        """.trimIndent()
        val cleaned = TraceCleaner.clean(raw)
        assertTrue(cleaned.contains("at com.shabakaty.cinemana.CinemanaApplication.attachBaseContext(CinemanaApplication.kt:2)"))
        assertTrue(cleaned.contains("at android.app.Instrumentation.newApplication(Instrumentation.java:1357)"))
        assertTrue(cleaned.contains("at android.app.LoadedApk.makeApplicationlnner(LoadedApk.java:1534)"))
    }

    @Test fun repairsErrorWordAndMiddleDots() {
        val raw = "java.lang.NoClassDefFound Error: x\nat com.a.ca1·Queue110·remember(Queue 110·java:23)"
        val cleaned = TraceCleaner.clean(raw)
        assertTrue(cleaned.startsWith("java.lang.NoClassDefFoundError: x"))
        assertTrue(cleaned.contains("at com.a.ca1.Queue110.remember(Queue110.java:23)"))
    }

    @Test fun nativeMethodKeepsItsSpace() {
        val cleaned = TraceCleaner.clean("java.lang.X: y\nat java.lang.reflect.Method.invoke(Native Method)")
        assertTrue(cleaned.contains("invoke(Native Method)"))
    }

    @Test fun keepsObfuscatedNamesUntouched() {
        val cleaned = TraceCleaner.clean("java.lang.NoClassDefFoundError: Failed resolution of: Lcom/shabakaty/downloader/j32;")
        assertTrue(cleaned.contains("Lcom/shabakaty/downloader/j32;"))
    }

    @Test fun geometryDecidesBetweenSpaceAndMidTokenBreak() {
        // Container is 880px wide (x = 85..965), image 1047px.
        val lines = listOf(
            OcrLine("java.lang.NoClassDefFoundError: Failed resolution of:", 150, 0, 965, 20),   // 63px short -> space
            OcrLine(";Lcom/shabakaty/downloader/j32", 450, 20, 965, 40),
            OcrLine("java.lang.VerifyError: Verifier rejected class com.x.RecoveryCap", 85, 40, 965, 60), // full -> glue
            OcrLine("sule.embedMp4(java.io.File)", 600, 60, 965, 80)
        )
        val cleaned = TraceCleaner.clean(lines, 1047)
        assertTrue(cleaned.contains("resolution of: ;Lcom/shabakaty/downloader/j32"))
        assertTrue(cleaned.contains("com.x.RecoveryCapsule.embedMp4(java.io.File)"))
    }

    @Test fun noErrorFallbackIsSafe() {
        assertEquals("hello world", TraceCleaner.clean("إرسال تعليق\nhello world\nإلغاء"))
        assertEquals("", TraceCleaner.clean(" \n \t"))
    }

    @Test fun analyzerFindsAppPackageAndCompacts() {
        val trace = """
            java.lang.VerifyError: Verifier rejected class com.shabakaty.cinemana.ca1.RecoveryCapsule
            at com.shabakaty.cinemana.ca1.Queue110.scanBody(Queue110.java:63)
            at java.util.concurrent.FutureTask.run(FutureTask.java:328)
            at java.lang.Thread.run(Thread.java:1571)
        """.trimIndent()
        val r = TraceAnalyzer.analyze(trace)
        assertTrue(r.hasError)
        assertEquals("com.shabakaty.cinemana", r.appPackage)
        assertTrue(r.suspicious.isEmpty())
        assertTrue(TraceAnalyzer.compact(trace).contains("2 framework frames hidden"))
    }
}
