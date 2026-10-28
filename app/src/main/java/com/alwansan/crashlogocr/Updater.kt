package com.alwansan.crashlogocr

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Manual "check for updates" against GitHub Releases. The app never touches the network otherwise. */
object Updater {
    data class Info(val tag: String, val newer: Boolean, val apkUrl: String?, val pageUrl: String)

    private const val API = "https://api.github.com/repos/alwansan/OCR-APK/releases/latest"
    private val io = Executors.newSingleThreadExecutor()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun check(callback: (Result<Info>) -> Unit) {
        io.execute {
            val r = runCatching {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (c.responseCode == 404) error("no_release")
                    if (c.responseCode != 200) error("http_${c.responseCode}")
                    val j = JSONObject(c.inputStream.bufferedReader().readText())
                    val tag = j.getString("tag_name")
                    var apk: String? = null
                    val assets = j.optJSONArray("assets")
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
                    }
                    Info(tag, isNewer(tag, BuildConfig.VERSION_NAME), apk, j.getString("html_url"))
                } finally { c.disconnect() }
            }
            main.post { callback(r) }
        }
    }

    internal fun isNewer(tag: String, current: String): Boolean {
        val semver = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")
        val candidate = semver.matchEntire(tag.trim()) ?: return false
        val installed = semver.matchEntire(current.trim()) ?: return false
        fun version(m: MatchResult) = (1..3).map { m.groupValues[it].toLongOrNull() ?: -1L }
        val a = version(candidate); val b = version(installed)
        if (a.size != 3 || b.size != 3) return false
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        val preA = candidate.groupValues[4]
        val preB = installed.groupValues[4]
        if (preA.isEmpty()) return preB.isNotEmpty()
        if (preB.isEmpty()) return false
        return preA > preB
    }
}
