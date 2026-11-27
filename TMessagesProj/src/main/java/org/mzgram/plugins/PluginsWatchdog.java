package org.mzgram.plugins;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.mzgram.core.helpers.AppRestartHelper;

public class PluginsWatchdog {

    private static final int FREEZE_TIMEOUT_SECONDS = 5;
    private static final long IDLE_CLEAR_DELAY_MS = 2000L;
    private static final long MARKER_AFTER_MS = 1000L;
    private static final String MARKER_FILE = ".watchdog";
    private static final int CRASH_STRIKES_BEFORE_DISABLE = 2;

    private static final long MAIN_BUDGET_WINDOW_MS = 1000L;
    private static final long EXECUTION_SWEEP_MS = 500L;
    private static final long MAIN_BUDGET_NANOS = 250L * 1_000_000L;
    private static final int SLOW_WINDOWS_BEFORE_ALERT = 3;
    private static final long EARLY_CRASH_WINDOW_MS = 15000L;
    private static final int EARLY_CRASHES_BEFORE_SAFE_MODE = 3;
    private static final String MZPACK_NAMESPACE = "MzPackPlugins.";
    private static final String FALLBACK_MODULE_PREFIX = "mz_plugin_";

    public static final class ExecutionInfo {
        final String pluginId;
        final long mainEnterNanos;
        final long startNanos;
        volatile boolean markerWritten;

        ExecutionInfo(String pluginId, long mainEnterNanos, long startNanos) {
            this.pluginId = pluginId;
            this.mainEnterNanos = mainEnterNanos;
            this.startNanos = startNanos;
        }

        public String getPluginId() {
            return pluginId;
        }
    }

    private static final class Frame {
        final String pluginId;
        final long mainEnterNanos;

        Frame(String pluginId, long mainEnterNanos) {
            this.pluginId = pluginId;
            this.mainEnterNanos = mainEnterNanos;
        }
    }

    private final SharedPreferences preferences;

    private final ConcurrentHashMap<Thread, ExecutionInfo> executingPlugins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Thread, ExecutionInfo> frozenExecutions = new ConcurrentHashMap<>();
    private final ThreadLocal<ArrayDeque<Frame>> frames = ThreadLocal.withInitial(ArrayDeque::new);

    private final ConcurrentHashMap<String, AtomicLong> mainThreadNanos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> overBudgetWindows = new ConcurrentHashMap<>();
    private final Set<String> loggedSlow = ConcurrentHashMap.newKeySet();
    private final Set<String> alertedSlow = ConcurrentHashMap.newKeySet();

    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> budgetSweep;
    private volatile ScheduledFuture<?> executionSweep;
    private volatile long lastActivityNanos;
    private static final long IDLE_STOP_NANOS = 5_000_000_000L;
    private final Thread mainThread = android.os.Looper.getMainLooper().getThread();

    private volatile RandomAccessFile markerFile;
    private volatile String persistedMarker;
    private volatile ScheduledFuture<?> pendingMarkerClear;
    private volatile boolean dying;

    PluginsWatchdog(SharedPreferences preferences) {
        this.preferences = preferences;
    }


