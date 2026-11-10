package org.telegram.messenger.mzgram.test

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.messenger.mzgram.MZGramFolderSync
import org.telegram.messenger.mzgram.MZGramLocalFolders
import org.telegram.tgnet.TLRPC

class MZGramLocalFoldersTest {

    private fun folder(id: Int, included: Int = 0) = MessagesController.DialogFilter().apply {
        this.id = id
        for (i in 1..included) alwaysShow.add(i.toLong())
    }

    @Test
    fun isLocal_recognizesNegativeIds() {
        assertTrue(MZGramLocalFolders.isLocal(folder(-2)))
        assertTrue(MZGramLocalFolders.isLocal(folder(-100)))
        assertFalse(MZGramLocalFolders.isLocal(folder(0))) // All chats
        assertFalse(MZGramLocalFolders.isLocal(folder(2)))
        assertFalse(MZGramLocalFolders.isLocal(null))
    }

    @Test
    fun dropIfLocal_filtersOrderDropsNegative() {
        val req = TLRPC.TL_messages_updateDialogFiltersOrder().apply {
            order.add(2)
            order.add(-5)
            order.add(3)
            order.add(-10)
        }
        val dropped = MZGramLocalFolders.dropIfLocal(req, null)
        assertFalse(dropped)
        assertEquals(listOf(2, 3), req.order)
    }

    @Test
    fun dropIfLocal_singleFilterDrop() {
        val reqLocal = TLRPC.TL_messages_updateDialogFilter().apply {
            id = -3
        }
        var callbackRan = false
        val dropped = MZGramLocalFolders.dropIfLocal(reqLocal) { _, _ -> callbackRan = true }
        assertTrue(dropped)
        assertTrue(callbackRan)

        val reqServer = TLRPC.TL_messages_updateDialogFilter().apply {
            id = 2
        }
        assertFalse(MZGramLocalFolders.dropIfLocal(reqServer, null))
    }

    @Test
    fun mergeRemoteOrder_preservesLocalSlots() {
        val f0 = folder(0) // All chats
        val fLocal1 = folder(-3)
        val fServer1 = folder(2)
        val fLocal2 = folder(-5)
        val fServer2 = folder(3)
        val list = arrayListOf(f0, fLocal1, fServer1, fLocal2, fServer2)

        // Server only knows about server folders 3 then 2
        val serverOrder = arrayListOf(3, 2)
        val changed = MZGramLocalFolders.mergeRemoteOrder(list, serverOrder)
        assertTrue(changed)
        // Order of server folders is swapped, local folders keep their slots
        assertEquals(f0.id, list[0].id)
        assertEquals(fLocal1.id, list[1].id)
        assertEquals(fServer2.id, list[2].id) // 3 moved here
        assertEquals(fLocal2.id, list[3].id)
        assertEquals(fServer1.id, list[4].id) // 2 moved here
    }

    @Test
    fun roundTripIsCanonical() {
        val mc = MessagesController.getInstance(0)
        val f = folder(-3).apply {
            name = "Work extra"
            color = 3
            flags = MessagesController.DIALOG_FILTER_FLAG_GROUPS
            alwaysShow.add(-1234567890L)
            neverShow.add(-987654321L)
            pinnedDialogs.put(-1234567890L, 0)
            title_noanimate = true
            entities.add(TLRPC.TL_messageEntityCustomEmoji().apply {
                offset = 0
                length = 4
                document_id = 5307685888880885312L
            })
        }
        val icons = JSONObject("""{"-3": "💼"}""")
        val json = MZGramFolderSync.toJson(mc, listOf(f), 1757181000, icons)
        assertEquals("mzgram-folders", json.getString("format"))
        assertEquals(1, json.getInt("version"))
        assertEquals(1757181000, json.getInt("updated"))
        assertEquals(1, json.getJSONArray("folders").length())
    }
}
