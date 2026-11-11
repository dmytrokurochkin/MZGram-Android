package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.mzgram.plugins.MZGramPluginCapabilityScan
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat
import org.telegram.messenger.mzgram.plugins.MZGramPluginGrants
import org.telegram.messenger.mzgram.plugins.MZGramPlugins
import org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MZGramPluginFileInstallTest {

    @Test
    fun testInstall_validPluginFile() {
        val pluginId = "test.install.valid"
        val metaJson = """
            {
                "id": "$pluginId",
                "name": "Test Valid Plugin",
                "version": "1.0.0",
                "author": "Test Author",
                "description": "A valid test plugin",
                "permissions": ["network"],
                "permissions_declared": true
            }
        """.trimIndent()

        val meta = MZGramPluginFormat.metaFromJson(metaJson)
        assertNotNull("Metadata must parse correctly", meta)
        assertEquals(pluginId, meta!!.id)
        assertEquals("Test Valid Plugin", meta.name)
        assertTrue(meta.permissionsDeclared)

        // Create temporary dummy .plugin file
        val cacheDir = ApplicationLoader.applicationContext.cacheDir
        val tempFile = File(cacheDir, "$pluginId.plugin")
        FileOutputStream(tempFile).use { it.write("# Test plugin code\nprint('hello')\n".toByteArray()) }

        val latch = CountDownLatch(1)
        var installOk = false

        try {
            MZGramPlugins.install(tempFile, meta, null, listOf("network")) { ok ->
                installOk = ok
                latch.countDown()
            }

            assertTrue("Installation callback must be called within timeout", latch.await(5, TimeUnit.SECONDS))
            assertTrue("Installation of valid plugin must succeed", installOk)

            // Verify plugin exists in registry and stored metadata matches
            val installed = MZGramPlugins.get(pluginId)
            assertNotNull("Installed plugin must be present in MZGramPlugins", installed)
            assertEquals("Test Valid Plugin", installed!!.meta.name)
            assertEquals("1.0.0", installed.meta.version)

            // Verify grants
            val grants = MZGramPluginGrants.getStored(pluginId)
            assertNotNull("Stored grants must not be null", grants)
            assertTrue("Granted permissions must contain network", grants!!.contains("network"))
        } finally {
            MZGramPlugins.delete(pluginId)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    @Test
    fun testInstall_corruptedFile() {
        // Corrupted file (not valid json, syntax error, or incomplete payload)
        val corruptedJson = "not a json string at all"
        val result = MZGramPluginFormat.parse(corruptedJson)
        assertFalse("Parsing corrupted JSON must return isOk=false", result.isOk)
        assertNotNull("Error description must be present for corrupted JSON", result.error)

        // When install is called with null picked file or null meta, it must return false safely
        val latch = CountDownLatch(1)
        var installOk: Boolean? = null
        MZGramPlugins.install(null, null, null) { ok ->
            installOk = ok
            latch.countDown()
        }
        assertTrue("Callback must fire", latch.await(2, TimeUnit.SECONDS))
        assertFalse("Install with null parameters must fail gracefully", installOk == true)
    }

    @Test
    fun testInstall_missingMetadata() {
        // Missing id field
        val missingIdJson = """{"ok": true, "meta": {"name": "No ID Plugin", "version": "1.0"}}"""
        val resultNoId = MZGramPluginFormat.parse(missingIdJson)
        assertFalse("Metadata without id must return isOk=false", resultNoId.isOk)
        assertEquals("no metadata", resultNoId.error)

        // Empty metadata dictionary
        val emptyMetaJson = """{"ok": true, "meta": {}}"""
        val resultEmpty = MZGramPluginFormat.parse(emptyMetaJson)
        assertFalse("Empty metadata dictionary must return isOk=false", resultEmpty.isOk)
        assertEquals("no metadata", resultEmpty.error)

        // Syntax error reported by python reader
        val syntaxErrorJson = """{"ok": false, "error": "SyntaxError: invalid syntax on line 1"}"""
        val resultSyntax = MZGramPluginFormat.parse(syntaxErrorJson)
        assertFalse("Syntax error from parser must return isOk=false", resultSyntax.isOk)
        assertEquals("SyntaxError: invalid syntax on line 1", resultSyntax.error)

        // metaFromJson on missing id must return null
        assertNull("metaFromJson without id must return null", MZGramPluginFormat.metaFromJson("""{"name": "No ID"}"""))
    }

    @Test
    fun testCapabilityScan_withPythonRuntime_orSkippedWhenUnavailable() {
        // Capability AST scanning requires Python runtime (present only with -PmzgramPlugins=true)
        assumeTrue("Skipping AST scan test because Python runtime is unavailable in this build", MZGramPythonRuntime.isAvailable())

        val latch = CountDownLatch(1)
        var scanResult: MZGramPluginCapabilityScan.Result? = null
        val temp = File(ApplicationLoader.applicationContext.cacheDir, "test_scan.plugin")
        temp.writeText("import os\nprint('hello')\n")
        try {
            MZGramPluginCapabilityScan.scan(temp) { res ->
                scanResult = res
                latch.countDown()
            }
            assertTrue("Scan callback must execute", latch.await(5, TimeUnit.SECONDS))
            assertNotNull("Scan result must be non-null", scanResult)
            assertTrue("Valid python code must scan cleanly", scanResult!!.isOk)
        } finally {
            if (temp.exists()) {
                temp.delete()
            }
        }
    }
}
