package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.Components.ShareAlert
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

    // Forward a message (or share a file into the app) and pick a chat: the
    // chat list for picking shows the comment field and the send button at
    // the bottom. With the folder tabs at the bottom, both stay above the
    // tabs instead of under them.

    private val otherUserId = 7_000_000_002L

    // Forward in a chat opens the picker with these arguments.
    private fun openPicker(): DialogsActivity {
        MZGramScreens.launchApp().also { activity = it }
        assertTrue("chat list shown", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment()?.fragmentView?.isShown == true })
        MZGramScreens.log("picker: chat list shown (${MZGramScreens.lastFragment()?.javaClass?.simpleName})")
        val picker = DialogsActivity(Bundle().apply {
            putBoolean("onlySelect", true)
            putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD)
            putBoolean("canSelectTopics", true)
        })
        MZGramScreens.open(picker)
        return preparePicker(picker)
    }

    // Share in another app, MZGram chosen: LaunchActivity.openDialogsToSend
    // opens the picker with these arguments. Opened here directly: a share
    // intent from the test process takes the CI emulator down.
    private fun openSharePicker(): DialogsActivity {
        MZGramScreens.launchApp().also { activity = it }
        assertTrue("chat list shown", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment()?.fragmentView?.isShown == true })
        val picker = DialogsActivity(Bundle().apply {
            putBoolean("onlySelect", true)
            putBoolean("canSelectTopics", true)
            putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD)
            putBoolean("allowSwitchAccount", true)
            putString("selectAlertString", LocaleController.getString(R.string.SendMessagesToText))
            putString("selectAlertStringGroup", LocaleController.getString(R.string.SendMessagesToGroupText))
        })
        MZGramScreens.open(picker)
        return preparePicker(picker)
    }

    private fun preparePicker(picker: DialogsActivity): DialogsActivity {
        assertTrue("picker shown", MZGramScreens.waitFor(30) { picker.fragmentView?.isShown == true })
        MZGramScreens.log("picker: shown")
        var shown = false
        for (attempt in 1..30) {
            putFolders()
            if (MZGramScreens.waitFor(1) { pickerTabs(picker) != null }) {
                shown = true
                break
            }
        }
        assertTrue("picker shows folder tabs", shown)
        MZGramScreens.log("picker: folder tabs shown")
        instrumentation.runOnMainSync {
            picker.addOrRemoveSelectedDialog(otherUserId, null)
            DialogsActivity::class.java.getDeclaredMethod("updateSelectedCount").apply { isAccessible = true }.invoke(picker)
        }
        assertTrue("comment field shown", MZGramScreens.waitFor(10) { commentField(picker) != null })
        MZGramScreens.log("picker: chat selected, comment field shown")
        Thread.sleep(1500)
        return picker
    }

    private fun pickerTabs(picker: DialogsActivity): FilterTabsView? =
        MZGramScreens.findView(picker.fragmentView, FilterTabsView::class.java) { it.alpha > 0.9f && it.height > 0 }

    private fun commentField(picker: DialogsActivity): ChatActivityEnterView? =
        MZGramScreens.findView(picker.fragmentView, ChatActivityEnterView::class.java) { it.height > 0 }

    private fun keyboardShown(): Boolean =
        activity!!.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true

    private fun showKeyboard(field: View) {
        MZGramScreens.log("keyboard: showing")
        instrumentation.runOnMainSync {
            val edit = MZGramScreens.findView(field, EditText::class.java)!!
            edit.requestFocus()
            AndroidUtilities.showKeyboard(edit)
        }
        MZGramScreens.waitFor(10) { keyboardShown() }
        Thread.sleep(1500)
    }

    // The field and the send button end above the top of the tabs.
    private fun assertAboveTabs(picker: DialogsActivity, name: String) {
        var tabs = IntArray(4)
        var field = IntArray(4)
        var send = IntArray(4)
        var keyboard = false
        instrumentation.runOnMainSync {
            tabs = MZGramScreens.screenRect(pickerTabs(picker)!!)
            field = MZGramScreens.screenRect(commentField(picker)!!)
            send = MZGramScreens.screenRect(DialogsActivity::class.java.getDeclaredField("writeButton").apply { isAccessible = true }.get(picker) as View)
            keyboard = keyboardShown()
        }
        MZGramScreens.capture(name)
        MZGramScreens.log("$name: tabs top=${tabs[1]} bottom=${tabs[3]}, field bottom=${field[3]}, send bottom=${send[3]}, keyboard=$keyboard")
        assertTrue("$name: comment field above the folder tabs: field bottom=${field[3]} tabs top=${tabs[1]}", field[3] <= tabs[1])
        assertTrue("$name: send button above the folder tabs: send bottom=${send[3]} tabs top=${tabs[1]}", send[3] <= tabs[1])
    }

    @Test
    fun folderTabsAtTheBottom_forwardCommentFieldStaysAboveThem() {
        MZGramConfig.folderTabsAtBottom = true
        val picker = openPicker()
        assertAboveTabs(picker, "folder-tabs-forward")
    }

    @Test
    fun folderTabsAtTheBottom_forwardCommentFieldWithKeyboardStaysAboveThem() {
        MZGramConfig.folderTabsAtBottom = true
        val picker = openPicker()
        showKeyboard(commentField(picker)!!)
        assertAboveTabs(picker, "folder-tabs-forward-keyboard")
    }

    @Test
    fun folderTabsAtTheBottom_chatFieldIsNotCovered() {
        MZGramConfig.folderTabsAtBottom = true
        tabsPosition("folder-tabs-before-chat")
        val other = TLRPC.TL_user().also {
            it.id = otherUserId
            it.first_name = "MZGram test peer"
        }
        instrumentation.runOnMainSync { controller.putUser(other, false) }
        val chat = ChatActivity(Bundle().apply { putLong("user_id", otherUserId) })
        MZGramScreens.open(chat)
        assertTrue("chat shown", MZGramScreens.waitFor(30) { chat.chatActivityEnterView?.isShown == true && chat.chatActivityEnterView.height > 0 })
        Thread.sleep(1500)

        fun assertFieldFree(name: String) {
            var field = IntArray(4)
            var covering = ""
            instrumentation.runOnMainSync {
                field = MZGramScreens.screenRect(chat.chatActivityEnterView)
                val tabs = MZGramScreens.findView(activity?.window?.decorView, FilterTabsView::class.java) { it.alpha > 0.01f && it.height > 0 }
                if (tabs != null) {
                    val rect = MZGramScreens.screenRect(tabs)
                    if (rect[1] < field[3] && rect[3] > field[1]) covering = "tabs top=${rect[1]} bottom=${rect[3]}"
                }
            }
            MZGramScreens.capture(name)
            MZGramScreens.log("$name: field top=${field[1]} bottom=${field[3]} $covering")
            assertTrue("$name: folder tabs over the message field: $covering", covering.isEmpty())
        }

        assertFieldFree("folder-tabs-chat")
        val message = TLRPC.TL_message().also {
            it.id = 1
            it.message = "MZGram test message"
            it.date = (System.currentTimeMillis() / 1000).toInt()
            it.peer_id = TLRPC.TL_peerUser().also { peer -> peer.user_id = otherUserId }
            it.from_id = TLRPC.TL_peerUser().also { peer -> peer.user_id = otherUserId }
            it.dialog_id = otherUserId
        }
        instrumentation.runOnMainSync { chat.showFieldPanelForReply(MessageObject(account, message, true, false)) }
        Thread.sleep(1500)
        assertFieldFree("folder-tabs-chat-reply")
        showKeyboard(chat.chatActivityEnterView)
        assertFieldFree("folder-tabs-chat-keyboard")

        instrumentation.runOnMainSync {
            AndroidUtilities.hideKeyboard(chat.chatActivityEnterView)
            chat.finishFragment()
        }
        assertTrue("back on the chat list", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() !is ChatActivity && MZGramScreens.lastFragment()?.fragmentView?.isShown == true })
        val (top, _, screen) = measureTabs("folder-tabs-after-chat").toList()
        assertTrue("at the bottom after the chat: top=$top screen=$screen", top > screen / 2)
    }

    @Test
    fun folderTabsAtTheBottom_sharePickerFromAnotherApp_sendPanelStaysAboveThem() {
        MZGramConfig.folderTabsAtBottom = true
        val picker = openSharePicker()
        assertAboveTabs(picker, "folder-tabs-share")
        showKeyboard(commentField(picker)!!)
        assertAboveTabs(picker, "folder-tabs-share-keyboard")
    }

    // The share sheet of a chat (Share on a message or a link) has no folder
    // tabs of its own, so the setting cannot cover its send panel.
    @Test
    fun folderTabsAtTheBottom_shareSheetHasNoFolderTabs() {
        MZGramConfig.folderTabsAtBottom = true
        tabsPosition("folder-tabs-before-share-sheet")
        var sheet: ShareAlert? = null
        instrumentation.runOnMainSync {
            sheet = ShareAlert(activity!!, null, "https://telegram.org", false, null, false)
            MZGramScreens.lastFragment()!!.showDialog(sheet)
        }
        try {
            assertTrue("share sheet shown", MZGramScreens.waitFor(10) { sheet!!.isShowing && sheet!!.window?.decorView?.isShown == true })
            Thread.sleep(1500)
            MZGramScreens.capture("folder-tabs-share-sheet")
            var tabs: FilterTabsView? = null
            instrumentation.runOnMainSync { tabs = MZGramScreens.findView(sheet!!.window?.decorView, FilterTabsView::class.java) }
            assertTrue("no folder tabs in the share sheet", tabs == null)
        } finally {
            instrumentation.runOnMainSync { sheet?.dismiss() }
        }
    }
}
