package org.telegram.messenger.mzgram.test

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.plugins.MZGramPluginMenus

class MZGramPluginSdkTest {

    @Test
    fun menuContext_wrapsMessageFieldsProperly() {
        val context = JSONObject()
        context.put("account", 0)
        context.put("dialog_id", 12345L)
        context.put("message_id", 42)
        context.put("from_id", 999L)
        context.put("out", true)
        context.put("date", 1700000000)
        context.put("text", "Test message for plugin")

        assertEquals(12345L, context.getLong("dialog_id"))
        assertEquals(42, context.getInt("message_id"))
        assertEquals("Test message for plugin", context.getString("text"))
        assertTrue(context.getBoolean("out"))
    }

    @Test
    fun pluginMenus_placesAreDefined() {
        assertNotNull(MZGramPluginMenus.MESSAGE_CONTEXT_MENU)
        assertNotNull(MZGramPluginMenus.CHAT_ACTION_MENU)
        assertNotNull(MZGramPluginMenus.PROFILE_ACTION_MENU)
        assertNotNull(MZGramPluginMenus.MAIN_MENU)
        assertNotNull(MZGramPluginMenus.DRAWER_MENU)

        assertEquals("MESSAGE_CONTEXT_MENU", MZGramPluginMenus.MESSAGE_CONTEXT_MENU)
        assertEquals("CHAT_ACTION_MENU", MZGramPluginMenus.CHAT_ACTION_MENU)
        assertEquals("PROFILE_ACTION_MENU", MZGramPluginMenus.PROFILE_ACTION_MENU)
    }
}
