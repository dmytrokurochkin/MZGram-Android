/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Replace "edited" with an icon: an edited message shows a pencil before
 * its time instead of the word. The pencil is a text mark (also what a
 * copy of the time or a screen reader gets) drawn with Telegram's pencil
 * icon in the colour of the time.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.Components.ColoredImageSpan;

public final class MZGramEditedIcon {

    // U+270E LOWER RIGHT PENCIL
    public static final String MARK = "\u270E";

    private MZGramEditedIcon() {
    }

    public static boolean on() {
        return MZGramConfig.replaceEditedWithIcon;
    }

    /** The time with the pencil mark drawn as the icon; the text as it was when it has no mark. */
    public static CharSequence apply(CharSequence time) {
        if (time == null) {
            return null;
        }
        int at = TextUtils.indexOf(time, MARK);
        if (at < 0) {
            return time;
        }
        SpannableStringBuilder text = new SpannableStringBuilder(time);
        ColoredImageSpan icon = new ColoredImageSpan(R.drawable.msg_edit, ColoredImageSpan.ALIGN_CENTER);
        icon.setSize(dp(14));
        text.setSpan(icon, at, at + MARK.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /** The width of the time as it is drawn, with the icon in place of the mark. */
    public static int width(CharSequence time, TextPaint paint) {
        return (int) Math.ceil(Layout.getDesiredWidth(time, paint));
    }
}
