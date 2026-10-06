/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * What happened to push notifications on this device, for Notifications
 * and Sounds > UnifiedPush > Notification diagnostics: counters, the last
 * answer of Google Play Services (or microG) to the built-in Google FCM
 * registration, and a short list of recent events. Kept on disk, since the
 * interesting case is a push that never woke the app up.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MZGramPushDiagnostics {

    private static final String PREFERENCES_NAME = "mzgram_push_stats";
    private static final String KEY_LAST_RECEIVED = "lastReceived";
    private static final String KEY_RECEIVED = "received";
    private static final String KEY_DECRYPTED = "decrypted";
    private static final String KEY_WAKE_UPS = "wakeUps";
    private static final String KEY_DECRYPT_FAILED = "decryptFailed";
    private static final String KEY_LAST_FAILURE = "lastFailure";
    private static final String KEY_FCM_RESULT = "fcmResult";
    private static final String KEY_FCM_RESULT_TIME = "fcmResultTime";
    private static final String KEY_FCM_REQUEST_TIME = "fcmRequestTime";
    private static final String KEY_EVENTS = "events";

    public static final int EVENTS_KEPT = 20;

    public enum Kind {
        PUSH, WAKE_UP, DECRYPT_FAILED
    }

    private MZGramPushDiagnostics() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void onReceived(Kind kind) {
        SharedPreferences preferences = preferences();
        String counter = kind == Kind.PUSH ? KEY_DECRYPTED : kind == Kind.WAKE_UP ? KEY_WAKE_UPS : KEY_DECRYPT_FAILED;
        preferences.edit()
                .putLong(KEY_LAST_RECEIVED, System.currentTimeMillis())
                .putLong(KEY_RECEIVED, preferences.getLong(KEY_RECEIVED, 0) + 1)
                .putLong(counter, preferences.getLong(counter, 0) + 1)
                .apply();
        log(kind == Kind.PUSH ? "push" : kind == Kind.WAKE_UP ? "wake-up" : "push (decrypt failed, woke up instead)");
    }

    public static synchronized void log(String event) {
        List<String> events = events();
        events.add(new SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(new Date()) + " " + event);
        while (events.size() > EVENTS_KEPT) {
            events.remove(0);
        }
        preferences().edit().putString(KEY_EVENTS, TextUtils.join("\n", events)).apply();
    }

    // Oldest first.
    public static synchronized List<String> events() {
        String saved = preferences().getString(KEY_EVENTS, "");
        return saved.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(saved.split("\n")));
    }

    public static void setLastFailure(String reason) {
        preferences().edit().putString(KEY_LAST_FAILURE, reason).apply();
        if (reason != null) {
            log("registration failed: " + reason);
        }
    }

    public static String lastFailure() {
        return preferences().getString(KEY_LAST_FAILURE, null);
    }

    // The built-in Google FCM distributor asked Google Play Services (or
    // microG) for an endpoint.
    public static void onFcmRequested() {
        preferences().edit().putLong(KEY_FCM_REQUEST_TIME, System.currentTimeMillis()).apply();
        log("FCM: token requested from " + MZGramUnifiedPushRules.PLAY_SERVICES_PACKAGE);
    }

    // What Google Play Services (or microG) answered: a token, or the error
    // it gave ("SERVICE_NOT_AVAILABLE", "AUTHENTICATION_FAILED" and the like).
    public static void onFcmAnswer(boolean registered, String error) {
        String result = registered ? "registered" : "error: " + (TextUtils.isEmpty(error) ? "no token and no error given" : error);
        preferences().edit().putString(KEY_FCM_RESULT, result).putLong(KEY_FCM_RESULT_TIME, System.currentTimeMillis()).apply();
        log("FCM: " + result);
    }

    public static String fcmResult() {
        return preferences().getString(KEY_FCM_RESULT, null);
    }

    public static long fcmResultTime() {
        return preferences().getLong(KEY_FCM_RESULT_TIME, 0);
    }

    public static long fcmRequestTime() {
        return preferences().getLong(KEY_FCM_REQUEST_TIME, 0);
    }

    public static long lastReceived() {
        return preferences().getLong(KEY_LAST_RECEIVED, 0);
    }

    public static long received() {
        return preferences().getLong(KEY_RECEIVED, 0);
    }

    public static long decrypted() {
        return preferences().getLong(KEY_DECRYPTED, 0);
    }

    public static long wakeUps() {
        return preferences().getLong(KEY_WAKE_UPS, 0);
    }

    public static long decryptFailed() {
        return preferences().getLong(KEY_DECRYPT_FAILED, 0);
    }

    public static synchronized void reset() {
        preferences().edit().clear().commit();
    }

    // "com.google.android.gms 24.08.12 (microG)", or null when neither Google
    // Play Services nor microG is installed.
    public static String playServices() {
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(MZGramUnifiedPushRules.PLAY_SERVICES_PACKAGE, 0);
            CharSequence label = ApplicationLoader.applicationContext.getPackageManager().getApplicationLabel(info.applicationInfo);
            boolean microG = (info.versionName != null && info.versionName.toLowerCase(Locale.ROOT).contains("microg"))
                    || (label != null && label.toString().toLowerCase(Locale.ROOT).contains("microg"));
            return MZGramUnifiedPushRules.PLAY_SERVICES_PACKAGE + " " + info.versionName + (microG ? " (microG)" : "");
        } catch (Exception e) {
            return null;
        }
    }
}
