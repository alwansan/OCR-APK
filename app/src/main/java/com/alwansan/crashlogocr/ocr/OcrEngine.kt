package com.alwansan.crashlogocr.ocr

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.alwansan.crashlogocr.OcrLine
import com.alwansan.crashlogocr.OcrOutput
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/** On-device recognition. Handles one or many images; tall images are tiled automatically. */
object OcrEngine {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Callback always runs on the main thread. */
    fun recognize(context: Context, uris: List<Uri>, callback: (Result<OcrOutput>) -> Unit) {
        val app = context.applicationContext
        require(uris.size <= 12) { "Select up to 12 images" }
        worker.execute {
            val result = runCatching { recognizeBlocking(app, uris) }
            main.post { callback(result) }
        }
    }

    private fun recognizeBlocking(context: Context, uris: List<Uri>): OcrOutput {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val all = mutableListOf<OcrLine>()
            var width = 0
            var yBase = 0
            for (uri in uris) {
                var maxBottom = 0
                val w = ImageTiler.forEachTile(context, uri) { tile ->
                    val text = Tasks.await(recognizer.process(InputImage.fromBitmap(tile.bitmap, 0)))
                    val picked = mutableListOf<OcrLine>()
                    for (block in text.textBlocks) for (line in block.lines) {
                        val box = line.boundingBox ?: continue
                        val centre = tile.top + (box.top + box.bottom) / 2
                        if (centre < tile.ownLo || centre >= tile.ownHi) continue
                        picked += OcrLine(
                            line.text, box.left, yBase + tile.top + box.top,
                            box.right, yBase + tile.top + box.bottom
                        )
                    }
                    all += picked
                    maxBottom = maxOf(maxBottom, tile.top + tile.bitmap.height)
                }
                if (width == 0) width = w
                yBase += maxBottom + 40
            }
            return OcrOutput(all.sortedWith(compareBy({ it.top }, { it.left })), width)
        } finally {
            recognizer.close()
        }
    }
}
