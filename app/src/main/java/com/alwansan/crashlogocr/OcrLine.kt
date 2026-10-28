package com.alwansan.crashlogocr

/** OCR word with coordinates normalized to the displayed image (0..1).
 * Normalization is independent of ML Kit image upscaling / tile overlap.
 */
data class OcrWord(
    val text: String,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val imageIndex: Int = 0,
    val confidence: Int? = null
) {
    val valid get() = text.isNotBlank() && left >= 0f && top >= 0f &&
        right > left && bottom > top && right <= 1.01f && bottom <= 1.01f
}

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

data class OcrOutput(
    val lines: List<OcrLine>,
    val imageWidth: Int,
    val words: List<OcrWord> = emptyList()
) {
    val rawText get() = lines.joinToString("\n") { it.text }
}
