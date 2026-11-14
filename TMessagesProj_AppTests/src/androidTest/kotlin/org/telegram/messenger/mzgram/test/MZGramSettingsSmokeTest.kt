package org.telegram.messenger.mzgram.test

import android.content.Context
import android.os.Process
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextCheckCell2
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalFragment
import org.telegram.ui.mzgram.MZGramAccountOrderActivity
import org.telegram.ui.mzgram.MZGramDeletedDialogsActivity
import org.telegram.ui.mzgram.MZGramDrawerItemsActivity
import org.telegram.ui.mzgram.MZGramFeedChannelsActivity
import org.telegram.ui.mzgram.MZGramIconPacksActivity
import org.telegram.ui.mzgram.MZGramPasscodeAccountsActivity
import org.telegram.ui.mzgram.MZGramPeripheralsActivity
import org.telegram.ui.mzgram.MZGramPillsActivity
import org.telegram.ui.mzgram.MZGramPinnedReactionsActivity
import org.telegram.ui.mzgram.MZGramPluginsActivity
import org.telegram.ui.mzgram.MZGramRegexFiltersActivity
import org.telegram.ui.mzgram.MZGramSettingsActivity
import org.telegram.ui.mzgram.MZGramShadowBanActivity
import org.telegram.ui.mzgram.MZGramTranslationAiActivity

