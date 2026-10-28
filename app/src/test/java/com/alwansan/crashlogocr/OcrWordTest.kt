package com.alwansan.crashlogocr

import org.junit.Assert.*
import org.junit.Test

class OcrWordTest {
    @Test fun rejectsInvalidWordCoordinates() {
        assertFalse(OcrWord("", 0f, 0f, 0.3f, 0.4f).valid)
        assertFalse(OcrWord("java", 0.8f, 0f, 0.2f, 0.4f).valid)
        assertFalse(OcrWord("java", -0.1f, 0f, 0.2f, 0.4f).valid)
        assertTrue(OcrWord("java", 0.1f, 0.1f, 0.2f, 0.4f).valid)
    }

    @Test fun outputRetainsOriginalTextWhenGeometryIsAdded() {
        val line = OcrLine("java.lang.NoClassDefFoundError")
        val word = OcrWord("java.lang.NoClassDefFoundError", 0.1f, 0.2f, 0.8f, 0.24f)
        val output = OcrOutput(listOf(line), 1080, listOf(word))
        assertEquals(line.text, output.rawText)
        assertEquals(1, output.words.size)
    }
}
