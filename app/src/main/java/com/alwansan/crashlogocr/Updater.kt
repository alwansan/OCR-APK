package com.alwansan.crashlogocr

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Manual checks only: find published GitHub APK releases, including feature-branch
 * prereleases. Does not download/install APKs silently.
 */
object Updater {
    data class Info(val tag: String, val newer: Boolean, val apkUrl: String?, val pageUrl: String)
    private const val API = "https://api.github.com/repos/alwansan/OCR-APK/releases?per_page=30"
    private val io = Executors.newSingleThreadExecutor()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun check(callback: (Result<Info>) -> Unit) {
        io.execute {
            val result = runCatching {
                val conn = URL(API).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (conn.responseCode != 200) error("http_${conn.responseCode}")
                    val releases = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                    var found: Info? = null
                    for (index in 0 until releases.length()) {
                        val r = releases.getJSONObject(index)
                        if (r.optBoolean("draft", false)) continue
                        val tag = r.optString("tag_name")
                        val assets = r.optJSONArray("assets") ?: continue
                        var apk: String? = null
                        for (a in 0 until assets.length()) {
                            val asset = assets.getJSONObject(a)
                            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                                apk = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                                break
                            }
                        }
                        if (apk == null) continue
                        val info = Info(tag, isNewer(tag, BuildConfig.VERSION_NAME), apk, r.optString("html_url"))
                        if (found == null || isNewer(tag, found.tag)) found = info
                    }
                    found ?: error("no_release")
                } finally {
                    conn.disconnect()
                }
            }
            main.post { callback(result) }
        }
    }

    internal fun isNewer(tag: String, current: String): Boolean {
        val semver = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")
        val candidate = semver.matchEntire(tag.trim()) ?: return false
        val installed = semver.matchEntire(current.trim()) ?: return false
        val a = (1..3).map { candidate.groupValues[it].toLongOrNull() ?: -1L }
        val b = (1..3).map { installed.groupValues[it].toLongOrNull() ?: -1L }
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        val preA = candidate.groupValues[4]
        val preB = installed.groupValues[4]
        if (preA.isEmpty()) return preB.isNotEmpty()
        if (preB.isEmpty()) return false
        val ca = preA.split('.'); val cb = preB.split('.')
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i].toLongOrNull(); val y = cb[i].toLongOrNull()
            val comparison = when {
                x != null && y != null -> x.compareTo(y)
                x != null -> -1
                y != null -> 1
                else -> ca[i].compareTo(cb[i])
            }
            if (comparison != 0) return comparison > 0
        }
        return ca.size > cb.size
    }
}
