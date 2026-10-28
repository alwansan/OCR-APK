package com.alwansan.crashlogocr.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pure-Kotlin spatial fusion. The alternative pass can repair genuinely omitted
 * text, but only replaces matching original lines when it is substantially more
 * complete and plausibly refers to the same content.
 */
internal object OcrPassFusion {
    data class Word(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Line(
        val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int,
        val words: List<Word>, val confidence: Float = 0f
    )

    fun fuse(original: List<Line>, contrasted: List<Line>): List<Line> {
        val kept = original.toMutableList()
        for (candidate in contrasted) {
            if (candidate.text.isBlank()) continue
            val idx = kept.indexOfFirst { matches(it, candidate) }
            if (idx < 0) {
                // Do not add a fake second line beside a highly similar original
                // line just because the OCR engine's bounding box moved.
                val sameHeight = kept.any { old ->
                    abs((old.top + old.bottom) - (candidate.top + candidate.bottom)) <=
                        max(old.bottom-old.top, candidate.bottom-candidate.top)
                }
                if (!sameHeight) kept.add(candidate)
            } else {
                val old = kept[idx]
                if (preferAlternative(old, candidate)) kept[idx] = candidate
            }
        }
        return kept.sortedWith(compareBy<Line>({ it.top }, { it.left }))
    }

    private fun matches(a: Line, b: Line): Boolean {
        val height = max(1, max(a.bottom-a.top, b.bottom-b.top))
        val centerDiff = abs((a.top+a.bottom)-(b.top+b.bottom)) / 2f
        val left = max(a.left, b.left)
        val right = min(a.right, b.right)
        val shorter = max(1, min(a.right-a.left, b.right-b.left))
        return centerDiff < height * 0.6f && right-left > shorter * 0.38f
    }

    private fun preferAlternative(a: Line, b: Line): Boolean {
        val original = a.text.filter { !it.isWhitespace() }
        val second = b.text.filter { !it.isWhitespace() }
        if (second.length <= original.length + 2 || second.length < original.length * 1.12f)
            return false
        val tokensA = normalizedTokens(a.text)
        val tokensB = normalizedTokens(b.text)
        // Require substantial shared text before adopting a longer hypothesis.
        if (tokensA.isNotEmpty() && tokensB.isNotEmpty()) {
            if (tokensA.intersect(tokensB).size >= max(1, tokensA.size / 2)) return true
        }
        return longestCommonSubsequenceRatio(original, second) >= 0.56f
    }

    private fun normalizedTokens(s: String) =
        s.lowercase().split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 3 }.toSet()

    private fun longestCommonSubsequenceRatio(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        // Bounded to avoid pathological CPU use on code/stacktrace fragments.
        val x = a.take(256); val y = b.take(256)
        val row = IntArray(y.length+1)
        for (i in x.indices) {
            var diagonal = 0
            for (j in y.indices) {
                val old = row[j+1]
                row[j+1] = if (x[i].equals(y[j], true)) diagonal+1
                    else max(row[j+1], row[j])
                diagonal = old
            }
        }
        return row[y.length].toFloat() / max(1, x.length)
    }
}
