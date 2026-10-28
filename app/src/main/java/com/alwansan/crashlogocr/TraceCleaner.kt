package com.alwansan.crashlogocr

/**
 * Conservative formatter for Java/Android stacktraces captured from Arabic/RTL
 * crash-report screenshots. Never invents missing text or silently changes
 * class names. OCR may still misread individual characters; keep the raw view.
 */
object TraceCleaner {
    private val start = Regex(
        """^(?:FATAL EXCEPTION|Process:|Exception in thread|Caused by:|Suppressed:|java\.|javax\.|android\.|kotlin\.|dalvik\.)""",
        RegexOption.IGNORE_CASE
    )
    private val javaError = Regex("""(?:java|android|kotlin)\.[\w.$]+(?:Exception|Error)""")
    private val frame = Regex("""^(?:at\s+(?:[\w$]+\.)+|at\s+[\w$]+\(|\.\.\.\s*\d+\s+more\b)""")
    private val uiNoise = setOf(
        "إرسال تعليق", "إرسال", "إلغاء", "إلغاء الأمر",
        "send feedback", "send", "cancel", "feedback"
    )
    private val bidiControls = Regex("[\u200e\u200f\u061c\u202a-\u202e\u2066-\u2069]")
    private val extraSpaces = Regex("[\t ]+")
    private val camelCaseRemainder = Regex("""^[a-z]{1,4}[A-Z(.(]""")
    private val truncatedPath = Regex("""(?:[\w$]+\.)+[\w$]+$""")

    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw
            .replace("\r\n", "\n").replace('\r', '\n')
            .replace(bidiControls, "")
            .lines()
            .map { extraSpaces.replace(it.trim(), " ") }
            .filter { it.isNotBlank() }
            .filterNot { uiNoise.contains(it.lowercase()) }
        if (lines.isEmpty()) return ""

        val first = lines.indexOfFirst { start.containsMatchIn(it) || javaError.containsMatchIn(it) }
        val relevant = if (first >= 0) lines.drop(first) else lines
        return repairWrappedLines(relevant).joinToString("\n").trim()
    }

    private fun repairWrappedLines(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var current = ""
        for (line in lines) {
            if (current.isEmpty()) {
                current = line
                continue
            }
            if (isNewEntry(line) && !current.endsWith("at")) {
                out.add(current)
                current = line
                continue
            }
            val separator = when {
                current.endsWith("at") -> " "
                current.endsWith(".") || current.endsWith("/") -> ""
                current.endsWith("(") || current.endsWith("$") -> ""
                // e.g. "attachBas" + "eContext(...)" or "LoadedAp" + "k.java:1534)"
                camelCaseRemainder.containsMatchIn(line) &&
                    (current.contains(" at ") || current.startsWith("at ")) -> ""
                // Stack frame split inside its Java filename or method signature.
                current.startsWith("at ") && !current.endsWith(")") &&
                    (line.contains(".java:") || line.contains("(")) -> ""
                // A class descriptor split at a slash/dot is never separated by a space.
                truncatedPath.containsMatchIn(current) &&
                    line.startsWith(".") -> ""
                else -> " "
            }
            current += separator + line
        }
        if (current.isNotEmpty()) out.add(current)
        return out
    }

    private fun isNewEntry(line: String): Boolean {
        if (line == "at") return false
        return start.containsMatchIn(line) ||
            frame.containsMatchIn(line) ||
            line.startsWith("at ") ||
            line.startsWith("... ") ||
            line.startsWith("Caused by:") ||
            line.startsWith("Suppressed:")
    }
}
