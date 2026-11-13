/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The look of the music player: cover, title, blocks, buttons, colors.
 * The values are saved in the "mzgram_player_style" preferences and are
 * changed in the style editor (MZGramPlayerStyleEditor).
 *
 * Port of exteraless PlayerStyle (GPL-3.0), from exteraGram.
 * Changed for MZGram: own preferences instead of AppearanceConfig; the
 * mini player settings are left for the floating mini player.
 */

package org.telegram.ui.mzgram.player;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.List;

public final class MZGramPlayerStyle {

    public static final int COVER_CARD = 0;
    public static final int COVER_WIDE = 1;
    public static final int COVER_FULL = 2;
    public static final int COVER_DISC = 3;

    public static final int BLOCK_PROGRESS = 0;
    public static final int BLOCK_CONTROLS = 1;
    public static final int BLOCK_ACTIONS = 2;

    public static final int ACTIONS_BUTTONS = 0;
    public static final int ACTIONS_ISLAND = 1;

    public static final int COLORS_COVER = 0;
    public static final int COLORS_THEME = 1;

    public static final int BACKGROUND_SOLID = 0;
    public static final int BACKGROUND_GRADIENT = 1;
    public static final int BACKGROUND_BLUR = 2;

    // The floating mini player above the main tabs (MZGramPlayerMiniView)
    public static final int MINI_COVER_ROUNDED = 0;
    public static final int MINI_COVER_ROUND = 1;
    public static final int MINI_COVER_HIDDEN = 2;

    public static final int MINI_SHAPE_ROUNDED = 0;
    public static final int MINI_SHAPE_PILL = 1;
    public static final int MINI_SHAPE_SQUARE = 2;

    public static final int MINI_PROGRESS_RING = 0;
    public static final int MINI_PROGRESS_LINE = 1;
    public static final int MINI_PROGRESS_NONE = 2;
    public static final int MINI_PROGRESS_BORDER = 3;

    public static final int COVER_SIZE_MIN = 60;
    public static final int COVER_SIZE_MAX = 100;
    public static final int COVER_RADIUS_MAX = 48;

    public static final String PREFERENCES = "mzgram_player_style";

    public static final String KEY_COVER_STYLE = "coverStyle";
    public static final String KEY_COVER_SIZE = "coverSize";
    public static final String KEY_COVER_RADIUS = "coverRadius";
    public static final String KEY_DISC_SPIN = "discSpin";
    public static final String KEY_COVER_SWIPE = "coverSwipe";
    public static final String KEY_COVER_EXTEND = "coverExtend";
    public static final String KEY_TITLE_ALIGN = "titleAlign";
    public static final String KEY_TITLE_SIZE = "titleSize";
    public static final String KEY_TITLE_ABOVE = "titleAbove";
    public static final String KEY_SHOW_ACTIONS = "showActions";
    public static final String KEY_ACTIONS_STYLE = "actionsStyle";
    public static final String KEY_SHOW_MODES = "showModes";
    public static final String KEY_SHOW_LIKE = "showLike";
    public static final String KEY_SHOW_SOURCE = "showSource";
    public static final String KEY_WAVY = "wavy";
    public static final String KEY_CONTROLS_SIZE = "controlsSize";
    public static final String KEY_COLORS = "colors";
    public static final String KEY_BACKGROUND = "background";
    public static final String KEY_MINI_COVER = "miniCover";
    public static final String KEY_MINI_SHAPE = "miniShape";
    public static final String KEY_MINI_PROGRESS = "miniProgress";
    public static final String KEY_MINI_COLORS = "miniColors";
    public static final String KEY_MINI_COMPACT = "miniCompact";
    public static final String KEY_MINI_ARTIST = "miniArtist";
    public static final String KEY_MINI_PREVIOUS = "miniPrevious";
    public static final String KEY_MINI_NEXT = "miniNext";
    public static final String KEY_MINI_CLOSE = "miniClose";
    public static final String KEY_MINI_TOP = "miniTop";

    private static final int[] DEFAULT_ORDER = {BLOCK_PROGRESS, BLOCK_CONTROLS, BLOCK_ACTIONS};

