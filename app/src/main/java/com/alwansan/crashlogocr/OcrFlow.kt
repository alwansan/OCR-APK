package com.alwansan.crashlogocr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import com.alwansan.crashlogocr.ocr.OcrEngine
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Shared "image(s) -> OCR -> clean -> history -> result screen" pipeline used by every entry point. */
object OcrFlow {
    fun run(activity: Activity, uris: List<Uri>, onFinished: () -> Unit = {}) {
        if (uris.isEmpty()) return
        val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
            .setView(LayoutInflater.from(activity).inflate(R.layout.dialog_progress, null))
            .setCancelable(false)
            .show()
        OcrEngine.recognize(activity, uris.take(12)) { result ->
            if (!activity.isFinishing) dialog.dismiss()
            result.onSuccess { out ->
                val clean = TraceCleaner.clean(out.lines, out.imageWidth)
                if (out.rawText.isBlank()) {
                    Toast.makeText(activity, R.string.empty, Toast.LENGTH_LONG).show()
                } else {
                    val item = HistoryStore.add(activity, out.rawText, clean)
                    activity.startActivity(
                        Intent(activity, ResultActivity::class.java).putExtra(ResultActivity.EXTRA_ID, item.id))
                }
            }.onFailure { Toast.makeText(activity, R.string.read_failed, Toast.LENGTH_LONG).show() }
            onFinished()
        }
    }
}
