/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Values of plugin settings: one SharedPreferences file per plugin
 * ("mzgram_plugin_<id>"), each value a JSON text under its key. Python reads
 * and writes them through mzgram_plugins.settings (get_setting, set_setting),
 * so they outlive the interpreter and a backup can read them from Java.
 *
 * Nothing is written for an id that is not a valid plugin id (the rule of
 * __id__ in mzgram_plugins/metadata.py), so a file name never comes from
 * anything else.
 */

package org.telegram.messenger.mzgram.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.Map;
import java.util.regex.Pattern;

public final class MZGramPluginSettingsStore {

    public static final String PREFS_PREFIX = "mzgram_plugin_";

    private static final Pattern ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{1,31}$");

    private MZGramPluginSettingsStore() {
    }

    public static boolean isValidId(String pluginId) {
        return pluginId != null && ID.matcher(pluginId).matches();
    }

    private static SharedPreferences prefs(String pluginId) {
        if (!isValidId(pluginId) || ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_PREFIX + pluginId, Context.MODE_PRIVATE);
    }

    /** The JSON text kept under key, or null. */
    public static String get(String pluginId, String key) {
        SharedPreferences prefs = prefs(pluginId);
        if (prefs == null || key == null) {
            return null;
        }
        try {
            return prefs.getString(key, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /** Keeps json (a JSON text) under key; null removes the key. */
    public static void set(String pluginId, String key, String json) {
        SharedPreferences prefs = prefs(pluginId);
        if (prefs == null || key == null || key.isEmpty()) {
            return;
        }
        if (json == null) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putString(key, json).apply();
        }
    }

    /** All values of the plugin as a JSON object: key to its JSON text. */
    public static String export(String pluginId) {
        JSONObject object = new JSONObject();
        SharedPreferences prefs = prefs(pluginId);
        if (prefs == null) {
            return object.toString();
        }
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getValue() instanceof String) {
                try {
                    object.put(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return object.toString();
    }

    /** Removes every value of the plugin (when it is deleted). */
    public static void clear(String pluginId) {
        SharedPreferences prefs = prefs(pluginId);
        if (prefs != null) {
            prefs.edit().clear().apply();
        }
    }
}
