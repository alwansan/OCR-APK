package com.alwansan.crashlogocr

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class SquareFrameLayout @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : FrameLayout(c, a) {
    override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, w)
}
