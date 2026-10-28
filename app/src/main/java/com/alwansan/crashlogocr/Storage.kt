package com.alwansan.crashlogocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun quickCopy(c: Context) = p(c).getBoolean("quick_copy", true)
    fun setQuickCopy(c: Context, v: Boolean) = p(c).edit().putBoolean("quick_copy", v).apply()
    fun wrapLines(c: Context) = p(c).getBoolean("wrap", false)
    fun setWrapLines(c: Context, v: Boolean) = p(c).edit().putBoolean("wrap", v).apply()
}

object ClipboardUtils {
    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CrashLogOCR", text))
    }
    fun paste(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }
}

data class HistoryItem(val id: Long, val raw: String, val clean: String) {
    val title: String
        get() = clean.lineSequence().firstOrNull { it.isNotBlank() }?.take(90) ?: "—"
}

/** Last results, stored privately on the phone only. */
object HistoryStore {
    private const val KEY = "items"
    private const val MAX = 30
    private fun p(c: Context) = c.getSharedPreferences("history", Context.MODE_PRIVATE)

    @Synchronized fun all(c: Context): List<HistoryItem> {
        val arr = runCatching { JSONArray(p(c).getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { index ->
            runCatching {
                val o = arr.getJSONObject(index)
                HistoryItem(o.getLong("id"), o.getString("raw"), o.getString("clean"))
            }.getOrNull()
        }
    }

    @Synchronized fun get(c: Context, id: Long) = all(c).firstOrNull { it.id == id }

    @Synchronized fun add(c: Context, raw: String, clean: String): HistoryItem {
        val item = HistoryItem(System.currentTimeMillis() * 1000 + (System.nanoTime() % 1000), raw, clean)
        save(c, listOf(item) + all(c))
        return item
    }

    @Synchronized fun update(c: Context, id: Long, clean: String) =
        save(c, all(c).map { if (it.id == id) it.copy(clean = clean) else it })

    @Synchronized fun delete(c: Context, id: Long) = save(c, all(c).filterNot { it.id == id })
    @Synchronized fun clear(c: Context) = save(c, emptyList())

    private fun save(c: Context, items: List<HistoryItem>) {
        val arr = JSONArray()
        items.take(MAX).forEach {
            arr.put(JSONObject().put("id", it.id).put("raw", it.raw).put("clean", it.clean))
        }
        p(c).edit().putString(KEY, arr.toString()).apply()
    }
}
