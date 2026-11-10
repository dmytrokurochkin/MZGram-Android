package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramShortcutActivity

class MZGramStaticShortcutsTest {

    @Test
    fun shortcutActions_constants() {
        assertEquals("org.telegram.messenger.mzgram.ACTION_TOGGLE_GHOST_MODE", MZGramShortcutActivity.ACTION_TOGGLE_GHOST_MODE)
        assertEquals("org.telegram.messenger.mzgram.ACTION_SCAN_QR", MZGramShortcutActivity.ACTION_SCAN_QR)
    }

    @Test
    fun ghostModeToggle_togglesState() {
        val original = MZGramConfig.ghostMode
        try {
            MZGramConfig.ghostMode = false
            assertFalse(MZGramConfig.ghostMode)

            MZGramConfig.toggleGhostMode()
            assertTrue(MZGramConfig.ghostMode)

            MZGramConfig.toggleGhostMode()
            assertFalse(MZGramConfig.ghostMode)
        } finally {
            MZGramConfig.ghostMode = original
        }
    }
}
