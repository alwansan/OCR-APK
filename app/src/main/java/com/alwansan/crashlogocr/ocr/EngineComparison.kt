package com.alwansan.crashlogocr.ocr

/** Pure data comparison, not an OCR accuracy score (no ground truth exists). */
object EngineComparison {
    data class Differences(val commonLines: Int, val onlyFirst: List<String>, val onlySecond: List<String>)
    fun compare(first: String, second: String): Differences {
        fun meaningful(text: String) =
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val a = meaningful(first)
        val b = meaningful(second)
        val remainingB = b.toMutableList()
        val firstOnly = mutableListOf<String>()
        var common = 0
        for (line in a) {
            val index = remainingB.indexOf(line)
            if (index >= 0) {
                remainingB.removeAt(index)
                common++
            } else firstOnly += line
        }
        return Differences(common, firstOnly, remainingB)
    }
}
