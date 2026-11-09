/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The colors of the music player (MZGramPlayerSheet and its bar): a
 * Material 3 tonal palette from one seed color, taken from the cover or
 * from the theme.
 *
 * Port of exteraless PlayerColors (GPL-3.0), from exteraGram.
 * Changed for MZGram: the build has no Material color library, so the
 * palette is built on CIELAB (MZGramMonet.lch, MZGramMonet.fromLch) with
 * the tones and chromas of the Material "tonal spot" scheme, and the seed
 * is the strongest hue of the cover instead of the Material quantizer.
 * The shades can differ a little from exteraless.
 */

package org.telegram.ui.mzgram.player;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.mzgram.MZGramMonet;
import org.telegram.ui.ActionBar.Theme;

public final class MZGramPlayerColors {

    // Chromas of the Material tonal spot scheme
    private static final double PRIMARY_CHROMA = 36;
    private static final double SECONDARY_CHROMA = 16;
    private static final double NEUTRAL_CHROMA = 6;
    private static final double NEUTRAL_VARIANT_CHROMA = 8;
    // The seed takes the hue of the cover; the colors with less chroma are grey
    private static final double MIN_SEED_CHROMA = 8;
    private static final int HUE_BINS = 36;

    public final boolean dark;
    public final int surface;
    public final int surfaceLow;
    public final int surfaceContainer;
    public final int surfaceHigh;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int outlineVariant;
    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int secondaryContainer;
    public final int onSecondaryContainer;

    private MZGramPlayerColors(boolean dark, int[] c) {
        this.dark = dark;
        surface = c[0];
        surfaceLow = c[1];
        surfaceContainer = c[2];
        surfaceHigh = c[3];
        onSurface = c[4];
        onSurfaceVariant = c[5];
        outlineVariant = c[6];
        primary = c[7];
        onPrimary = c[8];
        primaryContainer = c[9];
        onPrimaryContainer = c[10];
        secondaryContainer = c[11];
        onSecondaryContainer = c[12];
    }

    private int[] values() {
        return new int[]{surface, surfaceLow, surfaceContainer, surfaceHigh, onSurface, onSurfaceVariant, outlineVariant,
                primary, onPrimary, primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer};
    }

    private static int tone(double hue, double chroma, double tone) {
        return MZGramMonet.fromLch(tone, chroma, hue, 0xff);
    }

    public static MZGramPlayerColors fromSeed(int seed, boolean dark) {
        double hue = MZGramMonet.lch(seed | 0xff000000)[2];
        double p = PRIMARY_CHROMA;
        double s = SECONDARY_CHROMA;
        double n = NEUTRAL_CHROMA;
        double nv = NEUTRAL_VARIANT_CHROMA;
        if (dark) {
            return new MZGramPlayerColors(true, new int[]{
                    tone(hue, n, 6), tone(hue, n, 10), tone(hue, n, 12), tone(hue, n, 17),
                    tone(hue, n, 90), tone(hue, nv, 80), tone(hue, nv, 30),
                    tone(hue, p, 80), tone(hue, p, 20), tone(hue, p, 30), tone(hue, p, 90),
                    tone(hue, s, 30), tone(hue, s, 90)
            });
        }
        return new MZGramPlayerColors(false, new int[]{
                tone(hue, n, 98), tone(hue, n, 96), tone(hue, n, 94), tone(hue, n, 92),
                tone(hue, n, 10), tone(hue, nv, 30), tone(hue, nv, 80),
                tone(hue, p, 40), tone(hue, p, 100), tone(hue, p, 90), tone(hue, p, 10),
                tone(hue, s, 90), tone(hue, s, 10)
        });
    }

    public static MZGramPlayerColors lerp(MZGramPlayerColors a, MZGramPlayerColors b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int[] from = a.values();
        int[] to = b.values();
        int[] out = new int[from.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = ColorUtils.blendARGB(from[i], to[i], t);
        }
        return new MZGramPlayerColors(b.dark, out);
    }

    public static Bitmap sample(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        try {
            Bitmap source = bitmap;
            if (Build.VERSION.SDK_INT >= 26 && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                source = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                if (source == null) {
                    return null;
                }
            }
            int w = source.getWidth();
            int h = source.getHeight();
            float k = Math.min(1f, 112f / Math.max(w, h));
            int sw = Math.max(1, Math.round(w * k));
            int sh = Math.max(1, Math.round(h * k));
            Bitmap small = Bitmap.createScaledBitmap(source, sw, sh, true);
            if (small == source) {
                small = source.copy(Bitmap.Config.ARGB_8888, false);
            }
            return small;
        } catch (Throwable e) {
            return null;
        }
    }

