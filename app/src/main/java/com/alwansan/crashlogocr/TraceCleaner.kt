package com.alwansan.crashlogocr

/**
 * Turns noisy OCR output of an Android crash dialog into a Java stack trace.
 *
 * Key idea: the screenshot soft-wraps long tokens, so a wrapped line may or may not hide a space.
 *  - Stack frames ("at pkg.Class.method(File.java:12)") never contain spaces, so every wrapped
 *    piece is glued back without one.
 *  - In message lines we use the line geometry: a renderer only breaks INSIDE a word when the
 *    previous line is completely full, so a nearly-full previous line means "no space".
 * Identifiers are never invented; only I/l confusions of well-known framework names are repaired.
 */
object TraceCleaner {
    private val startRe = Regex(
        """^(?:FATAL EXCEPTION|Process:|Exception in thread|Caused by:|Suppressed:|java\.|javax\.|android\.|androidx\.|kotlin\.|dalvik\.)""",
        RegexOption.IGNORE_CASE
    )
    private val errorRe = Regex("""(?:java|android|kotlin)\.[\w.$]+(?:Exception|Error)""")
    private val hardHeaderRe = Regex("""^(?:Caused by:|Suppressed:|FATAL EXCEPTION|Process:|Exception in thread)""", RegexOption.IGNORE_CASE)
    private val frameStartRe = Regex("""^at(?:\s|$)""")
    private val moreRe = Regex("""^(?:\.\.\.|…|·{2,})\s*(\d+)\s+more\b""")
    // RTL paragraphs show "... 15 more" reversed as "more 15 ..."
    private val moreReversedRe = Regex("""^more\s+(\d+)\s*(?:\.\.\.|…|·+|\.+)?$""", RegexOption.IGNORE_CASE)

    private val uiNoise = setOf(
        "إرسال تعليق", "إرسال", "إلغاء", "إلغاء الأمر", "ارسال تعليق", "ارسال", "الغاء",
        "send feedback", "send", "cancel", "feedback"
    )
    private val bidi = Regex("[\u200e\u200f\u061c\u202a-\u202e\u2066-\u2069]")
    private val spaces = Regex("[\\t ]+")
    private val midDot = Regex("""(?<=[\w$])[·•∙・](?=[\w$])""")
    private val glueErrorWord = Regex("""^((?:[\w$]+\.)+[\w$]*[a-z]) (Error|Exception)\b""")

    fun clean(raw: String): String =
        clean(raw.lines().map { OcrLine(it) }, 0)

    fun clean(input: List<OcrLine>, imageWidth: Int): String {
        val lines = normalise(input)
        if (lines.isEmpty()) return ""
        val first = lines.indexOfFirst { startRe.containsMatchIn(it.text) || errorRe.containsMatchIn(it.text) }
        val relevant = if (first >= 0) lines.drop(first) else lines

        val maxWidth = relevant.filter { it.hasGeometry }.maxOfOrNull { it.width } ?: 0
        // The widest line only proves "full width" when it is close to the image width.
        val containerKnown = maxWidth > 0 && imageWidth > 0 && maxWidth >= imageWidth * 0.7

        val entries = mutableListOf<Entry>()
        for (line in relevant) {
            val t = line.text
            val kind = when {
                frameStartRe.containsMatchIn(t) -> Kind.FRAME
                moreRe.containsMatchIn(t) || moreReversedRe.matches(t) -> Kind.MORE
                // Inside a frame, "android.app.X(...)" on its own line is the wrapped rest of "at", not a new header.
                entries.lastOrNull()?.kind == Kind.FRAME && !hardHeaderRe.containsMatchIn(t) -> null
                startRe.containsMatchIn(t) || entries.isEmpty() -> Kind.HEADER
                else -> null
            }
            if (kind != null) {
                entries += Entry(kind, StringBuilder(t), line)
            } else {
                val e = entries.last()
                e.text.append(separator(e, line, maxWidth, containerKnown)).append(t)
                e.last = line
            }
        }
        return entries.joinToString("\n") { finish(it) }.trim()
    }

    private fun normalise(input: List<OcrLine>): List<OcrLine> =
        input.mapNotNull { l ->
            var t = l.text.replace(bidi, "")
            t = spaces.replace(t.trim(), " ")
            t = t.map { arabicDigit(it) }.joinToString("")
            t = midDot.replace(t, ".")
            if (t.isBlank() || uiNoise.contains(t.lowercase())) null else l.copy(text = t)
        }

    private fun arabicDigit(c: Char): Char = when (c) {
        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
        in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
        else -> c
    }

    private fun separator(e: Entry, next: OcrLine, maxWidth: Int, containerKnown: Boolean): String {
        if (e.kind == Kind.FRAME) return "" // frames never contain spaces
        val prev = e.text.toString()
        if (prev.endsWith(".") || prev.endsWith("/") || prev.endsWith("(") || prev.endsWith("$")) return ""
        if (next.text.startsWith(".") || next.text.startsWith(")")) return ""
        val p = e.last
        if (containerKnown && p.hasGeometry && p.text.isNotEmpty()) {
            val avgChar = p.width.toFloat() / p.text.length
            val gap = maxWidth - p.width
            if (gap < avgChar * 1.5f) return ""   // previous line is full: the word was cut mid-token
        }
        return " "
    }

    private fun finish(e: Entry): String {
        var s = e.text.toString()
        when (e.kind) {
            Kind.FRAME -> {
                val body = s.removePrefix("at").replace(" ", "")
                    .replace("NativeMethod", "Native Method")
                    .replace("UnknownSource", "Unknown Source")
                s = "at " + fixLineNumber(body)
            }
            Kind.MORE -> {
                val n = (moreRe.find(s) ?: Regex("""(\d+)""").find(s))?.groupValues?.get(1)
                s = if (n != null) "... $n more" else s
            }
            Kind.HEADER -> {
                s = glueErrorWord.replace(s, "$1$2")
                // Never rewrite package, class or method names based on OCR guesses.
            }
        }
        return s
    }

    /** "(File.java:l34)" -> "(File.java:134)": only the digits after the last colon are touched. */
    private fun fixLineNumber(s: String): String {
        val m = Regex(""":([0-9OolIiS]+)\)$""").find(s) ?: return s
        val digits = m.groupValues[1]
        if (digits.none { it.isDigit() }) return s
        val fixed = digits.map {
            when (it) { 'O', 'o' -> '0'; 'l', 'I', 'i' -> '1'; 'S' -> '5'; else -> it }
        }.joinToString("")
        return s.substring(0, m.range.first) + ":" + fixed + ")"
    }

    private enum class Kind { HEADER, FRAME, MORE }
    private class Entry(val kind: Kind, val text: StringBuilder, var last: OcrLine)
}
