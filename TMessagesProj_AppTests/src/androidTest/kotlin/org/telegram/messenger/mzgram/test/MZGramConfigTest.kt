package org.telegram.messenger.mzgram.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramGhostMode

class MZGramConfigTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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

    // Saving deleted and edited messages is on by default, for every chat:
    // the old allowlist and the old switch's stored "off" are dropped.
    @Test
    fun saveMessageHistory_isOnByDefault_withoutAChatList() {
        val preferences = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
        val before = MZGramConfig.saveMessageHistory
        try {
            preferences.edit()
                .remove("saveDeletedAndEdited")
                .putBoolean("saveMessageHistory", false)
                .putString("historyTrackedDialogs", "123,456")
                .commit()
            MZGramConfig.loadConfig(true)
            assertEquals(true, MZGramConfig.saveMessageHistory)
            assertEquals(false, preferences.contains("saveMessageHistory"))
            assertEquals(false, preferences.contains("historyTrackedDialogs"))
            assertEquals("no total size cap by default", 0, preferences.getInt("historyTotalMediaCapMb", 0))
        } finally {
            if (MZGramConfig.saveMessageHistory != before) {
                MZGramConfig.toggleSaveMessageHistory()
            }
        }
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
