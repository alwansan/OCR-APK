package com.alwansan.crashlogocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesNumericPrereleaseVersions() {
        assertTrue(Updater.isNewer("v2.4.0-dev.109", "2.4.0-dev.99"))
        assertFalse(Updater.isNewer("v2.4.0-dev.99", "2.4.0-dev.109"))
        assertFalse(Updater.isNewer("v2.4.0-dev.110", "2.4.0"))
        assertTrue(Updater.isNewer("v2.4.0", "2.4.0-dev.110"))
    }
    @Test fun comparesSemanticVersions() {
        assertTrue(Updater.isNewer("v2.2.0", "2.1.0"))
        assertFalse(Updater.isNewer("v2.0.0", "2.1.0"))
        assertFalse(Updater.isNewer("v2.1.0-beta.1", "2.1.0"))
        assertFalse(Updater.isNewer("nonsense", "2.1.0"))
    }
}
