package org.telegram.messenger.mzgram.test

import android.content.Context
import android.util.Log
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramDefaultThemes
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatThemeBottomSheet
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.DefaultThemesPreviewCell
import org.telegram.ui.ThemeActivity

// Settings > Chat settings > the color theme row. The standard themes are
// built on the device and always listed, whatever the server answered, and
// whatever an earlier build saved: these tests start from the states a phone
// is really in (a fresh install with no answer, an update over a build that
// saved an answer without usable themes, an answer without and with default
// themes, a theme chosen before the update). The emulator is not signed in,
// so no request of the app gets an answer, as on a phone without a
// connection or before the first answer.
class MZGramThemesTest {

    private val account = 0
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MediaDataController.getInstance(account)
    // The labels of the standard themes the app builds (MZGramDefaultThemes.LOCAL_EMOJI)
    private val localEmoji = listOf("🌊", "🌿", "🍑", "🌸", "🍇", "☁️", "🔥", "🌙")
    private val localCount get() = localEmoji.size
    private val homeEmoji = "🏠"

    private fun savedAnswer() = ApplicationLoader.applicationContext.getSharedPreferences("emojithemes_config_$account", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        // No saved answer and no chat themes: what a fresh install has
        savedAnswer().edit().clear().commit()
        ApplicationLoader.applicationContext.getSharedPreferences("chatthemeconfig_$account", Context.MODE_PRIVATE).edit().clear().commit()
        instrumentation.runOnMainSync { controller.defaultEmojiThemes.clear() }
    }

    @After
    fun tearDown() {
        savedAnswer().edit().clear().commit()
    }

    private fun themes(): List<ChatThemeBottomSheet.ChatThemeItem> {
        var result = listOf<ChatThemeBottomSheet.ChatThemeItem>()
        instrumentation.runOnMainSync { result = ArrayList(controller.defaultEmojiThemes) }
        return result
    }

    private fun emoji(items: List<ChatThemeBottomSheet.ChatThemeItem>) = items.map { it.chatTheme.emoticonOrSlug }

    private fun waitForThemes(count: Int): List<ChatThemeBottomSheet.ChatThemeItem> {
        MZGramScreens.waitFor(15) { controller.defaultEmojiThemes.size >= count }
        return themes()
    }

    // The home theme, then the local standard themes, each with four variants.
    private fun assertStandardThemesFirst(listed: List<ChatThemeBottomSheet.ChatThemeItem>, where: String) {
        MZGramScreens.log("$where: ${listed.size} themes, ${emoji(listed)}")
        assertTrue("$where: at least ${1 + localCount} themes, got ${emoji(listed)}", listed.size >= 1 + localCount)
        assertEquals("$where: the home theme first", homeEmoji, listed.first().chatTheme.emoticonOrSlug)
        assertEquals("$where: the standard themes after it", localEmoji, emoji(listed.subList(1, 1 + localCount)))
        assertTrue("$where: four variants each", listed.take(1 + localCount).all { it.chatTheme.items.size == 4 })
        assertTrue("$where: every variant is a built-in theme", listed.take(1 + localCount).all { item -> item.chatTheme.items.all { it.themeInfo != null } })
    }

    // A theme as account.getThemes gives it: an emoticon with the classic,
    // day, night and tinted settings, every setting with its wallpaper colors.
    private fun serverTheme(i: Int, emoticon: String, variants: Int = 4, withWallpaper: Boolean = true): TLRPC.TL_theme {
        val theme = TLRPC.TL_theme()
        theme.id = 5_100_000_000L + i
        theme.access_hash = 1
        theme.slug = "mzgram-server-$i"
        theme.title = emoticon
        theme.isDefault = true
        theme.emoticon = emoticon
        theme.flags = theme.flags or 8 or 64
        val bases = listOf(TLRPC.TL_baseThemeClassic(), TLRPC.TL_baseThemeDay(), TLRPC.TL_baseThemeNight(), TLRPC.TL_baseThemeTinted())
        for (v in 0 until variants) {
            val settings = TLRPC.TL_themeSettings()
            settings.base_theme = bases[v]
            settings.accent_color = 0xff3390ec.toInt() + i
            if (withWallpaper) {
                val colors = TLRPC.TL_wallPaperSettings()
                colors.flags = 1 or 16
                colors.background_color = 0xdbddbb + i
                colors.second_background_color = 0x6ba587
                val wallpaper = TLRPC.TL_wallPaperNoFile()
                wallpaper.id = 6_100_000_000L + i * 4 + v
                wallpaper.flags = 4
                wallpaper.dark = v >= 2
                wallpaper.settings = colors
                settings.wallpaper = wallpaper
                settings.flags = settings.flags or 2
            }
            theme.settings.add(settings)
        }
        return theme
    }

