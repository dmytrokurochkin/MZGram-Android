package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.messenger.mzgram.MZGramFolderEmoticons
import org.telegram.tgnet.TLRPC

class MZGramFolderEmoticonTest {

    @Test
    fun dialogFilter_emoticonFieldPreserved() {
        val filter = MessagesController.DialogFilter()
        filter.id = 100
        filter.name = "Work"
        filter.emoticon = "💼"

        assertEquals("💼", filter.emoticon)

        val reqFilter = TLRPC.TL_dialogFilter()
        reqFilter.id = filter.id
        reqFilter.title = TLRPC.TL_textWithEntities()
        reqFilter.title.text = filter.name

        // Simulate emoticon preservation logic from FilterCreateActivity
        val preservedEmoticon = filter.emoticon
        if (!preservedEmoticon.isNullOrEmpty()) {
            reqFilter.flags = reqFilter.flags or (1 shl 25)
            reqFilter.emoticon = preservedEmoticon
        }

        assertEquals("💼", reqFilter.emoticon)
        assertEquals(1 shl 25, reqFilter.flags and (1 shl 25))
    }

    @Test
    fun folderEmoticons_storageAndRetrieval() {
        val account = 0
        val filterId = 205
        val testEmoji = "⭐️"

        MZGramFolderEmoticons.remember(account, filterId, testEmoji)
        val loaded = MZGramFolderEmoticons.get(account, filterId)
        assertEquals(testEmoji, loaded)

        // Cleaning up
        MZGramFolderEmoticons.remember(account, filterId, "")
        assertNull(MZGramFolderEmoticons.get(account, filterId))
    }
}
