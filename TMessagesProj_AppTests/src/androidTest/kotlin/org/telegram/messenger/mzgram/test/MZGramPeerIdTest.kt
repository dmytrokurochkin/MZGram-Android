package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramPeerId

// Settings > MZGram > Interface > Show ID: hidden by default; Telegram API
// the bare number; Bot API -100 before a channel or supergroup, - before a
// group, a user or bot as is.
class MZGramPeerIdTest {

    private val before = MZGramConfig.peerIdMode

    @After
    fun tearDown() {
        MZGramConfig.setPeerIdMode(before)
    }

    @Test
    fun format_hiddenTelegramApiBotApi() {
        assertEquals("", MZGramPeerId.format(123, MZGramPeerId.USER, MZGramPeerId.HIDDEN))
        assertEquals("", MZGramPeerId.format(123, MZGramPeerId.CHANNEL, MZGramPeerId.HIDDEN))
        assertEquals("777000", MZGramPeerId.format(777000, MZGramPeerId.USER, MZGramPeerId.TELEGRAM_API))
        assertEquals("777000", MZGramPeerId.format(777000, MZGramPeerId.USER, MZGramPeerId.BOT_API))
        assertEquals("456", MZGramPeerId.format(456, MZGramPeerId.GROUP, MZGramPeerId.TELEGRAM_API))
        assertEquals("-456", MZGramPeerId.format(456, MZGramPeerId.GROUP, MZGramPeerId.BOT_API))
        assertEquals("1234567890", MZGramPeerId.format(1234567890, MZGramPeerId.CHANNEL, MZGramPeerId.TELEGRAM_API))
        assertEquals("-1001234567890", MZGramPeerId.format(1234567890, MZGramPeerId.CHANNEL, MZGramPeerId.BOT_API))
        assertEquals("-1000000000123", MZGramPeerId.format(123, MZGramPeerId.CHANNEL, MZGramPeerId.BOT_API))
    }

    @Test
    fun hiddenByDefault_andTheChoiceIsKept() {
        val preferences = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("mzgram_config", android.content.Context.MODE_PRIVATE)
        preferences.edit().remove("peerIdMode").commit()
        MZGramConfig.loadConfig(true)
        assertEquals(MZGramPeerId.HIDDEN, MZGramConfig.peerIdMode)
        MZGramConfig.setPeerIdMode(MZGramPeerId.BOT_API)
        MZGramConfig.loadConfig(true)
        assertEquals(MZGramPeerId.BOT_API, MZGramConfig.peerIdMode)
    }
}
