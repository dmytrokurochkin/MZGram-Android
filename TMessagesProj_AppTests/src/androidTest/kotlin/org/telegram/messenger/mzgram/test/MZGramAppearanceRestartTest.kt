package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ThemeActivity
import java.io.File
import org.telegram.messenger.mzgram.test.MZGramAppearance as A

// Appearance across a restart of the app, run by mzgram_appearance_restart.sh:
// each step is its own instrumentation run, so each starts a new process of
// the app, which reads the saved theme the way a phone does after a restart
// or an update.
//
// 1. chooseThemeAndAccent: Day and one of its accents, picked by tapping.
// 2. afterRestart: the new process starts with them, colors and all.
// 3. leaveTheStateOfAnEarlierBuild: what an earlier build leaves behind
//    (Night with an accent, a saved account.getThemes answer without usable
//    themes, the night mode off), written while the app is not running it.
// 4. afterTheUpdate: the new process starts with that theme, Appearance lists
//    every standard theme and accent, and a tap on a theme still works.
//
// Skipped unless the script passes mzAppearanceRestart.
class MZGramAppearanceRestartTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val expectedFile get() = File(instrumentation.targetContext.filesDir, "mzgram-appearance-expected.txt")

    // Each step signs in a test user that has no key for the server; the app
    // saves it, and the next process (the rest of the tests) would start
    // signed in with a chat list that never finishes loading. Signed out
    // again, as the emulator was before.
    @After
    fun signOut() {
        if (InstrumentationRegistry.getArguments().getString("mzAppearanceRestart") != null) {
            UserConfig.getInstance(0).clearConfig()
            Thread.sleep(500)
        }
    }

    private fun requireScript() {
        assumeTrue("run by mzgram_appearance_restart.sh", InstrumentationRegistry.getArguments().getString("mzAppearanceRestart") != null)
    }

    private fun expect(key: String, accent: Int, background: Int) {
        expectedFile.writeText("$key|$accent|$background")
    }

    private fun expected(): Triple<String, Int, Int> {
        val parts = expectedFile.readText().split("|")
        return Triple(parts[0], parts[1].toInt(), parts[2].toInt())
    }

    @Test
    fun chooseThemeAndAccent() {
        requireScript()
        A.startApp()
        A.reset()
        val browse = A.open(ThemeActivity.THEME_TYPE_THEMES_BROWSER)
        A.scrollTo(browse, "themeListRow")
        val day = A.showTheme(browse, "Day")
        assertNotNull("Day in the theme list", day)
        A.tap(day!!)
        assertTrue("Day applied: ${A.describe()}", MZGramScreens.waitFor(10) { Theme.getActiveTheme().key == "Day" })
        Thread.sleep(1200)
        A.scrollTo(browse, "themeAccentListRow")
        val current = A.active().currentAccentId
        val other = A.accentViews(browse).firstOrNull { it.second != current && it.second < 100 }
        assertNotNull("another accent of Day", other)
        A.tap(other!!.first)
        assertTrue("accent applied: ${A.describe()}", MZGramScreens.waitFor(10) { Theme.getActiveTheme().currentAccentId == other.second })
        Thread.sleep(1500)
        MZGramScreens.capture("appearance-4-chosen")
        expect("Day", other.second, A.color(Theme.key_windowBackgroundWhiteBlueHeader))
        MZGramScreens.log("appearance restart: chose ${A.describe()}")
        A.close(browse)
    }

    @Test
    fun afterRestart() {
        requireScript()
        val (key, accent, header) = expected()
        // Read when the process started, before any screen
        MZGramScreens.log("appearance restart: the new process starts with ${A.describe()}")
        assertEquals("theme after the restart", key, A.active().key)
        assertEquals("accent after the restart", accent, A.active().currentAccentId)
        assertEquals("accent colors after the restart", header, A.color(Theme.key_windowBackgroundWhiteBlueHeader))
        A.startApp()
        val browse = A.open(ThemeActivity.THEME_TYPE_THEMES_BROWSER)
        A.scrollTo(browse, "themeAccentListRow")
        MZGramScreens.capture("appearance-4-after-restart")
        A.close(browse)
    }

    @Test
    fun leaveTheStateOfAnEarlierBuild() {
        requireScript()
        val night = A.builtInAccents("Night")
        assertTrue("Night has built-in accents: $night", night.size > 2)
        val accent = night[2]
        // The saved account.getThemes answer of an earlier build: themes the
        // color theme row cannot use (no settings), and its hash
        val answer = ApplicationLoader.applicationContext.getSharedPreferences("emojithemes_config_0", Context.MODE_PRIVATE).edit().clear()
        for (i in 0 until 3) {
            val theme = TLRPC.TL_theme()
            theme.id = 5_200_000_000L + i
            theme.slug = "mzgram-earlier-$i"
            theme.title = "Earlier $i"
            theme.isDefault = true
            val data = SerializedData(theme.objectSize)
            theme.serializeToStream(data)
            answer.putString("theme_$i", Utilities.bytesToHex(data.toByteArray()))
        }
        answer.putInt("count", 3).commit()
        MessagesController.getGlobalMainSettings().edit()
            .putString("theme", "Night")
            .putInt("selectedAutoNightType", Theme.AUTO_NIGHT_TYPE_NONE)
            .commit()
        ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit()
            .putString("lastDarkTheme", "Night")
            .remove("lastDayTheme")
            .putInt("accent_current_" + Theme.getTheme("Night").assetName, accent)
            .commit()
        expect("Night", accent, 0)
        MZGramScreens.log("appearance update: an earlier build left Night accent $accent")
    }

    @Test
    fun afterTheUpdate() {
        requireScript()
        val (key, accent, _) = expected()
        MZGramScreens.log("appearance update: the new process starts with ${A.describe()}")
        assertEquals("theme after the update", key, A.active().key)
        assertEquals("accent after the update", accent, A.active().currentAccentId)
        A.startApp()

        val basic = A.open(ThemeActivity.THEME_TYPE_BASIC)
        A.scrollTo(basic, "themeListRow2")
        var rowThemes = 0
        MZGramScreens.waitFor(15) { rowThemes = A.row(basic)?.let { A.rowCount(it) } ?: 0; rowThemes >= 10 }
        val dark = A.brightness(MZGramScreens.capture("appearance-5-after-update"))
        assertTrue("the color theme row after the update: $rowThemes themes", rowThemes >= 10)

        val browse = A.open(ThemeActivity.THEME_TYPE_THEMES_BROWSER)
        A.scrollTo(browse, "themeListRow")
        val themes = A.themeList(browse)
        assertTrue("every built-in theme after the update: $themes", themes.containsAll(A.builtIn))
        A.scrollTo(browse, "themeAccentListRow")
        val accents = A.accentViews(browse)
        MZGramScreens.capture("appearance-5-after-update-browse")
        assertTrue("accent circles after the update: ${accents.size}", accents.size >= 5)

        A.scrollTo(browse, "themeListRow")
        val classic = A.showTheme(browse, "Blue")
        assertNotNull(classic)
        A.tap(classic!!)
        assertTrue("Classic applied after the update: ${A.describe()}", MZGramScreens.waitFor(10) { Theme.getActiveTheme().key == "Blue" })
        Thread.sleep(1500)
        val light = A.brightness(MZGramScreens.capture("appearance-5-after-update-classic"))
        MZGramScreens.log("appearance update: night screen $dark, classic screen $light")
        assertTrue("the screen turned light: $dark -> $light", light > dark + 60)
        A.close(browse)
        A.close(basic)
        A.reset()
        // Not the state the other tests start from
        ApplicationLoader.applicationContext.getSharedPreferences("emojithemes_config_0", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
