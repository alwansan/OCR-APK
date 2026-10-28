package com.alwansan.crashlogocr

/**
 * Conservative cleanup of *known* bilingual OCR tail artifacts. Only acts
 * after an actual JVM stack-frame closing parenthesis, never on normal prose.
 * Raw OCR is always preserved in history so users can inspect uncertainty.
 */
object OcrTailGuard {
    private val trace = Regex(
        """(?:java\.lang\.|android\.|at\s+com\.|Caused by:|VerifyError|NoClassDefFoundError)"""
    )
    private val frameAtEnd = Regex(
        """\((?:[\w$]+\.(?:java|kt):\d+|Native Method|Unknown Source)\)$"""
    )
    private val garbage = Regex("""^(?:July!?|Jluy!?|J\s*luy!?|ع)$""", RegexOption.IGNORE_CASE)
    private val attached = Regex(
        """(\((?:[\w$]+\.(?:java|kt):\d+|Native Method|Unknown Source)\))\s*(?:July!?|Jluy!?|J\s*luy!?|ع)\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun stripClearlySpuriousSuffix(text: String): String {
        if (!trace.containsMatchIn(text)) return text
        val clean = text.trimEnd()
        val lines = clean.lines()
        if (lines.size >= 2 && garbage.matches(lines.last().trim())) {
            if (frameAtEnd.containsMatchIn(lines[lines.lastIndex - 1].trimEnd()))
                return lines.dropLast(1).joinToString("\n")
        }
        val tail = attached.find(clean) ?: return text
        return clean.substring(0, tail.range.first) + tail.groupValues[1]
    }
}