    public static int seedFromSample(Bitmap small, int fallback) {
        if (small == null) {
            return fallback;
        }
        try {
            int w = small.getWidth();
            int h = small.getHeight();
            int[] pixels = new int[w * h];
            small.getPixels(pixels, 0, w, 0, 0, w, h);
            return seedFromPixels(pixels, fallback);
        } catch (Throwable e) {
            return fallback;
        }
    }

    // The hue that covers the most of the picture, weighted by chroma; a
    // grey picture keeps the fallback. Public for the tests.
    public static int seedFromPixels(int[] pixels, int fallback) {
        double[] weight = new double[HUE_BINS];
        double[] sinSum = new double[HUE_BINS];
        double[] cosSum = new double[HUE_BINS];
        int counted = 0;
        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 0xff) {
                continue;
            }
            counted++;
            double[] lch = MZGramMonet.lch(pixel);
            if (lch[1] < MIN_SEED_CHROMA || lch[0] < 10 || lch[0] > 97) {
                continue;
            }
            int bin = Math.min(HUE_BINS - 1, (int) (lch[2] / (360.0 / HUE_BINS)));
            double radians = Math.toRadians(lch[2]);
            weight[bin] += lch[1];
            sinSum[bin] += Math.sin(radians) * lch[1];
            cosSum[bin] += Math.cos(radians) * lch[1];
        }
        int best = -1;
        double bestWeight = 0;
        for (int i = 0; i < HUE_BINS; i++) {
            // Neighbouring bins count too, so a hue on a border is not split
            double w = weight[i] + 0.5 * (weight[(i + 1) % HUE_BINS] + weight[(i + HUE_BINS - 1) % HUE_BINS]);
            if (w > bestWeight) {
                bestWeight = w;
                best = i;
            }
        }
        // Less than one percent of the picture in colour: grey
        if (best < 0 || counted == 0 || weight[best] < counted * MIN_SEED_CHROMA * 0.01) {
            return fallback;
        }
        double hue = Math.toDegrees(Math.atan2(sinSum[best], cosSum[best]));
        if (hue < 0) {
            hue += 360;
        }
        return MZGramMonet.fromLch(50, PRIMARY_CHROMA * 1.5, hue, 0xff);
    }

    public static boolean isDark(Theme.ResourcesProvider resourcesProvider) {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) < 0.5;
    }

    public static int noCoverSeed(Theme.ResourcesProvider resourcesProvider) {
        Theme.ThemeInfo theme = Theme.getActiveTheme();
        if (Build.VERSION.SDK_INT >= 31 && theme != null && MZGramMonet.isMonet(theme.getKey())) {
            try {
                int monet = ApplicationLoader.applicationContext.getColor(android.R.color.system_accent1_500);
                if (Color.alpha(monet) != 0) {
                    return monet | 0xff000000;
                }
            } catch (Exception ignore) {
            }
        }
        return fallbackSeed(resourcesProvider);
    }

    public static int fallbackSeed(Theme.ResourcesProvider resourcesProvider) {
        int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        return Color.alpha(color) == 0 ? 0xff4285f4 : color | 0xff000000;
    }

    public boolean lightStatusBar() {
        return ColorUtils.calculateLuminance(surface) > 0.5;
    }

    public int scrim() {
        return dark ? 0x8c000000 : 0x52000000;
    }

    public int shadow() {
        return dark ? 0xff000000 : ColorUtils.blendARGB(primary, Color.BLACK, 0.4f);
    }

    public int ripple() {
        return ColorUtils.setAlphaComponent(onSurface, 0x1f);
    }

    public Theme.ResourcesProvider provider(Theme.ResourcesProvider base) {
        return new Theme.ResourcesProvider() {
            @Override
            public int getColor(int key) {
                if (key == Theme.key_actionBarDefaultSubmenuBackground || key == Theme.key_dialogBackground) {
                    return surfaceContainer;
                } else if (key == Theme.key_actionBarDefaultSubmenuItem || key == Theme.key_dialogTextBlack
                        || key == Theme.key_windowBackgroundWhiteBlackText) {
                    return onSurface;
                } else if (key == Theme.key_actionBarDefaultSubmenuItemIcon || key == Theme.key_dialogTextGray2
                        || key == Theme.key_windowBackgroundWhiteGrayText) {
                    return onSurfaceVariant;
                } else if (key == Theme.key_actionBarDefaultSubmenuSeparator || key == Theme.key_windowBackgroundGray) {
                    return surfaceLow;
                } else if (key == Theme.key_dialogButtonSelector || key == Theme.key_listSelector) {
                    return ripple();
                } else if (key == Theme.key_windowBackgroundWhite) {
                    return surface;
                } else if (key == Theme.key_featuredStickers_addButton || key == Theme.key_windowBackgroundWhiteBlueText) {
                    return primary;
                }
                return Theme.getColor(key, base);
            }

            @Override
            public boolean isDark() {
                return dark;
            }
        };
    }
}
