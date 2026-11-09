/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The plugin engine, Java side: which plugins run and in what state they
 * are. A plugin runs only while "Run plugins" is on (MZGramConfig.pluginsRun,
 * off by default) and its own switch is on (MZGramPlugins); then it goes
 *
 *   STOPPED -> STARTING (on_plugin_load) -> RUNNING -> on_plugin_unload -> STOPPED
 *
 * or to FAILED with the error when it cannot start. The work itself is in
 * mzgram_plugins/loader.py on the Python thread of MZGramPythonRuntime; this
 * class only asks for it, in order, and keeps the answers for the screens.
 * Every public method may be called from any thread; the state changes only
 * on the UI thread and may be read from any.
 *
 * The plugins that are on start with the first LaunchActivity.onResume of
 * the process (the only time Python starts by itself, and only when there is
 * a plugin to run); later resumes and pauses go to them as app events.
 *
 * Exists in every build; without the Python runtime (a build without
 * -PmzgramPlugins=true) isOn() is false and nothing here does anything.
 *
 * Based on exteraless PluginsController and PythonPluginsEngine (GPL-3.0):
 * loading the plugins that are on at the start, reloading after an update,
 * the app events; written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.mzgram.MZGramConfig;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class MZGramPluginEngine {

    public static final int STATE_STOPPED = 0;
    public static final int STATE_STARTING = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_FAILED = 3;

    public static final String EVENT_START = "START";
    public static final String EVENT_STOP = "STOP";
    public static final String EVENT_PAUSE = "PAUSE";
    public static final String EVENT_RESUME = "RESUME";

    private static final String MODULE = "mzgram_plugins.loader";
    private static final int MAX_ERROR = 600;

    private static final class State {
        volatile int state = STATE_STOPPED;
        volatile String error;
        volatile boolean hasSettings;
        // Requests sent and not answered yet; the state shown follows the last one (UI thread)
        int pending;
    }

    // Changed on the UI thread only
    private static final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private static final ArrayList<Runnable> listeners = new ArrayList<>();
    private static boolean startedOnce;
    private static boolean paused;

    private MZGramPluginEngine() {
    }

    private static void onUi(Runnable work) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            work.run();
        } else {
            AndroidUtilities.runOnUIThread(work);
        }
    }

    /** True when plugins may run: a build with Python and "Run plugins" on. */
    public static boolean isOn() {
        return MZGramConfig.pluginsRun && MZGramPythonRuntime.isAvailable();
    }

    // ---------- what the screens and the developer server read ----------

    public static int getState(String pluginId) {
        final State state = states.get(pluginId);
        return state != null ? state.state : STATE_STOPPED;
    }

    /** Why the plugin did not start, or null. */
    public static String getError(String pluginId) {
        final State state = states.get(pluginId);
        return state != null && state.state == STATE_FAILED ? state.error : null;
    }

    /** True when the running plugin has a settings page (create_settings). */
    public static boolean hasSettings(String pluginId) {
        final State state = states.get(pluginId);
        return state != null && state.state == STATE_RUNNING && state.hasSettings;
    }

    /** The listener runs on the UI thread whenever a state changes. */
    public static void addListener(Runnable listener) {
        onUi(() -> {
            if (listener != null && !listeners.contains(listener)) {
                listeners.add(listener);
            }
        });
    }

    public static void removeListener(Runnable listener) {
        onUi(() -> listeners.remove(listener));
    }

    private static void changed() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            try {
                listener.run();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    // ---------- the app ----------

    /** LaunchActivity.onResume: the first one starts the plugins that are on. */
    public static void onResume() {
        onUi(() -> {
            paused = false;
            if (!startedOnce) {
                startedOnce = true;
                startAll();
            } else {
                appEvent(EVENT_RESUME);
            }
        });
    }

    /** LaunchActivity.onPause. */
    public static void onPause() {
        onUi(() -> {
            paused = true;
            appEvent(EVENT_PAUSE);
        });
    }

    // Every plugin that is on, then START to all of them
    private static void startAll() {
        if (!isOn()) {
            return;
        }
        boolean any = false;
        for (MZGramPlugins.Plugin plugin : MZGramPlugins.list()) {
            if (plugin.enabled) {
                load(plugin);
                any = true;
            }
        }
        if (any) {
            appEvent(EVENT_START);
        }
    }

    /** "Run plugins" was turned on or off. */
    public static void onRunChanged() {
        onUi(() -> {
            if (isOn()) {
                startAll();
            } else {
                stopAll();
            }
        });
    }

    private static void stopAll() {
        for (String pluginId : new ArrayList<>(states.keySet())) {
            final State state = states.get(pluginId);
            if (state != null && (state.state == STATE_RUNNING || state.state == STATE_STARTING || state.pending > 0)) {
                unload(pluginId);
            } else {
                states.remove(pluginId);
            }
        }
        changed();
    }

    // ---------- one plugin ----------

    /**
     * The plugin was turned on or off, added or updated: it starts (again,
     * from its new file) when it is on and plugins may run, else it stops.
     */
    public static void apply(String pluginId) {
        onUi(() -> {
            final MZGramPlugins.Plugin plugin = MZGramPlugins.get(pluginId);
            if (plugin != null && plugin.enabled && isOn()) {
                load(plugin);
            } else if (isRunning(pluginId)) {
                unload(pluginId);
            } else if (states.remove(pluginId) != null) {
                changed();
            }
        });
    }

    /** The plugin is being deleted: it stops and its state goes. */
    public static void forget(String pluginId) {
        onUi(() -> {
            if (isRunning(pluginId)) {
                unload(pluginId);
            } else if (states.remove(pluginId) != null) {
                changed();
            }
        });
    }

    /** Starts a plugin that is on again; false when it can't run now. */
    public static boolean restart(String pluginId) {
        final MZGramPlugins.Plugin plugin = MZGramPlugins.get(pluginId);
        if (plugin == null || !plugin.enabled || !isOn()) {
            return false;
        }
        onUi(() -> load(plugin));
        return true;
    }

    // Running, starting or with an answer still to come
    private static boolean isRunning(String pluginId) {
        final State state = states.get(pluginId);
        return state != null && (state.state == STATE_RUNNING || state.state == STATE_STARTING || state.pending > 0);
    }

    private static State stateOf(String pluginId) {
        State state = states.get(pluginId);
        if (state == null) {
            state = new State();
            states.put(pluginId, state);
        }
        return state;
    }

    private static void load(MZGramPlugins.Plugin plugin) {
        final String pluginId = plugin.meta.id;
        final String request;
        try {
            request = new JSONObject().put("id", pluginId).put("path", plugin.file.getAbsolutePath()).toString();
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        final State state = stateOf(pluginId);
        state.state = STATE_STARTING;
        state.error = null;
        state.pending++;
        changed();
        MZGramPythonRuntime.call(MODULE, "load_json", request, json -> {
            final State current = stateOf(pluginId);
            current.pending = Math.max(0, current.pending - 1);
            boolean ok = false;
            String error = null;
            boolean hasSettings = false;
            if (json == null) {
                error = MZGramPythonRuntime.getLastError();
            } else {
                try {
                    final JSONObject reply = new JSONObject(json);
                    ok = reply.optBoolean("ok", false);
                    hasSettings = reply.optBoolean("has_settings", false);
                    error = reply.isNull("error") ? null : reply.optString("error", null);
                } catch (Exception e) {
                    error = e.getClass().getSimpleName();
                }
            }
            if (current.pending > 0) {
                // A later request decides what is shown
                return;
            }
            if (ok) {
                current.state = STATE_RUNNING;
                current.hasSettings = hasSettings;
                current.error = null;
                MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_STARTED,
                        MZGramPluginAuditJournal.CATEGORY_RUNTIME, "", MZGramPluginAuditJournal.LEVEL_INFO);
                // A plugin started from the background still learns the app is paused
                if (paused) {
                    appEvent(EVENT_PAUSE);
                }
            } else {
                current.state = STATE_FAILED;
                current.hasSettings = false;
                current.error = shorten(error != null ? error : "?");
                MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_START_FAILED,
                        MZGramPluginAuditJournal.CATEGORY_RUNTIME, current.error, MZGramPluginAuditJournal.LEVEL_ERROR);
            }
            changed();
        });
    }

    private static void unload(String pluginId) {
        final String request;
        try {
            request = new JSONObject().put("id", pluginId).toString();
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        final State state = stateOf(pluginId);
        final boolean wasRunning = state.state == STATE_RUNNING;
        state.pending++;
        state.state = STATE_STOPPED;
        state.hasSettings = false;
        state.error = null;
        changed();
        MZGramPythonRuntime.call(MODULE, "unload_json", request, json -> {
            final State current = stateOf(pluginId);
            current.pending = Math.max(0, current.pending - 1);
            String error = null;
            if (json != null) {
                try {
                    final JSONObject reply = new JSONObject(json);
                    error = reply.isNull("error") ? null : reply.optString("error", null);
                } catch (Exception e) {
                    error = e.getClass().getSimpleName();
                }
            }
            if (error != null) {
                MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_ERROR,
                        MZGramPluginAuditJournal.CATEGORY_RUNTIME, shorten("on_plugin_unload: " + error), MZGramPluginAuditJournal.LEVEL_ERROR);
            }
            if (wasRunning) {
                MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_STOPPED,
                        MZGramPluginAuditJournal.CATEGORY_RUNTIME, "", MZGramPluginAuditJournal.LEVEL_INFO);
            }
            if (current.pending == 0 && current.state == STATE_STOPPED && MZGramPlugins.get(pluginId) == null) {
                states.remove(pluginId);
            }
            changed();
        });
    }

    private static void appEvent(String event) {
        boolean any = false;
        for (State state : states.values()) {
            if (state.state == STATE_RUNNING || state.state == STATE_STARTING) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        final String request;
        try {
            request = new JSONObject().put("event", event).toString();
        } catch (Exception e) {
            return;
        }
        MZGramPythonRuntime.call(MODULE, "app_event_json", request, json -> {
            if (json == null) {
                return;
            }
            try {
                final JSONArray errors = new JSONObject(json).optJSONArray("errors");
                for (int i = 0; errors != null && i < errors.length(); i++) {
                    final JSONObject item = errors.optJSONObject(i);
                    if (item != null) {
                        MZGramPluginAuditJournal.record(item.optString("id", "?"), MZGramPluginAuditJournal.EVENT_ERROR,
                                MZGramPluginAuditJournal.CATEGORY_RUNTIME, shorten("on_app_event " + event + ": " + item.optString("error", "?")),
                                MZGramPluginAuditJournal.LEVEL_ERROR);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    // ---------- from Python ----------

    /** BasePlugin.log of a plugin (mzgram_plugins/loader.py, by name through jclass). */
    public static void log(String pluginId, String text) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId) || text == null) {
            return;
        }
        MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_LOG,
                MZGramPluginAuditJournal.CATEGORY_RUNTIME, text, MZGramPluginAuditJournal.LEVEL_INFO);
    }

    static String shorten(String text) {
        final String line = text.replaceAll("\\s+", " ").trim();
        return line.length() > MAX_ERROR ? line.substring(0, MAX_ERROR) : line;
    }
}
