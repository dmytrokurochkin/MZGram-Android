package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.plugins.MZGramPluginEngine
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat
import org.telegram.messenger.mzgram.plugins.MZGramPlugins
import org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// A plugin file all the way to a running plugin, in a build with Python
// (-PmzgramPlugins=true): Python reads the metadata, the file is installed
// and turned on, the engine starts it. A plugin that raises, or that tries
// to start a process, ends up failed. Skipped in the default build, which
// has no Python.
class MZGramPluginRuntimeSmokeTest {

    private var runBefore = false
    private var changed = false
    private val ids = ArrayList<String>()

    @Before
    fun needsPython() {
        assumeTrue("no Python runtime in this build", MZGramPythonRuntime.isAvailable())
        runBefore = MZGramConfig.pluginsRun
        changed = true
        MZGramConfig.pluginsRun = true
    }

    @After
    fun cleanUp() {
        for (id in ids) {
            MZGramPlugins.delete(id)
        }
        if (changed) {
            MZGramConfig.pluginsRun = runBefore
        }
    }

    @Test
    fun plugin_runsFromItsFile() {
        assertTrue("Run plugins is on and Safe Mode is off", MZGramPluginEngine.isOn())
        val id = "mz_smoke_runs"
        val state = installAndStart(id, """
            class SmokePlugin(BasePlugin):
                def on_plugin_load(self):
                    self.loaded = True
        """)
        assertEquals("error: " + MZGramPluginEngine.getError(id), MZGramPluginEngine.STATE_RUNNING, state)
    }

    @Test
    fun plugin_importsTheBundledPackages() {
        val id = "mz_smoke_packages"
        val state = installAndStart(id, """
            class SmokePlugin(BasePlugin):
                def on_plugin_load(self):
                    import yaml, bs4, PIL, packaging.version, requests
                    self.value = yaml.safe_load("a: 1")["a"]
        """)
        assertEquals("error: " + MZGramPluginEngine.getError(id), MZGramPluginEngine.STATE_RUNNING, state)
    }

    @Test
    fun plugin_thatRaises_isFailed() {
        val id = "mz_smoke_raises"
        val state = installAndStart(id, """
            class SmokePlugin(BasePlugin):
                def on_plugin_load(self):
                    raise RuntimeError("smoke")
        """)
        assertEquals(MZGramPluginEngine.STATE_FAILED, state)
        val error = MZGramPluginEngine.getError(id)
        assertTrue("error: $error", error != null && error.contains("RuntimeError"))
    }

    @Test
    fun plugin_cannotStartAProcess() {
        val id = "mz_smoke_process"
        val state = installAndStart(id, """
            class SmokePlugin(BasePlugin):
                def on_plugin_load(self):
                    import subprocess
                    subprocess.run(["sh", "-c", "true"])
        """)
        assertEquals(MZGramPluginEngine.STATE_FAILED, state)
        val error = MZGramPluginEngine.getError(id)
        assertTrue("error: $error", error != null && error.contains("PermissionError"))
    }

    // Writes, reads, installs (no permissions) and turns on the plugin; the
    // state it reached: running, failed, or what it was when time ran out.
    private fun installAndStart(id: String, body: String): Int {
        val file = File(ApplicationLoader.applicationContext.cacheDir, "$id.plugin")
        file.writeText(
            "from base_plugin import BasePlugin\n\n" +
                "__id__ = \"$id\"\n" +
                "__name__ = \"Smoke test $id\"\n" +
                "__version__ = \"1.0\"\n" +
                "__permissions__ = []\n\n" +
                body.trimIndent() + "\n"
        )

        val read = CountDownLatch(1)
        var result: MZGramPluginFormat.Result? = null
        // The first call starts Python, which unpacks its files on a new install
        MZGramPluginFormat.readMetadata(file) { r ->
            result = r
            read.countDown()
        }
        assertTrue("metadata read in time", read.await(180, TimeUnit.SECONDS))
        val meta = result?.meta
        assertNotNull("metadata: " + result?.error, meta)
        assertEquals(id, meta!!.id)
        ids.add(id)

        val installed = CountDownLatch(1)
        var ok = false
        MZGramPlugins.install(file, meta, null, emptyList<String>()) { done ->
            ok = done
            installed.countDown()
        }
        assertTrue("installed in time", installed.await(30, TimeUnit.SECONDS))
        assertTrue("installed", ok)

        MZGramPlugins.setEnabled(id, true)
        val deadline = System.currentTimeMillis() + 120_000
        var state = MZGramPluginEngine.getState(id)
        while (state != MZGramPluginEngine.STATE_RUNNING && state != MZGramPluginEngine.STATE_FAILED &&
            System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            state = MZGramPluginEngine.getState(id)
        }
        return state
    }
}
