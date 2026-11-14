// Ported from exteraless (GPL-3.0).

package org.telegram.ui.mzgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Preview card background: rounded rectangle with outline that illuminates
 * with the accent color when the card is selected.
 */
public class MZGramPreviewBackgroundDrawable extends Drawable {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final float radius;

    private float selectionProgress;
    private int alpha = 0xFF;

    public MZGramPreviewBackgroundDrawable() {
        this(12f);
    }

    public MZGramPreviewBackgroundDrawable(float cornerRadiusDp) {
        strokePaint.setStyle(Paint.Style.STROKE);
        radius = AndroidUtilities.dp(cornerRadiusDp);
    }

    public Paint getStrokePaint() {
        return strokePaint;
    }

    public float getSelectionProgress() {
        return selectionProgress;
    }

    /** 0 is normal card, 1 is selected card: thicker accent outline. */
    public void setSelectionProgress(float progress) {
        if (selectionProgress == progress) {
            return;
        }
        selectionProgress = progress;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        backgroundPaint.setColor(MZGramPreviewColors.getBackgroundColor());
        strokePaint.setColor(ColorUtils.blendARGB(MZGramPreviewColors.getOutlineColor(),
                Theme.getColor(Theme.key_windowBackgroundWhiteValueText), selectionProgress));
        if (alpha != 0xFF) {
            backgroundPaint.setAlpha(backgroundPaint.getAlpha() * alpha / 0xFF);
            strokePaint.setAlpha(strokePaint.getAlpha() * alpha / 0xFF);
        }
        strokePaint.setStrokeWidth(AndroidUtilities.dp(AndroidUtilities.lerp(0.5f, 2f, selectionProgress)));

        float half = strokePaint.getStrokeWidth() / 2f;
        rectF.set(getBounds().left + half, getBounds().top + half,
                getBounds().right - half, getBounds().bottom - half);
        canvas.drawRoundRect(rectF, radius, radius, backgroundPaint);
        canvas.drawRoundRect(rectF, radius, radius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) {
            return;
        }
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        backgroundPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
