package org.telegram.messenger.mzgram.test

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramSettingsBackup
import org.telegram.ui.mzgram.MZGramDrawer
import org.telegram.ui.mzgram.MZGramFabShape
import org.telegram.ui.mzgram.MZGramDoubleTap
import org.telegram.ui.mzgram.MZGramStickers

// The local backup of the MZGram settings: what goes in, what a file from
// elsewhere may write back.
class MZGramSettingsBackupTest {

    private val config = "mzgram_config"

    @Test
    fun accepted_typesAndRanges() {
        assertEquals(true, MZGramSettingsBackup.accepted(config, "hideStories", true))
        assertNull(MZGramSettingsBackup.accepted(config, "hideStories", 1))
        assertEquals(120, MZGramSettingsBackup.accepted(config, "stickerSize", 120))
        assertNull(MZGramSettingsBackup.accepted(config, "stickerSize", MZGramStickers.SIZE_MAX + 1))
        assertNull(MZGramSettingsBackup.accepted(config, "stickerSize", 1.5))
        assertEquals(setOf("a", "b"), MZGramSettingsBackup.accepted(config, "hiddenSettingsRows", JSONArray(listOf("a", "b"))))
        assertNull(MZGramSettingsBackup.accepted(config, "hiddenSettingsRows", JSONArray(listOf("a", 2))))
        assertEquals(-1, MZGramSettingsBackup.accepted("mzgram_archive", "limit2", -1))
        assertNull(MZGramSettingsBackup.accepted("mzgram_archive", "counter2", 5))
    }

    @Test
    fun accepted_rangesMatchTheChoices() {
        // A larger value would be an index past the titles of the choice
        assertNotNull(MZGramSettingsBackup.accepted(config, "fabShape", MZGramFabShape.TITLES.size - 1))
        assertNull(MZGramSettingsBackup.accepted(config, "fabShape", MZGramFabShape.TITLES.size))
        assertNotNull(MZGramSettingsBackup.accepted(config, "doubleTapOwn", MZGramDoubleTap.TITLES.size - 1))
        assertNull(MZGramSettingsBackup.accepted(config, "doubleTapOther", MZGramDoubleTap.TITLES.size))
        assertNotNull(MZGramSettingsBackup.accepted(config, "drawerHeaderStyle", MZGramDrawer.HEADER_TITLES.size - 1))
        assertNull(MZGramSettingsBackup.accepted(config, "drawerHeaderStyle", MZGramDrawer.HEADER_TITLES.size))
    }

    @Test
    fun secretsAndDeviceStateStayOut() {
        for (key in listOf("unifiedPushVapidKey", "unifiedPushGateway", "useUnifiedPush", "passcodeAccounts", "aiWarned", "appFontName")) {
            assertNull(key, MZGramSettingsBackup.accepted(config, key, "x"))
            assertNull(key, MZGramSettingsBackup.accepted(config, key, true))
        }
        for (file in listOf("mzgram_push", "mzgram_plugin_grants", "mzgram_plugins", "mzgram_update")) {
            assertFalse(file, MZGramSettingsBackup.files().contains(file))
        }
    }

    @Test
    fun json_roundTrip() {
        val values = linkedMapOf(
                config to linkedMapOf<String, Any>("hideStories" to true, "fabShape" to 2, "language" to "uk",
                        "aiMenuButtons" to hashSetOf("summary"), "unifiedPushVapidKey" to "secret"),
                "mzgram_player_style" to linkedMapOf<String, Any>("coverStyle" to 3, "wavy" to false))
        val text = MZGramSettingsBackup.toJson(values)
        assertNotNull(text)
        assertFalse(text!!.contains("secret"))
        val parsed = MZGramSettingsBackup.parse(text)!!
        assertEquals(6, MZGramSettingsBackup.count(parsed))
        assertEquals(true, parsed[config]!!["hideStories"])
        assertEquals(2, parsed[config]!!["fabShape"])
        assertEquals(setOf("summary"), parsed[config]!!["aiMenuButtons"])
        assertEquals(3, parsed["mzgram_player_style"]!!["coverStyle"])
    }

    @Test
    fun parse_foreignFiles() {
        assertNull(MZGramSettingsBackup.parse("not json"))
        assertNull(MZGramSettingsBackup.parse("{\"exteraconfig\": {\"hideStories\": true}}"))
        val tampered = "{\"format\": \"mzgram-settings\", \"version\": 1, \"files\": {" +
                "\"mzgram_config\": {\"fabShape\": 99, \"hideStories\": \"yes\", \"passcodeAccounts\": true, \"ghostMode\": true}," +
                "\"mzgram_push\": {\"token\": \"x\"}}}"
        val parsed = MZGramSettingsBackup.parse(tampered)!!
        assertEquals(1, MZGramSettingsBackup.count(parsed))
        assertTrue(parsed[config]!!.containsKey("ghostMode"))
    }
}
