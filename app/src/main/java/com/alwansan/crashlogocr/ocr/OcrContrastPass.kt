package com.alwansan.crashlogocr.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * A bounded alternative reading pass. For dark screenshots, white text on black
 * becomes dark text on white, with contrast boosted. The source bitmap is not modified.
 * Never process giant full screenshots here; ImageTiler passes one bounded tile.
 */
internal object OcrContrastPass {
    fun isDark(bitmap: Bitmap): Boolean {
        val dx = maxOf(1, bitmap.width / 24)
        val dy = maxOf(1, bitmap.height / 24)
        var dark = 0
        var total = 0
        var y = dy / 2
        while (y < bitmap.height) {
            var x = dx / 2
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val brightness = (Color.red(color) * 299 +
                    Color.green(color) * 587 + Color.blue(color) * 114) / 1000
                if (brightness < 105) dark++
                total++
                x += dx
            }
            y += dy
        }
        return total > 0 && dark * 100 / total >= 58
    }

    fun create(bitmap: Bitmap, dark: Boolean): Bitmap {
        val dest = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val factor = if (dark) -1.20f else 1.25f
        val offset = if (dark) 280f else -32f
        val transform = ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, offset,
            0f, factor, 0f, 0f, offset,
            0f, 0f, factor, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        Canvas(dest).drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(transform)
        })
        return dest
    }
}
