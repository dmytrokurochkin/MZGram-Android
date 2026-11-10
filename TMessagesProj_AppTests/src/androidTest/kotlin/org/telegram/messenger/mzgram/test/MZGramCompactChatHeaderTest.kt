package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig

class MZGramCompactChatHeaderTest {

    @Test
    fun compactChatHeader_defaultIsFalse() {
        val original = MZGramConfig.compactChatHeader
        try {
            MZGramConfig.compactChatHeader = false
            assertFalse(MZGramConfig.compactChatHeader)

            MZGramConfig.toggleCompactChatHeader()
            assertTrue(MZGramConfig.compactChatHeader)

            MZGramConfig.toggleCompactChatHeader()
            assertFalse(MZGramConfig.compactChatHeader)
        } finally {
            MZGramConfig.compactChatHeader = original
        }
    }
}
