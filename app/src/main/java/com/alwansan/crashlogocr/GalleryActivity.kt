package com.alwansan.crashlogocr

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.util.concurrent.Executors

/** In-app gallery over MediaStore: screenshots first, pull to refresh, multi-select (long press). */
class GalleryActivity : AppCompatActivity() {
    private lateinit var grid: RecyclerView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var empty: View
    private lateinit var emptyText: TextView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var shotsChip: Chip
    private val adapter = Adapter()
    private var all: List<GalleryImage> = emptyList()
    private val io = Executors.newSingleThreadExecutor()
    private val selected = LinkedHashSet<Long>()

    private val systemPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) OcrFlow.run(this, uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        grid = findViewById(R.id.grid)
        refresh = findViewById(R.id.refresh)
        empty = findViewById(R.id.empty)
        emptyText = findViewById(R.id.emptyText)
        fab = findViewById(R.id.fab)
        shotsChip = findViewById(R.id.chipShots)

        grid.layoutManager = GridLayoutManager(this, 3)
        grid.adapter = adapter
        refresh.setOnRefreshListener { load() }
        shotsChip.setOnCheckedChangeListener { _, _ -> applyFilter() }
        findViewById<Chip>(R.id.chipAll).setOnCheckedChangeListener { _, _ -> applyFilter() }
        findViewById<MaterialButton>(R.id.grant).setOnClickListener { MediaAccess.request(this, REQ) }
        findViewById<MaterialButton>(R.id.pickerBtn).setOnClickListener {
            systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        fab.setOnClickListener {
            val uris = all.filter { it.id in selected }.map { it.uri }
            OcrFlow.run(this, uris)
        }

        if (MediaAccess.hasAccess(this)) load() else showEmpty(R.string.gallery_need_permission)
    }

    override fun onResume() {
        super.onResume()
        if (MediaAccess.hasAccess(this) && all.isEmpty()) load()
    }

    @Deprecated("Legacy callback keeps minSdk 23 simple")
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ) { if (MediaAccess.hasAccess(this)) load() else showEmpty(R.string.gallery_denied) }
    }

    private fun load() {
        refresh.isRefreshing = true
        io.execute {
            val list = runCatching { MediaAccess.query(this) }.getOrDefault(emptyList())
            runOnUiThread {
                all = list
                refresh.isRefreshing = false
                if (list.none { it.isScreenshot } && shotsChip.isChecked) { findViewById<Chip>(R.id.chipAll).isChecked = true }
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val shots = shotsChip.isChecked
        val shown = if (shots) all.filter { it.isScreenshot } else all
        selected.clear(); updateFab()
        adapter.submit(shown)
        if (shown.isEmpty()) {
            showEmpty(if (all.isEmpty()) R.string.gallery_empty else R.string.gallery_no_screenshots, keepButtons = true)
        } else {
            empty.visibility = View.GONE; grid.visibility = View.VISIBLE
        }
    }

    private fun showEmpty(text: Int, keepButtons: Boolean = false) {
        grid.visibility = View.GONE
        empty.visibility = View.VISIBLE
        emptyText.setText(text)
        findViewById<View>(R.id.grant).visibility =
            if (!keepButtons && !MediaAccess.hasAccess(this)) View.VISIBLE else View.GONE
    }

    private fun updateFab() {
        if (selected.isEmpty()) fab.hide() else {
            fab.text = getString(R.string.extract_n, selected.size); fab.show()
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<GalleryImage> = emptyList()
        fun submit(list: List<GalleryImage>) { items = list; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.thumb)
            val check: View = v.findViewById(R.id.check)
            val dim: View = v.findViewById(R.id.dim)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(layoutInflater.inflate(R.layout.item_thumb, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            Thumbs.load(contentResolver, item, h.img)
            val sel = item.id in selected
            h.check.visibility = if (sel) View.VISIBLE else View.GONE
            h.dim.visibility = if (sel) View.VISIBLE else View.GONE
            h.itemView.setOnClickListener {
                if (selected.isNotEmpty()) toggle(item, pos) else OcrFlow.run(this@GalleryActivity, listOf(item.uri))
            }
            h.itemView.setOnLongClickListener { toggle(item, pos); true }
        }

        private fun toggle(item: GalleryImage, pos: Int) {
            if (!selected.add(item.id)) selected.remove(item.id)
            notifyItemChanged(pos); updateFab()
        }
    }

    companion object { private const val REQ = 77 }
}
