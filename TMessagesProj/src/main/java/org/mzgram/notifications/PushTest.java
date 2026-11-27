/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.notifications;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UnifiedPushService;
import org.telegram.messenger.Utilities;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.mzgram.core.utils.WebPushDecryptor;

/**
 * Push status > Test push (UnifiedPush only): the app encrypts a message for its own keys
 * the way Telegram does and sends it to the address Telegram sends to (the gateway), then
 * waits for it to come back. That tells the way gateway > distributor > app apart from
 * Telegram itself. Only the gateway the user chose is contacted.
 */
public final class PushTest {

    public static final int WAIT_SECONDS = 10;

    public enum Outcome {
        ARRIVED, LOST, REFUSED, FAILED, NO_ENDPOINT
    }

    public interface Callback {
        /** On the UI thread. detail: seconds for ARRIVED, the HTTP code for LOST and REFUSED, the error for FAILED. */
        void onResult(Outcome outcome, String detail);
    }

    private static final Object lock = new Object();
    private static String pending;
    private static long sentAt;
    private static Callback callback;

    private PushTest() {
    }

    /** The address Telegram sends pushes to: the gateway with the distributor's endpoint. */
    public static String target() {
        if (SharedConfig.pushType != PushListenerController.PUSH_TYPE_WEB || TextUtils.isEmpty(SharedConfig.pushString)) {
            return null;
        }
        try {
            final String endpoint = new JSONObject(SharedConfig.pushString).optString("endpoint");
            return TextUtils.isEmpty(endpoint) ? null : endpoint;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean available() {
        return target() != null;
    }

    public static void send(Callback result) {
        Utilities.globalQueue.postRunnable(() -> {
            final String target = target();
            final byte[][] keys = UnifiedPushService.webPushPublicKeys();
            if (target == null || keys == null) {
                PushDiagnostics.event("Test push: no endpoint");
                AndroidUtilities.runOnUIThread(() -> result.onResult(Outcome.NO_ENDPOINT, null));
                return;
            }
            final byte[] nonce = new byte[8];
            new SecureRandom().nextBytes(nonce);
            final String marker = "mzgram-test-" + Utilities.bytesToHex(nonce);
            synchronized (lock) {
                pending = marker;
                callback = result;
                sentAt = SystemClock.elapsedRealtime();
            }
            final int code;
            try {
                code = post(target, marker, keys[0], keys[1]);
            } catch (Exception e) {
                FileLog.e(e);
                finish(Outcome.FAILED, e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
                return;
            }
            PushDiagnostics.event("Test push sent to " + Uri.parse(target).getHost() + ": HTTP " + code);
            if (code < 200 || code >= 300) {
                finish(Outcome.REFUSED, String.valueOf(code));
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                synchronized (lock) {
                    if (!marker.equals(pending)) {
                        return;
                    }
                }
                finish(Outcome.LOST, String.valueOf(code));
            }, WAIT_SECONDS * 1000L);
        });
    }

    /** The decrypted data of a push: true when it is the test push, which then goes no further. */
    public static boolean onPayload(String payload) {
        synchronized (lock) {
            if (pending == null || !pending.equals(payload)) {
                return false;
            }
        }
        final long millis = SystemClock.elapsedRealtime() - sentAt;
        finish(Outcome.ARRIVED, String.format(Locale.US, "%.1f", millis / 1000f));
        return true;
    }

    private static void finish(Outcome outcome, String detail) {
        final Callback result;
        synchronized (lock) {
            result = callback;
            pending = null;
            callback = null;
        }
        PushDiagnostics.event("Test push: " + outcome.name().toLowerCase(Locale.US).replace('_', ' ') + (detail != null ? " (" + detail + ")" : ""));
        if (result != null) {
            AndroidUtilities.runOnUIThread(() -> result.onResult(outcome, detail));
        }
    }

    // As Telegram sends it: the "aesgcm" body with its two headers, which the gateway
    // moves into the body for the distributor.
    private static int post(String target, String marker, byte[] clientPublicKey, byte[] authSecret) throws Exception {
        final byte[] plaintext = new JSONObject().put("p", marker).toString().getBytes(StandardCharsets.UTF_8);
        final byte[][] encrypted = encrypt(plaintext, clientPublicKey, authSecret);
        final HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        try {
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Encoding", "aesgcm");
            connection.setRequestProperty("Encryption", "salt=" + base64Url(encrypted[0]));
            connection.setRequestProperty("Crypto-Key", "dh=" + base64Url(encrypted[1]));
            connection.setRequestProperty("TTL", "60");
            connection.setRequestProperty("Urgency", "high");
            connection.setFixedLengthStreamingMode(encrypted[2].length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(encrypted[2]);
            }
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * "aesgcm" content encoding (draft-ietf-webpush-encryption-04), the mirror of
     * WebPushDecryptor: returns the salt, the one-time public key and the ciphertext.
     */
    static byte[][] encrypt(byte[] plaintext, byte[] clientPublicKey, byte[] authSecret) throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        final KeyPair ephemeral = generator.generateKeyPair();
        final byte[] serverPublicKey = WebPushDecryptor.extractRawPublicKey((ECPublicKey) ephemeral.getPublic());

        final KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(rawToPublicKey(clientPublicKey), true);
        final byte[] sharedSecret = agreement.generateSecret();

        final byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        final byte[] ikm = expand(hmac(authSecret, sharedSecret), "Content-Encoding: auth\0".getBytes(StandardCharsets.UTF_8), 32);
        final byte[] prk = hmac(salt, ikm);
        final byte[] context = context(clientPublicKey, serverPublicKey);
        final byte[] key = expand(prk, concat("Content-Encoding: aesgcm\0".getBytes(StandardCharsets.UTF_8), context), 16);
        final byte[] nonce = expand(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8), context), 12);

        final byte[] padded = new byte[plaintext.length + 2]; // two bytes of padding length, zero
        System.arraycopy(plaintext, 0, padded, 2, plaintext.length);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return new byte[][]{salt, serverPublicKey, cipher.doFinal(padded)};
    }

    private static ECPublicKey rawToPublicKey(byte[] raw) throws Exception {
        final byte[] header = {
                0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce,
                0x3d, 0x02, 0x01, 0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d,
                0x03, 0x01, 0x07, 0x03, 0x42, 0x00
        };
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(concat(header, raw)));
    }

    private static byte[] context(byte[] clientPublicKey, byte[] serverPublicKey) {
        final byte[] label = "P-256\0".getBytes(StandardCharsets.UTF_8);
        final byte[] out = new byte[label.length + 2 + clientPublicKey.length + 2 + serverPublicKey.length];
        int off = 0;
        System.arraycopy(label, 0, out, off, label.length);
        off += label.length;
        out[off++] = (byte) (clientPublicKey.length >> 8);
        out[off++] = (byte) clientPublicKey.length;
        System.arraycopy(clientPublicKey, 0, out, off, clientPublicKey.length);
        off += clientPublicKey.length;
        out[off++] = (byte) (serverPublicKey.length >> 8);
        out[off++] = (byte) serverPublicKey.length;
        System.arraycopy(serverPublicKey, 0, out, off, serverPublicKey.length);
        return out;
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        final byte[] input = Arrays.copyOf(info, info.length + 1);
        input[info.length] = 0x01;
        return Arrays.copyOfRange(hmac(prk, input), 0, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        final byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
