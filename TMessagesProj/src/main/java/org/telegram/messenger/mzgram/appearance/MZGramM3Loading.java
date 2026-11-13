/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Appearance > Material Design 3 > Loading: Telegram's
 * round spinners (RadialProgressView, CircularProgressDrawable) drawn as a
 * Material 3 circular progress indicator: a wavy arc, the rest of the circle
 * as a faint track, 2 dp gaps between them. Drawn by hand with the metrics of
 * the Material 3 indicator, after exteraless M3CircularProgress (GPL-3.0).
 */

package org.telegram.messenger.mzgram.appearance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.mzgram.MZGramConfig;

public final class MZGramM3Loading {

    // The track: the arc's colour at this share of its alpha
    public static final float TRACK_ALPHA = 0.2f;
    // indicatorTrackGapSize of the Material 3 indicator
    public static final float GAP_DP = 2;
    // Big spinners (radius from 12 dp): wavelength, amplitude and speed of the wave in dp
    static final float[] WAVE_BIG = {15, 1.6f, 5};
    // Small spinners in buttons and bars
    static final float[] WAVE_SMALL = {7, 0.75f, 6};
    static final float SMALL_RADIUS_DP = 12;

    private static final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();

    static {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private MZGramM3Loading() {
    }

    public static boolean enabled() {
        return MZGramConfig.m3Loading;
    }

    // Draws the spinner's arc: Telegram's plain arc, or the Material 3 one when the setting is on
    public static void drawArc(Canvas canvas, RectF oval, float startAngle, float sweepAngle, Paint paint) {
        if (!enabled()) {
            canvas.drawArc(oval, startAngle, sweepAngle, false, paint);
            return;
        }
        final float radius = Math.min(oval.width(), oval.height()) / 2f;
        if (radius <= 0) {
            return;
        }
        final float sweep = Math.max(-360, Math.min(360, sweepAngle));
        final float rest = trackSweep(radius, sweep);
        if (rest > 0) {
            final float gapDeg = (float) Math.toDegrees(AndroidUtilities.dp(GAP_DP) / radius);
            trackPaint.setColor(paint.getColor());
            trackPaint.setAlpha((int) (paint.getAlpha() * TRACK_ALPHA));
            trackPaint.setStrokeWidth(paint.getStrokeWidth());
            final float from = sweep >= 0 ? startAngle + sweep + gapDeg : startAngle + gapDeg;
            canvas.drawArc(oval, from, rest, false, trackPaint);
        }
        final float[] wave = radius < AndroidUtilities.dp(SMALL_RADIUS_DP) ? WAVE_SMALL : WAVE_BIG;
        if (Math.abs(sweep) < 1) {
            canvas.drawArc(oval, startAngle, sweep, false, paint);
            return;
        }
        buildWave(oval, radius, startAngle, sweep, AndroidUtilities.dp(wave[0]), AndroidUtilities.dp(wave[1]), AndroidUtilities.dp(wave[2]));
        canvas.drawPath(path, paint);
    }

    // Degrees of the track: the circle less the arc and the two gaps; none for a full circle
    public static float trackSweep(float radius, float sweep) {
        if (radius <= 0 || Math.abs(sweep) >= 360) {
            return 0;
        }
        final float gapDeg = (float) Math.toDegrees(AndroidUtilities.dp(GAP_DP) / radius);
        return Math.max(0, 360 - Math.abs(sweep) - gapDeg * 2);
    }

    private static void buildWave(RectF oval, float radius, float startAngle, float sweepAngle, float waveLength, float amplitude, float speed) {
        path.rewind();
        final float cx = oval.centerX();
        final float cy = oval.centerY();
        final float arcLength = (float) (Math.abs(sweepAngle) / 180.0 * Math.PI * radius);
        final int steps = Math.max(8, Math.min(180, (int) (Math.abs(sweepAngle) / 2f)));
        final float phase = (SystemClock.elapsedRealtime() % 100000L) / 1000f * speed;
        for (int i = 0; i <= steps; i++) {
            final float t = i / (float) steps;
            final double rad = Math.toRadians(startAngle + sweepAngle * t);
            final float r = radius + (float) (amplitude * Math.sin(2 * Math.PI * (arcLength * t + phase) / waveLength));
            final float x = cx + (float) (r * Math.cos(rad));
            final float y = cy + (float) (r * Math.sin(rad));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
    }
}