    public void start() {
        ScheduledExecutorService existing = scheduler;
        if (existing == null || existing.isShutdown()) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "plugins-watchdog");
                thread.setDaemon(true);
                return thread;
            });
            executor.setRemoveOnCancelPolicy(true);
            scheduler = executor;
        }
        lastActivityNanos = System.nanoTime();
        ensureSweeping();
    }

    private synchronized void ensureSweeping() {
        ScheduledExecutorService active = scheduler;
        if (active != null && budgetSweep == null) {
            try {
                budgetSweep = active.scheduleWithFixedDelay(this::sweepMainThreadBudget,
                        MAIN_BUDGET_WINDOW_MS, MAIN_BUDGET_WINDOW_MS, TimeUnit.MILLISECONDS);
                executionSweep = active.scheduleWithFixedDelay(this::sweepExecutions,
                        EXECUTION_SWEEP_MS, EXECUTION_SWEEP_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    private synchronized void stopSweepingIfIdle(long now) {
        if (!executingPlugins.isEmpty() || now - lastActivityNanos < IDLE_STOP_NANOS) {
            return;
        }
        ScheduledFuture<?> sweep = budgetSweep;
        if (sweep != null) {
            sweep.cancel(false);
        }
        budgetSweep = null;
        ScheduledFuture<?> executions = executionSweep;
        if (executions != null) {
            executions.cancel(false);
        }
        executionSweep = null;
    }

    public void stop() {
        ScheduledFuture<?> sweep = budgetSweep;
        if (sweep != null) {
            sweep.cancel(false);
        }
        budgetSweep = null;
        ScheduledFuture<?> executions = executionSweep;
        if (executions != null) {
            executions.cancel(false);
        }
        executionSweep = null;
        mainThreadNanos.clear();
        overBudgetWindows.clear();
        loggedSlow.clear();
        alertedSlow.clear();
        ScheduledExecutorService existing = scheduler;
        if (existing != null) {
            existing.shutdownNow();
        }
        scheduler = null;
        frozenExecutions.clear();
        executingPlugins.clear();
        clearMarkerNow();
    }


    public void notePluginEnter(String pluginId) {
        notePluginEnter(pluginId, false);
    }

    public void notePluginEnter(String pluginId, boolean risky) {
        if (pluginId == null) {
            return;
        }
        Thread thread = Thread.currentThread();
        final long now = System.nanoTime();
        final long mainEnter = thread == mainThread ? now : 0L;

        ArrayDeque<Frame> stack = frames.get();
        stack.push(new Frame(pluginId, mainEnter));

        if (stack.size() == 1) {
            ExecutionInfo info = new ExecutionInfo(pluginId, mainEnter, now);
            executingPlugins.put(thread, info);
            lastActivityNanos = now;
            if (budgetSweep == null && scheduler != null) {
                ensureSweeping();
            }

            ExecutionInfo wasFrozen = frozenExecutions.remove(thread);
            if (wasFrozen != null) {
                if (wasFrozen.pluginId.equals(pluginId)) {
                    frozenExecutions.put(thread, info);
                } else {
                    notifyRecoveredIfLastFrozen(wasFrozen.pluginId);
                }
            }
        }

        if (risky) {
            noteMarker(pluginId);
            ExecutionInfo outer = executingPlugins.get(thread);
            if (outer != null) {
                outer.markerWritten = true;
            }
        }
    }

    public void onAppBackgrounded() {
        clearMarkerNow();
    }

    public void noteHealthy(String pluginId) {
        if (pluginId == null) {
            return;
        }
        String key = PluginsConstants.KEY_WATCHDOG_STRIKES_PREFIX + pluginId;
        if (preferences.getInt(key, 0) != 0) {
            preferences.edit().remove(key).apply();
        }
    }

    public void notePluginExit(String pluginId) {
        if (pluginId == null) {
            return;
        }
        ArrayDeque<Frame> stack = frames.get();
        Frame frame = stack.peek();
        if (frame == null || !frame.pluginId.equals(pluginId)) {
            return;
        }
        stack.pop();

        if (frame.mainEnterNanos != 0L) {
            mainThreadNanos.computeIfAbsent(pluginId, id -> new AtomicLong())
                    .addAndGet(System.nanoTime() - frame.mainEnterNanos);
        }
        if (!stack.isEmpty()) {
            return;
        }

        Thread thread = Thread.currentThread();
        executingPlugins.remove(thread);
        lastActivityNanos = System.nanoTime();
        ExecutionInfo wasFrozen = frozenExecutions.remove(thread);
        if (wasFrozen != null) {
            notifyRecoveredIfLastFrozen(wasFrozen.pluginId);
        }
        scheduleMarkerClear();
    }

    private void sweepMainThreadBudget() {
        for (Map.Entry<String, AtomicLong> entry : mainThreadNanos.entrySet()) {
            final String pluginId = entry.getKey();
            final long spent = entry.getValue().getAndSet(0L);
            AtomicInteger streak = overBudgetWindows.computeIfAbsent(pluginId, id -> new AtomicInteger());
            if (spent < MAIN_BUDGET_NANOS) {
                streak.set(0);
                continue;
            }
            if (streak.incrementAndGet() < SLOW_WINDOWS_BEFORE_ALERT) {
                continue;
            }
            streak.set(0);
            final long millis = spent / 1_000_000L;
            if (loggedSlow.add(pluginId)) {
                FileLog.e("PluginsWatchdog: plugin " + pluginId + " is holding the main thread for "
                        + millis + "ms per second");
            }
            Activity activity = currentActivity();
            if (activity != null && alertedSlow.add(pluginId)) {
                showSlowingDownAlert(pluginId, activity, millis);
            }
        }
    }

    private void sweepExecutions() {
        final long now = System.nanoTime();
        for (Map.Entry<Thread, ExecutionInfo> entry : executingPlugins.entrySet()) {
            final Thread thread = entry.getKey();
            final ExecutionInfo info = entry.getValue();
            final long ageMs = (now - info.startNanos) / 1_000_000L;

            if (!info.markerWritten && ageMs >= MARKER_AFTER_MS) {
                info.markerWritten = true;
                noteMarker(info.pluginId);
            }
            if (ageMs < FREEZE_TIMEOUT_SECONDS * 1000L) {
                continue;
            }
            if (frozenExecutions.putIfAbsent(thread, info) != null) {
                continue;
            }
            FileLog.e("PluginsWatchdog: plugin " + info.pluginId + " is not responding on "
                    + thread.getName());
            NotificationCenter.getGlobalInstance()
                    .postNotificationNameOnUIThread(NotificationCenter.pluginIsNotResponding, info.pluginId);
            showNotRespondingAlert(info.pluginId, currentActivity());
        }
        stopSweepingIfIdle(now);
    }

    private static Activity currentActivity() {
        try {
            org.telegram.ui.ActionBar.BaseFragment fragment =
                    org.telegram.ui.LaunchActivity.getLastFragment();
            return fragment != null ? fragment.getParentActivity() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void notifyRecoveredIfLastFrozen(String pluginId) {
        for (ExecutionInfo other : frozenExecutions.values()) {
            if (other.pluginId.equals(pluginId)) {
                return;
            }
        }
        NotificationCenter.getGlobalInstance()
                .postNotificationNameOnUIThread(NotificationCenter.pluginIsNotResponding, pluginId);
    }

    public boolean isNotResponding(String pluginId) {
        if (pluginId == null) {
            return false;
        }
        for (ExecutionInfo info : frozenExecutions.values()) {
            if (info.pluginId.equals(pluginId)) {
                return true;
            }
        }
        return false;
    }


    private File markerPath() {
        File dir = PluginsController.getInstance().getPluginsDir();
        return dir == null ? null : new File(dir, MARKER_FILE);
    }

    private void noteMarker(String pluginId) {
        ScheduledFuture<?> pending = pendingMarkerClear;
        if (pending != null) {
            pending.cancel(false);
            pendingMarkerClear = null;
        }
        if (pluginId.equals(persistedMarker)) {
            return;
        }
        try {
            RandomAccessFile file = markerFile;
            if (file == null) {
                File path = markerPath();
                if (path == null) {
                    return;
                }
                file = new RandomAccessFile(path, "rw");
                markerFile = file;
            }
            byte[] bytes = pluginId.getBytes(StandardCharsets.UTF_8);
            file.seek(0);
            file.write(bytes);
            file.setLength(bytes.length);
            persistedMarker = pluginId;
        } catch (Throwable t) {
            FileLog.e("PluginsWatchdog: cannot write crash marker", t);
            markerFile = null;
        }
    }

    private void scheduleMarkerClear() {
        if (persistedMarker == null) {
            return;
        }
        ScheduledExecutorService executor = scheduler;
        if (executor == null) {
            clearMarkerNow();
            return;
        }
        ScheduledFuture<?> pending = pendingMarkerClear;
        if (pending != null && !pending.isDone()) {
            return;
        }
        try {
            pendingMarkerClear = executor.schedule(() -> {
                if (executingPlugins.isEmpty()) {
                    clearMarkerNow();
                }
            }, IDLE_CLEAR_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            clearMarkerNow();
        }
    }

    private void clearMarkerNow() {
        if (persistedMarker == null || dying) {
            return;
        }
        try {
            RandomAccessFile file = markerFile;
            if (file != null) {
                file.setLength(0);
            }
            persistedMarker = null;
        } catch (Throwable t) {
            FileLog.e("PluginsWatchdog: cannot clear crash marker", t);
        }
    }

    public String recoverAfterCrash() {
        String legacy = preferences.getString(PluginsConstants.KEY_WATCHDOG_LOADING_LEGACY, null);
        if (legacy != null) {
            preferences.edit()
                    .remove(PluginsConstants.KEY_WATCHDOG_LOADING_LEGACY)
                    .putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + legacy, false)
                    .putString(PluginsConstants.KEY_WATCHDOG_CRASHED, legacy)
                    .apply();
            return legacy;
        }
        File path = markerPath();
        if (path == null || !path.isFile() || path.length() == 0) {
            return null;
        }
        String crashed = null;
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            byte[] bytes = new byte[(int) Math.min(file.length(), 256)];
            file.readFully(bytes);
            crashed = new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            FileLog.e("PluginsWatchdog: cannot read crash marker", t);
        }
        //noinspection ResultOfMethodCallIgnored
        path.delete();
        if (crashed == null || crashed.isEmpty()) {
            return null;
        }
        String strikeKey = PluginsConstants.KEY_WATCHDOG_STRIKES_PREFIX + crashed;
        int strikes = preferences.getInt(strikeKey, 0) + 1;
        if (strikes < CRASH_STRIKES_BEFORE_DISABLE) {
            preferences.edit().putInt(strikeKey, strikes).apply();
            FileLog.e("PluginsWatchdog: plugin " + crashed + " was active when the process died ("
                    + strikes + "/" + CRASH_STRIKES_BEFORE_DISABLE + "), keeping it enabled");
            return null;
        }
        preferences.edit()
                .remove(strikeKey)
                .putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + crashed, false)
                .putString(PluginsConstants.KEY_WATCHDOG_CRASHED, crashed)
                .apply();
        FileLog.e("PluginsWatchdog: plugin " + crashed + " active during "
                + CRASH_STRIKES_BEFORE_DISABLE + " process deaths, disabled");
        return crashed;
    }


    public void handlePluginError(String pluginId, Throwable t) {
        FileLog.e("PluginsWatchdog: plugin " + pluginId + " threw", t);
        notePluginExit(pluginId);
    }

    private void disablePluginPref(String pluginId) {
        preferences.edit()
                .putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + pluginId, false)
                .commit();
    }

    public void forceDisablePlugin(String pluginId, Activity activity) {
        disablePluginPref(pluginId);
        clearMarkerNow();
        restartApp(activity);
    }

    private void restartApp(Activity activity) {
        if (activity == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                AppRestartHelper.triggerRebirth(activity, new Intent(activity, LaunchActivity.class));
            } catch (Throwable t) {
                FileLog.e("PluginsWatchdog: restart failed", t);
                System.exit(0);
            }
        });
    }

    public boolean isWarningMuted(String pluginId) {
        return pluginId != null
                && preferences.getBoolean(PluginsConstants.KEY_WATCHDOG_MUTED_PREFIX + pluginId, false);
    }

    public void setWarningMuted(String pluginId, boolean muted) {
        if (pluginId == null) {
            return;
        }
        String key = PluginsConstants.KEY_WATCHDOG_MUTED_PREFIX + pluginId;
        if (muted) {
            preferences.edit().putBoolean(key, true).apply();
        } else {
            preferences.edit().remove(key).apply();
            alertedSlow.remove(pluginId);
        }
    }

    private String displayNameOf(String pluginId) {
        for (Plugin plugin : PluginsController.getInstance().getPluginsSnapshot()) {
            if (pluginId.equals(plugin.id)) {
                return plugin.getDisplayName();
            }
        }
        return pluginId;
    }

    public void showNotRespondingAlert(String pluginId, Activity activity) {
        if (activity == null || pluginId == null || isWarningMuted(pluginId)) {
            return;
        }
        final String displayName = displayNameOf(pluginId);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                new AlertDialog.Builder(activity)
                        .setTitle(LocaleController.getString(R.string.PluginNotRespondingTitle))
                        .setMessage(LocaleController.formatString(
                                R.string.PluginNotRespondingMessage, displayName))
                        .setPositiveButton(LocaleController.getString(R.string.PluginDisable),
                                (dialog, which) -> forceDisablePlugin(pluginId, activity))
                        .setNeutralButton(LocaleController.getString(R.string.PluginWatchdogMute),
                                (dialog, which) -> muteFromDialog(pluginId, displayName))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .show();
            } catch (Throwable t) {
                FileLog.e("PluginsWatchdog: cannot show not-responding alert", t);
            }
        });
    }

    public void showSlowingDownAlert(String pluginId, Activity activity, long millisPerSecond) {
        if (activity == null || pluginId == null || isWarningMuted(pluginId)) {
            return;
        }
        final String displayName = displayNameOf(pluginId);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                new AlertDialog.Builder(activity)
                        .setTitle(LocaleController.getString(R.string.PluginSlowingDownTitle))
                        .setMessage(LocaleController.formatString(
                                R.string.PluginSlowingDownMessage, displayName, millisPerSecond))
                        .setPositiveButton(LocaleController.getString(R.string.PluginDisable),
                                (dialog, which) -> PluginsController.getInstance().setPluginEnabled(pluginId, false, null))
                        .setNeutralButton(LocaleController.getString(R.string.PluginWatchdogMute),
                                (dialog, which) -> muteFromDialog(pluginId, displayName))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .show();
            } catch (Throwable t) {
                FileLog.e("PluginsWatchdog: cannot show slowing-down alert", t);
            }
        });
    }

    private void muteFromDialog(String pluginId, String displayName) {
        setWarningMuted(pluginId, true);
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            return;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip,
                LocaleController.formatString(R.string.PluginWatchdogMuted, displayName)).show();
    }

    public long getMainThreadMillis(String pluginId) {
        AtomicLong counter = pluginId == null ? null : mainThreadNanos.get(pluginId);
        return counter == null ? 0L : counter.get() / 1_000_000L;
    }

    public static void onUncaughtException(Throwable error) {
        try {
            PluginsController controller = PluginsController.peekInstance();
            PluginsWatchdog watchdog = controller != null ? controller.getWatchdog() : null;
            if (watchdog != null) {
                watchdog.recordUncaught(error, controller);
            }
        } catch (Throwable ignore) {
        }
    }

    private void recordUncaught(Throwable error, PluginsController controller) {
        String culprit = findPluginInTrace(error, controller.plugins.keySet());
        if (culprit != null) {
            dying = true;
            persistedMarker = null;
            noteMarker(culprit);
            FileLog.e("PluginsWatchdog: uncaught exception thrown through plugin " + culprit);
            return;
        }
        if (!controller.isEngineEnabled() || controller.isSafeMode()) {
            return;
        }
        if (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() < EARLY_CRASH_WINDOW_MS) {
            int crashes = preferences.getInt(PluginsConstants.KEY_WATCHDOG_EARLY_CRASHES, 0) + 1;
            preferences.edit().putInt(PluginsConstants.KEY_WATCHDOG_EARLY_CRASHES, crashes).commit();
        }
    }

    static String findPluginInTrace(Throwable error, Collection<String> pluginIds) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 10; t = t.getCause(), depth++) {
            String outermost = null;
            for (StackTraceElement element : t.getStackTrace()) {
                String className = element.getClassName();
                if (className == null || !className.startsWith("<python>.")) {
                    continue;
                }
                String id = pluginIdForModule(className.substring("<python>.".length()), pluginIds);
                if (id != null) {
                    outermost = id;
                }
            }
            if (outermost != null) {
                return outermost;
            }
        }
        return null;
    }

    static String pluginIdForModule(String module, Collection<String> pluginIds) {
        if (module.startsWith(MZPACK_NAMESPACE)) {
            String rest = module.substring(MZPACK_NAMESPACE.length());
            int dot = rest.indexOf('.');
            String id = dot >= 0 ? rest.substring(0, dot) : rest;
            return pluginIds.contains(id) ? id : null;
        }
        int dot = module.indexOf('.');
        String top = dot >= 0 ? module.substring(0, dot) : module;
        if (pluginIds.contains(top)) {
            return top;
        }
        if (top.startsWith(FALLBACK_MODULE_PREFIX)) {
            String sanitized = top.substring(FALLBACK_MODULE_PREFIX.length());
            for (String id : pluginIds) {
                if (id.equals(sanitized) || id.replaceAll("[^0-9A-Za-z_]", "_").equals(sanitized)) {
                    return id;
                }
            }
        }
        return null;
    }

    public boolean consumeEarlyCrashLoop() {
        int crashes = preferences.getInt(PluginsConstants.KEY_WATCHDOG_EARLY_CRASHES, 0);
        if (crashes < EARLY_CRASHES_BEFORE_SAFE_MODE) {
            return false;
        }
        preferences.edit().remove(PluginsConstants.KEY_WATCHDOG_EARLY_CRASHES).apply();
        return true;
    }

    public void scheduleEarlyCrashReset() {
        long alive = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime();
        AndroidUtilities.runOnUIThread(() -> {
            if (preferences.contains(PluginsConstants.KEY_WATCHDOG_EARLY_CRASHES)) {
                preferences.edit().remove(PluginsConstants.KEY_WATCHDOG_EARLY_CRASHES).apply();
            }
        }, Math.max(0L, EARLY_CRASH_WINDOW_MS - alive));
    }

    public boolean consumeAutoSafeMode() {
        if (!preferences.getBoolean(PluginsConstants.KEY_SAFE_MODE_AUTO, false)) {
            return false;
        }
        preferences.edit().remove(PluginsConstants.KEY_SAFE_MODE_AUTO).apply();
        return true;
    }

    public static String consumeStartupNotice() {
        PluginsController controller = PluginsController.peekInstance();
        PluginsWatchdog watchdog = controller != null ? controller.getWatchdog() : null;
        if (watchdog == null) {
            return null;
        }
        if (watchdog.consumeAutoSafeMode()) {
            return LocaleController.getString(R.string.PluginsAutoSafeMode);
        }
        String crashed = watchdog.consumeCrashedPlugin();
        if (crashed != null) {
            return LocaleController.formatString(R.string.PluginsCrashedMessage, watchdog.displayNameOf(crashed));
        }
        return null;
    }

    public String consumeCrashedPlugin() {
        String id = preferences.getString(PluginsConstants.KEY_WATCHDOG_CRASHED, null);
        if (id != null) {
            preferences.edit().remove(PluginsConstants.KEY_WATCHDOG_CRASHED).apply();
        }
        return id;
    }

    public Map<Thread, ExecutionInfo> getExecutingPlugins() {
        return executingPlugins;
    }
}
