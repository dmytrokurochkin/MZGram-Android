package org.telegram.messenger.mzgram.test

import android.content.res.Configuration
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramBranding
import org.telegram.tgnet.TLRPC
import org.telegram.ui.SettingsActivity
import org.telegram.utils.localization.Localization
import java.util.Locale

// MZGram's own name wherever the app names itself, in English and in a
// Ukrainian cloud pack; the Telegram service and the package keep theirs.
class MZGramBrandingTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Suppress("UNCHECKED_CAST")
    // The strings of a downloaded (cloud) language pack: since Telegram 13.0
    // LocaleController keeps them as a Localization, built from the pack's map
    private val cloudPack = LocaleController::class.java.getDeclaredField("localizationExternal").also { it.isAccessible = true }

    private fun string(locale: String, res: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(locale))
        return context.createConfigurationContext(config).getString(res)
    }

    @Test
    fun rules_renameTheAppOnly_andReadTheVersionName() {
        assertEquals("MZGram needs access", MZGramBranding.rename("Telegram needs access"))
        assertEquals("MZGram 1.0.1", MZGramBranding.rename("Telegram for Android 1.0.1"))
        assertEquals("a Telegram account", MZGramBranding.brand("TosDeclineDeleteAccount", "a Telegram account"))
        assertTrue(MZGramBranding.isRenamed("AppName"))
        assertFalse("the service keeps its name", MZGramBranding.isRenamed("SessionsListInfo"))
        val release = MZGramBranding.versions("1.0.1 (12.10.1)")
        assertEquals("1.0.1", release[0])
        assertEquals("12.10.1", release[1])
        val untagged = MZGramBranding.versions("12.10.1")
        assertEquals("12.10.1", untagged[0])
        assertNull(untagged[1])
    }

    // The texts as the app reads them, also when a cloud pack (Ukrainian
    // here) gives them; the pack itself is not changed.
    @Test
    fun appTexts_sayMZGram_inEnglishAndInACloudPack() {
        assertEquals("MZGram", LocaleController.getString(R.string.AppName))
        assertTrue(LocaleController.getString(R.string.PermissionNoLocation).startsWith("MZGram"))
        assertTrue(LocaleController.formatString(R.string.StorageUsageTelegram, "1 GB").startsWith("MZGram"))
        val saved = cloudPack.get(LocaleController.getInstance())
        try {
            val pack = Localization.Builder().addLocalization(hashMapOf(
                "AppName" to "Telegram",
                "PermissionNoLocation" to "Telegram потребує доступу до вашого місцезнаходження.",
                "SessionsListInfo" to "Офіційний застосунок Telegram доступний для Android.",
            )).build()
            cloudPack.set(LocaleController.getInstance(), pack)
            assertEquals("MZGram", LocaleController.getString(R.string.AppName))
            assertEquals("MZGram потребує доступу до вашого місцезнаходження.", LocaleController.getString(R.string.PermissionNoLocation))
            assertTrue("the service's own texts stay", LocaleController.getString(R.string.SessionsListInfo).contains("Telegram"))
            assertEquals("the pack keeps its text", "Telegram", pack.getByResName("AppName"))
        } finally {
            cloudPack.set(LocaleController.getInstance(), saved)
        }
    }

    // The version line at the bottom of Settings, its strings in English and
    // Ukrainian, and the app's label (home screen, recent apps).
    @Test
    fun settings_versionLine_isMZGrams() {
        assertEquals("MZGram 1.0.1 (based on Telegram 12.10.1)", String.format(Locale.US, string("en", R.string.MZGramVersion), "1.0.1", "12.10.1"))
        assertEquals("MZGram 1.0.1 (на базі Telegram 12.10.1)", String.format(Locale.US, string("uk", R.string.MZGramVersion), "1.0.1", "12.10.1"))
        for (text in listOf(string("en", R.string.MZGramVersion), string("uk", R.string.MZGramVersion), string("en", R.string.MZGramVersionBase), string("uk", R.string.MZGramVersionBase))) {
            assertFalse("no \"official\": $text", Regex("\\bofficial\\b", RegexOption.IGNORE_CASE).containsMatchIn(text))
            assertFalse("no \"офіційний\": $text", Regex("(?<![а-яіїєґА-ЯІЇЄҐ])[оО]фіційн").containsMatchIn(text))
        }
        assertEquals("MZGram", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        assertEquals("MZGram's own package", "org.mzgram.messenger", context.packageName)

        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val settings = SettingsActivity()
        MZGramScreens.open(settings)
        assertTrue("settings shown", MZGramScreens.waitFor(30) { settings.fragmentView?.isShown == true })
        val versionView = SettingsActivity::class.java.getDeclaredField("versionView").also { it.isAccessible = true }.get(settings) as TextView
        instrumentation.runOnMainSync {
            val list = SettingsActivity::class.java.getDeclaredField("listView").also { it.isAccessible = true }.get(settings)
            val adapter = list.javaClass.getMethod("getAdapter").invoke(list)
            val count = adapter.javaClass.getMethod("getItemCount").invoke(adapter) as Int
            list.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(list, count - 1)
        }
        MZGramScreens.waitFor(10) { versionView.isShown }
        Thread.sleep(1000)
        MZGramScreens.capture("settings-version")
        var text = ""
        instrumentation.runOnMainSync { text = versionView.text.toString() }
        MZGramScreens.log("settings version line: $text")
        assertTrue("the version line names MZGram: $text", text.startsWith("MZGram "))
        assertTrue("based on Telegram: $text", text.contains("based on Telegram") || text.contains("на базі Telegram"))
        assertFalse("not Telegram for Android: $text", text.contains("Telegram for Android"))
        instrumentation.runOnMainSync { settings.finishFragment(false) }
    }
}
