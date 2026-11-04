/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Reliable delivery's journal, for the push log of the device check: when
 * the process started, when it was last seen alive, what started and
 * stopped the service and what refused it, and how late the last
 * notification came after its message. No tokens, keys or texts of
 * messages: only times and short English events.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MZGramReliableJournal {

    public static final int EVENTS_KEPT = 40;
    private static final String PREFERENCES = "mzgram_reliable";
    // When this process started (class load is at the latest the first use)
    private static final long PROCESS_START = System.currentTimeMillis();

    private MZGramReliableJournal() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String time(long ms) {
        return ms <= 0 ? "never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(ms));
    }

    public static synchronized void event(String text) {
        try {
            SharedPreferences preferences = preferences();
            String all = preferences.getString("events", "");
            String line = time(System.currentTimeMillis()) + " " + text.replace('\n', ' ');
            String[] lines = all.isEmpty() ? new String[0] : all.split("\n");
            StringBuilder result = new StringBuilder();
            for (int i = Math.max(0, lines.length - (EVENTS_KEPT - 1)); i < lines.length; i++) {
                result.append(lines[i]).append('\n');
            }
            result.append(line);
            preferences.edit()
                    .putString("events", result.toString())
                    .putLong("processStart", PROCESS_START)
                    .apply();
        } catch (Throwable ignore) {
        }
    }

    public static void alive() {
        try {
            preferences().edit().putLong("alive", System.currentTimeMillis()).putLong("processStart", PROCESS_START).apply();
        } catch (Throwable ignore) {
        }
    }

    // A notification of a message (its date in seconds) was shown now.
    public static void onShown(int messageDate) {
        if (messageDate <= 0) {
            return;
        }
        try {
            long delay = System.currentTimeMillis() - messageDate * 1000L;
            preferences().edit()
                    .putLong("lastDelay", Math.max(0, delay))
                    .putLong("lastShown", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignore) {
        }
    }

    public static List<String> events() {
        String all = preferences().getString("events", "");
        List<String> result = new ArrayList<>();
        for (String line : all.split("\n")) {
            if (!line.isEmpty()) {
                result.add(line);
            }
        }
        return result;
    }

    public static void reset() {
        preferences().edit().clear().apply();
    }

    // The part of the push log, in English.
    public static String log(boolean running, boolean watch) {
        SharedPreferences preferences = preferences();
        StringBuilder text = new StringBuilder();
        text.append("Reliable delivery: ").append(MZGramConfig.reliableDelivery ? "on" : "off")
                .append(", service ").append(running ? "running" : "not running")
                .append(", watch ").append(watch ? "scheduled" : "none").append('\n');
        text.append("Process started: ").append(time(PROCESS_START)).append('\n');
        text.append("Last alive: ").append(time(preferences.getLong("alive", 0))).append('\n');
        long shown = preferences.getLong("lastShown", 0);
        text.append("Last notification: ").append(time(shown));
        if (shown > 0) {
            text.append(", ").append(preferences.getLong("lastDelay", 0) / 1000).append(" s after its message");
        }
        text.append('\n');
        List<String> events = events();
        if (!events.isEmpty()) {
            text.append("Reliable delivery events:\n");
            for (String line : events) {
                text.append("  ").append(line).append('\n');
            }
        }
        return text.toString();
    }
}
