/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.notifications;

import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;

import org.mzgram.ext.ExtConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.GooglePushListenerServiceProvider;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MZGram settings > General > Notifications > Push status: what happened to push
 * notifications on this phone. Counters and a short list of recent events, kept on disk,
 * since the interesting case is a push that never woke the app up. Only times, counts and
 * short English events: no tokens, keys or texts of messages.
 */
public final class PushDiagnostics {

    private static final String PREFS = "mzgram_push_diagnostics";
    private static final String KEY_EVENTS = "events";
    private static final int MAX_EVENTS = 40;

    private static final Object lock = new Object();

    private PushDiagnostics() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void increment(String key) {
        synchronized (lock) {
            final SharedPreferences p = prefs();
            p.edit().putLong(key, p.getLong(key, 0) + 1).apply();
        }
    }

    private static long get(String key) {
        return prefs().getLong(key, 0);
    }

    /** A short English line, kept with its time; the oldest go first. */
    public static void event(String text) {
        if (ApplicationLoader.applicationContext == null || text == null) {
            return;
        }
        final String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + text.replace('\n', ' ');
        synchronized (lock) {
            final SharedPreferences p = prefs();
            final String old = p.getString(KEY_EVENTS, "");
            final ArrayList<String> lines = new ArrayList<>();
            if (!TextUtils.isEmpty(old)) {
                for (String l : old.split("\n")) {
                    lines.add(l);
                }
            }
            lines.add(line);
            while (lines.size() > MAX_EVENTS) {
                lines.remove(0);
            }
            p.edit().putString(KEY_EVENTS, TextUtils.join("\n", lines)).apply();
        }
    }

    public static List<String> events() {
        final String all = prefs().getString(KEY_EVENTS, "");
        final ArrayList<String> lines = new ArrayList<>();
        if (!TextUtils.isEmpty(all)) {
            for (String l : all.split("\n")) {
                lines.add(l);
            }
        }
        return lines;
    }

    public static void reset() {
        synchronized (lock) {
            prefs().edit().clear().apply();
        }
        event("Counters reset");
    }

    // ---- hooks ----

