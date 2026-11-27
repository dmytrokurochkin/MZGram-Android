package org.mzgram.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurredRecyclerView;

public class SectionCardRecyclerView extends BlurredRecyclerView {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] radii = new float[8];

    private boolean forceShadows = true;

    public SectionCardRecyclerView(Context context) {
        super(context);
    }

    public void setForceShadows(boolean value) {
        forceShadows = value;
    }

    @Override
    public void drawBackgroundRect(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha) {
        if (!forceShadows || AppearanceConfig.sectionRadius() <= 0) {
            super.drawBackgroundRect(canvas, rect, topRadius, bottomRadius, alpha);
            return;
        }

        strokePaint.setColor(0);
        strokePaint.setShadowLayer(AndroidUtilities.dpf2(0.33f), 0, 0, Theme.multAlpha(0x0C000000, alpha));
        fillPaint.setShadowLayer(AndroidUtilities.dpf2(2f), 0, AndroidUtilities.dpf2(0.33f), Theme.multAlpha(0x0A000000, alpha));
        fillPaint.setColor(Theme.multAlpha(Theme.mzCardSurface(resourcesProvider), alpha));

        if (topRadius == bottomRadius) {
            canvas.drawRoundRect(rect, topRadius, topRadius, strokePaint);
            canvas.drawRoundRect(rect, topRadius, topRadius, fillPaint);
        } else {
            path.rewind();
            radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
            radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
            path.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(path, strokePaint);
            canvas.drawPath(path, fillPaint);
        }
    }
}
