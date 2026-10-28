package com.alwansan.crashlogocr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Read-only on-image OCR. Default drag PANS, tap toggles a word, long press or
 * explicit Select mode extends the range. A draggable right-hand scrollbar
 * jumps through tall screenshots, and a fling keeps moving after finger-up.
 * Never opens an IME. Only visible image regions are decoded into bitmaps.
 */
@Suppress("DEPRECATION")
class LiveTextImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val dp = resources.displayMetrics.density
    private val cfg = ViewConfiguration.get(context)
    private val slop = cfg.scaledTouchSlop.toFloat()
    private val minFling = cfg.scaledMinimumFlingVelocity.toFloat()
    private val scroller = OverScroller(context)
    private var tracker: VelocityTracker? = null

    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(104, 163, 255) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(72, 140, 255); style = Paint.Style.STROKE; strokeWidth = 2 * dp
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 145, 250) }
    private val caution = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 252, 178, 42) }
    private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 200, 40); style = Paint.Style.STROKE; strokeWidth = 2 * dp
    }
    private val scrollTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 160, 171, 189) }
    private val scrollThumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(127, 166, 251) }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var reader: BitmapRegionDecoder? = null
    // Region decoding on the UI thread caused stutter every time the image moved
    // beyond its cached tile. Decode regions off-main, then swap the bitmap on main.
    private val readerLock = Any()
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var decodeRunning = false
    private var decodeEpoch = 0L
    private val failedRegion = Rect()
    private var failedSample = 0
    private var cache: Bitmap? = null
    private val cacheRect = Rect()
    private var cacheSample = 0
    private var iw = 1
    private var ih = 1
    private var words: List<OcrWord> = emptyList()
    private val selected = linkedSetOf<Int>()

    private var baseScale = 1f
    private var zoom = 1f
    private var shiftX = 0f
    private var shiftY = 0f
    private var showUncertain = true
    private var selectMode = false
    private var cropMode = false
    private var cropA: PointF? = null
    private var cropB: PointF? = null

    private enum class Gesture { IDLE, PENDING, PAN, SELECT, HANDLE, CROP, SCRUB, PINCH }
    private var gesture = Gesture.IDLE
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var firstWord = -1
    private var fixedWord = -1
    private var moved = false
    private val longPress = Runnable {
        if (gesture == Gesture.PENDING && firstWord >= 0) {
            gesture = Gesture.SELECT
            setRange(firstWord, firstWord)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }

    var onSelectionChanged: ((String, Int) -> Unit)? = null
    val isSelectionMode get() = selectMode

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            gesture = Gesture.PINCH
            removeCallbacks(longPress)
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(baseScale * 0.70f, baseScale * 7f)
            val ratio = zoom / old
            shiftX = detector.focusX - (detector.focusX - shiftX) * ratio
            shiftY = detector.focusY - (detector.focusY - shiftY) * ratio
            clamp()
            invalidate()
            return true
        }
    })

    fun setImage(file: File, detected: List<OcrWord>) {
        releaseImage()
        synchronized(readerLock) {
            reader = BitmapRegionDecoder.newInstance(file.absolutePath, false)
                ?: error("Unable to open screenshot")
        }
        iw = reader!!.width; ih = reader!!.height
        words = detected.filter { it.valid }
        selected.clear()
        cropMode = false; cropA = null; cropB = null
        if (width > 0 && height > 0) resetView()
        notifySelection()
        invalidate()
    }

    fun setSelectionMode(enabled: Boolean) {
        selectMode = enabled
        gesture = Gesture.IDLE
        removeCallbacks(longPress)
        invalidate()
    }

    fun clearSelection() { selected.clear(); notifySelection(); invalidate() }
    fun selectAll() {
        selected.clear()
        words.indices.forEach { selected.add(it) }
        notifySelection(); invalidate()
    }
    fun selectionSize() = selected.size
    fun wordCount() = words.size
    fun hasConfidenceData() = words.any { it.confidence != null }
    fun toggleUncertain(): Boolean { showUncertain = !showUncertain; invalidate(); return showUncertain }
    fun setCropMode(enabled: Boolean) {
        cropMode = enabled; cropA = null; cropB = null; gesture = Gesture.IDLE; invalidate()
    }
    fun getCropRect(): Rect? {
        val a = cropA ?: return null
        val b = cropB ?: return null
        val rect = Rect(min(a.x, b.x).toInt(), min(a.y, b.y).toInt(),
            max(a.x, b.x).toInt(), max(a.y, b.y).toInt())
        return rect.takeIf { it.width() >= 32 && it.height() >= 32 }
    }
    fun selectedText(): String {
        val b = StringBuilder()
        var previous: OcrWord? = null
        for (i in selected.sorted()) {
            val w = words.getOrNull(i) ?: continue
            previous?.let { p ->
                val height = max(p.bottom - p.top, w.bottom - w.top)
                val centreDelta = abs((w.top + w.bottom - p.top - p.bottom) / 2f)
                b.append(if (centreDelta <= height * 0.65f) " " else "\n")
            }
            b.append(w.text)
            previous = w
        }
        return b.toString()
    }
    private fun notifySelection() { onSelectionChanged?.invoke(selectedText(), selected.size) }

    fun resetView() {
        if (width <= 0 || height <= 0) return
        scroller.forceFinished(true)
        baseScale = ((width - 20f * dp) / iw).coerceAtLeast(0.0001f)
        zoom = baseScale
        shiftX = (width - iw * zoom) / 2
        shiftY = 0f
        clamp(); invalidate()
    }
    private fun clamp() {
        val dw = iw * zoom; val dh = ih * zoom
        shiftX = if (dw <= width) (width - dw) / 2 else shiftX.coerceIn(width - dw, 0f)
        shiftY = if (dh <= height) (height - dh) / 2 else shiftY.coerceIn(height - dh, 0f)
    }
    private fun sourcePoint(x: Float, y: Float) = PointF(
        ((x - shiftX) / zoom).coerceIn(0f, iw.toFloat()),
        ((y - shiftY) / zoom).coerceIn(0f, ih.toFloat())
    )
    private fun setRange(a: Int, b: Int) {
        if (a !in words.indices || b !in words.indices) return
        selected.clear()
        for (i in min(a, b)..max(a, b)) selected.add(i)
        notifySelection(); invalidate()
    }

    private fun wordAt(x: Float, y: Float): Int {
        val p = sourcePoint(x, y)
        val px = p.x / iw; val py = p.y / ih
        val nearX = 3f * dp / zoom / iw
        val nearY = 3f * dp / zoom / ih
        var best = -1; var score = Float.MAX_VALUE
        for (i in words.indices) {
            val w = words[i]
            if (px < w.left - nearX || px > w.right + nearX ||
                py < w.top - nearY || py > w.bottom + nearY) continue
            val dx = px - (w.left + w.right) / 2
            val dy = py - (w.top + w.bottom) / 2
            val dist = dx * dx + dy * dy
            if (dist < score) { score = dist; best = i }
        }
        return best
    }
    private fun handleAnchor(x: Float, y: Float): Int {
        if (selected.size < 2) return -1
        val ordered = selected.sorted()
        for ((index, anchor, leading) in listOf(
            Triple(ordered.first(), ordered.last(), true),
            Triple(ordered.last(), ordered.first(), false))) {
            val w = words[index]
            val hx = (if (leading) w.left else w.right) * iw * zoom + shiftX
            val hy = w.bottom * ih * zoom + shiftY + 8 * dp
            if (abs(x - hx) <= 22 * dp && abs(y - hy) <= 22 * dp) return anchor
        }
        return -1
    }

    private val trackW get() = 28f * dp
    private val barTop get() = 10f * dp
    private val barBottom get() = height - 10f * dp
    private fun scrollable() = ih * zoom > height + 2f
    private fun scrub(y: Float) {
        val span = ih * zoom - height
        if (span <= 0f) return
        val thumb = max(48f * dp, (height * height / (ih * zoom)).coerceAtMost(height - 20f * dp))
        val usable = max(1f, barBottom - barTop - thumb)
        val fraction = ((y - barTop - thumb / 2) / usable).coerceIn(0f, 1f)
        shiftY = -span * fraction
        clamp(); invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (reader == null) return false
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
            scroller.forceFinished(true)
            pinch.onTouchEvent(event)
            tracker?.recycle()
            tracker = VelocityTracker.obtain()
            startX = event.x; startY = event.y
            lastX = event.x; lastY = event.y; moved = false
            firstWord = wordAt(event.x, event.y)
            fixedWord = -1
            gesture = when {
                cropMode -> Gesture.CROP
                scrollable() && event.x >= width - trackW -> Gesture.SCRUB
                else -> {
                    val anchor = handleAnchor(event.x, event.y)
                    if (anchor >= 0) { fixedWord = anchor; Gesture.HANDLE } else Gesture.PENDING
                }
            }
            if (gesture == Gesture.CROP) { cropA = sourcePoint(event.x, event.y); cropB = cropA }
            if (gesture == Gesture.SCRUB) scrub(event.y)
            if (gesture == Gesture.PENDING && firstWord >= 0) postDelayed(longPress, 450L)
            invalidate()
            return true
        }

        pinch.onTouchEvent(event)
        when (action) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                gesture = Gesture.PINCH; removeCallbacks(longPress)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || gesture == Gesture.PINCH) return true
                val x = event.x; val y = event.y
                val dx = x - lastX; val dy = y - lastY
                val distance = max(abs(x - startX), abs(y - startY))
                if (distance > slop) moved = true
                if (gesture == Gesture.PENDING && moved) {
                    removeCallbacks(longPress)
                    gesture = if (selectMode && firstWord >= 0) Gesture.SELECT else Gesture.PAN
                }
                when (gesture) {
                    Gesture.PAN -> {
                        shiftX += dx; shiftY += dy
                        clamp(); invalidate()
                        tracker?.addMovement(event)
                    }
                    Gesture.SELECT -> setRange(firstWord, wordAt(x, y).takeIf { it >= 0 } ?: firstWord)
                    Gesture.HANDLE -> setRange(fixedWord, wordAt(x, y).takeIf { it >= 0 } ?: fixedWord)
                    Gesture.CROP -> { cropB = sourcePoint(x, y); invalidate() }
                    Gesture.SCRUB -> scrub(y)
                    else -> Unit
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (gesture == Gesture.PENDING && !moved && firstWord >= 0) {
                    if (!selected.add(firstWord)) selected.remove(firstWord)
                    notifySelection(); invalidate(); performClick()
                }
                if (gesture == Gesture.PAN) {
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000, cfg.scaledMaximumFlingVelocity.toFloat())
                    val vx = tracker?.xVelocity ?: 0f
                    val vy = tracker?.yVelocity ?: 0f
                    if (max(abs(vx), abs(vy)) > minFling) {
                        val minX = min(0, (width - iw * zoom).toInt())
                        val minY = min(0, (height - ih * zoom).toInt())
                        scroller.fling(shiftX.toInt(), shiftY.toInt(), vx.toInt(), vy.toInt(),
                            minX, 0, minY, 0)
                        postInvalidateOnAnimation()
                    }
                }
                if (gesture == Gesture.CROP) { cropB = sourcePoint(event.x, event.y); invalidate() }
                gesture = Gesture.IDLE
                tracker?.recycle(); tracker = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                tracker?.recycle(); tracker = null
                gesture = Gesture.IDLE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            shiftX = scroller.currX.toFloat()
            shiftY = scroller.currY.toFloat()
            clamp()
            postInvalidateOnAnimation()
        }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && reader != null) resetView() else { clamp(); invalidate() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val decoder = reader ?: return
        if (width <= 0 || height <= 0 || zoom <= 0f) return
        val l = ((-shiftX) / zoom).toInt().coerceIn(0, iw)
        val t = ((-shiftY) / zoom).toInt().coerceIn(0, ih)
        val r = ((width - shiftX) / zoom).toInt().coerceIn(0, iw)
        val b = ((height - shiftY) / zoom).toInt().coerceIn(0, ih)
        if (r > l && b > t) {
            val region = Rect(l, t, r, b)
            var sample = 1
            while (sample < 16 && 1f / sample > zoom * 1.6f) sample *= 2
            if (cache == null || cacheSample != sample || !cacheRect.contains(region)) {
                val padX = max(16, region.width() / 5)
                val padY = max(16, region.height() / 5)
                val needed = Rect(max(0, l - padX), max(0, t - padY),
                    min(iw, r + padX), min(ih, b + padY))
                while (needed.width().toLong() * needed.height() / (sample.toLong() * sample) > 3_000_000L)
                    sample *= 2
                requestDecode(needed, sample)
            }
            cache?.let {
                canvas.drawBitmap(it, null, RectF(
                    cacheRect.left * zoom + shiftX, cacheRect.top * zoom + shiftY,
                    cacheRect.right * zoom + shiftX, cacheRect.bottom * zoom + shiftY), bitmapPaint)
            }
        }

        for ((index, w) in words.withIndex()) {
            val box = RectF(w.left * iw * zoom + shiftX, w.top * ih * zoom + shiftY,
                w.right * iw * zoom + shiftX, w.bottom * ih * zoom + shiftY)
            if (box.right < 0 || box.left > width || box.bottom < 0 || box.top > height) continue
            overlay.alpha = if (index in selected) 112 else 32
            val paint = if (showUncertain && w.confidence != null &&
                w.confidence < 55 && index !in selected) caution else overlay
            canvas.drawRoundRect(box, 3 * dp, 3 * dp, paint)
            if (index in selected) canvas.drawRoundRect(box, 3 * dp, 3 * dp, outline)
        }
        if (selected.isNotEmpty()) {
            val sorted = selected.sorted()
            for ((index, leading) in listOf(sorted.first() to true, sorted.last() to false)) {
                val w = words[index]
                val x = (if (leading) w.left else w.right) * iw * zoom + shiftX
                val y = w.bottom * ih * zoom + shiftY + 8 * dp
                canvas.drawCircle(x, y, 6 * dp, handle)
            }
        }
        val a = cropA; val z = cropB
        if (a != null && z != null) canvas.drawRect(
            min(a.x, z.x) * zoom + shiftX, min(a.y, z.y) * zoom + shiftY,
            max(a.x, z.x) * zoom + shiftX, max(a.y, z.y) * zoom + shiftY, cropPaint)

        if (scrollable()) {
            val thumbH = max(48f * dp, (height * height / (ih * zoom)).coerceAtMost(height - 20 * dp))
            val total = max(1f, barBottom - barTop - thumbH)
            val fraction = (-shiftY / (ih * zoom - height)).coerceIn(0f, 1f)
            val thumbTop = barTop + total * fraction
            val x = width - 9 * dp
            canvas.drawRoundRect(x - 2 * dp, barTop, x + 2 * dp, barBottom,
                3 * dp, 3 * dp, scrollTrack)
            canvas.drawRoundRect(x - 5 * dp, thumbTop, x + 5 * dp, thumbTop + thumbH,
                5 * dp, 5 * dp, scrollThumb)
        }
    }
    private fun requestDecode(rect: Rect, sample: Int) {
        if (decodeRunning) return
        if (failedSample == sample && failedRegion == rect) return
        decodeRunning = true
        val generation = decodeEpoch
        val region = Rect(rect)
        imageExecutor.execute {
            val decoded = runCatching {
                synchronized(readerLock) {
                    reader?.decodeRegion(region, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    })
                }
            }.getOrNull()
            mainHandler.post {
                decodeRunning = false
                if (generation != decodeEpoch || reader == null) {
                    decoded?.recycle()
                } else {
                    if (decoded == null) {
                        failedRegion.set(region)
                        failedSample = sample
                    } else {
                        failedRegion.setEmpty()
                        failedSample = 0
                        cache?.recycle()
                        cache = decoded
                        cacheRect.set(region)
                        cacheSample = sample
                    }
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    fun releaseImage() {
        decodeEpoch++
        failedRegion.setEmpty()
        failedSample = 0
        removeCallbacks(longPress)
        scroller.forceFinished(true)
        tracker?.recycle(); tracker = null
        cache?.recycle(); cache = null; cacheSample = 0
        synchronized(readerLock) { reader?.recycle(); reader = null }
    }
    override fun onDetachedFromWindow() { releaseImage(); imageExecutor.shutdownNow(); super.onDetachedFromWindow() }
}
