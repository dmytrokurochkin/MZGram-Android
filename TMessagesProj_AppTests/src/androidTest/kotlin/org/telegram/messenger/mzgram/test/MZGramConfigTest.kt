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
    private val archiveKeys = listOf(
        "saveDeletedMessages", "saveEditHistory", "saveArchiveMedia", "saveFormatting",
        "saveReactions", "saveForBots", "semiTransparentDeleted", "deletedMark", "editedMark",
        "saveDeletedAndEdited", "saveMessageHistory", "historyTrackedDialogs",
    )

    private fun withArchivePreferences(block: (android.content.SharedPreferences) -> Unit) {
        val preferences = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
        val saved = preferences.all.filterKeys { it in archiveKeys }
        try {
            val editor = preferences.edit()
            archiveKeys.forEach { editor.remove(it) }
            editor.commit()
            block(preferences)
        } finally {
            val editor = preferences.edit()
            archiveKeys.forEach { editor.remove(it) }
            saved.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.commit()
            MZGramConfig.loadConfig(true)
        }
    }

    // Settings > MZGram > Archive: every part has its own switch, all on by
    // default, with the broom and pencil marks, for every chat (no list).
    @Test
    fun archiveSwitches_areOnByDefault_withoutAChatList() = withArchivePreferences { preferences ->
        preferences.edit()
            .putBoolean("saveMessageHistory", false)
            .putString("historyTrackedDialogs", "123,456")
            .commit()
        MZGramConfig.loadConfig(true)
        assertEquals(true, MZGramConfig.saveDeletedMessages)
        assertEquals(true, MZGramConfig.saveEditHistory)
        assertEquals(true, MZGramConfig.saveArchiveMedia)
        assertEquals(true, MZGramConfig.saveFormatting)
        assertEquals(true, MZGramConfig.saveReactions)
        assertEquals(true, MZGramConfig.saveForBots)
        assertEquals(true, MZGramConfig.semiTransparentDeleted)
        assertEquals("\uD83E\uDDF9", MZGramConfig.deletedMark)
        assertEquals("\u270F\uFE0F", MZGramConfig.editedMark)
        assertEquals(false, preferences.contains("saveMessageHistory"))
        assertEquals(false, preferences.contains("historyTrackedDialogs"))
    }

    // The one switch of the previous version carries over to both.
    @Test
    fun oldArchiveSwitchOff_turnsOffDeletedAndEdited() = withArchivePreferences { preferences ->
        preferences.edit().putBoolean("saveDeletedAndEdited", false).commit()
        MZGramConfig.loadConfig(true)
        assertEquals(false, MZGramConfig.saveDeletedMessages)
        assertEquals(false, MZGramConfig.saveEditHistory)
        assertEquals(false, preferences.contains("saveDeletedAndEdited"))
        assertEquals(false, preferences.getBoolean("saveDeletedMessages", true))
        assertEquals(false, preferences.getBoolean("saveEditHistory", true))
    }

    @Test
    fun archiveMarks_persistAcrossReload() = withArchivePreferences {
        MZGramConfig.setDeletedMark("[x]")
        MZGramConfig.setEditedMark("")
        MZGramConfig.loadConfig(true)
        assertEquals("[x]", MZGramConfig.deletedMark)
        assertEquals("", MZGramConfig.editedMark)
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

    // Hiding the own online status and phone number is left to Telegram's
    // own privacy settings: no such switches, and the old keys go.
    @Test
    fun privacyDuplicates_areGone() {
        val preferences = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
        preferences.edit()
            .putBoolean("hideOwnOnlineStatus", true)
            .putInt("savedLastSeenPrivacyState", 1)
            .putBoolean("hideOwnPhoneNumber", true)
            .commit()
        MZGramConfig.loadConfig(true)
        assertEquals(false, preferences.contains("hideOwnOnlineStatus"))
        assertEquals(false, preferences.contains("savedLastSeenPrivacyState"))
        assertEquals(false, preferences.contains("hideOwnPhoneNumber"))
        val fields = MZGramConfig::class.java.declaredFields.map { it.name }
        assertEquals(emptyList<String>(), fields.filter { it.startsWith("hideOwn") || it == "savedLastSeenPrivacyState" })
    }
}