    private MZGramPlayerStyle() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES, Activity.MODE_PRIVATE);
    }

    private static int getInt(String key, int def) {
        return preferences().getInt(key, def);
    }

    private static boolean getBoolean(String key, boolean def) {
        return preferences().getBoolean(key, def);
    }

    // The style editor saves its values here
    public static void set(String key, int value) {
        preferences().edit().putInt(key, value).apply();
    }

    public static void set(String key, boolean value) {
        preferences().edit().putBoolean(key, value).apply();
    }

    public static int coverStyle() {
        int value = getInt("coverStyle", COVER_CARD);
        return value < COVER_CARD || value > COVER_DISC ? COVER_CARD : value;
    }

    public static float coverSize() {
        int value = getInt("coverSize", COVER_SIZE_MAX);
        return Math.max(COVER_SIZE_MIN, Math.min(COVER_SIZE_MAX, value)) / 100f;
    }

    public static int coverRadiusDp() {
        int value = getInt("coverRadius", 28);
        return Math.max(0, Math.min(COVER_RADIUS_MAX, value));
    }

    public static boolean discSpin() {
        return getBoolean("discSpin", true);
    }

    public static boolean coverSwipe() {
        return getBoolean("coverSwipe", true);
    }

    public static boolean coverExtend() {
        return getBoolean("coverExtend", true);
    }

    public static boolean titleCentered() {
        return getInt("titleAlign", 0) == 1;
    }

    public static int titleSize() {
        int value = getInt("titleSize", 1);
        return Math.max(0, Math.min(2, value));
    }

    public static boolean titleAbove() {
        int style = coverStyle();
        return (style == COVER_CARD || style == COVER_DISC) && getBoolean("titleAbove", false);
    }

    // The saved choice itself: titleAbove() also looks at the cover style
    public static boolean titleAboveSaved() {
        return getBoolean("titleAbove", false);
    }

    public static int[] order() {
        return parseOrder(preferences().getString("order", ""));
    }

    // Public for the tests: unknown and repeated blocks are skipped, the
    // missing ones go to the end in their usual order.
    public static int[] parseOrder(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return DEFAULT_ORDER.clone();
        }
        List<Integer> result = new ArrayList<>(3);
        for (String part : raw.split(",")) {
            try {
                int block = Integer.parseInt(part.trim());
                if (block >= BLOCK_PROGRESS && block <= BLOCK_ACTIONS && !result.contains(block)) {
                    result.add(block);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        for (int block : DEFAULT_ORDER) {
            if (!result.contains(block)) {
                result.add(block);
            }
        }
        int[] out = new int[result.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = result.get(i);
        }
        return out;
    }

    public static void setOrder(int[] order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(order[i]);
        }
        String value = sb.toString();
        preferences().edit().putString("order", "0,1,2".equals(value) ? "" : value).apply();
    }

    public static boolean showActions() {
        return getBoolean("showActions", true);
    }

    public static boolean actionsIsland() {
        return getInt("actionsStyle", ACTIONS_BUTTONS) == ACTIONS_ISLAND;
    }

    public static boolean showModes() {
        return getBoolean("showModes", true);
    }

    public static boolean showLike() {
        return getBoolean("showLike", true);
    }

    public static boolean showSource() {
        return getBoolean("showSource", true);
    }

    public static boolean wavy() {
        return getBoolean("wavy", true);
    }

    public static int controlsSize() {
        int value = getInt("controlsSize", 1);
        return value < 0 || value > 2 ? 1 : value;
    }

    public static float controlsScale() {
        switch (getInt("controlsSize", 1)) {
            case 0:
                return 0.84f;
            case 2:
                return 1.14f;
            default:
                return 1f;
        }
    }

    public static boolean themeColors() {
        return getInt("colors", COLORS_COVER) == COLORS_THEME;
    }

    public static int background() {
        int value = getInt("background", BACKGROUND_SOLID);
        return value < BACKGROUND_SOLID || value > BACKGROUND_BLUR ? BACKGROUND_SOLID : value;
    }

    private static int miniChoice(String key, int max) {
        int value = getInt(key, 0);
        return value < 0 || value > max ? 0 : value;
    }

    public static int miniCover() {
        return miniChoice(KEY_MINI_COVER, MINI_COVER_HIDDEN);
    }

    public static int miniShape() {
        return miniChoice(KEY_MINI_SHAPE, MINI_SHAPE_SQUARE);
    }

    public static int miniProgress() {
        return miniChoice(KEY_MINI_PROGRESS, MINI_PROGRESS_BORDER);
    }

    // The mini player takes the cover colours unless the theme's are chosen
    public static boolean miniThemeColors() {
        return getInt(KEY_MINI_COLORS, COLORS_THEME) != COLORS_COVER;
    }

    public static boolean miniCompact() {
        return getBoolean(KEY_MINI_COMPACT, false);
    }

    public static boolean miniArtist() {
        return getBoolean(KEY_MINI_ARTIST, true);
    }

    public static boolean miniPrevious() {
        return getBoolean(KEY_MINI_PREVIOUS, false);
    }

    public static boolean miniNext() {
        return getBoolean(KEY_MINI_NEXT, true);
    }

    public static boolean miniClose() {
        return getBoolean(KEY_MINI_CLOSE, true);
    }

    // Docked at the top of the chat list (dragged up) instead of above the tabs
    public static boolean miniTop() {
        return getBoolean(KEY_MINI_TOP, false);
    }

    public static void reset() {
        preferences().edit().clear().apply();
    }
}
