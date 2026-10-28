package com.alwansan.crashlogocr

/**
 * OCR often confuses I / l / 1 / i in identifiers ("Zygotelnit", "makeApplicationlnner").
 * Only well-known JDK/AOSP names are repaired, by comparing a folded form, so app-specific
 * identifiers (obfuscated names like j32 or ca1) are never touched.
 */
object KnownTokens {
    private val canonical = listOf(
        "ActivityThread", "handleBindApplication", "handleMessage", "handleLaunchActivity",
        "performLaunchActivity", "makeApplicationInner", "makeApplication", "newApplication",
        "Instrumentation", "LoadedApk", "attachBaseContext", "ZygoteInit", "RuntimeInit",
        "MethodAndArgsCaller", "loopOnce", "dispatchMessage",
        "FutureTask", "runAndReset", "ThreadPoolExecutor", "ScheduledThreadPoolExecutor",
        "ScheduledFutureTask", "runWorker", "RunnableAdapter",
        "NoClassDefFoundError", "ClassNotFoundException", "VerifyError", "NullPointerException",
        "IllegalStateException", "IllegalArgumentException", "RuntimeException",
        "UnsatisfiedLinkError", "OutOfMemoryError", "StackOverflowError", "SecurityException",
        "IndexOutOfBoundsException", "ClassCastException", "NoSuchMethodError",
        "NoSuchFieldError", "AbstractMethodError", "IncompatibleClassChangeError",
        "ExceptionInInitializerError", "InflateException",
        "classForName", "FragmentManager", "SyntheticClass", "ExternalSyntheticLambda"
    )

    private fun fold(s: String) = s.lowercase().replace('l', 'i')

    private val byFold: Map<String, String> = canonical.associateBy { fold(it) }

    private val word = Regex("[A-Za-z][A-Za-z0-9]{3,}")

    fun repair(text: String): String =
        word.replace(text) { m ->
            val fixed = byFold[fold(m.value)]
            if (fixed != null && fixed != m.value) fixed else m.value
        }
}
