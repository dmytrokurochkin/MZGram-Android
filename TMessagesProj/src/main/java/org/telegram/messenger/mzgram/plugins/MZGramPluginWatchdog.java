/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Watchdog and Safe Mode for plugins:
 * - Detects looping crashes caused by plugin code.
 * - If a plugin crashes 3 times (strikes), it is automatically disabled.
 * - If the app crashes 3 times in a row early during startup (within 15 seconds),
 *   it automatically enters Safe Mode, temporarily blocking all plugins and showing
 *   MZGramPluginSafeModeSheet.
 * - Marker file (.watchdog) tracks the currently executing plugin to attribute
 *   process deaths.
 * - Detects frozen plugins (taking more than 5 seconds).
 *
 * Based on exteraless PluginsWatchdog (GPL-3.0); written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.mzgram.MZGramPluginSafeModeSheet;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MZGramPluginWatchdog {

    public static final int STRIKES_BEFORE_DISABLE = 3;
    public static final int EARLY_CRASHES_BEFORE_SAFE_MODE = 3;
    public static final long EARLY_CRASH_WINDOW_MS = 15000L;
    public static final long FREEZE_TIMEOUT_MS = 5000L;
    public static final String MARKER_FILE = ".watchdog";

    private static final String PREFS = "mzgram_plugins_watchdog";
    private static final String KEY_SAFE_MODE = "safe_mode";
    private static final String KEY_SAFE_MODE_AUTO = "safe_mode_auto";
    private static final String KEY_EARLY_CRASHES = "early_crashes";
    private static final String KEY_CRASHED_PLUGIN = "crashed_plugin";
    private static final String KEY_STRIKES_PREFIX = "strikes_";

    public static final class ExecutionInfo {
        public final String pluginId;
        public final long startNanos;

        ExecutionInfo(String pluginId, long startNanos) {
            this.pluginId = pluginId;
            this.startNanos = startNanos;
        }
    }

    private static final ConcurrentHashMap<Thread, ExecutionInfo> executingPlugins = new ConcurrentHashMap<>();
    private static boolean installed;
    private static volatile boolean dying;

    private MZGramPluginWatchdog() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Installs uncaught exception listener to attribute crashes and detect crash loops. */
    public static synchronized void install() {
        // Without the Python runtime no plugin can run: a crash of the app is never a plugin's
        if (installed || !MZGramPythonRuntime.isAvailable()) {
            return;
        }
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                onUncaughtException(thread, error);
            } catch (Throwable ignore) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else if (thread != null && thread.getThreadGroup() != null) {
                thread.getThreadGroup().uncaughtException(thread, error);
            }
        });
        recoverAfterCrash();
    }

    // ---------- Safe Mode ----------

    public static boolean isSafeMode() {
        try {
            return prefs().getBoolean(KEY_SAFE_MODE, false);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setSafeMode(boolean enabled) {
        try {
            SharedPreferences.Editor editor = prefs().edit().putBoolean(KEY_SAFE_MODE, enabled);
            if (!enabled) {
                editor.remove(KEY_SAFE_MODE_AUTO);
            }
            editor.apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static boolean consumeAutoSafeMode() {
        try {
            if (prefs().getBoolean(KEY_SAFE_MODE_AUTO, false)) {
                prefs().edit().remove(KEY_SAFE_MODE_AUTO).apply();
                return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    public static String consumeCrashedPlugin() {
        try {
            String id = prefs().getString(KEY_CRASHED_PLUGIN, null);
            if (id != null) {
                prefs().edit().remove(KEY_CRASHED_PLUGIN).apply();
            }
            return id;
        } catch (Throwable ignore) {
            return null;
        }
    }

    // ---------- Execution tracking ----------

    public static void notePluginEnter(String pluginId) {
        if (pluginId == null) {
            return;
        }
        final Thread thread = Thread.currentThread();
        executingPlugins.put(thread, new ExecutionInfo(pluginId, System.nanoTime()));
    }

    public static void notePluginExit(String pluginId) {
        final Thread thread = Thread.currentThread();
        executingPlugins.remove(thread);
        if (executingPlugins.isEmpty() && !dying) {
            clearMarker();
        }
    }

    public static boolean isNotResponding(String pluginId) {
        if (pluginId == null) {
            return false;
        }
        final long now = System.nanoTime();
        final long timeoutNanos = FREEZE_TIMEOUT_MS * 1_000_000L;
        for (ExecutionInfo info : executingPlugins.values()) {
            if (pluginId.equals(info.pluginId) && (now - info.startNanos) >= timeoutNanos) {
                return true;
            }
        }
        return false;
    }

    // ---------- Crash marker and recovery ----------

    private static File markerPath() {
        try {
            File dir = new File(ApplicationLoader.getFilesDirFixed(), "mzgram_plugins");
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
            return new File(dir, MARKER_FILE);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void writeMarker(String pluginId) {
        if (pluginId == null) {
            return;
        }
        try {
            File file = markerPath();
            if (file == null) {
                return;
            }
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                byte[] bytes = pluginId.getBytes(StandardCharsets.UTF_8);
                raf.seek(0);
                raf.write(bytes);
                raf.setLength(bytes.length);
            }
        } catch (Throwable e) {
            FileLog.e("MZGramPluginWatchdog: cannot write crash marker", e);
        }
    }

    private static void clearMarker() {
        try {
            File file = markerPath();
            if (file != null && file.exists()) {
                file.delete();
            }
        } catch (Throwable ignore) {
        }
    }

    public static void onAppBackgrounded() {
        clearMarker();
    }

    /** Called on startup to process any crash marker or early crash loop. */
    public static String recoverAfterCrash() {
        String crashed = null;
        try {
            File file = markerPath();
            if (file != null && file.isFile() && file.length() > 0) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    byte[] bytes = new byte[(int) Math.min(raf.length(), 256)];
                    raf.readFully(bytes);
                    crashed = new String(bytes, StandardCharsets.UTF_8).trim();
                }
                file.delete();
            }
        } catch (Throwable e) {
            FileLog.e("MZGramPluginWatchdog: cannot read crash marker", e);
        }

        if (crashed != null && !crashed.isEmpty()) {
            final String strikeKey = KEY_STRIKES_PREFIX + crashed;
            int strikes = prefs().getInt(strikeKey, 0) + 1;
            if (strikes >= STRIKES_BEFORE_DISABLE) {
                prefs().edit()
                        .remove(strikeKey)
                        .putString(KEY_CRASHED_PLUGIN, crashed)
                        .commit();
                MZGramPlugins.setEnabled(crashed, false, "watchdog");
                MZGramPluginAuditJournal.record(crashed, MZGramPluginAuditJournal.EVENT_DISABLED,
                        MZGramPluginAuditJournal.CATEGORY_RUNTIME, "watchdog: disabled after " + strikes + " crashes",
                        MZGramPluginAuditJournal.LEVEL_ERROR);
                FileLog.e("MZGramPluginWatchdog: plugin " + crashed + " disabled after " + strikes + " crashes");
            } else {
                prefs().edit().putInt(strikeKey, strikes).commit();
                FileLog.e("MZGramPluginWatchdog: plugin " + crashed + " crash count: " + strikes + "/" + STRIKES_BEFORE_DISABLE);
            }
        }

        try {
            int early = prefs().getInt(KEY_EARLY_CRASHES, 0);
            if (early >= EARLY_CRASHES_BEFORE_SAFE_MODE) {
                prefs().edit()
                        .remove(KEY_EARLY_CRASHES)
                        .putBoolean(KEY_SAFE_MODE, true)
                        .putBoolean(KEY_SAFE_MODE_AUTO, true)
                        .commit();
                FileLog.e("MZGramPluginWatchdog: early crash loop detected, Safe Mode activated");
            } else {
                scheduleEarlyCrashReset();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        return crashed;
    }

    private static void scheduleEarlyCrashReset() {
        long alive = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime();
        long delay = Math.max(0L, EARLY_CRASH_WINDOW_MS - alive);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (prefs().contains(KEY_EARLY_CRASHES)) {
                    prefs().edit().remove(KEY_EARLY_CRASHES).apply();
                }
            } catch (Throwable ignore) {
            }
        }, delay);
    }

    public static void onUncaughtException(Thread thread, Throwable error) {
        dying = true;
        try {
            if (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() < EARLY_CRASH_WINDOW_MS) {
                int count = prefs().getInt(KEY_EARLY_CRASHES, 0) + 1;
                prefs().edit().putInt(KEY_EARLY_CRASHES, count).commit();
            }
        } catch (Throwable ignore) {
        }

        String culprit = null;
        if (thread != null) {
            ExecutionInfo info = executingPlugins.get(thread);
            if (info != null) {
                culprit = info.pluginId;
            }
        }
        if (culprit == null) {
            culprit = MZGramPluginPermissions.current();
        }
        if (culprit == null && error != null) {
            culprit = findPluginInTrace(error);
        }

        if (culprit != null) {
            writeMarker(culprit);
            FileLog.e("MZGramPluginWatchdog: uncaught exception in plugin " + culprit);
        }
    }

    public static String findPluginInTrace(Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 10; t = t.getCause(), depth++) {
            for (StackTraceElement element : t.getStackTrace()) {
                String className = element.getClassName();
                if (className == null) {
                    continue;
                }
                if (className.contains("mzgram_plugin_")) {
                    int idx = className.indexOf("mzgram_plugin_");
                    String rest = className.substring(idx + "mzgram_plugin_".length());
                    int dot = rest.indexOf('.');
                    return dot >= 0 ? rest.substring(0, dot) : rest;
                }
            }
        }
        return null;
    }

    /** Checks startup notices: Safe Mode sheet or crashed plugin notice. */
    public static void checkStartup(Activity activity) {
        if (activity == null || activity.isFinishing() || !MZGramPythonRuntime.isAvailable()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (activity.isFinishing()) {
                return;
            }
            if (consumeAutoSafeMode() || isSafeMode()) {
                Theme.ResourcesProvider provider = null;
                if (activity instanceof LaunchActivity) {
                    BaseFragment fragment = ((LaunchActivity) activity).getActionBarLayout().getLastFragment();
                    if (fragment != null) {
                        provider = fragment.getResourceProvider();
                    }
                }
                new MZGramPluginSafeModeSheet(activity, provider).show();
                return;
            }

            String crashed = consumeCrashedPlugin();
            if (crashed != null) {
                MZGramPlugins.Plugin plugin = MZGramPlugins.get(crashed);
                String displayName = plugin != null && plugin.meta != null ? plugin.meta.name : crashed;
                new AlertDialog.Builder(activity)
                        .setTitle(LocaleController.getString(R.string.MZGramPluginSafeMode))
                        .setMessage(LocaleController.formatString(R.string.MZGramPluginCrashed, displayName))
                        .setPositiveButton(LocaleController.getString(R.string.MZGramOk), null)
                        .show();
            }
        }, 500);
    }
}
