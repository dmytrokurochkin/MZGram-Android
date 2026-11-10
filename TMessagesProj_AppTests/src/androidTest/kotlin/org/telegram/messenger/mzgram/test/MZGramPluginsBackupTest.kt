package org.telegram.messenger.mzgram.test

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramSettingsBackup
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat
import org.telegram.messenger.mzgram.plugins.MZGramPluginsBackup

/**
 * Tests for feature #16 (plugins.security.plugins-backup).
 * Verifies secret filtering, catalog authorship enforcement, corrupted
 * plugins section handling, and settings backup backward compatibility.
 */
class MZGramPluginsBackupTest {

    @Test
    fun isSecretKey_detectsSensitiveKeys() {
        val sensitive = listOf(
            "token", "api_token", "authToken", "bot_token",
            "secret", "client_secret", "password", "user_password",
            "api_key", "apikey", "auth", "credential", "bearer", "private_key"
        )
        for (key in sensitive) {
            assertTrue("Key '$key' must be detected as sensitive", MZGramPluginsBackup.isSecretKey(key))
        }

        val safe = listOf(
            "theme", "dark_mode", "items_count", "fontSize", "color",
            "enabled", "auto_refresh", "timeout_sec", "format"
        )
        for (key in safe) {
            assertFalse("Key '$key' must be considered safe", MZGramPluginsBackup.isSecretKey(key))
        }
    }

    @Test
    fun sanitizeSettings_stripsSecretsAndKeepsSafe() {
        val input = JSONObject().apply {
            put("theme", "dracula")
            put("api_key", "secret123")
            put("bot_token", "token456")
            put("fontSize", 14)
            put("password", "p@ssword")
        }.toString()

        val sanitized = MZGramPluginsBackup.sanitizeSettings(input)
        val result = JSONObject(sanitized)

        assertEquals("dracula", result.optString("theme"))
        assertEquals(14, result.optInt("fontSize"))
        assertFalse("api_key must be stripped", result.has("api_key"))
        assertFalse("bot_token must be stripped", result.has("bot_token"))
        assertFalse("password must be stripped", result.has("password"))
    }

    @Test
    fun pluginsBackup_disabledByDefault() {
        assertFalse("pluginsBackup must be false by default", MZGramConfig.pluginsBackup)
    }

    @Test
    fun settingsBackup_legacyFormatPreservedWhenSwitchOff() {
        val original = MZGramConfig.pluginsBackup
        try {
            MZGramConfig.pluginsBackup = false
            val values = linkedMapOf(
                "mzgram_config" to linkedMapOf<String, Any>("hideStories" to true)
            )
            val json = MZGramSettingsBackup.toJson(values)
            assertNotNull(json)
            val obj = JSONObject(json!!)
            assertEquals("mzgram-settings", obj.optString("format"))
            assertEquals(1, obj.optInt("version"))
            assertTrue("files section must be present", obj.has("files"))
            assertFalse("plugins section must NOT be present when switch is off", obj.has("plugins"))
        } finally {
            MZGramConfig.pluginsBackup = original
        }
    }

    @Test
    fun settingsBackup_corruptedPluginsSectionDoesNotBreakImport() {
        // Corrupted plugins section with invalid JSON and malformed types
        val corruptedJson = """
            {
              "format": "mzgram-settings",
              "version": 1,
              "files": {
                "mzgram_config": {
                  "hideStories": true,
                  "ghostMode": true
                }
              },
              "plugins": "not_an_array_corrupted_data_!@#$%"
            }
        """.trimIndent()

        val parsed = MZGramSettingsBackup.parse(corruptedJson)
        assertNotNull("Settings parse must succeed despite corrupted plugins", parsed)
        assertEquals(2, MZGramSettingsBackup.count(parsed))
        assertEquals(true, parsed!!["mzgram_config"]?.get("hideStories"))
        assertEquals(true, parsed["mzgram_config"]?.get("ghostMode"))

        // Corrupted plugins with invalid array elements
        val corruptedArrayJson = """
            {
              "format": "mzgram-settings",
              "version": 1,
              "files": {
                "mzgram_config": {
                  "hideStories": true
                }
              },
              "plugins": [
                null,
                "broken_string",
                {"id": "../invalid/path/traversal", "content": "???"},
                {"missing_all_required_fields": true}
              ]
            }
        """.trimIndent()

        val parsed2 = MZGramSettingsBackup.parse(corruptedArrayJson)
        assertNotNull("Settings parse must succeed with malformed plugins array", parsed2)
        assertEquals(1, MZGramSettingsBackup.count(parsed2))

        // restorePluginsFromJson must handle gracefully and return without throwing
        val restoredCount = MZGramPluginsBackup.restorePluginsFromJson(corruptedArrayJson)
        assertEquals("Invalid plugins must be safely skipped", 0, restoredCount)
    }

