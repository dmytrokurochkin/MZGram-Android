package org.telegram.messenger.mzgram.test

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import org.telegram.ui.DefaultThemesPreviewCell
import org.telegram.ui.ThemeActivity

// Settings > Chat settings > Color theme: the standard themes are listed
// even when the server's theme list has no default themes in it (or has not
// come yet -- the emulator has no connection at all).
class MZGramThemesTest {

    private val account = 0
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MediaDataController.getInstance(account)

    private fun themes(): List<ChatThemeBottomSheet.ChatThemeItem> {
        var result = listOf<ChatThemeBottomSheet.ChatThemeItem>()
        instrumentation.runOnMainSync { result = ArrayList(controller.defaultEmojiThemes) }
        return result
    }

    // The home theme's own themes: Classic, Day, Night, Tinted.
    private fun homeThemeKeys(item: ChatThemeBottomSheet.ChatThemeItem) =
        item.chatTheme.items.mapNotNull { it.themeInfo?.key }

    @Test
    fun serverWithoutDefaultThemes_keepsTheStandardThemes() {
        instrumentation.runOnMainSync {
            controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
        }
        assertTrue("themes listed", MZGramScreens.waitFor(10) { controller.defaultEmojiThemes.isNotEmpty() })
        val home = themes().first()
        assertEquals("the home theme first", "🏠", home.chatTheme.emoticonOrSlug)
        assertEquals(listOf("Blue", "Day", "Night", "Dark Blue"), homeThemeKeys(home))
    }

