/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The pills of the chat list's search bar (MZGramConfig.pillStack, off by
 * default): which pills are chosen, their order and the one shown last.
 * Only one pill is on screen at a time; a vertical swipe on it shows the
 * next one (ui/mzgram/pills/MZGramPillStackView).
 *
 * The ids are kept in the settings, so they never change; a new pill gets
 * a new id and is added at the end of a saved order. A pill is shown only
 * when chosen in Settings > MZGram > Interface > Pills. Every pill works on
 * the device or with Telegram's own servers only: MZGram makes no network
 * calls to third parties, so there are no weather or exchange rate pills.
 *
 * Numbers in a pill are always short (4.8K, 1.2M), also with "Exact
 * numbers" (MZGramConfig.disableNumberRounding): a pill is 28dp high and a
 * long number would push the text out of it.
 *
 * Idea from exteraless PillStackConfig and PillRegistry (GPL-3.0); written
 * anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.List;

public final class MZGramPills {

    /** Every pill MZGram has, in the order of a fresh install. Filled by the pill features. */
    public static final int[] ALL = {};

    private static final String PREFS = "mzgram_pills";
    private static final String KEY_ORDER = "order";
    private static final String KEY_SHOWN = "shown";
    private static final String KEY_LAST = "last";

    private static final ArrayList<Runnable> listeners = new ArrayList<>();

    private MZGramPills() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isKnown(int id) {
        for (int known : ALL) {
            if (known == id) {
                return true;
            }
        }
        return false;
    }

    /** Ids from a comma list; unknown ids and repeats are dropped. */
    public static ArrayList<Integer> parse(String saved) {
        ArrayList<Integer> list = new ArrayList<>();
        if (TextUtils.isEmpty(saved)) {
            return list;
        }
        for (String part : saved.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (isKnown(id) && !list.contains(id)) {
                    list.add(id);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return list;
    }

    public static String join(List<Integer> ids) {
        return TextUtils.join(",", ids);
    }

    /** A saved order with the pills it does not have added at the end. */
    public static ArrayList<Integer> complete(List<Integer> order) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int id : order) {
            if (isKnown(id) && !list.contains(id)) {
                list.add(id);
            }
        }
        for (int id : ALL) {
            if (!list.contains(id)) {
                list.add(id);
            }
        }
        return list;
    }

    public static ArrayList<Integer> order() {
        return complete(parse(prefs().getString(KEY_ORDER, "")));
    }

    public static void setOrder(List<Integer> order) {
        prefs().edit().putString(KEY_ORDER, join(complete(order))).apply();
        changed();
    }

    public static boolean isShown(int id) {
        return parse(prefs().getString(KEY_SHOWN, "")).contains(id);
    }

    public static void setShown(int id, boolean shown) {
        ArrayList<Integer> list = parse(prefs().getString(KEY_SHOWN, ""));
        if (shown == list.contains(id) || !isKnown(id)) {
            return;
        }
        if (shown) {
            list.add(id);
        } else {
            list.remove((Integer) id);
        }
        prefs().edit().putString(KEY_SHOWN, join(list)).apply();
        changed();
    }

    /** The chosen pills in their order: what the search bar shows. */
    public static ArrayList<Integer> active() {
        ArrayList<Integer> shown = parse(prefs().getString(KEY_SHOWN, ""));
        ArrayList<Integer> list = new ArrayList<>();
        for (int id : order()) {
            if (shown.contains(id)) {
                list.add(id);
            }
        }
        return list;
    }

    /** The pill shown last, so the search bar opens on it again; 0 when none. */
    public static int lastActive() {
        return prefs().getInt(KEY_LAST, 0);
    }

    public static void setLastActive(int id) {
        prefs().edit().putInt(KEY_LAST, id).apply();
    }

    public static void reset() {
        prefs().edit().clear().apply();
        changed();
    }

    /** Called on the UI thread when the chosen pills or their order change. */
    public static void addListener(Runnable listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** The pill bar is turned on or off, or its pills changed: every search bar rebuilds. */
    public static void changed() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }

    /**
     * A short number for a pill: 999, 4.8K, 12K, 1.2M, 3.4B. Always short,
     * whatever MZGramConfig.disableNumberRounding says (LocaleController
     * formatShortNumber gives the exact number with it).
     */
    public static String shortNumber(long number) {
        boolean negative = number < 0;
        // Long.MIN_VALUE has no positive twin; one less does not change what is shown
        long value = number == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(number);
        String text;
        if (value < 1000) {
            text = String.valueOf(value);
        } else {
            String[] suffixes = {"K", "M", "B", "T"};
            long divisor = 1000;
            int index = 0;
            while (index < suffixes.length - 1 && value / divisor >= 1000) {
                divisor *= 1000;
                index++;
            }
            // In whole numbers, rounded down, so 999 999 stays 999K and never becomes 1000K.
            // One decimal below 10 (4.8K), none above (12K); a trailing .0 is dropped
            long tenths = value / (divisor / 10);
            if (tenths < 100) {
                text = tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
            } else {
                text = String.valueOf(value / divisor);
            }
            text += suffixes[index];
        }
        return negative ? "-" + text : text;
    }
}
