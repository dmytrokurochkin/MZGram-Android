/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The top of Settings > MZGram: the app's icon, its name and its version
 * with the build number, on the grey background above the categories.
 *
 * Idea from exteraless AboutHeaderCell (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.mzgram.MZGramBranding;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class MZGramAboutHeader extends LinearLayout {

    private final TextView title;
    private final TextView version;

    public MZGramAboutHeader(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImageDrawable(icon(context));
        addView(logo, LayoutHelper.createLinear(84, 84, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        title = new TextView(context);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setText(MZGramBranding.APP_NAME);
        addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 12, 24, 0));

        version = new TextView(context);
        version.setTypeface(AndroidUtilities.bold());
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        version.setGravity(Gravity.CENTER);
        version.setSingleLine(true);
        version.setText(versionText());
        addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 2, 24, 20));

        updateColors();
    }

    public void updateColors() {
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    private static Drawable icon(Context context) {
        try {
            return context.getPackageManager().getApplicationIcon(context.getPackageName());
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    // "1.0.10 (6512)": the MZGram version and the build number, as the
    // version line of Telegram's Settings counts it (the code without its
    // last digit, which only says the build kind)
    public static String versionText() {
        try {
            Context context = ApplicationLoader.applicationContext;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String[] versions = MZGramBranding.versions(info.versionName);
            return versions[0] + " (" + (info.versionCode / 10) + ")";
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        }
    }
}
