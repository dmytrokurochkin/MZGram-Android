package org.mzgram.plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.mzgram.debug.PluginToggleTrace;

public class PluginsController extends org.mzgram.sdk.plugins.PluginsController {

    private static volatile PluginsController instance;

    public static PluginsController peekInstance() {
        return instance;
    }

    public static PluginsController getInstance() {
        if (instance == null) {
            synchronized (PluginsController.class) {
                if (instance == null) {
                    instance = new PluginsController();
                }
            }
        }
        return instance;
    }

    private Context appContext;
    private SharedPreferences preferences;
    private PluginsWatchdog watchdog;

    public final Map<String, Plugin> plugins = new ConcurrentHashMap<>();

    private final Map<String, String> knownPaths = new ConcurrentHashMap<>();

    private static final java.util.regex.Pattern PLUGIN_ID =
            java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{1,31}$");
    private final Map<String, Integer> sendMessageHooks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> requestHooks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> requestHooksSubstring = new ConcurrentHashMap<>();
    private final Map<String, List<String>> updateHooks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> updatesContainerHooks = new ConcurrentHashMap<>();

    private final Map<String, Integer> hookPriorities = new ConcurrentHashMap<>();
    private final HookTargetCache sendTargetsCache = new HookTargetCache();
    private final HookTargetCache requestTargetsCache = new HookTargetCache();
    private final Map<String, String> metadataJsonCache = new ConcurrentHashMap<>();
    private final HookTargetCache updateTargetsCache = new HookTargetCache();
    private final HookTargetCache updatesTargetsCache = new HookTargetCache();

    private final List<MenuItemRecord> menuItems = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<Runnable>> settingsReloadListeners = new ConcurrentHashMap<>();

    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor(r ->
            new Thread(r, "plugins-io"));

    private volatile boolean initialized;

    private PluginsController() {
    }

    // ---------- init ----------

    public void init(Context context) {
        if (PluginSinkGate.refuseFromPlugin("init", true)) {
            return;
        }
        if (initialized) {
            return;
        }
        initialized = true;
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PluginsConstants.PREFS_NAME, Context.MODE_PRIVATE);
        PluginGrantStore.get();
        watchdog = new PluginsWatchdog(preferences);
        getPluginsDir().mkdirs();

