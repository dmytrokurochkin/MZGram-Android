/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The bar at the bottom of the screen about a newer MZGram release
 * (MZGramUpdateChecker). "Update" downloads, checks and installs it
 * (MZGramUpdateInstaller), showing the progress; a failure says what went
 * wrong and offers "Retry" and the release page. When the release has no APK
 * that can be checked, the release page opens instead. "Close" hides the bar
 * for that version.
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
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.mzgram.MZGramUpdateChecker;
import org.telegram.messenger.mzgram.MZGramUpdateInstaller;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class MZGramUpdateBanner extends LinearLayout {

    public interface Opener {
        void open(Context context, String url);
    }

    // Android's "install unknown apps" for MZGram
    public interface Permission {
        boolean granted();

        void ask(Context context);

        Permission SYSTEM = new Permission() {
            @Override
            public boolean granted() {
                return MZGramUpdateInstaller.canInstall();
            }

            @Override
            public void ask(Context context) {
                new AlertDialog.Builder(context)
                        .setTitle(LocaleController.getString(R.string.MZGramUpdatePermissionTitle))
                        .setMessage(LocaleController.getString(R.string.MZGramUpdatePermission))
                        .setPositiveButton(LocaleController.getString(R.string.MZGramUpdatePermissionOpen), (dialog, which) -> {
                            try {
                                context.startActivity(MZGramUpdateInstaller.permissionIntent());
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        // MZGram's own Cancel: Telegram's follows Telegram's language
                        .setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null)
                        .show();
            }
        };
    }

    // Swapped by the tests, which must not start a browser or open settings
    public static Opener opener = (context, url) -> Browser.openUrl(context, url);
    public static Permission permission = Permission.SYSTEM;

    private final TextView text;
    private final TextView page;
    private final TextView update;
    private final TextView close;
    private MZGramUpdateChecker.Release release;
    private boolean failed;
    // What the text says now, so that it can be said again in another language
    private int textRes = R.string.MZGramUpdateAvailable;
    private int textPercent;

    public MZGramUpdateBanner(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
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
        text.setMaxLines(3);
        text.setEllipsize(TextUtils.TruncateAt.END);
        addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        page = button(context, LocaleController.getString(R.string.MZGramUpdatePage));
        page.setOnClickListener(v -> {
            if (release != null) {
                opener.open(getContext(), release.url);
            }
            hide();
        });
        buttons.addView(page, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        update = button(context, LocaleController.getString(R.string.MZGramUpdateOpen));
        update.setOnClickListener(v -> startUpdate());
        buttons.addView(update, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        close = button(context, LocaleController.getString(R.string.MZGramUpdateClose));
        close.setOnClickListener(v -> {
            if (release != null) {
                MZGramUpdateChecker.dismiss(release);
            }
            hide();
        });
        buttons.addView(close, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

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

    // Offer, retry or progress: which buttons there are
    private void buttons(boolean offer) {
        page.setVisibility(failed ? VISIBLE : GONE);
        update.setVisibility(offer ? VISIBLE : GONE);
        close.setVisibility(offer ? VISIBLE : GONE);
        labels();
    }

    // The buttons were labelled once, when the app started, so after the
    // MZGram language changed they kept the old one
    private void labels() {
        page.setText(LocaleController.getString(R.string.MZGramUpdatePage));
        update.setText(LocaleController.getString(failed ? R.string.MZGramUpdateRetry : R.string.MZGramUpdateOpen));
        close.setText(LocaleController.getString(R.string.MZGramUpdateClose));
    }

    private void setText(int res, int percent) {
        textRes = res;
        textPercent = percent;
        String version = release == null ? "" : release.version();
        if (res == R.string.MZGramUpdateDownloading) {
            text.setText(LocaleController.formatString(res, version, percent));
        } else {
            text.setText(LocaleController.formatString(res, version));
        }
    }

    // Every text in the MZGram language now (after it was changed)
    public void refreshTexts() {
        labels();
        if (release != null) {
            setText(textRes, textPercent);
        }
    }

    public void show(MZGramUpdateChecker.Release release) {
        // The check of the app coming to the front must not reset an update in progress
        if (MZGramUpdateInstaller.isRunning() && this.release != null && this.release.tag.equals(release.tag) && getVisibility() == VISIBLE) {
            return;
        }
        this.release = release;
        failed = false;
        setText(R.string.MZGramUpdateAvailable, 0);
        buttons(true);
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void hide() {
        failed = false;
        setVisibility(GONE);
    }

    private void startUpdate() {
        MZGramUpdateChecker.Release release = this.release;
        if (release == null) {
            FileLog.d("MZGram update: Update tapped without a release");
            return;
        }
        if (!permission.granted()) {
            FileLog.d("MZGram update: asking for the install permission");
            permission.ask(getContext());
            return;
        }
        MZGramUpdateInstaller.Listener listener = new MZGramUpdateInstaller.Listener() {
            private boolean current() {
                return MZGramUpdateBanner.this.release == release && getVisibility() == VISIBLE;
            }

            @Override
            public void onProgress(int percent) {
                if (current()) {
                    setText(R.string.MZGramUpdateDownloading, percent);
                }
            }

            @Override
            public void onVerifying() {
                if (current()) {
                    setText(R.string.MZGramUpdateVerifying, 0);
                }
            }

            @Override
            public void onInstalling() {
                if (current()) {
                    setText(R.string.MZGramUpdateInstalling, 0);
                }
            }

            @Override
            public void onFailed(MZGramUpdateInstaller.Failure failure, String detail) {
                if (!current()) {
                    return;
                }
                if (failure == MZGramUpdateInstaller.Failure.NOT_FOUND) {
                    // The release is gone or no longer newer
                    hide();
                    return;
                }
                failed = true;
                int message;
                switch (failure) {
                    case CHECKSUM:
                        message = R.string.MZGramUpdateFailedChecksum;
                        break;
                    case INSTALL:
                        message = R.string.MZGramUpdateFailedInstall;
                        break;
                    default:
                        message = R.string.MZGramUpdateFailedNetwork;
                        break;
                }
                setText(message, 0);
                buttons(true);
            }

            @Override
            public void onFallback(String url) {
                if (current()) {
                    opener.open(getContext(), url);
                    hide();
                }
            }
        };
        if (MZGramUpdateInstaller.start(release, listener)) {
            failed = false;
            setText(R.string.MZGramUpdateDownloading, 0);
            buttons(false);
        } else {
            FileLog.d("MZGram update: " + release.tag + " not started, one is running");
        }
    }

    public MZGramUpdateChecker.Release getRelease() {
        return getVisibility() == VISIBLE ? release : null;
    }

    public CharSequence getText() {
        return text.getText();
    }

    public boolean isFailed() {
        return failed && getVisibility() == VISIBLE;
    }

    public View getUpdateButton() {
        return update;
    }

    public View getPageButton() {
        return page;
    }

    public View getCloseButton() {
        return close;
    }

    // The banner of the running app, or null before LaunchActivity is up
    public static MZGramUpdateBanner current() {
        org.telegram.ui.LaunchActivity activity = org.telegram.ui.LaunchActivity.instance;
        return activity == null ? null : activity.mzgramUpdateBanner;
    }

    // The text of the last checkByHand result, for the tests
    public static volatile String lastCheckText;

    // "Check for updates" (Settings > MZGram > Other, and Telegram's own
    // item): a check now, outside the interval; the result in a bulletin,
    // and a newer version also gets the banner.
    public static void checkByHand(org.telegram.ui.ActionBar.BaseFragment fragment, Runnable done) {
        MZGramUpdateChecker.checkManually(result -> {
            if (done != null) {
                done.run();
            }
            if (result.result == MZGramUpdateChecker.MANUAL_AVAILABLE && result.release != null) {
                MZGramUpdateBanner banner = current();
                if (banner != null) {
                    banner.show(result.release);
                }
            }
            String text = resultText(result);
            lastCheckText = text;
            if (fragment != null && fragment.getParentActivity() != null) {
                org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, text).show();
            }
        });
    }

    public static String resultText(MZGramUpdateChecker.Manual result) {
        switch (result.result) {
            case MZGramUpdateChecker.MANUAL_AVAILABLE:
                return LocaleController.formatString(R.string.MZGramUpdateCheckAvailable, result.release.version());
            case MZGramUpdateChecker.MANUAL_ERROR:
                return LocaleController.getString(R.string.MZGramUpdateCheckFailed);
            default:
                return LocaleController.getString(R.string.MZGramUpdateCheckLatest);
        }
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
