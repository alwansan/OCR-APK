package com.alwansan.crashlogocr.ocr

/**
 * ML Kit may recognise a whole line yet omit one or more of its element boxes.
 * Recover ONLY fragments actually present in line.text, and approximate their
 * boxes between the reported element boxes and the line's bounding rectangle.
 * No fabricated words; original OCR text remains authoritative.
 */
internal object LineBoxRecovery {
    data class Item(val text: String, val left: Int, val right: Int)
    data class Fragment(val text: String, val left: Int, val right: Int)

    fun recover(lineText: String, lineLeft: Int, lineRight: Int, items: List<Item>): List<Fragment> {
        if (lineText.isBlank() || lineRight <= lineLeft || lineText.length > 4000) return emptyList()
        if (items.isEmpty()) return listOf(Fragment(lineText.trim(), lineLeft, lineRight))
        // Every ML Kit element must match the actual line in order, otherwise avoid guessing.
        val found = mutableListOf<Pair<Int, Item>>()
        var cursor = 0
        for (item in items) {
            val pos = lineText.indexOf(item.text, cursor, ignoreCase = true)
            if (pos < 0 || item.text.isBlank()) return emptyList()
            found.add(pos to item)
            cursor = pos + item.text.length
        }

        val gaps = mutableListOf<Fragment>()
        var before = 0
        var previousRight = lineLeft
        val charWidth = (lineRight - lineLeft).toFloat() / lineText.length
        for ((index, entry) in found.withIndex()) {
            val (position, item) = entry
            val missing = lineText.substring(before, position).trim()
            if (missing.isNotEmpty()) {
                val span = if (index == 0) {
                    val estimated = (missing.length * charWidth).toInt().coerceAtLeast(2)
                    (minOf(lineLeft, item.left - estimated) to item.left)
                } else previousRight to item.left
                val left = span.first.coerceIn(0, lineRight)
                val right = span.second.coerceAtLeast(left + 2).coerceAtMost(lineRight)
                if (left < right) gaps += Fragment(missing, left, right)
            }
            before = position + item.text.length
            previousRight = item.right
        }
        val suffix = lineText.substring(before).trim()
        if (suffix.isNotEmpty()) {
            val estimated = maxOf(2, (suffix.length * charWidth).toInt())
            val right = maxOf(lineRight, previousRight + estimated)
            gaps += Fragment(suffix, previousRight.coerceAtLeast(0), right)
        }
        return gaps.take(8)
    }
}