    // Only the home theme is listed (no default themes saved from the
    // server yet), but an older theme list hash is saved. Asking with that
    // hash gets "not modified" back, and the list then never fills. The
    // request must ask for the whole list (hash 0).
    @Test
    fun homeThemeOnly_asksTheServerForTheWholeList() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        BuildVars.LOGS_ENABLED = true
        val hashes = Theme::class.java.getDeclaredField("remoteThemesHash").also { it.isAccessible = true }.get(null) as LongArray
        val loading = Theme::class.java.getDeclaredField("loadingRemoteThemes").also { it.isAccessible = true }.get(null) as BooleanArray
        val savedHash = hashes[account]
        try {
            hashes[account] = 4_242_424_242L
            loading[account] = false
            val marker = "theme request check ${System.nanoTime()}"
            Log.i("theme", marker)
            instrumentation.runOnMainSync {
                controller.defaultEmojiThemes.clear()
                controller.defaultEmojiThemes.addAll(MZGramDefaultThemes.homeOnly(account))
                Theme.loadRemoteThemes(account, true)
            }
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

    // The hash of the first theme list request logged after the marker,
    // from the app's own log.
    private fun themeRequestHashAfter(marker: String): String? {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "raw", "-s", "theme:I"))
        val lines = process.inputStream.bufferedReader().readLines()
        val start = lines.lastIndexOf(marker)
        if (start < 0) return null
        return lines.drop(start + 1).firstOrNull { it.startsWith("loading remote themes, hash ") }
            ?.removePrefix("loading remote themes, hash ")?.trim()
    }

    @Test
    fun appearance_showsTheStandardThemes() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        instrumentation.runOnMainSync {
            controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
        }
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val fragment = ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)
        MZGramScreens.open(fragment)
        assertTrue("appearance shown", MZGramScreens.waitFor(30) { fragment.fragmentView?.isShown == true })

        // The color theme row, scrolled into view.
        instrumentation.runOnMainSync {
            val list = ThemeActivity::class.java.getDeclaredField("listView").also { it.isAccessible = true }.get(fragment)
            val row = ThemeActivity::class.java.getDeclaredField("themeListRow2").also { it.isAccessible = true }.getInt(fragment)
            list.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(list, row)
        }
        var shown = 0
        val listed = MZGramScreens.waitFor(15) {
            val cell = MZGramScreens.findView(fragment.fragmentView, DefaultThemesPreviewCell::class.java)
            shown = cell?.let { itemsOf(it).size } ?: 0
            shown > 0
        }
        Thread.sleep(1500)
        MZGramScreens.capture("appearance-themes")
        MZGramScreens.log("appearance themes: $shown, ${themes().map { it.chatTheme.emoticonOrSlug }}")
        assertTrue("the color theme row lists themes", listed)
    }

    // The chat themes as account.getChatThemes gives them (the set the chat
    // theme picker shows): an emoticon with a light and a dark setting each,
    // every setting with its wallpaper colors, saved where ChatThemeController
    // keeps them, fresh, so no request is needed.
    private val chatThemeEmoticons = listOf("🐥", "⛄", "💎", "👨‍🏫", "🌷", "💜", "🎄", "🎮")

    private fun seedChatThemes() {
        val editor = ApplicationLoader.applicationContext.getSharedPreferences("chatthemeconfig_$account", Context.MODE_PRIVATE).edit().clear()
        chatThemeEmoticons.forEachIndexed { i, emoticon ->
            val theme = TLRPC.TL_theme()
            theme.id = 5_000_000_000L + i
            theme.access_hash = 1
            theme.slug = "mzgram-test-$i"
            theme.title = emoticon
            theme.for_chat = true
            theme.emoticon = emoticon
            theme.flags = theme.flags or 8 or 64
            for (night in listOf(false, true)) {
                val settings = TLRPC.TL_themeSettings()
                settings.base_theme = if (night) TLRPC.TL_baseThemeNight() else TLRPC.TL_baseThemeClassic()
                settings.accent_color = 0xff3390ec.toInt() + i
                val colors = TLRPC.TL_wallPaperSettings()
                colors.flags = 1 or 16 or 32 or 64
                colors.background_color = 0xdbddbb + i
                colors.second_background_color = 0x6ba587
                colors.third_background_color = 0xd5d88d
                colors.fourth_background_color = 0x88b884
                val wallpaper = TLRPC.TL_wallPaperNoFile()
                wallpaper.id = 6_000_000_000L + i * 2 + (if (night) 1 else 0)
                wallpaper.flags = 4
                wallpaper.dark = night
                wallpaper.settings = colors
                settings.wallpaper = wallpaper
                settings.flags = settings.flags or 2
                theme.settings.add(settings)
            }
            val data = SerializedData(theme.objectSize)
            theme.serializeToStream(data)
            editor.putString("theme_$i", Utilities.bytesToHex(data.toByteArray()))
        }
        editor.putInt("count", chatThemeEmoticons.size)
            .putLong("hash", 4_242L)
            .putLong("lastReload", System.currentTimeMillis())
            .commit()
    }

    private fun clearChatThemes() {
        ApplicationLoader.applicationContext.getSharedPreferences("chatthemeconfig_$account", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // The server's theme list has no default themes for this app: the color
    // theme list still has the home theme and every chat theme, nine in all,
    // each with the four variants the list needs.
    @Test
    fun serverWithoutDefaultThemes_listsTheHomeAndChatThemes() {
        seedChatThemes()
        try {
            instrumentation.runOnMainSync {
                controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
            }
            val expected = 1 + chatThemeEmoticons.size
            MZGramScreens.waitFor(15) { controller.defaultEmojiThemes.size >= expected }
            val listed = themes()
            MZGramScreens.log("color themes: ${listed.size}, ${listed.map { it.chatTheme.emoticonOrSlug }}")
            assertEquals("themes in the color theme list", expected, listed.size)
            assertEquals("the home theme first", "🏠", listed.first().chatTheme.emoticonOrSlug)
            assertEquals(chatThemeEmoticons, listed.drop(1).map { it.chatTheme.emoticonOrSlug })
            assertTrue("four variants each", listed.all { it.chatTheme.items.size >= 4 })
        } finally {
            clearChatThemes()
        }
    }

    // The same on the screen: Settings > Chat settings, the color theme row.
    @Test
    fun appearance_listsAllStandardThemes() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        seedChatThemes()
        try {
            instrumentation.runOnMainSync {
                controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
            }
            val expected = 1 + chatThemeEmoticons.size
            MZGramScreens.waitFor(15) { controller.defaultEmojiThemes.size >= expected }
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
            var shown = 0
            MZGramScreens.waitFor(15) {
                val cell = MZGramScreens.findView(fragment.fragmentView, DefaultThemesPreviewCell::class.java)
                shown = cell?.let { itemsOf(it).size } ?: 0
                shown > expected
            }
            Thread.sleep(1500)
            MZGramScreens.capture("appearance-all-themes")
            MZGramScreens.log("appearance themes on the screen: $shown")
            // The themes and the custom one ("🎨") the screen adds at the end.
            assertEquals("themes in the color theme row", expected + 1, shown)
            instrumentation.runOnMainSync { fragment.finishFragment() }
        } finally {
            clearChatThemes()
        }
    }

    // The list already has the chat themes and the server again answers
    // without default themes: the list must not drop to the home theme
    // while the chat themes load again (a screen opened in that moment
    // showed one theme).
    @Test
    fun serverWithoutDefaultThemesAgain_keepsTheListWhole() {
        seedChatThemes()
        val sizes = ArrayList<Int>()
        val observer = NotificationCenter.NotificationCenterDelegate { _, _, _ -> sizes.add(controller.defaultEmojiThemes.size) }
        try {
            instrumentation.runOnMainSync {
                controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
            }
            val expected = 1 + chatThemeEmoticons.size
            assertTrue("chat themes listed", MZGramScreens.waitFor(15) { controller.defaultEmojiThemes.size >= expected })
            Thread.sleep(1000)
            instrumentation.runOnMainSync {
                NotificationCenter.getGlobalInstance().addObserver(observer, NotificationCenter.emojiPreviewThemesChanged)
                controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
            }
            Thread.sleep(2000)
            instrumentation.runOnMainSync {
                NotificationCenter.getGlobalInstance().removeObserver(observer, NotificationCenter.emojiPreviewThemesChanged)
            }
            MZGramScreens.log("theme list sizes after the second answer: $sizes")
            assertTrue("the list dropped to the home theme: $sizes", sizes.none { it < expected })
            assertEquals("themes in the color theme list", expected, themes().size)
        } finally {
            instrumentation.runOnMainSync {
                NotificationCenter.getGlobalInstance().removeObserver(observer, NotificationCenter.emojiPreviewThemesChanged)
            }
            clearChatThemes()
        }
    }

    // Appearance is open with the home theme only, then the chat themes
    // come: the color theme row shows them without reopening the screen.
    @Test
    fun appearance_rowShowsThemesThatComeWhileItIsOpen() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        seedChatThemes()
        try {
            instrumentation.runOnMainSync {
                controller.defaultEmojiThemes.clear()
                controller.defaultEmojiThemes.addAll(MZGramDefaultThemes.homeOnly(account))
            }
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
            fun shown(): Int {
                var count = 0
                instrumentation.runOnMainSync {
                    val cell = MZGramScreens.findView(fragment.fragmentView, DefaultThemesPreviewCell::class.java)
                    count = cell?.let { itemsOf(it).size } ?: 0
                }
                return count
            }
            assertTrue("the row with the home theme", MZGramScreens.waitFor(15) { shown() > 0 })
            MZGramScreens.log("appearance themes before the list came: ${shown()}")

            instrumentation.runOnMainSync {
                controller.generateEmojiPreviewThemes(ArrayList<TLRPC.TL_theme>(), account)
            }
            // The themes and the custom one ("🎨") the screen adds at the end.
            val expected = 1 + chatThemeEmoticons.size + 1
            MZGramScreens.waitFor(15) { shown() >= expected }
            Thread.sleep(1500)
            MZGramScreens.capture("appearance-themes-came-later")
            MZGramScreens.log("appearance themes after the list came: ${shown()}")
            assertEquals("themes in the color theme row", expected, shown())
            instrumentation.runOnMainSync { fragment.finishFragment() }
        } finally {
            clearChatThemes()
        }
    }

    private fun itemsOf(cell: DefaultThemesPreviewCell): List<*> {
        val adapter = DefaultThemesPreviewCell::class.java.getDeclaredField("adapter").also { it.isAccessible = true }.get(cell)
        return adapter.javaClass.getField("items").get(adapter) as List<*>? ?: emptyList<Any>()
    }
}
