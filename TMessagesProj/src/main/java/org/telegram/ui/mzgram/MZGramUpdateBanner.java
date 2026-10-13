/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The bar at the bottom of the screen about a newer MZGram release
 * (MZGramUpdateChecker). "Update" opens the release page on GitHub, nothing is
 * downloaded or installed by the app; "Close" hides it for that version.
 */

package org.telegram.ui.mzgram;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.mzgram.MZGramUpdateChecker;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class MZGramUpdateBanner extends LinearLayout {

    public interface Opener {
        void open(Context context, String url);
    }

    // Swapped by the tests, which must not start a browser
    public static Opener opener = (context, url) -> Browser.openUrl(context, url);

    private final TextView text;
    private final TextView update;
    private final TextView close;
    private MZGramUpdateChecker.Release release;

    public MZGramUpdateBanner(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        background.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        setBackground(background);
        setElevation(AndroidUtilities.dp(4));
        // A tap on the bar itself must not reach the screen under it
        setClickable(true);

        text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        addView(text, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        update = button(context, LocaleController.getString(R.string.MZGramUpdateOpen));
        update.setOnClickListener(v -> {
            if (release != null) {
                opener.open(getContext(), release.url);
            }
            hide();
        });
        addView(update, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        close = button(context, LocaleController.getString(R.string.MZGramUpdateClose));
        close.setOnClickListener(v -> {
            if (release != null) {
                MZGramUpdateChecker.dismiss(release);
            }
            hide();
        });
        addView(close, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        setVisibility(GONE);
    }

    private TextView button(Context context, String label) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        button.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        return button;
    }

    public void show(MZGramUpdateChecker.Release release) {
        this.release = release;
        text.setText(LocaleController.formatString(R.string.MZGramUpdateAvailable, release.version()));
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void hide() {
        setVisibility(GONE);
    }

    public MZGramUpdateChecker.Release getRelease() {
        return getVisibility() == VISIBLE ? release : null;
    }

    public View getUpdateButton() {
        return update;
    }

    public View getCloseButton() {
        return close;
    }

    // The banner of the running app, or null before LaunchActivity is up
    public static MZGramUpdateBanner current() {
        org.telegram.ui.LaunchActivity activity = org.telegram.ui.LaunchActivity.instance;
        return activity == null ? null : activity.mzgramUpdateBanner;
    }

    // The app's update check (LaunchActivity.checkAppUpdate), as on resume or from settings
    public static void checkFromApp(boolean force) {
        org.telegram.ui.LaunchActivity activity = org.telegram.ui.LaunchActivity.instance;
        if (activity != null) {
            activity.checkAppUpdate(force, null);
        }
    }

    public static FrameLayout.LayoutParams layoutParams() {
        return LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 8, 0, 8, 72);
    }
}
