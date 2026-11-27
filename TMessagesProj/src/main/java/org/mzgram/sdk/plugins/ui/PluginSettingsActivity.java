package org.mzgram.sdk.plugins.ui;

public class PluginSettingsActivity extends org.mzgram.plugins.ui.PluginSettingsActivity {

    public PluginSettingsActivity(org.mzgram.plugins.Plugin plugin) {
        super(plugin != null ? plugin.id : null);
    }

    public PluginSettingsActivity(String pluginId) {
        super(pluginId);
    }
}