    @Test
    fun catalogPlugin_restoresWithMZGramAuthorAndNoIcon() {
        val metaJson = JSONObject().apply {
            put("id", "catalog_plugin")
            put("name", "Catalog Plugin")
            put("version", "1.0")
            put("author", "ThirdPartyDev")
            put("icon", "https://example.com/icon.png")
            put("description", "A plugin from catalog")
        }
        val meta = MZGramPluginFormat.Meta(metaJson)
        val catalogMeta = meta.forCatalog()

        assertEquals("MZGram", catalogMeta.author)
        assertNull("Icon must be removed for catalog plugin", catalogMeta.icon)
    }

    @Test
    fun restorePluginsFromJson_onlyRestoresWhenPluginsBackupEnabled() {
        val original = MZGramConfig.pluginsBackup
        try {
            val jsonWithPlugins = """
                {
                  "format": "mzgram-settings",
                  "version": 1,
                  "files": {},
                  "plugins": [
                    {
                      "id": "sample_plugin",
                      "name": "Sample",
                      "version": "1.0",
                      "content": "cHJpbnQoJ2hlbGxvJyk="
                    }
                  ]
                }
            """.trimIndent()

            // When switch is off, restorePluginsFromJson must return 0 without restoring anything
            MZGramConfig.pluginsBackup = false
            val countOff = MZGramPluginsBackup.restorePluginsFromJson(jsonWithPlugins)
            assertEquals("Must not restore plugins when pluginsBackup switch is off", 0, countOff)
        } finally {
            MZGramConfig.pluginsBackup = original
        }
    }

    @Test
    fun settingsBackup_restoreWithoutFullJson_behavesAsLegacy() {
        val values = linkedMapOf(
            "mzgram_config" to linkedMapOf<String, Any>("hideStories" to false)
        )
        // 1-arg overload restore(values) delegates to restore(values, null)
        val written = MZGramSettingsBackup.restore(values)
        assertTrue("Legacy restore must write settings and return count > 0", written > 0)
    }

    @Test
    fun settingsBackup_fileWithPluginsBackupTrue_doesNotSelfEnableOrInstallWhenSwitchOff() {
        val original = MZGramConfig.pluginsBackup
        try {
            MZGramConfig.pluginsBackup = false

            // A settings backup JSON that maliciously attempts to self-enable plugins_backup
            // and contains a plugins section
            val maliciousJson = """
                {
                  "format": "mzgram-settings",
                  "version": 1,
                  "files": {
                    "mzgram_config": {
                      "hideStories": true,
                      "plugins_backup": true
                    }
                  },
                  "plugins": [
                    {
                      "id": "malicious_plugin",
                      "name": "Malicious Plugin",
                      "version": "1.0",
                      "content": "cHJpbnQoJ2hhY2tlZCcp"
                    }
                  ]
                }
            """.trimIndent()

            val parsed = MZGramSettingsBackup.parse(maliciousJson)
            assertNotNull(parsed)

            // plugins_backup must NOT be accepted in restore keys
            assertFalse("plugins_backup must not be accepted as a restorable key",
                MZGramSettingsBackup.accepted("mzgram_config", "plugins_backup", true) != null)

            val written = MZGramSettingsBackup.restore(parsed, maliciousJson)
            assertTrue("Settings other than plugins_backup must be restored", written > 0)

            // Switch on device must remain FALSE!
            assertFalse("pluginsBackup on device must remain disabled despite backup file having plugins_backup=true", MZGramConfig.pluginsBackup)

            // Legacy format import without plugins section behaves identically
            val legacyJson = """
                {
                  "format": "mzgram-settings",
                  "version": 1,
                  "files": {
                    "mzgram_config": {
                      "hideStories": false
                    }
                  }
                }
            """.trimIndent()
            val parsedLegacy = MZGramSettingsBackup.parse(legacyJson)
            assertNotNull(parsedLegacy)
            val writtenLegacy = MZGramSettingsBackup.restore(parsedLegacy, legacyJson)
            assertTrue("Legacy backup format must import successfully", writtenLegacy > 0)
        } finally {
            MZGramConfig.pluginsBackup = original
        }
    }
}
