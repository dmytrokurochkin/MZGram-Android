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
}
