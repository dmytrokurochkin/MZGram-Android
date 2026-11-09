/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Shows the report of the last crash once, on the first resume after it
 * (MZGramCrashLog). The text can be selected or copied; nothing is sent.
 *
 * Based on exteraless CrashReportDialog (GPL-3.0).
 */

package org.telegram.ui.mzgram;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramCrashLog;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

public final class MZGramCrashReport {

    private static boolean shown;

    private MZGramCrashReport() {
    }

    public static void showIfPending(Activity activity) {
        if (shown || activity == null || activity.isFinishing()) {
            return;
        }
        shown = true;
        if (!MZGramConfig.crashReports) {
            // Switched off after the crash: drop the report unread
            MZGramCrashLog.clear();
            return;
        }
        final String report = MZGramCrashLog.take();
        if (TextUtils.isEmpty(report)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> show(activity, report), 400);
    }

    private static void show(Activity activity, String report) {
        if (activity.isFinishing()) {
            return;
        }
        if (SharedConfig.isWaitingForPasscodeEnter) {
            // Not over the passcode screen: wait until the app is unlocked
            AndroidUtilities.runOnUIThread(() -> show(activity, report), 1000);
            return;
        }
        final Context context = activity;

        final TextView noticeView = new TextView(context);
        noticeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        noticeView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        noticeView.setText(LocaleController.getString(R.string.MZGramCrashReportText));
        noticeView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(12));

        final TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setText(report);
        textView.setTextIsSelectable(true);
        textView.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(4), AndroidUtilities.dp(22), AndroidUtilities.dp(4));

        final ScrollView scrollView = new ScrollView(context);
        scrollView.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(noticeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                (int) (AndroidUtilities.displaySize.y * 0.4f / AndroidUtilities.density)));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MZGramCrashReportTitle));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Copy), (dialog, which) -> {
            copy(context, report);
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Close), (dialog, which) -> dialog.dismiss());
        try {
            builder.show();
        } catch (Throwable ignore) {
            // The window went away in the 400 ms before the dialog
        }
    }

    private static void copy(Context context, String report) {
        try {
            final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("MZGram crash", report));
            }
        } catch (Throwable ignore) {
            return;
        }
        final BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment != null) {
            BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
        }
    }
}
