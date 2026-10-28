package com.alwansan.crashlogocr

/** Read-only insight about a cleaned trace: headline, root cause, app package, suspicious lines. */
object TraceAnalyzer {
    data class Report(
        val headline: String,
        val rootCause: String?,
        val appPackage: String?,
        val lineCount: Int,
        val frameCount: Int,
        val suspicious: List<Int>,
        val hasError: Boolean
    )

    private val strictFrame =
        Regex("""^at [\w$<>.\-]+\((?:[\w$.\-]+:\d+|Native Method|Unknown Source|[\w$.\-]+)\)$""")
    private val frameworkPrefixes = listOf(
        "java.", "javax.", "android.", "androidx.", "com.android.", "dalvik.", "kotlin.",
        "kotlinx.", "libcore.", "sun.", "com.google.android."
    )
    private val errorRe = Regex("""(?:java|android|kotlin)\.[\w.$]+(?:Exception|Error)""")

    fun isFramework(frame: String): Boolean {
        val name = frame.removePrefix("at ")
        return frameworkPrefixes.any { name.startsWith(it) }
    }

    fun analyze(clean: String): Report {
        val lines = clean.lines().filter { it.isNotBlank() }
        val frames = lines.filter { it.startsWith("at ") }
        val suspicious = lines.indices.filter { i ->
            lines[i].startsWith("at ") && !strictFrame.matches(lines[i])
        }
        val headline = lines.firstOrNull { errorRe.containsMatchIn(it) } ?: lines.firstOrNull().orEmpty()
        val cause = lines.lastOrNull { it.startsWith("Caused by:") }?.removePrefix("Caused by:")?.trim()
        val appFrames = frames.filterNot { isFramework(it) }
        val pkg = appFrames
            .map { it.removePrefix("at ").split('.').take(3).joinToString(".") }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return Report(
            headline = headline.take(220),
            rootCause = cause?.take(220),
            appPackage = pkg,
            lineCount = lines.size,
            frameCount = frames.size,
            suspicious = suspicious,
            hasError = lines.any { errorRe.containsMatchIn(it) }
        )
    }

    /** Short form for pasting into chats / AI: messages + app frames, framework frames collapsed. */
    fun compact(clean: String): String {
        val out = mutableListOf<String>()
        var hidden = 0
        fun flush() { if (hidden > 0) { out += "    … $hidden framework frames hidden"; hidden = 0 } }
        for (line in clean.lines()) {
            if (line.startsWith("at ") && isFramework(line)) hidden++
            else { flush(); if (line.isNotBlank()) out += line }
        }
        flush()
        return out.joinToString("\n")
    }

    fun markdown(clean: String): String = "```text\n$clean\n```"
}
