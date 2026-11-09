/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Menu items added by plugins, Java side. mzgram_plugins/menus.py hands
 * each item here as JSON (add, remove, removeAll; by name through jclass)
 * and keeps the callback; MZGramPluginMenuUi shows the items of a place in
 * its menu, and a tap goes back to Python through menu_click_json with the
 * context of the tap as JSON (ids and texts, never Java objects).
 *
 * Only items of plugins that are added and turned on are shown. Items live
 * only while the app runs: a plugin adds them again when it starts.
 *
 * Based on exteraless plugins/MenuItemRecord.java and menus/MenuInjector.java
 * (GPL-3.0): the places and fields of an item; written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import org.json.JSONObject;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

public final class MZGramPluginMenus {

    public static final String MESSAGE_CONTEXT_MENU = "MESSAGE_CONTEXT_MENU";
    public static final String DRAWER_MENU = "DRAWER_MENU";
    public static final String MAIN_MENU = "MAIN_MENU";
    public static final String CHAT_ACTION_MENU = "CHAT_ACTION_MENU";
    public static final String PROFILE_ACTION_MENU = "PROFILE_ACTION_MENU";

    /** More items of plugins than this in one place are folded into one "Plugins" item (rule 7.1). */
    public static final int MAX_SHOWN = 2;

    private static final String MODULE = "mzgram_plugins.menus";
    private static final int MAX_TEXT = 64;
    private static final int MAX_ITEMS = 256;
    private static final Pattern ITEM_ID = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private MZGramPluginMenus() {
    }

    /** One menu item of a plugin. */
    public static final class Item {
        public final String pluginId;
        public final String itemId;
        public final String place;
        public final String text;
        /** May be null. */
        public final String subtext;
        /** Name of a drawable of the app, or null. */
        public final String icon;
        public final int priority;
        final long order;

        Item(String pluginId, String itemId, String place, String text, String subtext, String icon, int priority, long order) {
            this.pluginId = pluginId;
            this.itemId = itemId;
            this.place = place;
            this.text = text;
            this.subtext = subtext;
            this.icon = icon;
            this.priority = priority;
            this.order = order;
        }
    }

    // "plugin/item" to the item, in the order they were added
    private static final LinkedHashMap<String, Item> items = new LinkedHashMap<>();
    private static long added;

    private static boolean isPlace(String place) {
        return MESSAGE_CONTEXT_MENU.equals(place) || DRAWER_MENU.equals(place) || MAIN_MENU.equals(place)
                || CHAT_ACTION_MENU.equals(place) || PROFILE_ACTION_MENU.equals(place);
    }

    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        final String line = text.replaceAll("\\s+", " ").trim();
        if (line.isEmpty()) {
            return null;
        }
        return line.length() > MAX_TEXT ? line.substring(0, MAX_TEXT) : line;
    }

    /**
     * Keeps the item described by json ({"item_id", "menu_type", "text",
     * "priority", "icon", "subtext"}); the same item_id replaces the item.
     * Returns the item_id, or null when the item is refused.
     */
    public static String add(String pluginId, String json) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId) || json == null) {
            return null;
        }
        try {
            final JSONObject object = new JSONObject(json);
            final String itemId = object.optString("item_id", "");
            final String place = object.optString("menu_type", "");
            final String text = clean(object.optString("text", null));
            if (!ITEM_ID.matcher(itemId).matches() || !isPlace(place) || text == null) {
                return null;
            }
            final String icon = object.isNull("icon") ? null : object.optString("icon", null);
            final String subtext = object.isNull("subtext") ? null : clean(object.optString("subtext", null));
            final String key = pluginId + "/" + itemId;
            synchronized (items) {
                if (!items.containsKey(key) && items.size() >= MAX_ITEMS) {
                    return null;
                }
                items.put(key, new Item(pluginId, itemId, place, text, subtext, icon, object.optInt("priority", 0), added++));
            }
            return itemId;
        } catch (Exception e) {
            return null;
        }
    }

    public static void remove(String pluginId, String itemId) {
        synchronized (items) {
            items.remove(pluginId + "/" + itemId);
        }
    }

    public static void removeAll(String pluginId) {
        synchronized (items) {
            // An iterator, not removeIf: that needs API 24 and the app runs from 23
            final java.util.Iterator<Item> iterator = items.values().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().pluginId.equals(pluginId)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * The items of a place from plugins that are turned on: higher priority
     * first, then in the order they were added. DRAWER_MENU and MAIN_MENU
     * are one place in MZGram (the menu of the chat list).
     */
    public static List<Item> items(String place) {
        final ArrayList<Item> list = new ArrayList<>();
        synchronized (items) {
            for (Item item : items.values()) {
                if (item.place.equals(place) || isMainMenu(place) && isMainMenu(item.place)) {
                    list.add(item);
                }
            }
        }
        if (list.isEmpty()) {
            return list;
        }
        final ArrayList<Item> shown = new ArrayList<>(list.size());
        for (Item item : list) {
            final MZGramPlugins.Plugin plugin = MZGramPlugins.get(item.pluginId);
            if (plugin != null && plugin.enabled) {
                shown.add(item);
            }
        }
        Collections.sort(shown, (a, b) -> a.priority != b.priority ? Integer.compare(b.priority, a.priority) : Long.compare(a.order, b.order));
        return shown;
    }

    private static boolean isMainMenu(String place) {
        return MAIN_MENU.equals(place) || DRAWER_MENU.equals(place);
    }

    /**
     * Calls the item's on_click in Python with context (a JSON object).
     * onResult gets, on the UI thread, null when it was handled, else the
     * reason it was not.
     */
    public static void click(Item item, JSONObject context, Utilities.Callback<String> onResult) {
        if (item == null) {
            return;
        }
        if (!MZGramPythonRuntime.isAvailable()) {
            if (onResult != null) {
                onResult.run("no Python runtime in this build");
            }
            return;
        }
        final String request;
        try {
            request = new JSONObject()
                    .put("plugin", item.pluginId)
                    .put("item", item.itemId)
                    .put("context", context != null ? context : new JSONObject())
                    .toString();
        } catch (Exception e) {
            if (onResult != null) {
                onResult.run(e.getMessage());
            }
            return;
        }
        MZGramPythonRuntime.call(MODULE, "menu_click_json", request, json -> {
            if (onResult == null) {
                return;
            }
            if (json == null) {
                final String error = MZGramPythonRuntime.getLastError();
                onResult.run(error != null ? error : "Python call failed");
                return;
            }
            try {
                final JSONObject reply = new JSONObject(json);
                if (!reply.optBoolean("ok", false)) {
                    onResult.run(reply.optString("error", "failed"));
                } else if (!reply.optBoolean("handled", false)) {
                    onResult.run("the plugin has no action for this item");
                } else {
                    onResult.run(null);
                }
            } catch (Exception e) {
                onResult.run(e.getMessage());
            }
        });
    }
}
