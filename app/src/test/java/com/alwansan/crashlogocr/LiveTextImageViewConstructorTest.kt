package com.alwansan.crashlogocr

import android.content.Context
import android.util.AttributeSet
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Prevents regression of ImageTextActivity crash: XML inflater uses the two-argument constructor. */
class LiveTextImageViewConstructorTest {
    @Test fun customViewHasXmlInflationConstructor() {
        val constructor = LiveTextImageView::class.java.getConstructor(
            Context::class.java, AttributeSet::class.java
        )
        assertNotNull(constructor)
    }
}
