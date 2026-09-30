package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramGhostMode

class MZGramConfigTest {

    // Toggle, then force-reload from SharedPreferences (as if the app had
    // restarted) to prove the value round-trips through disk, not just the
    // in-memory field.
    @Test
    fun ghostModeTogglePersistsAcrossReload() {
        val before = MZGramConfig.ghostMode
        try {
            MZGramConfig.toggleGhostMode()
            assertNotEquals(before, MZGramConfig.ghostMode)
            val afterToggle = MZGramConfig.ghostMode

            MZGramConfig.loadConfig(true)
            assertEquals(afterToggle, MZGramConfig.ghostMode)
            assertEquals(afterToggle, MZGramGhostMode.isEnabled())
        } finally {
            if (MZGramConfig.ghostMode != before) {
                MZGramConfig.toggleGhostMode()
            }
        }
    }

    @Test
    fun ghostSilentSendTogglePersistsAcrossReload() {
        val before = MZGramConfig.ghostSilentSend
        try {
            MZGramConfig.toggleGhostSilentSend()
            assertNotEquals(before, MZGramConfig.ghostSilentSend)
            val afterToggle = MZGramConfig.ghostSilentSend

            MZGramConfig.loadConfig(true)
            assertEquals(afterToggle, MZGramConfig.ghostSilentSend)
        } finally {
            if (MZGramConfig.ghostSilentSend != before) {
                MZGramConfig.toggleGhostSilentSend()
            }
        }
    }

    @Test
    fun trackedDialogsAddAndRemoveRoundTrip() {
        val dialogId = -9_999_999_999L
        assertEquals(false, MZGramConfig.isDialogTracked(dialogId))
        try {
            MZGramConfig.setDialogTracked(dialogId, true)
            assertEquals(true, MZGramConfig.isDialogTracked(dialogId))
            assertEquals(true, MZGramConfig.getTrackedDialogs().contains(dialogId))
        } finally {
            MZGramConfig.setDialogTracked(dialogId, false)
        }
        assertEquals(false, MZGramConfig.isDialogTracked(dialogId))
    }

    @Test
    fun historyTotalMediaCapMbClampsNegativeToZero() {
        val before = MZGramConfig.historyTotalMediaCapMb
        try {
            MZGramConfig.setHistoryTotalMediaCapMb(-50)
            assertEquals(0, MZGramConfig.historyTotalMediaCapMb)
        } finally {
            MZGramConfig.setHistoryTotalMediaCapMb(before)
        }
    }
}
