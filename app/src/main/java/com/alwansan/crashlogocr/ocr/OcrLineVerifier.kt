package com.alwansan.crashlogocr.ocr

import kotlin.math.max

/** A safe gate for accepting a third OCR hypothesis.
 * Never fabricate symbols, concatenate unrelated rows or assume all longer results are better.
 */
internal object OcrLineVerifier {
    private val frame = Regex("""(?:android\.|java\.|com\.|\.java:|\.kt:|ActivityThread|TransactionExecutor)""")
    private val suspiciousPrefix = Regex("""^(?:at\s+)?(?:andro(?:i|id)?\.|id\.app\.|app\.|roid\.|ndroid\.|com\.)""")
    fun priority(line: OcrPassFusion.Line): Int {
        val text = line.text.trim()
        if (text.length < 12 || !frame.containsMatchIn(text)) return 0
        if (line.confidence in 0.01f..0.67f) return 3
        if (suspiciousPrefix.containsMatchIn(text) && !text.startsWith("android.") &&
            !text.startsWith("at android.")) return 3
        if (line.confidence in 0.67f..0.83f) return 2
        return 1
    }

    fun useAlternative(original: String, alternative: String): Boolean {
        val old = original.trim().replace(Regex("""\s+"""), " ")
        val fresh = alternative.trim().replace(Regex("""\s+"""), " ")
        if (old.length < 8 || fresh.length <= old.length + 2 ||
            fresh.length > old.length * 1.6f + 25) return false
        val oldCompact = old.filterNot { it.isWhitespace() }
        val newCompact = fresh.filterNot { it.isWhitespace() }
        val similarity = lcsRatio(oldCompact, newCompact)
        if (similarity < 0.76f) return false
        // Require matching Java/frame anchor (not an arbitrary longer unrelated caption).
        val anchors = Regex("""(?:android|java|com|Activity|Thread|Exception|launch|\.kt:|\.java:)""",
            RegexOption.IGNORE_CASE)
        if (!anchors.containsMatchIn(fresh) || !anchors.containsMatchIn(old)) return false
        return true
    }

    private fun lcsRatio(a: String, b: String): Float {
        val x = a.take(240)
        val y = b.take(320)
        val dp = IntArray(y.length + 1)
        for (i in x.indices) {
            var diagonal = 0
            for (j in y.indices) {
                val previous = dp[j + 1]
                dp[j + 1] = if (x[i].equals(y[j], true)) diagonal + 1 else max(dp[j+1], dp[j])
                diagonal = previous
            }
        }
        return dp[y.length].toFloat() / max(1, x.length)
    }
}
