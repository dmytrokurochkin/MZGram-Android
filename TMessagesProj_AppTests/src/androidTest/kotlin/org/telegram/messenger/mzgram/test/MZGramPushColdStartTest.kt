package org.telegram.messenger.mzgram.test

import android.content.Context
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.telegram.messenger.MessageKeyData
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramPushDiagnostics
import org.telegram.messenger.mzgram.MZGramUnifiedPush
import org.telegram.messenger.mzgram.MZGramUnifiedPushRules
import org.telegram.messenger.mzgram.MZGramWebPushCrypto
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.mzgram.MZGramUnifiedPushActivity
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// A real push through Google FCM to an app that is not running, on the CI
// emulator (Google Play Services). Run by .github/scripts/mzgram_push_cold.sh
// in two steps around a push the CI host sends, never with the other tests:
//
//   prepare -- the built-in Google FCM distributor registers with a test
//              VAPID key the host holds (the host stands in for the
//              gateway, which takes pushes only from Telegram's servers),
//              and the push Telegram would send for a new message is
//              written out: Telegram's own encryption with this device's
//              push key, inside the WebPush encryption for this device's
//              keys, folded the way the gateway folds it.
//   (host)  -- the app's process is gone; the host sends that body to the
//              FCM endpoint with the test key's VAPID signature and looks
//              for the notification.
//   verify  -- what the app wrote down while it was started for the push.
//
// Skipped unless the script passes mzColdStart.
class MZGramPushColdStartTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val outDir get() = File(context.getExternalFilesDir(null), "mzgram-push").also { it.mkdirs() }

    private val selfId = 7_000_000_001L

    private fun base64Url(data: ByteArray) = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    @Test
    fun prepare() {
        val vapid = arguments.getString("mzVapidPublic")
        assumeTrue("run by mzgram_push_cold.sh", arguments.getString("mzColdStart") != null && vapid != null)
        assumeTrue("Google Play Services on this device", MZGramUnifiedPush.hasPlayServices())

        // A signed-in account for the push to be for, kept on disk for the
        // process the push starts.
        val self = TLRPC.TL_user().also {
            it.id = selfId
            it.first_name = "MZGram test self"
        }
        instrumentation.runOnMainSync {
            UserConfig.getInstance(0).setCurrentUser(self)
            UserConfig.getInstance(0).saveConfig(true)
        }

        // The host's key before the first registration, then the app as the
        // user opens it: push registration starts with the first screen.
        assertTrue("test VAPID key saved", MZGramConfig.setUnifiedPushVapidKey(vapid))
        val preferences = context.getSharedPreferences("mzgram_push", Context.MODE_PRIVATE)
        val prefix = MZGramUnifiedPushRules.fcmEndpointPrefix(MZGramConfig.unifiedPushGateway)
        MZGramScreens.launchApp()
        assertTrue("UnifiedPush is on", MZGramUnifiedPush.isActive())
        val registered = MZGramScreens.waitFor(120) { preferences.getString("endpoint", null)?.startsWith(prefix) == true }
        MZGramScreens.log("cold start: diagnostics after registering\n" + MZGramUnifiedPushActivity.diagnosticsText())
        assumeTrue("Play Services gave an endpoint for the test key: ${MZGramPushDiagnostics.fcmResult()}", registered)
        val token = preferences.getString("endpoint", null)!!.removePrefix(prefix)

        // Telegram's part: the notification encrypted with this device's
        // push key (MTProto 2.0, as PushListenerController decrypts it).
        if (SharedConfig.pushAuthKey == null) {
            SharedConfig.pushAuthKey = ByteArray(256).also { SecureRandom().nextBytes(it) }
            SharedConfig.pushAuthKeyId = null
            SharedConfig.saveConfig()
        }
        val notification = JSONObject()
            .put("loc_key", "MESSAGE_TEXT")
            .put("loc_args", JSONArray().put("MZGram test peer").put("Cold start push"))
            .put("custom", JSONObject().put("msg_id", "77").put("from_id", "7000000002"))
        val p = base64Url(telegramEncrypt(notification.toString().toByteArray(), SharedConfig.pushAuthKey))

        // The WebPush part, for this device's keys, folded by "the gateway".
        val body = webPushFolded(JSONObject().put("p", p).toString().toByteArray(), MZGramWebPushCrypto.keys())

        // The test account has no key on Telegram's servers, so the app
        // signs it out while the first screen is open; it is written to disk
        // again last and at once (saveConfig waits for an idle moment and
        // writes later), for the process the push starts.
        val data = SerializedData()
        self.serializeToStream(data)
        val written = UserConfig.getInstance(0).preferences.edit()
            .putString("user", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT))
            .putInt("selectedAccount", 0)
            .commit()
        data.cleanup()
        assertTrue("test account written", written)

        MZGramPushDiagnostics.reset()
        File(outDir, "token.txt").writeText(token)
        File(outDir, "body.b64").writeText(Base64.encodeToString(body, Base64.NO_WRAP))
        MZGramScreens.shell("mkdir -p /data/local/tmp/mzgram-push")
        MZGramScreens.shell("cp ${outDir.absolutePath}/token.txt ${outDir.absolutePath}/body.b64 /data/local/tmp/mzgram-push/")
        MZGramScreens.log("cold start: prepared, endpoint ${MZGramUnifiedPushRules.maskUrl(prefix + token)}, ${body.size} bytes")
    }

    @Test
    fun verify() {
        assumeTrue("run by mzgram_push_cold.sh", arguments.getString("mzColdStart") != null)
        val notified = arguments.getString("mzNotified") == "1"
        val text = MZGramUnifiedPushActivity.diagnosticsText()
        MZGramScreens.log("cold start: diagnostics after the push\n$text")
        val events = MZGramPushDiagnostics.events()
        assertTrue("Google Play Services delivered the message: $events", MZGramPushDiagnostics.gmsReceived() > 0)
        assertTrue("the push reached the app and was decrypted: $events", MZGramPushDiagnostics.decrypted() > 0)
        val telegram = events.lastOrNull { it.contains("Telegram push:") }
        assertTrue("Telegram's part was read: $events", telegram != null)
        // The test account has no key on Telegram's servers; when the app
        // signed it out before the push came, there is nobody to show the
        // notification to, which says nothing about the push itself.
        assumeFalse("the test account was signed out, so no notification can be shown: $telegram", telegram!!.contains("is not signed in"))
        assertTrue("Telegram's part was a new message: $telegram", telegram.contains("MESSAGE_TEXT"))
        assertTrue("the app showed a notification: $events", MZGramPushDiagnostics.shown() > 0)
        assertTrue("the notification is on the screen (dumpsys notification)", notified)
    }

    // MTProto 2.0 from the server's side: the auth key id, the message key,
    // then the length, the data and padding, AES-IGE encrypted.
    private fun telegramEncrypt(data: ByteArray, authKey: ByteArray): ByteArray {
        var length = 4 + data.size
        val padding = 16 - length % 16 + 16
        length += padding
        val plain = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        plain.putInt(data.size)
        plain.put(data)
        plain.put(ByteArray(padding).also { SecureRandom().nextBytes(it) })
        val plaintext = plain.array()
        val messageKeyFull = Utilities.computeSHA256(authKey.copyOfRange(88 + 8, 88 + 8 + 32) + plaintext)
        val messageKey = messageKeyFull.copyOfRange(8, 24)
        val keyData = MessageKeyData.generateMessageKeyData(authKey, messageKey, true, 2)
        val encrypted = plaintext.copyOf()
        Utilities.aesIgeEncryptionByteArray(encrypted, keyData.aesKey, keyData.aesIv, true, false, 0, encrypted.size)
        val authKeyHash = Utilities.computeSHA1(authKey)
        val authKeyId = authKeyHash.copyOfRange(authKeyHash.size - 8, authKeyHash.size)
        return authKeyId + messageKey + encrypted
    }

    // What Telegram sends ("aesgcm", a fresh key pair and salt) with the
    // two headers moved into the body, as the gateway does.
    private fun webPushFolded(plaintext: ByteArray, receiver: MZGramWebPushCrypto.Keys): ByteArray {
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
        val ciphertext = cipher.doFinal(byteArrayOf(0, 0) + plaintext)
        return "aesgcm\nEncryption: salt=${base64Url(salt)}\nCrypto-Key: dh=${base64Url(senderPublicKey)}\n".toByteArray() + ciphertext
    }
}