    /** PushListenerController.processRemoteMessage: a push came in. */
    public static void onPush(int pushType) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        increment("received");
        increment("received_" + typeName(pushType));
        prefs().edit().putLong("last_received", System.currentTimeMillis()).apply();
    }

    /** UnifiedPushService.onMessage: decrypted, or only woke the connection up. */
    public static void onWebPush(boolean decrypted, String error) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        increment(decrypted ? "web_decrypted" : "web_wakeups");
        if (!decrypted && error != null) {
            event("UnifiedPush message not decrypted: " + error);
        }
    }

    /** NotificationsController: notifications put on screen. */
    public static void onShown(int messages) {
        if (ApplicationLoader.applicationContext == null || messages <= 0) {
            return;
        }
        increment("shown");
        prefs().edit().putLong("last_shown", System.currentTimeMillis()).apply();
    }

    /** MessagesController.registerForPush: Telegram's answer to the push token. */
    public static void onRegisterAnswer(int account, int pushType, boolean ok, String error) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        prefs().edit().putString("register_" + account, typeName(pushType) + " " + (ok ? "ok" : "refused" + (error != null ? " (" + error + ")" : ""))
                + " at " + new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date())).apply();
        if (!ok) {
            event("Telegram refused the " + typeName(pushType) + " token for account " + account + (error != null ? ": " + error : ""));
        }
    }

    public static String typeName(int pushType) {
        switch (pushType) {
            case PushListenerController.PUSH_TYPE_FIREBASE:
                return "FCM";
            case PushListenerController.PUSH_TYPE_WEB:
                return "UnifiedPush";
            case PushListenerController.PUSH_TYPE_HUAWEI:
                return "HMS";
            default:
                return "type " + pushType;
        }
    }

    private static String serviceName(int type) {
        switch (type) {
            case 0:
                return "in-app service";
            case 1:
                return "Google FCM";
            case 2:
                return "UnifiedPush";
            case 3:
                return "microG";
            default:
                return String.valueOf(type);
        }
    }

    private static String time(long millis) {
        return millis <= 0 ? "never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    /** The whole report, in English, with no token and no keys. */
    public static String report() {
        final Context context = ApplicationLoader.applicationContext;
        final StringBuilder t = new StringBuilder();
        t.append("MZGram ").append(BuildVars.BUILD_VERSION_STRING).append(" push status\n");
        t.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");

        final int service = ExtConfig.INSTANCE.getPushServiceType().Int();
        t.append("Push service: ").append(serviceName(service)).append('\n');
        final boolean hasToken = !TextUtils.isEmpty(SharedConfig.pushString);
        t.append("Token: ").append(hasToken ? "present (" + typeName(SharedConfig.pushType) + ")" : "none").append('\n');
        if (!hasToken) {
            t.append("Token status: ").append(SharedConfig.pushStringStatus).append('\n');
            if (GooglePushListenerServiceProvider.lastError != null) {
                t.append("Token error: ").append(GooglePushListenerServiceProvider.lastError).append('\n');
            }
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final UserConfig config = UserConfig.getInstance(a);
            if (config.getClientUserId() != 0) {
                t.append("Registration with Telegram, account ").append(a).append(": ").append(config.registeredForPush ? "yes" : "no");
                final String answer = prefs().getString("register_" + a, null);
                if (answer != null) {
                    t.append(", last answer ").append(answer);
                }
                t.append('\n');
            }
        }
        t.append("Google Play Services: ").append(PushListenerController.getProvider().hasServices() ? gmsVersion(context) : "no").append('\n');
        t.append("In-app service: ").append(MessagesController.getNotificationsSettings(UserConfig.selectedAccount).getBoolean("pushService", false) ? "on" : "off")
                .append(", push connection ").append(ConnectionsManager.getInstance(UserConfig.selectedAccount).isPushConnectionEnabled() ? "on" : "off").append('\n');
        t.append("Standby bucket: ").append(standbyBucket(context)).append('\n');
        t.append(DeviceCheck.report()).append('\n');

        t.append("Pushes received: ").append(get("received"));
        for (String type : new String[]{"FCM", "UnifiedPush", "HMS"}) {
            final long n = get("received_" + type);
            if (n > 0) {
                t.append(", ").append(type).append(' ').append(n);
            }
        }
        t.append('\n');
        if (get("web_decrypted") + get("web_wakeups") > 0) {
            t.append("UnifiedPush: decrypted ").append(get("web_decrypted")).append(", wake-ups only ").append(get("web_wakeups")).append('\n');
        }
        t.append("Last push: ").append(time(Math.max(prefs().getLong("last_received", 0), SharedConfig.pushLastReceivedTime))).append('\n');
        t.append("Notifications shown: ").append(get("shown")).append(", last ").append(time(prefs().getLong("last_shown", 0))).append('\n');

        final List<String> events = events();
        if (!events.isEmpty()) {
            t.append("\nEvents, oldest first:\n");
            for (String e : events) {
                t.append(e).append('\n');
            }
        }
        return DeviceCheck.scrub(t.toString());
    }

    private static String gmsVersion(Context context) {
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo("com.google.android.gms", 0);
            return info.versionName != null ? info.versionName : "yes";
        } catch (Throwable e) {
            return "yes";
        }
    }

    private static String standbyBucket(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return "n/a";
        }
        try {
            final UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            final int bucket = usm.getAppStandbyBucket();
            if (bucket <= 10) return "exempted";
            if (bucket <= 20) return "active";
            if (bucket <= 30) return "working set";
            if (bucket <= 40) return "frequent";
            if (bucket <= 45) return "rare";
            return "restricted";
        } catch (Throwable e) {
            return "unknown";
        }
    }
}
