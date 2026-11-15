package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

/**
 * MZGram: the Material 3 empty state of the plugins: the plugin icon on a tonal circle, a title and a line
 * of text. The ways to get a plugin are the rows above it, so the text only says what plugins are.
 */
final class PluginsEmptyCell extends FrameLayout {

    private final LinearLayout content;
    private final FrameLayout iconCircle;
    private final ImageView iconView;
    private final TextView titleView;
    private final LinkSpanDrawable.LinksTextView hintView;

    private boolean initialized;
    private boolean contentVisible;
    private int animationGeneration;

    PluginsEmptyCell(Context context, int currentAccount) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        iconCircle = new FrameLayout(context);
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setImageResource(R.drawable.baseline_extension_24);
        iconCircle.addView(iconView, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        content.addView(iconCircle, LayoutHelper.createLinear(88, 88, Gravity.CENTER, 0, 0, 0, 20));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 8));

        hintView = new LinkSpanDrawable.LinksTextView(context);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintView.setGravity(Gravity.CENTER);
        hintView.setLineSpacing(dp(2), 1f);
        content.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        updateColors();
    }

    void updateColors() {
        final int page = Theme.getColor(Theme.key_windowBackgroundGray) | 0xFF000000;
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton) | 0xFF000000;
        iconCircle.setBackground(Theme.createCircleDrawable(dp(88),
                ColorUtils.blendARGB(page, accent, Theme.isCurrentThemeDark() ? 0.2f : 0.16f)));
        iconView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hintView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
    }

    /** The title is shown over the list's empty state, and is null for a search with no result. */
    void setState(boolean visible, CharSequence title, CharSequence text, boolean animated) {
        final boolean showIcon = title != null;
        iconCircle.setVisibility(showIcon ? VISIBLE : GONE);
        titleView.setVisibility(showIcon ? VISIBLE : GONE);
        if (showIcon) {
            titleView.setText(title);
        }
        boolean textChanged = !TextUtils.equals(hintView.getText(), text);
        if (initialized && visible == contentVisible) {
            if (!textChanged) {
                return;
            }
            if (!visible) {
                hintView.setText(text);
                return;
            }
        }
        int generation = ++animationGeneration;
        content.animate().setListener(null).cancel();

        if (!initialized || !animated || !isAttachedToWindow()) {
            hintView.setText(text);
            contentVisible = visible;
            initialized = true;
            content.setAlpha(visible ? 1f : 0f);
            content.setVisibility(visible ? VISIBLE : INVISIBLE);
            content.setEnabled(visible);
            hintView.setEnabled(visible);
            return;
        }

        initialized = true;
        content.setEnabled(visible);
        hintView.setEnabled(visible);

        if (textChanged && visible && contentVisible) {
            content.animate()
                    .alpha(0f)
                    .setDuration(90)
                    .withEndAction(() -> {
                        if (generation != animationGeneration) {
                            return;
                        }
                        hintView.setText(text);
                        content.animate()
                                .alpha(1f)
                                .setDuration(150)
                                .withEndAction(null)
                                .start();
                    })
                    .start();
        } else {
            if (textChanged) {
                hintView.setText(text);
            }
            content.setVisibility(VISIBLE);
            content.animate()
                    .alpha(visible ? 1f : 0f)
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (generation == animationGeneration && !visible) {
                            content.setVisibility(INVISIBLE);
                        }
                    })
                    .start();
        }
        contentVisible = visible;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
