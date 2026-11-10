package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.plugins.MZGramPluginAuditJournal
import org.telegram.messenger.mzgram.plugins.MZGramPluginGrants
import org.telegram.messenger.mzgram.plugins.MZGramPluginPermissions
import org.telegram.messenger.mzgram.plugins.MZGramPluginSinkPolicy

// The rules of the sink gates of plugins: classes, requests to Telegram,
// remote addresses, the network record and one plugin acting for another.
class MZGramPluginSinkPolicyTest {

    private val plugin = "sink_test_plugin"
    private val other = "sink_test_other"

    @After
    fun cleanUp() {
        MZGramPluginPermissions.exit(null)
        MZGramPluginGrants.clear(plugin)
        MZGramPluginGrants.clear(other)
    }

    @Test
    fun classRule_byName() {
        assertEquals(MZGramPluginSinkPolicy.RULE_NEVER, MZGramPluginSinkPolicy.classRule("java.lang.ProcessBuilder"))
        assertEquals(MZGramPluginSinkPolicy.RULE_NEVER, MZGramPluginSinkPolicy.classRule("org.telegram.messenger.mzgram.plugins.MZGramPluginGrants"))
        assertEquals(MZGramPluginSinkPolicy.RULE_NEVER, MZGramPluginSinkPolicy.classRule("org/telegram/messenger/mzgram/plugins/MZGramPluginPermissions"))
        assertEquals(MZGramPluginSinkPolicy.RULE_NEVER, MZGramPluginSinkPolicy.classRule("org.telegram.ui.mzgram.MZGramPluginInfoActivity"))
        assertEquals(MZGramPluginSinkPolicy.RULE_HOOKS, MZGramPluginSinkPolicy.classRule("dalvik.system.InMemoryDexClassLoader"))
        assertEquals(MZGramPluginSinkPolicy.RULE_HOOKS, MZGramPluginSinkPolicy.classRule("de.robv.android.xposed.XposedBridge"))
        assertEquals(MZGramPluginSinkPolicy.RULE_NETWORK, MZGramPluginSinkPolicy.classRule("java.net.URL"))
        assertEquals(MZGramPluginSinkPolicy.RULE_NETWORK, MZGramPluginSinkPolicy.classRule("[Ljava.net.Socket;"))
        assertEquals(MZGramPluginSinkPolicy.RULE_NETWORK, MZGramPluginSinkPolicy.classRule("okhttp3.OkHttpClient\$Builder"))
        // Look-alike names are not the class
        assertEquals(MZGramPluginSinkPolicy.RULE_FREE, MZGramPluginSinkPolicy.classRule("java.net.URLDecoder"))
        assertEquals(MZGramPluginSinkPolicy.RULE_FREE, MZGramPluginSinkPolicy.classRule("java.lang.Runtime"))
        assertEquals(MZGramPluginSinkPolicy.RULE_FREE, MZGramPluginSinkPolicy.classRule("org.telegram.messenger.MessagesController"))
        assertEquals(MZGramPluginSinkPolicy.RULE_FREE, MZGramPluginSinkPolicy.classRule(null))
    }

    @Test
    fun requestKey_readSendAndAccount() {
        assertEquals(MZGramPluginGrants.MESSAGES_READ, MZGramPluginSinkPolicy.requestKey("TL_messages_getHistory"))
        assertEquals(MZGramPluginGrants.MESSAGES_READ, MZGramPluginSinkPolicy.requestKey("TL_contacts_resolveUsername"))
        assertEquals(MZGramPluginGrants.MESSAGES_SEND, MZGramPluginSinkPolicy.requestKey("TL_messages_sendMessage"))
        assertEquals(MZGramPluginGrants.MESSAGES_SEND, MZGramPluginSinkPolicy.requestKey("TL_messages_deleteMessages"))
        assertEquals(MZGramPluginGrants.HOOKS, MZGramPluginSinkPolicy.requestKey("TL_auth_sendCode"))
        assertEquals(MZGramPluginGrants.HOOKS, MZGramPluginSinkPolicy.requestKey("TL_account_getAuthorizations"))
        assertEquals(MZGramPluginGrants.MESSAGES_READ, MZGramPluginSinkPolicy.requestKey("TL_account_getNotifySettings"))
        assertEquals(MZGramPluginGrants.MESSAGES_SEND, MZGramPluginSinkPolicy.requestKey("TL_account_updateStatus"))
        assertEquals(MZGramPluginGrants.HOOKS, MZGramPluginSinkPolicy.requestKey("TL_payments_sendStarsForm"))
        assertEquals(MZGramPluginGrants.MESSAGES_READ, MZGramPluginSinkPolicy.requestKey("TL_payments_getStarsStatus"))
        assertEquals("TL_messages_getHistory", MZGramPluginSinkPolicy.requestName(org.telegram.tgnet.TLRPC.TL_messages_getHistory()))
        assertEquals("", MZGramPluginSinkPolicy.requestName(null))
    }

