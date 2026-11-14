package org.telegram.messenger.mzgram.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramForwardDate
import org.telegram.messenger.mzgram.MZGramUnreadSort
import org.telegram.tgnet.TLRPC

// Functions from exteraless that MZGram lacked (audit-exteraless.md): the
// switches, their defaults, and the helpers they use.
class MZGramExteralessTest {

    private val account get() = UserConfig.selectedAccount

    private fun hasSaved(key: String): Boolean {
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
        return prefs.contains(key)
    }

    @Test
    fun newSwitches_keepTelegramAsItWasByDefault() {
        val config = MZGramConfig::class.java
        val off = listOf("disableGlobalSearch", "hideChannelGiftButton", "hideChannelSearchButton", "hideCallButton", "hideAttachMoney",
            "forwardedDate", "smallGifs", "rememberAllReplies", "disableTrending", "hideGroupSticker", "disableAvatarBlur", "sortByUnread",
            "disableNotificationBubbles", "dontAutoPlayNextVoice", "disableProximity", "confirmRepeat", "confirmBotCommands")
        for (key in off) {
            assertTrue(key, !config.getField(key).getBoolean(null) || hasSaved(key))
        }
        // Android shares the screen content by default; the switch starts on
        assertTrue(config.getField("contentCapture").getBoolean(null) || hasSaved("contentCapture"))
    }

    @Test
    fun forwardedDate_addsWhenTheOriginalWasSent() {
        val message = TLRPC.TL_message().also {
            it.id = 1
            it.message = "forwarded"
            it.date = 1_700_000_000
            it.peer_id = TLRPC.TL_peerUser().also { p -> p.user_id = 7_000_000_002L }
            it.fwd_from = TLRPC.TL_messageFwdHeader().also { f -> f.date = 1_600_000_000 }
        }
        var obj: MessageObject? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { obj = MessageObject(account, message, false, false) }
        val was = MZGramConfig.forwardedDate
        try {
            if (MZGramConfig.forwardedDate) MZGramConfig.toggleForwardedDate()
            assertEquals("Forwarded from **A**", MZGramForwardDate.withDate(obj, "Forwarded from **A**"))
            MZGramConfig.toggleForwardedDate()
            val line = MZGramForwardDate.withDate(obj, "Forwarded from **A**")
            assertTrue(line, line.startsWith("Forwarded from **A** · ") && line.length > "Forwarded from **A** · ".length)
            message.fwd_from.date = 0
            assertEquals("no date, no change", "Forwarded from **A**", MZGramForwardDate.withDate(obj, "Forwarded from **A**"))
        } finally {
            if (MZGramConfig.forwardedDate != was) MZGramConfig.toggleForwardedDate()
        }
    }

    @Test
    fun unreadSort_putsChatsWithSomethingUnreadFirst() {
        fun dialog(unread: Int, mentions: Int = 0, reactions: Int = 0) = TLRPC.TL_dialog().also {
            it.id = 7_700_000_000L + (Math.random() * 1_000_000).toLong()
            it.unread_count = unread
            it.unread_mentions_count = mentions
            it.unread_reactions_count = reactions
        }
        assertTrue(MZGramUnreadSort.isPriority(account, dialog(3)))
        assertFalse(MZGramUnreadSort.isPriority(account, dialog(0)))
        assertTrue("a mention counts", MZGramUnreadSort.isPriority(account, dialog(0, mentions = 1)))
        assertTrue("a reaction counts", MZGramUnreadSort.isPriority(account, dialog(0, reactions = 1)))
        assertFalse("the archive folder row stays where it is", MZGramUnreadSort.isPriority(account, TLRPC.TL_dialogFolder()))
    }

    @Test
    fun switches_toggleBack() {
        val toggles = listOf<Pair<() -> Boolean, () -> Unit>>(
            { MZGramConfig.sortByUnread } to { MZGramConfig.toggleSortByUnread() },
            { MZGramConfig.disableTrending } to { MZGramConfig.toggleDisableTrending() },
            { MZGramConfig.rememberAllReplies } to { MZGramConfig.toggleRememberAllReplies() },
            { MZGramConfig.contentCapture } to { MZGramConfig.toggleContentCapture() },
        )
        for ((get, toggle) in toggles) {
            val was = get()
            toggle()
            assertEquals(!was, get())
            toggle()
            assertEquals(was, get())
        }
    }

