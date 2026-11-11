/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/**
 * MZGram: shape of the floating "new chat" button on the chat list: circle
 * (default, untouched), rounded square or a softer squircle.
 */
public class MZGramFabShape {

    public static final int CIRCLE = 0;
    public static final int ROUNDED_SQUARE = 1;
    public static final int SQUIRCLE = 2;

    public static final int[] TITLES = {
        R.string.MZGramFabShapeCircle,
        R.string.MZGramFabShapeSquare,
        R.string.MZGramFabShapeSquircle
    };

    private static int radiusDp(int shape) {
        return shape == ROUNDED_SQUARE ? 12 : 18;
    }

    /** Replaces the round background of the main button; does nothing for the circle. */
    public static void apply(View button, int defaultColor, int pressedColor) {
        int shape = MZGramConfig.fabShape;
        if (shape != ROUNDED_SQUARE && shape != SQUIRCLE) {
            if (button.getClipToOutline()) {
                // back from another shape: restore what the button had
                button.setOutlineProvider(org.telegram.messenger.utils.ViewOutlineProviderImpl.BOUNDS_OVAL);
                button.setClipToOutline(false);
            }
            return;
        }
        final int radius = dp(radiusDp(shape));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius, defaultColor, pressedColor));
        button.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        button.setClipToOutline(true);
    }

    public static void choose(BaseFragment fragment, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        CharSequence[] names = new CharSequence[TITLES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = LocaleController.getString(TITLES[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MZGramFabShape));
        builder.setItems(names, (dialog, which) -> {
            MZGramConfig.setFabShape(which);
            if (onChanged != null) {
                onChanged.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null);
        fragment.showDialog(builder.create());
    }
}
