package com.alwansan.crashlogocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** All recognition happens on-device. Caller must hold a temporary URI read grant. */
object OcrEngine {
    fun recognize(context: Context, uri: Uri, callback: (Result<String>) -> Unit) {
        val input = try {
            InputImage.fromFilePath(context, uri)
        } catch (error: Exception) {
            callback(Result.failure(error))
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(input)
                .addOnSuccessListener { result -> callback(Result.success(result.text)) }
                .addOnFailureListener { error -> callback(Result.failure(error)) }
                .addOnCompleteListener { recognizer.close() }
        } catch (error: Exception) {
            recognizer.close()
            callback(Result.failure(error))
        }
    }
}
