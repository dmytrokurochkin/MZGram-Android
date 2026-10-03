package org.telegram.messenger.mzgram.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC

// Settings > MZGram: which switches exist and what they do.
class MZGramSettingsTest {

    private val account = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preferences() = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)

    // The "hide others' online status" switch is gone: another user's
    // status is shown as it is, also for someone who had the switch on.
    @Test
    fun othersOnlineStatus_isAlwaysShown() {
        preferences().edit().putBoolean("hideOthersOnlineStatus", true).commit()
        MZGramConfig.loadConfig(true)

        assertTrue("no such setting", MZGramConfig::class.java.declaredFields.none { it.name == "hideOthersOnlineStatus" })
        assertEquals("no such switch", 0, context.resources.getIdentifier("MZGramHideOthersOnlineStatus", "string", context.packageName))
        assertFalse("the old value is cleared", preferences().contains("hideOthersOnlineStatus"))

        val user = TLRPC.TL_user().apply {
            id = 7_600_000_001L
            first_name = "MZGram test other"
            status = TLRPC.TL_userStatusOnline().apply {
                expires = ConnectionsManager.getInstance(account).currentTime + 300
            }
        }
        val online = BooleanArray(1)
        val text = LocaleController.formatUserStatus(account, user, online)
        assertTrue("shown online", online[0])
        assertEquals(LocaleController.getString(R.string.Online), text)
    }
}
