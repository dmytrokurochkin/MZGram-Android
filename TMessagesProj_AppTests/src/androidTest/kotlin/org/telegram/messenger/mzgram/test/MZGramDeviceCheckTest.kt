package org.telegram.messenger.mzgram.test

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.PushListenerController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.mzgram.MZGramDeviceCheck
import org.telegram.messenger.mzgram.MZGramDeviceCheck.State
import org.telegram.messenger.mzgram.MZGramPushDiagnostics
import org.telegram.messenger.mzgram.MZGramUnifiedPushRules
import org.telegram.messenger.mzgram.MZGramWebPushCrypto
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.mzgram.MZGramDeviceCheckActivity
import org.telegram.ui.mzgram.MZGramUnifiedPushActivity

// Notifications and Sounds > UnifiedPush > Device check: it only reads
// the phone's settings, opens the system screens and shares a push log
// without the push token.
class MZGramDeviceCheckTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        MZGramDeviceCheck.miuiForTest = null
        MZGramDeviceCheckActivity.openedForTest = null
    }

    private fun items(fragment: Any, type: Class<*>): List<UItem> {
        val items = ArrayList<UItem>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fill = type.getDeclaredMethod("fillItems", ArrayList::class.java, UniversalAdapter::class.java)
            fill.isAccessible = true
            fill.invoke(fragment, items, null)
        }
        return items
    }

    private fun deviceCheckItems() = items(MZGramDeviceCheckActivity(), MZGramDeviceCheckActivity::class.java)

    private fun row(items: List<UItem>, res: Int) = items.firstOrNull { it.text?.toString() == LocaleController.getString(res) }

    private val states get() = listOf(R.string.MZGramDeviceCheckYes, R.string.MZGramDeviceCheckNo, R.string.MZGramDeviceCheckUnknown).map { LocaleController.getString(it) }

    // MIUI answers allowed or refused; anything else is shown as not known
    // rather than taken as allowed.
    @Test
    fun miuiAnswers_allowedRefusedOrUnknown() {
        assertEquals(State.YES, MZGramDeviceCheck.opState(AppOpsManager.MODE_ALLOWED))
        assertEquals(State.YES, MZGramDeviceCheck.opState(AppOpsManager.MODE_FOREGROUND))
        assertEquals(State.NO, MZGramDeviceCheck.opState(AppOpsManager.MODE_IGNORED))
        assertEquals(State.NO, MZGramDeviceCheck.opState(AppOpsManager.MODE_ERRORED))
        assertEquals(State.UNKNOWN, MZGramDeviceCheck.opState(AppOpsManager.MODE_DEFAULT))
        assertEquals(State.UNKNOWN, MZGramDeviceCheck.opState(5))
        // Not MIUI: the phone does not know the operation.
        assertEquals(State.UNKNOWN, MZGramDeviceCheck.miuiPermission(MZGramDeviceCheck.OP_AUTO_START))
    }

    // Calls ring and pop up only with high importance; no channel yet is not known.
    @Test
    fun callsChannel_highImportanceOnly() {
        assertEquals(State.YES, MZGramDeviceCheck.callsImportance(NotificationManager.IMPORTANCE_HIGH))
        assertEquals(State.YES, MZGramDeviceCheck.callsImportance(NotificationManager.IMPORTANCE_MAX))
        assertEquals(State.NO, MZGramDeviceCheck.callsImportance(NotificationManager.IMPORTANCE_DEFAULT))
        assertEquals(State.NO, MZGramDeviceCheck.callsImportance(NotificationManager.IMPORTANCE_LOW))
        assertEquals(State.NO, MZGramDeviceCheck.callsImportance(NotificationManager.IMPORTANCE_NONE))
        assertEquals(State.UNKNOWN, MZGramDeviceCheck.callsImportance(null))
        assertEquals(State.UNKNOWN, MZGramDeviceCheck.callsImportance(NotificationManager.IMPORTANCE_UNSPECIFIED))
        val index = MessagesController.getGlobalNotificationsSettings().getInt("calls_notification_channel", 0)
        assertEquals("incoming_calls4$index", MZGramDeviceCheck.callsChannelId())
    }

    // Each row opens the system screen of its setting; nothing is changed by MZGram.
    @Test
    fun rows_openTheSystemScreens() {
        val pkg = context.packageName
        val notifications = MZGramDeviceCheckActivity.intentFor(MZGramDeviceCheckActivity.BUTTON_NOTIFICATIONS)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications.action)
        assertEquals(pkg, notifications.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        val calls = MZGramDeviceCheckActivity.intentFor(MZGramDeviceCheckActivity.BUTTON_CALLS)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, calls.action)
        assertEquals(MZGramDeviceCheck.callsChannelId(), calls.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        // The list of apps, not a direct request to stop optimizing MZGram
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, MZGramDeviceCheckActivity.intentFor(MZGramDeviceCheckActivity.BUTTON_BATTERY).action)
        for (id in listOf(MZGramDeviceCheckActivity.BUTTON_AUTOSTART, MZGramDeviceCheckActivity.BUTTON_POPUPS, MZGramDeviceCheckActivity.BUTTON_LOCK_SCREEN)) {
            val miui = MZGramDeviceCheckActivity.intentFor(id)
            assertEquals("miui.intent.action.APP_PERM_EDITOR", miui.action)
            assertEquals("com.miui.securitycenter", miui.`package`)
            assertEquals(pkg, miui.getStringExtra("extra_pkgname"))
        }
        val share = MZGramDeviceCheckActivity.intentFor(MZGramDeviceCheckActivity.BUTTON_SHARE)
        assertEquals(Intent.ACTION_CHOOSER, share.action)
        @Suppress("DEPRECATION")
        val send = share.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("text/plain", send.type)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.startsWith("MZGram "))
        val details = MZGramDeviceCheck.appDetailsIntent(pkg)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, details.action)
        assertEquals("package:$pkg", details.dataString)
    }

    // The shared log names the phone and the checks, in English, and has
    // neither the push token, its keys nor a full push address.
    @Test
    fun pushLog_hasNoTokenOrKeys() {
        val type = SharedConfig.pushType
        val saved = SharedConfig.pushString
        try {
            val secret = "dGhpc0lzQUxvbmdGY21Ub2tlbkZvclRoaXNEZXZpY2VPbmx5MTIzNDU2Nzg5"
            val keys = MZGramWebPushCrypto.keys()
            SharedConfig.pushType = PushListenerController.PUSH_TYPE_WEB
            SharedConfig.pushString = MZGramUnifiedPushRules.webPushToken(MZGramUnifiedPushRules.DEFAULT_GATEWAY + "fcm/" + secret, keys.publicKey, keys.authSecret)
            MZGramPushDiagnostics.event(R.string.MZGramPushEventDecrypted)
            val decrypted = MZGramPushDiagnostics.events().last()
            MZGramPushDiagnostics.setLastFailure("could not reach https://ntfy.example.org/upAbC123?up=1")
            val log = MZGramDeviceCheck.log()
            assertFalse(log, log.contains(secret))
            assertFalse(log, log.contains(MZGramUnifiedPushRules.base64Url(keys.authSecret)))
            assertFalse(log, log.contains(MZGramUnifiedPushRules.base64Url(keys.publicKey)))
            assertFalse(log, log.contains("upAbC123"))
            assertTrue(log, log.contains("https://ntfy.example.org/"))
            assertTrue(log, log.contains("Endpoint: present"))
            assertTrue(log, log.contains(Build.MODEL))
            assertTrue(log, log.contains("Calls pop up on screen: "))
            assertTrue(log, log.contains("No battery limits: "))
            assertTrue(log, log.contains("Notifications allowed: "))
            assertTrue(log, log.contains(decrypted))
            // A short value is never taken for a secret
            assertEquals("ok [hidden] ok", MZGramDeviceCheck.scrub("ok ${secret} ok", listOf(secret, "ok")))
        } finally {
            SharedConfig.pushType = type
            SharedConfig.pushString = saved
            MZGramPushDiagnostics.setLastFailure(null)
        }
    }

    // Three rows with their state on every phone, the MIUI rows only on MIUI.
    @Test
    fun screen_rowsWithTheirStates() {
        MZGramDeviceCheck.miuiForTest = false
        var items = deviceCheckItems()
        for (res in listOf(R.string.MZGramDeviceCheckNotifications, R.string.MZGramDeviceCheckCalls, R.string.MZGramDeviceCheckBattery)) {
            val item = row(items, res)
            assertNotNull("missing: " + LocaleController.getString(res), item)
            assertTrue(item!!.textValue?.toString(), item.textValue?.toString() in states)
        }
        assertEquals(MZGramDeviceCheckActivity.stateText(MZGramDeviceCheck.notifications()), row(items, R.string.MZGramDeviceCheckNotifications)!!.textValue.toString())
        assertNotNull(row(items, R.string.MZGramDeviceCheckShare))
        assertTrue(items.any { it.text?.toString() == LocaleController.getString(R.string.MZGramDeviceCheckInfo) })
        assertTrue(items.any { it.text?.toString() == LocaleController.getString(R.string.MZGramDeviceCheckShareInfo) })
        for (res in listOf(R.string.MZGramDeviceCheckAutostart, R.string.MZGramDeviceCheckPopups, R.string.MZGramDeviceCheckLockScreen)) {
            assertEquals(null, row(items, res))
        }
        MZGramDeviceCheck.miuiForTest = true
        items = deviceCheckItems()
        for (res in listOf(R.string.MZGramDeviceCheckAutostart, R.string.MZGramDeviceCheckPopups, R.string.MZGramDeviceCheckLockScreen)) {
            val item = row(items, res)
            assertNotNull("missing: " + LocaleController.getString(res), item)
            assertEquals(LocaleController.getString(R.string.MZGramDeviceCheckUnknown), item!!.textValue?.toString())
        }
        assertTrue(items.any { it.text?.toString() == LocaleController.getString(R.string.MZGramDeviceCheckMiuiInfo) })
    }

    // Opening the screen reads only: the calls channel number and the
    // MZGram notification settings stay as they were.
    @Test
    fun screen_changesNothing() {
        val settings = MessagesController.getGlobalNotificationsSettings()
        val before = settings.all.toString()
        val importance = MZGramDeviceCheck.callsChannelImportance()
        MZGramDeviceCheck.miuiForTest = true
        deviceCheckItems()
        MZGramDeviceCheck.log()
        assertEquals(before, settings.all.toString())
        assertEquals(importance, MZGramDeviceCheck.callsChannelImportance())
    }

    // The UnifiedPush page has the Device check row next to the diagnostics.
    @Test
    fun unifiedPushPage_hasDeviceCheck() {
        val items = items(MZGramUnifiedPushActivity(), MZGramUnifiedPushActivity::class.java)
        val texts = items.map { it.text?.toString() }
        val diagnostics = texts.indexOf(LocaleController.getString(R.string.MZGramPushDiagnostics))
        val check = texts.indexOf(LocaleController.getString(R.string.MZGramDeviceCheck))
        assertTrue("device check row", check >= 0)
        assertEquals(diagnostics + 1, check)
    }
}
