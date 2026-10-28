package com.alwansan.crashlogocr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/** Transparent share target: recognize, clean, copy and finish without navigating away. */
class ShareActivity : Activity() {
    private var processing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        process(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        process(intent)
    }

    private fun process(source: Intent?) {
        if (processing) return
        if (source?.action != Intent.ACTION_SEND ||
            !source.type.orEmpty().startsWith("image/")) {
            finish()
            return
        }
        val imageUri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            source.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (imageUri == null) {
            toast(R.string.read_failed)
            finish()
            return
        }
        processing = true
        OcrEngine.recognize(this, imageUri) { result ->
            result.onSuccess {
                val cleaned = TraceCleaner.clean(it)
                if (cleaned.isBlank()) {
                    toast(R.string.empty)
                } else {
                    ClipboardUtils.copy(this, cleaned)
                    toast(R.string.copied)
                }
            }.onFailure { toast(R.string.read_failed) }
            processing = false
            finish()
        }
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
}
