package com.alwansan.crashlogocr.ocr

import android.content.Context

/** Independent from the Arabic/English UI and OCR language setting. */
enum class OcrAccuracy(val maxVerifyPerTile: Int) {
    FAST(0), BALANCED(5), HIGH(18)
}
object OcrAccuracyPreference {
    fun get(context: Context): OcrAccuracy {
        val name = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("ocr_accuracy", OcrAccuracy.BALANCED.name)
        return OcrAccuracy.entries.firstOrNull { it.name == name } ?: OcrAccuracy.BALANCED
    }
    fun set(context: Context, value: OcrAccuracy) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("ocr_accuracy", value.name).apply()
    }
}
