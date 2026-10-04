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
        } finally {
            if (MZGramConfig.saveMessageHistory != before) {
                MZGramConfig.toggleSaveMessageHistory()
            }
        }
    }

    // The archive has no per-file size limit and no total quota: there is
    // no setting for either, and the keys an older version saved are
    // dropped on load.
    @Test
    fun archiveMedia_hasNoSizeLimitAndNoQuota() {
        val preferences = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
        preferences.edit()
            .putInt("historyMediaSizeLimitMb", 50)
            .putInt("historyTotalMediaCapMb", 300)
            .commit()
        MZGramConfig.loadConfig(true)
        assertEquals(false, preferences.contains("historyMediaSizeLimitMb"))
        assertEquals(false, preferences.contains("historyTotalMediaCapMb"))
        val limits = MZGramConfig::class.java.declaredFields.map { it.name }
            .filter { it.contains("SizeLimit") || it.contains("MediaCap") }
        assertEquals("no size limit or quota setting", emptyList<String>(), limits)
    }
}
