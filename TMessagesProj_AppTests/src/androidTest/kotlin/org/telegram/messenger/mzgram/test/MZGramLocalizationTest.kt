package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.mzgram.MZGramSettingsActivity
import java.util.Locale

// Names and descriptions of every MZGram feature are in English and
// Ukrainian, and follow the app's language.
class MZGramLocalizationTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private var activity: Activity? = null
    private var savedLocale: Locale? = null

    // Strings that read the same in both languages.
    private val sameInBoth = setOf("MZGram", "MZGramDetailsId")

    private val formatArg = Regex("%(\\d+\\$)?[sd]")

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { activity?.finish() }
        savedLocale?.let { setAppLanguage(it) }
    }

    private fun mzgramKeys(): List<Pair<String, Int>> = R.string::class.java.fields
        .filter { it.name.startsWith("MZGram") }
        .map { it.name to it.getInt(null) }
        .sortedBy { it.first }

    private fun resourcesIn(locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    // Every MZGram string has its own Ukrainian text in values-uk (a key
    // missing there would fall back to the English one), with the same
    // format arguments.
    @Test
    fun everyMZGramString_isInEnglishAndUkrainian() {
        val en = resourcesIn(Locale.ENGLISH)
        val uk = resourcesIn(Locale("uk"))
        val keys = mzgramKeys()
        assertTrue("MZGram strings found: ${keys.size}", keys.size > 100)
        val missing = ArrayList<String>()
        for ((name, id) in keys) {
            val enText = en.getString(id)
            val ukText = uk.getString(id)
            assertTrue("$name has English text", enText.isNotBlank())
            assertTrue("$name has Ukrainian text", ukText.isNotBlank())
            if (enText == ukText && name !in sameInBoth) {
                missing.add(name)
            }
            assertEquals("$name format arguments", formatArg.findAll(enText).map { it.value }.toList(), formatArg.findAll(ukText).map { it.value }.toList())
        }
        assertTrue("not translated into Ukrainian: $missing", missing.isEmpty())
        MZGramScreens.log("localization: ${keys.size} MZGram strings in English and Ukrainian")
    }

    // What changing the app's language does (LocaleController.applyLanguage):
    // the app's resources switch locale, and MZGram strings with them.
    private fun setAppLanguage(locale: Locale) {
        instrumentation.runOnMainSync {
            Locale.setDefault(locale)
            val config = Configuration()
            config.locale = locale
            val resources = ApplicationLoader.applicationContext.resources
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    @Test
    fun mzgramStrings_followTheAppLanguage() {
        savedLocale = ApplicationLoader.applicationContext.resources.configuration.locale
        setAppLanguage(Locale("uk"))
        assertEquals("Архів", LocaleController.getString(R.string.MZGramSectionArchive))
        assertEquals("Вкладки папок унизу", LocaleController.getString(R.string.MZGramFolderTabsAtBottom))
        setAppLanguage(Locale.ENGLISH)
        assertEquals("Archive", LocaleController.getString(R.string.MZGramSectionArchive))
        assertEquals("Folder tabs at the bottom", LocaleController.getString(R.string.MZGramFolderTabsAtBottom))
    }

    // The settings screen in Ukrainian.
    @Test
    fun settings_inUkrainian_screenshot() {
        savedLocale = ApplicationLoader.applicationContext.resources.configuration.locale
        setAppLanguage(Locale("uk"))
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        activity = MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val problems = MZGramScreens.settingsPages("settings-uk", listOf(
            MZGramSettingsActivity.SECTION_ARCHIVE,
            MZGramSettingsActivity.SECTION_GHOST_MODE,
            MZGramSettingsActivity.SECTION_MESSAGE_MENU,
        ))
        assertTrue(problems.joinToString(), problems.isEmpty())
    }
}
