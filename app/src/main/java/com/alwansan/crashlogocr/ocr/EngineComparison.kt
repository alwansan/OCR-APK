package com.alwansan.crashlogocr.ocr

/** Pure data comparison, not an OCR accuracy score (no ground truth exists). */
object EngineComparison {
    data class Differences(val commonLines: Int, val onlyFirst: List<String>, val onlySecond: List<String>)
    fun compare(first: String, second: String): Differences {
        fun meaningful(text: String) =
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val a = meaningful(first)
        val b = meaningful(second)
        val available = b.groupingBy { it }.eachCount().toMutableMap()
        val matched = mutableMapOf<String, Int>()
        val firstOnly = mutableListOf<String>()
        var common = 0
        for (line in a) {
            if ((available[line] ?: 0) > 0) {
                available[line] = available.getValue(line) - 1
                matched[line] = (matched[line] ?: 0) + 1
                common++
            } else firstOnly += line
        }
        val secondOnly = mutableListOf<String>()
        for (line in b) {
            if ((matched[line] ?: 0) > 0) {
                matched[line] = matched.getValue(line) - 1
            } else secondOnly += line
        }
        return Differences(common, firstOnly, secondOnly)
    }
}
