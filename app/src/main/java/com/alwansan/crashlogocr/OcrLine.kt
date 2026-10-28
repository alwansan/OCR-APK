package com.alwansan.crashlogocr

/** One recognised text line. Geometry is optional (pasted text has none). */
data class OcrLine(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val width get() = right - left
    val hasGeometry get() = right > left
}

data class OcrOutput(val lines: List<OcrLine>, val imageWidth: Int) {
    val rawText get() = lines.joinToString("\n") { it.text }
}
