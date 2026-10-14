package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ThemeActivity
import org.telegram.messenger.mzgram.test.MZGramAppearance as A

// Settings > Chat settings (Appearance) the way it works in Telegram: the
// standard themes and their accent colors are listed, a tap on a theme or an
// accent changes the app at once, the day/night switch and the night mode
// that follows the system work. The emulator is not signed in, so the server
// never answers account.getThemes: the account of point 5. The restart and
// the update over an earlier build are in MZGramAppearanceRestartTest.
class MZGramAppearanceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var savedCheckUpdates = true

    @Before
    fun setUp() {
        savedCheckUpdates = MZGramConfig.checkUpdates
        A.startApp()
        A.reset()
    }

    @After
    fun tearDown() {
        MZGramScreens.shell("cmd uimode night no")
        A.reset()
        MZGramConfig.checkUpdates = savedCheckUpdates
    }

    // 1 and 5: Appearance lists the standard themes, Browse Themes the
    // built-in themes with their accent circles, with no answer from the server.
    @Test
    fun appearance_listsTheStandardThemesAndAccents_withoutTheServer() {
        assertFalse("no themes from the server on this emulator", A.hasServerThemes())
        val basic = A.open(ThemeActivity.THEME_TYPE_BASIC)
        A.scrollTo(basic, "themeListRow2")
        var rowThemes = 0
        MZGramScreens.waitFor(15) { rowThemes = A.row(basic)?.let { A.rowCount(it) } ?: 0; rowThemes >= 10 }
        MZGramScreens.capture("appearance-1-color-theme-row")
        MZGramScreens.log("appearance: color theme row $rowThemes themes")
        assertTrue("the color theme row: the home theme, 8 standard ones and the custom one, got $rowThemes", rowThemes >= 10)

        val browse = A.open(ThemeActivity.THEME_TYPE_THEMES_BROWSER)
        A.scrollTo(browse, "themeListRow")
        val themes = A.themeList(browse)
        A.scrollTo(browse, "themeAccentListRow")
        val accents = A.accentViews(browse)
        MZGramScreens.capture("appearance-1-browse-themes")
        MZGramScreens.log("appearance: browse themes $themes, accent circles ${accents.map { it.second }}")
        assertTrue("every built-in theme listed: $themes", themes.containsAll(A.builtIn))
        assertTrue("accent circles of Classic shown: ${accents.size}", accents.size >= 5)
        A.close(browse)
        A.close(basic)
    }

    // 2: a tap on a theme, then on an accent circle, changes the app at once.
    @Test
    fun tapThemeAndAccent_changesTheAppAtOnce() {
        val browse = A.open(ThemeActivity.THEME_TYPE_THEMES_BROWSER)
        A.scrollTo(browse, "themeListRow")
        val lightScreen = A.brightness(MZGramScreens.capture("appearance-2-classic"))
        val classicBackground = A.color(Theme.key_windowBackgroundWhite)

        val night = A.showTheme(browse, "Night")
        assertNotNull("Night in the theme list", night)
        A.tap(night!!)
        assertTrue("Night applied: ${A.describe()}", MZGramScreens.waitFor(10) { Theme.getActiveTheme().key == "Night" })
        Thread.sleep(1500)
        val nightScreen = A.brightness(MZGramScreens.capture("appearance-2-night"))
        MZGramScreens.log("appearance: classic screen $lightScreen, night screen $nightScreen, ${A.describe()}")
        assertTrue("the screen turned dark: $lightScreen -> $nightScreen", nightScreen < lightScreen - 60)
        assertNotEquals("theme colors changed", classicBackground, A.color(Theme.key_windowBackgroundWhite))

        A.scrollTo(browse, "themeAccentListRow")
        val accents = A.accentViews(browse)
        val current = A.active().currentAccentId
        val other = accents.firstOrNull { it.second != current && it.second < 100 }
        assertNotNull("another accent circle of Night: ${accents.map { it.second }}", other)
        val accentColorBefore = A.color(Theme.key_chats_actionBackground)
        A.tap(other!!.first)
        assertTrue("accent ${other.second} applied: ${A.describe()}", MZGramScreens.waitFor(10) { Theme.getActiveTheme().currentAccentId == other.second })
        Thread.sleep(1500)
        MZGramScreens.capture("appearance-2-night-accent")
        assertNotEquals("the accent color changed", accentColorBefore, A.color(Theme.key_chats_actionBackground))
        A.close(browse)
    }

    // 3: the day/night switch of Appearance, and the night mode that follows
    // the system (dark mode of the phone on and off).
    @Test
    fun dayNightSwitch_andNightModeFollowingTheSystem() {
        val basic = A.open(ThemeActivity.THEME_TYPE_BASIC)
        A.scrollTo(basic, "themeListRow2")
        val row = A.row(basic)
        assertNotNull("the color theme row", row)
        val day = A.brightness(MZGramScreens.capture("appearance-3-day"))
        A.tap(A.dayNightSwitch(row!!))
        assertTrue("switched to night: ${A.describe()}", MZGramScreens.waitFor(10) { Theme.getActiveTheme().isDark })
        Thread.sleep(1500)
        val night = A.brightness(MZGramScreens.capture("appearance-3-night"))
        // The row updates once the colors of the new theme are worked out in the background
        var atNight = -1
        MZGramScreens.waitFor(10) { atNight = A.row(basic)?.let { A.rowSelected(it) } ?: -1; atNight == 0 }
        MZGramScreens.log("appearance: day/night switch -> ${A.describe()}, selected $atNight")
        assertTrue("the screen turned dark: $day -> $night", night < day - 60)
        assertEquals("the home theme stays selected at night", 0, atNight)
        A.tap(A.dayNightSwitch(A.row(basic)!!))
        assertTrue("switched back to day: ${A.describe()}", MZGramScreens.waitFor(10) { !Theme.getActiveTheme().isDark })
        var byDay = -1
        MZGramScreens.waitFor(10) { byDay = A.row(basic)?.let { A.rowSelected(it) } ?: -1; byDay == 0 }
        assertEquals("the home theme stays selected by day", 0, byDay)
        A.close(basic)

        val nightMode = A.open(ThemeActivity.THEME_TYPE_NIGHT)
        val disabled = A.rowView(nightMode, "nightDisabledRow")
        assertNotNull(disabled)
        A.tap(disabled!!)
        assertTrue("night mode off", MZGramScreens.waitFor(5) { Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE })
        val system = A.rowView(nightMode, "nightSystemDefaultRow")
        assertNotNull("System default in Auto-Night Mode", system)
        A.tap(system!!)
        assertTrue("night mode follows the system", MZGramScreens.waitFor(5) { Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM })
        MZGramScreens.shell("cmd uimode night yes")
        val darkBySystem = MZGramScreens.waitFor(15) { Theme.getActiveTheme().isDark }
        Thread.sleep(1500)
        MZGramScreens.capture("appearance-3-system-dark")
        MZGramScreens.log("appearance: system dark -> ${A.describe()}")
        assertTrue("dark mode of the phone turned the app dark", darkBySystem)
        MZGramScreens.shell("cmd uimode night no")
        val lightBySystem = MZGramScreens.waitFor(15) { !Theme.getActiveTheme().isDark }
        Thread.sleep(1500)
        MZGramScreens.capture("appearance-3-system-light")
        assertTrue("light mode of the phone turned the app light: ${A.describe()}", lightBySystem)
        assertEquals("still following the system", Theme.AUTO_NIGHT_TYPE_SYSTEM, Theme.selectedAutoNightType)
        A.close(nightMode)
    }
}
