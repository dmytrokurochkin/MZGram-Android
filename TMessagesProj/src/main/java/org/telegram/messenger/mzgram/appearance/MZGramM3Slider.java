/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Appearance > Material Design 3 > Slider: Telegram's
 * sliders (SeekBarView) drawn as a Material 3 slider: an 8 dp track split at
 * the thumb by 6 dp gaps, half-round outer ends, 2 dp inner corners and a
 * 3 x 24 dp bar for the thumb. The metrics of Widget.Material3.Slider, drawn
 * by hand after exteraless SeekBarView (GPL-3.0). The touch area is Telegram's.
 */

package org.telegram.messenger.mzgram.appearance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.mzgram.MZGramConfig;

public final class MZGramM3Slider {

    public static final float TRACK_HEIGHT_DP = 8;
    public static final float THUMB_WIDTH_DP = 3;
    public static final float THUMB_HEIGHT_DP = 24;
    public static final float THUMB_TRACK_GAP_DP = 6;
    public static final float INSIDE_CORNER_DP = 2;

    private static final RectF rect = new RectF();
    private static final Path path = new Path();
    private static final float[] radii = new float[8];

    private MZGramM3Slider() {
    }

    // Plain sliders only: not two-sided ones, not with a lower limit, a buffer,
    // a line width of their own or time codes, as in the Material 3 slider
    public static boolean applies(boolean twoSided, float minProgress, float bufferedProgress, int lineWidthDp, boolean hasTimestamps) {
        return MZGramConfig.m3Slider && !twoSided && minProgress <= 0 && bufferedProgress <= 0 && lineWidthDp == 3 && !hasTimestamps;
    }

    public static void draw(Canvas canvas, float left, float right, float centerY, float thumbCenterX, Paint active, Paint inactive) {
        final float half = AndroidUtilities.dp(TRACK_HEIGHT_DP) / 2f;
        final float inner = AndroidUtilities.dp(INSIDE_CORNER_DP);
        final float gap = AndroidUtilities.dp(THUMB_TRACK_GAP_DP);
        final float top = centerY - half, bottom = centerY + half;

        segment(canvas, left, top, thumbCenterX - gap, bottom, half, inner, active);
        segment(canvas, thumbCenterX + gap, top, right, bottom, inner, half, inactive);

        final float thumbHalfWidth = AndroidUtilities.dp(THUMB_WIDTH_DP) / 2f;
        final float thumbHalfHeight = AndroidUtilities.dp(THUMB_HEIGHT_DP) / 2f;
        rect.set(thumbCenterX - thumbHalfWidth, centerY - thumbHalfHeight, thumbCenterX + thumbHalfWidth, centerY + thumbHalfHeight);
        canvas.drawRoundRect(rect, thumbHalfWidth, thumbHalfWidth, active);
    }

    // A piece of the track with its own left and right corner radii
    private static void segment(Canvas canvas, float l, float t, float r, float b, float leftRadius, float rightRadius, Paint paint) {
        if (r - l <= 0) {
            return;
        }
        final float cap = (r - l) / 2f;
        leftRadius = Math.min(leftRadius, cap);
        rightRadius = Math.min(rightRadius, cap);
        radii[0] = radii[1] = radii[6] = radii[7] = leftRadius;
        radii[2] = radii[3] = radii[4] = radii[5] = rightRadius;
        rect.set(l, t, r, b);
        path.rewind();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        canvas.drawPath(path, paint);
    }
}
