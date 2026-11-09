package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramSettingsBackup
import org.telegram.messenger.mzgram.plugins.MZGramPluginAuditJournal
import org.telegram.messenger.mzgram.plugins.MZGramPluginEngine
import org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime

// Plugins run only with "Run plugins" on, in a build with Python.
class MZGramPluginEngineTest {

    @Test
    fun switch_offByDefault() {
        // The field's own value; a saved one is the user's
        val config = MZGramConfig::class.java
        assertFalse(config.getField("pluginsRun").getBoolean(null) && !hasSaved("pluginsRun"))
    }

    @Test
    fun isOn_needsThePythonRuntime() {
        val before = MZGramConfig.pluginsRun
        try {
            MZGramConfig.pluginsRun = true
            assertEquals(MZGramPythonRuntime.isAvailable(), MZGramPluginEngine.isOn())
            MZGramConfig.pluginsRun = false
            assertFalse(MZGramPluginEngine.isOn())
        } finally {
            MZGramConfig.pluginsRun = before
        }
    }

    @Test
    fun state_ofAPluginNeverStarted() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(MZGramPluginEngine.STATE_STOPPED, MZGramPluginEngine.getState("never_added_plugin"))
            assertNull(MZGramPluginEngine.getError("never_added_plugin"))
            assertFalse(MZGramPluginEngine.hasSettings("never_added_plugin"))
        }
    }

    @Test
    fun states_andEventsAreDistinct() {
        val states = listOf(MZGramPluginEngine.STATE_STOPPED, MZGramPluginEngine.STATE_STARTING,
                MZGramPluginEngine.STATE_RUNNING, MZGramPluginEngine.STATE_FAILED)
        assertEquals(states.size, states.toSet().size)
        val events = listOf(MZGramPluginAuditJournal.EVENT_STARTED, MZGramPluginAuditJournal.EVENT_START_FAILED,
                MZGramPluginAuditJournal.EVENT_STOPPED, MZGramPluginAuditJournal.EVENT_ERROR,
                MZGramPluginAuditJournal.EVENT_LOG, MZGramPluginAuditJournal.EVENT_HOOK_FAILED,
                MZGramPluginAuditJournal.EVENT_ENABLED, MZGramPluginAuditJournal.EVENT_DISABLED)
        assertEquals(events.size, events.toSet().size)
    }

    @Test
    fun backup_leavesTheSwitchOut() {
        // Running plugins is a choice of this device, like the plugins themselves
        assertNull(MZGramSettingsBackup.accepted("mzgram_config", "pluginsRun", true))
    }

    private fun hasSaved(key: String): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getSharedPreferences("mzgram_config", android.content.Context.MODE_PRIVATE).contains(key)
    }
}
