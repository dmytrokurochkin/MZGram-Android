/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Ghost in the header: while ghost mode is on, a ghost after the title of
 * the chat list, so it is seen at a glance (Settings > MZGram > Ghost mode,
 * off by default).
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramGhostMode;

public final class MZGramGhostTitle {

    private MZGramGhostTitle() {
    }

    public static boolean shown() {
        return MZGramConfig.ghostStatusInTitle && MZGramGhostMode.isEnabled();
    }

    /** The title, with the ghost after it while it is shown. */
    public static CharSequence title(CharSequence title, int color) {
        if (!shown() || title == null) {
            return title;
        }
        Drawable ghost = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.ghost).mutate();
        ghost.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        ghost.setBounds(0, 0, dp(20), dp(20));
        SpannableStringBuilder text = new SpannableStringBuilder(title);
        text.append("  ");
        text.setSpan(new ImageSpan(ghost, ImageSpan.ALIGN_BOTTOM), text.length() - 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
}
