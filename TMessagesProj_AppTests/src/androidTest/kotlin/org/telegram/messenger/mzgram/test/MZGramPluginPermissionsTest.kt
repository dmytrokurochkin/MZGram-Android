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

// The permission model of plugins: the dangerous keys, the mark of the
// plugin whose code runs on the thread, the checks and their cache, and
// that plugin code can't change permissions.
class MZGramPluginPermissionsTest {

    private val plugin = "permissions_test_plugin"
    private val other = "permissions_test_other"

    @After
    fun cleanUp() {
        MZGramPluginPermissions.exit(null)
        MZGramPluginGrants.clear(plugin)
        MZGramPluginGrants.clear(other)
    }

    @Test
    fun dangerous_onlyHooksAndNative() {
        for (key in MZGramPluginGrants.ALL) {
            val dangerous = key == MZGramPluginGrants.HOOKS || key == MZGramPluginGrants.NATIVE
            assertEquals(key, dangerous, MZGramPluginPermissions.isDangerous(key))
            assertTrue(MZGramPluginPermissions.describe(key).isNotEmpty())
        }
        assertFalse(MZGramPluginPermissions.isDangerous(null))
    }

    @Test
    fun mark_nestsAndComesBack() {
        assertNull(MZGramPluginPermissions.current())
        val first = MZGramPluginPermissions.enter(plugin)
        assertNull(first)
        assertEquals(plugin, MZGramPluginPermissions.current())
        assertTrue(MZGramPluginPermissions.insidePlugin())
        // The same plugin again, then back
        val again = MZGramPluginPermissions.enter(plugin)
        assertEquals(plugin, again)
        MZGramPluginPermissions.exit(again)
        assertEquals(plugin, MZGramPluginPermissions.current())
        MZGramPluginPermissions.exit(first)
        assertNull(MZGramPluginPermissions.current())
        assertFalse(MZGramPluginPermissions.insidePlugin())
        // An invalid id marks nothing
        MZGramPluginPermissions.exit(MZGramPluginPermissions.enter("../bad id"))
        assertNull(MZGramPluginPermissions.current())
    }

    @Test
    fun mark_nestingNeverAddsPermissions() {
        assertTrue(MZGramPluginGrants.setGranted(other, listOf(MZGramPluginGrants.NETWORK, MZGramPluginGrants.FILES)))
        assertTrue(MZGramPluginGrants.setGranted(plugin, emptyList()))
        val outer = MZGramPluginPermissions.enter(plugin)
        val inner = MZGramPluginPermissions.enter(other)
        // The poorer plugin stays marked
        assertEquals(plugin, MZGramPluginPermissions.current())
        MZGramPluginPermissions.exit(inner)
        MZGramPluginPermissions.exit(outer)
        val richer = MZGramPluginPermissions.enter(other)
        val poorer = MZGramPluginPermissions.enter(plugin)
        assertEquals(plugin, MZGramPluginPermissions.current())
        MZGramPluginPermissions.exit(poorer)
        assertEquals(other, MZGramPluginPermissions.current())
        MZGramPluginPermissions.exit(richer)
        assertNull(MZGramPluginPermissions.current())
    }

    @Test
    fun checks_followTheRecord() {
        assertTrue(MZGramPluginPermissions.has(plugin, MZGramPluginGrants.UI))
        assertFalse(MZGramPluginPermissions.has(plugin, MZGramPluginGrants.NETWORK))
        assertTrue(MZGramPluginGrants.setGranted(plugin, listOf(MZGramPluginGrants.NETWORK)))
        assertTrue(MZGramPluginPermissions.has(plugin, MZGramPluginGrants.NETWORK))
        assertTrue(MZGramPluginPermissions.check(plugin, MZGramPluginGrants.NETWORK, "test"))
        assertTrue(MZGramPluginGrants.revoke(plugin, MZGramPluginGrants.NETWORK))
        // The cache follows the change at once
        assertFalse(MZGramPluginPermissions.has(plugin, MZGramPluginGrants.NETWORK))
        assertFalse(MZGramPluginPermissions.check(plugin, MZGramPluginGrants.NETWORK, "test"))
        assertFalse(MZGramPluginPermissions.has(plugin, "teleport"))
        assertFalse(MZGramPluginPermissions.has(null, MZGramPluginGrants.UI))
    }

    @Test
    fun checkCurrent_onlyInsidePluginCode() {
        // MZGram's own code is never asked
        assertTrue(MZGramPluginPermissions.checkCurrent(MZGramPluginGrants.NATIVE, "test"))
        val previous = MZGramPluginPermissions.enter(plugin)
        assertFalse(MZGramPluginPermissions.checkCurrent(MZGramPluginGrants.NATIVE, "test"))
        assertTrue(MZGramPluginPermissions.checkCurrent(MZGramPluginGrants.UI, "test"))
        MZGramPluginPermissions.exit(previous)
    }

    @Test
    fun pluginCode_cannotChangePermissions() {
        assertTrue(MZGramPluginGrants.setGranted(plugin, emptyList()))
        val previous = MZGramPluginPermissions.enter(plugin)
        assertFalse(MZGramPluginGrants.grant(plugin, MZGramPluginGrants.HOOKS))
        assertFalse(MZGramPluginGrants.setGranted(other, listOf(MZGramPluginGrants.NETWORK)))
        MZGramPluginPermissions.exit(previous)
        assertFalse(MZGramPluginPermissions.has(plugin, MZGramPluginGrants.HOOKS))
        assertFalse(MZGramPluginGrants.hasRecord(other))
        // Outside plugin code the user's change goes through
        assertTrue(MZGramPluginGrants.grant(plugin, MZGramPluginGrants.HOOKS))
        assertTrue(MZGramPluginPermissions.has(plugin, MZGramPluginGrants.HOOKS))
    }

    @Test
    fun denied_isItsOwnEvent() {
        val events = listOf(MZGramPluginAuditJournal.EVENT_PERMISSION_DENIED, MZGramPluginAuditJournal.EVENT_HOOK_NO_PERMISSION,
                MZGramPluginAuditJournal.EVENT_GRANTED, MZGramPluginAuditJournal.EVENT_REVOKED,
                MZGramPluginAuditJournal.EVENT_HOOKED, MZGramPluginAuditJournal.EVENT_LOG)
        assertEquals(events.size, events.toSet().size)
    }
}
