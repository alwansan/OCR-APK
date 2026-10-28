package com.alwansan.crashlogocr

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.text.method.KeyListener
import android.widget.ImageButton
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.util.TypedValue
import android.view.View
import com.alwansan.crashlogocr.ocr.OcrLanguage
import com.alwansan.crashlogocr.ocr.OcrLanguagePreferences
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.appbar.MaterialToolbar

class ResultActivity : AppCompatActivity() {
    private enum class Mode { CLEAN, COMPACT, RAW }

    private lateinit var editor: EditText
    private lateinit var editEye: ImageButton
    private var originalKeyListener: KeyListener? = null
    private var editingAllowed = true
    private lateinit var headline: TextView
    private lateinit var cause: TextView
    private lateinit var chips: ChipGroup
    private lateinit var item: HistoryItem
    private var clean = ""
    private var raw = ""
    private var mode = Mode.CLEAN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val found = HistoryStore.get(this, id)
        if (found == null) { finish(); return }
        item = found
        clean = item.clean
        raw = item.raw

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        editor = findViewById(R.id.editor)
        originalKeyListener = editor.keyListener
        editingAllowed = Prefs.editEnabled(this)
        editEye = findViewById(R.id.editEye)
        editEye.setOnClickListener {
            editingAllowed = !editingAllowed
            Prefs.setEditEnabled(this, editingAllowed)
            applyEditingMode()
            toast(if (editingAllowed) R.string.editing_on_message else R.string.editing_off_message)
        }
        headline = findViewById(R.id.headline)
        cause = findViewById(R.id.cause)
        chips = findViewById(R.id.chips)

        val wrap = findViewById<Chip>(R.id.wrapChip)
        wrap.isChecked = Prefs.wrapLines(this)
        editor.setHorizontallyScrolling(!wrap.isChecked)
        wrap.setOnCheckedChangeListener { _, on ->
            Prefs.setWrapLines(this, on)
            editor.setHorizontallyScrolling(!on)
        }

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.modeToggle)
        toggle.check(R.id.modeClean)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            saveEdits()
            mode = when (checkedId) { R.id.modeCompact -> Mode.COMPACT; R.id.modeRaw -> Mode.RAW; else -> Mode.CLEAN }
            render()
        }

        findViewById<MaterialButton>(R.id.copyBtn).setOnClickListener { copy(current()) }
        findViewById<MaterialButton>(R.id.shareBtn).setOnClickListener { share(current()) }
        findViewById<MaterialButton>(R.id.reclean).setOnClickListener {
            raw = if (mode == Mode.RAW) editor.text.toString() else raw
            clean = TraceCleaner.clean(raw)
            toggle.check(R.id.modeClean); mode = Mode.CLEAN
            render(); summarize()
        }
        render(); summarize()
    }

    private fun isDark() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun saveEdits() {
        when (mode) {
            Mode.CLEAN -> clean = editor.text.toString()
            Mode.RAW -> raw = editor.text.toString()
            Mode.COMPACT -> {}
        }
    }

    private fun current(): String = when (mode) {
        Mode.CLEAN -> editor.text.toString()
        Mode.RAW -> editor.text.toString()
        Mode.COMPACT -> TraceAnalyzer.compact(clean)
    }

    private fun render() {
        val isArabicDocument = OcrLanguagePreferences.get(this) != OcrLanguage.ENGLISH &&
            !TraceAnalyzer.analyze(clean).hasError
        editor.textDirection = if (isArabicDocument) View.TEXT_DIRECTION_FIRST_STRONG
            else View.TEXT_DIRECTION_LTR
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isArabicDocument) 16f else 13.5f)
        when (mode) {
            Mode.CLEAN -> editor.setText(Highlighter.apply(this, clean, isDark()))
            Mode.COMPACT -> editor.setText(Highlighter.apply(this, TraceAnalyzer.compact(clean), isDark()))
            Mode.RAW -> editor.setText(raw)
        }
        applyEditingMode()
    }

    /** A selectable TextView still exposes copy actions, but will never start the IME. */
    private fun applyEditingMode() {
        val writable = editingAllowed && mode != Mode.COMPACT
        if (writable) {
            editor.setTextIsSelectable(false)
            editor.keyListener = originalKeyListener
            editor.setShowSoftInputOnFocus(true)
            editor.isFocusableInTouchMode = true
        } else {
            editor.keyListener = null
            editor.setTextIsSelectable(true)
            editor.setShowSoftInputOnFocus(false)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(editor.windowToken, 0)
        }
        editEye.setImageResource(if (writable) R.drawable.ic_eye_open else R.drawable.ic_eye_closed)
        editEye.contentDescription = getString(
            if (writable) R.string.editing_allowed else R.string.editing_blocked
        )
        // Compact is a generated view, always read-only to avoid discarding text edits.
        editEye.isEnabled = mode != Mode.COMPACT
        editEye.alpha = if (editEye.isEnabled) 1f else 0.45f
    }

    private fun summarize() {
        val r = TraceAnalyzer.analyze(clean)
        val isDocument = OcrLanguagePreferences.get(this) != OcrLanguage.ENGLISH && !r.hasError
        findViewById<View>(R.id.summaryCard).visibility = if (isDocument) View.GONE else View.VISIBLE
        headline.text = if (r.headline.isBlank()) getString(R.string.no_error_found) else r.headline
        cause.text = r.rootCause?.let { getString(R.string.root_cause, it) }.orEmpty()
        cause.visibility = if (r.rootCause == null) android.view.View.GONE else android.view.View.VISIBLE
        chips.removeAllViews()
        fun chip(t: String) = chips.addView(Chip(this).apply { text = t; isClickable = false })
        chip(getString(R.string.n_lines, r.lineCount))
        chip(getString(R.string.n_frames, r.frameCount))
        r.appPackage?.let { chip(it) }
        if (r.suspicious.isNotEmpty()) chip(getString(R.string.n_suspicious, r.suspicious.size))
        if (!r.hasError) chip(getString(R.string.review_needed))
    }

    private fun copy(text: String) {
        if (text.isBlank()) { toast(R.string.empty); return }
        ClipboardUtils.copy(this, text)
        toast(R.string.copied)
    }

    private fun share(text: String) {
        if (text.isBlank()) { toast(R.string.empty); return }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share_text)))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.result_menu, menu); return true
    }

    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        saveEdits()
        when (i.itemId) {
            R.id.copyCompact -> copy(TraceAnalyzer.compact(clean))
            R.id.copyMarkdown -> copy(TraceAnalyzer.markdown(clean))
            R.id.deleteItem -> { HistoryStore.delete(this, item.id); finish() }
            else -> return super.onOptionsItemSelected(i)
        }
        return true
    }

    override fun onPause() {
        saveEdits()
        HistoryStore.update(this, item.id, clean)
        super.onPause()
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    companion object { const val EXTRA_ID = "history_id" }
}