    private fun saveAnswer(themes: List<TLRPC.TL_theme>) {
        val editor = savedAnswer().edit().clear()
        themes.forEachIndexed { i, theme ->
            val data = SerializedData(theme.objectSize)
            theme.serializeToStream(data)
            editor.putString("theme_$i", Utilities.bytesToHex(data.toByteArray()))
        }
        editor.putInt("count", themes.size).commit()
    }

    // A fresh install: nothing saved, no answer yet (or no connection).
    @Test
    fun freshInstall_withoutAnAnswer_listsTheStandardThemes() {
        controller.loadEmojiThemes()
        assertStandardThemesFirst(waitForThemes(1 + localCount), "fresh install")
        assertEquals("nothing else without an answer", 1 + localCount, themes().size)
    }

    // An update over an older build that saved an answer whose themes the row
    // cannot use (two variants, no wallpaper colors): upstream read that back
    // and showed the home theme alone, without asking again.
    @Test
    fun updateOverAnOldBuild_withASavedAnswerOfUnusableThemes_listsTheStandardThemes() {
        saveAnswer(listOf(serverTheme(1, "🐥", variants = 2, withWallpaper = false), serverTheme(2, "⛄", variants = 2, withWallpaper = false)))
        controller.loadEmojiThemes()
        val listed = waitForThemes(1 + localCount)
        assertStandardThemesFirst(listed, "update over an old build")
        assertEquals("the unusable saved themes are left out", 1 + localCount, listed.size)
    }

    // An update over a build that saved good default themes: they stay, after the standard ones.
    @Test
    fun updateOverAnOldBuild_withSavedDefaultThemes_addsThemAfterTheStandardOnes() {
        saveAnswer(listOf(serverTheme(1, "🐥"), serverTheme(2, "⛄")))
        controller.loadEmojiThemes()
        val listed = waitForThemes(1 + localCount + 2)
        assertStandardThemesFirst(listed, "saved default themes")
        assertEquals("the saved themes after the standard ones", listOf("🐥", "⛄"), emoji(listed.drop(1 + localCount)))
    }

