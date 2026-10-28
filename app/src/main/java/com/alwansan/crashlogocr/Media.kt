package com.alwansan.crashlogocr

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.util.concurrent.Executors

data class GalleryImage(val id: Long, val uri: Uri, val name: String, val isScreenshot: Boolean)

object MediaAccess {
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** True for full access AND for Android 14 "selected photos only" access. */
    fun hasAccess(c: Context): Boolean {
        fun ok(p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 34 -> ok(Manifest.permission.READ_MEDIA_IMAGES) ||
                ok(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> ok(Manifest.permission.READ_MEDIA_IMAGES)
            else -> ok(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun isPartial(c: Context): Boolean = Build.VERSION.SDK_INT >= 34 &&
        c.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED &&
        c.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    fun request(a: Activity, code: Int) = a.requestPermissions(permissions(), code)

    /** Newest images first. Screenshots are detected by file name or folder (works on MIUI/DCIM/Screenshots). */
    fun query(c: Context, limit: Int = 800): List<GalleryImage> {
        val out = ArrayList<GalleryImage>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
        c.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sort)?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cur.moveToNext() && out.size < limit) {
                val id = cur.getLong(idCol)
                val name = cur.getString(nameCol).orEmpty()
                val bucket = cur.getString(bucketCol).orEmpty()
                val shot = name.contains("screenshot", true) || bucket.contains("screenshot", true) ||
                    name.contains("screen_", true) || bucket.contains("لقطات", true)
                out += GalleryImage(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), name, shot)
            }
        }
        return out
    }

    fun latestScreenshot(c: Context): Uri? = query(c, 400).firstOrNull { it.isScreenshot }?.uri
}

/** Small memory-cached thumbnail loader (no external image library). */
object Thumbs {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }

    fun load(resolver: ContentResolver, image: GalleryImage, view: ImageView) {
        view.tag = image.id
        cache.get(image.id)?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        pool.execute {
            val bmp = decode(resolver, image.uri) ?: return@execute
            cache.put(image.id, bmp)
            main.post { if (view.tag == image.id) view.setImageBitmap(bmp) }
        }
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { return resolver.loadThumbnail(uri, Size(320, 320), null) }
        }
        return runCatching {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
            var s = 1
            while (b.outWidth / (s * 2) >= 320) s *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }
        }.getOrNull()
    }
}