    @Test
    fun chatMenu_adminItemsOnUnderTheirSwitch_toTheBeginningOff() {
        val config = MZGramConfig::class.java
        for (key in listOf("adminShortcutRecentActions", "adminShortcutAdministrators", "adminShortcutPermissions",
                "adminShortcutMembers", "adminShortcutStatistics")) {
            assertTrue(key, config.getField(key).getBoolean(null) || hasSaved(key))
        }
        assertTrue(!MZGramConfig.chatMenuToBeginning || hasSaved("chatMenuToBeginning"))
        // The menu ids do not clash with Telegram's or MZGram's other items
        val ids = listOf(org.telegram.ui.mzgram.MZGramAdminShortcuts.RECENT_ACTIONS, org.telegram.ui.mzgram.MZGramAdminShortcuts.ADMINISTRATORS,
            org.telegram.ui.mzgram.MZGramAdminShortcuts.PERMISSIONS, org.telegram.ui.mzgram.MZGramAdminShortcuts.MEMBERS,
            org.telegram.ui.mzgram.MZGramAdminShortcuts.STATISTICS, org.telegram.ui.mzgram.MZGramAdminShortcuts.TO_BEGINNING)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun chatMenu_moreItemsOff_boostOn() {
        val config = MZGramConfig::class.java
        for (key in listOf("chatMenuLinkedChat", "chatMenuGoToMessage", "chatMenuHideTitle", "chatMenuClearDeleted", "chatMenuDeleteOwn")) {
            assertTrue(key, !config.getField(key).getBoolean(null) || hasSaved(key))
        }
        // Telegram's own Boost item stays as in Telegram
        assertTrue(MZGramConfig.chatMenuBoost || hasSaved("chatMenuBoost"))
        // The menu ids do not clash with each other
        val ids = listOf(org.telegram.ui.mzgram.MZGramAdminShortcuts.RECENT_ACTIONS, org.telegram.ui.mzgram.MZGramAdminShortcuts.ADMINISTRATORS,
            org.telegram.ui.mzgram.MZGramAdminShortcuts.PERMISSIONS, org.telegram.ui.mzgram.MZGramAdminShortcuts.MEMBERS,
            org.telegram.ui.mzgram.MZGramAdminShortcuts.STATISTICS, org.telegram.ui.mzgram.MZGramAdminShortcuts.TO_BEGINNING,
            org.telegram.ui.mzgram.MZGramChatMenu.LINKED_CHAT, org.telegram.ui.mzgram.MZGramChatMenu.GO_TO_MESSAGE,
            org.telegram.ui.mzgram.MZGramChatMenu.HIDE_TITLE, org.telegram.ui.mzgram.MZGramChatMenu.CLEAR_DELETED,
            org.telegram.ui.mzgram.MZGramChatMenu.DELETE_OWN)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun goToMessage_takesOnlyAPositiveNumber() {
        assertEquals(42, org.telegram.ui.mzgram.MZGramChatMenu.messageNumber(" 42 "))
        assertEquals(0, org.telegram.ui.mzgram.MZGramChatMenu.messageNumber("abc"))
        assertEquals(0, org.telegram.ui.mzgram.MZGramChatMenu.messageNumber(""))
        assertEquals(0, org.telegram.ui.mzgram.MZGramChatMenu.messageNumber(null))
        assertEquals(0, org.telegram.ui.mzgram.MZGramChatMenu.messageNumber("-5"))
        assertEquals(0, org.telegram.ui.mzgram.MZGramChatMenu.messageNumber("99999999999"))
    }

    @Test
    fun deleteMyMessages_takesOwnOlderOnes_andPagesDown() {
        fun message(id: Int, out: Boolean, date: Int, post: Boolean = false): TLRPC.Message =
            TLRPC.TL_message().also { it.id = id; it.out = out; it.date = date; it.post = post }
        val service = TLRPC.TL_messageService().also { it.id = 7; it.out = true; it.date = 10 }
        val page = arrayListOf(message(9, true, 100), message(8, false, 50), message(6, true, 300), message(5, true, 20, post = true), service)
        // Own, not a channel post or a service message, sent before the time
        assertEquals(listOf(9), org.telegram.ui.mzgram.MZGramDeleteOwnMessages.ownBefore(page, 200))
        assertEquals(listOf(9, 6), org.telegram.ui.mzgram.MZGramDeleteOwnMessages.ownBefore(page, Int.MAX_VALUE))
        // The next page starts below the lowest id; an empty page stays where it was
        assertEquals(5, org.telegram.ui.mzgram.MZGramDeleteOwnMessages.nextOffset(page, 0))
        assertEquals(5, org.telegram.ui.mzgram.MZGramDeleteOwnMessages.nextOffset(page, 12))
        assertEquals(12, org.telegram.ui.mzgram.MZGramDeleteOwnMessages.nextOffset(arrayListOf(), 12))
    }
}
