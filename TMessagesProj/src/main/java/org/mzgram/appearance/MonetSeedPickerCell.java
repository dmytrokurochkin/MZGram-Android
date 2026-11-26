package org.mzgram.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.material.color.utilities.TonalPalette;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashMap;

import org.mzgram.core.helpers.MonetHelper;

/**
 * MZGram: the colour of the Material You themes. The first swatch takes the colours of the
 * wallpaper, the others are seed colours; each swatch shows the primary, secondary and tertiary
 * colours the theme gets from it, the way Android's "Wallpaper and style" shows them.
 */
@SuppressLint("ViewConstructor")
public class MonetSeedPickerCell extends FrameLayout {

    /** 0 is the wallpaper. The seeds are spread over the hue circle so no two look alike. */
    public static final int[] SEEDS = {
            0,
            0xFF6750A4, // violet, the Material 3 baseline
            0xFF1E6FD9, // blue
            0xFF00897B, // teal
            0xFF43A047, // green
            0xFFF2B600, // yellow
            0xFFF57C00, // orange
            0xFFE53935, // red
            0xFFD81B60, // pink
    };

    public interface Callback {
        void onSeedPicked(int seed);
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final LinearLayout row;

    public MonetSeedPickerCell(Context context, Theme.ResourcesProvider resourcesProvider, Callback callback) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(10), 0, dp(10), 0);
        addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.CENTER_VERTICAL));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, dp(68)));

        for (int i = 0; i < SEEDS.length; i++) {
            final int seed = SEEDS[i];
            SwatchView swatch = new SwatchView(context, seed);
            swatch.setContentDescription(seed == 0
                    ? LocaleController.getString(R.string.MZMonetWallpaperColors)
                    : LocaleController.formatString(R.string.MZMonetSeedColor, i));
            swatch.setOnClickListener(v -> {
                callback.onSeedPicked(seed);
                updateSelection();
            });
            row.addView(swatch, LayoutHelper.createLinear(56, 68));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(68), MeasureSpec.EXACTLY));
    }

    /** Redraws the ring around the picked swatch, and the swatches in the colours of the theme. */
    public void updateSelection() {
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).invalidate();
        }
    }

    private class SwatchView extends View {

        private final int seed;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private Drawable wallpaperIcon;

        SwatchView(Context context, int seed) {
            super(context);
            this.seed = seed;
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2));
            setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP));
            if (seed == 0) {
                wallpaperIcon = context.getResources().getDrawable(R.drawable.msg_background).mutate();
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            final float r = dp(19);
            final int primary, secondary, tertiary, onPrimary;
            if (seed == 0) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    return;
                }
                // the wallpaper's own palette, read from the system and never from the picked seed
                primary = getContext().getColor(android.R.color.system_accent1_300);
                secondary = getContext().getColor(android.R.color.system_accent2_300);
                tertiary = getContext().getColor(android.R.color.system_accent3_300);
                onPrimary = getContext().getColor(android.R.color.system_accent1_800);
            } else {
                HashMap<String, TonalPalette> palettes = MonetHelper.palettesFor(seed);
                primary = palettes.get("a1").tone(70);
                secondary = palettes.get("a2").tone(70);
                tertiary = palettes.get("a3").tone(70);
                onPrimary = palettes.get("a1").tone(20);
            }
            oval.set(cx - r, cy - r, cx + r, cy + r);
            paint.setColor(primary);
            canvas.drawArc(oval, 180, 180, true, paint);
            paint.setColor(tertiary);
            canvas.drawArc(oval, 90, 90, true, paint);
            paint.setColor(secondary);
            canvas.drawArc(oval, 0, 90, true, paint);

            if (wallpaperIcon != null) {
                final int s = dp(18);
                wallpaperIcon.setColorFilter(new PorterDuffColorFilter(onPrimary, PorterDuff.Mode.SRC_IN));
                wallpaperIcon.setBounds((int) (cx - s / 2f), (int) (cy - s / 2f), (int) (cx + s / 2f), (int) (cy + s / 2f));
                wallpaperIcon.draw(canvas);
            }

            if (MonetHelper.seedColor() == seed) {
                ringPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                canvas.drawCircle(cx, cy, r + dp(5), ringPaint);
            }
        }
    }
}
