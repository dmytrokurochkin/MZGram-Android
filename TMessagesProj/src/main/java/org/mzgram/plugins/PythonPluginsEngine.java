package org.mzgram.plugins;

import android.content.Context;
import android.view.View;

import com.chaquo.python.PyException;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.UItem;

import org.mzgram.plugins.models.CustomSetting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.mzgram.debug.PluginToggleTrace;

public class PythonPluginsEngine extends org.mzgram.sdk.plugins.PythonPluginsEngine {

    private static volatile PythonPluginsEngine instance;

    public final ConcurrentHashMap<String, PyObject> pluginInstances = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, PyObject> getPluginInstances() {
        return pluginInstances;
    }

    public static PythonPluginsEngine getInstance() {
        if (instance == null) {
            synchronized (PythonPluginsEngine.class) {
                if (instance == null) {
                    instance = new PythonPluginsEngine();
                }
            }
        }
        return instance;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "plugins-engine");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private volatile boolean started;
    private volatile boolean startFailed;
    private PyObject loader;

    private PythonPluginsEngine() {
    }

    public void runOnEngine(Runnable runnable) {
        executor.execute(runnable);
    }

    public boolean isStarted() {
        return started;
    }

    public String pluginFromPythonStack() {
        if (!started || loader == null) {
            return null;
        }
        try {
            PyObject owner = loader.callAttr("plugin_frame_owner");
            return owner == null ? null : owner.toJava(String.class);
        } catch (Throwable t) {
            return null;
        }
    }

    public String pathPermission(String pluginId, String path) {
        if (!started || loader == null) {
            return null;
        }
        try {
            PyObject permission = loader.callAttr("java_path_permission", pluginId, path);
            return permission == null ? null : permission.toJava(String.class);
        } catch (Throwable t) {
            return PluginPermissions.FILES;
        }
    }

    public String debugPythonStacks() {
        if (!started || loader == null) {
            return null;
        }
        return loader.callAttr("debug_python_stacks").toJava(String.class);
    }

    public interface StartCallback {
        void onStarted(boolean ok);
    }

    public void ensureStarted(Context appContext, StartCallback callback) {
        if (started || startFailed) {
            if (callback != null) {
                callback.onStarted(started);
            }
            return;
        }
        executor.execute(() -> {
            try {
                if (!Python.isStarted()) {
                    long t0 = System.currentTimeMillis();
                    Python.start(new AndroidPlatform(appContext));
                    FileLog.d("PluginsEngine: Python started in " + (System.currentTimeMillis() - t0) + " ms");
                }
                Python.getInstance().getModule("sys").callAttr("setswitchinterval", 0.001);
                loader = Python.getInstance().getModule("mz_utils.plugin_loader");
                started = true;
                AndroidUtilities.runOnUIThread(PythonThreadState::pinCurrentThread);
                if (PluginsController.getInstance().isDeveloperMode()) {
                    try {
                        loader.callAttr("start_dev_server");
                    } catch (Throwable t) {
                        FileLog.e("PluginsEngine: dev server start failed", t);
                    }
                }
            } catch (Throwable t) {
                startFailed = true;
                FileLog.e("PluginsEngine: failed to start Python", t);
            }
            if (callback != null) {
                callback.onStarted(started);
            }
        });
    }

    public String scanCapabilitiesJson(String path) {
        if (!started) {
            return null;
        }
        try {
            return loader.callAttr("scan_capabilities_json", path).toJava(String.class);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: capability scan failed", t);
            return null;
        }
    }

    public void setUnsafeMode(boolean value) {
        if (PluginSinkGate.refuseFromPlugin("setUnsafeMode", false)) {
            return;
        }
        if (!started) {
            return;
        }
        try {
            loader.callAttr("set_unsafe_mode", value);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: cannot push unsafe mode", t);
        }
    }


    public String getAuditJournalJson(String pluginId) {
        if (!started) {
            return null;
        }
        try {
            return loader.callAttr("get_audit_journal_json", pluginId, 100).toJava(String.class);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: audit journal read failed", t);
            return null;
        }
    }

