package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

public class SearchTabsAndFiltersLayout extends FrameLayout implements Theme.Colorable {
    private final Path clipPath = new Path();
    private BlurredBackgroundDrawable blurredBackgroundDrawable;
    /** MZGram: Material bars. A flat full-width strip in the page colour, no rounded glass. */
    private boolean mzFlat;

    public SearchTabsAndFiltersLayout(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipPath.rewind();
        if (mzFlat) {
            clipPath.addRect(0, 0, w, h, Path.Direction.CW);
            return;
        }
        clipPath.addRoundRect(dp(9), dp(9), w - dp(9), h - dp(9),
                dp(16), dp(16), Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public void setBlurredBackground(BlurredBackgroundDrawable drawable) {
        setBackground(blurredBackgroundDrawable = drawable);
    }

    public void setMzFlat() {
        mzFlat = true;
        blurredBackgroundDrawable = null;
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(0, 0, 0, 0);
        clipPath.rewind();
        clipPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
    }

    @Override
    public void updateColors() {
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.updateColors();
        }
        if (mzFlat) {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
    }
}
