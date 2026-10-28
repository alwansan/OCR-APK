package com.alwansan.crashlogocr

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * Google Lens-style selection from the screenshot itself.
 * No text input is present, so tapping or selecting cannot summon a keyboard.
 */
class ImageTextActivity : AppCompatActivity() {
    private lateinit var canvas: LiveTextImageView
    private lateinit var pageLabel: TextView
    private lateinit var previewText: TextView
    private lateinit var counter: TextView
    private lateinit var copy: MaterialButton
    private lateinit var share: MaterialButton
    private lateinit var previous: MaterialButton
    private lateinit var next: MaterialButton
    private var pages = emptyList<ImageOverlayStore.ImagePage>()
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_image_text)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        pages = ImageOverlayStore.pages(this, id)
        if (pages.isEmpty()) {
            Toast.makeText(this, R.string.no_saved_image, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.imageToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        canvas = findViewById(R.id.imageCanvas)
        pageLabel = findViewById(R.id.pageLabel)
        previewText = findViewById(R.id.selectionText)
        counter = findViewById(R.id.selectionCount)
        copy = findViewById(R.id.copySelected)
        share = findViewById(R.id.shareSelected)
        previous = findViewById(R.id.previousPage)
        next = findViewById(R.id.nextPage)

        canvas.onSelectionChanged = { text, count ->
            counter.text = getString(R.string.selected_word_count, count)
            previewText.text = if (text.isBlank()) getString(R.string.select_words_hint) else text
            copy.isEnabled = count > 0
            share.isEnabled = count > 0
        }
        findViewById<MaterialButton>(R.id.selectAllWords).setOnClickListener { canvas.selectAll() }
        findViewById<MaterialButton>(R.id.clearSelected).setOnClickListener { canvas.clearSelection() }
        findViewById<MaterialButton>(R.id.resetZoom).setOnClickListener { canvas.resetView() }
        copy.setOnClickListener {
            val text = canvas.selectedText()
            if (text.isNotEmpty()) {
                ClipboardUtils.copy(this, text)
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
        share.setOnClickListener {
            val text = canvas.selectedText()
            if (text.isNotBlank()) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, getString(R.string.share_text)))
        }
        previous.setOnClickListener { loadPage(currentPage - 1) }
        next.setOnClickListener { loadPage(currentPage + 1) }
        loadPage(savedInstanceState?.getInt("page", 0) ?: 0)
    }

    private fun loadPage(index: Int) {
        if (index !in pages.indices) return
        currentPage = index
        val page = pages[index]
        pageLabel.text = getString(R.string.image_page_count, index + 1, pages.size)
        previous.isEnabled = index > 0
        next.isEnabled = index < pages.lastIndex
        previous.visibility = if (pages.size > 1) View.VISIBLE else View.GONE
        next.visibility = if (pages.size > 1) View.VISIBLE else View.GONE
        try {
            canvas.setImage(page.file, page.words)
            if (page.words.isEmpty())
                Toast.makeText(this, R.string.image_no_word_boxes, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.read_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", currentPage)
        super.onSaveInstanceState(outState)
    }

    companion object { const val EXTRA_ID = "history_id" }
}
