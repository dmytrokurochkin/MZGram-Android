package app.exteraless.appearance;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

/**
 * MZGram: Material bars. A flat strip in the colour of the page behind the view, so a docked bar (tabs,
 * a bottom button row) is seamless with the page instead of a floating glass pill. The colour is read on
 * every draw, so it follows a theme switch.
 */
public final class MzPageColorDrawable extends Drawable {

    private final View owner;
    private final int fallbackKey;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint paint = new Paint();
    private boolean hairlineTop;

    public MzPageColorDrawable(View owner, int fallbackKey, Theme.ResourcesProvider resourcesProvider) {
        this.owner = owner;
        this.fallbackKey = fallbackKey;
        this.resourcesProvider = resourcesProvider;
    }

    /** A 1px divider on the top edge, for a bar docked at the bottom over scrolling content. */
    public MzPageColorDrawable setHairlineTop(boolean hairlineTop) {
        this.hairlineTop = hairlineTop;
        return this;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final int page = ActionBar.mzPageColor(owner, Theme.getColor(fallbackKey, resourcesProvider) | 0xFF000000);
        paint.setColor(page);
        canvas.drawRect(getBounds(), paint);
        if (hairlineTop) {
            paint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            canvas.drawRect(getBounds().left, getBounds().top, getBounds().right, getBounds().top + 1, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
