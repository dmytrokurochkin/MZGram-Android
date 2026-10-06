package org.telegram.messenger.mzgram.test

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramFcmRegistrationReceiver
import org.telegram.messenger.mzgram.MZGramPushDiagnostics
import org.telegram.messenger.mzgram.MZGramUnifiedPush
import org.telegram.messenger.mzgram.MZGramUnifiedPushRules
import org.telegram.messenger.mzgram.MZGramWebPushCrypto
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.mzgram.MZGramUnifiedPushActivity
import java.io.File
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MZGramUnifiedPushTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val endpoint = "https://ntfy.example.org/upAbC123?up=1"

    // Telegram's own Google push cannot work in MZGram builds, so UnifiedPush
    // is on until the user turns it off.
    @Test
    fun unifiedPush_isOnUnlessTheUserTurnsItOff() {
        assertTrue(MZGramUnifiedPushRules.usesUnifiedPush(null))
        assertTrue(MZGramUnifiedPushRules.usesUnifiedPush(true))
        assertFalse(MZGramUnifiedPushRules.usesUnifiedPush(false))
    }

    // The built-in Google FCM is offered only with Google Play Services or
    // microG; a distributor app is preferred to it, and a saved choice stays.
    @Test
    fun distributor_appFirstThenBuiltInFcm_savedChoiceKept() {
        val own = "org.telegram.messenger.web"
        val ntfy = "io.heckel.ntfy"
        val sunup = "org.unifiedpush.distributor.sunup"
        assertEquals(listOf(own, ntfy), MZGramUnifiedPushRules.offeredDistributors(listOf(own, ntfy, ntfy), own, true))
        assertEquals(listOf(ntfy), MZGramUnifiedPushRules.offeredDistributors(listOf(own, ntfy), own, false))
        assertEquals(emptyList<String>(), MZGramUnifiedPushRules.offeredDistributors(listOf(own), own, false))

        assertEquals(own, MZGramUnifiedPushRules.pickDistributor(null, listOf(own), own))
        assertEquals(ntfy, MZGramUnifiedPushRules.pickDistributor(null, listOf(own, ntfy), own))
        assertEquals(own, MZGramUnifiedPushRules.pickDistributor(own, listOf(own, ntfy), own))
        assertEquals(sunup, MZGramUnifiedPushRules.pickDistributor(sunup, listOf(own, ntfy, sunup), own))
        // A saved distributor that was uninstalled is replaced.
        assertEquals(ntfy, MZGramUnifiedPushRules.pickDistributor(sunup, listOf(own, ntfy), own))
        assertNull(MZGramUnifiedPushRules.pickDistributor(null, emptyList(), own))
    }

    // The built-in distributor asks for an endpoint with the gateway's key
    // and gives Telegram the gateway's /fcm/ route, as it is.
    @Test
    fun builtInFcm_usesTheGatewaysKeyAndFcmRoute() {
        val savedGateway = MZGramConfig.unifiedPushGateway
        val savedKey = MZGramConfig.unifiedPushVapidKey
        try {
            MZGramConfig.setUnifiedPushGateway("")
            MZGramConfig.setUnifiedPushVapidKey("")
            assertEquals(MZGramUnifiedPushRules.DEFAULT_VAPID_KEY, MZGramUnifiedPush.fcmVapidKey())
            assertEquals(MZGramUnifiedPushRules.DEFAULT_GATEWAY + "fcm/tok123", MZGramUnifiedPush.fcmEndpoint("tok123"))

            val ownKey = MZGramUnifiedPushRules.base64Url(MZGramWebPushCrypto.generateKeys().publicKey)
            assertTrue(MZGramConfig.setUnifiedPushGateway("https://own.example.org/gw"))
            assertTrue(MZGramConfig.setUnifiedPushVapidKey(ownKey))
            assertEquals(ownKey, MZGramUnifiedPush.fcmVapidKey())
            assertEquals("https://own.example.org/gw/fcm/tok123", MZGramUnifiedPush.fcmEndpoint("tok123"))
            // Needs the gateway even with the gateway switch off.
            assertEquals("https://own.example.org/gw/fcm/", MZGramUnifiedPushRules.fcmEndpointPrefix(MZGramConfig.unifiedPushGateway))

            val fcmEndpoint = "https://own.example.org/gw/fcm/tok123"
            assertEquals(fcmEndpoint, MZGramUnifiedPushRules.webPushEndpoint(fcmEndpoint, "https://own.example.org/gw/", true))
            assertEquals(fcmEndpoint, MZGramUnifiedPushRules.simplePushToken(fcmEndpoint, "https://own.example.org/gw/", true))
            assertTrue(MZGramUnifiedPushRules.webPushEndpoint(endpoint, "https://own.example.org/gw/", false).contains("aesgcm?e="))
        } finally {
            MZGramConfig.setUnifiedPushGateway(savedGateway)
            MZGramConfig.setUnifiedPushVapidKey(savedKey)
        }
    }

    @Test
    fun vapidKey_onlyAnUncompressedP256KeyIsSaved() {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/mzgram_config.xml")
        val saved = MZGramConfig.unifiedPushVapidKey
        val ownKey = MZGramUnifiedPushRules.base64Url(MZGramWebPushCrypto.generateKeys().publicKey)
        try {
            assertTrue(MZGramUnifiedPushRules.isValidVapidKey(MZGramUnifiedPushRules.DEFAULT_VAPID_KEY))
            assertTrue(MZGramUnifiedPushRules.isValidVapidKey(ownKey))
            for (bad in listOf(null, "", "abc", "$ownKey=", ownKey.dropLast(1), "A" + ownKey.drop(1), ownKey.replace(ownKey[5], '+'))) {
                assertFalse("must be refused: $bad", MZGramUnifiedPushRules.isValidVapidKey(bad))
            }
            assertTrue(MZGramConfig.setUnifiedPushVapidKey(ownKey))
            assertTrue(file.readText().contains(ownKey))
            MZGramConfig.unifiedPushVapidKey = "lost"
            MZGramConfig.loadConfig(true)
            assertEquals(ownKey, MZGramConfig.unifiedPushVapidKey)
            assertFalse(MZGramConfig.setUnifiedPushVapidKey("not a key"))
            assertEquals(ownKey, MZGramConfig.unifiedPushVapidKey)
            // Reset: the default key is stored as "use the default".
            assertTrue(MZGramConfig.setUnifiedPushVapidKey(MZGramUnifiedPushRules.DEFAULT_VAPID_KEY))
            assertEquals("", MZGramConfig.unifiedPushVapidKey)
            assertEquals(MZGramUnifiedPushRules.DEFAULT_VAPID_KEY, MZGramUnifiedPushRules.vapidKey(MZGramConfig.unifiedPushVapidKey))
        } finally {
            MZGramConfig.setUnifiedPushVapidKey(saved)
        }
    }

    // The diagnostics show where notifications go without the parts that
    // identify this device.
    @Test
    fun diagnostics_maskTheTokenAndTheKeys() {
        val token = "dGhpc0lzQUxvbmdGY21Ub2tlbkZvclRoaXNEZXZpY2VPbmx5MTIzNDU2Nzg5"
        val keys = MZGramWebPushCrypto.keys()
        val json = MZGramUnifiedPushRules.webPushToken(MZGramUnifiedPushRules.DEFAULT_GATEWAY + "fcm/" + token, keys.publicKey, keys.authSecret)
        val masked = MZGramUnifiedPushRules.maskPushToken(json)
        assertTrue(masked, masked.contains(MZGramUnifiedPushRules.DEFAULT_GATEWAY + "fcm/"))
        assertFalse(masked, masked.contains(token))
        assertFalse(masked, masked.contains(MZGramUnifiedPushRules.base64Url(keys.authSecret)))
        assertFalse(masked, masked.contains(MZGramUnifiedPushRules.base64Url(keys.publicKey)))
        val viaGateway = MZGramUnifiedPushRules.maskUrl(MZGramUnifiedPushRules.webPushEndpoint(endpoint, MZGramUnifiedPushRules.DEFAULT_GATEWAY))
        assertTrue(viaGateway, viaGateway.startsWith(MZGramUnifiedPushRules.DEFAULT_GATEWAY + "aesgcm?e="))
        assertFalse(viaGateway, viaGateway.contains("upAbC123"))
    }

    // Counters, the last answer of Google Play Services or microG and the
    // recent events are kept on disk and can be reset.
    @Test
    fun diagnostics_countAndKeepEvents() {
        val preferences = context.getSharedPreferences("mzgram_push_stats", Context.MODE_PRIVATE)
        val saved = preferences.all
        try {
            MZGramPushDiagnostics.reset()
            assertEquals(0, MZGramPushDiagnostics.received())
            assertEquals(0, MZGramPushDiagnostics.lastReceived())
            MZGramPushDiagnostics.onReceived(MZGramPushDiagnostics.Kind.PUSH)
            MZGramPushDiagnostics.onReceived(MZGramPushDiagnostics.Kind.PUSH)
            MZGramPushDiagnostics.onReceived(MZGramPushDiagnostics.Kind.WAKE_UP)
            MZGramPushDiagnostics.onReceived(MZGramPushDiagnostics.Kind.DECRYPT_FAILED)
            assertEquals(4, MZGramPushDiagnostics.received())
            assertEquals(2, MZGramPushDiagnostics.decrypted())
            assertEquals(1, MZGramPushDiagnostics.wakeUps())
            assertEquals(1, MZGramPushDiagnostics.decryptFailed())
            assertTrue(MZGramPushDiagnostics.lastReceived() > 0)
            assertTrue(MZGramPushDiagnostics.events().last(), MZGramPushDiagnostics.events().last().endsWith("woke up instead)"))

            // What Google Play Services or microG answered, as it reached
            // the app.
            MZGramFcmRegistrationReceiver().onReceive(context, Intent("com.google.android.c2dm.intent.REGISTRATION").putExtra("error", "SERVICE_NOT_AVAILABLE"))
            assertEquals("error: SERVICE_NOT_AVAILABLE", MZGramPushDiagnostics.fcmResult())
            MZGramFcmRegistrationReceiver().onReceive(context, Intent("com.google.android.c2dm.intent.REGISTRATION").putExtra("registration_id", "1:abc:token"))
            assertEquals("registered", MZGramPushDiagnostics.fcmResult())

            for (i in 1..30) {
                MZGramPushDiagnostics.log("event $i")
            }
            val events = MZGramPushDiagnostics.events()
            assertEquals(MZGramPushDiagnostics.EVENTS_KEPT, events.size)
            assertTrue(events.last(), events.last().endsWith("event 30"))
            assertTrue(events.first(), events.first().endsWith("event 11"))

            val text = MZGramUnifiedPushActivity.diagnosticsText()
            assertTrue(text, text.contains("event 30"))
            assertTrue(text, text.contains("4"))

            MZGramPushDiagnostics.reset()
            assertEquals(0, MZGramPushDiagnostics.received())
            assertTrue(MZGramPushDiagnostics.events().isEmpty())
        } finally {
            val editor = preferences.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.commit()
        }
    }

    // On the CI emulator (Google Play Services, no distributor app) the
    // built-in distributor really asks Play Services for an endpoint when
    // the app starts. Whether Play Services answers depends on the
    // emulator's Google sign-in, so a missing answer skips the check; the
    // diagnostics go to the log either way.
    @Test
    fun builtInFcm_registersWithPlayServices() {
        assumeTrue("Google Play Services on this device", MZGramUnifiedPush.hasPlayServices())
        assertTrue("UnifiedPush is on", MZGramUnifiedPush.isActive())
        val answered = MZGramScreens.waitFor(90) { MZGramPushDiagnostics.fcmResult() != null || MZGramUnifiedPush.status() == MZGramUnifiedPush.Status.REGISTERED }
        MZGramScreens.log("UnifiedPush diagnostics on the emulator:\n" + MZGramUnifiedPushActivity.diagnosticsText())
        assertTrue("the built-in distributor asked Play Services", MZGramPushDiagnostics.fcmRequestTime() > 0 || MZGramPushDiagnostics.fcmResult() != null)
        assumeTrue("Play Services answered: ${MZGramPushDiagnostics.fcmResult()}", answered && MZGramPushDiagnostics.fcmResult() == "registered")
        val registered = MZGramScreens.waitFor(60) { MZGramUnifiedPush.status() == MZGramUnifiedPush.Status.REGISTERED }
        assertTrue("the endpoint came back", registered)
        val saved = context.getSharedPreferences("mzgram_push", Context.MODE_PRIVATE).getString("endpoint", "")!!
        assertTrue(saved, saved.startsWith(MZGramUnifiedPushRules.fcmEndpointPrefix(MZGramConfig.unifiedPushGateway)))
    }

    // A phone with Google Play Services and no distributor app installed
    // (the CI emulator is one): Telegram's own Google push cannot work in
    // MZGram builds, so UnifiedPush is on and takes the built-in Google FCM
    // distributor by itself.
    @Test
    fun phoneWithPlayServicesAndNoDistributorApp_getsTheBuiltInDistributor() {
        val playServices = try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (e: Exception) {
            false
        }
        assumeTrue("Google Play Services on this device", playServices)
        assertTrue("UnifiedPush is on", MZGramUnifiedPush.isActive())
        assertTrue("the built-in distributor is offered: ${MZGramUnifiedPush.distributors()}", context.packageName in MZGramUnifiedPush.distributors())
        assertTrue("the built-in distributor is chosen: ${MZGramUnifiedPush.distributor()}", MZGramScreens.waitFor(30) { MZGramUnifiedPush.distributor() == context.packageName })
    }

    @Test
    fun gatewayAddress_onlyHttpsAndNoCredentials() {
        assertEquals("https://push.example.org/", MZGramUnifiedPushRules.normalizeGateway("https://push.example.org"))
        assertEquals("https://push.example.org/", MZGramUnifiedPushRules.normalizeGateway("  https://push.example.org/  "))
        assertEquals("https://push.example.org:8443/gw/", MZGramUnifiedPushRules.normalizeGateway("HTTPS://push.example.org:8443/gw"))
        for (bad in listOf(
            null, "", "   ", "push.example.org", "http://push.example.org/", "ftp://push.example.org/",
            "https://user:secret@push.example.org/", "https://user@push.example.org/", "https://",
            "https:///path", "https://push.example.org/?token=1", "https://push.example.org/#x",
            "https://push example.org/", "javascript:alert(1)",
        )) {
            assertNull("must be refused: $bad", MZGramUnifiedPushRules.normalizeGateway(bad))
        }
    }

    // The default gateway, the user's own one, or none (direct delivery).
    @Test
    fun gateway_isTheDefaultTheUsersOwnOrNone() {
        assertEquals(MZGramUnifiedPushRules.DEFAULT_GATEWAY, MZGramUnifiedPushRules.gateway(true, ""))
        assertEquals(MZGramUnifiedPushRules.DEFAULT_GATEWAY, MZGramUnifiedPushRules.gateway(true, null))
        assertEquals("https://own.example.org/gw/", MZGramUnifiedPushRules.gateway(true, "https://own.example.org/gw"))
        // An unusable stored address never sends notifications anywhere odd.
        assertEquals(MZGramUnifiedPushRules.DEFAULT_GATEWAY, MZGramUnifiedPushRules.gateway(true, "http://own.example.org/"))
        assertNull(MZGramUnifiedPushRules.gateway(false, "https://own.example.org/"))
        assertNull(MZGramUnifiedPushRules.gateway(false, ""))
    }

    @Test
    fun tokens_carryTheEndpointThroughTheGatewayAndTheDeviceKeys() {
        val keys = MZGramWebPushCrypto.keys()
        assertEquals(65, keys.publicKey.size)
        assertEquals(4, keys.publicKey[0].toInt())
        assertEquals(16, keys.authSecret.size)

        val gateway = MZGramUnifiedPushRules.gateway(true, "https://own.example.org/gw")
        val viaGateway = MZGramUnifiedPushRules.webPushEndpoint(endpoint, gateway)
        assertTrue(viaGateway, viaGateway.startsWith("https://own.example.org/gw/aesgcm?e="))
        assertEquals(endpoint, URLDecoder.decode(viaGateway.substringAfter("?e="), "UTF-8"))
        assertEquals(endpoint, MZGramUnifiedPushRules.webPushEndpoint(endpoint, null))
        val simple = MZGramUnifiedPushRules.simplePushToken(endpoint, gateway)
        assertEquals(endpoint, URLDecoder.decode(simple.removePrefix("https://own.example.org/gw/"), "UTF-8"))
        assertEquals(endpoint, MZGramUnifiedPushRules.simplePushToken(endpoint, null))

        val token = JSONObject(MZGramUnifiedPushRules.webPushToken(viaGateway, keys.publicKey, keys.authSecret))
        assertEquals(viaGateway, token.getString("endpoint"))
        val p256dh = token.getJSONObject("keys").getString("p256dh")
        val auth = token.getJSONObject("keys").getString("auth")
        assertFalse("base64url without padding", p256dh.contains('=') || p256dh.contains('+') || p256dh.contains('/'))
        assertArrayEquals(keys.publicKey, Base64.decode(p256dh, Base64.URL_SAFE))
        assertArrayEquals(keys.authSecret, Base64.decode(auth, Base64.URL_SAFE))
        // The keys stay the same from one start to the next.
        assertArrayEquals(keys.publicKey, MZGramWebPushCrypto.keys().publicKey)
    }

    // What Telegram encrypts for this device's keys, with the headers moved
    // into the body by the gateway, comes out as the notification's data.
    @Test
    fun notificationThroughTheGateway_isDecrypted() {
        val data = "AAECAwQFBgcICQ"
        val body = encrypt("{\"p\":\"$data\"}".toByteArray(), MZGramWebPushCrypto.keys())
        assertEquals(data, MZGramUnifiedPush.decode(body))
    }

    // Anything that cannot be decrypted gives no data, so the app is woken up
    // and fetches new messages itself.
    @Test
    fun undecryptableNotification_fallsBackToWakeUp() {
        val keys = MZGramWebPushCrypto.keys()
        val good = encrypt("{\"p\":\"x\"}".toByteArray(), keys)
        // Sent straight to the distributor: the headers are lost.
        val direct = good.copyOfRange(good.indexOfThird('\n'.code.toByte()) + 1, good.size)
        assertNull(MZGramUnifiedPush.decode(direct))
        // A Simple Push wake-up.
        assertNull(MZGramUnifiedPush.decode("version=12".toByteArray()))
        // Damaged on the way.
        val damaged = good.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(MZGramUnifiedPush.decode(damaged))
        // Encrypted for another device.
        assertNull(MZGramUnifiedPush.decode(encrypt("{\"p\":\"x\"}".toByteArray(), MZGramWebPushCrypto.generateKeys())))
        // Headers without a ciphertext.
        assertNull(MZGramUnifiedPush.decode("aesgcm\nEncryption: salt=AA\nCrypto-Key: dh=AA\n".toByteArray()))
        // No exception escapes: the receiver goes on to the wake-up.
        MZGramUnifiedPush.onMessage(direct)
    }

    // The gateway settings are written to disk at once and read back on the
    // next start; an unusable address is not saved.
    @Test
    fun gatewaySettings_surviveARestart() {
        val preferences = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)
        val savedGateway = MZGramConfig.unifiedPushGateway
        val savedEnabled = MZGramConfig.unifiedPushGatewayEnabled
        val file = File(context.applicationInfo.dataDir, "shared_prefs/mzgram_config.xml")
        try {
            assertTrue(MZGramConfig.setUnifiedPushGateway("https://own.example.org/gw"))
            assertTrue(file.readText().contains("https://own.example.org/gw/"))
            MZGramConfig.unifiedPushGateway = "lost"
            MZGramConfig.loadConfig(true)
            assertEquals("https://own.example.org/gw/", MZGramConfig.unifiedPushGateway)

            assertFalse(MZGramConfig.setUnifiedPushGateway("http://own.example.org/"))
            assertFalse(MZGramConfig.setUnifiedPushGateway("https://me:pw@own.example.org/"))
            MZGramConfig.loadConfig(true)
            assertEquals("https://own.example.org/gw/", MZGramConfig.unifiedPushGateway)

            if (MZGramConfig.unifiedPushGatewayEnabled) {
                MZGramConfig.toggleUnifiedPushGatewayEnabled()
            }
            assertTrue(file.readText().contains("name=\"unifiedPushGatewayEnabled\" value=\"false\""))
            MZGramConfig.loadConfig(true)
            assertFalse(MZGramConfig.unifiedPushGatewayEnabled)

            // Reset to the default.
            assertTrue(MZGramConfig.setUnifiedPushGateway(""))
            MZGramConfig.loadConfig(true)
            assertEquals("", MZGramConfig.unifiedPushGateway)
            assertEquals(MZGramUnifiedPushRules.DEFAULT_GATEWAY, MZGramUnifiedPushRules.gateway(true, MZGramConfig.unifiedPushGateway))
        } finally {
            preferences.edit().putString("unifiedPushGateway", savedGateway).putBoolean("unifiedPushGatewayEnabled", savedEnabled).commit()
            MZGramConfig.loadConfig(true)
        }
    }

    // Notifications and Sounds > UnifiedPush: the switch, the gateway switch,
    // its address, the reset button and what the gateway can see.
    @Test
    fun settingsPage_hasTheGatewayAddressResetAndPrivacyNote() {
        val items = ArrayList<UItem>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fillItems = MZGramUnifiedPushActivity::class.java.getDeclaredMethod("fillItems", ArrayList::class.java, UniversalAdapter::class.java)
            fillItems.isAccessible = true
            fillItems.invoke(MZGramUnifiedPushActivity(), items, null)
        }
        val texts = items.mapNotNull { it.text?.toString() }
        for (id in listOf(R.string.MZGramUseUnifiedPush, R.string.MZGramUnifiedPushGatewayEnabled, R.string.MZGramUnifiedPushGateway, R.string.MZGramUnifiedPushGatewayReset)) {
            val text = LocaleController.getString(id)
            assertTrue("missing: $text", text in texts)
        }
        // The note under the address starts with what the address is.
        val note = LocaleController.getString(R.string.MZGramUnifiedPushGatewayPrefix) + " " + LocaleController.getString(R.string.MZGramUnifiedPushGatewayInfo)
        assertTrue("missing: $note", note in texts)
        val address = items.first { it.text?.toString() == LocaleController.getString(R.string.MZGramUnifiedPushGateway) }
        assertEquals(MZGramUnifiedPushRules.gateway(true, MZGramConfig.unifiedPushGateway), address.textValue?.toString())
        assertEquals(MZGramUnifiedPush.isActive(), items.first { it.text?.toString() == LocaleController.getString(R.string.MZGramUseUnifiedPush) }.checked)
        assertTrue("diagnostics row", LocaleController.getString(R.string.MZGramPushDiagnostics) in texts)
        if (MZGramUnifiedPush.isActive()) {
            // One choice per offered distributor, the built-in one named.
            val radios = items.filter { it.`object` is String }
            assertEquals(MZGramUnifiedPush.distributors(), radios.map { it.`object` })
            if (MZGramUnifiedPush.hasPlayServices()) {
                assertTrue(LocaleController.getString(R.string.MZGramEmbeddedFcm) in radios.map { it.text?.toString() })
            }
            val current = MZGramUnifiedPush.ackedDistributor() ?: MZGramUnifiedPush.distributor()
            assertEquals("VAPID key row only for the built-in distributor", MZGramUnifiedPush.isBuiltIn(current),
                LocaleController.getString(R.string.MZGramEmbeddedFcmVapid) in texts)
        }
        assertNotNull(LocaleController.getString(R.string.MZGramUnifiedPush))
    }

    private fun ByteArray.indexOfThird(value: Byte): Int {
        var count = 0
        for (i in indices) {
            if (this[i] == value && ++count == 3) {
                return i
            }
        }
        return -1
    }

    private fun base64Url(data: ByteArray) = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    // Telegram's side: a fresh key pair and salt per notification, the
    // "aesgcm" scheme, two bytes of padding length.
    private fun encrypt(plaintext: ByteArray, receiver: MZGramWebPushCrypto.Keys): ByteArray {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val sender = generator.generateKeyPair()
        val senderPublicKey = MZGramWebPushCrypto.rawPublicKey(sender.public as ECPublicKey)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(sender.private)
        agreement.doPhase(MZGramWebPushCrypto.publicKey(receiver.publicKey), true)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val keyAndNonce = MZGramWebPushCrypto.contentKeyAndNonce(agreement.generateSecret(), receiver.authSecret, salt, receiver.publicKey, senderPublicKey)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyAndNonce[0], "AES"), GCMParameterSpec(128, keyAndNonce[1]))
        val ciphertext = cipher.doFinal(byteArrayOf(0, 3, 0, 0, 0) + plaintext)
        val headers = "aesgcm\nEncryption: salt=${base64Url(salt)}\nCrypto-Key: dh=${base64Url(senderPublicKey)}\n"
        return headers.toByteArray() + ciphertext
    }
}
