/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * One pill of the chat list's search bar (MZGramPills): a 28dp oval with an
 * icon and a short text, at the end of the search bar. A pill with a
 * refresh interval updates itself only while it is the one on screen.
 * A tap runs the pill's action; a long press opens the pill's own options,
 * where "Pills" leads to the choice of pills.
 *
 * Idea from exteraless BasePill (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram.pills;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.mzgram.MZGramPillsActivity;

@SuppressLint("ViewConstructor")
public abstract class MZGramPill extends FrameLayout {

    public static final int HEIGHT_DP = 28;

    protected final Theme.ResourcesProvider resourcesProvider;
    protected final LinearLayout layout;
    protected final ImageView iconView;
    protected final AnimatedTextView textView;

    private boolean selected;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update(true);
            schedule();
        }
    };

    public MZGramPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClipChildren(false);
        setClipToPadding(false);

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(dp(44));
        layout.setPadding(dp(8), 0, dp(10), 0);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, HEIGHT_DP,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL,
                LocaleController.isRTL ? 4 : 0, 0, LocaleController.isRTL ? 0 : 4, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(dp(13));
        textView.setIncludeFontPadding(false);
        textView.setTypeface(AndroidUtilities.bold());
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
    }

    /** The pill's id in MZGramPills. */
    public abstract int pillId();

    /** How often the pill updates while on screen, in ms; 0 when it updates on its own events. */
    public long refreshInterval() {
        return 0;
    }

    /** Reads the current value and shows it. */
    public abstract void update(boolean animated);

    /** A tap on the pill. */
    public abstract void onTap();

    /** Options of the pill on a long press; "Pills" is added after them. */
    protected void addOptions(ItemOptions options, BaseFragment fragment) {
    }

    protected void setIcon(int resId) {
        iconView.setImageResource(resId);
    }

    protected void setText(CharSequence text, boolean animated) {
        CharSequence old = textView.getText();
        if (old != null && text != null && old.toString().contentEquals(text)) {
            return;
        }
        textView.setText(text, animated && isLaidOut());
    }

    /** The colour of the pill's text and icon; the oval is a light tint of it. */
    protected int accentColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
    }

    public void updateColors() {
        int color = accentColor();
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(HEIGHT_DP / 2), Theme.multAlpha(color, 0.09f), Theme.multAlpha(color, 0.18f)));
        textView.setTextColor(color);
        iconView.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
    }

    /** The long press of the pill bar. */
    public boolean onHold() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return false;
        }
        ItemOptions options = ItemOptions.makeOptions(fragment, layout);
        addOptions(options, fragment);
        options.add(R.drawable.msg_settings, LocaleController.getString(R.string.MZGramPills),
                () -> fragment.presentFragment(new MZGramPillsActivity()));
        options.setDrawScrim(false).setDimAlpha(0).show();
        return true;
    }

    /** The pill came on screen (true) or left it (false). */
    public void setSelectedPill(boolean selected) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        removeCallbacks(refresh);
        if (selected) {
            update(false);
            schedule();
        }
    }

    public boolean isSelectedPill() {
        return selected;
    }

    private void schedule() {
        removeCallbacks(refresh);
        long interval = refreshInterval();
        if (selected && interval > 0 && isAttachedToWindow()) {
            postDelayed(refresh, interval);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
        if (selected) {
            update(false);
            schedule();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(refresh);
    }
}
