package org.mzgram.plugins;

import org.mzgram.sdk.utils.AppUtils;

import org.telegram.messenger.FileLog;

public final class PythonBridge {

    private PythonBridge() {
    }

    private static PluginsController controller() {
        return PluginsController.getInstance();
    }


    public static String getSetting(String pluginId, String key) {
        return controller().getPluginSettingJson(pluginId, key);
    }

    public static void setSetting(String pluginId, String key, String jsonValue, boolean reloadSettings) {
        controller().setPluginSettingJson(pluginId, key, jsonValue, reloadSettings);
    }

    public static String exportSettings(String pluginId) {
        return controller().exportPluginSettings(pluginId);
    }

    public static void importSettings(String pluginId, String json, boolean reloadSettings) {
        controller().importPluginSettings(pluginId, json, reloadSettings);
    }

    public static void replaceSettings(String pluginId, String json) {
        controller().replacePluginSettings(pluginId, json);
    }


    public static void log(String pluginId, String message) {
        FileLog.d("[plugin:" + pluginId + "] " + message);
        PluginLog.append(pluginId, "I", message);
        AppUtils.logPlugin(message);
    }

    public static void logStream(String pluginId, String level, String message) {
        PluginLog.append(pluginId, level, message);
    }


    public static void addRequestHook(String pluginId, String requestName, boolean matchSubstring, int priority) {
        controller().registerRequestHook(pluginId, requestName, matchSubstring, priority);
    }

    public static void addSendMessageHook(String pluginId, int priority) {
        controller().registerSendMessageHook(pluginId, priority);
    }

    public static void removeRequestHook(String pluginId, String requestName) {
        controller().unregisterRequestHook(pluginId, requestName);
    }

    public static void removeSendMessageHook(String pluginId) {
        controller().unregisterSendMessageHook(pluginId);
    }


    public static String addMenuItem(String pluginId, String jsonMenuItem,
                                     com.chaquo.python.PyObject onClick) {
        return controller().registerMenuItem(pluginId, jsonMenuItem, onClick);
    }

    public static void removeMenuItem(String pluginId, String itemId) {
        controller().removeMenuItem(pluginId, itemId);
    }


    public static boolean hasPermission(String pluginId, String permission) {
        return PluginPermissions.has(pluginId, permission);
    }

    public static boolean checkPermission(String pluginId, String permission, String what) {
        return PluginPermissions.check(pluginId, permission, what);
    }

    public static void noteOverlayDenied(String pluginId) {
        PluginDenialNotice.noteOverlay(pluginId);
    }

    public static boolean isUnsafeMode() {
        return PluginPermissions.isUnsafeMode();
    }

    public static String getPluginsDir() {
        return controller().getPluginsDir().getAbsolutePath();
    }

    public static String getSdkVersion() {
        return PluginsConstants.SDK_VERSION;
    }

    public static void reloadSettingsScreen(String pluginId) {
        controller().reloadSettingsScreen(pluginId);
    }
}
