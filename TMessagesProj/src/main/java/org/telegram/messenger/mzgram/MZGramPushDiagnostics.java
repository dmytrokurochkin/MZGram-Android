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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;

import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

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
    private static final String KEY_GMS_RECEIVED = "gmsReceived";
    private static final String KEY_TELEGRAM_RESULT = "telegramResult";
    private static final String KEY_TELEGRAM_RESULT_TIME = "telegramResultTime";
    private static final String KEY_TELEGRAM_REQUEST_TIME = "telegramRequestTime";
    private static final String KEY_SHOWN = "shown";

    public static final int EVENTS_KEPT = 40;
    // What Telegram and Google Play Services answered, as kept (fcmResult, telegramResult)
    public static final String RESULT_OK = "ok";
    public static final String RESULT_REGISTERED = "registered";
    public static final String RESULT_NO_ANSWER = "error: no answer";
    public static final String RESULT_NO_TOKEN = "error: no token and no error given";

    // An event is kept as the name of its string resource (ids change between
    // builds) and its arguments, so the list is shown in the app's language.
    // An argument can itself be a string resource (resource()) or the label
    // of a distributor app (distributor()).
    private static final char EVENT = '\u0001';
    private static final char ARG = '\u0002';
    private static final char RESOURCE = '\u0003';
    private static final char LABEL = '\u0004';

    public enum Kind {
        PUSH, WAKE_UP, DECRYPT_FAILED
    }

    private MZGramPushDiagnostics() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    // What the distributor handed over: decrypted, a wake-up without
    // content, or content that could not be decrypted (with the reason).
    public static synchronized void onReceived(Kind kind, int size, String reason) {
        SharedPreferences preferences = preferences();
        String counter = kind == Kind.PUSH ? KEY_DECRYPTED : kind == Kind.WAKE_UP ? KEY_WAKE_UPS : KEY_DECRYPT_FAILED;
        preferences.edit()
                .putLong(KEY_LAST_RECEIVED, System.currentTimeMillis())
                .putLong(KEY_RECEIVED, preferences.getLong(KEY_RECEIVED, 0) + 1)
                .putLong(counter, preferences.getLong(counter, 0) + 1)
                .commit();
        event(R.string.MZGramPushEventReceived, size);
        if (kind == Kind.PUSH) {
            event(R.string.MZGramPushEventDecrypted);
        } else if (kind == Kind.WAKE_UP) {
            event(R.string.MZGramPushEventWakeUp);
        } else {
            event(R.string.MZGramPushEventNotDecrypted, reason);
        }
    }

    // Google Play Services or microG delivered a message to this app,
    // before the distributor library looks at it.
    public static synchronized void onGmsMessage(int size, String subtype) {
        SharedPreferences preferences = preferences();
        preferences.edit().putLong(KEY_GMS_RECEIVED, preferences.getLong(KEY_GMS_RECEIVED, 0) + 1).commit();
        event(subtype != null && subtype.startsWith("wp:") ? R.string.MZGramPushEventGms : R.string.MZGramPushEventGmsOther, MZGramUnifiedPushRules.PLAY_SERVICES_PACKAGE, size);
    }

    public static long gmsReceived() {
        return preferences().getLong(KEY_GMS_RECEIVED, 0);
    }

    // account.registerDevice for a push token, and what Telegram answered.
    public static void onTelegramRegisterSent(int account, int tokenType) {
        preferences().edit().putLong(KEY_TELEGRAM_REQUEST_TIME, System.currentTimeMillis()).commit();
        event(R.string.MZGramPushEventRegisterSent, tokenType, account);
    }

    public static void onTelegramRegisterAnswer(int account, int tokenType, boolean ok, TLRPC.TL_error error) {
        String result = ok ? RESULT_OK : error != null ? "error " + error.code + " " + error.text : RESULT_NO_ANSWER;
        if (tokenType == org.telegram.messenger.PushListenerController.PUSH_TYPE_WEB) {
            preferences().edit().putString(KEY_TELEGRAM_RESULT, result).putLong(KEY_TELEGRAM_RESULT_TIME, System.currentTimeMillis()).commit();
        }
        if (ok) {
            event(R.string.MZGramPushEventRegisterOk, tokenType, account);
        } else if (error != null) {
            event(R.string.MZGramPushEventRegisterError, tokenType, error.code + " " + error.text, account);
        } else {
            event(R.string.MZGramPushEventRegisterNoAnswer, tokenType, account);
        }
    }

    public static String telegramResult() {
        return preferences().getString(KEY_TELEGRAM_RESULT, null);
    }

    public static long telegramResultTime() {
        return preferences().getLong(KEY_TELEGRAM_RESULT_TIME, 0);
    }

    public static long telegramRequestTime() {
        return preferences().getLong(KEY_TELEGRAM_REQUEST_TIME, 0);
    }

    // What the decrypted Telegram push turned into: an event resource and its arguments.
    public static void onTelegramPush(int res, Object... args) {
        event(res, args);
    }

    public static synchronized void onNotificationShown(int messages) {
        SharedPreferences preferences = preferences();
        preferences.edit().putLong(KEY_SHOWN, preferences.getLong(KEY_SHOWN, 0) + 1).commit();
        event(R.string.MZGramPushEventShown, messages);
    }

    public static long shown() {
        return preferences().getLong(KEY_SHOWN, 0);
    }

    public static boolean notificationsAllowed() {
        try {
            return NotificationManagerCompat.from(ApplicationLoader.applicationContext).areNotificationsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    // A string resource as an argument of an event, shown in the same language
    public static String resource(int res) {
        return RESOURCE + ApplicationLoader.applicationContext.getResources().getResourceEntryName(res);
    }

    // The name of a distributor app as an argument of an event
    public static String distributor(String packageName) {
        return LABEL + String.valueOf(packageName);
    }

    private static String encode(int res, Object... args) {
        StringBuilder text = new StringBuilder().append(EVENT).append(ApplicationLoader.applicationContext.getResources().getResourceEntryName(res));
        for (Object arg : args) {
            text.append(ARG).append(String.valueOf(arg).replace('\n', ' '));
        }
        return text.toString();
    }

    // Written at once: the interesting events happen in a process that was
    // started for a push and may be gone right after.
    public static synchronized void event(int res, Object... args) {
        add(encode(res, args));
    }

    private static synchronized void add(String event) {
        List<String> events = saved();
        events.add(new SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(new Date()) + " " + event);
        while (events.size() > EVENTS_KEPT) {
            events.remove(0);
        }
        preferences().edit().putString(KEY_EVENTS, TextUtils.join("\n", events)).commit();
    }

    private static List<String> saved() {
        String saved = preferences().getString(KEY_EVENTS, "");
        return saved.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(saved.split("\n")));
    }

    private static Resources english;

    private static Resources english() {
        if (english == null) {
            Context context = ApplicationLoader.applicationContext;
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(Locale.ENGLISH);
            english = context.createConfigurationContext(configuration).getResources();
        }
        return english;
    }

    private static String string(String name, boolean inEnglish, Object... args) {
        Context context = ApplicationLoader.applicationContext;
        int res = context.getResources().getIdentifier(name, "string", context.getPackageName());
        if (res == 0) {
            return name;
        }
        return inEnglish ? english().getString(res, args) : LocaleController.formatString(name, res, args);
    }

    // An event as text: in English or in the app's language. Lines kept by
    // earlier versions are plain text and stay as they are.
    static String render(String line, boolean inEnglish) {
        int start = line.indexOf(EVENT);
        if (start < 0) {
            return line;
        }
        String[] parts = line.substring(start + 1).split(String.valueOf(ARG), -1);
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            String arg = parts[i];
            if (!arg.isEmpty() && arg.charAt(0) == RESOURCE) {
                args[i - 1] = string(arg.substring(1), inEnglish);
            } else if (!arg.isEmpty() && arg.charAt(0) == LABEL) {
                String label = MZGramUnifiedPush.label(arg.substring(1));
                args[i - 1] = label != null ? label : arg.substring(1);
            } else {
                args[i - 1] = arg;
            }
        }
        return line.substring(0, start) + string(parts[0], inEnglish, args);
    }

    private static List<String> rendered(boolean inEnglish) {
        List<String> events = saved();
        for (int i = 0; i < events.size(); i++) {
            events.set(i, render(events.get(i), inEnglish));
        }
        return events;
    }

    // Oldest first, in English (logs and tests).
    public static synchronized List<String> events() {
        return rendered(true);
    }

    // Oldest first, in the app's language (the diagnostics screen).
    public static synchronized List<String> localizedEvents() {
        return rendered(false);
    }

    // Why the registration failed, as the distributor or the system gave it
    public static void setLastFailure(String reason) {
        preferences().edit().putString(KEY_LAST_FAILURE, reason).apply();
        if (reason != null) {
            event(R.string.MZGramPushEventFailed, reason);
        }
    }

    // The WebPush keys could not be made
    public static void setKeysFailure(String error) {
        preferences().edit().putString(KEY_LAST_FAILURE, encode(R.string.MZGramPushFailureKeys, error)).apply();
        event(R.string.MZGramPushEventKeysFailed, error);
    }

    // In the app's language
    public static String lastFailure() {
        String failure = preferences().getString(KEY_LAST_FAILURE, null);
        return failure == null ? null : render(failure, false);
    }

    // The built-in Google FCM distributor asked Google Play Services (or
    // microG) for an endpoint.
    public static void onFcmRequested() {
        preferences().edit().putLong(KEY_FCM_REQUEST_TIME, System.currentTimeMillis()).apply();
        event(R.string.MZGramPushEventFcmRequested, MZGramUnifiedPushRules.PLAY_SERVICES_PACKAGE);
    }

    // What Google Play Services (or microG) answered: a token, or the error
    // it gave ("SERVICE_NOT_AVAILABLE", "AUTHENTICATION_FAILED" and the like).
    public static void onFcmAnswer(boolean registered, String error) {
        String result = registered ? RESULT_REGISTERED : TextUtils.isEmpty(error) ? RESULT_NO_TOKEN : "error: " + error;
        preferences().edit().putString(KEY_FCM_RESULT, result).putLong(KEY_FCM_RESULT_TIME, System.currentTimeMillis()).apply();
        if (registered) {
            event(R.string.MZGramPushEventFcmRegistered);
        } else if (TextUtils.isEmpty(error)) {
            event(R.string.MZGramPushEventFcmNoToken);
        } else {
            event(R.string.MZGramPushEventFcmError, error);
        }
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
