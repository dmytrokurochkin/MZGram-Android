package org.mzgram.appearance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public class M3CircularProgress {

    public static final int STYLE_LEGACY = 0;
    public static final int STYLE_LOADING_INDICATOR = 1;
    /** CircularProgressIndicator. */
    public static final int STYLE_CIRCULAR = 2;
    public static final int STYLE_WAVY = 3;

    public static int degradeStyle(int style) {
        if (!AppearanceConfig.newLoadingStyle()
                && style != STYLE_LEGACY && style != STYLE_LOADING_INDICATOR) {
            return STYLE_LEGACY;
        }
        return style;
    }

    public static void prewarm() {
        if (!AppearanceConfig.newLoadingStyle()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Class.forName("com.google.android.material.loadingindicator.LoadingIndicatorDrawingDelegate");
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public static boolean isCircular(int style) {
        return style == STYLE_CIRCULAR || style == STYLE_WAVY;
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int trackColor;
    private float gap = AndroidUtilities.dp(2);
    private float waveAmplitude;
    private float waveLength;
    private float waveSpeed;

    public M3CircularProgress() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setTrackColor(int color) {
        trackColor = color;
    }

    public int getTrackColor() {
        return trackColor;
    }

    /** indicatorTrackGapSize — dp(2). */
    public void setGap(float px) {
        gap = px;
    }

    public void setWavyValues(float wavelengthPx, float amplitudePx, float speedPx) {
        waveLength = wavelengthPx;
        waveAmplitude = amplitudePx;
        waveSpeed = speedPx;
    }

    public void setWavy(boolean wavy) {
        if (!wavy) {
            waveAmplitude = 0;
            waveLength = 0;
            waveSpeed = 0;
        }
    }

    public boolean isWavy() {
        return waveAmplitude > 0 && waveLength > 0;
    }

    public void draw(Canvas canvas, RectF oval, float startAngle, float sweepAngle, Paint paint) {
        final float radius = Math.min(oval.width(), oval.height()) / 2f;
        if (radius <= 0) {
            return;
        }
        float sweep = sweepAngle;
        if (sweep > 360) sweep = 360;
        if (sweep < -360) sweep = -360;

        if (trackColor != 0 && Math.abs(sweep) < 360) {
            final float gapDeg = (float) Math.toDegrees(gap / radius);
            final float rest = 360 - Math.abs(sweep) - gapDeg * 2;
            if (rest > 0) {
                trackPaint.setColor(trackColor);
                trackPaint.setAlpha((int) ((trackColor >>> 24) * (paint.getAlpha() / 255f)));
                trackPaint.setStrokeWidth(paint.getStrokeWidth());
                final float from = sweep >= 0 ? startAngle + sweep + gapDeg : startAngle + gapDeg;
                canvas.drawArc(oval, from, rest, false, trackPaint);
            }
        }

        if (isWavy()) {
            buildWavePath(oval, radius, startAngle, sweep);
            canvas.drawPath(path, paint);
        } else {
            canvas.drawArc(oval, startAngle, sweep, false, paint);
        }
    }

    private void buildWavePath(RectF oval, float radius, float startAngle, float sweepAngle) {
        path.reset();
        final float cx = oval.centerX();
        final float cy = oval.centerY();
        final float arcLength = (float) (Math.abs(sweepAngle) / 180.0 * Math.PI * radius);
        final int steps = Math.max(8, Math.min(180, (int) (Math.abs(sweepAngle) / 2f)));
        final float phase = waveSpeed <= 0 ? 0 : (SystemClock.elapsedRealtime() % 100000L) / 1000f * waveSpeed;
        for (int i = 0; i <= steps; i++) {
            final float t = i / (float) steps;
            final float angle = startAngle + sweepAngle * t;
            final float along = arcLength * t + phase;
            final float offset = (float) (waveAmplitude * Math.sin(2 * Math.PI * along / waveLength));
            final double rad = Math.toRadians(angle);
            final float r = radius + offset;
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
