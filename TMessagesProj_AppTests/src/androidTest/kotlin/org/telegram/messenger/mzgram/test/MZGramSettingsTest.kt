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

    private fun settingsItems(): ArrayList<UItem> {
        val items = ArrayList<UItem>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fillItems = MZGramSettingsActivity::class.java.getDeclaredMethod("fillItems", ArrayList::class.java, UniversalAdapter::class.java)
            fillItems.isAccessible = true
            fillItems.invoke(MZGramSettingsActivity(), items, null)
        }
        return items
    }

    // Every switch and button of the settings screen, by its constant.
    private fun allButtons(): Set<Int> = MZGramSettingsActivity::class.java.declaredFields
        .filter { it.name.startsWith("BUTTON_") }
        .map { it.isAccessible = true; it.getInt(null) }
        .toSet()

    // Settings > MZGram is grouped into sections by topic, in this order,
    // and every switch and button is in one of them -- none lost.
    @Test
    fun settings_areGroupedIntoSections() {
        val items = settingsItems()
        val headers = items.filter { it.viewType == UniversalAdapter.VIEW_TYPE_HEADER }.map { it.text.toString() }
        val expected = listOf(
            R.string.MZGramSectionArchive,
            R.string.MZGramSectionPrivacy,
            R.string.MZGramSectionGhostMode,
            R.string.MZGramSectionMessageMenu,
            R.string.MZGramSectionMediaAndCalls,
            R.string.MZGramSectionInterface,
            R.string.MZGramSectionAdsAndFilters,
            R.string.MZGramSectionOther,
        ).map { LocaleController.getString(it) }
        assertEquals(expected, headers)

        val shown = items.filter { it.viewType != UniversalAdapter.VIEW_TYPE_HEADER && it.viewType != UniversalAdapter.VIEW_TYPE_SHADOW }.map { it.id }
        assertEquals("each switch once", shown.size, shown.toSet().size)
        assertEquals("every switch and button is shown", allButtons(), shown.toSet())
        assertEquals("39 switches and buttons", 39, shown.size)

        // No section is empty.
        var lastWasHeader = false
        for (item in items) {
            if (item.viewType == UniversalAdapter.VIEW_TYPE_HEADER) {
                assertFalse("empty section before ${item.text}", lastWasHeader)
                lastWasHeader = true
            } else if (item.viewType != UniversalAdapter.VIEW_TYPE_SHADOW) {
                lastWasHeader = false
            }
        }
        assertFalse("last section is empty", lastWasHeader)
        MZGramScreens.log("settings sections: $headers, ${shown.size} switches and buttons")
    }

    // The settings screen as the user sees it, top to bottom.
    @Test
    fun settings_screenshots() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        activity = MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val fragment = MZGramSettingsActivity()
        MZGramScreens.open(fragment)
        assertTrue("settings shown", MZGramScreens.waitFor(30) { (MZGramScreens.listViewOf(fragment)?.childCount ?: 0) > 0 })
        Thread.sleep(1000)
        for (page in 1..8) {
            MZGramScreens.capture("settings-$page")
            var more = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val list = MZGramScreens.listViewOf(fragment)!!
                more = list.canScrollVertically(1)
                if (more) list.scrollBy(0, list.height - list.height / 8)
            }
            if (!more) break
            Thread.sleep(500)
        }
    }
}
