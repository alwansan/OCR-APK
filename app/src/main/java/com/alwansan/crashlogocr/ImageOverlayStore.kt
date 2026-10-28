package com.alwansan.crashlogocr

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Snapshot only user-selected images, with temporary share grants copied on a worker.
 * Images/word boxes live in app-private storage and are deleted with the history entry.
 */
object ImageOverlayStore {
    private const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 120L * 1024 * 1024
    // Never let 30 saved OCR scans consume gigabytes of phone storage.
    private const val GLOBAL_IMAGE_BUDGET = 300L * 1024 * 1024
    private const val MAX_WORDS = 20_000
    private fun folder(c: Context, id: Long) = File(c.filesDir, "ocr_images/$id")

    data class ImagePage(val index: Int, val file: File, val words: List<OcrWord>)

    fun save(context: Context, id: Long, sources: List<Uri>, words: List<OcrWord>) {
        val folder = folder(context, id)
        if (!folder.exists() && !folder.mkdirs()) return
        var total = 0L
        val writtenPages = mutableListOf<Int>()
        val orientations = mutableMapOf<Int, Int>()
        for ((index, uri) in sources.take(12).withIndex()) {
            val file = File(folder, "page_$index.img")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var size = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            size += n
                            if (size > MAX_IMAGE_BYTES || total + size > MAX_TOTAL_BYTES)
                                error("Image too large to keep for overlay")
                            output.write(buffer, 0, n)
                        }
                        if (size == 0L) error("Empty image")
                        total += size
                    }
                } ?: error("Unable to open image")
                writtenPages += index
                orientations[index] = runCatching {
                    ExifInterface(file.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) {
                file.delete()
            }
        }
        if (writtenPages.isEmpty()) {
            folder.deleteRecursively()
            return
        }
        val root = JSONArray()
        words.asSequence().filter { it.valid && it.imageIndex in writtenPages }
            .take(MAX_WORDS).forEach { original ->
                val w = mapToRawImage(original, orientations[original.imageIndex])
                root.put(JSONObject()
                    .put("page", w.imageIndex)
                    .put("text", w.text)
                    .put("left", w.left.toDouble()).put("top", w.top.toDouble())
                    .put("right", w.right.toDouble()).put("bottom", w.bottom.toDouble())
                    .put("confidence", w.confidence ?: JSONObject.NULL))
            }
        // Atomic write ensures the image metadata isn't left half-written if OCR is interrupted.
        val tmp = File(folder, "words.json.tmp")
        tmp.writeText(root.toString(), Charsets.UTF_8)
        val dst = File(folder, "words.json")
        if (!tmp.renameTo(dst)) {
            dst.delete()
            tmp.renameTo(dst)
        }
        trimOldImages(context, id)
    }

    /**
     * The region viewer shows encoded/raw pixels, whereas ImageTiler rotates OCR
     * pixels when EXIF says 90/180/270. Invert the rotation once at persistence time.
     */
    private fun mapToRawImage(w: OcrWord, orientation: Int?): OcrWord = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> w.copy(
            left = w.top, top = 1f - w.right,
            right = w.bottom, bottom = 1f - w.left
        )
        ExifInterface.ORIENTATION_ROTATE_180 -> w.copy(
            left = 1f - w.right, top = 1f - w.bottom,
            right = 1f - w.left, bottom = 1f - w.top
        )
        ExifInterface.ORIENTATION_ROTATE_270 -> w.copy(
            left = 1f - w.bottom, top = w.left,
            right = 1f - w.top, bottom = w.right
        )
        else -> w
    }

    fun pages(context: Context, id: Long): List<ImagePage> {
        val dir = folder(context, id)
        if (!dir.isDirectory) return emptyList()
        val geometry = runCatching {
            JSONArray(File(dir, "words.json").readText(Charsets.UTF_8))
        }.getOrDefault(JSONArray())
        val words = mutableListOf<OcrWord>()
        for (i in 0 until minOf(geometry.length(), MAX_WORDS)) {
            val w = runCatching {
                val data = geometry.getJSONObject(i)
                OcrWord(data.getString("text"),
                    data.getDouble("left").toFloat(), data.getDouble("top").toFloat(),
                    data.getDouble("right").toFloat(), data.getDouble("bottom").toFloat(),
                    data.getInt("page"),
                    if (data.isNull("confidence")) null else data.optInt("confidence"))
            }.getOrNull()
            if (w != null && w.valid) words += w
        }
        return (0..11).mapNotNull { index ->
            File(dir, "page_$index.img").takeIf { it.isFile && it.length() > 0L }?.let { f ->
                ImagePage(index, f, words.filter { it.imageIndex == index })
            }
        }
    }

    fun hasImages(c: Context, id: Long) =
        (0..11).any { File(folder(c, id), "page_$it.img").isFile }

    fun remove(context: Context, id: Long) {
        folder(context, id).deleteRecursively()
    }

    private fun trimOldImages(context: Context, justSaved: Long) {
        val root = File(context.filesDir, "ocr_images")
        val folders = root.listFiles()?.filter { it.isDirectory } ?: return
        fun bytes(folder: File) = folder.listFiles()?.sumOf { it.length() } ?: 0L
        var total = folders.sumOf { bytes(it) }
        if (total <= GLOBAL_IMAGE_BUDGET) return
        // Old results remain available as text; only old stored images are evicted.
        for (old in folders.sortedBy { it.lastModified() }) {
            if (total <= GLOBAL_IMAGE_BUDGET) break
            if (old.name.toLongOrNull() == justSaved) continue
            val size = bytes(old)
            if (old.deleteRecursively()) total -= size
        }
    }

    fun prune(context: Context, keepIds: Set<Long>) {
        val dir = File(context.filesDir, "ocr_images")
        dir.listFiles()?.filter { it.isDirectory && it.name.toLongOrNull() !in keepIds }
            ?.forEach { it.deleteRecursively() }
    }
}
