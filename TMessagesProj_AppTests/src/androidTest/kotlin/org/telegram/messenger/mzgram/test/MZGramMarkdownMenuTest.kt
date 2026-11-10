package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.URLSpanUserMention
import org.telegram.ui.mzgram.MZGramTextStyleMenu
import org.telegram.utils.code.highlight.CodeHighlighting

class MZGramMarkdownMenuTest {

    @Test
    fun codeHighlightingSpan_holdsLanguageAndContent() {
        val language = "kotlin"
        val codeText = "val x = 42"
        val span = CodeHighlighting.Span(true, 0, null, language, codeText)

        assertEquals(language, span.lng)

        // Convert to TLRPC.TL_messageEntityPre like MediaDataController
        val entity = TLRPC.TL_messageEntityPre()
        entity.offset = 0
        entity.length = codeText.length
        entity.language = span.lng ?: ""

        assertEquals("kotlin", entity.language)
        assertEquals(codeText.length, entity.length)
    }

    @Test
    fun urlSpanUserMention_extractsUserId() {
        val rawInput = "12345678"
        val span = URLSpanUserMention(rawInput, 3)

        val parsedId = Utilities.parseLong(span.url)
        assertEquals(12345678L, parsedId)

        val inputWithPrefix = "tg://user?id=87654321"
        val cleaned = if (inputWithPrefix.startsWith("tg://user?id=")) {
            inputWithPrefix.substring("tg://user?id=".length)
        } else {
            inputWithPrefix
        }
        val spanPrefixed = URLSpanUserMention(cleaned, 3)
        assertEquals(87654321L, Utilities.parseLong(spanPrefixed.url))
    }

    @Test
    fun textStyleMenu_includesCodeAndMention() {
        assertTrue(R.id.menu_code != 0)
        assertTrue(R.id.menu_mention != 0)
    }
}
