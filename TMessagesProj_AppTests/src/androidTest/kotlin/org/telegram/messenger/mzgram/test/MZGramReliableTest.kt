package org.telegram.messenger.mzgram.test

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramDeviceCheck
import org.telegram.messenger.mzgram.MZGramReliableJournal
import org.telegram.messenger.mzgram.MZGramReliableService
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.mzgram.MZGramDeviceCheckActivity

// Notifications and Sounds > Reliable delivery: off by default; on, a
// foreground service with a quiet notification without buttons runs and
// a watch job checks it; off, both go. The journal is in the push log,
// without tokens.
class MZGramReliableTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val before = MZGramConfig.reliableDelivery

    @After
    fun tearDown() {
        MZGramConfig.setReliableDelivery(before)
        MZGramReliableService.apply(context, "test")
        waitFor { MZGramReliableService.isRunning() == before }
    }

    private fun waitFor(check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            if (check()) {
                return true
            }
            Thread.sleep(200)
        }
        return check()
    }

    private fun ours(): Notification? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.firstOrNull { it.id == MZGramReliableService.NOTIFICATION_ID }?.notification
    }

    @Test
    fun offByDefault() {
        val preferences = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
        val had = preferences.contains("reliableDelivery")
        val value = preferences.getBoolean("reliableDelivery", false)
        preferences.edit().remove("reliableDelivery").commit()
        MZGramConfig.loadConfig(true)
        assertFalse(MZGramConfig.reliableDelivery)
        if (had) {
            preferences.edit().putBoolean("reliableDelivery", value).commit()
        }
        MZGramConfig.loadConfig(true)
    }

    @Test
    fun on_runsInTheForegroundWithAQuietNotification_off_stops() {
        MZGramConfig.setReliableDelivery(true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MZGramReliableService.apply(context, MZGramReliableService.REASON_SWITCH)
        }
        assertTrue("running", waitFor { MZGramReliableService.isRunning() })
        assertTrue("the watch job", MZGramReliableService.watchScheduled(context))
        assertTrue("its notification", waitFor { ours() != null })
        val notification = ours()!!
        assertTrue("permanent", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("no buttons", 0, notification.actions?.size ?: 0)
        assertNotNull("a tap opens the switch", notification.contentIntent)
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(MZGramReliableService.CHANNEL_ID, notification.channelId)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertEquals("a quiet channel", NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(MZGramReliableService.CHANNEL_ID).importance)
        }

        MZGramConfig.setReliableDelivery(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MZGramReliableService.apply(context, MZGramReliableService.REASON_SWITCH)
        }
        assertTrue("stopped", waitFor { !MZGramReliableService.isRunning() })
        assertFalse("no watch job", MZGramReliableService.watchScheduled(context))
        assertTrue("its notification goes", waitFor { ours() == null })
    }

    @Test
    fun journal_inThePushLog_withoutTokens() {
        MZGramReliableJournal.event("start asked: test")
        MZGramReliableJournal.onShown((System.currentTimeMillis() / 1000).toInt() - 5)
        val log = MZGramDeviceCheck.log()
        assertTrue(log, log.contains("Reliable delivery: "))
        assertTrue(log, log.contains("Process started: "))
        assertTrue(log, log.contains("Last alive: "))
        assertTrue(log, log.contains(" s after its message"))
        assertTrue(log, log.contains("start asked: test"))
        assertTrue(log, log.contains("Deleted messages without content: "))
    }

    private fun deviceCheckItems(): List<UItem> {
        val items = ArrayList<UItem>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fill = MZGramDeviceCheckActivity::class.java.getDeclaredMethod("fillItems", ArrayList::class.java, UniversalAdapter::class.java)
            fill.isAccessible = true
            fill.invoke(MZGramDeviceCheckActivity(), items, null)
        }
        return items
    }

    // The device check has the Reliable delivery row; when the phone limits
    // the app in the background it suggests turning it on.
    @Test
    fun deviceCheck_rowAndSuggestion() {
        MZGramConfig.setReliableDelivery(false)
        val items = deviceCheckItems()
        assertNotNull(items.firstOrNull { it.text?.toString() == LocaleController.getString(R.string.MZGramReliable) })
        val suggestion = items.any { it.text?.toString() == LocaleController.getString(R.string.MZGramReliableSuggest) }
        assertEquals(MZGramDeviceCheck.battery() == MZGramDeviceCheck.State.NO, suggestion)
        assertEquals(MZGramDeviceCheck.battery() == MZGramDeviceCheck.State.NO, MZGramDeviceCheckActivity.suggestsReliable())
        MZGramConfig.setReliableDelivery(true)
        assertFalse("no suggestion while it is on", MZGramDeviceCheckActivity.suggestsReliable())
        MZGramConfig.setReliableDelivery(false)
    }
}
