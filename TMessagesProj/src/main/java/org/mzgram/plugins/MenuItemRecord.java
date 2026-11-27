package org.mzgram.plugins;

import com.chaquo.python.PyObject;

public class MenuItemRecord {

    public enum MenuType {
        MESSAGE_CONTEXT_MENU,
        DRAWER_MENU,
        MAIN_MENU,
        CHAT_ACTION_MENU,
        PROFILE_ACTION_MENU;

        public static MenuType fromString(String s) {
            try {
                return s == null ? MESSAGE_CONTEXT_MENU : valueOf(s);
            } catch (IllegalArgumentException e) {
                return MESSAGE_CONTEXT_MENU;
            }
        }
    }

    public final String pluginId;
    public final String itemId;
    public final MenuType menuType;
    public final String text;
    public final String subtext;
    public final String icon;
    public final String condition;
    public final int priority;
    public final transient PyObject onClick;

    public MenuItemRecord(String pluginId, String itemId, MenuType menuType, String text,
                          String subtext, String icon, String condition, int priority,
                          PyObject onClick) {
        this.pluginId = pluginId;
        this.itemId = itemId;
        this.menuType = menuType;
        this.text = text;
        this.subtext = subtext;
        this.icon = icon;
        this.condition = condition;
        this.priority = priority;
        this.onClick = onClick;
    }
}
