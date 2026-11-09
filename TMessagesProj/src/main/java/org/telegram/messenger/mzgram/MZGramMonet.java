/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Monet themes: the colors of the phone's wallpaper (Material You, Android 12
 * and newer) as three themes beside Telegram's built-in ones: light, dark and
 * black (AMOLED), in two styles, Android's (surfaces as in the system apps)
 * and Telegram's (the layout of Telegram's own themes).
 *
 * The theme files (assets/mzgram_monet_*.attheme) give a palette token in
 * place of a color, for example surface_light or a1_600; Theme reads them
 * through color() while it parses these files and no others. A token is a
 * shade of the system palette (accent1-3, neutral1-2), a Material role, a
 * tone of a palette, a color tuned to the wallpaper, or a fixed color, with
 * optional (s=, l=, a=, t=, c=) changes.
 *
 * Ported from Nagram X MonetHelper (tw.nekomimi.nekogram.helpers, GPL-3.0)
 * and its Monet themes (monet_*.attheme, also in exteraless). Nagram X takes
 * the HCT math of Google's Material library; MZGram has not that library and
 * does the same steps in CIELAB, where the lightness is the HCT tone: tones
 * between the system's shades come from the shades around them, a color is
 * tuned to the wallpaper by turning its hue towards the accent's (at most 15
 * degrees, as Material's Blend.harmonize).
 *
 * Off by default (MZGramConfig.monetThemes); with it off and below Android 12
 * nothing here is called and the theme list is Telegram's own.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.os.Build;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public final class MZGramMonet {

    public static final String LIGHT = "MZGram Monet Light";
    public static final String DARK = "MZGram Monet Dark";
    public static final String AMOLED = "MZGram Monet AMOLED";

    public static final int STYLE_ANDROID = 0;
    public static final int STYLE_TELEGRAM = 1;

    private static final String ASSET_PREFIX = "mzgram_monet_";
    private static final String[] NAMES = {LIGHT, DARK, AMOLED};
    private static final String[] ASSETS = {"light", "dark", "amoled"};
    // After the built-in themes (sortIndex 1 to 5)
    private static final int[] SORT_INDEX = {6, 7, 8};

    private static final Theme.ThemeInfo[] themes = new Theme.ThemeInfo[NAMES.length];
    private static int lastSignature;

    private MZGramMonet() {
    }

    // ---- The themes ----

    // Android 12 is the first with the wallpaper's palette
    public static boolean available() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static boolean isOn() {
        return MZGramConfig.monetThemes && available();
    }

    public static boolean isMonet(String name) {
        return LIGHT.equals(name) || DARK.equals(name) || AMOLED.equals(name);
    }

    public static boolean isMonetDark(String name) {
        return DARK.equals(name) || AMOLED.equals(name);
    }

    public static boolean isMonetAsset(String assetName) {
        return assetName != null && assetName.startsWith(ASSET_PREFIX);
    }

    // Theme.ThemeInfo.getName: the name shown in the theme list, null for
    // the other themes.
    public static String title(String name) {
        if (LIGHT.equals(name)) {
            return LocaleController.getString(R.string.MZGramMonetLight);
        } else if (DARK.equals(name)) {
            return LocaleController.getString(R.string.MZGramMonetDark);
        } else if (AMOLED.equals(name)) {
            return LocaleController.getString(R.string.MZGramMonetBlack);
        }
        return null;
    }

    public static String assetName(int index, int style) {
        return ASSET_PREFIX + ASSETS[index] + (style == STYLE_TELEGRAM ? "_classic" : "") + ".attheme";
    }

    public static String styleTitle(int style) {
        return LocaleController.getString(style == STYLE_TELEGRAM ? R.string.MZGramMonetStyleTelegram : R.string.MZGramMonetStyleAndroid);
    }

    // Theme's static setup and turning the themes on: puts them in the theme
    // list beside the built-in ones.
    public static void addThemes() {
        if (!isOn()) {
            return;
        }
        try {
            for (int i = 0; i < NAMES.length; i++) {
                if (themes[i] == null) {
                    themes[i] = Theme.mzgramCreateTheme(NAMES[i], assetName(i, MZGramConfig.monetStyle), SORT_INDEX[i]);
                }
                updatePreview(i);
                Theme.mzgramAddTheme(themes[i]);
            }
            lastSignature = signature();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Turning the themes off: takes them out of the list; a Monet theme in
    // use gives way to Telegram's standard day or night theme.
    public static void removeThemes() {
        for (Theme.ThemeInfo info : themes) {
            if (info != null) {
                Theme.mzgramRemoveTheme(info);
            }
        }
    }

    public static void setStyle(int style) {
        if (MZGramConfig.monetStyle == style) {
            return;
        }
        MZGramConfig.setMonetStyle(style);
        for (int i = 0; i < NAMES.length; i++) {
            if (themes[i] != null) {
                themes[i].assetName = assetName(i, style);
            }
        }
        reapply();
    }

    // LaunchActivity.onResume: the wallpaper may have changed while MZGram
    // was in the background; the theme in use then takes the new colors.
    public static void refreshIfChanged() {
        if (!isOn() || themes[0] == null) {
            return;
        }
        int now = signature();
        if (now == lastSignature) {
            return;
        }
        lastSignature = now;
        invalidate();
        for (int i = 0; i < NAMES.length; i++) {
            updatePreview(i);
        }
        reapply();
    }

    private static void reapply() {
        Theme.ThemeInfo active = Theme.getActiveTheme();
        if (active == null || !isMonet(active.name)) {
            return;
        }
        boolean night = Theme.isCurrentThemeNight();
        Theme.applyTheme(active, true, night);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, active, night, null, -1);
    }

    private static int signature() {
        Integer accent = systemPalette.shade(0, 600);
        Integer neutral = systemPalette.shade(4, 500);
        Integer secondary = systemPalette.shade(1, 600);
        return Arrays.hashCode(new int[]{accent == null ? 0 : accent, neutral == null ? 0 : neutral, secondary == null ? 0 : secondary});
    }

    private static void updatePreview(int index) {
        Theme.ThemeInfo info = themes[index];
        if (info == null) {
            return;
        }
        String[] preview;
        if (index == 0) {
            preview = new String[]{"surface_light", "neutral_95", "primary_container_light"};
        } else if (index == 1) {
            preview = new String[]{"surface_dark", "neutral_20", "primary_dark"};
        } else {
            preview = new String[]{"black", "neutral_10", "primary_dark"};
        }
        info.setPreviewBackgroundColor(orZero(cached(preview[0])));
        info.setPreviewInColor(orZero(cached(preview[1])));
        info.setPreviewOutColor(orZero(cached(preview[2])));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    // ThemeActivity "Export theme": the theme's file holds tokens that only
    // MZGram reads; the exported file has the colors themselves.
    public static File exportFile(Theme.ThemeInfo info) {
        SparseIntArray colors = Theme.getThemeFileValues(null, info.assetName, null);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < colors.size(); i++) {
            int key = colors.keyAt(i);
            if (key == Theme.key_wallpaperFileOffset) {
                continue;
            }
            String name = ThemeColors.getStringName(key);
            if (name == null) {
                continue;
            }
            text.append(name).append('=').append(colors.valueAt(i)).append('\n');
        }
        File file = new File(ApplicationLoader.getFilesDirFixed(), "mzgram_monet_export.attheme");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(AndroidUtilities.getStringBytes(text.toString()));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return file;
    }

    // ---- The theme files ----

    // Theme.getThemeFileValues: the color of a value of a Monet theme file,
    // null for any other file or a value that is not a token (the caller then
    // reads it as a number, as before).
    public static Integer color(String assetName, String value, String key) {
        if (!isMonetAsset(assetName) || !available() || value == null) {
            return null;
        }
        try {
            return resolve(value.trim(), key, cachedPalette);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // The source of the system colors: Android's palette, or a fixed one in
    // the tests.
    public interface Palette {
        // One of the five palettes (0 to 2 accent1-3, 3 and 4 neutral1-2) at a
        // shade of SHADES, or null.
        Integer shade(int palette, int shade);

        // Android 14 and newer: the system's color of a Material role, or null.
        Integer role(String role, boolean dark);
    }

    public static final int[] SHADES = {0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
    // The tone of each shade: shade 0 is white (tone 100), 1000 black (tone 0)
    public static final int[] SHADE_TONES = {100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0};
    private static final String[] PALETTE_CODES = {"a1", "a2", "a3", "n1", "n2"};

    private static final HashMap<String, Integer> TONAL_PALETTES = new HashMap<>();
    private static final HashMap<String, Integer> CONSTANTS = new HashMap<>();
    private static final HashMap<String, Integer> HARMONIZED = new HashMap<>();
    private static final HashMap<String, Integer> SEEDS = new HashMap<>();
    private static final HashMap<String, String> LIGHT_ROLES = new HashMap<>();
    private static final HashMap<String, String> DARK_ROLES = new HashMap<>();
    private static final HashMap<String, String> FIXED_ROLES = new HashMap<>();
    private static final HashSet<String> LINK_KEYS = new HashSet<>(Arrays.asList(
            "chat_messageLinkIn", "chat_messageLinkOut", "windowBackgroundWhiteLinkText", "dialogTextLink"));

    // A grey wallpaper: links would be grey too, they take a blue instead
    private static final double NEUTRAL_ACCENT_CHROMA = 8.0;
    private static final double LINK_HUE = 265.0;
    private static final double LINK_CHROMA = 55.0;

    static {
        TONAL_PALETTES.put("primary", 0);
        TONAL_PALETTES.put("secondary", 1);
        TONAL_PALETTES.put("tertiary", 2);
        TONAL_PALETTES.put("neutral", 3);
        TONAL_PALETTES.put("neutral_variant", 4);

        CONSTANTS.put("white", 0xffffffff);
        CONSTANTS.put("black", 0xff000000);
        CONSTANTS.put("transparent", 0x00000000);
        CONSTANTS.put("mBlack", 0xff000000);
        CONSTANTS.put("mWhite", 0xffffffff);
        CONSTANTS.put("error_light", 0xffb3261e);
        CONSTANTS.put("on_error_light", 0xffffffff);
        CONSTANTS.put("error_container_light", 0xfff9dedc);
        CONSTANTS.put("on_error_container_light", 0xff410e0b);
        CONSTANTS.put("error_dark", 0xfff2b8b5);
        CONSTANTS.put("on_error_dark", 0xff601410);
        CONSTANTS.put("error_container_dark", 0xff8c1d18);
        CONSTANTS.put("on_error_container_dark", 0xfff9dedc);
        CONSTANTS.put("monetRedLight", 0xffb3261e);
        CONSTANTS.put("monetRedDark", 0xfff2b8b5);
        CONSTANTS.put("monetRedCall", 0xffef5350);
        CONSTANTS.put("monetGreenCall", 0xff4caf50);

        // Turned towards the wallpaper's accent
        HARMONIZED.put("mRed200", 0xffef9a9a);
        HARMONIZED.put("mRed500", 0xfff44336);
        HARMONIZED.put("mRed800", 0xffc62828);
        HARMONIZED.put("mGreen200", 0xffa5d6a7);
        HARMONIZED.put("mGreen500", 0xff4caf50);
        HARMONIZED.put("mGreen800", 0xff2e7d32);
        HARMONIZED.put("monetYellow", 0xffffb74d);
        HARMONIZED.put("monetGreen", 0xff4caf50);
        HARMONIZED.put("monetAvatarRed", 0xffff845e);
        HARMONIZED.put("monetAvatarOrange", 0xfffebb5b);
        HARMONIZED.put("monetAvatarViolet", 0xffb694f9);
        HARMONIZED.put("monetAvatarGreen", 0xff9ad164);
        HARMONIZED.put("monetAvatarCyan", 0xff5bcbe3);
        HARMONIZED.put("monetAvatarBlue", 0xff5caffa);
        HARMONIZED.put("monetAvatarPink", 0xffff8aac);

        // name_tone: the hue of the seed turned to the accent, its chroma
        SEEDS.put("blue", 0xff0000ff);
        SEEDS.put("red", 0xffff0000);
        SEEDS.put("green", 0xff00ff00);
        SEEDS.put("orange", 0xffffaa00);
        SEEDS.put("violet", 0xffeb00ff);
        SEEDS.put("pink", 0xffff32ac);
        SEEDS.put("cyan", 0xff14aaac);

        String[] light = {
                "primary", "0:40", "on_primary", "0:100", "primary_container", "0:90", "on_primary_container", "0:10",
                "inverse_primary", "0:80", "surface_tint", "0:40",
                "secondary", "1:40", "on_secondary", "1:100", "secondary_container", "1:90", "on_secondary_container", "1:10",
                "tertiary", "2:40", "on_tertiary", "2:100", "tertiary_container", "2:90", "on_tertiary_container", "2:10",
                "background", "4:98", "on_background", "4:10", "surface", "4:98", "on_surface", "4:10",
                "surface_variant", "4:90", "on_surface_variant", "4:30", "inverse_surface", "4:20", "inverse_on_surface", "4:95",
                "outline", "4:50", "outline_variant", "4:80", "scrim", "4:0", "shadow", "4:0",
                "surface_bright", "4:98", "surface_dim", "4:87", "surface_container", "4:94", "surface_container_high", "4:92",
                "surface_container_highest", "4:90", "surface_container_low", "4:96", "surface_container_lowest", "4:100",
        };
        String[] dark = {
                "primary", "0:80", "on_primary", "0:20", "primary_container", "0:30", "on_primary_container", "0:90",
                "inverse_primary", "0:40", "surface_tint", "0:80",
                "secondary", "1:80", "on_secondary", "1:20", "secondary_container", "1:30", "on_secondary_container", "1:90",
                "tertiary", "2:80", "on_tertiary", "2:20", "tertiary_container", "2:30", "on_tertiary_container", "2:90",
                "background", "4:6", "on_background", "4:90", "surface", "4:6", "on_surface", "4:90",
                "surface_variant", "4:30", "on_surface_variant", "4:80", "inverse_surface", "4:90", "inverse_on_surface", "4:20",
                "outline", "4:60", "outline_variant", "4:30", "scrim", "4:0", "shadow", "4:0",
                "surface_bright", "4:24", "surface_dim", "4:6", "surface_container", "4:12", "surface_container_high", "4:17",
                "surface_container_highest", "4:22", "surface_container_low", "4:10", "surface_container_lowest", "4:4",
        };
        String[] fixed = {
                "primary_fixed", "0:90", "primary_fixed_dim", "0:80", "on_primary_fixed_dim", "0:80", "on_primary_fixed", "0:10",
                "on_primary_fixed_variant", "0:30",
                "secondary_fixed", "1:90", "secondary_fixed_dim", "1:80", "on_secondary_fixed_dim", "1:80", "on_secondary_fixed", "1:10",
                "on_secondary_fixed_variant", "1:30",
                "tertiary_fixed", "2:90", "tertiary_fixed_dim", "2:80", "on_tertiary_fixed_dim", "2:80", "on_tertiary_fixed", "2:10",
                "on_tertiary_fixed_variant", "2:30",
        };
        for (int i = 0; i < light.length; i += 2) {
            LIGHT_ROLES.put(light[i], light[i + 1]);
        }
        for (int i = 0; i < dark.length; i += 2) {
            DARK_ROLES.put(dark[i], dark[i + 1]);
        }
        for (int i = 0; i < fixed.length; i += 2) {
            FIXED_ROLES.put(fixed[i], fixed[i + 1]);
        }
    }

    // A value of a theme file: token or token(s=, l=, a=, t=, c=); null when
    // it is not a token.
    public static Integer resolve(String value, String key, Palette palette) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String token = value;
        int saturation = 100;
        int lightness = 100;
        int alpha = 100;
        int tone = -1;
        int chroma = 100;
        int open = value.indexOf('(');
        if (open >= 0) {
            if (!value.endsWith(")") || open == 0) {
                return null;
            }
            token = value.substring(0, open).trim();
            for (String param : value.substring(open + 1, value.length() - 1).split(",")) {
                String[] parts = param.split("=");
                if (parts.length != 2) {
                    continue;
                }
                int parsed;
                try {
                    parsed = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignore) {
                    continue;
                }
                switch (parts[0].trim()) {
                    case "s":
                        saturation = parsed;
                        break;
                    case "l":
                        lightness = parsed;
                        break;
                    case "a":
                        alpha = Math.max(0, Math.min(100, parsed));
                        break;
                    case "t":
                        tone = Math.max(0, Math.min(100, parsed));
                        break;
                    case "c":
                        chroma = Math.max(0, Math.min(400, parsed));
                        break;
                }
            }
        }
        Integer resolved = token(token, palette, 0);
        if (resolved == null) {
            return null;
        }
        int color = resolved;
        if (tone >= 0 || chroma != 100) {
            double[] lch = lch(color);
            color = fromLch(tone >= 0 ? tone : lch[0], lch[1] * chroma / 100.0, lch[2], android.graphics.Color.alpha(color));
        }
        if (saturation != 100) {
            color = ColorUtils.blendARGB(0xffffffff, color, saturation / 100f);
        }
        if (lightness != 100) {
            color = ColorUtils.blendARGB(0xff000000, color, lightness / 100f);
        }
        if (alpha != 100) {
            color = ColorUtils.setAlphaComponent(color, Math.round(alpha * 2.55f));
        }
        if (key != null && LINK_KEYS.contains(key) && isAccentToken(token) && accentIsNeutral(palette)) {
            double[] lch = lch(color);
            color = fromLch(lch[0], LINK_CHROMA, LINK_HUE, android.graphics.Color.alpha(color));
        }
        return color;
    }

    private static Integer token(String token, Palette palette, int depth) {
        if (depth > 4) {
            return null;
        }
        if (token.startsWith("monet_")) {
            return token(token.substring(6), palette, depth + 1);
        }
        Integer constant = CONSTANTS.get(token);
        if (constant != null) {
            return constant;
        }
        Integer harmonized = HARMONIZED.get(token);
        if (harmonized != null) {
            Integer accent = palette.shade(0, 600);
            return accent == null ? harmonized : harmonize(harmonized, accent);
        }
        Integer system = systemShade(token, palette);
        if (system != null) {
            return system;
        }
        Integer role = role(token, palette);
        if (role != null) {
            return role;
        }
        int underscore = token.lastIndexOf('_');
        if (underscore <= 0 || underscore >= token.length() - 1) {
            return null;
        }
        String prefix = token.substring(0, underscore);
        int number = digits(token.substring(underscore + 1));
        if (number < 0) {
            return null;
        }
        Integer tonal = TONAL_PALETTES.get(prefix);
        if (tonal != null) {
            return number <= 100 ? tone(palette, tonal, number) : null;
        }
        Integer seed = SEEDS.get(prefix);
        if (seed != null) {
            if (number > 100) {
                return null;
            }
            Integer accent = palette.shade(0, 500);
            if (accent == null) {
                return null;
            }
            double hue = lch(harmonize(seed, accent))[2];
            return fromLch(number, lch(accent)[1] * 0.9, hue, 0xff);
        }
        // base_percent: the base color with its lightness times percent
        Integer base = token(prefix, palette, depth + 1);
        if (base != null && number >= 1 && number <= 100) {
            return darken(base, number);
        }
        return null;
    }

    // a1_600, n2_50: a shade of the system palette
    private static Integer systemShade(String token, Palette palette) {
        int underscore = token.indexOf('_');
        if (underscore != 2 || token.length() < 4) {
            return null;
        }
        int index = Arrays.asList(PALETTE_CODES).indexOf(token.substring(0, 2));
        int shade = digits(token.substring(3));
        if (index < 0 || shade < 0 || Arrays.binarySearch(SHADES, shade) < 0) {
            return null;
        }
        return palette.shade(index, shade);
    }

    // surface_container_dark, on_primary_light, primary_fixed: Material roles
    private static Integer role(String token, Palette palette) {
        boolean dark;
        String role;
        if (token.endsWith("_light")) {
            dark = false;
            role = token.substring(0, token.length() - 6);
        } else if (token.endsWith("_dark")) {
            dark = true;
            role = token.substring(0, token.length() - 5);
        } else {
            String fixed = FIXED_ROLES.get(token);
            return fixed == null ? null : roleTone(fixed, palette);
        }
        String descriptor = dark ? DARK_ROLES.get(role) : LIGHT_ROLES.get(role);
        if (descriptor == null) {
            return null;
        }
        if (frameworkRolesTrusted(palette)) {
            Integer framework = palette.role(role, dark);
            if (framework != null) {
                return framework;
            }
        }
        return roleTone(descriptor, palette);
    }

    private static Integer roleTone(String descriptor, Palette palette) {
        int separator = descriptor.indexOf(':');
        return tone(palette, Integer.parseInt(descriptor.substring(0, separator)), Integer.parseInt(descriptor.substring(separator + 1)));
    }

    // A palette at a tone: its shade, or between the two shades around it
    // (the lightness is the tone, the hue and chroma from the two shades).
    public static Integer tone(Palette palette, int index, int tone) {
        for (int i = 0; i < SHADE_TONES.length; i++) {
            if (SHADE_TONES[i] == tone) {
                return palette.shade(index, SHADES[i]);
            }
        }
        for (int i = 0; i < SHADE_TONES.length - 1; i++) {
            int high = SHADE_TONES[i];
            int low = SHADE_TONES[i + 1];
            if (tone < high && tone > low) {
                Integer lighter = palette.shade(index, SHADES[i]);
                Integer darker = palette.shade(index, SHADES[i + 1]);
                if (lighter == null || darker == null) {
                    return null;
                }
                double f = (tone - low) / (double) (high - low);
                double[] a = lab(darker);
                double[] b = lab(lighter);
                return fromLab(tone, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, 0xff);
            }
        }
        return null;
    }

    // Android 14 gives the roles themselves; they are taken only when they
    // match the palette (some phones give roles of another palette).
    private static Boolean rolesTrusted;
    private static Palette rolesTrustedFor;

    private static boolean frameworkRolesTrusted(Palette palette) {
        if (rolesTrusted != null && rolesTrustedFor == palette) {
            return rolesTrusted;
        }
        boolean trusted = true;
        int checked = 0;
        String[][] probes = {{"primary", "dark"}, {"surface", "dark"}, {"primary", "light"}, {"surface", "light"}};
        for (String[] probe : probes) {
            boolean dark = "dark".equals(probe[1]);
            Integer framework = palette.role(probe[0], dark);
            Integer computed = roleTone((dark ? DARK_ROLES : LIGHT_ROLES).get(probe[0]), palette);
            if (framework == null || computed == null) {
                continue;
            }
            checked++;
            if (channelDistance(framework, computed) > 24) {
                trusted = false;
                break;
            }
        }
        rolesTrusted = trusted && checked > 0;
        rolesTrustedFor = palette;
        return rolesTrusted;
    }

    private static int channelDistance(int a, int b) {
        return Math.max(Math.max(
                Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff)),
                Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff))),
                Math.abs((a & 0xff) - (b & 0xff)));
    }

    private static boolean isAccentToken(String token) {
        if (token.startsWith("monet_")) {
            token = token.substring(6);
        }
        if (token.startsWith("on_")) {
            token = token.substring(3);
        }
        return token.startsWith("a1_") || token.startsWith("a2_") || token.startsWith("a3_")
                || token.startsWith("primary") || token.startsWith("secondary")
                || token.startsWith("tertiary") || token.startsWith("inverse_primary")
                || token.startsWith("surface_tint");
    }

    private static boolean accentIsNeutral(Palette palette) {
        Integer accent = palette.shade(0, 500);
        return accent != null && lch(accent)[1] < NEUTRAL_ACCENT_CHROMA;
    }

    private static int digits(String value) {
        if (value.isEmpty() || value.length() > 4) {
            return -1;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(value);
    }

    // The lightness (HSL) times percent, as Nagram X does
    public static int darken(int color, int percent) {
        if (percent >= 100) {
            return color;
        }
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] = Math.max(0f, Math.min(1f, hsl[2] * percent / 100f));
        return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), android.graphics.Color.alpha(color));
    }

    // Material's Blend.harmonize in CIELAB: the hue turns towards the
    // source's by half the difference, at most 15 degrees; lightness and
    // chroma stay.
    public static int harmonize(int design, int source) {
        double[] from = lch(design);
        double[] to = lch(source);
        double difference = 180.0 - Math.abs(Math.abs(from[2] - to[2]) - 180.0);
        double rotation = Math.min(difference * 0.5, 15.0);
        double direction = sanitize(to[2] - from[2]) <= 180.0 ? 1.0 : -1.0;
        return fromLch(from[0], from[1], sanitize(from[2] + rotation * direction), android.graphics.Color.alpha(design));
    }

    private static double sanitize(double degrees) {
        degrees = degrees % 360.0;
        return degrees < 0 ? degrees + 360.0 : degrees;
    }

    // ---- CIELAB (D65) ----

    public static double[] lab(int argb) {
        double r = linear(((argb >> 16) & 0xff) / 255.0);
        double g = linear(((argb >> 8) & 0xff) / 255.0);
        double b = linear((argb & 0xff) / 255.0);
        double x = (0.41233895 * r + 0.35762064 * g + 0.18051042 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.01932141 * r + 0.11916382 * g + 0.95034478 * b) / 1.08883;
        double fx = labF(x);
        double fy = labF(y);
        double fz = labF(z);
        return new double[]{116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }

    public static double[] lch(int argb) {
        double[] lab = lab(argb);
        double hue = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        return new double[]{lab[0], Math.hypot(lab[1], lab[2]), sanitize(hue)};
    }

    // The lightness is kept; a chroma the screen cannot show is lowered to
    // the most it can.
    public static int fromLch(double lightness, double chroma, double hue, int alpha) {
        double radians = Math.toRadians(hue);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double[] rgb = labToRgb(lightness, chroma * cos, chroma * sin);
        if (!inGamut(rgb)) {
            double low = 0;
            double high = chroma;
            for (int i = 0; i < 20; i++) {
                double mid = (low + high) / 2;
                if (inGamut(labToRgb(lightness, mid * cos, mid * sin))) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            rgb = labToRgb(lightness, low * cos, low * sin);
        }
        return pack(rgb, alpha);
    }

    public static int fromLab(double lightness, double a, double b, int alpha) {
        double chroma = Math.hypot(a, b);
        double hue = Math.toDegrees(Math.atan2(b, a));
        return fromLch(lightness, chroma, hue, alpha);
    }

    private static double[] labToRgb(double l, double a, double b) {
        double fy = (l + 16.0) / 116.0;
        double fx = fy + a / 500.0;
        double fz = fy - b / 200.0;
        double x = labFInverse(fx) * 0.95047;
        double y = labFInverse(fy);
        double z = labFInverse(fz) * 1.08883;
        return new double[]{
                3.2409699 * x - 1.5373832 * y - 0.4986108 * z,
                -0.9692436 * x + 1.8759675 * y + 0.0415551 * z,
                0.0556301 * x - 0.2039770 * y + 1.0569715 * z
        };
    }

    private static boolean inGamut(double[] rgb) {
        for (double v : rgb) {
            if (v < -0.0001 || v > 1.0001) {
                return false;
            }
        }
        return true;
    }

    private static int pack(double[] rgb, int alpha) {
        int r = channel(rgb[0]);
        int g = channel(rgb[1]);
        int b = channel(rgb[2]);
        return (alpha & 0xff) << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(double linear) {
        double v = Math.max(0.0, Math.min(1.0, linear));
        double srgb = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, srgb)) * 255.0);
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16.0) / 116.0;
    }

    private static double labFInverse(double f) {
        double cube = f * f * f;
        return cube > 216.0 / 24389.0 ? cube : (116.0 * f - 16.0) / (24389.0 / 27.0);
    }

    // ---- Android's palette ----

    private static final HashMap<String, Integer> tokenCache = new HashMap<>();

    private static Integer cached(String token) {
        if (!available()) {
            return null;
        }
        synchronized (tokenCache) {
            if (tokenCache.containsKey(token)) {
                return tokenCache.get(token);
            }
        }
        Integer value = resolve(token, null, systemPalette);
        synchronized (tokenCache) {
            tokenCache.put(token, value);
        }
        return value;
    }

    private static void invalidate() {
        synchronized (tokenCache) {
            tokenCache.clear();
        }
        synchronized (MZGramMonet.class) {
            systemCache.clear();
        }
        rolesTrusted = null;
    }

    private static final HashMap<String, Integer> systemCache = new HashMap<>();

    // The system palette, read once until the wallpaper changes
    private static final Palette cachedPalette = new Palette() {
        @Override
        public Integer shade(int palette, int shade) {
            String key = palette + ":" + shade;
            synchronized (MZGramMonet.class) {
                if (systemCache.containsKey(key)) {
                    return systemCache.get(key);
                }
            }
            Integer value = systemPalette.shade(palette, shade);
            synchronized (MZGramMonet.class) {
                systemCache.put(key, value);
            }
            return value;
        }

        @Override
        public Integer role(String role, boolean dark) {
            String key = role + (dark ? ":dark" : ":light");
            synchronized (MZGramMonet.class) {
                if (systemCache.containsKey(key)) {
                    return systemCache.get(key);
                }
            }
            Integer value = systemPalette.role(role, dark);
            synchronized (MZGramMonet.class) {
                systemCache.put(key, value);
            }
            return value;
        }
    };

    private static final Palette systemPalette = new Palette() {
        @Override
        public Integer shade(int palette, int shade) {
            if (!available()) {
                return null;
            }
            int i = Arrays.binarySearch(SHADES, shade);
            if (palette < 0 || palette >= SYSTEM_COLORS.length || i < 0) {
                return null;
            }
            try {
                return ApplicationLoader.applicationContext.getColor(SYSTEM_COLORS[palette][i]);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public Integer role(String role, boolean dark) {
            if (Build.VERSION.SDK_INT < 34) {
                return null;
            }
            try {
                Context context = ApplicationLoader.applicationContext;
                int id = context.getResources().getIdentifier("system_" + role + (dark ? "_dark" : "_light"), "color", "android");
                return id == 0 ? null : context.getColor(id);
            } catch (Exception e) {
                return null;
            }
        }
    };

    // android.R.color.system_*: Android 12 (API 31) and newer, read only there
    private static final int[][] SYSTEM_COLORS = {
            {android.R.color.system_accent1_0, android.R.color.system_accent1_10, android.R.color.system_accent1_50,
                    android.R.color.system_accent1_100, android.R.color.system_accent1_200, android.R.color.system_accent1_300,
                    android.R.color.system_accent1_400, android.R.color.system_accent1_500, android.R.color.system_accent1_600,
                    android.R.color.system_accent1_700, android.R.color.system_accent1_800, android.R.color.system_accent1_900,
                    android.R.color.system_accent1_1000},
            {android.R.color.system_accent2_0, android.R.color.system_accent2_10, android.R.color.system_accent2_50,
                    android.R.color.system_accent2_100, android.R.color.system_accent2_200, android.R.color.system_accent2_300,
                    android.R.color.system_accent2_400, android.R.color.system_accent2_500, android.R.color.system_accent2_600,
                    android.R.color.system_accent2_700, android.R.color.system_accent2_800, android.R.color.system_accent2_900,
                    android.R.color.system_accent2_1000},
            {android.R.color.system_accent3_0, android.R.color.system_accent3_10, android.R.color.system_accent3_50,
                    android.R.color.system_accent3_100, android.R.color.system_accent3_200, android.R.color.system_accent3_300,
                    android.R.color.system_accent3_400, android.R.color.system_accent3_500, android.R.color.system_accent3_600,
                    android.R.color.system_accent3_700, android.R.color.system_accent3_800, android.R.color.system_accent3_900,
                    android.R.color.system_accent3_1000},
            {android.R.color.system_neutral1_0, android.R.color.system_neutral1_10, android.R.color.system_neutral1_50,
                    android.R.color.system_neutral1_100, android.R.color.system_neutral1_200, android.R.color.system_neutral1_300,
                    android.R.color.system_neutral1_400, android.R.color.system_neutral1_500, android.R.color.system_neutral1_600,
                    android.R.color.system_neutral1_700, android.R.color.system_neutral1_800, android.R.color.system_neutral1_900,
                    android.R.color.system_neutral1_1000},
            {android.R.color.system_neutral2_0, android.R.color.system_neutral2_10, android.R.color.system_neutral2_50,
                    android.R.color.system_neutral2_100, android.R.color.system_neutral2_200, android.R.color.system_neutral2_300,
                    android.R.color.system_neutral2_400, android.R.color.system_neutral2_500, android.R.color.system_neutral2_600,
                    android.R.color.system_neutral2_700, android.R.color.system_neutral2_800, android.R.color.system_neutral2_900,
                    android.R.color.system_neutral2_1000},
    };
}
