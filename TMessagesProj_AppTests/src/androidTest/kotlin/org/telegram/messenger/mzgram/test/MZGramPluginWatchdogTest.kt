package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.plugins.MZGramPluginEngine
import org.telegram.messenger.mzgram.plugins.MZGramPluginWatchdog

class MZGramPluginWatchdogTest {

    @Test
    fun safeMode_blocksEngine() {
        val beforeSafe = MZGramPluginWatchdog.isSafeMode()
        val beforeRun = MZGramConfig.pluginsRun
        try {
            MZGramPluginWatchdog.setSafeMode(false)
            assertFalse(MZGramPluginWatchdog.isSafeMode())

            MZGramPluginWatchdog.setSafeMode(true)
            assertTrue(MZGramPluginWatchdog.isSafeMode())
            // Engine is never on while in safe mode
            assertFalse(MZGramPluginEngine.isOn())

            MZGramPluginWatchdog.setSafeMode(false)
            assertFalse(MZGramPluginWatchdog.isSafeMode())
        } finally {
            MZGramPluginWatchdog.setSafeMode(beforeSafe)
            MZGramConfig.pluginsRun = beforeRun
        }
    }

    @Test
    fun findPluginInTrace_extractsId() {
        val trace = arrayOf(
            StackTraceElement("java.lang.Thread", "run", "Thread.java", 100),
            StackTraceElement("<python>.mzgram_plugin_translator.translate", "run", "translator.py", 42),
            StackTraceElement("org.telegram.messenger.Utilities", "run", "Utilities.java", 10)
        )
        val ex = RuntimeException("boom")
        ex.stackTrace = trace

        val found = MZGramPluginWatchdog.findPluginInTrace(ex)
        assertEquals("translator", found)
    }

    @Test
    fun findPluginInTrace_returnsNullWithoutPlugin() {
        val ex = RuntimeException("standard error")
        assertNull(MZGramPluginWatchdog.findPluginInTrace(ex))
    }

    @Test
    fun executionTracking_enterAndExit() {
        assertFalse(MZGramPluginWatchdog.isNotResponding("test_plugin"))
        MZGramPluginWatchdog.notePluginEnter("test_plugin")
        // Just entered, so not yet frozen (< 5000ms)
        assertFalse(MZGramPluginWatchdog.isNotResponding("test_plugin"))
        MZGramPluginWatchdog.notePluginExit("test_plugin")
        assertFalse(MZGramPluginWatchdog.isNotResponding("test_plugin"))
    }
}
