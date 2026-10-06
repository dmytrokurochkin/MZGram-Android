/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Notification diagnostics > Test push: the app encrypts a message for its
 * own keys the way Telegram does and sends it to the address Telegram
 * sends to (through the gateway), then waits for it to come back. That
 * tells the way gateway > distributor > app apart from Telegram itself.
 * Only the gateway is contacted, never a distributor's server directly.
 */

package org.telegram.messenger.mzgram;

import android.net.Uri;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class MZGramPushTest {

    public static final int WAIT_SECONDS = 10;

    public enum Outcome {
        ARRIVED, LOST, REFUSED, FAILED, NO_ENDPOINT
    }

    public interface Callback {
        // On the UI thread. detail: seconds for ARRIVED, the HTTP code for
        // LOST and REFUSED, the error for FAILED.
        void onResult(Outcome outcome, String detail);
    }

    private static final Object lock = new Object();
    private static String pending;
    private static long sentAt;
    private static Callback callback;

    private MZGramPushTest() {
    }

    public static void send(Callback result) {
        Utilities.globalQueue.postRunnable(() -> {
            String target = MZGramUnifiedPush.gatewayEndpoint();
            if (target == null) {
                MZGramPushDiagnostics.log("test push: no endpoint through the gateway");
                AndroidUtilities.runOnUIThread(() -> result.onResult(Outcome.NO_ENDPOINT, null));
                return;
            }
            byte[] nonce = new byte[8];
            new SecureRandom().nextBytes(nonce);
            String marker = "mzgram-test-" + Utilities.bytesToHex(nonce);
            synchronized (lock) {
                pending = marker;
                callback = result;
                sentAt = SystemClock.elapsedRealtime();
            }
            int code;
            try {
                code = post(target, marker);
            } catch (Exception e) {
                FileLog.e(e);
                finish(Outcome.FAILED, e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
                return;
            }
            MZGramPushDiagnostics.log("test push: sent to " + Uri.parse(target).getHost() + ", HTTP " + code);
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

    // The decrypted data of a push: true when it is the test push, which
    // then goes no further.
    public static boolean onPayload(String payload) {
        synchronized (lock) {
            if (pending == null || !pending.equals(payload)) {
                return false;
            }
        }
        long millis = SystemClock.elapsedRealtime() - sentAt;
        finish(Outcome.ARRIVED, String.format(java.util.Locale.US, "%.1f", millis / 1000f));
        return true;
    }

    private static void finish(Outcome outcome, String detail) {
        Callback result;
        synchronized (lock) {
            result = callback;
            pending = null;
            callback = null;
        }
        MZGramPushDiagnostics.log("test push: " + outcome.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + (detail != null ? " (" + detail + ")" : ""));
        if (result != null) {
            AndroidUtilities.runOnUIThread(() -> result.onResult(outcome, detail));
        }
    }

    // As Telegram sends it: the "aesgcm" body with its two headers, which
    // the gateway moves into the body for the distributor.
    private static int post(String target, String marker) throws Exception {
        MZGramWebPushCrypto.Keys keys = MZGramWebPushCrypto.keys();
        byte[] plaintext = new JSONObject().put("p", marker).toString().getBytes(StandardCharsets.UTF_8);
        byte[][] encrypted = MZGramWebPushCrypto.encrypt(plaintext, keys.publicKey, keys.authSecret);
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        try {
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Encoding", "aesgcm");
            connection.setRequestProperty("Encryption", "salt=" + MZGramUnifiedPushRules.base64Url(encrypted[0]));
            connection.setRequestProperty("Crypto-Key", "dh=" + MZGramUnifiedPushRules.base64Url(encrypted[1]));
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
}