    @Test
    fun addresses_remoteAndHostOnly() {
        assertTrue(MZGramPluginSinkPolicy.isRemoteUrl(" HTTPS://example.org/x"))
        assertTrue(MZGramPluginSinkPolicy.isRemoteUrl("wss://example.org"))
        assertFalse(MZGramPluginSinkPolicy.isRemoteUrl("file:///sdcard/a"))
        assertFalse(MZGramPluginSinkPolicy.isRemoteUrl("about:blank"))
        assertFalse(MZGramPluginSinkPolicy.isRemoteUrl(null))
        assertEquals("example.org:8443", MZGramPluginSinkPolicy.hostOf("https://user:pw@Example.org:8443/path?token=secret#x"))
        assertEquals("example.org", MZGramPluginSinkPolicy.hostOf("example.org/a"))
        assertEquals("", MZGramPluginSinkPolicy.hostOf(null))
    }

    @Test
    fun network_needsThePermissionAndIsLogged() {
        assertTrue(MZGramPluginSinkPolicy.allowNetwork(null, "connect", "example.org"))
        assertTrue(MZGramPluginGrants.setGranted(plugin, emptyList()))
        assertFalse(MZGramPluginSinkPolicy.allowNetwork(plugin, "connect", "example.org:443"))
        assertTrue(MZGramPluginGrants.setGranted(plugin, listOf(MZGramPluginGrants.NETWORK)))
        assertTrue(MZGramPluginSinkPolicy.allowNetwork(plugin, "connect", "example.org:443"))
        val last = MZGramPluginAuditJournal.entries(plugin).last()
        assertEquals(MZGramPluginAuditJournal.EVENT_NETWORK, last.event)
        assertEquals("example.org:443", last.detail)
    }

    @Test
    fun classes_forThePlugin() {
        assertTrue(MZGramPluginSinkPolicy.allowClass(null, "java.lang.ProcessBuilder"))
        assertTrue(MZGramPluginGrants.setGranted(plugin, listOf(MZGramPluginGrants.NETWORK)))
        assertFalse(MZGramPluginSinkPolicy.allowClass(plugin, "java.lang.ProcessBuilder"))
        assertEquals(MZGramPluginAuditJournal.EVENT_SINK_BLOCKED, MZGramPluginAuditJournal.entries(plugin).last().event)
        assertTrue(MZGramPluginSinkPolicy.allowClass(plugin, "java.net.URL"))
        assertFalse(MZGramPluginSinkPolicy.allowClass(plugin, "dalvik.system.DexClassLoader"))
        assertTrue(MZGramPluginSinkPolicy.allowClass(plugin, "android.graphics.Color"))
    }

    @Test
    fun foreign_onlyOutsideTheOwnCode() {
        // MZGram's own code acts for every plugin
        assertFalse(MZGramPluginSinkPolicy.refuseForeign(other, "settings"))
        val outer = MZGramPluginPermissions.enter(plugin)
        assertTrue(MZGramPluginPermissions.isOnThread(plugin))
        assertFalse(MZGramPluginSinkPolicy.refuseForeign(plugin, "settings"))
        assertTrue(MZGramPluginSinkPolicy.refuseForeign(other, "settings"))
        // A hook of the other plugin inside this one: both are on the thread
        val inner = MZGramPluginPermissions.enter(other)
        assertFalse(MZGramPluginSinkPolicy.refuseForeign(other, "settings"))
        MZGramPluginPermissions.exit(inner)
        assertFalse(MZGramPluginPermissions.isOnThread(other))
        MZGramPluginPermissions.exit(outer)
        assertNull(MZGramPluginPermissions.current())
        assertFalse(MZGramPluginPermissions.isOnThread(plugin))
    }

    @Test
    fun dataDir_insideTheAppFiles() {
        val dir = MZGramPluginSinkPolicy.dataDirOf(plugin)!!
        assertEquals(plugin, dir.name)
        assertEquals(MZGramPluginSinkPolicy.DATA_DIR, dir.parentFile!!.name)
        assertNull(MZGramPluginSinkPolicy.dataDirOf("../bad"))
        assertTrue(MZGramPluginSinkPolicy.paths().contains("plugin_data"))
    }
}
