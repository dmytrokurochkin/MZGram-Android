package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.UItem
import org.telegram.ui.SettingsActivity
import org.telegram.ui.mzgram.MZGramSettingsActivity

// Telegram's Settings: the MZGram row ("Features of this client") is the
// very first row of the list, above My Account and every Telegram setting.
class MZGramAppSettingsOrderTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun mzgramRow_isTheFirstSettingsRow() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val settings = SettingsActivity()
        MZGramScreens.open(settings)
        assertTrue("settings shown", MZGramScreens.waitFor(30) { settings.fragmentView?.isShown == true })
        Thread.sleep(1000)
        MZGramScreens.capture("settings-mzgram-first")
        val ids = ArrayList<Int>()
        instrumentation.runOnMainSync {
            val list = SettingsActivity::class.java.getDeclaredField("listView").also { it.isAccessible = true }.get(settings)
            val adapter = list.javaClass.getMethod("getAdapter").invoke(list)
            val count = adapter.javaClass.getMethod("getItemCount").invoke(adapter) as Int
            val getItem = adapter.javaClass.getMethod("getItem", Int::class.javaPrimitiveType)
            for (i in 0 until count) {
                val item = getItem.invoke(adapter, i) as UItem? ?: continue
                // The setting rows: 1 My Account ... 10 Language, MZGram's own id
                if (item.id >= 1) {
                    ids.add(item.id)
                }
            }
        }
        MZGramScreens.log("settings rows: $ids")
        assertEquals("the first row: $ids", MZGramSettingsActivity.SETTINGS_ROW_ID, ids.first())
        assertTrue("above My Account: $ids", ids.indexOf(MZGramSettingsActivity.SETTINGS_ROW_ID) < ids.indexOf(1))
        assertEquals("once: $ids", 1, ids.count { it == MZGramSettingsActivity.SETTINGS_ROW_ID })
        instrumentation.runOnMainSync { settings.finishFragment(false) }
    }
}
