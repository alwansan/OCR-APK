package com.alwansan.crashlogocr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var rawText: EditText
    private lateinit var cleanText: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rawText = findViewById(R.id.rawText)
        cleanText = findViewById(R.id.cleanText)
        status = findViewById(R.id.status)

        if (savedInstanceState != null) {
            rawText.setText(savedInstanceState.getString("raw", ""))
            cleanText.setText(savedInstanceState.getString("clean", ""))
        }

        findViewById<Button>(R.id.selectImage).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, PICK_IMAGE)
        }
        findViewById<Button>(R.id.pasteText).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (text.isBlank()) {
                toast(R.string.clipboard_empty)
            } else {
                updateResult(text)
            }
        }
        findViewById<Button>(R.id.cleanAgain).setOnClickListener {
            cleanText.setText(TraceCleaner.clean(rawText.text.toString()))
        }
        findViewById<Button>(R.id.copyClean).setOnClickListener {
            val text = cleanText.text.toString()
            if (text.isBlank()) {
                toast(R.string.empty)
            } else {
                ClipboardUtils.copy(this, text)
                toast(R.string.copied)
            }
        }
        findViewById<Button>(R.id.shareClean).setOnClickListener {
            val text = cleanText.text.toString()
            if (text.isBlank()) {
                toast(R.string.empty)
            } else {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }, getString(R.string.text_to_share)))
            }
        }
    }

    private fun updateResult(raw: String) {
        rawText.setText(raw)
        cleanText.setText(TraceCleaner.clean(raw))
        status.setText(if (raw.isBlank()) R.string.empty else R.string.done)
    }

    @Deprecated("The legacy callback remains compatible with minSdk 23.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        status.setText(R.string.recognizing)
        OcrEngine.recognize(this, uri) { result ->
            result.onSuccess { updateResult(it) }
                .onFailure {
                    status.setText(R.string.read_failed)
                    toast(R.string.read_failed)
                }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("raw", rawText.text.toString())
        outState.putString("clean", cleanText.text.toString())
        super.onSaveInstanceState(outState)
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    companion object { private const val PICK_IMAGE = 1001 }
}

internal object ClipboardUtils {
    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CrashLogOCR", text))
    }
}