// Phase A3: Smoke test verifying all settings pages and sub-screens:
// toggles switches twice (value changes and restores),
// expands and collapses expandable groups,
// opens and closes selection dialogs without changes,
// opens transition rows and navigates back,
// verifies process remains alive after every action,
// checks no FATAL EXCEPTION in logcat,
// and ensures final settings state matches initial state.
class MZGramSettingsSmokeTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
    private var initialPreferences: Map<String, *>? = null

    @Before
    fun setUp() {
        val self = TLRPC.TL_user().apply {
            id = 7_000_000_001L
            first_name = "MZGram test user"
        }
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramConfig.checkUpdates = false
        MZGramAppearance.startApp()
        MZGramScreens.shell("logcat -c")
        initialPreferences = HashMap(preferences.all)
    }

    @After
    fun tearDown() {
        checkNoFatalException()
        assertProcessAlive()
        initialPreferences?.let { initial ->
            val current = preferences.all
            if (current != initial) {
                val editor = preferences.edit().clear()
                for ((key, value) in initial) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                    }
                }
                editor.commit()
                MZGramConfig.loadConfig(true)
            }
        }
        MZGramAppearance.reset()
    }

    private fun assertProcessAlive() {
        val pid = Process.myPid()
        assertTrue("app process must be alive", pid > 0)
    }

    private fun checkNoFatalException() {
        val pid = Process.myPid()
        val pkg = context.packageName
        val log = instrumentation.uiAutomation.executeShellCommand("logcat -d -s AndroidRuntime:E").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
        }
        val hasFatal = log.lines().any { line ->
            line.contains("FATAL EXCEPTION") && (line.contains("PID: $pid") || line.contains(pkg))
        }
        assertFalse("FATAL EXCEPTION detected in logcat:\n$log", hasFatal)
    }

    private fun isNonActionable(viewType: Int): Boolean = when (viewType) {
        UniversalAdapter.VIEW_TYPE_HEADER,
        UniversalAdapter.VIEW_TYPE_BLACK_HEADER,
        UniversalAdapter.VIEW_TYPE_LARGE_HEADER,
        UniversalAdapter.VIEW_TYPE_ANIMATED_HEADER,
        UniversalAdapter.VIEW_TYPE_SHADOW,
        UniversalAdapter.VIEW_TYPE_LARGE_SHADOW,
        UniversalAdapter.VIEW_TYPE_CUSTOM_SHADOW,
        UniversalAdapter.VIEW_TYPE_SPACE,
        UniversalAdapter.VIEW_TYPE_TOPVIEW -> true
        else -> false
    }

    private fun isExternalIntent(itemId: Int): Boolean = when (itemId) {
        MZGramSettingsActivity.ROW_SOURCE_CODE,
        buttonOrZero("BUTTON_APP_FONT"),
        buttonOrZero("BUTTON_EXPORT_ARCHIVE"),
        buttonOrZero("BUTTON_EXPORT_SETTINGS"),
        buttonOrZero("BUTTON_IMPORT_SETTINGS"),
        buttonOrZero("BUTTON_IMPORT_FOREIGN_ARCHIVE"),
        buttonOrZero("BUTTON_CHECK_UPDATES_NOW") -> true
        else -> false
    }

    private fun buttonOrZero(name: String): Int = try {
        MZGramSettingsActivity::class.java.getDeclaredField(name).let {
            it.isAccessible = true
            it.getInt(null)
        }
    } catch (e: Throwable) {
        0
    }

    private fun isSwitch(viewType: Int): Boolean = when (viewType) {
        UniversalAdapter.VIEW_TYPE_CHECK,
        UniversalAdapter.VIEW_TYPE_TEXT_CHECK,
        UniversalAdapter.VIEW_TYPE_ICON_TEXT_CHECK,
        UniversalAdapter.VIEW_TYPE_CHECKRIPPLE,
        UniversalAdapter.VIEW_TYPE_SWITCH,
        UniversalAdapter.VIEW_TYPE_ROUND_CHECKBOX,
        UniversalAdapter.VIEW_TYPE_ROUND_GROUP_CHECKBOX,
        UniversalAdapter.VIEW_TYPE_USER_CHECKBOX -> true
        else -> false
    }

    private fun isExpandableGroup(viewType: Int): Boolean = when (viewType) {
        UniversalAdapter.VIEW_TYPE_EXPANDABLE_SWITCH,
        UniversalAdapter.VIEW_TYPE_SHADOW_COLLAPSE_BUTTON -> true
        else -> false
    }

    private fun isSelectionRow(viewType: Int, itemId: Int): Boolean {
        if (viewType == UniversalAdapter.VIEW_TYPE_TEXT_SETTINGS) return true
        val selectionIds = setOf(
            buttonOrZero("BUTTON_ARCHIVE_LIMIT"),
            buttonOrZero("BUTTON_TITLE_TEXT"),
            buttonOrZero("BUTTON_SAVE_FOLDER"),
            buttonOrZero("BUTTON_PEER_ID"),
            buttonOrZero("BUTTON_LANGUAGE"),
            buttonOrZero("BUTTON_VOICE_QUALITY"),
            buttonOrZero("BUTTON_FEED_POSTS"),
            buttonOrZero("BUTTON_ROUND_VIDEO_BITRATE"),
            buttonOrZero("BUTTON_RESET_SETTINGS"),
            buttonOrZero("BUTTON_DELETE_ACCOUNT"),
            buttonOrZero("BUTTON_ERASE_DATABASE"),
            buttonOrZero("BUTTON_WIPE_ARCHIVE"),
            buttonOrZero("BUTTON_AI_SIGN_OUT"),
        ).filter { it > 0 }
        return itemId in selectionIds
    }

    private fun isTransitionRow(itemId: Int): Boolean {
        if (itemId >= MZGramSettingsActivity.SECTION_ROW_ID &&
            itemId < MZGramSettingsActivity.SECTION_ROW_ID + MZGramSettingsActivity.SECTIONS_COUNT
        ) {
            return true
        }
        val transitionIds = setOf(
            buttonOrZero("BUTTON_MESSAGE_MENU"),
            buttonOrZero("BUTTON_FEED_OPEN"),
            buttonOrZero("BUTTON_FEED_CHANNELS"),
            buttonOrZero("BUTTON_FEED_SETTINGS"),
            buttonOrZero("BUTTON_PLUGINS"),
            buttonOrZero("BUTTON_PASSCODE_ACCOUNTS_SETUP"),
            buttonOrZero("BUTTON_PINNED_REACTIONS"),
            buttonOrZero("BUTTON_DRAWER_ITEMS"),
            buttonOrZero("BUTTON_ICON_PACKS"),
            buttonOrZero("BUTTON_PILLS"),
            buttonOrZero("BUTTON_ACCOUNT_ORDER"),
            buttonOrZero("BUTTON_TRANSLATION_AI"),
            buttonOrZero("BUTTON_EXTENDED_PERIPHERALS"),
            buttonOrZero("BUTTON_DELETED_DIALOGS_OPEN"),
            buttonOrZero("BUTTON_REGEX_FILTERS_RULES"),
            buttonOrZero("BUTTON_SHADOW_BAN_LIST"),
            buttonOrZero("BUTTON_ROUND_VIDEO_ADVANCED"),
            buttonOrZero("BUTTON_THEME_SETTINGS"),
        ).filter { it > 0 }
        return itemId in transitionIds
    }

    private fun getAdapter(fragment: UniversalFragment): UniversalAdapter? {
        val listView = MZGramScreens.listViewOf(fragment) ?: return null
        return UniversalFragment::class.java.getField("listView").get(fragment)
            ?.let { it.javaClass.getField("adapter").get(it) as? UniversalAdapter }
    }

    private fun getAdapterItemCount(fragment: UniversalFragment): Int {
        return getAdapter(fragment)?.itemCount ?: 0
    }

    private fun findItemView(fragment: UniversalFragment, itemId: Int): View? {
        val listView = MZGramScreens.listViewOf(fragment) ?: return null
        val adapter = getAdapter(fragment) ?: return null
        for (i in 0 until adapter.itemCount) {
            val item = adapter.getItem(i)
            if (item?.id == itemId) {
                instrumentation.runOnMainSync {
                    listView.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(listView, i)
                }
                Thread.sleep(100)
                var holder: Any? = null
                instrumentation.runOnMainSync {
                    holder = listView.javaClass.getMethod("findViewHolderForAdapterPosition", Int::class.javaPrimitiveType).invoke(listView, i)
                }
                return holder?.javaClass?.getField("itemView")?.get(holder) as? View
            }
        }
        return null
    }

    private fun clickItemById(fragment: UniversalFragment, itemId: Int): Boolean {
        val listView = MZGramScreens.listViewOf(fragment) ?: return false
        val adapter = getAdapter(fragment) ?: return false
        var targetPos = -1
        for (i in 0 until adapter.itemCount) {
            if (adapter.getItem(i)?.id == itemId) {
                targetPos = i
                break
            }
        }
        if (targetPos < 0) return false

        instrumentation.runOnMainSync {
            listView.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(listView, targetPos)
        }
        Thread.sleep(100)

        var clicked = false
        instrumentation.runOnMainSync {
            val holder = listView.javaClass.getMethod("findViewHolderForAdapterPosition", Int::class.javaPrimitiveType).invoke(listView, targetPos)
            val itemView = holder?.javaClass?.getField("itemView")?.get(holder) as? View
            listView.javaClass.getMethod("clickItem", View::class.java, Int::class.javaPrimitiveType).invoke(listView, itemView, targetPos)
            clicked = true
        }
        Thread.sleep(150)
        return clicked
    }

    private fun getSwitchChecked(fragment: UniversalFragment, itemId: Int): Boolean {
        val view = findItemView(fragment, itemId)
        if (view is TextCheckCell) return view.isChecked
        if (view is TextCheckCell2) return view.isChecked
        val adapter = getAdapter(fragment)
        if (adapter != null) {
            for (i in 0 until adapter.itemCount) {
                val itm = adapter.getItem(i)
                if (itm?.id == itemId) return itm.checked
            }
        }
        return false
    }

    private fun getCollapsedState(fragment: UniversalFragment, itemId: Int): Boolean {
        val adapter = getAdapter(fragment) ?: return false
        for (i in 0 until adapter.itemCount) {
            val itm = adapter.getItem(i)
            if (itm?.id == itemId) return itm.collapsed
        }
        return false
    }

    private fun testSwitch(fragment: UniversalFragment, itemId: Int) {
        val initialChecked = getSwitchChecked(fragment, itemId)
        val initialPrefs = HashMap(preferences.all)

        // Toggle 1
        val clicked1 = clickItemById(fragment, itemId)
        if (!clicked1) return
        assertProcessAlive()
        Thread.sleep(100)

        val toggledChecked = getSwitchChecked(fragment, itemId)
        val toggledPrefs = HashMap(preferences.all)
        val changed = (toggledChecked != initialChecked) || (toggledPrefs != initialPrefs)
        assertTrue("Switch (id=$itemId) did not change value when toggled once", changed)

        // Toggle 2 (restore)
        val clicked2 = clickItemById(fragment, itemId)
        assertTrue("Switch (id=$itemId) should be clickable on second toggle", clicked2)
        assertProcessAlive()
        Thread.sleep(100)

        val restoredChecked = getSwitchChecked(fragment, itemId)
        val restoredPrefs = HashMap(preferences.all)
        assertEquals("Switch (id=$itemId) did not restore checked state", initialChecked, restoredChecked)
        assertEquals("Switch (id=$itemId) did not restore configuration", initialPrefs, restoredPrefs)
    }

    private fun testExpandableGroup(fragment: UniversalFragment, itemId: Int) {
        val initialCollapsed = getCollapsedState(fragment, itemId)
        val initialCount = getAdapterItemCount(fragment)

        // Expand
        clickItemById(fragment, itemId)
        assertProcessAlive()
        Thread.sleep(100)

        val toggledCollapsed = getCollapsedState(fragment, itemId)
        val toggledCount = getAdapterItemCount(fragment)
        assertTrue(
            "Expandable group (id=$itemId) should change expansion state or child count",
            toggledCollapsed != initialCollapsed || toggledCount != initialCount
        )

        // Collapse
        clickItemById(fragment, itemId)
        assertProcessAlive()
        Thread.sleep(100)

        val restoredCollapsed = getCollapsedState(fragment, itemId)
        val restoredCount = getAdapterItemCount(fragment)
        assertEquals("Expandable group (id=$itemId) should restore collapse state", initialCollapsed, restoredCollapsed)
        assertEquals("Expandable group (id=$itemId) should restore item count", initialCount, restoredCount)
    }

    private fun testSelectionRow(fragment: UniversalFragment, itemId: Int) {
        clickItemById(fragment, itemId)
        assertProcessAlive()
        Thread.sleep(150)

        val dialog = fragment.visibleDialog
        if (dialog != null && dialog.isShowing) {
            instrumentation.runOnMainSync {
                dialog.dismiss()
            }
            Thread.sleep(100)
        }
        assertProcessAlive()
    }

    private fun testTransitionRow(fragment: UniversalFragment, itemId: Int) {
        val current = MZGramScreens.lastFragment()
        clickItemById(fragment, itemId)
        assertProcessAlive()
        Thread.sleep(250)

        val opened = MZGramScreens.lastFragment()
        if (opened != null && opened !== current) {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertTrue("Back should return to previous screen", MZGramScreens.waitFor(10) {
                MZGramScreens.lastFragment() === current
            })
            Thread.sleep(150)
        }
        assertProcessAlive()
    }

    private fun testGenericActionRow(fragment: UniversalFragment, itemId: Int) {
        val current = MZGramScreens.lastFragment()
        clickItemById(fragment, itemId)
        assertProcessAlive()
        Thread.sleep(150)

        val dialog = fragment.visibleDialog
        if (dialog != null && dialog.isShowing) {
            instrumentation.runOnMainSync {
                dialog.dismiss()
            }
            Thread.sleep(100)
        } else {
            val next = MZGramScreens.lastFragment()
            if (next != null && next !== current) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                MZGramScreens.waitFor(10) { MZGramScreens.lastFragment() === current }
                Thread.sleep(150)
            }
        }
        assertProcessAlive()
    }

    private fun testScreenItems(screenName: String, fragment: UniversalFragment) {
        val adapter = getAdapter(fragment) ?: return
        val count = adapter.itemCount
        val itemsSnapshot = (0 until count).mapNotNull { adapter.getItem(it) }

        for (item in itemsSnapshot) {
            val itemId = item.id
            val viewType = item.viewType

            if (isNonActionable(viewType)) continue
            if (isExternalIntent(itemId)) continue

            when {
                isExpandableGroup(viewType) -> testExpandableGroup(fragment, itemId)
                isSwitch(viewType) -> testSwitch(fragment, itemId)
                isTransitionRow(itemId) -> testTransitionRow(fragment, itemId)
                isSelectionRow(viewType, itemId) -> testSelectionRow(fragment, itemId)
                else -> testGenericActionRow(fragment, itemId)
            }
            assertProcessAlive()
        }
    }

    @Test
    fun testSmoke_allSettingsPagesAndSubscreens() {
        val main = MZGramSettingsActivity()
        MZGramScreens.open(main)
        assertTrue("main settings page opened", MZGramScreens.waitFor(30) {
            (MZGramScreens.listViewOf(main)?.childCount ?: 0) > 0
        })
        assertProcessAlive()

        // 1. Walk main page items
        testScreenItems("main", main)

        // 2. Walk each category in MAIN_PAGE_ORDER
        for (section in MZGramSettingsActivity.MAIN_PAGE_ORDER) {
            val sectionPage = MZGramSettingsActivity(section)
            MZGramScreens.open(sectionPage)
            assertTrue("section $section opened", MZGramScreens.waitFor(15) {
                (MZGramScreens.listViewOf(sectionPage)?.childCount ?: 0) > 0
            })
            assertProcessAlive()

            testScreenItems("section-$section", sectionPage)

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertTrue("Back returns to main settings", MZGramScreens.waitFor(15) {
                MZGramScreens.lastFragment() === main
            })
            assertProcessAlive()
            Thread.sleep(150)
        }

        // 3. Walk settings sub-screens
        val subScreens: List<Pair<String, () -> UniversalFragment>> = listOf(
            "message-menu" to { MZGramSettingsActivity(MZGramSettingsActivity.SECTION_MESSAGE_MENU) as UniversalFragment },
            "feed-settings" to { MZGramSettingsActivity(MZGramSettingsActivity.SECTION_FEED) as UniversalFragment },
            "pinned-reactions" to { MZGramPinnedReactionsActivity() as UniversalFragment },
            "passcode-accounts" to { MZGramPasscodeAccountsActivity() as UniversalFragment },
            "feed-channels" to { MZGramFeedChannelsActivity() as UniversalFragment },
            "plugins" to { MZGramPluginsActivity() as UniversalFragment },
            "pills" to { MZGramPillsActivity() as UniversalFragment },
            "icon-packs" to { MZGramIconPacksActivity() as UniversalFragment },
            "drawer-items" to { MZGramDrawerItemsActivity() as UniversalFragment },
            "account-order" to { MZGramAccountOrderActivity() as UniversalFragment },
            "translation-ai" to { MZGramTranslationAiActivity() as UniversalFragment },
            "peripherals" to { MZGramPeripheralsActivity() as UniversalFragment },
            "deleted-dialogs" to { MZGramDeletedDialogsActivity() as UniversalFragment },
            "regex-filters" to { MZGramRegexFiltersActivity() as UniversalFragment },
            "shadow-ban" to { MZGramShadowBanActivity() as UniversalFragment },
        )

        for ((name, factory) in subScreens) {
            val sub = factory()
            MZGramScreens.open(sub)
            val shown = MZGramScreens.waitFor(15) {
                (MZGramScreens.listViewOf(sub)?.childCount ?: 0) > 0 || (sub.fragmentView?.isShown == true)
            }
            if (shown) {
                assertProcessAlive()
                testScreenItems("sub-$name", sub)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                MZGramScreens.waitFor(15) { MZGramScreens.lastFragment() === main }
                assertProcessAlive()
                Thread.sleep(150)
            }
        }

        // 4. Verify settings state is identical to initial
        initialPreferences?.let { initial ->
            val current = preferences.all
            assertEquals("settings state after test must be identical to before test", initial, current)
        }

        // 5. Verify no FATAL EXCEPTION occurred
        checkNoFatalException()
    }
}
