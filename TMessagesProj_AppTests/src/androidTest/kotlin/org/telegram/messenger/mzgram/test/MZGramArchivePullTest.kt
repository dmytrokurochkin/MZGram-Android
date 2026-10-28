package org.telegram.messenger.mzgram.test

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DialogObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.DialogsActivity
import org.telegram.ui.LaunchActivity

// The archive hidden above the chat list (long press on Archive > Hide above
// the list): pulling the list down shows the Archive row, scrolling the list
// up hides it again. With Settings > MZGram > "Open archive on pull" the pull
// opens the archive instead. Checked on the real chat list with real swipes,
// with and without folder tabs, the tabs at the bottom and the bottom bar.
class MZGramArchivePullTest {

    private val account = 0
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MessagesController.getInstance(account)
    private val baseId = 7_300_000_000L
    private val made = ArrayList<TLRPC.Dialog>()
    private var folderMade = false
    private var activity: Activity? = null

    private var savedArchiveHidden = false
    private var savedOpenOnPull = false
    private var savedAtBottom = false
    private var savedHideBottomBar = false

    // DialogsActivity.ARCHIVE_ITEM_STATE_*
    private val stateShowed = 1
    private val stateHidden = 2

    @Before
    fun setUp() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        savedArchiveHidden = SharedConfig.archiveHidden
        savedOpenOnPull = MZGramConfig.openArchiveOnPull
        savedAtBottom = MZGramConfig.folderTabsAtBottom
        savedHideBottomBar = MZGramConfig.hideBottomNavigationBar
        SharedConfig.archiveHidden = true
        MZGramConfig.openArchiveOnPull = false
        MZGramConfig.folderTabsAtBottom = false
        MZGramConfig.hideBottomNavigationBar = false
    }

    @After
    fun tearDown() {
        SharedConfig.archiveHidden = savedArchiveHidden
        MZGramConfig.openArchiveOnPull = savedOpenOnPull
        MZGramConfig.folderTabsAtBottom = savedAtBottom
        MZGramConfig.hideBottomNavigationBar = savedHideBottomBar
        instrumentation.runOnMainSync {
            activity?.finish()
            controller.dialogFilters.clear()
            controller.dialogFiltersById.clear()
            for (dialog in made) {
                dictRemove(dialog.id)
                allDialogs().remove(dialog)
            }
            if (folderMade) {
                val folderDialogId = DialogObject.makeFolderDialogId(1)
                (dictGet(folderDialogId) as? TLRPC.Dialog)?.let { allDialogs().remove(it) }
                dictRemove(folderDialogId)
            }
            sortDialogs()
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload)
        }
    }

    // dialogs_dict through reflection: the test classpath has no androidx.collection
    private fun dict(): Any = MessagesController::class.java.getField("dialogs_dict").get(controller)!!
    private fun dictGet(id: Long): Any? = dict().javaClass.getMethod("get", Long::class.javaPrimitiveType).invoke(dict(), id)
    private fun dictPut(id: Long, dialog: TLRPC.Dialog) {
        dict().javaClass.getMethod("put", Long::class.javaPrimitiveType, Any::class.java).invoke(dict(), id, dialog)
    }
    private fun dictRemove(id: Long) {
        dict().javaClass.getMethod("remove", Long::class.javaPrimitiveType).invoke(dict(), id)
    }

    @Suppress("UNCHECKED_CAST")
    private fun allDialogs() = MessagesController::class.java.getDeclaredField("allDialogs").also { it.isAccessible = true }.get(controller) as ArrayList<TLRPC.Dialog>

    // sortDialogs(null) does nothing while the app is in the background
    private fun sortDialogs() {
        val paused = ApplicationLoader.mainInterfacePaused
        ApplicationLoader.mainInterfacePaused = false
        try {
            MessagesController::class.java.methods.first { it.name == "sortDialogs" && it.parameterCount == 1 }.invoke(controller, null)
        } finally {
            ApplicationLoader.mainInterfacePaused = paused
        }
    }

    private fun chat(n: Int, folderId: Int): TLRPC.Dialog {
        val user = TLRPC.TL_user()
        user.id = baseId + n
        user.first_name = "MZGram archive pull $n"
        controller.putUser(user, false)
        val dialog = TLRPC.TL_dialog()
        dialog.id = user.id
        dialog.peer = TLRPC.TL_peerUser().also { it.user_id = user.id }
        dialog.last_message_date = (System.currentTimeMillis() / 1000).toInt() - n * 60
        dialog.folder_id = folderId
        made.add(dialog)
        return dialog
    }

    // Enough chats to scroll the list, a few of them archived, so the chat
    // list has the Archive row. Put before the chat list opens, as when the
    // app starts with its chats in the cache.
    private fun putChats() {
        instrumentation.runOnMainSync {
            for (n in 1..30) {
                chat(n, if (n <= 3) 1 else 0)
            }
            for (dialog in made) {
                dictPut(dialog.id, dialog)
                allDialogs().add(dialog)
            }
            // The Archive row, as MessagesController.ensureFolderDialogExists
            // makes it, without writing it to the cache of the account.
            val folderDialogId = DialogObject.makeFolderDialogId(1)
            if (dictGet(folderDialogId) == null) {
                val archive = TLRPC.TL_dialogFolder()
                archive.id = folderDialogId
                archive.peer = TLRPC.TL_peerUser()
                archive.folder = TLRPC.TL_folder().also { it.id = 1; it.title = "Archived Chats" }
                archive.pinned = true
                archive.pinnedNum = 1
                dictPut(folderDialogId, archive)
                allDialogs().add(0, archive)
                folderMade = true
            }
            sortDialogs()
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload)
        }
    }

    private fun folder(id: Int, name: String?) = MessagesController.DialogFilter().also {
        it.id = id
        it.name = name
        it.order = id
    }

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

    // The chat list sits inside the main tabs
    private fun chatList(): DialogsActivity? =
        LaunchActivity::class.java.getMethod("getLastFragmentIncludeMainTabs").invoke(null) as? DialogsActivity

    private fun field(owner: Any, name: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try {
                return type.getDeclaredField(name).also { it.isAccessible = true }.get(owner)
            } catch (e: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw AssertionError("no field $name")
    }

    private fun pullState(): Int {
        var state = -1
        instrumentation.runOnMainSync { state = pullStateUnsafe() }
        return state
    }

    private fun archiveCell(): DialogCell? =
        MZGramScreens.findView(activity?.window?.decorView, DialogCell::class.java) { it.isFolderCell }

    private fun describe(): String {
        var text = ""
        instrumentation.runOnMainSync {
            val cell = archiveCell()
            text = "state=${pullStateUnsafe()} archive=" + (cell?.let { MZGramScreens.screenRect(it).joinToString(",") } ?: "none") +
                " hidden=${SharedConfig.archiveHidden} hasHiddenArchive=${controller.hasHiddenArchive()}"
        }
        return text
    }

    // DialogsActivity.viewPages[0].archivePullViewState, on the main thread
    private fun pullStateUnsafe(): Int {
        val list = chatList() ?: return -1
        val pages = field(list, "viewPages") as Array<*>? ?: return -1
        return field(pages[0]!!, "archivePullViewState") as Int
    }

    private fun screenHeight(): Int {
        var height = 0
        instrumentation.runOnMainSync { height = activity!!.window.decorView.height }
        return height
    }

    private fun screenWidth(): Int {
        var width = 0
        instrumentation.runOnMainSync { width = activity!!.window.decorView.width }
        return width
    }

    // A finger on the chat list moving from one height to another (parts of
    // the screen height), as the user does.
    private fun swipe(from: Float, to: Float, ms: Int) {
        val x = screenWidth() / 2
        val h = screenHeight()
        MZGramScreens.shell("input swipe $x ${(h * from).toInt()} $x ${(h * to).toInt()} $ms")
        Thread.sleep(1500)
    }

    private fun openChatList(name: String) {
        putChats()
        MZGramScreens.launchApp().also { activity = it }
        assertTrue("chat list shown", MZGramScreens.waitFor(45) { chatList()?.fragmentView?.isShown == true })
        assertTrue("the archive is hidden above the list: ${describe()}",
            MZGramScreens.waitFor(30) { controller.hasHiddenArchive() && pullStateUnsafe() == stateHidden })
        Thread.sleep(1500)
        MZGramScreens.capture("$name-open")
        MZGramScreens.log("archive pull $name open: ${describe()}")
    }

    private fun pullAndHide(name: String) {
        openChatList(name)
        swipe(0.35f, 0.85f, 1500)
        MZGramScreens.capture("$name-pulled")
        MZGramScreens.log("archive pull $name pulled: ${describe()}")
        assertEquals("pulling the list down shows the archive: ${describe()}", stateShowed, pullState())
        swipe(0.75f, 0.35f, 600)
        MZGramScreens.capture("$name-scrolled")
        MZGramScreens.log("archive pull $name scrolled: ${describe()}")
        assertEquals("scrolling the list up hides the archive: ${describe()}", stateHidden, pullState())
    }

    @Test
    fun archive_pullShowsItAndScrollingHidesIt() {
        pullAndHide("archive-pull")
    }

    @Test
    fun archive_withFolderTabs_pullShowsItAndScrollingHidesIt() {
        putFolders()
        pullAndHide("archive-pull-folders")
    }

    @Test
    fun archive_withFolderTabsAtTheBottom_pullShowsItAndScrollingHidesIt() {
        MZGramConfig.folderTabsAtBottom = true
        putFolders()
        pullAndHide("archive-pull-folders-bottom")
    }

    @Test
    fun archive_withoutBottomBar_pullShowsItAndScrollingHidesIt() {
        MZGramConfig.hideBottomNavigationBar = true
        pullAndHide("archive-pull-no-bottom-bar")
    }

    @Test
    fun archive_openOnPull_opensTheArchive() {
        MZGramConfig.openArchiveOnPull = true
        openChatList("archive-open-on-pull")
        swipe(0.35f, 0.85f, 1500)
        MZGramScreens.capture("archive-open-on-pull-pulled")
        val opened = MZGramScreens.waitFor(10) {
            val last = MZGramScreens.lastFragment()
            last is DialogsActivity && field(last, "folderId") == 1
        }
        MZGramScreens.log("archive pull open-on-pull: ${MZGramScreens.lastFragment()?.javaClass?.simpleName} ${describe()}")
        assertTrue("pulling the list down opens the archive", opened)
    }
}
