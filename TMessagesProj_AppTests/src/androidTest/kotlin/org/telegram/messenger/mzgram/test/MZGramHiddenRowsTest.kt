package org.telegram.messenger.mzgram.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramSettingsRows
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.UItem
import org.telegram.ui.ProfileActivity
import org.telegram.ui.SettingsActivity

// Settings > MZGram > Interface > "Hide in Telegram's Settings": Premium,
// Stars, Gram Earnings, Business, Send a Gift, Wallet, Ask a Question, FAQ,
// Telegram Features and Privacy Policy, one switch each, all shown by
// default. A hidden row is gone from Telegram's Settings and from the
// settings search.
class MZGramHiddenRowsTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var saved: Set<String>? = null

    private fun preferences() = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        saved = preferences().getStringSet("hiddenSettingsRows", null)?.toSet()
        preferences().edit().remove("hiddenSettingsRows").commit()
        MZGramConfig.loadConfig(true)
    }

    @After
    fun tearDown() {
        preferences().edit().apply { if (saved == null) remove("hiddenSettingsRows") else putStringSet("hiddenSettingsRows", saved) }.commit()
        MZGramConfig.loadConfig(true)
    }

    private fun hideAll(hidden: Boolean) {
        for (row in MZGramSettingsRows.rows()) MZGramSettingsRows.setHidden(row.key, hidden)
    }

    @Test
    fun rows_shownByDefault_keptWhenHidden() {
        val rows = MZGramSettingsRows.rows()
        assertEquals(listOf("premium", "stars", "earnings", "business", "gift", "wallet", "ask", "faq", "features", "policy"), rows.map { it.key })
        assertEquals(listOf(11, 12, 13, 15, 16, 25, 17, 18, 23, 19), rows.map { it.settingsId })
        assertTrue("none hidden by default", rows.none { MZGramSettingsRows.hidden(it.key) })
        assertFalse(MZGramSettingsRows.helpHidden())

        MZGramSettingsRows.setHidden("premium", true)
        MZGramSettingsRows.setHidden("faq", true)
        MZGramConfig.loadConfig(true)
        assertTrue(MZGramSettingsRows.hidesRow(11))
        assertTrue(MZGramSettingsRows.hidesRow(18))
        assertFalse(MZGramSettingsRows.hidesRow(12))
        assertFalse("My Account is never hidden", MZGramSettingsRows.hidesRow(1))
        for (guid in 800..813) assertTrue("search $guid", MZGramSettingsRows.hidesSearchResult(guid))
        assertTrue(MZGramSettingsRows.hidesSearchResult(403))
        assertFalse(MZGramSettingsRows.hidesSearchResult(402))
        assertFalse(MZGramSettingsRows.hidesSearchResult(404))
        assertFalse("Language", MZGramSettingsRows.hidesSearchResult(400))
        assertFalse("the help group stays", MZGramSettingsRows.helpHidden())

        for (key in listOf("ask", "features", "policy")) MZGramSettingsRows.setHidden(key, true)
        assertTrue("the help group goes", MZGramSettingsRows.helpHidden())
        MZGramSettingsRows.setHidden("premium", false)
        MZGramConfig.loadConfig(true)
        assertFalse(MZGramSettingsRows.hidesRow(11))
        assertFalse(MZGramSettingsRows.hidesSearchResult(800))
    }

    private fun settingsItems(): List<UItem> {
        val settings = SettingsActivity()
        MZGramScreens.open(settings)
        assertTrue("settings shown", MZGramScreens.waitFor(30) { settings.fragmentView?.isShown == true })
        Thread.sleep(800)
        val all = ArrayList<UItem>()
        instrumentation.runOnMainSync {
            val list = SettingsActivity::class.java.getDeclaredField("listView").also { it.isAccessible = true }.get(settings)
            val adapter = list.javaClass.getMethod("getAdapter").invoke(list)
            val count = adapter.javaClass.getMethod("getItemCount").invoke(adapter) as Int
            val getItem = adapter.javaClass.getMethod("getItem", Int::class.javaPrimitiveType)
            for (i in 0 until count) {
                (getItem.invoke(adapter, i) as UItem?)?.let { all.add(it) }
            }
        }
        instrumentation.runOnMainSync { settings.finishFragment(false) }
        Thread.sleep(500)
        return all
    }

    private fun searchGuids(): List<Int> {
        val guids = ArrayList<Int>()
        instrumentation.runOnMainSync {
            val adapter = ProfileActivity.SearchAdapter(MZGramScreens.lastFragment()!!, MZGramScreens.lastFragment()!!.parentActivity)
            val array = ProfileActivity.SearchAdapter::class.java.getDeclaredField("searchArray").also { it.isAccessible = true }.get(adapter) as Array<*>
            for (result in array) {
                (result as ProfileActivity.SearchAdapter.SearchResult?)?.let { guids.add(it.guid) }
            }
        }
        return guids
    }

    @Test
    fun hiddenRows_goneFromSettingsAndSearch() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val help = LocaleController.getString(R.string.SettingsHelp)
        val hiddenIds = MZGramSettingsRows.rows().map { it.settingsId }.toSet()

        val shown = settingsItems()
        val shownIds = shown.map { it.id }
        MZGramScreens.log("settings rows shown: $shownIds")
        assertTrue("the help rows are there by default: $shownIds", shownIds.containsAll(listOf(17, 18, 23, 19)))
        assertTrue("the help header", shown.any { it.text?.toString() == help })
        assertTrue("the search finds the help rows", searchGuids().containsAll(listOf(402, 403, 404)))

        hideAll(true)
        val hidden = settingsItems()
        MZGramScreens.capture("settings-rows-hidden")
        val left = hidden.map { it.id }.filter { it in hiddenIds }
        assertTrue("no hidden row in Settings: $left", left.isEmpty())
        assertTrue("no help header", hidden.none { it.text?.toString() == help })
        assertTrue("My Account stays", hidden.any { it.id == 1 })
        val found = searchGuids()
        val leftInSearch = found.filter { it in 800..899 || it in listOf(402, 403, 404) }
        assertTrue("not in the search: $leftInSearch", leftInSearch.isEmpty())
        assertTrue("the rest of the search stays", found.contains(400))
        MZGramScreens.log("settings rows hidden: ${hidden.map { it.id }}, search ${found.size} results")

        hideAll(false)
        assertTrue("back after showing them", settingsItems().map { it.id }.containsAll(listOf(17, 18, 23, 19)))
    }
}