        if (!isEngineEnabled()) {
            return;
        }
        // The plugin catalog is gone: drop the list it kept on disk.
        new File(ApplicationLoader.getFilesDirFixed(), "mzgram_plugin_catalog.json").delete();
        String crashed = watchdog.recoverAfterCrash();
        if (crashed != null) {
            FileLog.e("PluginsController: disabled crashed plugin " + crashed);
        }
        if (watchdog.consumeEarlyCrashLoop() && !isSafeMode()) {
            preferences.edit()
                    .putBoolean(PluginsConstants.KEY_SAFE_MODE, true)
                    .putBoolean(PluginsConstants.KEY_SAFE_MODE_AUTO, true)
                    .apply();
            FileLog.e("PluginsController: repeated crashes right after launch, starting in safe mode");
        }
        if (!isSafeMode() && hasEnabledPlugin()) {
            org.mzgram.plugins.xposed.XposedHooks.ensureReady();
        }
        watchdog.scheduleEarlyCrashReset();
        watchdog.start();
        PythonPluginsEngine.getInstance().ensureStarted(appContext, ok -> {
            if (ok) {
                PluginGrantStore.warmUp();
                rescanAndLoadEnabled();
                executeOnAppEvent(PluginsConstants.EVENT_APP_START);
            }
        });
    }

    private boolean hasEnabledPlugin() {
        for (java.util.Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX)
                    && Boolean.TRUE.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    public PluginsWatchdog getWatchdog() {
        return watchdog;
    }

    public File getPluginsDir() {
        return new File(appContext.getFilesDir(), "plugins");
    }


    public SharedPreferences getPreferences() {
        return preferences;
    }

    public boolean isEngineEnabled() {
        return preferences != null && preferences.getBoolean(PluginsConstants.KEY_ENGINE_ENABLED, false);
    }

    public void setEngineEnabled(boolean enabled) {
        if (PluginSinkGate.refuseFromPlugin("setEngineEnabled", false)) {
            return;
        }
        preferences.edit()
                .putBoolean(PluginsConstants.KEY_ENGINE_ENABLED, enabled)
                .remove(PluginsConstants.KEY_NATIVE_HOOKS_BROKEN)
                .remove(PluginsConstants.KEY_NATIVE_HOOKS_BROKEN_STAMP)
                .remove(PluginsConstants.KEY_NATIVE_HOOKS_STRIKES)
                .apply();
        if (enabled && initialized) {
            PythonPluginsEngine.getInstance().ensureStarted(appContext, ok -> {
                if (ok) {
                    rescanAndLoadEnabled();
                }
            });
        } else if (!enabled) {
            unloadAll();
        }
    }

    public boolean isSafeMode() {
        return preferences != null && preferences.getBoolean(PluginsConstants.KEY_SAFE_MODE, false);
    }

    public void setSafeMode(boolean safeMode) {
        if (PluginSinkGate.refuseFromPlugin("setSafeMode", false)) {
            return;
        }
        preferences.edit().putBoolean(PluginsConstants.KEY_SAFE_MODE, safeMode).apply();
    }

    public boolean isUnsafeMode() {
        Boolean cached = unsafeMode;
        if (cached != null) {
            return cached;
        }
        SharedPreferences store = PluginGrantStore.get();
        if (store == null) {
            return false;
        }
        cached = store.getBoolean(PluginsConstants.KEY_UNSAFE_MODE, false);
        unsafeMode = cached;
        return cached;
    }

    public void setUnsafeMode(boolean value) {
        if (PluginSinkGate.refuseFromPlugin("setUnsafeMode", false)) {
            return;
        }
        unsafeMode = value;
        SharedPreferences store = PluginGrantStore.get();
        if (store != null) {
            store.edit().putBoolean(PluginsConstants.KEY_UNSAFE_MODE, value).apply();
        }
        FileLog.w("PluginsController: unsafe mode " + (value ? "ON" : "off"));
        PythonPluginsEngine.getInstance().setUnsafeMode(value);
    }

    private volatile Boolean unsafeMode;

    public boolean isDeveloperMode() {
        return preferences != null && preferences.getBoolean(PluginsConstants.KEY_DEVELOPER_MODE, false);
    }

    public void setDeveloperMode(boolean developerMode) {
        preferences.edit().putBoolean(PluginsConstants.KEY_DEVELOPER_MODE, developerMode).apply();
    }


    public boolean isCompactView() {
        return preferences != null && preferences.getBoolean(PluginsConstants.KEY_COMPACT_VIEW, false);
    }

    public void setCompactView(boolean value) {
        preferences.edit().putBoolean(PluginsConstants.KEY_COMPACT_VIEW, value).apply();
    }

    public boolean isCompatibilityMode() {
        return preferences != null
                && preferences.getBoolean(PluginsConstants.KEY_COMPATIBILITY_MODE, false);
    }

    public void setCompatibilityMode(boolean value) {
        preferences.edit().putBoolean(PluginsConstants.KEY_COMPATIBILITY_MODE, value).apply();
    }

    public static boolean isPluginPinned(String id) {
        SharedPreferences prefs = getInstance().preferences;
        return id != null && prefs != null && prefs.getBoolean("plugin_pinned_" + id, false);
    }

    public static void setPluginPinned(String id, boolean pinned) {
        SharedPreferences prefs = getInstance().preferences;
        if (id == null || prefs == null) {
            return;
        }
        if (pinned) {
            prefs.edit().putBoolean("plugin_pinned_" + id, true).apply();
        } else {
            prefs.edit().remove("plugin_pinned_" + id).apply();
        }
    }

    private static boolean hasPluginExtension(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(PluginsConstants.PLUGIN_EXT)
                || lower.endsWith(PluginsConstants.PLUGIN_EXT_PY)
                || lower.endsWith(PluginsConstants.PLUGIN_EXT_MZPACK)
                || lower.endsWith(PluginsConstants.PLUGIN_EXT_EAF);
    }

    public static boolean isPlugin(File file, MessageObject message) {
        return file != null && file.exists() && file.length() > 0
                && hasPluginExtension(file.getName());
    }

    public static boolean isPlugin(MessageObject message) {
        if (message == null || !hasPluginExtension(message.getDocumentName())) {
            return false;
        }
        try {
            File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(message.messageOwner);
            return path != null && hasPluginExtension(path.getName());
        } catch (Throwable t) {
            return false;
        }
    }

    public List<Plugin> getPluginsSnapshot() {
        return new ArrayList<>(plugins.values());
    }

    @Override
    public Map<String, Plugin> getPlugins() {
        return plugins;
    }

    public Map<String, Plugin> getPluginsMap() {
        return plugins;
    }

    public Plugin getPlugin(String id) {
        return plugins.get(id);
    }

    private File normalizeInstalledName(File file) {
        String name = file.getName();
        if (!name.endsWith(PluginsConstants.PLUGIN_EXT)) {
            return file;
        }
        File renamed = new File(file.getParentFile(),
                name.substring(0, name.length() - PluginsConstants.PLUGIN_EXT.length())
                        + PluginsConstants.PLUGIN_EXT_PY);
        if (renamed.exists() || !file.renameTo(renamed)) {
            return file;
        }
        return renamed;
    }

    private void markUnconsented(String id) {
        PluginTrustLevel.setLevel(id, PluginTrustLevel.ISOLATED);
        PluginDenialNotice.noteUnconsented(id);
    }

    private static final String[] PLUGIN_FILE_EXTS = {
            PluginsConstants.PLUGIN_EXT_PY, PluginsConstants.PLUGIN_EXT,
            PluginsConstants.PLUGIN_EXT_MZPACK, PluginsConstants.PLUGIN_EXT_EAF};

    private static boolean isCanonicalFile(File file, String id) {
        String name = file.getName();
        for (String ext : PLUGIN_FILE_EXTS) {
            if (name.equals(id + ext)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCanonicalFile(String id) {
        for (String ext : PLUGIN_FILE_EXTS) {
            if (new File(getPluginsDir(), id + ext).isFile()) {
                return true;
            }
        }
        return false;
    }

    public synchronized void rescanPlugins() {
        if (!PythonPluginsEngine.getInstance().isStarted()) {
            return;
        }
        File[] files = getPluginsDir().listFiles();
        if (files == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (File f : files) {
            if (!f.isFile() || f.getName().startsWith(".")) {
                continue;
            }
            f = normalizeInstalledName(f);
            String name = f.getName();
            if (!name.endsWith(PluginsConstants.PLUGIN_EXT_PY) && !name.endsWith(PluginsConstants.PLUGIN_EXT)
                    && !name.endsWith(PluginsConstants.PLUGIN_EXT_MZPACK) && !name.endsWith(PluginsConstants.PLUGIN_EXT_EAF)) {
                continue;
            }
            Plugin fresh = readPluginMetadata(f);
            if (fresh == null || fresh.id == null) {
                continue;
            }
            if (!isCanonicalFile(f, fresh.id) && hasCanonicalFile(fresh.id)) {
                FileLog.w("PluginsController: " + f.getName() + " claims the id of an installed plugin "
                        + fresh.id + ", skipped");
                continue;
            }
            seen.add(fresh.id);
            if (!PluginPermissions.hasRecord(fresh.id)) {
                markUnconsented(fresh.id);
            }
            boolean enabled = preferences.getBoolean(
                    PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + fresh.id, true);
            Plugin existing = plugins.get(fresh.id);
            String freshPath = f.getAbsolutePath();
            if (existing != null && existing.loaded && !freshPath.equals(existing.path)
                    && existing.path != null && new File(existing.path).exists()) {
                continue;
            }
            if (existing != null && existing.loaded) {
                existing.path = freshPath;
                knownPaths.put(fresh.id, freshPath);
                existing.name = fresh.name;
                existing.description = fresh.description;
                existing.author = fresh.author;
                existing.version = fresh.version;
                existing.icon = fresh.icon;
                existing.appVersion = fresh.appVersion;
                existing.sdkVersion = fresh.sdkVersion;
                existing.beta = fresh.beta;
                existing.requirements = fresh.requirements;
                existing.permissions = fresh.permissions;
                existing.permissionsDeclared = fresh.permissionsDeclared;
                existing.loadError = fresh.loadError;
                existing.enabled = enabled;
                continue;
            }
            fresh.enabled = enabled;
            knownPaths.put(fresh.id, freshPath);
            plugins.put(fresh.id, fresh);
        }
        for (Iterator<Map.Entry<String, Plugin>> it = plugins.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Plugin> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            Plugin gone = entry.getValue();
            if (gone.loaded) {
                unregisterPluginHooks(gone.id);
                PythonPluginsEngine.getInstance().unload(gone);
            }
            knownPaths.remove(entry.getKey());
            it.remove();
        }
    }

    private SharedPreferences metadataPrefs;

    private SharedPreferences metadataPrefs() {
        SharedPreferences prefs = metadataPrefs;
        if (prefs == null && appContext != null) {
            prefs = appContext.getSharedPreferences(PluginsConstants.PREFS_NAME + "_metadata", Context.MODE_PRIVATE);
            metadataPrefs = prefs;
        }
        return prefs;
    }

    private String readPersistedMetadata(String path, String cacheKey) {
        SharedPreferences prefs = metadataPrefs();
        if (prefs == null) {
            return null;
        }
        String stored = prefs.getString(path, null);
        if (stored == null) {
            return null;
        }
        String prefix = cacheKey + "\n";
        return stored.startsWith(prefix) ? stored.substring(prefix.length()) : null;
    }

    private void persistMetadata(String path, String cacheKey, String json) {
        SharedPreferences prefs = metadataPrefs();
        if (prefs != null) {
            prefs.edit().putString(path, cacheKey + "\n" + json).apply();
        }
    }

    private Plugin readPluginMetadata(File f) {
        final String path = f.getAbsolutePath();
        final String cacheKey = path + "|" + f.length() + "|" + f.lastModified();
        String json = metadataJsonCache.get(cacheKey);
        if (json == null) {
            json = readPersistedMetadata(path, cacheKey);
            if (json == null) {
                json = PythonPluginsEngine.getInstance().readMetadataJson(path);
                if (json != null && (json.contains("\"ok\": true") || json.contains("\"ok\":true"))) {
                    persistMetadata(path, cacheKey, json);
                }
            }
            if (json != null) {
                metadataJsonCache.keySet().removeIf(k -> k.startsWith(path + "|"));
                metadataJsonCache.put(cacheKey, json);
            }
        }
        if (json == null) {
            Plugin p = new Plugin();
            p.id = f.getName();
            p.name = f.getName();
            p.path = f.getAbsolutePath();
            p.loadError = "engine not ready";
            return p;
        }
        try {
            JSONObject root = new JSONObject(json);
            Plugin p = new Plugin();
            p.path = f.getAbsolutePath();
            if (!root.optBoolean("ok")) {
                p.id = f.getName();
                p.name = f.getName();
                p.loadError = root.optString("error", "unknown error");
                p.loadDebug = root.optString("debug", null);
                return p;
            }
            JSONObject meta = root.getJSONObject("meta");
            p.id = meta.optString("id");
            p.name = meta.optString("name");
            p.description = JsonUtils.optStringOrNull(meta, "description");
            p.author = JsonUtils.optStringOrNull(meta, "author");
            p.version = meta.optString("version", "1.0");
            p.icon = JsonUtils.optStringOrNull(meta, "icon");
            p.appVersion = JsonUtils.optStringOrNull(meta, "app_version");
            p.sdkVersion = JsonUtils.optStringOrNull(meta, "sdk_version");
            p.beta = meta.optBoolean("beta", false);
            p.requirements = new ArrayList<>();
            JSONArray reqs = meta.optJSONArray("requirements");
            if (reqs != null) {
                for (int i = 0; i < reqs.length(); i++) {
                    p.requirements.add(reqs.optString(i));
                }
            }
            p.permissions = new ArrayList<>();
            p.permissionsDeclared = meta.optBoolean("permissions_declared", false);
            JSONArray perms = meta.optJSONArray("permissions");
            if (perms != null) {
                for (int i = 0; i < perms.length(); i++) {
                    String key = perms.optString(i);
                    if (PluginPermissions.isKnown(key) && !p.permissions.contains(key)) {
                        p.permissions.add(key);
                    }
                }
            }
            // MzPack requires: {"dep_id": {"min_version": ..., "url": ...}}
            p.requires = new java.util.LinkedHashMap<>();
            JSONObject requires = meta.optJSONObject("requires");
            if (requires != null) {
                Iterator<String> keys = requires.keys();
                while (keys.hasNext()) {
                    String depId = keys.next();
                    JSONObject dep = requires.optJSONObject(depId);
                    p.requires.put(depId, new String[]{
                            dep == null ? null : JsonUtils.optStringOrNull(dep, "min_version"),
                            dep == null ? null : JsonUtils.optStringOrNull(dep, "url"),
                    });
                }
            }
            String constraintError = checkVersionConstraints(p);
            if (constraintError != null) {
                p.loadError = constraintError;
            }
            return p;
        } catch (Exception e) {
            FileLog.e("PluginsController: bad metadata json for " + f.getName(), e);
            Plugin p = new Plugin();
            p.id = f.getName();
            p.name = f.getName();
            p.path = f.getAbsolutePath();
            p.loadError = "metadata parse error";
            return p;
        }
    }

    private String checkVersionConstraints(Plugin p) {
        if (p.appVersion != null && !checkVersionConstraint(p.appVersion, BuildVars.BUILD_VERSION_STRING)) {
            return "requires app " + p.appVersion;
        }
        if (p.sdkVersion != null && !checkVersionConstraint(p.sdkVersion, PluginsConstants.SDK_VERSION)) {
            return "requires sdk " + p.sdkVersion;
        }
        return null;
    }

    public static boolean checkVersionConstraint(String constraint, String current) {
        if (constraint == null || constraint.isEmpty()) {
            return true;
        }
        String op = ">=";
        String version = constraint.trim();
        boolean hasDigit = false;
        for (int i = 0; i < version.length(); i++) {
            if (Character.isDigit(version.charAt(i))) {
                hasDigit = true;
                break;
            }
        }
        if (!hasDigit) {
            return true;
        }
        for (String candidate : new String[]{">=", "<=", "==", ">", "<"}) {
            if (version.startsWith(candidate)) {
                op = candidate;
                version = version.substring(candidate.length()).trim();
                break;
            }
        }
        int cmp = compareVersions(current, version);
        switch (op) {
            case ">=": return cmp >= 0;
            case "<=": return cmp <= 0;
            case ">": return cmp > 0;
            case "<": return cmp < 0;
            default: return cmp == 0;
        }
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? parseVersionPart(pa[i]) : 0;
            int vb = i < pb.length ? parseVersionPart(pb[i]) : 0;
            if (va != vb) {
                return va < vb ? -1 : 1;
            }
        }
        return 0;
    }

    private static int parseVersionPart(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    public void rescanAndLoadEnabled() {
        rescanPlugins();
        if (isSafeMode()) {
            FileLog.d("PluginsController: safe mode, no plugins loaded");
            return;
        }
        List<Plugin> snapshot = loadOrder(getPluginsSnapshot());
        for (Plugin p : snapshot) {
            if (p.enabled && !p.loaded && p.loadError == null) {
                loadPluginInternal(p);
            }
        }
    }

    private static List<Plugin> loadOrder(List<Plugin> snapshot) {
        List<Plugin> ranked = new ArrayList<>(snapshot);
        ranked.sort(Comparator.comparingInt(p -> isStructuredPlugin(p) ? 0 : 1));
        Map<String, Plugin> byId = new HashMap<>();
        for (Plugin p : ranked) {
            if (p.id != null) {
                byId.put(p.id, p);
            }
        }
        java.util.LinkedHashSet<Plugin> ordered = new java.util.LinkedHashSet<>();
        for (Plugin p : ranked) {
            addAfterRequired(p, byId, ordered, new HashSet<>());
        }
        return new ArrayList<>(ordered);
    }

    private static void addAfterRequired(Plugin p, Map<String, Plugin> byId, Set<Plugin> ordered,
                                         Set<Plugin> visiting) {
        if (ordered.contains(p) || !visiting.add(p)) {
            return;
        }
        if (p.requires != null) {
            for (String dependency : p.requires.keySet()) {
                Plugin required = byId.get(dependency);
                if (required != null) {
                    addAfterRequired(required, byId, ordered, visiting);
                }
            }
        }
        ordered.add(p);
    }

    private static boolean isStructuredPlugin(Plugin p) {
        String path = p.path == null ? "" : p.path.toLowerCase(Locale.ROOT);
        return path.endsWith(PluginsConstants.PLUGIN_EXT_MZPACK) || path.endsWith(PluginsConstants.PLUGIN_EXT_EAF);
    }

    private String checkRequiredPlugins(Plugin p) {
        if (p.requires == null || p.requires.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String[]> entry : p.requires.entrySet()) {
            String depId = entry.getKey();
            String minVersion = entry.getValue() == null ? null : entry.getValue()[0];
            Plugin dep = getPlugin(depId);
            if (dep == null) {
                return "requires plugin " + depId + " (not installed)";
            }
            if (!dep.enabled) {
                return "requires plugin " + depId + " (disabled)";
            }
            if (minVersion != null && !checkVersionConstraint(">=" + minVersion, dep.version)) {
                return "requires plugin " + depId + " >= " + minVersion
                        + " (installed " + dep.version + ")";
            }
        }
        return null;
    }

    private boolean loadPluginInternal(Plugin p) {
        String knownPath = p.id == null ? null : knownPaths.get(p.id);
        if (knownPath == null) {
            p.loaded = false;
            p.loadError = "unknown plugin";
            return false;
        }
        if (!knownPath.equals(p.path)) {
            FileLog.w("PluginsController: path of " + p.id + " was changed outside the engine, restored");
            p.path = knownPath;
        }
        String missingDependency = checkRequiredPlugins(p);
        if (missingDependency != null) {
            p.loaded = false;
            p.loadError = missingDependency;
            return false;
        }
        String result = PythonPluginsEngine.getInstance().loadPlugin(p);
        try {
            JSONObject root = new JSONObject(result);
            if (root.optBoolean("ok")) {
                p.loaded = true;
                p.loadError = null;
                p.loadDebug = null;
                p.hasSettings = root.optBoolean("has_settings", false);
                JSONObject handles = root.optJSONObject("handles");
                p.handlesAppEvent = handles == null || handles.optBoolean("app_event", true);
                p.handlesPreRequest = handles == null || handles.optBoolean("pre_request", true);
                p.handlesPostRequest = handles == null || handles.optBoolean("post_request", true);
                p.handlesUpdate = handles == null || handles.optBoolean("update", true);
                p.handlesUpdates = handles == null || handles.optBoolean("updates", true);
                p.handlesSendMessage = handles == null || handles.optBoolean("send_message", true);
                notifyPluginSettings(p.id, p.hasSettings);
                return true;
            }
            p.loaded = false;
            p.loadError = root.optString("error", "load failed");
            p.loadDebug = root.optString("debug", null);
            return false;
        } catch (Exception e) {
            p.loaded = false;
            p.loadError = "load failed";
            return false;
        }
    }

    private void unloadAll() {
        List<Plugin> snapshot = getPluginsSnapshot();
        for (Plugin p : snapshot) {
            if (p.loaded) {
                unregisterPluginHooks(p.id);
                PythonPluginsEngine.getInstance().unload(p);
            }
        }
        sendMessageHooks.clear();
        requestHooks.clear();
        requestHooksSubstring.clear();
        updateHooks.clear();
        updatesContainerHooks.clear();
        hookPriorities.clear();
        invalidateHookTargets();
        synchronized (menuItems) {
            menuItems.clear();
        }
    }

    public boolean setPluginEnabled(String id, boolean enabled) {
        if (PluginSinkGate.refuseFromPlugin("setPluginEnabled", true)) {
            return false;
        }
        Plugin p = getPlugin(id);
        if (p == null) {
            return false;
        }
        preferences.edit().putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + id, enabled).apply();
        p.enabled = enabled;
        if (!PythonPluginsEngine.getInstance().isStarted()) {
            return true;
        }
        if (enabled && !p.loaded && !isSafeMode()) {
            return loadPluginInternal(p);
        } else if (!enabled && p.loaded) {
            unregisterPluginHooks(id);
            final PluginToggleTrace trace = PluginToggleTrace.current();
            if (trace != null) {
                trace.mark("hooks removed");
            }
            PythonPluginsEngine.getInstance().unload(p);
        }
        return true;
    }

    public void setPluginEnabled(String id, boolean enabled, Utilities.Callback<String> callback) {
        final PluginToggleTrace trace = PluginToggleTrace.start(id, enabled);
        final Plugin requested = id == null ? null : getPlugin(id);
        PythonPluginsEngine.getInstance().runOnEngine(() -> {
            trace.enterEngine();
            boolean ok;
            try {
                if (!enabled && requested != null && getPlugin(id) == null) {
                    ok = disableDetached(requested);
                } else {
                    ok = setPluginEnabled(id, enabled);
                }
            } finally {
                trace.leaveEngine();
            }
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    callback.run(ok ? null : id);
                    trace.finish(ok);
                });
            } else {
                trace.finish(ok);
            }
        });
    }

    private boolean disableDetached(Plugin p) {
        preferences.edit().putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + p.id, false).apply();
        p.enabled = false;
        if (p.loaded && PythonPluginsEngine.getInstance().isStarted()) {
            unregisterPluginHooks(p.id);
            PythonPluginsEngine.getInstance().unload(p);
        }
        return true;
    }

    private Plugin restoreDetached(String id) {
        String path = knownPaths.get(id);
        if (path == null || !new File(path).isFile()) {
            return null;
        }
        Plugin fresh = readPluginMetadata(new File(path));
        if (fresh == null || !id.equals(fresh.id)) {
            return null;
        }
        fresh.enabled = preferences.getBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + id, true);
        synchronized (this) {
            Plugin raced = plugins.get(id);
            if (raced != null) {
                return raced;
            }
            plugins.put(id, fresh);
            return fresh;
        }
    }

    public void rescanPlugins(Runnable onDone) {
        PythonPluginsEngine.getInstance().runOnEngine(() -> {
            rescanPlugins();
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        });
    }

    public void reloadPlugin(String id, Runnable onDone) {
        PythonPluginsEngine.getInstance().runOnEngine(() -> {
            reloadPlugin(id);
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        });
    }

    public void reloadPlugin(String id) {
        if (PluginSinkGate.refuseForeign(id, "reloadPlugin")) {
            return;
        }
        if (id == null || !PythonPluginsEngine.getInstance().isStarted()) {
            return;
        }
        Plugin p = getPlugin(id);
        if (p == null) {
            p = restoreDetached(id);
        }
        if (p == null) {
            return;
        }
        if (p.loaded) {
            unregisterPluginHooks(id);
            PythonPluginsEngine.getInstance().unload(p);
        }
        Plugin fresh = readPluginMetadata(new File(p.path));
        if (fresh != null && fresh.loadError == null) {
            p.name = fresh.name;
            p.description = fresh.description;
            p.author = fresh.author;
            p.version = fresh.version;
            p.icon = fresh.icon;
            p.requirements = fresh.requirements;
            p.permissions = fresh.permissions;
            p.permissionsDeclared = fresh.permissionsDeclared;
            p.loadError = null;
            loadPluginInternal(p);
        } else if (fresh != null) {
            p.loadError = fresh.loadError;
        }
    }


    public interface InstallCallback {
        void onResult(boolean ok, String error, Plugin plugin);
    }

    private boolean awaitEngineStarted() {
        PythonPluginsEngine engine = PythonPluginsEngine.getInstance();
        if (engine.isStarted()) {
            return true;
        }
        final Object lock = new Object();
        final boolean[] started = {false};
        final boolean[] done = {false};
        engine.ensureStarted(appContext, ok -> {
            synchronized (lock) {
                started[0] = ok;
                done[0] = true;
                lock.notify();
            }
        });
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 30_000;
            while (!done[0]) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) {
                    break;
                }
                try {
                    lock.wait(wait);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return started[0];
    }

    public void readMetadataAsync(File source, org.telegram.messenger.Utilities.Callback<Plugin> callback) {
        fileExecutor.execute(() -> {
            Plugin plugin = null;
            try {
                if (awaitEngineStarted()) {
                    plugin = readPluginMetadata(source);
                }
            } catch (Throwable t) {
                FileLog.e("PluginsController: metadata preview failed", t);
            }
            if (callback != null) {
                callback.run(plugin);
            }
        });
    }

    public void installPlugin(File source, InstallCallback callback) {
        installPlugin(source, true, callback);
    }

    public void installPlugin(File source, boolean enable, InstallCallback callback) {
        installPlugin(source, enable, false, callback);
    }

    public void installPlugin(File source, boolean enable, boolean keepEnabledState, InstallCallback callback) {
        final String installer = PluginSinkGate.callingPlugin();
        final boolean installerTrusted = installer != null
                && PluginPermissions.has(installer, PluginPermissions.HOOKS);
        fileExecutor.execute(() -> {
            PythonPluginsEngine engine = PythonPluginsEngine.getInstance();
            if (!awaitEngineStarted()) {
                deliver(callback, false, "Python engine failed to start", null);
                return;
            }
            String json = engine.readMetadataJson(source.getAbsolutePath());
            if (json == null) {
                deliver(callback, false, "failed to read metadata", null);
                return;
            }
            try {
                JSONObject root = new JSONObject(json);
                if (!root.optBoolean("ok")) {
                    deliver(callback, false, root.optString("error", "invalid plugin"), null);
                    return;
                }
                String id = root.getJSONObject("meta").optString("id");
                if (!PLUGIN_ID.matcher(id).matches()) {
                    deliver(callback, false, "invalid plugin id", null);
                    return;
                }
                String srcName = source.getName().toLowerCase();
                String ext = PluginsConstants.PLUGIN_EXT_PY;
                if (srcName.endsWith(PluginsConstants.PLUGIN_EXT_MZPACK)) {
                    ext = PluginsConstants.PLUGIN_EXT_MZPACK;
                } else if (srcName.endsWith(PluginsConstants.PLUGIN_EXT_EAF)) {
                    ext = PluginsConstants.PLUGIN_EXT_EAF;
                }
                File dest = new File(getPluginsDir(), id + ext);
                copyFile(source, dest);

                Plugin existing = getPlugin(id);
                if (installer != null && !installerTrusted && !installer.equals(id)) {
                    PluginPermissions.clear(id);
                    PluginTrustLevel.clear(id);
                }
                final boolean enabled = keepEnabledState && existing != null ? existing.enabled : enable;
                if (existing != null && existing.loaded) {
                    unregisterPluginHooks(id);
                    engine.unload(existing);
                }
                for (String otherExt : PLUGIN_FILE_EXTS) {
                    File stale = new File(getPluginsDir(), id + otherExt);
                    if (!otherExt.equals(ext) && stale.isFile() && !stale.delete()) {
                        FileLog.w("PluginsController: failed to remove " + stale.getName());
                    }
                }
                Plugin p = readPluginMetadata(dest);
                if (p == null) {
                    deliver(callback, false, "metadata parse error", null);
                    return;
                }
                p.enabled = enabled;
                preferences.edit().putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + id, enabled).apply();
                if (!PluginPermissions.hasRecord(id)) {
                    markUnconsented(id);
                }
                synchronized (this) {
                    knownPaths.put(id, dest.getAbsolutePath());
                    plugins.put(id, p);
                }
                if (enabled && !isSafeMode()) {
                    loadPluginInternal(p);
                }
                if (p.loadError != null) {
                    final String error = p.loadError;
                    uninstallPlugin(id);
                    deliver(callback, false, error, null);
                    return;
                }
                // The plugin list listens for this: a plugin installed from a file shows up at once.
                notifyPluginsChanged();
                deliver(callback, true, null, p);
            } catch (Exception e) {
                FileLog.e("PluginsController: install failed", e);
                deliver(callback, false, e.getMessage(), null);
            }
        });
    }

    public boolean uninstallPlugin(String id) {
        if (PluginSinkGate.refuseFromPlugin("uninstallPlugin", true)) {
            return false;
        }
        Plugin p = getPlugin(id);
        if (p == null) {
            return false;
        }
        if (p.loaded) {
            unregisterPluginHooks(id);
            PythonPluginsEngine.getInstance().unload(p);
        }
        PythonPluginsEngine.getInstance().uninstallPlugin(id);
        synchronized (this) {
            plugins.remove(id);
            knownPaths.remove(id);
        }
        preferences.edit().remove(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + id).apply();
        PluginPermissions.clear(id);
        PluginTrustLevel.clear(id);
        PluginCapabilityScan.clear(id);
        setPluginPinned(id, false);
        PythonPluginsEngine.getInstance().forgetAudit(id);
        File f = new File(p.path);
        File prefsFile = new File(appContext.getFilesDir().getParentFile(),
                "shared_prefs/" + PluginsConstants.SETTINGS_PREFS_PREFIX + id + ".xml");
        if (prefsFile.exists()) {
            prefsFile.delete();
        }
        PythonPluginsEngine.getInstance().invalidateSettingsMirror(id);
        final boolean deleted = f.delete();
        notifyPluginsChanged();
        return deleted;
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    private static void deliver(InstallCallback callback, boolean ok, String error, Plugin plugin) {
        if (callback == null) {
            return;
        }
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                callback.onResult(ok, error, plugin));
    }


    private SharedPreferences pluginPrefs(String pluginId) {
        String caller = PluginSinkGate.callingPlugin();
        if (caller != null && !caller.equals(pluginId)
                && !PluginPermissions.has(caller, PluginPermissions.HOOKS)) {
            FileLog.w("PluginsController: plugin " + caller + " asked for the settings of " + pluginId);
            SharedPreferences foreign = appContext.getSharedPreferences(FOREIGN_SETTINGS_PREFS, Context.MODE_PRIVATE);
            foreign.edit().clear().commit();
            return foreign;
        }
        return appContext.getSharedPreferences(
                PluginsConstants.SETTINGS_PREFS_PREFIX + pluginId, Context.MODE_PRIVATE);
    }

    private static final String FOREIGN_SETTINGS_PREFS = "mzgram_plugin_foreign_settings";

    public String getPluginSettingJson(String pluginId, String key) {
        if (appContext == null) {
            return null;
        }
        return pluginPrefs(pluginId).getString(key, null);
    }

    public void setPluginSettingJson(String pluginId, String key, String jsonValue, boolean reloadSettings) {
        if (appContext == null) {
            return;
        }
        pluginPrefs(pluginId).edit().putString(key, jsonValue).apply();
        PythonPluginsEngine.getInstance().invalidateSettingsMirror(pluginId);
        if (reloadSettings) {
            reloadSettingsScreen(pluginId);
        }
    }

    private static void notifyPluginSettings(String pluginId, boolean hasSettings) {
        if (pluginId == null || pluginId.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(
                hasSettings ? NotificationCenter.pluginSettingsRegistered
                        : NotificationCenter.pluginSettingsUnregistered, pluginId));
    }

    public boolean hasPluginSettingsPreferences(String pluginId) {
        return appContext != null && pluginId != null && !pluginPrefs(pluginId).getAll().isEmpty();
    }

    public void clearPluginSettingsPreferences(String pluginId) {
        if (appContext == null || pluginId == null) {
            return;
        }
        pluginPrefs(pluginId).edit().clear().apply();
        PythonPluginsEngine.getInstance().invalidateSettingsMirror(pluginId);
        reloadSettingsScreen(pluginId);
    }

    public String exportPluginSettings(String pluginId) {
        if (appContext == null) {
            return "{}";
        }
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, ?> e : pluginPrefs(pluginId).getAll().entrySet()) {
            try {
                obj.put(e.getKey(), e.getValue());
            } catch (Exception ignored) {
            }
        }
        return obj.toString();
    }

    public void replacePluginSettings(String pluginId, String json) {
        if (appContext == null) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(json);
            SharedPreferences.Editor editor = pluginPrefs(pluginId).edit();
            editor.clear();
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                editor.putString(key, obj.optString(key));
            }
            editor.apply();
            PythonPluginsEngine.getInstance().invalidateSettingsMirror(pluginId);
        } catch (Exception e) {
            FileLog.e("PluginsController: replaceSettings failed for " + pluginId, e);
        }
    }

    public void addXposedHook(String pluginId, de.robv.android.xposed.XC_MethodHook.Unhook unhook) {
        if (PluginSinkGate.refuseForeign(pluginId, "addXposedHook")) {
            return;
        }
        org.mzgram.plugins.xposed.XposedHooks.addPluginUnhook(pluginId, unhook);
    }

    @Override
    public void addXposedHooks(String pluginId,
                               java.util.ArrayList<de.robv.android.xposed.XC_MethodHook.Unhook> unhooks) {
        if (PluginSinkGate.refuseForeign(pluginId, "addXposedHooks")) {
            return;
        }
        if (unhooks == null) {
            return;
        }
        for (de.robv.android.xposed.XC_MethodHook.Unhook unhook : unhooks) {
            addXposedHook(pluginId, unhook);
        }
    }

    public void removeXposedHook(String pluginId, de.robv.android.xposed.XC_MethodHook.Unhook unhook) {
        if (PluginSinkGate.refuseForeign(pluginId, "removeXposedHook")) {
            return;
        }
        org.mzgram.plugins.xposed.XposedHooks.removePluginUnhook(pluginId, unhook);
    }

    public void loadPluginSettings(String pluginId) {
        reloadSettingsScreen(pluginId);
    }

    public static Map<String, PythonPluginsEngine> getEngines() {
        Map<String, PythonPluginsEngine> engines = new ConcurrentHashMap<>();
        engines.put(PluginsConstants.PYTHON, PythonPluginsEngine.getInstance());
        return engines;
    }

    public static void openPluginSettings(String pluginId) {
        openPluginSettings(pluginId, null);
    }

    public static String settingsLink(String pluginId, String linkAlias) {
        String link = "https://t.me/mzSettings?p=" + android.net.Uri.encode(pluginId);
        return TextUtils.isEmpty(linkAlias) ? link : link + "&s=" + android.net.Uri.encode(linkAlias);
    }

    public static boolean isSettingsLink(String path, android.net.Uri data) {
        return path != null && data != null
                && ("mzsettings".equalsIgnoreCase(path) || "mzgram".equalsIgnoreCase(path))
                && !TextUtils.isEmpty(data.getQueryParameter("p"));
    }

    public static void openSettingsLink(android.net.Uri data) {
        String pluginId = data.getQueryParameter("p");
        String linkAlias = data.getQueryParameter("s");
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment == null) {
                return;
            }
            PluginsController controller = getInstance();
            if (!controller.isEngineEnabled()) {
                org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                        AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MZPluginLinkEngineDisabled, pluginId)),
                        LocaleController.getString(R.string.Enable), () -> fragment.presentFragment(new org.mzgram.plugins.ui.PluginsActivity())).show();
                return;
            }
            Plugin plugin = controller.getPlugin(pluginId);
            if (plugin == null) {
                org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                        AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MZPluginLinkNotFound, pluginId))).show();
                return;
            }
            if (!controller.hasPluginSettings(pluginId)) {
                org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                        AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MZPluginLinkNoSettings, plugin.getDisplayName()))).show();
                return;
            }
            PythonPluginsEngine.getInstance().openPluginSettings(plugin, fragment, TextUtils.isEmpty(linkAlias) ? null : linkAlias);
        });
    }

    public static void openPluginSettings(String pluginId, String targetSetting) {
        if (pluginId == null || pluginId.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            Plugin plugin = getInstance().getPlugin(pluginId);
            if (plugin == null) {
                return;
            }
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment == null) {
                return;
            }
            PythonPluginsEngine.getInstance().openPluginSettings(plugin, fragment, targetSetting);
        });
    }

    public void importPluginSettings(String pluginId, String json, boolean reloadSettings) {
        if (appContext == null) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(json);
            SharedPreferences.Editor editor = pluginPrefs(pluginId).edit();
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                editor.putString(key, obj.optString(key));
            }
            editor.apply();
            PythonPluginsEngine.getInstance().invalidateSettingsMirror(pluginId);
            if (reloadSettings) {
                reloadSettingsScreen(pluginId);
            }
        } catch (Exception e) {
            FileLog.e("PluginsController: importSettings failed for " + pluginId, e);
        }
    }


    public String getPluginSettingsJson(String pluginId) {
        return PythonPluginsEngine.getInstance().getSettingsJson(pluginId);
    }

    /** A sub-page the plugin builds only when it is opened; owners are the row ids on the way to it. */
    public String getPluginSubPageJson(String pluginId, String[] owners) {
        return PythonPluginsEngine.getInstance().getSubPageJson(pluginId, owners);
    }


    public void init() {
        if (PluginSinkGate.refuseFromPlugin("init", true)) {
            return;
        }
        init(isSafeMode(), null);
    }

    public void init(Runnable onDone) {
        if (PluginSinkGate.refuseFromPlugin("init", true)) {
            return;
        }
        init(isSafeMode(), onDone);
    }

    public void init(boolean startWithSafeMode) {
        if (PluginSinkGate.refuseFromPlugin("init", true)) {
            return;
        }
        init(startWithSafeMode, null);
    }

    public void init(boolean startWithSafeMode, Runnable onDone) {
        if (PluginSinkGate.refuseFromPlugin("init", true)) {
            return;
        }
        setSafeMode(startWithSafeMode);
        if (appContext == null && ApplicationLoader.applicationContext != null) {
            init(ApplicationLoader.applicationContext);
        }
        if (onDone != null) {
            AndroidUtilities.runOnUIThread(onDone);
        }
    }

    public boolean getInitialized() {
        return initialized;
    }

    public void restart() {
        if (PluginSinkGate.refuseFromPlugin("restart", true)) {
            return;
        }
        restart(isSafeMode());
    }

    public void restart(boolean startWithSafeMode) {
        if (PluginSinkGate.refuseFromPlugin("restart", true)) {
            return;
        }
        setSafeMode(startWithSafeMode);
        unloadAll();
        if (!isEngineEnabled() || startWithSafeMode) {
            return;
        }
        PythonPluginsEngine.getInstance().ensureStarted(appContext, ok -> {
            if (ok) {
                rescanAndLoadEnabled();
            }
        });
    }

    public void shutdown() {
        if (PluginSinkGate.refuseFromPlugin("shutdown", true)) {
            return;
        }
        shutdown(null);
    }

    public void shutdown(Runnable onDone) {
        if (PluginSinkGate.refuseFromPlugin("shutdown", true)) {
            return;
        }
        unloadAll();
        if (onDone != null) {
            AndroidUtilities.runOnUIThread(onDone);
        }
    }

    public static void runOnPluginsQueue(Runnable runnable) {
        if (runnable != null) {
            getInstance().fileExecutor.execute(runnable);
        }
    }

    public String getPluginPath(String id) {
        Plugin plugin = getPlugin(id);
        return plugin == null ? null : plugin.path;
    }

    public PythonPluginsEngine getPluginEngine(String pluginId) {
        return PythonPluginsEngine.getInstance();
    }

    public static PythonPluginsEngine engineForFile(File file) {
        if (file == null) {
            return null;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(PluginsConstants.PLUGIN_EXT) || name.endsWith(PluginsConstants.PLUGIN_EXT_PY)
                || name.endsWith(PluginsConstants.PLUGIN_EXT_MZPACK) || name.endsWith(PluginsConstants.PLUGIN_EXT_EAF)) {
            return PythonPluginsEngine.getInstance();
        }
        return null;
    }

    public boolean isPluginEngineAvailable() {
        return PythonPluginsEngine.getInstance().isStarted();
    }

    public boolean isPluginEngineSupported() {
        return true;
    }

    public void notifyPluginsChanged() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.pluginsUpdated));
    }

    public void deletePlugin(String pluginId, Utilities.Callback<String> callback) {
        boolean removed = uninstallPlugin(pluginId);
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(removed ? null : pluginId));
        }
    }

    public void loadPluginSettings() {
        for (Plugin plugin : getPluginsSnapshot()) {
            loadPluginSettings(plugin.id);
        }
    }

    public void invalidatePluginSettings(String pluginId) {
        reloadSettingsScreen(pluginId);
    }

    public boolean hasPluginSettings(String pluginId) {
        String json = getPluginSettingsJson(pluginId);
        return json != null && !json.isEmpty() && !"[]".equals(json.trim());
    }

    public Map<String, ?> getPluginSettingsPreferences(String pluginId) {
        if (appContext == null || pluginId == null) {
            return Collections.emptyMap();
        }
        return pluginPrefs(pluginId).getAll();
    }

    public void clearPluginSettingsPreferences(String pluginId, boolean reloadSettings) {
        if (appContext == null || pluginId == null) {
            return;
        }
        pluginPrefs(pluginId).edit().clear().apply();
        PythonPluginsEngine.getInstance().invalidateSettingsMirror(pluginId);
        if (reloadSettings) {
            reloadSettingsScreen(pluginId);
        }
    }

    public List<Object> getPluginSettingsList(String pluginId) {
        List<Object> items = new ArrayList<>();
        String json = getPluginSettingsJson(pluginId);
        if (json == null || json.isEmpty()) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                items.add(fromJson(array.opt(i)));
            }
        } catch (Exception e) {
            FileLog.e("PluginsController: settings list failed for " + pluginId, e);
        }
        return items;
    }

    public Map<String, List<Object>> getSettings() {
        Map<String, List<Object>> all = new ConcurrentHashMap<>();
        for (Plugin plugin : getPluginsSnapshot()) {
            all.put(plugin.id, getPluginSettingsList(plugin.id));
        }
        return all;
    }

    public boolean getPluginSettingBoolean(String pluginId, String key, boolean defaultValue) {
        Object value = readSetting(pluginId, key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return defaultValue;
    }

    public int getPluginSettingInt(String pluginId, String key, int defaultValue) {
        Object value = readSetting(pluginId, key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        return defaultValue;
    }

    public String getPluginSettingString(String pluginId, String key, String defaultValue) {
        Object value = readSetting(pluginId, key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public void setPluginSetting(String pluginId, String key, Object value) {
        setPluginSettingJson(pluginId, key, toJson(value), true);
    }

    public void setPluginSettingAndTriggerOnChange(String pluginId, String key, Object value,
                                                   com.chaquo.python.PyObject onChangeCallback) {
        String json = toJson(value);
        setPluginSettingJson(pluginId, key, json, true);
        notifySettingChanged(pluginId, key, json);
        if (onChangeCallback != null) {
            try {
                onChangeCallback.call(value);
            } catch (Throwable t) {
                FileLog.e("PluginsController: on_change failed for " + pluginId + "/" + key, t);
            }
        }
    }

    private Object readSetting(String pluginId, String key) {
        String raw = getPluginSettingJson(pluginId, key);
        if (raw == null) {
            return null;
        }
        try {
            return fromJson(new JSONArray("[" + raw + "]").opt(0));
        } catch (Exception e) {
            return raw;
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return JSONObject.quote(String.valueOf(value));
    }

    private static Object fromJson(Object value) {
        if (value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Map<String, Object> map = new HashMap<>();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, fromJson(object.opt(key)));
            }
            return map;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(fromJson(array.opt(i)));
            }
            return list;
        }
        return value;
    }

    public boolean dispatchSettingsCustomClick(String pluginId,
                                                org.telegram.ui.Components.UItem item,
                                                android.view.View view, boolean longClick) {
        return PythonPluginsEngine.getInstance().dispatchSettingsCustomClick(pluginId, item, view, longClick);
    }

    public Object getPluginSettingsCustomContent(String pluginId, String viewId,
                                                  android.content.Context context) {
        return PythonPluginsEngine.getInstance().getSettingsCustomContent(pluginId, viewId, context);
    }

    public android.view.View getPluginSettingsCustomView(String pluginId, String viewId,
                                                         android.content.Context context) {
        return PythonPluginsEngine.getInstance().getSettingsCustomView(pluginId, viewId, context);
    }

    public void notifySettingChanged(String pluginId, String key, String jsonValue) {
        PythonPluginsEngine.getInstance().notifySettingChanged(pluginId, key, jsonValue);
    }

    public void dispatchSettingClick(String pluginId, String callbackId, android.view.View view) {
        PythonPluginsEngine.getInstance().dispatchSettingClick(pluginId, callbackId, view);
    }

    public void addSettingsReloadListener(String pluginId, Runnable listener) {
        if (pluginId == null || listener == null) {
            return;
        }
        settingsReloadListeners
                .computeIfAbsent(pluginId, id -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    public void removeSettingsReloadListener(String pluginId, Runnable listener) {
        if (pluginId == null || listener == null) {
            return;
        }
        List<Runnable> list = settingsReloadListeners.get(pluginId);
        if (list == null) {
            return;
        }
        list.remove(listener);
        if (list.isEmpty()) {
            settingsReloadListeners.remove(pluginId);
        }
    }

    public void reloadSettingsScreen(String pluginId) {
        PythonPluginsEngine.getInstance().invalidateSettingsJson(pluginId);
        List<Runnable> list = settingsReloadListeners.get(pluginId);
        if (list == null) {
            return;
        }
        for (Runnable r : list) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(r);
        }
    }


    public void registerSendMessageHook(String pluginId, int priority) {
        if (PluginSinkGate.refuseForeign(pluginId, "registerSendMessageHook")) {
            return;
        }
        if (!PluginPermissions.check(pluginId, PluginPermissions.MESSAGES_SEND,
                "send-message hook")) {
            return;
        }
        sendMessageHooks.put(pluginId, priority);
        sendTargetsCache.invalidate();
    }

    public void registerRequestHook(String pluginId, String requestName, boolean matchSubstring, int priority) {
        if (PluginSinkGate.refuseForeign(pluginId, "registerRequestHook")) {
            return;
        }
        if (pluginId == null || requestName == null || requestName.isEmpty()) {
            return;
        }
        if (!PluginPermissions.check(pluginId, PluginPermissions.MESSAGES_READ,
                "request hook " + requestName)) {
            return;
        }
        Map<String, List<String>> target;
        if (matchSubstring) {
            target = requestHooksSubstring;
        } else if (requestName.startsWith("TL_updates") || requestName.startsWith("TL_updateShort")) {
            target = updatesContainerHooks;
        } else if (requestName.startsWith("TL_update")) {
            target = updateHooks;
        } else {
            target = requestHooks;
        }
        synchronized (target) {
            List<String> list = target.computeIfAbsent(requestName, k -> new ArrayList<>());
            if (!list.contains(pluginId)) {
                list.add(pluginId);
            }
        }
        hookPriorities.merge(priorityKey(requestName, pluginId), priority, Math::max);
        invalidateHookTargets();
    }

    private static String priorityKey(String hookName, String pluginId) {
        return hookName + '\u0001' + pluginId;
    }

    private int hookPriority(String hookName, String pluginId) {
        Integer stored = hookPriorities.get(priorityKey(hookName, pluginId));
        return stored != null ? stored : 0;
    }

    private static List<String> byPriority(Map<String, Integer> priorities) {
        List<String> ordered = new ArrayList<>(priorities.keySet());
        ordered.sort(Comparator
                .comparingInt((String id) -> priorities.get(id)).reversed()
                .thenComparing(Comparator.<String>naturalOrder()));
        return ordered;
    }



    public void unregisterRequestHook(String pluginId, String requestName) {
        if (PluginSinkGate.refuseForeign(pluginId, "unregisterRequestHook")) {
            return;
        }
        if (pluginId == null || requestName == null) {
            return;
        }
        synchronized (requestHooks) {
            dropPluginFromKey(requestHooks, requestName, pluginId);
        }
        synchronized (requestHooksSubstring) {
            dropPluginFromKey(requestHooksSubstring, requestName, pluginId);
        }
        synchronized (updateHooks) {
            dropPluginFromKey(updateHooks, requestName, pluginId);
        }
        synchronized (updatesContainerHooks) {
            dropPluginFromKey(updatesContainerHooks, requestName, pluginId);
        }
        hookPriorities.remove(priorityKey(requestName, pluginId));
        invalidateHookTargets();
    }

    public void unregisterSendMessageHook(String pluginId) {
        if (PluginSinkGate.refuseForeign(pluginId, "unregisterSendMessageHook")) {
            return;
        }
        if (pluginId != null) {
            sendMessageHooks.remove(pluginId);
            sendTargetsCache.invalidate();
        }
    }

    private static void dropPluginFromKey(Map<String, List<String>> hooks, String key,
                                          String pluginId) {
        List<String> list = hooks.get(key);
        if (list == null) {
            return;
        }
        list.remove(pluginId);
        if (list.isEmpty()) {
            hooks.remove(key);
        }
    }

    private static void dropPluginFrom(Map<String, List<String>> hooks, String pluginId) {
        hooks.values().removeIf(list -> {
            list.remove(pluginId);
            return list.isEmpty();
        });
    }

    public void unregisterPluginHooks(String pluginId) {
        if (PluginSinkGate.refuseForeign(pluginId, "unregisterPluginHooks")) {
            return;
        }
        removeHooksByPluginId(pluginId);
        synchronized (menuItems) {
            menuItems.removeIf(item -> item.pluginId.equals(pluginId));
        }
        org.mzgram.plugins.files.FilesControllerJava.unregisterAllForPlugin(pluginId);
        org.mzgram.plugins.intents.IntentsDispatcher.unregisterAllForPlugin(pluginId);
        org.mzgram.plugins.utils.ClassProxyFactory.releaseAllForPlugin(pluginId);
    }

    @Override
    public void removeHooksByPluginId(String pluginId) {
        if (PluginSinkGate.refuseForeign(pluginId, "removeHooksByPluginId")) {
            return;
        }
        if (pluginId == null) {
            return;
        }
        sendMessageHooks.remove(pluginId);
        hookPriorities.keySet().removeIf(k -> k.endsWith('\u0001' + pluginId));
        synchronized (requestHooks) {
            dropPluginFrom(requestHooks, pluginId);
        }
        synchronized (requestHooksSubstring) {
            dropPluginFrom(requestHooksSubstring, pluginId);
        }
        synchronized (updateHooks) {
            dropPluginFrom(updateHooks, pluginId);
        }
        synchronized (updatesContainerHooks) {
            dropPluginFrom(updatesContainerHooks, pluginId);
        }
        invalidateHookTargets();
        org.mzgram.plugins.xposed.XposedHooks.unhookAllForPlugin(pluginId);
    }

    @Override
    public void cleanupPlugin(String pluginId) {
        if (PluginSinkGate.refuseForeign(pluginId, "cleanupPlugin")) {
            return;
        }
        unregisterPluginHooks(pluginId);
        invalidatePluginSettings(pluginId);
    }

    @Override
    public void addEventHook(String pluginId, String hookName, boolean matchSubstring, int priority) {
        if (PluginSinkGate.refuseForeign(pluginId, "addEventHook")) {
            return;
        }
        if (!matchSubstring && PluginsConstants.SEND_MESSAGE_HOOK.equals(hookName)) {
            registerSendMessageHook(pluginId, priority);
            return;
        }
        registerRequestHook(pluginId, hookName, matchSubstring, priority);
    }

    @Override
    public void removeEventHook(String pluginId, String hookName) {
        if (PluginSinkGate.refuseForeign(pluginId, "removeEventHook")) {
            return;
        }
        if (PluginsConstants.SEND_MESSAGE_HOOK.equals(hookName)) {
            unregisterSendMessageHook(pluginId);
            return;
        }
        unregisterRequestHook(pluginId, hookName);
    }


    public void executeOnAppEvent(String event) {
        if (!PythonPluginsEngine.getInstance().isStarted()) {
            return;
        }
        List<Plugin> snapshot = getPluginsSnapshot();
        ArrayList<String> receivers = new ArrayList<>();
        for (Plugin p : snapshot) {
            if (p.loaded && p.handlesAppEvent) {
                receivers.add(p.id);
            }
        }
        if (!receivers.isEmpty()) {
            PythonPluginsEngine.getInstance().runOnEngine(() -> {
                for (String id : receivers) {
                    Plugin p = getPlugin(id);
                    if (p != null && p.loaded) {
                        PythonPluginsEngine.getInstance().callAppEvent(id, event);
                    }
                }
            });
        }
        if (PluginsConstants.EVENT_APP_PAUSE.equals(event)
                || PluginsConstants.EVENT_APP_STOP.equals(event)) {
            PluginsWatchdog wd = getWatchdog();
            if (wd != null) {
                wd.onAppBackgrounded();
                for (Plugin p : snapshot) {
                    if (p.loaded) {
                        wd.noteHealthy(p.id);
                    }
                }
            }
        }
    }

    public boolean hasSendMessageHooks() {
        return !sendMessageHooks.isEmpty();
    }

    private static final ConcurrentHashMap<Class<?>, String> HOOK_NAMES = new ConcurrentHashMap<>();

    public static String hookName(Object object) {
        Class<?> cls = object.getClass();
        String name = HOOK_NAMES.get(cls);
        if (name == null) {
            name = cls.getSimpleName();
            HOOK_NAMES.put(cls, name);
        }
        return name;
    }

    public boolean hasAnyRequestHooks() {
        return !requestHooks.isEmpty() || !requestHooksSubstring.isEmpty();
    }

    public HookResult executeOnSendMessageHook(int account, Object params) {
        if (sendMessageHooks.isEmpty() || !PythonPluginsEngine.getInstance().isStarted()) {
            return HookResult.DEFAULT;
        }
        List<String> sorted = sendTargetsCache.get("send", () -> byPriority(new HashMap<>(sendMessageHooks)));
        HookResult last = HookResult.DEFAULT;
        for (String pluginId : sorted) {
            Plugin p = getPlugin(pluginId);
            if (p == null || !p.loaded || !p.handlesSendMessage) {
                continue;
            }
            HookResult r = PythonPluginsEngine.getInstance().callSendMessageHook(pluginId, account, params);
            if (r.isCancel()) {
                return r;
            }
            if (r.strategy != HookResult.Strategy.DEFAULT) {
                Object replacement = r.replacement(org.telegram.messenger.SendMessagesHelper.SendMessageParams.class);
                if (replacement != null) {
                    params = replacement;
                }
                last = new HookResult(r.strategy, params);
            }
            if (r.isFinal()) {
                break;
            }
        }
        return last;
    }

    public HookResult executePreRequestHook(int account, String requestName, Object request) {
        List<String> targets = findRequestHookTargets(requestName);
        if (targets.isEmpty() || !PythonPluginsEngine.getInstance().isStarted()) {
            return HookResult.DEFAULT;
        }
        HookResult last = HookResult.DEFAULT;
        for (String pluginId : targets) {
            Plugin p = getPlugin(pluginId);
            if (p == null || !p.loaded || !p.handlesPreRequest) {
                continue;
            }
            HookResult r = PythonPluginsEngine.getInstance()
                    .callPreRequestHook(pluginId, account, requestName, request);
            if (r.isCancel()) {
                return r;
            }
            if (r.strategy != HookResult.Strategy.DEFAULT) {
                Object replacement = r.replacement(org.telegram.tgnet.TLObject.class);
                if (replacement != null) {
                    request = replacement;
                }
                last = new HookResult(r.strategy, request);
            }
            if (r.isFinal()) {
                break;
            }
        }
        return last;
    }

    public HookResult executePostRequestHook(int account, String requestName, Object response, Object error) {
        List<String> targets = findRequestHookTargets(requestName);
        if (targets.isEmpty() || !PythonPluginsEngine.getInstance().isStarted()) {
            return HookResult.DEFAULT;
        }
        HookResult last = HookResult.DEFAULT;
        for (String pluginId : targets) {
            Plugin p = getPlugin(pluginId);
            if (p == null || !p.loaded || !p.handlesPostRequest) {
                continue;
            }
            HookResult r = PythonPluginsEngine.getInstance()
                    .callPostRequestHook(pluginId, account, requestName, response, error);
            if (r.isCancel()) {
                return r;
            }
            if (r.strategy != HookResult.Strategy.DEFAULT) {
                Object replacement = r.replacement(org.telegram.tgnet.TLObject.class);
                if (replacement != null) {
                    response = replacement;
                }
                last = new HookResult(r.strategy, response);
            }
            if (r.isFinal()) {
                break;
            }
        }
        return last;
    }

    private List<String> findRequestHookTargets(String requestName) {
        return requestTargetsCache.get(requestName, () -> resolveRequestHookTargets(requestName));
    }


    public boolean hasAnyUpdateHooks() {
        return !updateHooks.isEmpty() || !requestHooksSubstring.isEmpty();
    }

    public boolean hasAnyUpdatesContainerHooks() {
        return !updatesContainerHooks.isEmpty() || !requestHooksSubstring.isEmpty();
    }

    public HookResult executeOnUpdateHook(int account, String updateName, Object update) {
        List<String> targets = updateTargetsCache.get(updateName, () -> resolveHookTargets(updateName, updateHooks));
        if (targets.isEmpty() || !PythonPluginsEngine.getInstance().isStarted()) {
            return HookResult.DEFAULT;
        }
        HookResult last = HookResult.DEFAULT;
        for (String pluginId : targets) {
            Plugin p = getPlugin(pluginId);
            if (p == null || !p.loaded || !p.handlesUpdate) {
                continue;
            }
            HookResult r = PythonPluginsEngine.getInstance()
                    .callUpdateHook(pluginId, account, updateName, update);
            if (r.isCancel()) {
                return r;
            }
            if (r.strategy != HookResult.Strategy.DEFAULT) {
                Object replacement = r.replacement(org.telegram.tgnet.TLRPC.Update.class);
                if (replacement != null) {
                    update = replacement;
                }
                last = new HookResult(r.strategy, update);
            }
            if (r.isFinal()) {
                break;
            }
        }
        return last;
    }

    public HookResult executeOnUpdatesHook(int account, String containerName, Object updates) {
        List<String> targets = updatesTargetsCache.get(containerName, () -> resolveHookTargets(containerName, updatesContainerHooks));
        if (targets.isEmpty() || !PythonPluginsEngine.getInstance().isStarted()) {
            return HookResult.DEFAULT;
        }
        HookResult last = HookResult.DEFAULT;
        for (String pluginId : targets) {
            Plugin p = getPlugin(pluginId);
            if (p == null || !p.loaded || !p.handlesUpdates) {
                continue;
            }
            HookResult r = PythonPluginsEngine.getInstance()
                    .callUpdatesHook(pluginId, account, containerName, updates);
            if (r.isCancel()) {
                return r;
            }
            if (r.strategy != HookResult.Strategy.DEFAULT) {
                Object replacement = r.replacement(org.telegram.tgnet.TLRPC.Updates.class);
                if (replacement != null) {
                    updates = replacement;
                }
                last = new HookResult(r.strategy, updates);
            }
            if (r.isFinal()) {
                break;
            }
        }
        return last;
    }

    public List<MenuItemRecord> getMenuItemsFor(MenuItemRecord.MenuType type) {
        List<MenuItemRecord> result = new ArrayList<>();
        synchronized (menuItems) {
            for (MenuItemRecord item : menuItems) {
                if (item.menuType == type) {
                    result.add(item);
                }
            }
        }
        result.sort((a, b) -> Integer.compare(b.priority, a.priority));
        return result;
    }


    public void dispatchMenuClick(String pluginId, String itemId, java.util.Map<String, Object> context) {
        MenuItemRecord target = null;
        synchronized (menuItems) {
            for (MenuItemRecord item : menuItems) {
                if (item.pluginId.equals(pluginId) && item.itemId.equals(itemId)) {
                    target = item;
                    break;
                }
            }
        }
        if (target == null || target.onClick == null || org.telegram.ui.Wallet.WalletPluginFreeze.isFrozen()) {
            return;
        }
        PluginsWatchdog wd = getWatchdog();
        wd.notePluginEnter(pluginId);
        try {
            target.onClick.callAttr("__call__", context);
        } catch (Throwable t) {
            wd.handlePluginError(pluginId, t);
        } finally {
            wd.notePluginExit(pluginId);
        }
    }


    private static final AtomicLong generatedMenuItemId = new AtomicLong();

    public String registerMenuItem(String pluginId, String jsonMenuItem,
                                   com.chaquo.python.PyObject onClick) {
        if (PluginSinkGate.refuseForeign(pluginId, "registerMenuItem")) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(jsonMenuItem);
            String requestedId = obj.optString("item_id");
            MenuItemRecord.MenuType menuType = MenuItemRecord.MenuType.fromString(obj.optString("menu_type"));
            String text = obj.optString("text");
            String subtext = JsonUtils.optStringOrNull(obj, "subtext");
            String icon = JsonUtils.optStringOrNull(obj, "icon");
            String condition = JsonUtils.optStringOrNull(obj, "condition");
            int priority = obj.optInt("priority", 0);
            final String finalItemId;
            synchronized (menuItems) {
                String itemId = requestedId;
                if (itemId == null || itemId.isEmpty()) {
                    itemId = findSameMenuItemId(pluginId, menuType, text, subtext, icon, condition, priority);
                }
                if (itemId == null || itemId.isEmpty()) {
                    itemId = pluginId + "_" + generatedMenuItemId.incrementAndGet();
                }
                finalItemId = itemId;
                MenuItemRecord record = new MenuItemRecord(pluginId, itemId, menuType, text, subtext,
                        icon, condition, priority, onClick);
                menuItems.removeIf(i -> i.pluginId.equals(pluginId) && i.itemId.equals(finalItemId));
                menuItems.add(record);
                menuItems.sort(Comparator.comparingInt((MenuItemRecord i) -> i.priority).reversed());
            }
            notifyMenuItemsUpdated();
            return finalItemId;
        } catch (Exception e) {
            FileLog.e("PluginsController: bad menu item json", e);
            return null;
        }
    }

    private String findSameMenuItemId(String pluginId, MenuItemRecord.MenuType menuType, String text,
                                      String subtext, String icon, String condition, int priority) {
        synchronized (menuItems) {
            for (MenuItemRecord item : menuItems) {
                if (item.pluginId.equals(pluginId) && item.menuType == menuType
                        && java.util.Objects.equals(item.text, text)
                        && java.util.Objects.equals(item.subtext, subtext)
                        && java.util.Objects.equals(item.icon, icon)
                        && java.util.Objects.equals(item.condition, condition)
                        && item.priority == priority) {
                    return item.itemId;
                }
            }
        }
        return null;
    }

    @Override
    public boolean removeMenuItem(String pluginId, String itemId) {
        if (PluginSinkGate.refuseForeign(pluginId, "removeMenuItem")) {
            return false;
        }
        boolean removed;
        synchronized (menuItems) {
            removed = menuItems.removeIf(i -> i.pluginId.equals(pluginId) && i.itemId.equals(itemId));
        }
        notifyMenuItemsUpdated();
        return removed;
    }

    @Override
    public void removeMenuItemsByPluginId(String pluginId) {
        if (PluginSinkGate.refuseForeign(pluginId, "removeMenuItemsByPluginId")) {
            return;
        }
        boolean removed;
        synchronized (menuItems) {
            removed = menuItems.removeIf(i -> i.pluginId.equals(pluginId));
        }
        if (removed) {
            notifyMenuItemsUpdated();
        }
    }

    private final Runnable menuItemsUpdatedRunnable = () ->
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                    .postNotificationName(org.telegram.messenger.NotificationCenter.pluginMenuItemsUpdated);

    public void notifyMenuItemsUpdated() {
        AndroidUtilities.cancelRunOnUIThread(menuItemsUpdatedRunnable);
        AndroidUtilities.runOnUIThread(menuItemsUpdatedRunnable, 50);
    }

    private void invalidateHookTargets() {
        sendTargetsCache.invalidate();
        requestTargetsCache.invalidate();
        updateTargetsCache.invalidate();
        updatesTargetsCache.invalidate();
    }

    private List<String> resolveRequestHookTargets(String requestName) {
        return resolveHookTargets(requestName, requestHooks);
    }

    private List<String> resolveHookTargets(String requestName, Map<String, List<String>> exactHooks) {
        Map<String, Integer> priorities = new HashMap<>();
        synchronized (exactHooks) {
            List<String> exact = exactHooks.get(requestName);
            if (exact != null) {
                for (String pluginId : exact) {
                    priorities.merge(pluginId, hookPriority(requestName, pluginId), Math::max);
                }
            }
        }
        synchronized (requestHooksSubstring) {
            for (Map.Entry<String, List<String>> e : requestHooksSubstring.entrySet()) {
                if (requestName != null && requestName.contains(e.getKey())) {
                    for (String pluginId : e.getValue()) {
                        priorities.merge(pluginId, hookPriority(e.getKey(), pluginId), Math::max);
                    }
                }
            }
        }
        return byPriority(priorities);
    }
}
