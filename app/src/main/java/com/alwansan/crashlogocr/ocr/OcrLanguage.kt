package com.alwansan.crashlogocr.ocr

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Latin crash logs use ML Kit; Arabic and mixed scripts use locally stored Tesseract models. */
enum class OcrLanguage(val tessCode: String) {
    ENGLISH("eng"),
    ARABIC("ara"),
    BILINGUAL("ara+eng");

    fun requiredPacks(): List<String> = if (this == BILINGUAL) listOf("ara", "eng")
        else listOf(tessCode)
}

object OcrLanguagePreferences {
    private const val PREF = "settings"
    fun get(context: Context): OcrLanguage {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("ocr_language", null)
        val automatic = if (context.resources.configuration.locales[0].language == "ar")
            OcrLanguage.BILINGUAL else OcrLanguage.ENGLISH
        return OcrLanguage.entries.firstOrNull { it.name == saved } ?: automatic
    }
    fun set(context: Context, language: OcrLanguage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString("ocr_language", language.name).apply()
    }
}

/**
 * Arabic/English traineddata stored in app-private files/tessdata.
 * A bundled, offline model is installed on first use. Explicit user-requested refresh downloads
 * from the official tessdata_fast repository over HTTPS and replaces only after init validation.
 * No OCR screenshot data ever leaves the device.
 */
object TessModels {
    private val allowed = setOf("ara", "eng")
    private const val MAX_BYTES = 35L * 1024 * 1024
    private const val MIN_BYTES = 100_000L
    private val lock = Any()

    fun available(context: Context, lang: String): Boolean {
        require(lang in allowed)
        val onDisk = File(context.filesDir, "tessdata/$lang.traineddata")
        if (onDisk.length() >= MIN_BYTES) return true
        return try {
            context.assets.open("tessdata/$lang.traineddata").use { it.available() >= MIN_BYTES }
        } catch (_: Exception) {
            false
        }
    }

    fun ensureBundled(context: Context, packs: List<String>) = synchronized(lock) {
        val dir = File(context.filesDir, "tessdata")
        check(dir.isDirectory || dir.mkdirs()) { "Could not create OCR model directory" }
        for (name in packs) {
            require(name in allowed) { "Unsupported OCR model" }
            val file = File(dir, "$name.traineddata")
            if (file.length() >= MIN_BYTES) continue
            val tmp = File(dir, "$name.traineddata.part")
            try {
                context.assets.open("tessdata/$name.traineddata").use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                check(tmp.length() in MIN_BYTES..MAX_BYTES) { "Invalid bundled model size" }
                check(tmp.renameTo(file)) { "Could not install bundled OCR model" }
            } catch (e: Exception) {
                tmp.delete()
                throw IllegalStateException("OCR model $name is not installed; open Settings to download it", e)
            }
        }
    }

    /** Called only when user explicitly requests a language-pack refresh in Settings. */
    fun refresh(context: Context, lang: String) = synchronized(lock) {
        require(lang in allowed)
        val root = File(context.filesDir, "model-check-$lang")
        val tempData = File(root, "tessdata")
        check(tempData.isDirectory || tempData.mkdirs()) { "Unable to create temporary model directory" }
        val tmp = File(tempData, "$lang.traineddata")
        val url = URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$lang.traineddata")
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = false
        try {
            check(connection.responseCode == 200) { "Model download failed (HTTP ${connection.responseCode})" }
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var size = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        check(size <= MAX_BYTES) { "OCR model too large" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(tmp.length() >= MIN_BYTES) { "OCR model is incomplete" }
            val probe = com.googlecode.tesseract.android.TessBaseAPI()
            try {
                check(probe.init(root.absolutePath, lang)) { "OCR model failed validation" }
            } finally {
                probe.recycle()
            }
            val targetDir = File(context.filesDir, "tessdata")
            check(targetDir.isDirectory || targetDir.mkdirs())
            val target = File(targetDir, "$lang.traineddata")
            val backup = File(targetDir, "$lang.traineddata.bak")
            backup.delete()
            if (target.exists()) check(target.renameTo(backup)) { "Could not preserve previous model" }
            if (!tmp.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("Could not install new model")
            }
            backup.delete()
        } finally {
            connection.disconnect()
            tmp.delete()
            tempData.delete()
            root.delete()
        }
    }
}
