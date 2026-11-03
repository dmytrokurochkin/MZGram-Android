/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Notifications and Sounds > UnifiedPush > Device check: what on this phone
 * can keep a push or a call from reaching MZGram. Everything here only
 * reads: the app never changes these settings, never asks for an exception
 * and never works around the system. It shows the state and the system
 * screen where the user can change it, and makes a push log to share that
 * has no push token and no keys.
 */

package org.telegram.messenger.mzgram;

import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.XiaomiUtilities;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MZGramDeviceCheck {

    public enum State {
        YES, NO, UNKNOWN
    }

    // MIUI's own permissions, as Telegram's XiaomiUtilities names them
    public static final int OP_AUTO_START = XiaomiUtilities.OP_AUTO_START;
    public static final int OP_BACKGROUND_START_ACTIVITY = XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY;
    public static final int OP_SHOW_WHEN_LOCKED = XiaomiUtilities.OP_SHOW_WHEN_LOCKED;

    public static final String MIUI_PERMISSIONS = "miui.intent.action.APP_PERM_EDITOR";
    public static final String MIUI_SECURITY_CENTER = "com.miui.securitycenter";
    // Telegram's incoming calls channel; the number grows each time
    // Telegram has to make the channel again.
    public static final String CALLS_CHANNEL = "incoming_calls4";
    public static final String HIDDEN = "[hidden]";

    private static final Pattern URL = Pattern.compile("https?://[^\\s\"']+");

    // null: the phone decides. Tests set it to see the MIUI rows.
    public static Boolean miuiForTest;

    private MZGramDeviceCheck() {
    }

    public static boolean isMiui() {
        if (miuiForTest != null) {
            return miuiForTest;
        }
        return XiaomiUtilities.isMIUI();
    }

    // "V125" and the like, or null on other phones
    public static String miuiVersion() {
        String version = AndroidUtilities.getSystemProperty("ro.miui.ui.version.name");
        return TextUtils.isEmpty(version) ? null : version;
    }

    // What AppOpsManager answered for one of MIUI's permissions. Allowed is
    // MODE_ALLOWED or MODE_FOREGROUND; refused is MODE_IGNORED or
    // MODE_ERRORED; anything else (the default, MIUI's "ask") is not known.
    public static State opState(int mode) {
        if (mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND) {
            return State.YES;
        }
        if (mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED) {
            return State.NO;
        }
        return State.UNKNOWN;
    }

    // Read the way Telegram reads it; when the phone does not answer, the
    // state is not known (Telegram takes it as allowed). Other phones do not
    // have these operations and are not asked.
    public static State miuiPermission(int op) {
        if (!XiaomiUtilities.isMIUI()) {
            return State.UNKNOWN;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            Method check = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            Object mode = check.invoke(manager, op, android.os.Process.myUid(), context.getPackageName());
            return mode instanceof Integer ? opState((Integer) mode) : State.UNKNOWN;
        } catch (Throwable e) {
            return State.UNKNOWN;
        }
    }

    // The importance of the incoming calls channel: high or urgent rings and
    // pops up, lower does not, none means no channel yet (it is made at the
    // first call) or the importance is not given.
    public static State callsImportance(Integer importance) {
        if (importance == null || importance == NotificationManager.IMPORTANCE_UNSPECIFIED) {
            return State.UNKNOWN;
        }
        return importance >= NotificationManager.IMPORTANCE_HIGH ? State.YES : State.NO;
    }

    public static String callsChannelId() {
        return CALLS_CHANNEL + MessagesController.getGlobalNotificationsSettings().getInt("calls_notification_channel", 0);
    }

    public static Integer callsChannelImportance() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        try {
            NotificationManager manager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = manager.getNotificationChannel(callsChannelId());
            return channel == null ? null : channel.getImportance();
        } catch (Throwable e) {
            return null;
        }
    }

    public static State calls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return notifications();
        }
        return callsImportance(callsChannelImportance());
    }

    public static State notifications() {
        try {
            return NotificationManagerCompat.from(ApplicationLoader.applicationContext).areNotificationsEnabled() ? State.YES : State.NO;
        } catch (Throwable e) {
            return State.UNKNOWN;
        }
    }

    public static State battery() {
        try {
            Context context = ApplicationLoader.applicationContext;
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return power.isIgnoringBatteryOptimizations(context.getPackageName()) ? State.YES : State.NO;
        } catch (Throwable e) {
            return State.UNKNOWN;
        }
    }

    // The screens a row opens. The user changes the setting there.
    public static Intent miuiPermissionsIntent(String packageName, int uid) {
        Intent intent = new Intent(MIUI_PERMISSIONS);
        intent.setPackage(MIUI_SECURITY_CENTER);
        intent.putExtra("extra_package_uid", uid);
        intent.putExtra("extra_pkgname", packageName);
        return intent;
    }

    public static Intent notificationsIntent(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        return intent;
    }

    public static Intent channelIntent(String packageName, String channelId) {
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
        return intent;
    }

    // The list of apps and their battery use; MZGram never asks the system
    // directly to stop optimizing it.
    public static Intent batteryIntent() {
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }

    public static Intent appDetailsIntent(String packageName) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
    }

    // The push token and its keys, as they could appear in the log
    public static List<String> secrets() {
        List<String> secrets = new ArrayList<>();
        String token = SharedConfig.pushString;
        if (TextUtils.isEmpty(token)) {
            return secrets;
        }
        secrets.add(token);
        try {
            JSONObject json = new JSONObject(token);
            secrets.add(json.optString("endpoint"));
            JSONObject keys = json.optJSONObject("keys");
            if (keys != null) {
                secrets.add(keys.optString("p256dh"));
                secrets.add(keys.optString("auth"));
            }
        } catch (Exception ignore) {
        }
        return secrets;
    }

    // Each secret becomes [hidden], and an address keeps only its host, so
    // nothing in a shared log can send a push to this phone.
    public static String scrub(String text, List<String> secrets) {
        if (text == null) {
            return "";
        }
        for (String secret : secrets) {
            if (secret != null && secret.length() >= 6) {
                text = text.replace(secret, HIDDEN);
            }
        }
        Matcher matcher = URL.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            Uri uri = Uri.parse(matcher.group());
            String host = uri.getScheme() + "://" + uri.getHost() + "/";
            String rest = matcher.group().length() > host.length() ? "…" : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(host + rest));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String text(State state) {
        return state == State.YES ? "yes" : state == State.NO ? "no" : "unknown";
    }

    // The log to share, in English: the phone, the checks, the counters and
    // the recent events. The endpoint shows only whether there is one.
    public static String log() {
        StringBuilder log = new StringBuilder();
        log.append("MZGram ").append(BuildVars.BUILD_VERSION_STRING).append(" push log\n");
        log.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        String miui = miuiVersion();
        log.append("MIUI: ").append(miui != null ? miui : "no").append("\n\n");
        log.append("Notifications allowed: ").append(text(notifications())).append('\n');
        log.append("Calls pop up on screen: ").append(text(calls())).append('\n');
        log.append("No battery limits: ").append(text(battery())).append('\n');
        if (isMiui()) {
            log.append("MIUI autostart: ").append(text(miuiPermission(OP_AUTO_START))).append('\n');
            log.append("MIUI pop-up windows in the background: ").append(text(miuiPermission(OP_BACKGROUND_START_ACTIVITY))).append('\n');
            log.append("MIUI show on lock screen: ").append(text(miuiPermission(OP_SHOW_WHEN_LOCKED))).append('\n');
        }
        log.append('\n');
        log.append("Pushes received: ").append(MZGramPushDiagnostics.received())
                .append(", decrypted ").append(MZGramPushDiagnostics.decrypted())
                .append(", wake-ups ").append(MZGramPushDiagnostics.wakeUps())
                .append(", not decrypted ").append(MZGramPushDiagnostics.decryptFailed()).append('\n');
        log.append("Messages from Google Play Services: ").append(MZGramPushDiagnostics.gmsReceived()).append('\n');
        log.append("Notifications shown: ").append(MZGramPushDiagnostics.shown()).append('\n');
        String playServices = MZGramPushDiagnostics.playServices();
        log.append("Google Play Services: ").append(playServices != null ? playServices : "not installed").append('\n');
        log.append("FCM: ").append(String.valueOf(MZGramPushDiagnostics.fcmResult())).append('\n');
        log.append("Telegram: ").append(String.valueOf(MZGramPushDiagnostics.telegramResult())).append('\n');
        boolean endpoint = SharedConfig.pushType == PushListenerController.PUSH_TYPE_WEB && !TextUtils.isEmpty(SharedConfig.pushString);
        log.append("Endpoint: ").append(endpoint ? "present" : "none").append('\n');
        String distributor = MZGramUnifiedPush.distributor();
        log.append("Distributor: ").append(distributor != null ? distributor : "none").append('\n');
        String failure = MZGramPushDiagnostics.lastFailureInEnglish();
        if (failure != null) {
            log.append("Last failure: ").append(failure).append('\n');
        }
        List<String> events = MZGramPushDiagnostics.events();
        if (!events.isEmpty()) {
            log.append("\nEvents, oldest first:\n");
            for (String event : events) {
                log.append(event).append('\n');
            }
        }
        return scrub(log.toString(), secrets());
    }
}
