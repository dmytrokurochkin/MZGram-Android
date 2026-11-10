package org.telegram.messenger.mzgram.test

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramSettingsBackup
import org.telegram.messenger.mzgram.plugins.MZGramPluginsCatalog

class MZGramPluginsCatalogTest {

    private var initialCatalogUrl = ""

    @Before
    fun setUp() {
        initialCatalogUrl = MZGramConfig.pluginsCatalogUrl
        MZGramConfig.pluginsCatalogUrl = ""
    }

    @After
    fun tearDown() {
        MZGramConfig.pluginsCatalogUrl = initialCatalogUrl
    }

    @Test
    fun testCatalogConfigDefaultsAndSetter() {
        assertEquals("", MZGramConfig.pluginsCatalogUrl)
        MZGramConfig.setPluginsCatalogUrl("https://raw.githubusercontent.com/user/repo/main")
        assertEquals("https://raw.githubusercontent.com/user/repo/main", MZGramConfig.pluginsCatalogUrl)
        MZGramConfig.setPluginsCatalogUrl(null)
        assertEquals("", MZGramConfig.pluginsCatalogUrl)
    }

    @Test
    fun testCatalogPluginParsing() {
        val json = JSONObject().apply {
            put("id", "test_plugin")
            put("name", "Test Plugin")
            put("version", "2.1.0")
            put("description", "A test plugin description")
            put("author", "Developer")
            put("file", "test.plugin")
            put("sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            put("size", 1024L)
            put("min_app_version", "1.0.0")
            put("requirements", JSONArray().apply { put("requests") })
            put("permissions", JSONArray().apply { put("ui"); put("messages.read") })
        }
        val plugin = MZGramPluginsCatalog.CatalogPlugin(json)
        assertEquals("test_plugin", plugin.id)
        assertEquals("Test Plugin", plugin.name)
        assertEquals("2.1.0", plugin.version)
        assertEquals("test.plugin", plugin.file)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", plugin.sha256)
        assertEquals(1024L, plugin.size)
        assertEquals(1, plugin.requirements.size)
        assertEquals("requests", plugin.requirements[0])
        assertEquals(2, plugin.permissions.size)
        assertTrue(plugin.permissions.contains("ui"))
        assertTrue(plugin.permissions.contains("messages.read"))
    }

    @Test
    fun testCatalogStringsExist() {
        val ctx = ApplicationLoader.applicationContext
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalog))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogNotConnected))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogNotConnectedInfo))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogUrl))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogUrlPrompt))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogConnect))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogDisconnect))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogRefresh))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogInstalled))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogInstall))
        assertNotNull(ctx.getString(R.string.MZGramPluginsCatalogSha256Mismatch))
    }

    @Test
    fun testSettingsBackupIncludesCatalogUrl() {
        MZGramConfig.setPluginsCatalogUrl("https://example.com/catalog.json")
        val json = MZGramSettingsBackup.exportToJson()
        assertTrue(json.contains("pluginsCatalogUrl"))
    }
}
