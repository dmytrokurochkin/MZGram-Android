package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.mzgram.MZGramSettingsActivity

// Settings > MZGram: which switches exist and what they do.
class MZGramSettingsTest {

    private val account = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preferences() = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)

    // The "hide others' online status" switch is gone: another user's
    // status is shown as it is, also for someone who had the switch on.
    @Test
    fun othersOnlineStatus_isAlwaysShown() {
        preferences().edit().putBoolean("hideOthersOnlineStatus", true).commit()
        MZGramConfig.loadConfig(true)

        assertTrue("no such setting", MZGramConfig::class.java.declaredFields.none { it.name == "hideOthersOnlineStatus" })
        assertEquals("no such switch", 0, context.resources.getIdentifier("MZGramHideOthersOnlineStatus", "string", context.packageName))
        assertFalse("the old value is cleared", preferences().contains("hideOthersOnlineStatus"))

        val user = TLRPC.TL_user().apply {
            id = 7_600_000_001L
            first_name = "MZGram test other"
            status = TLRPC.TL_userStatusOnline().apply {
                expires = ConnectionsManager.getInstance(account).currentTime + 300
            }
        }
        val online = BooleanArray(1)
        val text = LocaleController.formatUserStatus(account, user, online)
        assertTrue("shown online", online[0])
        assertEquals(LocaleController.getString(R.string.Online), text)
    }

    private var activity: Activity? = null

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity?.finish() }
    }

    private fun settingsItems(section: Int = MZGramSettingsActivity.SECTION_MAIN): ArrayList<UItem> {
        val items = ArrayList<UItem>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fillItems = MZGramSettingsActivity::class.java.getDeclaredMethod("fillItems", ArrayList::class.java, UniversalAdapter::class.java)
            fillItems.isAccessible = true
            fillItems.invoke(MZGramSettingsActivity(section), items, null)
        }
        return items
    }

    // Every switch and button of the settings screen, by its constant.
    private fun allButtons(): Set<Int> = MZGramSettingsActivity::class.java.declaredFields
        .filter { it.name.startsWith("BUTTON_") }
        .map { it.isAccessible = true; it.getInt(null) }
        .toSet()

    // Settings > MZGram lists the topics, in this order, each with a short
    // description; every switch and button is on exactly one topic's page --
    // none lost.
    @Test
    fun settings_areGroupedIntoTopicPages() {
        val main = settingsItems()
        val rows = main.filter { it.id >= MZGramSettingsActivity.SECTION_ROW_ID }
        val expected = listOf(
            R.string.MZGramSectionArchive,
            R.string.MZGramSectionGhostMode,
            R.string.MZGramSectionMessageMenu,
            R.string.MZGramSectionMediaAndCalls,
            R.string.MZGramSectionInterface,
            R.string.MZGramSectionAdsAndFilters,
            R.string.MZGramSectionOther,
        ).map { LocaleController.getString(it) }
        assertEquals(expected, rows.map { it.text.toString() })
        assertEquals("one row per topic", (0 until MZGramSettingsActivity.SECTIONS_COUNT).map { MZGramSettingsActivity.SECTION_ROW_ID + it }, rows.map { it.id })
        assertTrue("every topic has a description", rows.all { !it.subtext.isNullOrEmpty() })
        assertTrue("no switches on the main page", main.none { it.id in allButtons() })

        val shown = ArrayList<Int>()
        val perTopic = ArrayList<String>()
        for (section in 0 until MZGramSettingsActivity.SECTIONS_COUNT) {
            val ids = settingsItems(section).filter { it.viewType != UniversalAdapter.VIEW_TYPE_HEADER && it.viewType != UniversalAdapter.VIEW_TYPE_SHADOW }.map { it.id }
            assertTrue("topic $section is not empty", ids.isNotEmpty())
            shown.addAll(ids)
            perTopic.add("${expected[section]}=${ids.size}")
        }
        assertEquals("each switch once", shown.size, shown.toSet().size)
        assertEquals("every switch and button is shown", allButtons(), shown.toSet())
        assertEquals("46 switches and buttons", 46, shown.size)
        MZGramScreens.log("settings topics: $perTopic, ${shown.size} switches and buttons")
    }

    // The main page and a few topic pages as the user sees them; tapping a
    // topic opens its page and Back returns to the main page.
    @Test
    fun settings_screenshots() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        activity = MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val problems = MZGramScreens.settingsPages("settings", listOf(
            MZGramSettingsActivity.SECTION_ARCHIVE,
            MZGramSettingsActivity.SECTION_MESSAGE_MENU,
            MZGramSettingsActivity.SECTION_INTERFACE,
        ))
        assertTrue(problems.joinToString(), problems.isEmpty())
    }
}
