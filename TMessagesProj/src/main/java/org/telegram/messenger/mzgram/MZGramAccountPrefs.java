/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Small per-account storage for MZGram features, on this device only.
 *
 * App-wide switches live in MZGramConfig (prefs "mzgram_config"). Data that
 * belongs to one account (lists of chats, per-chat exceptions, ...) goes
 * here instead: one SharedPreferences file per feature, "mzgram_<feature>",
 * with every key of an account prefixed "a<account>_", the same layout as
 * MZGramLocalPins. One instance per feature and account:
 *
 *     MZGramAccountPrefs prefs = MZGramAccountPrefs.of("ghost_exclusions", currentAccount);
 *     prefs.putLongList("dialogs", ids);
 *
 * Nothing here is sent anywhere. Not for large data: a table in
 * MZGramHistoryDatabase is the place for that.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class MZGramAccountPrefs {

    private static final HashMap<String, MZGramAccountPrefs[]> instances = new HashMap<>();

    private final String preferencesName;
    private final int account;

    // feature: a short lowercase name, the file is "mzgram_" + feature. Taken
    // by other MZGram files: config, archive, local_pins, push, push_stats,
    // reliable, update.
    public static MZGramAccountPrefs of(String feature, int account) {
        synchronized (instances) {
            MZGramAccountPrefs[] perAccount = instances.get(feature);
            if (perAccount == null) {
                perAccount = new MZGramAccountPrefs[UserConfig.MAX_ACCOUNT_COUNT];
                instances.put(feature, perAccount);
            }
            MZGramAccountPrefs prefs = perAccount[account];
            if (prefs == null) {
                perAccount[account] = prefs = new MZGramAccountPrefs("mzgram_" + feature, account);
            }
            return prefs;
        }
    }

    private MZGramAccountPrefs(String preferencesName, int account) {
        this.preferencesName = preferencesName;
        this.account = account;
    }

    public SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
    }

    private String prefix() {
        return "a" + account + "_";
    }

    // The stored key of this account for a feature's own key
    public String key(String name) {
        return prefix() + name;
    }

    public boolean getBoolean(String name, boolean def) {
        return preferences().getBoolean(key(name), def);
    }

    public void putBoolean(String name, boolean value) {
        preferences().edit().putBoolean(key(name), value).apply();
    }

    public int getInt(String name, int def) {
        return preferences().getInt(key(name), def);
    }

    public void putInt(String name, int value) {
        preferences().edit().putInt(key(name), value).apply();
    }

    public long getLong(String name, long def) {
        return preferences().getLong(key(name), def);
    }

    public void putLong(String name, long value) {
        preferences().edit().putLong(key(name), value).apply();
    }

    // Also for JSON: the feature serializes it itself
    public String getString(String name, String def) {
        return preferences().getString(key(name), def);
    }

    public void putString(String name, String value) {
        preferences().edit().putString(key(name), value).apply();
    }

    // A list of ids (dialogs, users), kept in order as "1,2,3"
    public ArrayList<Long> getLongList(String name) {
        ArrayList<Long> list = new ArrayList<>();
        String saved = getString(name, "");
        if (!TextUtils.isEmpty(saved)) {
            for (String part : saved.split(",")) {
                try {
                    list.add(Long.parseLong(part));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return list;
    }

    public void putLongList(String name, List<Long> list) {
        putString(name, TextUtils.join(",", list));
    }

    public void remove(String name) {
        preferences().edit().remove(key(name)).apply();
    }

    // Everything of this account in this feature, for example when the
    // account is logged out
    public void clear() {
        SharedPreferences preferences = preferences();
        SharedPreferences.Editor editor = preferences.edit();
        for (String stored : preferences.getAll().keySet()) {
            if (stored.startsWith(prefix())) {
                editor.remove(stored);
            }
        }
        editor.commit();
    }
}
