package com.alwansan.crashlogocr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import com.alwansan.crashlogocr.ocr.OcrEngine
import com.alwansan.crashlogocr.ocr.OcrLanguage
import com.alwansan.crashlogocr.ocr.OcrLanguagePreferences
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

/** Shared image -> OCR -> private image/word cache -> history -> results pipeline. */
object OcrFlow {
    private val io = Executors.newSingleThreadExecutor()
    private fun looksLikeCrash(text: String) =
        Regex("""(?:java\.lang\.|Exception|Error|Caused by:)""").containsMatchIn(text)

    fun run(activity: Activity, uris: List<Uri>, onFinished: () -> Unit = {}) {
        if (uris.isEmpty()) return
        val chosen = uris.take(12)
        val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
            .setView(LayoutInflater.from(activity).inflate(R.layout.dialog_progress, null))
            .setCancelable(false)
            .show()
        OcrEngine.recognize(activity, chosen) { result ->
            result.onSuccess { out ->
                val language = OcrLanguagePreferences.get(activity)
                val clean = if (language != OcrLanguage.ENGLISH && !looksLikeCrash(out.rawText))
                    out.rawText else TraceCleaner.clean(out.lines, out.imageWidth)
                if (out.rawText.isBlank()) {
                    dialog.dismiss()
                    Toast.makeText(activity, R.string.empty, Toast.LENGTH_LONG).show()
                    onFinished()
                    return@onSuccess
                }
                val item = HistoryStore.add(activity, out.rawText, clean)
                // URI grants from Shares/Pickers may expire. Copy source on a worker before
                // launching the result screen. Keep UI responsive for tall image files.
                io.execute {
                    runCatching {
                        ImageOverlayStore.save(activity.applicationContext, item.id, chosen, out.words)
                    }
                    activity.runOnUiThread {
                        dialog.dismiss()
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            activity.startActivity(Intent(activity, ResultActivity::class.java)
                                .putExtra(ResultActivity.EXTRA_ID, item.id))
                        }
                        onFinished()
                    }
                }
            }.onFailure {
                dialog.dismiss()
                Toast.makeText(activity,
                    it.localizedMessage ?: activity.getString(R.string.read_failed),
                    Toast.LENGTH_LONG).show()
                onFinished()
            }
        }
    }
}
