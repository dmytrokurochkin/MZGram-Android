package org.mzgram.plugins;

import android.content.SharedPreferences;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;

public final class PluginTrustLevel {

    public static final int ISOLATED = 0;
    public static final int GATED = 1;
    public static final int TRUSTED = 2;

    public static final int DEFAULT = ISOLATED;

    private PluginTrustLevel() {
    }

    public static String prefsKey(String pluginId) {
        return PluginsConstants.KEY_PLUGIN_LEVEL_PREFIX + pluginId;
    }

    private static SharedPreferences prefs() {
        return PluginGrantStore.get();
    }

    public static int getLevel(String pluginId) {
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return DEFAULT;
        }
        String key = prefsKey(pluginId);
        if (p.contains(key)) {
            return clamp(p.getInt(key, DEFAULT));
        }
        int migrated = migrate(pluginId);
        p.edit().putInt(key, migrated).apply();
        return migrated;
    }

    private static int migrate(String pluginId) {
        List<String> effective = PluginPermissions.getEffectiveRaw(pluginId);
        if (effective.contains(PluginPermissions.HOOKS)
                || effective.contains(PluginPermissions.NATIVE)) {
            return TRUSTED;
        }
        for (String perm : effective) {
            if (!PluginPermissions.UI.equals(perm)) {
                return GATED;
            }
        }
        return ISOLATED;
    }

    public static void setLevel(String pluginId, int level) {
        if (PluginSinkGate.refuseFromPlugin("setLevel", false)) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return;
        }
        int value = clamp(level);
        p.edit().putInt(prefsKey(pluginId), value).apply();
        PluginPermissions.invalidateCache();
        if (value == ISOLATED) {
            PluginPermissions.setGranted(pluginId, new ArrayList<>());
        } else if (value == GATED) {
            PluginPermissions.revoke(pluginId, PluginPermissions.HOOKS);
            PluginPermissions.revoke(pluginId, PluginPermissions.NATIVE);
        }
        FileLog.d("PluginTrustLevel: " + pluginId + " -> " + name(value));
    }

    public static boolean allows(int level, String perm) {
        if (PluginPermissions.UI.equals(perm)) {
            return true;
        }
        if (PluginPermissions.isUnsafeMode()) {
            return true;
        }
        if (level == ISOLATED) {
            return false;
        }
        return level == TRUSTED || !PluginPermissions.isDangerous(perm);
    }

    public static boolean allows(String pluginId, String perm) {
        return allows(getLevel(pluginId), perm);
    }

    public static void clear(String pluginId) {
        if (PluginSinkGate.refuseFromPlugin("clearLevel", true)) {
            return;
        }
        SharedPreferences p = prefs();
        if (p != null && pluginId != null) {
            p.edit().remove(prefsKey(pluginId)).apply();
            PluginPermissions.invalidateCache();
        }
    }

    private static int clamp(int level) {
        if (level < ISOLATED) {
            return ISOLATED;
        }
        return level > TRUSTED ? TRUSTED : level;
    }

    private static String name(int level) {
        switch (level) {
            case ISOLATED: return "isolated";
            case GATED: return "gated";
            case TRUSTED: return "trusted";
            default: return String.valueOf(level);
        }
    }
}
