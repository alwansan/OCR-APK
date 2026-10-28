package com.alwansan.crashlogocr

import android.content.Intent
import android.app.SearchManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.File
import java.util.concurrent.Executors
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
    private val cropExecutor = Executors.newSingleThreadExecutor()
    private var cropSelecting = false
    private var cropBusy = false

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
            findViewById<MaterialButton>(R.id.searchSelected).isEnabled = count > 0
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
        findViewById<MaterialButton>(R.id.searchSelected).setOnClickListener {
            val value = canvas.selectedText().trim()
            if (value.isNotBlank()) {
                val intent = Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, value)
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=" + Uri.encode(value))))
                    } catch (_: Exception) {
                        Toast.makeText(this, R.string.search_unavailable, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        val uncertain = findViewById<MaterialButton>(R.id.uncertainWords)
        uncertain.setOnClickListener { canvas.toggleUncertain() }

        val cropMode = findViewById<MaterialButton>(R.id.cropMode)
        val runCrop = findViewById<MaterialButton>(R.id.runCrop)
        cropMode.setOnClickListener {
            if (cropBusy) return@setOnClickListener
            cropSelecting = !cropSelecting
            canvas.setCropMode(cropSelecting)
            cropMode.alpha = if (cropSelecting) 0.6f else 1f
            runCrop.visibility = if (cropSelecting) View.VISIBLE else View.GONE
            if (cropSelecting)
                Toast.makeText(this, R.string.crop_hint, Toast.LENGTH_LONG).show()
        }
        runCrop.setOnClickListener { runCroppedOcr() }
        val gestureButton = findViewById<MaterialButton>(R.id.gestureMode)
        gestureButton.setOnClickListener {
            val enabled = !canvas.isSelectionMode
            canvas.setSelectionMode(enabled)
            gestureButton.setText(
                if (enabled) R.string.gesture_select_mode else R.string.gesture_pan_mode
            )
            gestureButton.contentDescription = getString(
                if (enabled) R.string.gesture_select_description
                else R.string.gesture_pan_description
            )
        }
        previous.setOnClickListener { loadPage(currentPage - 1) }
        next.setOnClickListener { loadPage(currentPage + 1) }
        loadPage(savedInstanceState?.getInt("page", 0) ?: 0)
    }

    private fun loadPage(index: Int) {
        if (index !in pages.indices) return
        currentPage = index
        val page = pages[index]
        cropSelecting = false
        canvas.setSelectionMode(false)
        findViewById<MaterialButton>(R.id.gestureMode).setText(R.string.gesture_pan_mode)
        findViewById<MaterialButton>(R.id.runCrop).visibility = View.GONE
        findViewById<MaterialButton>(R.id.cropMode).alpha = 1f
        findViewById<MaterialButton>(R.id.uncertainWords).isEnabled =
            page.words.any { it.confidence != null }
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

    private fun runCroppedOcr() {
        if (cropBusy) return
        val rectangle = canvas.getCropRect()
        if (rectangle == null) {
            Toast.makeText(this, R.string.crop_empty, Toast.LENGTH_SHORT).show()
            return
        }
        cropBusy = true
        val file = pages[currentPage].file
        Toast.makeText(this, R.string.cropping, Toast.LENGTH_SHORT).show()
        cropExecutor.execute {
            val output = runCatching {
                val cropFile = File(cacheDir, "ocr_crop_" + System.nanoTime() + ".png")
                @Suppress("DEPRECATION")
                val reader = BitmapRegionDecoder.newInstance(file.absolutePath, false)
                    ?: error("Could not decode source")
                try {
                    val rect = Rect(rectangle)
                    rect.intersect(0, 0, reader.width, reader.height)
                    require(rect.width() > 31 && rect.height() > 31)
                    var sample = 1
                    while (rect.width().toLong() * rect.height() / (sample * sample) > 4_000_000L)
                        sample *= 2
                    val bmp = reader.decodeRegion(rect, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }) ?: error("Crop decoding failed")
                    try {
                        cropFile.outputStream().use { out ->
                            check(bmp.compress(Bitmap.CompressFormat.PNG, 100, out))
                        }
                    } finally {
                        bmp.recycle()
                    }
                    cropFile
                } finally {
                    reader.recycle()
                }
            }
            runOnUiThread {
                cropBusy = false
                if (isFinishing || isDestroyed) {
                    output.getOrNull()?.delete()
                    return@runOnUiThread
                }
                output.onSuccess { cropped ->
                    cropSelecting = false
                    canvas.setCropMode(false)
                    findViewById<MaterialButton>(R.id.runCrop).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.cropMode).alpha = 1f
                    OcrFlow.run(this, listOf(Uri.fromFile(cropped))) { cropped.delete() }
                }.onFailure {
                    Toast.makeText(this, it.message ?: getString(R.string.read_failed),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        cropExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", currentPage)
        super.onSaveInstanceState(outState)
    }

    companion object { const val EXTRA_ID = "history_id" }
}
