package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramLocalPins
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.DialogsActivity

// Pinned chats past the server's limit: pinned in MZGram only, after the
// server's pinned chats, kept on the device, never sent to the server.
class MZGramLocalPinsTest {

    private val account = 0
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MessagesController.getInstance(account)
    private val pins get() = MZGramLocalPins.getInstance(account)
    private val baseId = 7_200_000_000L
    private val made = ArrayList<TLRPC.Dialog>()
    private var savedLimit = 5

    // dialogs_dict through reflection: the test classpath has no androidx.collection
    private fun dict(): Any = MessagesController::class.java.getField("dialogs_dict").get(controller)!!
    private fun dictGet(id: Long): Any? = dict().javaClass.getMethod("get", Long::class.javaPrimitiveType).invoke(dict(), id)
    private fun dictPut(id: Long, dialog: TLRPC.Dialog) {
        dict().javaClass.getMethod("put", Long::class.javaPrimitiveType, Any::class.java).invoke(dict(), id, dialog)
    }
    private fun dictRemove(id: Long) {
        dict().javaClass.getMethod("remove", Long::class.javaPrimitiveType).invoke(dict(), id)
    }

    private fun sortDialogs() {
        MessagesController::class.java.methods.first { it.name == "sortDialogs" && it.parameterCount == 1 }.invoke(controller, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun allDialogs() = MessagesController::class.java.getDeclaredField("allDialogs").also { it.isAccessible = true }.get(controller) as ArrayList<TLRPC.Dialog>

    // A private chat with a user, its last message at the given time
    private fun chat(n: Int, date: Int, serverPinnedNum: Int = 0, folderId: Int = 0): TLRPC.Dialog {
        val user = TLRPC.TL_user()
        user.id = baseId + n
        user.first_name = "MZGram pin test $n"
        controller.putUser(user, false)
        val dialog = TLRPC.TL_dialog()
        dialog.id = user.id
        dialog.peer = TLRPC.TL_peerUser().also { it.user_id = user.id }
        dialog.last_message_date = date
        dialog.folder_id = folderId
        dialog.pinned = serverPinnedNum > 0
        dialog.pinnedNum = serverPinnedNum
        made.add(dialog)
        return dialog
    }

    private fun publish() {
        instrumentation.runOnMainSync {
            for (dialog in made) {
                if (dictGet(dialog.id) == null) {
                    dictPut(dialog.id, dialog)
                    allDialogs().add(dialog)
                }
            }
            sortDialogs()
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload)
        }
    }

    private fun order(folderId: Int = 0): List<Long> {
        var result = listOf<Long>()
        instrumentation.runOnMainSync {
            result = controller.getDialogs(folderId).map { it.id }.filter { it in baseId..(baseId + 1000) }
        }
        return result
    }

    @Before
    fun setUp() {
        pins.clear()
        savedLimit = controller.maxPinnedDialogsCountDefault
        controller.maxPinnedDialogsCountDefault = 5
    }

    @After
    fun tearDown() {
        pins.clear()
        controller.maxPinnedDialogsCountDefault = savedLimit
        instrumentation.runOnMainSync {
            for (dialog in made) {
                dictRemove(dialog.id)
                allDialogs().remove(dialog)
            }
            sortDialogs()
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload)
        }
    }

    @Test
    fun rule_localOnlyPastTheServerLimit_upToTen() {
        assertFalse("below the server limit the server pins", MZGramLocalPins.pinsLocally(4, 0, 5))
        assertTrue("at the server limit MZGram pins", MZGramLocalPins.pinsLocally(5, 0, 5))
        assertTrue(MZGramLocalPins.pinsLocally(5, 4, 5))
        assertFalse("ten in all", MZGramLocalPins.pinsLocally(5, 5, 5))
        assertFalse("ten in all with a higher server limit", MZGramLocalPins.pinsLocally(10, 0, 10))
        assertEquals(10, MZGramLocalPins.MAX_TOTAL)
    }

    // Kept on the device: a restart (a fresh read from the storage) keeps the pins and their order.
    @Test
    fun localPins_surviveARestart_inTheirOrder() {
        pins.pin(baseId + 3, 0)
        pins.pin(baseId + 1, 0)
        pins.pin(baseId + 2, 1)
        pins.reload()
        assertEquals(listOf(baseId + 3, baseId + 1), pins.get(0))
        assertEquals("the archive keeps its own", listOf(baseId + 2), pins.get(1))
        pins.unpin(baseId + 3, 0)
        pins.reload()
        assertEquals(listOf(baseId + 1), pins.get(0))
    }

