package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramKeepFrom
import org.telegram.messenger.mzgram.MZGramKeepFrom.BOT
import org.telegram.messenger.mzgram.MZGramKeepFrom.CHANNEL
import org.telegram.messenger.mzgram.MZGramKeepFrom.CHAT_CHANNEL
import org.telegram.messenger.mzgram.MZGramKeepFrom.CHAT_GROUP
import org.telegram.messenger.mzgram.MZGramKeepFrom.CHAT_PRIVATE
import org.telegram.messenger.mzgram.MZGramKeepFrom.GROUP
import org.telegram.messenger.mzgram.MZGramKeepFrom.USER

// Settings > MZGram > Archive > Keep messages: from users, from bots, in
// groups, in channels, all on by default. The author decides first (a bot's
// message goes by From bots in any chat), then the chat. Turning one off
// keeps what was saved.
class MZGramKeepFromTest {

    @After
    fun tearDown() {
        MZGramConfig.saveFromUsers = true
        MZGramConfig.saveForBots = true
        MZGramConfig.saveInGroups = true
        MZGramConfig.saveInChannels = true
    }

    @Test
    fun theAuthorThenTheChat() {
        assertEquals(USER, MZGramKeepFrom.sourceOf(false, CHAT_PRIVATE))
        assertEquals(BOT, MZGramKeepFrom.sourceOf(true, CHAT_PRIVATE))
        assertEquals("a bot in a group", BOT, MZGramKeepFrom.sourceOf(true, CHAT_GROUP))
        assertEquals(BOT, MZGramKeepFrom.sourceOf(true, CHAT_CHANNEL))
        assertEquals("a person, a channel writing or an anonymous admin in a group", GROUP, MZGramKeepFrom.sourceOf(false, CHAT_GROUP))
        assertEquals(CHANNEL, MZGramKeepFrom.sourceOf(false, CHAT_CHANNEL))
    }

    @Test
    fun allOnByDefault_eachDecidesAlone() {
        val sources = listOf(USER, BOT, GROUP, CHANNEL)
        for (source in sources) assertTrue("on by default: $source", MZGramKeepFrom.saves(source))
        val off = listOf<() -> Unit>(
            { MZGramConfig.saveFromUsers = false },
            { MZGramConfig.saveForBots = false },
            { MZGramConfig.saveInGroups = false },
            { MZGramConfig.saveInChannels = false },
        )
        for (i in sources.indices) {
            tearDown()
            off[i]()
            for (j in sources.indices) {
                assertEquals("switch $i for source $j", i != j, MZGramKeepFrom.saves(sources[j]))
            }
        }
    }

    @Test
    fun aGroupsBots_withInGroupsOff() {
        MZGramConfig.saveInGroups = false
        assertTrue("a group may still have bots' messages to keep", MZGramKeepFrom.maySaveChat(false, CHAT_GROUP))
        MZGramConfig.saveForBots = false
        assertFalse(MZGramKeepFrom.maySaveChat(false, CHAT_GROUP))
        tearDown()
        MZGramConfig.saveForBots = false
        assertFalse("a private chat with a bot", MZGramKeepFrom.maySaveChat(true, CHAT_PRIVATE))
        assertTrue(MZGramKeepFrom.maySaveChat(false, CHAT_PRIVATE))
        MZGramConfig.saveInChannels = false
        assertFalse(MZGramKeepFrom.maySaveChat(false, CHAT_CHANNEL))
    }
}
