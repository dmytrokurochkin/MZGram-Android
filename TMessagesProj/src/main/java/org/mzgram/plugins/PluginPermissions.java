package org.mzgram.plugins;

import android.content.SharedPreferences;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginPermissions {

    private PluginPermissions() {
    }


    public static final String UI = "ui";
    public static final String MESSAGES_READ = "messages.read";
    public static final String MESSAGES_SEND = "messages.send";
    public static final String NETWORK = "network";
    public static final String FILES = "files";
    public static final String INTENTS = "intents";
    public static final String SETTINGS = "settings";
    public static final String HOOKS = "hooks";
    public static final String NATIVE = "native";

    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            UI, MESSAGES_READ, MESSAGES_SEND, NETWORK, FILES, INTENTS, SETTINGS, HOOKS, NATIVE));

    public static final List<String> REQUESTABLE = Collections.unmodifiableList(
            new ArrayList<>(ALL.subList(1, ALL.size())));

    private static final Set<String> KNOWN = Collections.unmodifiableSet(
            new LinkedHashSet<>(ALL));

    private static final Set<String> LOGGED_DENIALS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_LEGACY = ConcurrentHashMap.newKeySet();

    public static boolean isKnown(String perm) {
        return perm != null && KNOWN.contains(perm);
    }

    public static boolean isDangerous(String perm) {
        return HOOKS.equals(perm) || NATIVE.equals(perm);
    }

    public static String describe(String perm) {
        if (perm == null) {
            return "unknown";
        }
        switch (perm) {
            case UI: return "user interface";
            case MESSAGES_READ: return "read messages and updates";
            case MESSAGES_SEND: return "send, edit and delete messages";
            case NETWORK: return "network access";
            case FILES: return "files outside its own directory";
            case INTENTS: return "intercept links and intents";
            case SETTINGS: return "app settings";
            case HOOKS: return "Java hooks (full control)";
            case NATIVE: return "load native libraries (full control)";
            default: return perm;
        }
    }

    public static List<String> sanitize(Collection<String> perms) {
        List<String> out = new ArrayList<>();
        if (perms == null) {
            return out;
        }
        for (String key : ALL) {
            if (perms.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }


    public static String prefsKey(String pluginId) {
        return PluginsConstants.KEY_PLUGIN_PERMS_PREFIX + pluginId;
    }

    private static SharedPreferences prefs() {
        return PluginGrantStore.get();
    }

    public static boolean hasRecord(String pluginId) {
        SharedPreferences p = prefs();
        return p != null && pluginId != null && p.contains(prefsKey(pluginId));
    }

    public static List<String> getStored(String pluginId) {
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return null;
        }
        String raw = p.getString(prefsKey(pluginId), null);
        if (raw == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String key = part.trim();
            if (!key.isEmpty() && isKnown(key) && !out.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    public static void setGranted(String pluginId, Collection<String> perms) {
        if (PluginSinkGate.refuseFromPlugin("setGranted", false)) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return;
        }
        List<String> clean = sanitize(perms);
        clean.remove(UI);
        p.edit().putString(prefsKey(pluginId), String.join(",", clean)).apply();
        invalidateCache();
        LOGGED_DENIALS.removeIf(k -> k.startsWith(pluginId + "|"));
        LOGGED_LEGACY.remove(pluginId);
        PluginDenialNotice.reset(pluginId);
    }

    public static void grant(String pluginId, String perm) {
        if (!isKnown(perm)) {
            return;
        }
        Set<String> current = new LinkedHashSet<>(getEffective(pluginId));
        current.add(perm);
        setGranted(pluginId, current);
        PluginDenialNotice.reset(pluginId);
    }

    public static void revoke(String pluginId, String perm) {
        if (UI.equals(perm)) {
            return;
        }
        Set<String> current = new LinkedHashSet<>(getEffective(pluginId));
        current.remove(perm);
        setGranted(pluginId, current);
        PluginDenialNotice.reset(pluginId);
    }

    public static void clear(String pluginId) {
        if (PluginSinkGate.refuseFromPlugin("clearGrants", true)) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return;
        }
        p.edit().remove(prefsKey(pluginId)).apply();
        invalidateCache();
        LOGGED_DENIALS.removeIf(k -> k.startsWith(pluginId + "|"));
        LOGGED_LEGACY.remove(pluginId);
        PluginDenialNotice.reset(pluginId);
    }


    public static List<String> getEffective(String pluginId) {
        List<String> raw = getEffectiveRaw(pluginId);
        int level = PluginTrustLevel.getLevel(pluginId);
        if (level == PluginTrustLevel.ISOLATED) {
            return new ArrayList<>(Collections.singletonList(UI));
        }
        if (level != PluginTrustLevel.TRUSTED) {
            raw.remove(HOOKS);
            raw.remove(NATIVE);
        }
        return raw;
    }

    public static List<String> getEffectiveRaw(String pluginId) {
        List<String> stored = getStored(pluginId);
        if (stored != null) {
            if (!stored.contains(UI)) {
                stored.add(0, UI);
            }
            return stored;
        }
        if (pluginId != null && LOGGED_LEGACY.add(pluginId)) {
            FileLog.w("PluginPermissions: no consent record for " + pluginId
                    + ", granting nothing but ui");
        }
        return new ArrayList<>(Collections.singletonList(UI));
    }

    public static List<String> getRequested(Plugin plugin) {
        List<String> out = plugin == null ? new ArrayList<>() : sanitize(plugin.permissions);
        out.remove(UI);
        return out;
    }


    public static boolean has(String pluginId, String perm) {
        if (UI.equals(perm)) {
            return true;
        }
        if (isUnsafeMode()) {
            return true;
        }
        if (pluginId == null || !isKnown(perm)) {
            return false;
        }
        return effectiveCached(pluginId).contains(perm);
    }

    private static final class CachedEffective {
        final Set<String> perms;
        final Plugin plugin;
        final List<String> declared;
        final boolean declaredFlag;
        final int generation;

        CachedEffective(Set<String> perms, Plugin plugin, int generation) {
            this.perms = perms;
            this.plugin = plugin;
            this.declared = plugin == null ? null : plugin.permissions;
            this.declaredFlag = plugin != null && plugin.permissionsDeclared;
            this.generation = generation;
        }

        boolean valid(Plugin current, int currentGeneration) {
            return generation == currentGeneration && plugin == current
                    && (current == null || (declared == current.permissions && declaredFlag == current.permissionsDeclared));
        }
    }

    private static final ConcurrentHashMap<String, CachedEffective> EFFECTIVE = new ConcurrentHashMap<>();
    private static volatile int cacheGeneration;

    public static void invalidateCache() {
        cacheGeneration++;
        EFFECTIVE.clear();
    }

    private static Set<String> effectiveCached(String pluginId) {
        int generation = cacheGeneration;
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        CachedEffective cached = EFFECTIVE.get(pluginId);
        if (cached != null && cached.valid(plugin, generation)) {
            return cached.perms;
        }
        Set<String> perms = Collections.unmodifiableSet(new HashSet<>(getEffective(pluginId)));
        EFFECTIVE.put(pluginId, new CachedEffective(perms, plugin, generation));
        return perms;
    }

    public static boolean isUnsafeMode() {
        try {
            return PluginsController.getInstance().isUnsafeMode();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean check(String pluginId, String perm) {
        if (has(pluginId, perm)) {
            return true;
        }
        String mark = pluginId + "|" + perm;
        if (LOGGED_DENIALS.add(mark)) {
            FileLog.w("PluginPermissions: denied '" + perm + "' (" + describe(perm)
                    + ") to plugin " + pluginId + " — not granted");
        }
        PluginDenialNotice.note(pluginId, perm);
        return false;
    }

    public static boolean check(String pluginId, String perm, String what) {
        if (has(pluginId, perm)) {
            return true;
        }
        String mark = pluginId + "|" + perm + "|" + what;
        if (LOGGED_DENIALS.add(mark)) {
            FileLog.w("PluginPermissions: denied " + what + " to plugin " + pluginId
                    + " — missing '" + perm + "' (" + describe(perm) + ")");
        }
        PluginDenialNotice.note(pluginId, perm);
        return false;
    }
}
