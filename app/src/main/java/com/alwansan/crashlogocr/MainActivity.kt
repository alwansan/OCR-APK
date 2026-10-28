package com.alwansan.crashlogocr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var historyList: RecyclerView
    private lateinit var historyEmpty: TextView
    private lateinit var historyHeader: View
    private val adapter = HistoryAdapter()
    private var pendingLatest = false
    private val io = Executors.newSingleThreadExecutor()

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        if (uris.isNotEmpty()) OcrFlow.run(this, uris)
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) OcrFlow.run(this, uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        historyList = findViewById(R.id.historyList)
        historyEmpty = findViewById(R.id.historyEmpty)
        historyHeader = findViewById(R.id.historyHeader)
        historyList.layoutManager = LinearLayoutManager(this)
        historyList.adapter = adapter
        historyList.isNestedScrollingEnabled = false

        findViewById<MaterialButton>(R.id.btnPhotos).setOnClickListener {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<MaterialButton>(R.id.btnLatest).setOnClickListener { latestScreenshot() }
        findViewById<MaterialButton>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnFiles).setOnClickListener { filePicker.launch(arrayOf("image/*")) }
        findViewById<MaterialButton>(R.id.btnPaste).setOnClickListener {
            val text = ClipboardUtils.paste(this)
            if (text.isBlank()) Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            else {
                val item = HistoryStore.add(this, text, TraceCleaner.clean(text))
                startActivity(Intent(this, ResultActivity::class.java).putExtra(ResultActivity.EXTRA_ID, item.id))
            }
        }
        findViewById<View>(R.id.clearHistory).setOnClickListener {
            HistoryStore.clear(this); refreshHistory()
        }
        if (intent?.action == ACTION_LATEST) latestScreenshot()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_LATEST) latestScreenshot()
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
    }

    private fun refreshHistory() {
        val items = HistoryStore.all(this)
        adapter.submit(items)
        historyEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        historyHeader.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun latestScreenshot() {
        if (!MediaAccess.hasAccess(this)) {
            pendingLatest = true
            MediaAccess.request(this, REQ_LATEST)
            return
        }
        io.execute {
            val uri = runCatching { MediaAccess.latestScreenshot(this) }.getOrNull()
            runOnUiThread {
                if (uri == null) Toast.makeText(this, R.string.no_screenshot, Toast.LENGTH_LONG).show()
                else OcrFlow.run(this, listOf(uri))
            }
        }
    }

    @Deprecated("Legacy callback keeps minSdk 23 simple")
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_LATEST && pendingLatest) {
            pendingLatest = false
            if (MediaAccess.hasAccess(this)) latestScreenshot()
            else Toast.makeText(this, R.string.gallery_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menuSettings -> { showSettings(); true }
        R.id.menuUpdate -> { checkUpdates(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showSettings() {
        val labels = arrayOf(getString(R.string.setting_quick))
        val checked = booleanArrayOf(Prefs.quickCopy(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setMultiChoiceItems(labels, checked) { _, i, on -> if (i == 0) Prefs.setQuickCopy(this, on) }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun checkUpdates() {
        Toast.makeText(this, R.string.checking_updates, Toast.LENGTH_SHORT).show()
        Updater.check { r ->
            r.onSuccess { info ->
                if (info.newer) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.update_available, info.tag))
                        .setMessage(R.string.update_message)
                        .setPositiveButton(R.string.download) { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl ?: info.pageUrl)))
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                } else Toast.makeText(this, R.string.up_to_date, Toast.LENGTH_LONG).show()
            }.onFailure {
                val msg = if (it.message == "no_release") R.string.no_release_yet else R.string.update_failed
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {
        private var items: List<HistoryItem> = emptyList()
        fun submit(l: List<HistoryItem>) { items = l; notifyDataSetChanged() }
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.itemTitle)
            val sub: TextView = v.findViewById(R.id.itemSub)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(layoutInflater.inflate(R.layout.item_history, p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, i: Int) {
            val it = items[i]
            h.title.text = it.title
            h.sub.text = DateUtils.getRelativeTimeSpanString(it.id, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            h.itemView.setOnClickListener { _ ->
                startActivity(Intent(this@MainActivity, ResultActivity::class.java).putExtra(ResultActivity.EXTRA_ID, it.id))
            }
        }
    }

    companion object {
        const val ACTION_LATEST = "com.alwansan.crashlogocr.LATEST"
        private const val REQ_LATEST = 55
    }
}