    public String getAuditProfileJson(String pluginId) {
        if (!started) {
            return null;
        }
        try {
            return loader.callAttr("get_audit_profile_json", pluginId).toJava(String.class);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: audit profile read failed", t);
            return null;
        }
    }

    public void forgetAudit(String pluginId) {
        if (PluginSinkGate.refuseFromPlugin("forgetAudit", false)) {
            return;
        }
        PluginAuditJournal.forget(pluginId);
        if (!started) {
            return;
        }
        try {
            loader.callAttr("forget_audit", pluginId);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: audit forget failed", t);
        }
    }


    public String readMetadataJson(String path) {
        if (!started) {
            return null;
        }
        try {
            return loader.callAttr("read_metadata_json", path).toJava(String.class);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: readMetadata failed for " + path, t);
            return null;
        }
    }


    public String loadPlugin(Plugin plugin) {
        if (PluginSinkGate.refuseFromPlugin("loadPlugin", true)) {
            return "{\"ok\":false,\"error\":\"not allowed\"}";
        }
        dropSettingsJson(plugin.id);
        if (!started) {
            return "{\"ok\":false,\"error\":\"engine not started\"}";
        }
        try {
            PluginSinkGate.install();
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: sink gate install failed", t);
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(plugin.id, true);
        final PluginToggleTrace trace = PluginToggleTrace.current();
        try {
            if (trace != null) {
                trace.beforePython();
            }
            String result = loader.callAttr("load_plugin", plugin.path, plugin.id).toJava(String.class);
            if (trace != null) {
                trace.afterPython(result);
            }
            watchdog.notePluginExit(plugin.id);
            rememberInstance(plugin.id);
            return result;
        } catch (Throwable t) {
            watchdog.handlePluginError(plugin.id, t);
            return "{\"ok\":false,\"error\":" + quote(t.getMessage()) + "}";
        }
    }

    private void rememberInstance(String pluginId) {
        try {
            PyObject value = loader.callAttr("get_plugin_instance", pluginId);
            if (value == null || "None".equals(value.toString())) {
                pluginInstances.remove(pluginId);
            } else {
                pluginInstances.put(pluginId, value);
            }
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: cannot cache instance of " + pluginId, t);
        }
    }

    public void loadPlugins(Object callback) {
        try {
            PluginsController.getInstance().rescanPlugins();
        } catch (Throwable t) {
            FileLog.e("PluginsEngine.loadPlugins: rescan failed", t);
        }
        if (callback instanceof Runnable) {
            try {
                ((Runnable) callback).run();
            } catch (Throwable t) {
                FileLog.e("PluginsEngine.loadPlugins: callback failed", t);
            }
        }
    }

    public void unload(Plugin plugin) {
        if (PluginSinkGate.refuseFromPlugin("unload", true)) {
            return;
        }
        if (plugin == null) {
            return;
        }
        unloadingPlugins.put(plugin.id, plugin);
        try {
            unloadPlugin(plugin.id);
        } finally {
            unloadingPlugins.remove(plugin.id);
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Plugin> unloadingPlugins = new java.util.concurrent.ConcurrentHashMap<>();

    public void unloadPlugin(String pluginId) {
        if (PluginSinkGate.refuseForeign(pluginId, "unloadPlugin")) {
            return;
        }
        if (pluginId == null) {
            return;
        }
        Plugin plugin = unloadingPlugins.get(pluginId);
        if (plugin == null) {
            plugin = PluginsController.getInstance().getPlugin(pluginId);
        }
        if (plugin == null) {
            return;
        }
        dropSettingsJson(plugin.id);
        if (!started) {
            return;
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(plugin.id);
        final PluginToggleTrace trace = PluginToggleTrace.current();
        try {
            if (trace != null) {
                trace.beforePython();
            }
            PyObject result = loader.callAttr("unload_plugin", plugin.id);
            if (trace != null) {
                trace.afterPython(result == null ? null : result.toString());
            }
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: unload failed for " + plugin.id, t);
        } finally {
            watchdog.notePluginExit(plugin.id);
        }
        plugin.loaded = false;
        pluginInstances.remove(plugin.id);
        PluginsController.getInstance().notifyMenuItemsUpdated();
    }

    public void uninstallPlugin(String pluginId) {
        if (PluginSinkGate.refuseFromPlugin("uninstallPlugin", true)) {
            return;
        }
        if (!started) {
            return;
        }
        try {
            loader.callAttr("uninstall_plugin", pluginId);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: uninstall cleanup failed for " + pluginId, t);
        }
    }


    public void callAppEvent(String pluginId, String event) {
        callSimple(pluginId, "call_app_event", event);
    }

    public HookResult callSendMessageHook(String pluginId, int account, Object params) {
        PyObject result = callHook(pluginId, "call_send_message_hook", account, params);
        return resultOf(result);
    }

    public HookResult callPreRequestHook(String pluginId, int account, String requestName, Object request) {
        PyObject result = callHook(pluginId, "call_pre_request_hook", account, requestName, request);
        return resultOf(result);
    }

    public HookResult callPostRequestHook(String pluginId, int account, String requestName, Object response, Object error) {
        PyObject result = callHook(pluginId, "call_post_request_hook", account, requestName, response, error);
        return resultOf(result);
    }

    public HookResult callUpdateHook(String pluginId, int account, String updateName, Object update) {
        PyObject result = callHook(pluginId, "call_update_hook", account, updateName, update);
        return resultOf(result);
    }

    public HookResult callUpdatesHook(String pluginId, int account, String containerName, Object updates) {
        PyObject result = callHook(pluginId, "call_updates_hook", account, containerName, updates);
        return resultOf(result);
    }


    private static final long SETTINGS_JSON_TTL_MS = 500;
    private final java.util.concurrent.ConcurrentHashMap<String, Object[]> settingsJsonCache = new java.util.concurrent.ConcurrentHashMap<>();

    public void invalidateSettingsJson(String pluginId) {
        dropSettingsJson(pluginId);
    }

    private void dropSettingsJson(String pluginId) {
        if (pluginId != null) {
            settingsJsonCache.remove(pluginId);
        }
    }

    public String getSettingsJson(String pluginId) {
        if (!started) {
            return null;
        }
        Object[] cached = settingsJsonCache.get(pluginId);
        long now = android.os.SystemClock.elapsedRealtime();
        if (cached != null && now - (Long) cached[1] < SETTINGS_JSON_TTL_MS) {
            return (String) cached[0];
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(pluginId);
        try {
            String json = loader.callAttr("get_settings_json", pluginId).toJava(String.class);
            if (json != null) {
                settingsJsonCache.put(pluginId, new Object[]{json, now});
            }
            return json;
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: getSettingsJson failed for " + pluginId, t);
            return null;
        } finally {
            watchdog.notePluginExit(pluginId);
        }
    }

    /**
     * One sub-page that {@link #getSettingsJson} left as "sub_page_lazy": the plugin builds it only when it is
     * opened, as in MZGram. owners are the row ids from the root page down to the row that opens it.
     */
    public String getSubPageJson(String pluginId, String[] owners) {
        if (!started || owners == null || owners.length == 0) {
            return null;
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(pluginId);
        try {
            String ownersJson = new org.json.JSONArray(java.util.Arrays.asList(owners)).toString();
            String json = loader.callAttr("get_sub_page_json", pluginId, ownersJson).toJava(String.class);
            return json == null || "null".equals(json) ? null : json;
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: getSubPageJson failed for " + pluginId, t);
            return null;
        } finally {
            watchdog.notePluginExit(pluginId);
        }
    }

    public android.view.View getSettingsCustomView(String pluginId, String viewId,
                                                   android.content.Context context) {
        Object content = getSettingsCustomContent(pluginId, viewId, context);
        return content instanceof android.view.View ? (android.view.View) content : null;
    }

    public Object getSettingsCustomContent(String pluginId, String viewId,
                                           android.content.Context context) {
        if (!started) {
            return null;
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(pluginId);
        try {
            PyObject result = loader.callAttr("get_custom_setting_view", pluginId, viewId, context);
            Object content = result == null ? null : result.toJava(Object.class);
            if (content instanceof CustomSetting) {
                CustomSetting setting = (CustomSetting) content;
                CustomSetting.Factory<?> factory = setting.getFactory();
                if (factory == null) {
                    return setting.getItem();
                }
                UItem.UItemFactory.setup(factory);
                UItem item = factory.create(PluginsController.getInstance().getPlugin(pluginId),
                        setting, setting.getFactoryArgs());
                if (item != null) {
                    item.settingItem = setting;
                }
                return item;
            }
            return content;
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: getSettingsCustomContent failed for " + pluginId, t);
            return null;
        } finally {
            watchdog.notePluginExit(pluginId);
        }
    }

    public boolean dispatchSettingsCustomClick(String pluginId, UItem item, View view, boolean longClick) {
        if (!started || item == null || !(item.settingItem instanceof CustomSetting)) {
            return false;
        }
        CustomSetting.Factory<?> factory = ((CustomSetting) item.settingItem).getFactory();
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        if (factory == null || plugin == null) {
            return false;
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(pluginId);
        try {
            if (longClick) {
                factory.onLongClick(plugin, item, view);
            } else {
                factory.onClick(plugin, item, view);
            }
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: custom setting click failed for " + pluginId, t);
        } finally {
            watchdog.notePluginExit(pluginId);
        }
        return true;
    }

    public void notifySettingChanged(String pluginId, String key, String jsonValue) {
        dropSettingsJson(pluginId);
        callSimple(pluginId, "notify_setting_changed", key, jsonValue);
    }

    public void invalidateSettingsMirror(String pluginId) {
        dropSettingsJson(pluginId);
        if (!started) {
            return;
        }
        try {
            loader.callAttr("invalidate_settings_mirror", pluginId);
        } catch (Throwable t) {
            FileLog.e("PluginsEngine: cannot invalidate the settings mirror of " + pluginId, t);
        }
    }

    public void dispatchSettingClick(String pluginId, String callbackId, android.view.View view) {
        dropSettingsJson(pluginId);
        callSimple(pluginId, "dispatch_setting_click", callbackId, view);
    }


    private void callSimple(String pluginId, String method, Object... args) {
        callHook(pluginId, method, args);
    }

    private PyObject callHook(String pluginId, String method, Object... args) {
        if (!started || org.telegram.ui.Wallet.WalletPluginFreeze.isFrozen()) {
            return null;
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(pluginId);
        String previousRuntime = PluginRuntime.enter(pluginId);
        try {
            Object[] callArgs = new Object[args.length + 1];
            callArgs[0] = pluginId;
            System.arraycopy(args, 0, callArgs, 1, args.length);
            return loader.callAttr(method, callArgs);
        } catch (Throwable t) {
            watchdog.handlePluginError(pluginId, t);
            return null;
        } finally {
            PluginRuntime.exit(previousRuntime);
            watchdog.notePluginExit(pluginId);
        }
    }

    private static HookResult resultOf(PyObject result) {
        if (result == null) {
            return HookResult.DEFAULT;
        }
        try {
            PyObject strategyObject = result.get("strategy");
            if (strategyObject != null) {
                HookResult.Strategy strategy = HookResult.Strategy.fromString(strategyObject.toJava(String.class));
                PyObject value = result.get("value");
                return new HookResult(strategy, value == null ? null : value.toJava(Object.class));
            }
            HookResult.Strategy strategy = HookResult.Strategy.fromString(result.toJava(String.class));
            return strategy == HookResult.Strategy.DEFAULT ? HookResult.DEFAULT : new HookResult(strategy);
        } catch (ClassCastException | PyException e) {
            FileLog.e("PluginsEngine: invalid hook strategy", e);
        }
        return HookResult.DEFAULT;
    }

    private static String quote(String s) {
        if (s == null) {
            return "\"unknown error\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
