package org.telegram.messenger.mzgram.test

import android.content.Context
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
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

    // Without a choice UnifiedPush runs only where Google services are
    // missing; the user's choice wins both ways.
    @Test
    fun unifiedPush_isUsedWithoutGoogleOrWhenTheUserTurnsItOn() {
        assertFalse(MZGramUnifiedPushRules.usesUnifiedPush(null, true))
        assertTrue(MZGramUnifiedPushRules.usesUnifiedPush(null, false))
        assertTrue(MZGramUnifiedPushRules.usesUnifiedPush(true, true))
        assertFalse(MZGramUnifiedPushRules.usesUnifiedPush(false, false))
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
        for (id in listOf(R.string.MZGramUseUnifiedPush, R.string.MZGramUnifiedPushGatewayEnabled, R.string.MZGramUnifiedPushGateway, R.string.MZGramUnifiedPushGatewayReset, R.string.MZGramUnifiedPushGatewayInfo)) {
            val text = LocaleController.getString(id)
            assertTrue("missing: $text", text in texts)
        }
        val address = items.first { it.text?.toString() == LocaleController.getString(R.string.MZGramUnifiedPushGateway) }
        assertEquals(MZGramUnifiedPushRules.gateway(true, MZGramConfig.unifiedPushGateway), address.textValue?.toString())
        assertEquals(MZGramUnifiedPush.isActive(), items.first { it.text?.toString() == LocaleController.getString(R.string.MZGramUseUnifiedPush) }.checked)
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
