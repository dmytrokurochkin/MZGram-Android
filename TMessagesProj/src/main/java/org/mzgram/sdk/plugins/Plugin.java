package org.mzgram.sdk.plugins;

import java.util.List;

public abstract class Plugin {

    public abstract String getId();

    public abstract String getName();

    public abstract String getAuthor();

    public abstract String getVersion();

    public abstract String getDescription();

    public abstract String getIcon();

    public abstract String getEngine();

    public abstract String getAppVersion();

    public abstract String getSdkVersion();

    public abstract List<String> getRequirements();

    public abstract String getPack();

    public abstract int getIndex();

    public abstract boolean isEnabled();

    public abstract void setEnabled(boolean enabled);

    public abstract boolean hasError();

    public abstract boolean isNotResponding();

    public boolean getIsNotResponding() {
        return isNotResponding();
    }
}