    // The chat list: the server's pinned chats, then the local ones in pin order, then the rest by date.
    @Test
    fun chatList_serverPinsFirst_thenLocalPins_thenByDate() {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val server1 = chat(1, now - 500, serverPinnedNum = 2)
        val server2 = chat(2, now - 400, serverPinnedNum = 1)
        val newest = chat(3, now)
        val local1 = chat(4, now - 900)
        val local2 = chat(5, now - 800)
        val older = chat(6, now - 100)
        pins.pin(local2.id, 0)
        pins.pin(local1.id, 0)
        publish()
        val expected = listOf(server1.id, server2.id, local2.id, local1.id, newest.id, older.id)
        assertEquals(expected, order())

        // After a restart
        pins.reload()
        publish()
        assertEquals(expected, order())
        assertFalse("a local pin is not a server pin", local1.pinned || local2.pinned)
    }

    // A local pin belongs to its list: one in the archive does not move a chat in the main list.
    @Test
    fun localPins_belongToTheirList() {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val main = chat(1, now)
        val archived = chat(2, now - 1000, folderId = 1)
        val archivedNewer = chat(3, now, folderId = 1)
        pins.pin(archived.id, 1)
        pins.pin(main.id, 1)
        publish()
        assertEquals("pinned in the archive", listOf(archived.id, archivedNewer.id), order(1))
        assertEquals(listOf(main.id), order(0))
    }

    // Custom chat folders keep the server's own pinned chats (a much higher
    // limit); a chat pinned in MZGram only is sorted there by date.
    @Test
    fun customFolders_keepTheirOwnPins() {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val pinnedInFolder = chat(1, now - 900)
        val localInMain = chat(2, now - 800)
        val newest = chat(3, now)
        pins.pin(localInMain.id, 0)
        publish()
        val filter = MessagesController.DialogFilter()
        filter.pinnedDialogs.put(pinnedInFolder.id, 1)
        val comparator = MessagesController::class.java.getDeclaredField("dialogDateComparator").also { it.isAccessible = true }.get(controller) as Comparator<TLRPC.Dialog>
        val sorting = MessagesController::class.java.getDeclaredField("sortingDialogFilter").also { it.isAccessible = true }
        sorting.set(controller, filter)
        try {
            val sorted = listOf(newest, localInMain, pinnedInFolder).sortedWith(comparator).map { it.id }
            assertEquals("the folder's pin first, then by date", listOf(pinnedInFolder.id, newest.id, localInMain.id), sorted)
        } finally {
            sorting.set(controller, null)
        }
    }

    // The real pin in the chat list: with five server pins, pinning another
    // chat pins it in MZGram only (the server pin state stays), it shows a pin
    // in its own color, and unpinning takes it out again.
    @Test
    fun chatList_pinPastTheLimit_pinsInMZGramOnly() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        val now = (System.currentTimeMillis() / 1000).toInt()
        for (i in 1..5) {
            chat(i, now - i * 10, serverPinnedNum = 6 - i)
        }
        val extra = (6..9).map { chat(it, now - 1000 - it) }
        publish()
        MZGramScreens.launchApp()
        assertTrue("chat list", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() is DialogsActivity })
        val dialogs = MZGramScreens.lastFragment() as DialogsActivity
        val pinMethod = DialogsActivity::class.java.getDeclaredMethod("pinDialog", Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, MessagesController.DialogFilter::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).also { it.isAccessible = true }
        val serverPinsBefore = made.filter { it.pinned }.map { it.id }

        instrumentation.runOnMainSync {
            pinMethod.invoke(dialogs, extra[2].id, true, null, 0, false)
            pinMethod.invoke(dialogs, extra[0].id, true, null, 0, false)
        }
        Thread.sleep(1500)
        assertEquals("pinned in MZGram only, in pin order", listOf(extra[2].id, extra[0].id), pins.get(0))
        assertEquals("the server's pins are unchanged", serverPinsBefore, made.filter { it.pinned }.map { it.id })
        assertTrue("no server pin for the new ones", !extra[2].pinned && !extra[0].pinned)
        assertEquals("after the five server pins", listOf(extra[2].id, extra[0].id), order().drop(5).take(2))

        var localPin = false
        var serverPin = false
        MZGramScreens.waitFor(10) {
            localPin = cellPin(dialogs, extra[2].id, "drawLocalPin")
            serverPin = cellPin(dialogs, made[0].id, "drawPin") && !cellPin(dialogs, made[0].id, "drawLocalPin")
            localPin && serverPin
        }
        MZGramScreens.capture("chat-list-local-pins")
        assertTrue("the local pin is marked in the chat list", localPin)
        assertTrue("a server pin keeps the usual mark", serverPin)

        instrumentation.runOnMainSync { pinMethod.invoke(dialogs, extra[2].id, false, null, 0, false) }
        Thread.sleep(800)
        assertEquals("unpinned", listOf(extra[0].id), pins.get(0))
    }

    private fun cellPin(dialogs: DialogsActivity, dialogId: Long, field: String): Boolean {
        val cell = MZGramScreens.findView(dialogs.fragmentView, DialogCell::class.java) { it.dialogId == dialogId } ?: return false
        return DialogCell::class.java.getDeclaredField(field).also { it.isAccessible = true }.getBoolean(cell)
    }
}
