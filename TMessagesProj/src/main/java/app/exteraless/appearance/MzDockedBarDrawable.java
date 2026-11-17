package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * MZGram: Material bars. A bar docked to the bottom of a sheet: the sheet surface edge to edge with a hairline
 * on top, and a rounded container inside it, either a neutral text field or an accent tonal button. It replaces
 * the floating glass pills of the sheets. The colours are read on every draw, so a theme switch recolours it.
 */
public final class MzDockedBarDrawable extends Drawable {

    public static final int FIELD = 0;
    public static final int TONAL_BUTTON = 1;

    private final Theme.ResourcesProvider resourcesProvider;
    private final int surfaceKey;
    private final int style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int inset = dp(9);
    private float radius = dp(22);
    private java.util.function.IntSupplier surfaceProvider;

    public MzDockedBarDrawable(Theme.ResourcesProvider resourcesProvider, int surfaceKey, int style) {
        this.resourcesProvider = resourcesProvider;
        this.surfaceKey = surfaceKey;
        this.style = style;
    }

    /** The bar colour from elsewhere, for a bar that takes the place of another docked bar of that tone. */
    public MzDockedBarDrawable setSurfaceProvider(java.util.function.IntSupplier surfaceProvider) {
        this.surfaceProvider = surfaceProvider;
        return this;
    }

    public MzDockedBarDrawable setInset(int inset) {
        this.inset = inset;
        return this;
    }

    public MzDockedBarDrawable setRadius(float radius) {
        this.radius = radius;
        return this;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        final int surface = (surfaceProvider != null ? surfaceProvider.getAsInt() : Theme.getColor(surfaceKey, resourcesProvider)) | 0xFF000000;
        final boolean dark = AndroidUtilities.computePerceivedBrightness(surface) < .721f;
        final int onSurface = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider) | 0xFF000000;

        paint.setColor(surface);
        canvas.drawRect(b, paint);
        paint.setColor(ColorUtils.setAlphaComponent(onSurface, dark ? 0x1F : 0x14));
        canvas.drawRect(b.left, b.top, b.right, b.top + Math.max(1, dp(0.5f)), paint);

        final int fill;
        if (style == TONAL_BUTTON) {
            final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) | 0xFF000000;
            fill = ColorUtils.blendARGB(surface, accent, dark ? 0.2f : 0.16f);
        } else {
            fill = ColorUtils.blendARGB(surface, onSurface, dark ? 0.1f : 0.06f);
        }
        paint.setColor(fill);
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        canvas.drawRoundRect(rect, radius, radius, paint);
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
