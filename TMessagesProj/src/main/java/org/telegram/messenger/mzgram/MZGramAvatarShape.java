/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Avatar shape: one slider turns the round avatars of the whole app into
 * rounded squares, from a circle (as in Telegram, the default) to a square.
 * The radius each place asks for is scaled, so forum avatars and other
 * rounded squares keep their look relative to the circle. Applied in
 * ImageReceiver (avatars set with setForUserOrChat or with an AvatarDrawable),
 * AvatarDrawable, the story rings and the profile header.
 *
 * Conflict 1.4 of the knowledge base: while the gooey avatar animation of the
 * profile runs, the avatar turns back into a circle smoothly, so the melt is
 * drawn from a circle and the shape is not applied twice.
 *
 * Ported from exteraless AppearanceConfig.avatarCorners / ImageReceiver and
 * StoriesUtilities avatar corners (GPL-3.0), written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import android.graphics.RectF;

public final class MZGramAvatarShape {

    // Slider range: 0 is a square, MAX is a circle (Telegram, the shape is off)
    public static final int MIN = 0;
    public static final int MAX = 28;

    private static volatile int generation;

    private MZGramAvatarShape() {
    }

    public static boolean isOn() {
        return MZGramConfig.avatarCorners < MAX;
    }

    // Grows on each change of the slider; avatars already shown recompute their radius
    public static int generation() {
        return generation;
    }

    static void changed() {
        generation++;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Share of the radius asked for that is kept: 1 is the circle of Telegram.
     * {@code circle} 0..1 moves the share toward the circle (gooey morph).
     */
    public static float factor(float circle) {
        int corners = Math.max(MIN, Math.min(MAX, MZGramConfig.avatarCorners));
        float f = corners / (float) MAX;
        return f + (1f - f) * clamp01(circle);
    }

    public static int scaleRadius(int asked, float circle) {
        if (!isOn() || asked <= 0) {
            return asked;
        }
        return (int) Math.ceil(asked * factor(circle));
    }

    /**
     * ImageReceiver: the radius of an avatar from the radius asked for. A radius
     * bigger than half of the side (asked for a sure circle) is cut to half first.
     * Returns {@code asked} itself while the shape is off, else fills {@code out}.
     */
    public static int[] apply(int[] asked, float width, int[] out) {
        if (!isOn()) {
            return asked;
        }
        float f = factor(0f);
        int half = width > 0 ? (int) Math.ceil(width / 2f) : Integer.MAX_VALUE;
        for (int a = 0; a < 4; a++) {
            int r = Math.min(asked[a], half);
            // at least 1 px: a square stays "rounded" for ImageReceiver, so its
            // AvatarDrawable gets the radius too instead of keeping the old circle
            out[a] = r <= 0 ? r : Math.max(1, (int) Math.ceil(r * f));
        }
        return out;
    }

    // AvatarDrawable drawn without a radius (a circle in Telegram): the radius of its shape
    public static float circleRadius(float size) {
        return size / 2f * factor(0f);
    }

    /**
     * Radius of a story ring around an avatar: the ring keeps the gap to the
     * avatar along the whole edge. {@code stock} is the ring radius of Telegram
     * for forums (rounded squares already), 0 for the round avatars.
     */
    public static float ringRadius(RectF ring, RectF avatar, float stock) {
        float avatarWidth = avatar != null && avatar.width() > 0 ? avatar.width() : ring.width();
        float gap = Math.max(0f, (ring.width() - avatarWidth) / 2f);
        float f = factor(0f);
        if (stock > 0) {
            return stock * f + gap * (1f - f);
        }
        return avatarWidth / 2f * f + gap;
    }

    /**
     * Profile header: how much the avatar turns back into a circle while the
     * gooey animation (pull up into the camera cutout) runs, 0..1.
     */
    public static float gooeyCircle(boolean gooeyRuns, float pullUpProgress) {
        if (!isOn() || !gooeyRuns || !MZGramConfig.gooeyAvatarAnimation) {
            return 0f;
        }
        return Math.min(Math.max(pullUpProgress, 0f), 0.25f) / 0.25f;
    }
}
