package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
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

    private fun itemsOf(cell: DefaultThemesPreviewCell): List<*> {
        val adapter = DefaultThemesPreviewCell::class.java.getDeclaredField("adapter").also { it.isAccessible = true }.get(cell)
        return adapter.javaClass.getField("items").get(adapter) as List<*>? ?: emptyList<Any>()
    }
}
