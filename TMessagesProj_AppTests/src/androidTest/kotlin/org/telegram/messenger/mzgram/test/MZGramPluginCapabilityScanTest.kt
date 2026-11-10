package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.plugins.MZGramPluginCapabilityScan
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat

// The result of the capability scan of a plugin's code: its JSON, the order
// of the keys, the undeclared permissions and the evidence line.
class MZGramPluginCapabilityScanTest {

    @Test
    fun parse_keysInTheOrderOfThePermissions() {
        val result = MZGramPluginCapabilityScan.parse(
            """{"ok": true, "found": {"hooks": ["Xposed"], "network": ["requests", "requests", " "],
               "ui": ["x"], "error": ["y"], "never": ["subprocess"], "dex": ["size:2048", "class:a.B"],
               "files": []}}"""
        )
        assertTrue(result.isOk)
        assertEquals(listOf("network", "hooks"), result.permissions())
        assertEquals(listOf("requests"), result.evidence("network"))
        assertEquals(listOf("subprocess"), result.evidence(MZGramPluginCapabilityScan.KEY_NEVER))
        assertEquals(2048L, result.dexSize())
        assertFalse(result.found.containsKey("files"))
        assertFalse(result.found.containsKey("ui"))
        assertFalse(result.found.containsKey("error"))
    }

    @Test
    fun parse_failuresAreErrors() {
        assertEquals("FileNotFoundError: x", MZGramPluginCapabilityScan.parse("""{"ok": false, "error": "FileNotFoundError: x"}""").error)
        assertFalse(MZGramPluginCapabilityScan.parse("not json").isOk)
        val empty = MZGramPluginCapabilityScan.parse("""{"ok": true, "found": {}}""")
        assertTrue(empty.isOk)
        assertTrue(empty.permissions().isEmpty())
        assertEquals(0L, empty.dexSize())
    }

    @Test
    fun undeclared_onlyWhenThePluginListsItsPermissions() {
        val declared = MZGramPluginFormat.metaFromJson(
            """{"id": "a", "permissions": ["network"], "permissions_declared": true}"""
        )
        assertFalse(MZGramPluginCapabilityScan.isUndeclared(declared, "network"))
        assertTrue(MZGramPluginCapabilityScan.isUndeclared(declared, "files"))
        val silent = MZGramPluginFormat.metaFromJson("""{"id": "b", "permissions": [], "permissions_declared": false}""")
        assertFalse(MZGramPluginCapabilityScan.isUndeclared(silent, "files"))
        assertFalse(MZGramPluginCapabilityScan.isUndeclared(null, "files"))
    }

    @Test
    fun evidenceLine_countsTheRest() {
        assertEquals("a, b", MZGramPluginCapabilityScan.evidenceLine(listOf("a", "b")))
        val many = (1..9).map { "e$it" }
        assertEquals("e1, e2, e3, e4, e5, e6 +3", MZGramPluginCapabilityScan.evidenceLine(many))
        assertEquals("", MZGramPluginCapabilityScan.evidenceLine(emptyList()))
    }
}
