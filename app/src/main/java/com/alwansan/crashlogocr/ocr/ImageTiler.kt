package com.alwansan.crashlogocr.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Cuts a (possibly very tall) screenshot into overlapping horizontal tiles so ML Kit always
 * sees full-resolution text instead of one huge, internally down-scaled bitmap.
 * Each tile reports the vertical range it "owns"; the cut is placed in the middle of the
 * overlap so no line is lost or duplicated.
 */
object ImageTiler {
    private const val TILE = 1800      // source px per tile
    private const val OVERLAP = 220    // source px shared between tiles (>> one text line)
    private const val MAX_FULL_DECODE = 9000

    class Tile(val bitmap: Bitmap, val top: Int, val ownLo: Int, val ownHi: Int)

    /** Returns image width in output pixels. [onTile] runs on the calling (background) thread. */
    fun forEachTile(context: Context, uri: Uri, onTile: (Tile) -> Unit): Int {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        require(w > 0 && h > 0) { "Unreadable image" }

        // Keep original pixels for common screenshots: downsampling can erase dots
        // in filenames, Java class names and punctuation.
        val sample = if (w > 3200) 2 else 1
        val effectiveWidth = w / sample
        val scale = when {
            effectiveWidth < 380 -> 3f
            effectiveWidth < 650 -> 2.5f
            effectiveWidth < 900 -> 2f
            else -> 1f
        }
        // Cap EACH processed tile to roughly 4MP. This keeps upscaling a
        // narrow, tall screenshot safe on devices with limited free RAM.
        val tileHeight = minOf(
            TILE,
            maxOf(650, (4_000_000f / (effectiveWidth * scale * scale)).toInt())
        )

        return if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            @Suppress("DEPRECATION")
            val decoder = resolver.openInputStream(uri)!!.use { BitmapRegionDecoder.newInstance(it, false) }
                ?: error("Cannot open image")
            try {
                val overlap = minOf(OVERLAP, tileHeight / 4)
                val step = (tileHeight - overlap) * sample
                val srcTile = tileHeight * sample
                var y = 0
                var prevHi = Int.MIN_VALUE
                while (true) {
                    val bottom = minOf(y + srcTile, h)
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val raw = decoder.decodeRegion(Rect(0, y, w, bottom), opts) ?: error("decode failed")
                    val bmp = scaled(raw, scale)
                    val outTop = (y / sample * scale).toInt()
                    val last = bottom >= h
                    val nextTop = ((y + step) / sample * scale).toInt()
                    val hi = if (last) Int.MAX_VALUE else (nextTop + outTop + bmp.height) / 2
                    onTile(Tile(bmp, outTop, prevHi, hi))
                    if (bmp !== raw) raw.recycle()
                    bmp.recycle()
                    prevHi = hi
                    if (last) break
                    y += step
                }
            } finally {
                decoder.recycle()
            }
            (w / sample * scale).toInt()
        } else {
            rotatedPath(context, uri, orientation, w, h, scale, tileHeight, onTile)
        }
    }

    private fun rotatedPath(
        context: Context, uri: Uri, orientation: Int, w: Int, h: Int, scale: Float,
        tileHeight: Int, onTile: (Tile) -> Unit
    ): Int {
        var sample = 1
        while (maxOf(w, h) / sample > MAX_FULL_DECODE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("decode failed")
        val deg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val full = if (deg == 0f) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(deg) }, true)
        if (full !== decoded) decoded.recycle()
        val fw = full.width
        val fh = full.height
        val step = tileHeight - minOf(OVERLAP, tileHeight / 4)
        var y = 0
        var prevHi = Int.MIN_VALUE
        while (true) {
            val bottom = minOf(y + tileHeight, fh)
            val piece = Bitmap.createBitmap(full, 0, y, fw, bottom - y)
            val bmp = scaled(piece, scale)
            val outTop = (y * scale).toInt()
            val last = bottom >= fh
            val nextTop = ((y + step) * scale).toInt()
            val hi = if (last) Int.MAX_VALUE else (nextTop + outTop + bmp.height) / 2
            onTile(Tile(bmp, outTop, prevHi, hi))
            if (bmp !== piece) piece.recycle()
            bmp.recycle()
            prevHi = hi
            if (last) break
            y += step
        }
        full.recycle()
        return (fw * scale).toInt()
    }

    private fun scaled(src: Bitmap, scale: Float): Bitmap =
        if (scale == 1f) src
        else Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
}
