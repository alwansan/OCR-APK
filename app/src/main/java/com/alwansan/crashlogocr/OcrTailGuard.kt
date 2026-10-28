package com.alwansan.crashlogocr

/** Protects stacktrace copy from two observed trailing bilingual OCR artifacts.
 * Applies ONLY to Java/Android stacktraces, and ONLY at the last visible frame.
 * Raw OCR is preserved for review. Do not run this on ordinary Arabic prose.
 */
object OcrTailGuard {
    private val looksLikeTrace = Regex(
        """(?:java\.lang\.|android\.|at\s+com\.|Caused by:|VerifyError|NoClassDefFoundError)"""
    )
    private val endFrame = Regex(
        """(\((?:[\w.$]+\.(?:java|kt):\d+|Native Method|Unknown Source)\))"""
    )
    private val lastWord = Regex("""(?:\s+)?(?:July!?|ع)\s*$""", RegexOption.IGNORE_CASE)
    private val finalLineOnly = Regex("""(?m)^\s*(?:July!?|ع)\s*$""", RegexOption.IGNORE_CASE)

    fun stripClearlySpuriousSuffix(text: String): String {
        if (!looksLikeTrace.containsMatchIn(text)) return text
        val trimmed = text.trimEnd()
        val lines = trimmed.lines()
        val last = lines.lastOrNull()?.trim().orEmpty()
        if (lines.size >= 2 && finalLineOnly.matches(last)) {
            val previous = lines[lines.lastIndex - 1]
            if (endFrame.containsMatchIn(previous) || previous.contains("... ") ||
                previous.contains(" at ") || previous.startsWith("at ")) {
                return lines.dropLast(1).joinToString("\n")
            }
        }
        val m = lastWord.find(trimmed) ?: return text
        // Do not remove arbitrary date words; only if literally appended to an
        // Android/JVM frame such as "... ZygoteInit.java:944)July!".
        val prefix = trimmed.substring(0, m.range.first).trimEnd()
        val lastFrame = prefix.lines().lastOrNull().orEmpty()
        if (endFrame.containsMatchIn(lastFrame) && lastFrame.trimEnd().endsWith(")"))
            return prefix
        return text
    }
}
