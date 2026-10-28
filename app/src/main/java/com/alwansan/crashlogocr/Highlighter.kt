package com.alwansan.crashlogocr

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/** Colours a cleaned trace: errors red, "Caused by" amber, app frames bold, framework frames dim. */
object Highlighter {
    fun apply(context: Context, text: String, dark: Boolean): CharSequence {
        val sb = SpannableStringBuilder(text)
        val red = if (dark) Color.parseColor("#FF8A80") else Color.parseColor("#B3261E")
        val amber = if (dark) Color.parseColor("#FFCC80") else Color.parseColor("#8A5100")
        val dim = if (dark) Color.parseColor("#8E99A8") else Color.parseColor("#6B7686")
        val warn = if (dark) Color.parseColor("#4D3F00") else Color.parseColor("#FFF1B8")
        var pos = 0
        for (line in text.split("\n")) {
            val end = pos + line.length
            when {
                line.startsWith("Caused by:") -> {
                    sb.setSpan(ForegroundColorSpan(amber), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.startsWith("at ") -> {
                    if (TraceAnalyzer.isFramework(line))
                        sb.setSpan(ForegroundColorSpan(dim), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    else
                        sb.setSpan(StyleSpan(Typeface.BOLD), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (!Regex("""^at [\w$<>.\-]+\((?:[\w$.\-]+:\d+|Native Method|Unknown Source|[\w$.\-]+)\)$""").matches(line))
                        sb.setSpan(BackgroundColorSpan(warn), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.startsWith("... ") -> sb.setSpan(ForegroundColorSpan(dim), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else -> if (pos == 0 || line.contains("Exception") || line.contains("Error")) {
                    sb.setSpan(ForegroundColorSpan(red), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            pos = end + 1
        }
        return sb
    }
}
