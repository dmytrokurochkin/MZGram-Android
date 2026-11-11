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

    private fun button(name: String): Int = MZGramSettingsActivity::class.java.getDeclaredField(name)
        .let { it.isAccessible = true; it.getInt(null) }

    // Rows that appear only under a switch that is off by default; each new
    // one goes here by name, so a row that is never shown cannot slip through.
    private val underSwitch = listOf(
        "BUTTON_CAMERA_X_WIDE", "BUTTON_CAMERA_X_ZOOM",
        "BUTTON_DELETED_DIALOGS_IN_MAIN_LIST", "BUTTON_DELETED_DIALOGS_OPEN",
        "BUTTON_DOUBLE_TAP_OWN", "BUTTON_DOUBLE_TAP_OTHER",
        "BUTTON_DRAWER_GLASS", "BUTTON_DRAWER_HEADER", "BUTTON_DRAWER_ITEMS", "BUTTON_DRAWER_LARGE_AVATAR", "BUTTON_DRAWER_SHOW_QR",
        "BUTTON_ICON_PACKS", "BUTTON_MONET_PICK", "BUTTON_MONET_STYLE",
        "BUTTON_MUSIC_PLAYER_BAR", "BUTTON_MUSIC_PLAYER_STYLE",
        "BUTTON_OWN_LAST_SEEN_VALUE", "BUTTON_PASSCODE_ACCOUNTS_SETUP", "BUTTON_PILLS", "BUTTON_REGEX_FILTERS_RULES",
        "BUTTON_ROUND_VIDEO_ADVANCED", "BUTTON_ROUND_VIDEO_BITRATE", "BUTTON_ROUND_VIDEO_HIGH_FPS", "BUTTON_SHADOW_BAN_LIST",
    )

    // Rows this build or device does not offer at all, and reports shown only once there is one
    private fun notOffered(): Set<Int> {
        val names = mutableListOf("BUTTON_ENERGY_REPORT", "BUTTON_JANK_REPORT")
        if (!org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.isAvailable()) {
            names += listOf("BUTTON_PLUGINS", "BUTTON_PYTHON_RUNTIME")
        }
        if (!org.telegram.messenger.mzgram.MZGramMonet.available()) {
            names += "BUTTON_MONET_THEMES"
        }
        if (!org.telegram.messenger.mzgram.MZGramProxyAutoDisable.available()) {
            names += listOf("BUTTON_PROXY_OFF_VPN", "BUTTON_PROXY_OFF_MOBILE", "BUTTON_PROXY_OFF_WIFI")
        }
        return names.map { button(it) }.toSet()
    }

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
            R.string.MZGramSectionPrivacy,
            R.string.MZGramSectionChats,
            R.string.MZGramSectionMessageMenu,
            R.string.MZGramSectionMediaAndCalls,
            R.string.MZGramSectionInterface,
            R.string.MZGramSectionAppearance,
            R.string.MZGramSectionAdsAndFilters,
            // "Ask AI": its switch, the default site and "Sign out of AI sites"
            context.resources.getIdentifier("MZGramSectionAi", "string", context.packageName),
            R.string.MZGramSectionOther,
        ).map { if (it == 0) "(no AI topic)" else LocaleController.getString(it) }
        assertEquals(expected, rows.map { it.text.toString() })
        assertEquals("one row per topic", MZGramSettingsActivity.MAIN_PAGE_ORDER.map { MZGramSettingsActivity.SECTION_ROW_ID + it }, rows.map { it.id })
        assertTrue("every topic has a description", rows.all { !it.subtext.isNullOrEmpty() })
        assertTrue("no switches on the main page", main.none { it.id in allButtons() })

        val shown = ArrayList<Int>()
        val perTopic = ArrayList<String>()
        for (section in 0 until MZGramSettingsActivity.SECTIONS_COUNT) {
            // The archive size at the top of Archive is a view, and sliders are slider views, not switches
            val ids = settingsItems(section).filter {
                it.viewType != UniversalAdapter.VIEW_TYPE_HEADER &&
                it.viewType != UniversalAdapter.VIEW_TYPE_SHADOW &&
                it.viewType != UniversalAdapter.VIEW_TYPE_CUSTOM &&
                it.viewType != UniversalAdapter.VIEW_TYPE_INTSLIDE
            }.map { it.id }
            assertTrue("topic $section is not empty", ids.isNotEmpty())
            shown.addAll(ids)
            perTopic.add("$section=${ids.size}")
        }
        assertEquals("each switch once", shown.size, shown.toSet().size)
        assertTrue("every switch and button is defined", allButtons().containsAll(shown))
        // None lost: every switch and button is shown, but those under an off switch and those not offered here
        val maybe = notOffered()
        assertEquals("every switch and button is shown", allButtons() - underSwitch.map { button(it) }.toSet() - maybe, shown.toSet() - maybe)
        // 78 in base, plus added switches and buttons in features 68-85, N16, N12 (subtitles) and N14 (Persian calendar),
        // less the DoH provider row (it changed no request), plus the switches of local folders and exact last seen;
        // a build with plugins (Python) also has Plugins and Python for plugins
        val expectedCount = 170 + if (org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.isAvailable()) 2 else 0
        assertEquals("$expectedCount switches and buttons", expectedCount, shown.size)
        MZGramScreens.log("settings topics: $perTopic, ${shown.size} switches and buttons")
    }

    // The test switch that only proved settings survive a restart is gone:
    // no switch, no setting, no text.
    @Test
    fun settings_haveNoTestSwitch() {
        val fields = MZGramSettingsActivity::class.java.declaredFields.map { it.name }
        assertTrue("no test button: $fields", fields.none { it.contains("TEST") })
        assertTrue("no test setting", org.telegram.messenger.mzgram.MZGramConfig::class.java.declaredFields.none { it.name == "testToggle" })
        assertTrue("no test text", R.string::class.java.fields.none { it.name.startsWith("MZGramTestToggle") })
        val other = settingsItems(MZGramSettingsActivity.SECTION_OTHER).map { it.text?.toString() ?: "" }
        assertTrue("Other: $other", other.none { it.contains("Test switch") || it.contains("Тестовий") })
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