    // The server answers without default themes for this app.
    @Test
    fun serverAnswerWithoutDefaultThemes_listsTheStandardThemes() {
        instrumentation.runOnMainSync { controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account) }
        val listed = waitForThemes(1 + localCount)
        assertStandardThemesFirst(listed, "answer without default themes")
        assertEquals(1 + localCount, listed.size)
    }

    // The server answers with default themes: they are added, never in place of the standard ones.
    @Test
    fun serverAnswerWithDefaultThemes_addsThemAfterTheStandardOnes() {
        instrumentation.runOnMainSync {
            controller.generateEmojiPreviewThemes(arrayListOf(serverTheme(1, "🐥"), serverTheme(2, "⛄"), serverTheme(3, "💎", withWallpaper = false)), account)
        }
        val listed = waitForThemes(1 + localCount + 2)
        assertStandardThemesFirst(listed, "answer with default themes")
        assertEquals("server themes after, one without wallpaper colors left out", listOf("🐥", "⛄"), emoji(listed.drop(1 + localCount)))
        assertTrue("server themes with four variants", listed.drop(1 + localCount).all { it.chatTheme.items.size >= 4 })
    }

    // A second answer must not drop the row to fewer themes, even for a moment
    // (a screen opened in that moment kept one theme).
    @Test
    fun secondAnswer_neverShrinksTheRow() {
        instrumentation.runOnMainSync { controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account) }
        waitForThemes(1 + localCount)
        val sizes = ArrayList<Int>()
        val observer = NotificationCenter.NotificationCenterDelegate { _, _, _ -> sizes.add(controller.defaultEmojiThemes.size) }
        try {
            instrumentation.runOnMainSync {
                NotificationCenter.getGlobalInstance().addObserver(observer, NotificationCenter.emojiPreviewThemesChanged)
                controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
            }
            Thread.sleep(2000)
            MZGramScreens.log("theme list sizes after the second answer: $sizes")
            assertTrue("the row shrank: $sizes", sizes.none { it < 1 + localCount })
        } finally {
            instrumentation.runOnMainSync {
                NotificationCenter.getGlobalInstance().removeObserver(observer, NotificationCenter.emojiPreviewThemesChanged)
            }
        }
    }

    // Until the server's own themes are listed, the theme request asks for the
    // whole list (hash 0): with a saved hash the answer is "not modified" and
    // the server's themes would never come.
    @Test
    fun withoutServerThemes_asksTheServerForTheWholeList() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        BuildVars.LOGS_ENABLED = true
        controller.loadEmojiThemes()
        waitForThemes(1)
        val hashes = Theme::class.java.getDeclaredField("remoteThemesHash").also { it.isAccessible = true }.get(null) as LongArray
        val loading = Theme::class.java.getDeclaredField("loadingRemoteThemes").also { it.isAccessible = true }.get(null) as BooleanArray
        val savedHash = hashes[account]
        try {
            hashes[account] = 4_242_424_242L
            loading[account] = false
            val marker = "theme request check ${System.nanoTime()}"
            Log.i("theme", marker)
            instrumentation.runOnMainSync { Theme.loadRemoteThemes(account, true) }
            var hash: String? = null
            MZGramScreens.waitFor(10) {
                hash = themeRequestHashAfter(marker)
                hash != null
            }
            assertEquals("theme list asked for in full", "0", hash)
        } finally {
            hashes[account] = savedHash
        }
    }

    private fun themeRequestHashAfter(marker: String): String? {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "raw", "-s", "theme:I"))
        val lines = process.inputStream.bufferedReader().readLines()
        val start = lines.lastIndexOf(marker)
        if (start < 0) return null
        return lines.drop(start + 1).firstOrNull { it.startsWith("loading remote themes, hash ") }
            ?.removePrefix("loading remote themes, hash ")?.trim()
    }

    private fun openAppearance(): ThemeActivity {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val fragment = ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)
        MZGramScreens.open(fragment)
        assertTrue("appearance shown", MZGramScreens.waitFor(30) { fragment.fragmentView?.isShown == true })
        instrumentation.runOnMainSync {
            val list = ThemeActivity::class.java.getDeclaredField("listView").also { it.isAccessible = true }.get(fragment)
            val row = ThemeActivity::class.java.getDeclaredField("themeListRow2").also { it.isAccessible = true }.getInt(fragment)
            list.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(list, row)
        }
        return fragment
    }

    // On the main thread: waitFor runs its check there
    private fun rowOf(fragment: ThemeActivity) = MZGramScreens.findView(fragment.fragmentView, DefaultThemesPreviewCell::class.java)

    private fun itemsOf(cell: DefaultThemesPreviewCell): List<*> {
        val adapter = DefaultThemesPreviewCell::class.java.getDeclaredField("adapter").also { it.isAccessible = true }.get(cell)
        return adapter.javaClass.getField("items").get(adapter) as List<*>? ?: emptyList<Any>()
    }

    private fun selectedOf(cell: DefaultThemesPreviewCell) =
        DefaultThemesPreviewCell::class.java.getDeclaredField("selectedPosition").also { it.isAccessible = true }.getInt(cell)

    // The row's list, through reflection: the test classpath has no androidx.core
    private fun rowList(cell: DefaultThemesPreviewCell): Any =
        DefaultThemesPreviewCell::class.java.getDeclaredField("recyclerView").also { it.isAccessible = true }.get(cell)

    private fun scrollRow(cell: DefaultThemesPreviewCell, position: Int) {
        val list = rowList(cell)
        list.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(list, position)
    }

    // A tap on a theme of the row, as the row's own click listener gets it
    private fun tapRow(cell: DefaultThemesPreviewCell, position: Int): Boolean {
        val list = rowList(cell)
        val holder = list.javaClass.getMethod("findViewHolderForAdapterPosition", Int::class.javaPrimitiveType).invoke(list, position) ?: return false
        val view = holder.javaClass.getField("itemView").get(holder) as View
        val listener = RecyclerListView::class.java.getDeclaredField("onItemClickListener").also { it.isAccessible = true }.get(list)
        listener.javaClass.getMethod("onItemClick", View::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }.invoke(listener, view, position)
        return true
    }

    // What the user sees on Appearance after a fresh install with no answer:
    // the home theme, the standard themes and the custom one ("🎨").
    @Test
    fun appearance_freshInstall_showsTheStandardThemes() {
        controller.loadEmojiThemes()
        waitForThemes(1 + localCount)
        val fragment = openAppearance()
        val expected = 1 + localCount + 1
        var shown = 0
        MZGramScreens.waitFor(15) { shown = rowOf(fragment)?.let { itemsOf(it).size } ?: 0; shown >= expected }
        Thread.sleep(1500)
        MZGramScreens.capture("appearance-standard-themes")
        MZGramScreens.log("appearance themes on the screen: $shown")
        assertEquals("themes in the color theme row", expected, shown)
        instrumentation.runOnMainSync { fragment.finishFragment() }
    }

    // A standard theme picked before the update (its accent is saved with the
    // theme) is still the selected one when Appearance opens again.
    @Test
    fun appearance_themeChosenBefore_isSelectedAfterReopening() {
        controller.loadEmojiThemes()
        waitForThemes(1 + localCount)
        val fragment = openAppearance()
        assertTrue("the row", MZGramScreens.waitFor(15) { rowOf(fragment)?.let { itemsOf(it).size > localCount } ?: false })
        val chosen = 3
        try {
            instrumentation.runOnMainSync { scrollRow(rowOf(fragment)!!, chosen) }
            Thread.sleep(800)
            var tapped = false
            instrumentation.runOnMainSync { tapped = tapRow(rowOf(fragment)!!, chosen) }
            assertTrue("theme $chosen tapped", tapped)
            Thread.sleep(1500)
            val active = Theme.getActiveTheme()
            MZGramScreens.log("picked theme $chosen: ${active.key} accent ${active.currentAccentId}")
            instrumentation.runOnMainSync { fragment.finishFragment() }
            Thread.sleep(800)

            val again = openAppearance()
            var selected = -1
            MZGramScreens.waitFor(15) { selected = rowOf(again)?.let { selectedOf(it) } ?: -1; selected == chosen }
            MZGramScreens.capture("appearance-theme-chosen-before")
            assertEquals("the picked standard theme is selected after reopening", chosen, selected)
            instrumentation.runOnMainSync { again.finishFragment() }
        } finally {
            // Back to the home theme for the other tests
            val back = openAppearance()
            MZGramScreens.waitFor(15) { rowOf(back) != null }
            instrumentation.runOnMainSync { scrollRow(rowOf(back)!!, 0) }
            Thread.sleep(800)
            instrumentation.runOnMainSync { tapRow(rowOf(back)!!, 0) }
            Thread.sleep(1000)
            instrumentation.runOnMainSync { back.finishFragment() }
        }
    }

    // The list changes while Appearance is open: the row follows it, both
    // ways, without reopening the screen.
    @Test
    fun appearance_rowFollowsTheListWhileItIsOpen() {
        controller.loadEmojiThemes()
        waitForThemes(1 + localCount)
        val fragment = openAppearance()
        var opened = 0
        assertTrue("the color theme row", MZGramScreens.waitFor(15) { opened = rowOf(fragment)?.let { itemsOf(it).size } ?: 0; opened > 0 })

        instrumentation.runOnMainSync {
            controller.defaultEmojiThemes.clear()
            controller.defaultEmojiThemes.addAll(MZGramDefaultThemes.homeOnly(account))
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiPreviewThemesChanged)
        }
        var homeOnly = 0
        MZGramScreens.waitFor(10) { homeOnly = rowOf(fragment)?.let { itemsOf(it).size } ?: 0; homeOnly == 2 }

        instrumentation.runOnMainSync { controller.generateEmojiPreviewThemes(arrayListOf(serverTheme(1, "🐥")), account) }
        val expected = 1 + localCount + 1 + 1
        var full = 0
        MZGramScreens.waitFor(15) { full = rowOf(fragment)?.let { itemsOf(it).size } ?: 0; full == expected }
        MZGramScreens.log("appearance themes: opened with $opened, home theme only $homeOnly, after the answer $full")
        assertEquals("themes in the row after the list dropped to the home theme", 2, homeOnly)
        assertEquals("themes in the row after the answer", expected, full)
        instrumentation.runOnMainSync { fragment.finishFragment() }
    }
}
