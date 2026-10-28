package com.alwansan.crashlogocr.ocr

/** Heuristic to route actual Android/JVM crash logs to the higher-quality
 * Latin OCR engine even if the user selected the Arabic+English AUTO mode.
 * App language has no effect on whether a Java exception is Java text.
 */
internal object OcrScriptRouter {
    private val exception = Regex(
        """(?:Exception|VerifyError|NoClassDefFoundError|LinkageError|FATAL EXCEPTION|Caused by:)""",
        RegexOption.IGNORE_CASE
    )
    private val locations = Regex(
        """(?:\.java:\s*\d+|\.kt:\s*\d+|android\.app\.|java\.lang\.|com\.[\w$]+\.)""",
        RegexOption.IGNORE_CASE
    )
    fun isJavaTrace(text: String): Boolean {
        if (text.isBlank() || !exception.containsMatchIn(text)) return false
        return locations.findAll(text).take(3).count() >= 2
    }
}
