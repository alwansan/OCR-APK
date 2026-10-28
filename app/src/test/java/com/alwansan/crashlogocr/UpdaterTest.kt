package com.alwansan.crashlogocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesSemanticVersions() {
        assertTrue(Updater.isNewer("v2.2.0", "2.1.0"))
        assertFalse(Updater.isNewer("v2.0.0", "2.1.0"))
        assertFalse(Updater.isNewer("v2.1.0-beta.1", "2.1.0"))
        assertFalse(Updater.isNewer("nonsense", "2.1.0"))
    }
}
