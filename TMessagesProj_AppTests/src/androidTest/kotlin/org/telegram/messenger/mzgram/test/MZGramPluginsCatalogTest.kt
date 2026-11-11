package org.telegram.messenger.mzgram.test

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramSettingsBackup
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat
import org.telegram.messenger.mzgram.plugins.MZGramPluginsCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        assertNotNull(MZGramConfig.DEFAULT_PLUGINS_CATALOG_URL)
        assertTrue(MZGramConfig.DEFAULT_PLUGINS_CATALOG_URL.isNotEmpty())
        assertEquals("https://raw.githubusercontent.com/dmytrokurochkin/MZGram-Plugins/main/index.json", MZGramConfig.DEFAULT_PLUGINS_CATALOG_URL)
        assertTrue(MZGramConfig.DEFAULT_PLUGINS_CATALOG_URL.startsWith("https://raw.githubusercontent.com/dmytrokurochkin/MZGram-Plugins/"))

        MZGramConfig.setPluginsCatalogUrl("https://raw.githubusercontent.com/user/repo/main/index.json")
        assertEquals("https://raw.githubusercontent.com/user/repo/main/index.json", MZGramConfig.pluginsCatalogUrl)
        MZGramConfig.setPluginsCatalogUrl(null)
        assertEquals("", MZGramConfig.pluginsCatalogUrl)
    }

    @Test
    fun testCatalogSwitchesOffByDefault() {
        assertEquals(false, MZGramConfig.pluginsRun)
        assertEquals(false, MZGramConfig.pipEnabled)
    }

    @Test
    fun testCatalogNetworkNotTriggeredWhenUrlEmpty() {
        val latch = CountDownLatch(1)
        var errorDelivered = false
        var successDelivered = false
        MZGramPluginsCatalog.fetchCatalog("", {
            successDelivered = true
            latch.countDown()
        }, {
            errorDelivered = true
            latch.countDown()
        })
        assertTrue("Callback must fire within timeout", latch.await(2, TimeUnit.SECONDS))
        // Empty URL must immediately deliver error without making any HTTP request
        assertTrue(errorDelivered)
        assertFalse(successDelivered)
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
        assertEquals(MZGramPluginsCatalog.AUTHOR_MZGRAM, plugin.author)
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
    fun testCatalogAuthorshipAndNoAvatar() {
        val json = JSONObject().apply {
            put("id", "test_plugin")
            put("name", "Test Plugin")
            put("version", "2.1.0")
            put("description", "A test plugin description")
            put("author", "Foreign Developer")
            put("avatar", "https://example.com/avatar.png")
            put("icon_url", "https://example.com/icon.png")
            put("icon", "custom_icon_set")
            put("file", "test.plugin")
            put("sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        }
        val plugin = MZGramPluginsCatalog.CatalogPlugin(json)
        assertEquals(MZGramPluginsCatalog.AUTHOR_MZGRAM, plugin.author)
        assertEquals("MZGram", plugin.author)

        val fileMetaJson = JSONObject().apply {
            put("id", "test_plugin")
            put("name", "Test Plugin")
            put("author", "Third Party Author")
            put("icon", "third_party_icon")
            put("version", "1.0")
        }
        val rawMeta = MZGramPluginFormat.Meta(fileMetaJson)
        assertEquals("Third Party Author", rawMeta.author)
        assertEquals("third_party_icon", rawMeta.icon)

        val catalogMeta = rawMeta.forCatalog()
        assertEquals("MZGram", catalogMeta.author)
        assertNull(catalogMeta.icon)
    }

    @Test
    fun testEmptyCatalogParsesWithoutError() {
        val emptyJson = JSONObject().apply {
            put("name", "MZGram Plugins")
            put("description", "Empty catalog")
            put("plugins", JSONArray())
        }
        val data = MZGramPluginsCatalog.CatalogData(
            emptyJson.optString("name", "MZGram Plugins"),
            emptyJson.optString("description", ""),
            ArrayList()
        )
        assertEquals("MZGram Plugins", data.name)
        assertEquals("Empty catalog", data.description)
        assertTrue(data.plugins.isEmpty())

        val bareJson = JSONObject("{}")
        val pluginsArr = bareJson.optJSONArray("plugins")
        val list = ArrayList<MZGramPluginsCatalog.CatalogPlugin>()
        if (pluginsArr != null) {
            for (i in 0 until pluginsArr.length()) {
                pluginsArr.optJSONObject(i)?.let { list.add(MZGramPluginsCatalog.CatalogPlugin(it)) }
            }
        }
        val bareData = MZGramPluginsCatalog.CatalogData(
            bareJson.optString("name", "MZGram Plugins"),
            bareJson.optString("description", ""),
            list
        )
        assertEquals("MZGram Plugins", bareData.name)
        assertTrue(bareData.plugins.isEmpty())
    }

    @Test
    fun testHttp404AndMalformedJsonHandledGracefully() {
        var errorOccurred = false
        try {
            JSONObject("{ malformed json }")
        } catch (e: Exception) {
            errorOccurred = true
        }
        assertTrue(errorOccurred)

        val latch = CountDownLatch(1)
        var receivedError: String? = null
        MZGramPluginsCatalog.fetchCatalog("https://127.0.0.1:1/nonexistent.json", {
            latch.countDown()
        }, { error ->
            receivedError = error
            latch.countDown()
        })
        latch.await(5, TimeUnit.SECONDS)
        assertNotNull(receivedError)
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

    @Test
    fun testCatalogPluginLocalizedDescriptionAndNameSelection() {
        val json = JSONObject().apply {
            put("id", "sample_calc")
            put("name", "Calculator")
            put("name_i18n", JSONObject().apply {
                put("en", "Calculator")
                put("uk", "Калькулятор")
                put("ru", "Калькулятор")
            })
            put("description", "Base English calculation description")
            put("description_i18n", JSONObject().apply {
                put("en", "English calculation description")
                put("uk", "Український опис калькулятора")
                put("ru", "Русское описание калькулятора")
                put("pl", "Polski opis kalkulatora")
            })
        }
        val plugin = MZGramPluginsCatalog.CatalogPlugin(json)

        // Exact match
        assertEquals("Український опис калькулятора", plugin.getLocalizedDescription("uk"))
        assertEquals("Русское описание калькулятора", plugin.getLocalizedDescription("ru"))
        assertEquals("Polski opis kalkulatora", plugin.getLocalizedDescription("pl"))

        // Fallback to English when language is not in map
        assertEquals("English calculation description", plugin.getLocalizedDescription("de"))
        assertEquals("English calculation description", plugin.getLocalizedDescription("es"))

        // Names
        assertEquals("Калькулятор", plugin.getLocalizedName("uk"))
        assertEquals("Calculator", plugin.getLocalizedName("de"))
    }

    @Test
    fun testCatalogPluginBackwardCompatibilityWithoutI18n() {
        val json = JSONObject().apply {
            put("id", "old_plugin")
            put("name", "Old Plugin")
            put("description", "Legacy plugin description without i18n")
        }
        val plugin = MZGramPluginsCatalog.CatalogPlugin(json)

        assertNotNull(plugin.descriptionI18n)
        assertTrue(plugin.descriptionI18n.isEmpty())
        assertNotNull(plugin.nameI18n)
        assertTrue(plugin.nameI18n.isEmpty())

        assertEquals("Legacy plugin description without i18n", plugin.getLocalizedDescription("uk"))
        assertEquals("Legacy plugin description without i18n", plugin.getLocalizedDescription("en"))
        assertEquals("Legacy plugin description without i18n", plugin.getLocalizedDescription("fr"))

        assertEquals("Old Plugin", plugin.getLocalizedName("uk"))
        assertEquals("Old Plugin", plugin.getLocalizedName("de"))
    }

    @Test
    fun testCatalogPluginMalformedOrEmptyI18nValues() {
        val json = JSONObject().apply {
            put("id", "malformed_i18n")
            put("name", "Safe Plugin")
            put("description", "Default safe description")
            put("description_i18n", JSONObject().apply {
                put("uk", "")
                put("ru", "   ")
                put("pl", 12345)
                put("es", true)
                put("en", "Valid English")
            })
            put("name_i18n", JSONObject().apply {
                put("uk", "")
                put("en", "Safe English Name")
            })
        }
        val plugin = MZGramPluginsCatalog.CatalogPlugin(json)

        // Empty/whitespace/non-string skipped and falls back to English
        assertEquals("Valid English", plugin.getLocalizedDescription("uk"))
        assertEquals("Valid English", plugin.getLocalizedDescription("ru"))
        assertEquals("Valid English", plugin.getLocalizedDescription("pl"))
        assertEquals("Valid English", plugin.getLocalizedDescription("es"))

        assertEquals("Safe English Name", plugin.getLocalizedName("uk"))
    }

    @Test
    fun testMetaForCatalogWithLocalizedNameAndDescription() {
        val fileMetaJson = JSONObject().apply {
            put("id", "my_plugin")
            put("name", "Raw Name")
            put("description", "Raw Description")
            put("author", "Raw Author")
            put("icon", "raw_icon")
        }
        val rawMeta = MZGramPluginFormat.Meta(fileMetaJson)

        val catalogMeta = rawMeta.forCatalog("Локалізована назва", "Локалізований опис")
        assertEquals("MZGram", catalogMeta.author)
        assertNull(catalogMeta.icon)
        assertEquals("Локалізована назва", catalogMeta.name)
        assertEquals("Локалізований опис", catalogMeta.description)
    }

    // Same shape as the published index.json: no author, descriptions in 12 languages
    private val stagingShapedCatalog = """
        {
          "name": "MZGram Plugins",
          "description": "Official plugins and extensions catalog for MZGram",
          "plugins": [
            {
              "id": "sample_notes",
              "name": "Sample Notes",
              "version": "1.2",
              "min_app_version": "1.0.0",
              "file": "plugins/sample_notes/1.2/sample_notes.plugin",
              "sha256": "57c4099c9ab29e7ae5f09adfe4770bbcd58787d35e6b11ec672e696898eab0a5",
              "size": 40370,
              "description": "Keeps short notes",
              "description_i18n": {
                "en": "Keeps short notes",
                "uk": "Зберігає короткі нотатки",
                "ru": "Хранит короткие заметки",
                "pl": "Przechowuje krótkie notatki",
                "es": "Guarda notas breves",
                "de": "Speichert kurze Notizen",
                "fr": "Garde de courtes notes",
                "pt-BR": "Guarda notas curtas",
                "tr": "Kısa notları saklar",
                "ar": "يحفظ ملاحظات قصيرة",
                "be": "Захоўвае кароткія нататкі",
                "kk": "Қысқа жазбаларды сақтайды"
              },
              "permissions": ["messages.read", "network", "hooks"]
            },
            {
              "id": "sample_clock",
              "name": "Sample Clock",
              "version": "0.3",
              "file": "plugins/sample_clock/0.3/sample_clock.plugin",
              "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
              "size": 812,
              "description": "Shows the time",
              "description_i18n": {
                "en": "Shows the time",
                "uk": "Показує час"
              },
              "requirements": ["pytz"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testStagingShapedCatalogParsesWithClient() {
        val root = JSONObject(stagingShapedCatalog)
        assertEquals("MZGram Plugins", root.optString("name"))
        val arr = root.optJSONArray("plugins")
        assertNotNull(arr)
        assertEquals(2, arr!!.length())

        val notes = MZGramPluginsCatalog.CatalogPlugin(arr.getJSONObject(0))
        assertEquals("sample_notes", notes.id)
        assertEquals("Sample Notes", notes.name)
        assertEquals("1.2", notes.version)
        assertEquals("1.0.0", notes.minAppVersion)
        assertEquals(40370L, notes.size)
        assertEquals(listOf("messages.read", "network", "hooks"), notes.permissions)
        assertEquals(12, notes.descriptionI18n.size)
        assertEquals("Keeps short notes", notes.getLocalizedDescription("en"))
        assertEquals("Зберігає короткі нотатки", notes.getLocalizedDescription("uk"))
        assertEquals("Guarda notas curtas", notes.getLocalizedDescription("pt-BR"))
        assertEquals("Қысқа жазбаларды сақтайды", notes.getLocalizedDescription("kk"))

        val clock = MZGramPluginsCatalog.CatalogPlugin(arr.getJSONObject(1))
        assertEquals("sample_clock", clock.id)
        assertEquals(listOf("pytz"), clock.requirements)
        assertTrue(clock.permissions.isEmpty())
        assertEquals("Показує час", clock.getLocalizedDescription("uk"))
        // A language the entry does not have falls back to English
        assertEquals("Shows the time", clock.getLocalizedDescription("de"))

        // The catalog never carries an author: every entry shows as MZGram
        for (p in listOf(notes, clock)) {
            assertEquals("MZGram", p.author)
        }
    }
}
