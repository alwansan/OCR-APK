package com.alwansan.crashlogocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Read-only selectable OCR overlay on the actual screenshot.
 * Source image is region-decoded at screen resolution: tall screenshots need
 * only a small viewport bitmap, NOT a full-resolution allocation.
 * Tap selects/deselects, drag between words selects range, pinch zooms, drag
 * blank background pans. No EditText/IME exists in this view.
 */
@Suppress("DEPRECATION")
class LiveTextImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(106, 165, 255) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(78, 144, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val cropBrush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 200, 46)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val uncertainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 179, 42)
        alpha = 90
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(79, 148, 255) }
    private val viewport = Rect()
    private val cacheRect = Rect()
    private var decoder: BitmapRegionDecoder? = null
    private var cache: Bitmap? = null
    private var cacheSample = 0
    private var imageWidth = 1
    private var imageHeight = 1
    private var words = emptyList<OcrWord>()
    private val selection = linkedSetOf<Int>()
    private var showUncertain = true
    private var cropMode = false
    private var cropStart: PointF? = null
    private var cropEnd: PointF? = null
    private var adjustingHandle = false
    private var baseScale = 1f
    private var zoom = 1f
    private var shiftX = 0f
    private var shiftY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downIndex = -1
    private var dragged = false
    private var scaling = false
    private val slop = 10f * resources.displayMetrics.density
    var onSelectionChanged: ((String, Int) -> Unit)? = null

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val before = zoom
            val next = (before * detector.scaleFactor).coerceIn(baseScale * 0.75f, baseScale * 6f)
            val ratio = next / before
            shiftX = detector.focusX - (detector.focusX - shiftX) * ratio
            shiftY = detector.focusY - (detector.focusY - shiftY) * ratio
            zoom = next
            clamp()
            invalidate()
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = true
            return true
        }
    })

    fun setImage(file: File, detected: List<OcrWord>) {
        releaseImage()
        decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false)
            ?: throw IllegalArgumentException("Cannot open selected screenshot")
        imageWidth = decoder!!.width
        imageHeight = decoder!!.height
        words = detected.filter { it.valid }
        selection.clear()
        cropMode = false
        cropStart = null
        cropEnd = null
        post { resetView(); notifySelection() }
    }

    fun clearSelection() {
        selection.clear()
        notifySelection()
        invalidate()
    }

    fun selectAll() {
        selection.clear()
        words.indices.forEach { selection.add(it) }
        notifySelection()
        invalidate()
    }

    fun selectionSize() = selection.size
    fun hasConfidenceData() = words.any { it.confidence != null }
    fun toggleUncertain(): Boolean {
        showUncertain = !showUncertain
        invalidate()
        return showUncertain
    }
    fun setCropMode(enabled: Boolean) {
        cropMode = enabled
        cropStart = null
        cropEnd = null
        invalidate()
    }
    fun getCropRect(): Rect? {
        val a = cropStart ?: return null
        val b = cropEnd ?: return null
        val l = min(a.x, b.x).toInt().coerceIn(0, imageWidth)
        val t = min(a.y, b.y).toInt().coerceIn(0, imageHeight)
        val r = max(a.x, b.x).toInt().coerceIn(0, imageWidth)
        val bot = max(a.y, b.y).toInt().coerceIn(0, imageHeight)
        return if (r - l >= 32 && bot - t >= 32) Rect(l, t, r, bot) else null
    }
    private fun toSource(x: Float, y: Float) = PointF(
        ((x - shiftX) / zoom).coerceIn(0f, imageWidth.toFloat()),
        ((y - shiftY) / zoom).coerceIn(0f, imageHeight.toFloat())
    )
    fun wordCount() = words.size

    fun selectedText(): String {
        if (selection.isEmpty()) return ""
        val ordered = selection.sorted()
        val out = StringBuilder()
        var previous: OcrWord? = null
        for (idx in ordered) {
            val w = words[idx]
            if (previous != null) {
                val prev = previous
                val height = max(prev.bottom - prev.top, w.bottom - w.top)
                val sameLine = abs((prev.top + prev.bottom) / 2f - (w.top + w.bottom) / 2f) <= height * 0.65f
                out.append(if (sameLine) " " else "\n")
            }
            out.append(w.text)
            previous = w
        }
        return out.toString()
    }

    fun resetView() {
        if (width <= 0 || height <= 0) return
        baseScale = (width - 16f * resources.displayMetrics.density) / imageWidth
        zoom = baseScale
        shiftX = (width - imageWidth * zoom) / 2f
        shiftY = 0f
        clamp()
        invalidate()
    }

    private fun notifySelection() = onSelectionChanged?.invoke(selectedText(), selection.size)

    private fun clamp() {
        if (width <= 0 || height <= 0) return
        val drawWidth = imageWidth * zoom
        val drawHeight = imageHeight * zoom
        shiftX = if (drawWidth <= width) (width - drawWidth) / 2f else shiftX.coerceIn(width - drawWidth, 0f)
        shiftY = if (drawHeight <= height) (height - drawHeight) / 2f else shiftY.coerceIn(height - drawHeight, 0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && decoder != null) resetView() else clamp()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val reader = decoder ?: return
        val left = ((-shiftX) / zoom).toInt().coerceIn(0, imageWidth)
        val top = ((-shiftY) / zoom).toInt().coerceIn(0, imageHeight)
        val right = ((width - shiftX) / zoom).toInt().coerceIn(0, imageWidth)
        val bottom = ((height - shiftY) / zoom).toInt().coerceIn(0, imageHeight)
        if (right <= left || bottom <= top) return
        viewport.set(left, top, right, bottom)

        var sample = 1
        while (sample < 16 && 1f / sample > zoom * 1.55f) sample *= 2
        // Reuse image when only the viewport moves a little; decode a padded region otherwise.
        if (cache == null || cacheSample != sample || !cacheRect.contains(viewport)) {
            val padX = (viewport.width() * 0.18f).toInt()
            val padY = (viewport.height() * 0.18f).toInt()
            val region = Rect(
                (left - padX).coerceAtLeast(0), (top - padY).coerceAtLeast(0),
                (right + padX).coerceAtMost(imageWidth), (bottom + padY).coerceAtMost(imageHeight)
            )
            while ((region.width().toLong() * region.height()) / (sample * sample) > 4_000_000L)
                sample *= 2
            val bitmap = try {
                reader.decodeRegion(region, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            } catch (_: Exception) { null }
            if (bitmap != null) {
                cache?.recycle()
                cache = bitmap
                cacheRect.set(region)
                cacheSample = sample
            }
        }

        cache?.let { bitmap ->
            val dest = RectF(
                cacheRect.left * zoom + shiftX, cacheRect.top * zoom + shiftY,
                cacheRect.right * zoom + shiftX, cacheRect.bottom * zoom + shiftY
            )
            canvas.drawBitmap(bitmap, null, dest, null)
        }

        for ((index, w) in words.withIndex()) {
            val x1 = w.left * imageWidth * zoom + shiftX
            val y1 = w.top * imageHeight * zoom + shiftY
            val x2 = w.right * imageWidth * zoom + shiftX
            val y2 = w.bottom * imageHeight * zoom + shiftY
            if (x2 < 0 || x1 > width || y2 < 0 || y1 > height) continue
            val box = RectF(x1 - 2f, y1 - 1f, x2 + 2f, y2 + 1f)
            marker.alpha = if (index in selection) 118 else 32
            canvas.drawRoundRect(box, 3f, 3f,
                if (showUncertain && w.confidence != null && w.confidence < 55 && index !in selection)
                    uncertainPaint else marker)
            if (index in selection) canvas.drawRoundRect(box, 3f, 3f, outline)
        }
        if (selection.isNotEmpty()) {
            val indices = selection.sorted()
            for ((first, last) in listOf(indices.first() to true, indices.last() to false)) {
                val w = words[first]
                val hx = (if (last) w.left else w.right) * imageWidth * zoom + shiftX
                val hy = w.bottom * imageHeight * zoom + shiftY
                canvas.drawCircle(hx, hy + 9f * resources.displayMetrics.density,
                    6f * resources.displayMetrics.density, handlePaint)
            }
        }
        val a = cropStart
        val b = cropEnd
        if (a != null && b != null) {
            canvas.drawRect(RectF(
                min(a.x, b.x) * zoom + shiftX, min(a.y, b.y) * zoom + shiftY,
                max(a.x, b.x) * zoom + shiftX, max(a.y, b.y) * zoom + shiftY
            ), cropBrush)
        }
    }

    private fun handleAnchor(x: Float, y: Float): Int {
        if (selection.size < 2) return -1
        val indices = selection.sorted()
        val radius = 22f * resources.displayMetrics.density
        for ((handleIndex, anchoredIndex, leftSide) in listOf(
            Triple(indices.first(), indices.last(), true),
            Triple(indices.last(), indices.first(), false))) {
            val w = words[handleIndex]
            val hx = (if (leftSide) w.left else w.right) * imageWidth * zoom + shiftX
            val hy = w.bottom * imageHeight * zoom + shiftY + 9f * resources.displayMetrics.density
            if (abs(x - hx) < radius && abs(y - hy) < radius) return anchoredIndex
        }
        return -1
    }

    private fun wordAt(x: Float, y: Float): Int {
        if (zoom <= 0f) return -1
        val px = (x - shiftX) / (zoom * imageWidth)
        val py = (y - shiftY) / (zoom * imageHeight)
        val nearX = 8f * resources.displayMetrics.density / zoom / imageWidth
        val nearY = 8f * resources.displayMetrics.density / zoom / imageHeight
        return words.indexOfLast { w ->
            px >= w.left - nearX && px <= w.right + nearX &&
                py >= w.top - nearY && py <= w.bottom + nearY
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (decoder == null) return false
        if (cropMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    cropStart = toSource(event.x, event.y)
                    cropEnd = cropStart
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    if (event.pointerCount == 1) cropEnd = toSource(event.x, event.y)
                    if (event.actionMasked == MotionEvent.ACTION_UP)
                        parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            invalidate()
            return true
        }
        pinch.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                val anchor = handleAnchor(event.x, event.y)
                adjustingHandle = anchor >= 0
                downIndex = if (adjustingHandle) anchor else wordAt(event.x, event.y)
                dragged = adjustingHandle
                scaling = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                scaling = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || scaling) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > slop || abs(dy) > slop) dragged = true
                if (dragged) {
                    if (downIndex >= 0) {
                        val last = wordAt(event.x, event.y)
                        if (last >= 0) {
                            selection.clear()
                            (min(downIndex, last)..max(downIndex, last)).forEach { selection.add(it) }
                            notifySelection()
                        }
                    } else {
                        shiftX += dx
                        shiftY += dy
                        clamp()
                    }
                    invalidate()
                }
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!scaling && !dragged && downIndex >= 0) {
                    if (!selection.add(downIndex)) selection.remove(downIndex)
                    notifySelection()
                    invalidate()
                    performClick()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun releaseImage() {
        cache?.recycle()
        cache = null
        cacheSample = 0
        decoder?.recycle()
        decoder = null
    }

    override fun onDetachedFromWindow() {
        releaseImage()
        super.onDetachedFromWindow()
    }
}
