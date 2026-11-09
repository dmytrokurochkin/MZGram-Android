/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Outline of the glass surfaces (the blurred panels, buttons and menus of
 * Telegram 13): the glare as in Telegram (the default, untouched), a solid
 * thin line around the whole shape without the shadow, or no outline and no
 * shadow at all. Applied in BlurredBackgroundDrawable (and its RenderNode
 * variant), StrokeDrawable of the story buttons and the glare border of the
 * settings icons.
 *
 * Idea from exteraless GlassOutlineStyle / AppearanceConfig.glassOutlineStyle
 * (GPL-3.0), written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.StrokeDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class MZGramGlassOutline {

    public static final int GLARE = 0;
    public static final int SOLID = 1;
    public static final int HIDDEN = 2;

    public static final int[] TITLES = {
        R.string.MZGramGlassOutlineGlare,
        R.string.MZGramGlassOutlineSolid,
        R.string.MZGramGlassOutlineHidden
    };

    // Solid line: light on dark glass, dark on light glass, like a divider
    private static final int SOLID_ON_DARK = 0x33FFFFFF;
    private static final int SOLID_ON_LIGHT = 0x24000000;

    // Drawables that already took their colors: they are recolored on a change
    private static final Set<Drawable> tracked = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Object drawLock = new Object();
    private static final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path solidPath = new Path();
    private static final RectF solidRect = new RectF();
    private static final float[] solidRadii = new float[8];

    static {
        solidPaint.setStyle(Paint.Style.STROKE);
    }

    private MZGramGlassOutline() {
    }

    public static int style() {
        final int style = MZGramConfig.glassOutline;
        return style == SOLID || style == HIDDEN ? style : GLARE;
    }

    public static boolean isGlare() {
        return style() == GLARE;
    }

    public static boolean isSolid() {
        return style() == SOLID;
    }

    public static void track(Drawable drawable) {
        if (drawable == null) {
            return;
        }
        synchronized (tracked) {
            tracked.add(drawable);
        }
    }

    /** Recolors the glass already on screen; called on the UI thread after a change. */
    public static void changed() {
        final ArrayList<Drawable> snapshot;
        synchronized (tracked) {
            snapshot = new ArrayList<>(tracked);
        }
        for (Drawable drawable : snapshot) {
            if (drawable instanceof BlurredBackgroundDrawable) {
                ((BlurredBackgroundDrawable) drawable).updateColors();
            } else if (drawable instanceof StrokeDrawable) {
                ((StrokeDrawable) drawable).updateColors();
            }
            drawable.invalidateSelf();
        }
    }

    /** Color of the solid line for glass filled with the given color (0 when unknown). */
    public static int solidColor(int fillColor) {
        if (Color.alpha(fillColor) < 0x40) {
            return Theme.getColor(Theme.key_divider);
        }
        return AndroidUtilities.computePerceivedBrightness(ColorUtils.setAlphaComponent(fillColor, 255)) < 0.5f ? SOLID_ON_DARK : SOLID_ON_LIGHT;
    }

    /** Color of the solid line for the story buttons, always over dark media. */
    public static int solidColorOnMedia() {
        return SOLID_ON_DARK;
    }

    public static float solidWidth() {
        return Math.max(1f, dpf2(2 / 3f));
    }

    public static void drawSolid(Canvas canvas, Rect bounds, float[] radii, int color, float alpha) {
        drawSolid(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom, radii, color, alpha);
    }

    /** A line inside the given round rect, so a clip to the shape keeps it whole. */
    public static void drawSolid(Canvas canvas, float left, float top, float right, float bottom, float[] radii, int color, float alpha) {
        final int c = Theme.multAlpha(color, Math.max(0f, Math.min(1f, alpha)));
        if (Color.alpha(c) == 0 || right <= left || bottom <= top) {
            return;
        }
        synchronized (drawLock) {
            final float width = solidWidth();
            final float half = width / 2f;
            solidRect.set(left + half, top + half, right - half, bottom - half);
            final float max = Math.min(solidRect.width(), solidRect.height()) / 2f;
            for (int i = 0; i < 8; i++) {
                solidRadii[i] = radii == null ? 0 : Math.max(0, Math.min(radii[i] - half, max));
            }
            solidPath.rewind();
            solidPath.addRoundRect(solidRect, solidRadii, Path.Direction.CW);
            solidPaint.setStrokeWidth(width);
            solidPaint.setColor(c);
            canvas.drawPath(solidPath, solidPaint);
        }
    }

    public static void choose(BaseFragment fragment, Runnable onChanged) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final CharSequence[] names = new CharSequence[TITLES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = LocaleController.getString(TITLES[i]);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MZGramGlassOutline));
        builder.setItems(names, (dialog, which) -> {
            MZGramConfig.setGlassOutline(which);
            changed();
            if (onChanged != null) {
                onChanged.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null);
        fragment.showDialog(builder.create());
    }
}
