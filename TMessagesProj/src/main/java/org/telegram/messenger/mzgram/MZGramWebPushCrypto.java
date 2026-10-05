/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The device's WebPush keys and the decryption of what Telegram sends to
 * them. Telegram encrypts with the "aesgcm" scheme (draft 4 of WebPush
 * encryption), which puts the salt and its public key in HTTP headers; the
 * gateway moves those headers into the body:
 *
 *   aesgcm\n
 *   Encryption: salt=<base64url>\n
 *   Crypto-Key: dh=<base64url>\n
 *   <ciphertext>
 *
 * The keys never leave the device, so the gateway cannot read anything.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MZGramWebPushCrypto {

    private static final String PREFERENCES_NAME = "mzgram_push";
    private static final String KEY_PRIVATE = "webPushPrivateKey";
    private static final String KEY_PUBLIC = "webPushPublicKey";
    private static final String KEY_AUTH = "webPushAuthSecret";

    // DER header of a P-256 public key; followed by the 65-byte point.
    private static final byte[] P256_PUBLIC_KEY_HEADER = {
            0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce,
            0x3d, 0x02, 0x01, 0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d,
            0x03, 0x01, 0x07, 0x03, 0x42, 0x00
    };

    public static final class Keys {
        // PKCS#8.
        public final byte[] privateKey;
        // The uncompressed point, 65 bytes starting with 4.
        public final byte[] publicKey;
        // 16 random bytes.
        public final byte[] authSecret;

        public Keys(byte[] privateKey, byte[] publicKey, byte[] authSecret) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.authSecret = authSecret;
        }
    }

    private static Keys keys;

    private MZGramWebPushCrypto() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    // This device's keys, made once and kept.
    public static synchronized Keys keys() throws Exception {
        if (keys != null) {
            return keys;
        }
        SharedPreferences preferences = preferences();
        String privateKey = preferences.getString(KEY_PRIVATE, null);
        String publicKey = preferences.getString(KEY_PUBLIC, null);
        String authSecret = preferences.getString(KEY_AUTH, null);
        if (privateKey != null && publicKey != null && authSecret != null) {
            keys = new Keys(Base64.decode(privateKey, Base64.NO_WRAP), Base64.decode(publicKey, Base64.NO_WRAP), Base64.decode(authSecret, Base64.NO_WRAP));
            return keys;
        }
        Keys created = generateKeys();
        preferences.edit()
                .putString(KEY_PRIVATE, Base64.encodeToString(created.privateKey, Base64.NO_WRAP))
                .putString(KEY_PUBLIC, Base64.encodeToString(created.publicKey, Base64.NO_WRAP))
                .putString(KEY_AUTH, Base64.encodeToString(created.authSecret, Base64.NO_WRAP))
                .commit();
        keys = created;
        return keys;
    }

    public static Keys generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        byte[] authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);
        return new Keys(pair.getPrivate().getEncoded(), rawPublicKey((ECPublicKey) pair.getPublic()), authSecret);
    }

    public static byte[] rawPublicKey(ECPublicKey key) {
        byte[] raw = new byte[65];
        raw[0] = 0x04;
        copyCoordinate(key.getW().getAffineX().toByteArray(), raw, 1);
        copyCoordinate(key.getW().getAffineY().toByteArray(), raw, 33);
        return raw;
    }

    // A coordinate is exactly 32 bytes; BigInteger may add a sign byte or
    // drop leading zeros.
    private static void copyCoordinate(byte[] value, byte[] target, int offset) {
        if (value.length >= 32) {
            System.arraycopy(value, value.length - 32, target, offset, 32);
        } else {
            System.arraycopy(value, 0, target, offset + 32 - value.length, value.length);
        }
    }

    public static ECPublicKey publicKey(byte[] raw) throws Exception {
        byte[] encoded = new byte[P256_PUBLIC_KEY_HEADER.length + raw.length];
        System.arraycopy(P256_PUBLIC_KEY_HEADER, 0, encoded, 0, P256_PUBLIC_KEY_HEADER.length);
        System.arraycopy(raw, 0, encoded, P256_PUBLIC_KEY_HEADER.length, raw.length);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
    }

    // The plaintext of a notification the gateway passed on; throws when it
    // cannot be decrypted.
    public static byte[] decrypt(byte[] body, Keys keys) throws Exception {
        int split = -1;
        int newlines = 0;
        for (int i = 0; i < body.length; i++) {
            if (body[i] == '\n' && ++newlines == 3) {
                split = i + 1;
                break;
            }
        }
        if (split < 0 || split >= body.length) {
            throw new IllegalArgumentException("no ciphertext");
        }
        String headers = new String(body, 0, split, StandardCharsets.UTF_8);
        byte[] salt = headerValue(headers, "Encryption:", "salt=");
        byte[] serverPublicKey = headerValue(headers, "Crypto-Key:", "dh=");
        if (salt == null || serverPublicKey == null) {
            throw new IllegalArgumentException("no salt or key");
        }
        byte[] ciphertext = Arrays.copyOfRange(body, split, body.length);

        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keys.privateKey));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey(serverPublicKey), true);
        byte[] sharedSecret = agreement.generateSecret();

        byte[][] keyAndNonce = contentKeyAndNonce(sharedSecret, keys.authSecret, salt, keys.publicKey, serverPublicKey);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndNonce[0], "AES"), new GCMParameterSpec(128, keyAndNonce[1]));
        byte[] padded = cipher.doFinal(ciphertext);

        // Two bytes of padding length, then the padding, then the data.
        if (padded.length < 2) {
            throw new IllegalArgumentException("too short");
        }
        int padding = ((padded[0] & 0xff) << 8) | (padded[1] & 0xff);
        if (2 + padding > padded.length) {
            throw new IllegalArgumentException("bad padding");
        }
        return Arrays.copyOfRange(padded, 2 + padding, padded.length);
    }

    // The content key (16 bytes) and the nonce (12 bytes) of the "aesgcm"
    // scheme; the same on both sides, so tests use it to encrypt.
    public static byte[][] contentKeyAndNonce(byte[] sharedSecret, byte[] authSecret, byte[] salt, byte[] receiverPublicKey, byte[] senderPublicKey) throws Exception {
        byte[] ikm = expand(hmac(authSecret, sharedSecret), "Content-Encoding: auth\0".getBytes(StandardCharsets.UTF_8), 32);
        byte[] prk = hmac(salt, ikm);
        byte[] context = context(receiverPublicKey, senderPublicKey);
        byte[] key = expand(prk, concat("Content-Encoding: aesgcm\0".getBytes(StandardCharsets.UTF_8), context), 16);
        byte[] nonce = expand(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8), context), 12);
        return new byte[][]{key, nonce};
    }

    private static byte[] headerValue(String headers, String header, String name) {
        for (String line : headers.split("\n")) {
            line = line.trim();
            if (!line.startsWith(header)) {
                continue;
            }
            for (String part : line.substring(header.length()).split("[;,]")) {
                part = part.trim();
                if (part.startsWith(name)) {
                    return Base64.decode(part.substring(name.length()), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                }
            }
        }
        return null;
    }

    // "P-256\0", then each public key preceded by its two-byte length.
    private static byte[] context(byte[] receiverPublicKey, byte[] senderPublicKey) {
        byte[] label = "P-256\0".getBytes(StandardCharsets.UTF_8);
        byte[] context = new byte[label.length + 2 + receiverPublicKey.length + 2 + senderPublicKey.length];
        int offset = 0;
        System.arraycopy(label, 0, context, offset, label.length);
        offset += label.length;
        context[offset++] = (byte) (receiverPublicKey.length >> 8);
        context[offset++] = (byte) receiverPublicKey.length;
        System.arraycopy(receiverPublicKey, 0, context, offset, receiverPublicKey.length);
        offset += receiverPublicKey.length;
        context[offset++] = (byte) (senderPublicKey.length >> 8);
        context[offset++] = (byte) senderPublicKey.length;
        System.arraycopy(senderPublicKey, 0, context, offset, senderPublicKey.length);
        return context;
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        byte[] input = Arrays.copyOf(info, info.length + 1);
        input[info.length] = 1;
        return Arrays.copyOf(hmac(prk, input), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
