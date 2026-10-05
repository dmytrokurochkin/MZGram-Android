package org.telegram.messenger.mzgram.test

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.DialogsActivity
import org.telegram.ui.mzgram.MZGramSettingsActivity

// Settings > MZGram > Interface > "Folder tabs at the bottom": the chat list
// shows its folder tabs at the bottom of the screen, above the bottom bars,
// instead of under the search field. Checked on the real chat list, with a
// screenshot of each position.
class MZGramFolderTabsTest {

    private val account = 0
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MessagesController.getInstance(account)

    private var savedAtBottom = false
    private var savedHideBottomBar = false
    private var activity: Activity? = null

    @Before
    fun setUp() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        savedAtBottom = MZGramConfig.folderTabsAtBottom
        savedHideBottomBar = MZGramConfig.hideBottomNavigationBar
    }

    @After
    fun tearDown() {
        MZGramConfig.folderTabsAtBottom = savedAtBottom
        MZGramConfig.hideBottomNavigationBar = savedHideBottomBar
        instrumentation.runOnMainSync {
            activity?.finish()
            controller.dialogFilters.clear()
            controller.dialogFiltersById.clear()
        }
    }

    private fun folder(id: Int, name: String?) = MessagesController.DialogFilter().also {
        it.id = id
        it.name = name
        it.order = id
    }

    // Three folders, so the chat list shows its folder tabs.
    private fun putFolders() {
        instrumentation.runOnMainSync {
            controller.dialogFilters.clear()
            controller.dialogFiltersById.clear()
            listOf(folder(0, null), folder(2, "Work"), folder(3, "Family")).forEach {
                controller.dialogFilters.add(it)
                controller.dialogFiltersById.put(it.id, it)
            }
            controller.dialogFiltersLoaded = true
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated)
        }
    }

    private fun tabs(): FilterTabsView? =
        MZGramScreens.findView(activity?.window?.decorView, FilterTabsView::class.java) { it.alpha > 0.9f && it.height > 0 }

    // Where the tabs are on screen, once they have settled.
    private fun tabsPosition(name: String): IntArray {
        MZGramScreens.launchApp().also { activity = it }
        return measureTabs(name)
    }

    private fun measureTabs(name: String): IntArray {
        // The chat list may load its folders from the cache after it opens;
        // put the test folders back until the tabs show.
        var shown = false
        for (attempt in 1..30) {
            putFolders()
            if (MZGramScreens.waitFor(1) { tabs() != null }) {
                shown = true
                break
            }
        }
        assertTrue("chat list shows folder tabs", shown)
        Thread.sleep(1500)
        var rect = IntArray(4)
        var screenHeight = 0
        instrumentation.runOnMainSync {
            rect = MZGramScreens.screenRect(tabs()!!)
            screenHeight = activity!!.window.decorView.height
        }
        MZGramScreens.capture(name)
        MZGramScreens.log("folder tabs $name: top=${rect[1]} bottom=${rect[3]} screen=$screenHeight")
        return intArrayOf(rect[1], rect[3], screenHeight)
    }

    @Test
    fun folderTabs_atTheTopByDefault() {
        MZGramConfig.folderTabsAtBottom = false
        val (top, _, screen) = tabsPosition("folder-tabs-top").toList()
        assertTrue("under the search field: top=$top screen=$screen", top < screen / 3)
    }

    @Test
    fun folderTabs_atTheBottomWhenSwitchedOn() {
        MZGramConfig.folderTabsAtBottom = true
        val (top, bottom, screen) = tabsPosition("folder-tabs-bottom").toList()
        assertTrue("at the bottom: top=$top screen=$screen", top > screen * 2 / 3)
        assertTrue("on screen: bottom=$bottom screen=$screen", bottom <= screen)
    }

    // Space between the bottom of the tabs and the system navigation bar.
    private fun gapAboveSystemBar(rect: IntArray): Int {
        var inset = 0
        instrumentation.runOnMainSync {
            inset = activity!!.window.decorView.rootWindowInsets?.systemWindowInsetBottom ?: 0
        }
        val (_, bottom, screen) = rect.toList()
        return screen - inset - bottom
    }

    // The app's own bottom bar is shown: the tabs stay above it.
    @Test
    fun folderTabs_withBottomBar_stayAboveIt() {
        MZGramConfig.folderTabsAtBottom = true
        MZGramConfig.hideBottomNavigationBar = false
        val gap = gapAboveSystemBar(tabsPosition("folder-tabs-bottom-bar"))
        assertTrue("above the bottom bar: gap=$gap", gap >= AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT.toFloat()))
    }

    // The app's bottom bar is switched off: the tabs go to the very bottom.
    @Test
    fun folderTabs_withoutBottomBar_sitAtTheVeryBottom() {
        MZGramConfig.folderTabsAtBottom = true
        MZGramConfig.hideBottomNavigationBar = true
        val gap = gapAboveSystemBar(tabsPosition("folder-tabs-no-bottom-bar"))
        assertTrue("at the very bottom: gap=$gap", gap < AndroidUtilities.dp(24f))
    }

    // The bottom bar is switched off in settings while the chat list is
    // open: back on the chat list, the tabs go to the very bottom.
    @Test
    fun folderTabs_bottomBarSwitchedOffInSettings_goToTheVeryBottom() {
        MZGramConfig.folderTabsAtBottom = true
        MZGramConfig.hideBottomNavigationBar = false
        tabsPosition("folder-tabs-before-switch")
        val settings = MZGramSettingsActivity()
        MZGramScreens.open(settings)
        assertTrue("settings shown", MZGramScreens.waitFor(30) { settings.fragmentView?.isShown == true })
        MZGramConfig.hideBottomNavigationBar = true
        instrumentation.runOnMainSync { settings.finishFragment() }
        assertTrue("back on the chat list", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() !is MZGramSettingsActivity })
        val gap = gapAboveSystemBar(measureTabs("folder-tabs-after-switch"))
        assertTrue("at the very bottom after the switch: gap=$gap", gap < AndroidUtilities.dp(24f))
    }
}
