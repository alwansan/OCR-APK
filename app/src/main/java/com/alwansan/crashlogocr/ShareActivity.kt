package com.alwansan.crashlogocr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.alwansan.crashlogocr.ocr.OcrEngine
import com.alwansan.crashlogocr.ocr.OcrLanguage
import com.alwansan.crashlogocr.ocr.OcrLanguagePreferences
import java.util.concurrent.Executors

/** Transparent share target: quick-copy, but always persist the shared screenshot for image selection later. */
class ShareActivity : Activity() {
    private var busy = false
    private val imageIo = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(src: Intent?) {
        if (busy) return
        val sharedText = if (src?.action == Intent.ACTION_SEND && src.type?.startsWith("text/") == true)
            src.getStringExtra(Intent.EXTRA_TEXT) else null
        if (sharedText != null) { finishWithText(sharedText); return }

        val uris = extractUris(src).take(12)
        if (uris.isEmpty()) { toast(R.string.read_failed); finish(); return }
        busy = true
        toast(R.string.recognizing)
        OcrEngine.recognize(this, uris) { result ->
            result.onSuccess { out ->
                val clean = if (OcrLanguagePreferences.get(this) != OcrLanguage.ENGLISH &&
                    !Regex("""(?:java\.lang\.|Exception|Error|Caused by:)""").containsMatchIn(out.rawText))
                    out.rawText else OcrTailGuard.stripClearlySpuriousSuffix(TraceCleaner.clean(out.lines, out.imageWidth))
                if (out.rawText.isBlank()) {
                    toast(R.string.empty)
                    busy = false
                    finish()
                    return@onSuccess
                }
                val item = HistoryStore.add(this, out.rawText, clean)
                val quick = Prefs.quickCopy(this) && TraceAnalyzer.analyze(clean).hasError
                imageIo.execute {
                    runCatching { ImageOverlayStore.save(applicationContext, item.id, uris, out.words) }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            if (quick) {
                                ClipboardUtils.copy(this, clean)
                                Toast.makeText(this, getString(R.string.copied_n, clean.lines().size),
                                    Toast.LENGTH_SHORT).show()
                            } else {
                                startActivity(Intent(this, ResultActivity::class.java)
                                    .putExtra(ResultActivity.EXTRA_ID, item.id)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        busy = false
                        finish()
                    }
                }
            }.onFailure {
                Toast.makeText(this, it.localizedMessage ?: getString(R.string.read_failed),
                    Toast.LENGTH_LONG).show()
                busy = false
                finish()
            }
        }
    }

    private fun finishWithText(text: String) {
        val clean = TraceCleaner.clean(text)
        val item = HistoryStore.add(this, text, clean)
        startActivity(Intent(this, ResultActivity::class.java)
            .putExtra(ResultActivity.EXTRA_ID, item.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Suppress("DEPRECATION")
    private fun extractUris(src: Intent?): List<Uri> {
        src ?: return emptyList()
        return when (src.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) src.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else src.getParcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) src.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else src.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(src.data)
            else -> emptyList()
        }
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
}
