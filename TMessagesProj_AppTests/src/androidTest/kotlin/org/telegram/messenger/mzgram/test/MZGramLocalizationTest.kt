package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramLanguage
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.mzgram.MZGramSettingsActivity
import java.util.Locale

// Every MZGram string is in each of the twelve MZGram languages, and MZGram
// shows them in the MZGram language (Settings > MZGram > Interface), apart
// from Telegram's language and from the language Android gives the app's
// resources: System is the phone's language when MZGram has it, else English.
class MZGramLocalizationTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private var activity: Activity? = null
    private var savedLocale: Locale? = null
    private var savedLanguage = ""

    // Strings that may read the same as in English: names and terms
    private val sameEverywhere = setOf("MZGram", "MZGramDetailsId", "MZGramUnifiedPush", "MZGramDetailsBot", "MZGramDetailsDcMiami", "MZGramDetailsDcAmsterdam", "MZGramSectionInterface", "MZGramMessageDetails", "MZGramDetailsMessage", "MZGramDetailsDate", "MZGramStripZalgoText", "MZGramDetailsMedia")

    private val formatArg = Regex("%(\\d+\\$)?[sd]")

    // "Archive" in each language, as values-xx has it
    private val archive = mapOf(
        "en" to "Archive", "uk" to "Архів", "ru" to "Архив", "pl" to "Archiwum",
        "es" to "Archivo", "de" to "Archiv", "fr" to "Archives", "pt-BR" to "Arquivo",
        "tr" to "Arşiv", "ar" to "الأرشيف", "be" to "Архіў", "kk" to "Мұрағат",
    )

    @Before
    fun setUp() {
        savedLocale = ApplicationLoader.applicationContext.resources.configuration.locale
        savedLanguage = MZGramConfig.language
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { activity?.finish() }
        MZGramLanguage.systemOverride = null
        MZGramConfig.setLanguage(savedLanguage)
        savedLocale?.let { setAppLanguage(it) }
    }

    private fun mzgramKeys(): List<Pair<String, Int>> = R.string::class.java.fields
        .filter { it.name.startsWith("MZGram") }
        .map { it.name to it.getInt(null) }
        .sortedBy { it.first }

    private fun locale(code: String): Locale = code.split("-").let { if (it.size == 2) Locale(it[0], it[1]) else Locale(it[0]) }

    private fun resourcesIn(locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    // Every MZGram string has its own text in each values-xx (a key missing
    // there would fall back to English), with the same format arguments.
    @Test
    fun everyMZGramString_isInEveryLanguage() {
        assertEquals(archive.keys.toList(), MZGramLanguage.LANGUAGES.toList())
        val en = resourcesIn(Locale.ENGLISH)
        val keys = mzgramKeys()
        assertTrue("MZGram strings found: ${keys.size}", keys.size > 100)
        val missing = ArrayList<String>()
        for (code in MZGramLanguage.LANGUAGES.filter { it != "en" }) {
            val other = resourcesIn(locale(code))
            for ((name, id) in keys) {
                val enText = en.getString(id)
                val text = other.getString(id)
                assertTrue("$code $name has text", text.isNotBlank())
                if (enText == text && name !in sameEverywhere) {
                    missing.add("$code $name")
                }
                assertEquals("$code $name format arguments", formatArg.findAll(enText).map { it.value }.sorted().toList(), formatArg.findAll(text).map { it.value }.sorted().toList())
            }
        }
        assertTrue("not translated: $missing", missing.isEmpty())
        MZGramScreens.log("localization: ${keys.size} MZGram strings in ${MZGramLanguage.LANGUAGES.size} languages")
    }

    // What Telegram does when its language changes (LocaleController.applyLanguage),
    // and what Android does on its own: the app's resources switch locale.
    private fun setAppLanguage(locale: Locale) {
        instrumentation.runOnMainSync {
            Locale.setDefault(locale)
            val config = Configuration()
            config.locale = locale
            val resources = ApplicationLoader.applicationContext.resources
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    // The bug: with Telegram in English and Android putting the app's
    // resources back to the phone's Ukrainian, some MZGram screens were
    // Ukrainian. Now the MZGram language decides, whatever the resources say.
    @Test
    fun theChosenLanguage_isShown_whateverTheAppResources() {
        for ((code, expected) in archive) {
            MZGramConfig.setLanguage(code)
            for (appLocale in listOf(Locale.ENGLISH, Locale("uk"), Locale("ja"))) {
                setAppLanguage(appLocale)
                assertEquals("$code with the app resources in $appLocale", expected, LocaleController.getString(R.string.MZGramSectionArchive))
            }
        }
        MZGramConfig.setLanguage("tr")
        assertEquals("formatted, in Turkish", "MZGram 1.0.4 indiriliyor: %42", LocaleController.formatString(R.string.MZGramUpdateDownloading, "1.0.4", 42))
        MZGramConfig.setLanguage("de")
        assertEquals("formatted, in German", "MZGram 1.0.4 ist verfügbar", LocaleController.formatString(R.string.MZGramUpdateCheckAvailable, "1.0.4"))
    }

    @Test
    fun system_isThePhoneLanguage_elseEnglish() {
        MZGramConfig.setLanguage(MZGramLanguage.SYSTEM)
        setAppLanguage(Locale("uk"))
        val expected = mapOf(
            Locale("de", "AT") to "Archiv",
            Locale("pt", "PT") to "Arquivo",
            Locale("pt", "BR") to "Arquivo",
            Locale("kk") to "Мұрағат",
            Locale("be") to "Архіў",
            Locale("ar", "EG") to "الأرشيف",
            Locale("it") to "Archive",
            Locale("ja") to "Archive",
        )
        for ((phone, text) in expected) {
            MZGramLanguage.systemOverride = phone
            assertEquals("phone in $phone", text, LocaleController.getString(R.string.MZGramSectionArchive))
        }
    }

    private fun interfaceItems(): ArrayList<UItem> {
        val items = ArrayList<UItem>()
        instrumentation.runOnMainSync {
            val fillItems = MZGramSettingsActivity::class.java.getDeclaredMethod("fillItems", ArrayList::class.java, UniversalAdapter::class.java)
            fillItems.isAccessible = true
            fillItems.invoke(MZGramSettingsActivity(MZGramSettingsActivity.SECTION_INTERFACE), items, null)
        }
        return items
    }

    // Settings > MZGram > Interface starts with the language: System, or
    // the chosen one by its own name.
    @Test
    fun settings_haveTheLanguageChoice() {
        MZGramConfig.setLanguage(MZGramLanguage.SYSTEM)
        MZGramLanguage.systemOverride = Locale.ENGLISH
        var row = interfaceItems().first { it.viewType != UniversalAdapter.VIEW_TYPE_SHADOW }
        assertEquals("MZGram language", row.text.toString())
        assertEquals("System", row.textValue.toString())
        MZGramConfig.setLanguage("ar")
        row = interfaceItems().first { it.viewType != UniversalAdapter.VIEW_TYPE_SHADOW }
        assertEquals("لغة MZGram", row.text.toString())
        assertEquals("العربية", row.textValue.toString())
        MZGramConfig.setLanguage("kk")
        row = interfaceItems().first { it.viewType != UniversalAdapter.VIEW_TYPE_SHADOW }
        assertEquals("Қазақша", row.textValue.toString())
    }

    private fun screenshots(language: String, appLocale: Locale) {
        MZGramConfig.setLanguage(language)
        setAppLanguage(appLocale)
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        activity = MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val problems = MZGramScreens.settingsPages("settings-$language", listOf(
            MZGramSettingsActivity.SECTION_ARCHIVE,
            MZGramSettingsActivity.SECTION_INTERFACE,
            MZGramSettingsActivity.SECTION_OTHER,
        ))
        assertTrue(problems.joinToString(), problems.isEmpty())
    }

    // The settings screen in Ukrainian.
    @Test
    fun settings_inUkrainian_screenshot() {
        screenshots("uk", Locale.ENGLISH)
    }

    // Arabic is written right to left: the screenshots show its texts.
    @Test
    fun settings_inArabic_screenshot() {
        screenshots("ar", Locale.ENGLISH)
        assertEquals("الأرشيف", LocaleController.getString(R.string.MZGramSectionArchive))
    }
}
