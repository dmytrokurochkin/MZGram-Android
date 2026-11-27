/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.notifications;

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
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.XiaomiUtilities;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MZGram settings > General > Notifications > Device check: what on this phone can keep
 * notifications from coming. Each check says yes, no or unknown, and names the system
 * screen where the user changes it; MZGram changes nothing itself.
 */
public final class DeviceCheck {

    public enum State {
        YES, NO, UNKNOWN
    }

    public static final String HIDDEN = "[hidden]";
    private static final String CALLS_CHANNEL = "incoming_calls4";
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"']+");

    private DeviceCheck() {
    }

    public static boolean isMiui() {
        return XiaomiUtilities.isMIUI();
    }

    /** "V125" and the like, or null on other phones. */
    public static String miuiVersion() {
        final String version = AndroidUtilities.getSystemProperty("ro.miui.ui.version.name");
        return TextUtils.isEmpty(version) ? null : version;
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
            final Context context = ApplicationLoader.applicationContext;
            final PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return power.isIgnoringBatteryOptimizations(context.getPackageName()) ? State.YES : State.NO;
        } catch (Throwable e) {
            return State.UNKNOWN;
        }
    }

    public static String callsChannelId() {
        return CALLS_CHANNEL + MessagesController.getGlobalNotificationsSettings().getInt("calls_notification_channel", 0);
    }

    /** Calls ring and pop up: the calls channel is high or urgent (no channel before the first call). */
    public static State calls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return notifications();
        }
        try {
            final NotificationManager manager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            final NotificationChannel channel = manager.getNotificationChannel(callsChannelId());
            if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_UNSPECIFIED) {
                return State.UNKNOWN;
            }
            return channel.getImportance() >= NotificationManager.IMPORTANCE_HIGH ? State.YES : State.NO;
        } catch (Throwable e) {
            return State.UNKNOWN;
        }
    }

    /** One of MIUI's own permissions (XiaomiUtilities.OP_*); other phones are not asked. */
    public static State miuiPermission(int op) {
        if (!isMiui()) {
            return State.UNKNOWN;
        }
        try {
            final Context context = ApplicationLoader.applicationContext;
            final AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            final Method check = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            final Object mode = check.invoke(manager, op, android.os.Process.myUid(), context.getPackageName());
            if (!(mode instanceof Integer)) {
                return State.UNKNOWN;
            }
            final int m = (Integer) mode;
            if (m == AppOpsManager.MODE_ALLOWED || m == AppOpsManager.MODE_FOREGROUND) {
                return State.YES;
            }
            if (m == AppOpsManager.MODE_IGNORED || m == AppOpsManager.MODE_ERRORED) {
                return State.NO;
            }
            return State.UNKNOWN;
        } catch (Throwable e) {
            return State.UNKNOWN;
        }
    }

    // ---- the system screens the checks open ----

    public static Intent notificationsIntent() {
        final Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, ApplicationLoader.applicationContext.getPackageName());
        return intent;
    }

    public static Intent callsIntent() {
        final Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, ApplicationLoader.applicationContext.getPackageName());
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, callsChannelId());
        return intent;
    }

    public static Intent batteryIntent() {
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }

    public static Intent miuiPermissionsIntent() {
        final Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        intent.setPackage("com.miui.securitycenter");
        intent.putExtra("extra_package_uid", android.os.Process.myUid());
        intent.putExtra("extra_pkgname", ApplicationLoader.applicationContext.getPackageName());
        return intent;
    }

    public static Intent appDetailsIntent() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
    }

    // ---- a report safe to share ----

    /** The push token and its keys, as they could show up in a report. */
    private static List<String> secrets() {
        final List<String> secrets = new ArrayList<>();
        final String token = SharedConfig.pushString;
        if (TextUtils.isEmpty(token)) {
            return secrets;
        }
        secrets.add(token);
        try {
            final JSONObject json = new JSONObject(token);
            secrets.add(json.optString("endpoint"));
            final JSONObject keys = json.optJSONObject("keys");
            if (keys != null) {
                secrets.add(keys.optString("p256dh"));
                secrets.add(keys.optString("auth"));
            }
        } catch (Exception ignore) {
        }
        return secrets;
    }

    /** Each secret becomes [hidden] and an address keeps only its host. */
    public static String scrub(String text) {
        if (text == null) {
            return "";
        }
        for (String secret : secrets()) {
            if (secret != null && secret.length() >= 6) {
                text = text.replace(secret, HIDDEN);
            }
        }
        final Matcher matcher = URL.matcher(text);
        final StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            final Uri uri = Uri.parse(matcher.group());
            final String host = uri.getScheme() + "://" + uri.getHost() + "/";
            matcher.appendReplacement(out, Matcher.quoteReplacement(host + (matcher.group().length() > host.length() ? "…" : "")));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String text(State state) {
        return state == State.YES ? "yes" : state == State.NO ? "no" : "unknown";
    }

    /** The checks in English, for the push status report. */
    public static String report() {
        final StringBuilder t = new StringBuilder();
        final String miui = miuiVersion();
        t.append("MIUI: ").append(miui != null ? miui : "no").append('\n');
        t.append("Notifications allowed: ").append(text(notifications())).append('\n');
        t.append("Calls pop up on screen: ").append(text(calls())).append('\n');
        t.append("No battery limits: ").append(text(battery())).append('\n');
        if (isMiui()) {
            t.append("MIUI autostart: ").append(text(miuiPermission(XiaomiUtilities.OP_AUTO_START))).append('\n');
            t.append("MIUI pop-ups in the background: ").append(text(miuiPermission(XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY))).append('\n');
            t.append("MIUI show on lock screen: ").append(text(miuiPermission(XiaomiUtilities.OP_SHOW_WHEN_LOCKED))).append('\n');
        }
        return t.toString();
    }
}
