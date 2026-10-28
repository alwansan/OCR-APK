package com.alwansan.crashlogocr.ocr

import org.junit.Assert.*
import org.junit.Test

class LineBoxRecoveryTest {
    @Test fun recoversMissingPrefixWithoutInventingText() {
        val got = LineBoxRecovery.recover("android.app.ActivityThread", 100, 520,
            listOf(LineBoxRecovery.Item("id.app.ActivityThread", 195, 520)))
        assertEquals("andro", got.single().text)
        assertTrue(got.single().right <= 195)
        assertTrue(got.single().left < got.single().right)
    }

    @Test fun recoversMissingTrailingFragment() {
        val got = LineBoxRecovery.recover("android.app.ActivityThread", 0, 490,
            listOf(LineBoxRecovery.Item("android.app.Activity", 0, 380)))
        assertEquals("Thread", got.single().text)
    }

    @Test fun noGuessingOnConflictingElementText() {
        assertTrue(LineBoxRecovery.recover("android.app.Activity", 0, 400,
            listOf(LineBoxRecovery.Item("entirely different", 0, 380))).isEmpty())
    }

    @Test fun wholeLineWhenNoElementBoxes() {
        val got = LineBoxRecovery.recover("at java.lang.Thread.run", 10, 350, emptyList())
        assertEquals("at java.lang.Thread.run", got.single().text)
    }
}
