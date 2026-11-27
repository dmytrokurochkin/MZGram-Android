package org.mzgram.sdk.plugins;

import org.telegram.ui.ActionBar.BaseFragment;

public abstract class PythonPluginsEngine {

    public void showInstallDialog(BaseFragment fragment,
                                  org.mzgram.sdk.plugins.ui.components
                                          .InstallPluginBottomSheet.PluginInstallParams params) {
        if (params == null || android.text.TextUtils.isEmpty(params.getFilePath())) {
            return;
        }
        if (org.mzgram.plugins.PluginInstallHelper.calledFromPlugin()) {
            // A plugin may not install other plugins: only the user adds a plugin, from a file.
            org.telegram.messenger.FileLog.e("plugins: a plugin tried to install " + params.getFilePath() + ", refused");
            return;
        }
        android.app.Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            activity = org.telegram.ui.LaunchActivity.instance;
        }
        if (activity == null) {
            activity = org.telegram.messenger.AndroidUtilities.findActivity(
                    org.telegram.messenger.ApplicationLoader.applicationContext);
        }
        if (activity == null) {
            return;
        }
        final android.app.Activity target = activity;
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                org.mzgram.plugins.PluginInstallHelper.confirmAndInstall(target, params));
    }

    public void loadPlugin(String pluginId, String filePath) {
        loadPlugin(pluginId, filePath, null, null);
    }

    public void loadPlugin(String pluginId, String filePath, Plugin metadata) {
        loadPlugin(pluginId, filePath, metadata, null);
    }

    public void loadPlugin(String pluginId, String filePath, Plugin metadata, Object delegate) {
        if (android.text.TextUtils.isEmpty(pluginId)) {
            return;
        }
        org.mzgram.plugins.PluginsController.getInstance().reloadPlugin(pluginId, null);
    }

    public void loadPluginFromFile(String filePath, Plugin pluginMetadata,
                                   org.telegram.messenger.Utilities.Callback<String> callback) {
        loadPluginFromFile(filePath, pluginMetadata, callback, null);
    }

    public void loadPluginFromFile(String filePath, Plugin pluginMetadata,
                                   org.telegram.messenger.Utilities.Callback<String> callback,
                                   Object delegate) {
        if (android.text.TextUtils.isEmpty(filePath)) {
            if (callback != null) {
                callback.run("empty file path");
            }
            return;
        }
        if (org.mzgram.plugins.PluginInstallHelper.calledFromPlugin()) {
            // A plugin may not install other plugins: only the user adds a plugin, from a file.
            if (callback != null) {
                callback.run("plugins can not install other plugins");
            }
            return;
        }
        org.mzgram.plugins.PluginsController.getInstance().installPlugin(
                new java.io.File(filePath), true, true, (ok, error, plugin) -> {
                    if (callback != null) {
                        callback.run(ok ? null : (android.text.TextUtils.isEmpty(error) ? "install failed" : error));
                    }
                });
    }

    public boolean isPlugin(java.io.File file, org.telegram.messenger.MessageObject messageObject) {
        return org.mzgram.plugins.PluginsController.isPlugin(file, messageObject);
    }

    public void setPluginEnabled(String pluginId, boolean enabled,
                                 org.telegram.messenger.Utilities.Callback<String> callback) {
        if (android.text.TextUtils.isEmpty(pluginId)
                || org.mzgram.plugins.PluginSinkGate.refuseForeign(pluginId, "setPluginEnabled")) {
            return;
        }
        org.mzgram.plugins.PluginsController.getInstance().setPluginEnabled(pluginId, enabled, callback);
    }

    public void openPluginSettings(Plugin plugin, BaseFragment fragment) {
        openPluginSettings(plugin, fragment, null);
    }

    public void openPluginSettings(Plugin plugin, BaseFragment fragment, String targetSetting) {
        if (plugin == null || fragment == null) {
            return;
        }
        fragment.presentFragment(org.mzgram.plugins.ui.PluginSettingsActivity
                .newInstance(plugin.getId(), targetSetting));
    }
}
