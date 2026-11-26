package org.mzgram.core;

public class DialogConfig {
    public static final String customForumTabPrefix = "customForumTabs_";

    public static String getCustomForumTabsKey(long dialogId) {
        return customForumTabPrefix + dialogId;
    }

    public static boolean isCustomForumTabsEnable(long dialogId) {
        return CoreConfig.getPreferences().getBoolean(getCustomForumTabsKey(dialogId), false);
    }

    public static boolean hasCustomForumTabsConfig(long dialogId) {
        return CoreConfig.getPreferences().contains(getCustomForumTabsKey(dialogId));
    }

    public static void setCustomForumTabsEnable(long dialogId, boolean enable) {
        CoreConfig.getPreferences().edit().putBoolean(getCustomForumTabsKey(dialogId), enable).apply();
    }

    public static void removeCustomForumTabsConfig(long dialogId) {
        CoreConfig.getPreferences().edit().remove(getCustomForumTabsKey(dialogId)).apply();
    }
}
