package org.mzgram.plugins;

import java.util.ArrayList;
import java.util.List;

public class Plugin extends org.mzgram.sdk.plugins.Plugin {

    public Plugin() {
    }

    public Plugin(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getId() {
        return id;
    }


    public String id;
    public String name;
    public String description;
    public String author;
    public String version = "1.0";
    public String icon;
    public String engine = PluginsConstants.PYTHON;
    public String appVersion;
    public String sdkVersion;
    public boolean beta;
    public List<String> requirements = new ArrayList<>();

    public java.util.Map<String, String[]> requires = new java.util.LinkedHashMap<>();

    public List<String> permissions = new ArrayList<>();
    public boolean permissionsDeclared;

    public String path;

    public boolean enabled = true;
    public transient boolean loaded;
    public transient boolean hasSettings;
    public String loadError;
    public transient String loadDebug;
    public transient boolean notResponding;
    public transient volatile boolean handlesAppEvent = true;
    public transient volatile boolean handlesPreRequest = true;
    public transient volatile boolean handlesPostRequest = true;
    public transient volatile boolean handlesUpdate = true;
    public transient volatile boolean handlesUpdates = true;
    public transient volatile boolean handlesSendMessage = true;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String value) {
        engine = value;
    }

    public void setName(String value) {
        name = value;
    }

    public void setDescription(String value) {
        description = value;
    }

    public void setAuthor(String value) {
        author = value;
    }

    public void setVersion(String value) {
        version = value;
    }

    public void setIcon(String value) {
        icon = value;
    }

    public void setAppVersion(String value) {
        appVersion = value;
    }

    public void setSdkVersion(String value) {
        sdkVersion = value;
    }

    public void setRequirements(List<String> value) {
        requirements = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getVersion() {
        return version;
    }

    public String getIcon() {
        return icon;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public boolean isEnabled() {
        return enabled && loadError == null;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public Throwable getError() {
        return loadError == null ? null : new Exception(loadError);
    }

    public void setError(String value) {
        loadError = value;
    }

    public boolean hasError() {
        return loadError != null;
    }

    public boolean isNotResponding() {
        return notResponding;
    }

    public void setNotResponding(boolean value) {
        notResponding = value;
    }

    public String getPack() {
        if (icon == null) {
            return null;
        }
        int slash = icon.indexOf('/');
        return slash < 0 ? icon : icon.substring(0, slash);
    }

    public int getIndex() {
        if (icon == null) {
            return 0;
        }
        int slash = icon.indexOf('/');
        if (slash < 0 || slash + 1 >= icon.length()) {
            return 0;
        }
        try {
            return Integer.parseInt(icon.substring(slash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getDisplayName() {
        return name != null ? name : id;
    }

    public String getDisplayDescription() {
        return description;
    }

    public String getSubtitle() {
        StringBuilder sb = new StringBuilder();
        sb.append("v").append(version != null ? version : "1.0");
        // MZGram shows no plugin authors in the interface.
        if (loadError != null) {
            sb.append(" • error");
        }
        return sb.toString();
    }
}
