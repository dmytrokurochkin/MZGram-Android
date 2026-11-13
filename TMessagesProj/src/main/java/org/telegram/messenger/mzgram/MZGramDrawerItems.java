/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The items of the MZGram side menu (MZGramDrawer, Settings > MZGram >
 * Interface > Side menu): their order and which ones are hidden. The ids
 * are kept in the settings, so they never change; a new item gets a new id
 * and is added at the end of a saved order. "Full menu" (the chat list menu
 * of Telegram, with the AI sites, plugins, bots and proxy) cannot be hidden,
 * so nothing of that menu is ever out of reach.
 *
 * Idea from exteraless MainMenuItem and MainMenuHelper (GPL-3.0); written
 * anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.List;

public final class MZGramDrawerItems {

    public static final int PROFILE = 1;
    public static final int NEW_GROUP = 2;
    public static final int NEW_CHANNEL = 3;
    public static final int CONTACTS = 4;
    public static final int CALLS = 5;
    public static final int SAVED = 6;
    public static final int ARCHIVE = 7;
    public static final int NIGHT_MODE = 8;
    public static final int SETTINGS = 9;
    public static final int MZGRAM = 10;
    public static final int QR_LOGIN = 11;
    public static final int FULL_MENU = 12;
    public static final int GHOST_MODE = 13;

    /** Every item in the order of a fresh install. */
    public static final int[] DEFAULT_ORDER = {
            PROFILE, NEW_GROUP, NEW_CHANNEL, CONTACTS, CALLS, SAVED, ARCHIVE,
            NIGHT_MODE, GHOST_MODE, SETTINGS, MZGRAM, QR_LOGIN, FULL_MENU
    };

    /** Hidden on a fresh install: the QR button of the header does the same. */
    private static final int[] DEFAULT_HIDDEN = {QR_LOGIN};

    private static final String PREFS = "mzgram_drawer";
    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";

    private MZGramDrawerItems() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isKnown(int id) {
        for (int known : DEFAULT_ORDER) {
            if (known == id) {
                return true;
            }
        }
        return false;
    }

    /** Whether an item can be hidden: all but "Full menu". */
    public static boolean canHide(int id) {
        return id != FULL_MENU;
    }

    /**
     * The ids of a saved list ("3,1,2"): unknown ids, repeats and broken
     * parts are dropped.
     */
    public static ArrayList<Integer> parse(String saved) {
        ArrayList<Integer> result = new ArrayList<>();
        if (TextUtils.isEmpty(saved)) {
            return result;
        }
        for (String part : saved.split(",")) {
            int id;
            try {
                id = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (isKnown(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    public static String join(List<Integer> ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ids.get(i));
        }
        return builder.toString();
    }

    /** A saved order made whole: the items it lacks follow in the default order. */
    public static ArrayList<Integer> complete(List<Integer> saved) {
        ArrayList<Integer> result = new ArrayList<>(saved);
        for (int id : DEFAULT_ORDER) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    /** Every item, in the order of the menu. */
    public static ArrayList<Integer> order() {
        return complete(parse(prefs().getString(KEY_ORDER, null)));
    }

    public static void setOrder(List<Integer> order) {
        prefs().edit().putString(KEY_ORDER, join(complete(parse(join(order))))).apply();
    }

    private static ArrayList<Integer> hidden() {
        SharedPreferences preferences = prefs();
        if (!preferences.contains(KEY_HIDDEN)) {
            ArrayList<Integer> result = new ArrayList<>();
            for (int id : DEFAULT_HIDDEN) {
                result.add(id);
            }
            return result;
        }
        return parse(preferences.getString(KEY_HIDDEN, ""));
    }

    public static boolean isHidden(int id) {
        return canHide(id) && hidden().contains(id);
    }

    public static void setHidden(int id, boolean hide) {
        if (!isKnown(id) || !canHide(id)) {
            return;
        }
        ArrayList<Integer> ids = hidden();
        if (hide && !ids.contains(id)) {
            ids.add(id);
        } else if (!hide) {
            ids.remove(Integer.valueOf(id));
        }
        prefs().edit().putString(KEY_HIDDEN, join(ids)).apply();
    }

    /** The items shown in the menu, in its order. */
    public static ArrayList<Integer> visible() {
        ArrayList<Integer> ids = hidden();
        ArrayList<Integer> result = new ArrayList<>();
        for (int id : order()) {
            if (!canHide(id) || !ids.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    /** Back to the order and the hidden items of a fresh install. */
    public static void reset() {
        prefs().edit().remove(KEY_ORDER).remove(KEY_HIDDEN).apply();
    }
}
