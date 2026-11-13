/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Appearance > Header text: what the top of the chat
 * list shows instead of Telegram's logo. Telegram's logo (the default, as in
 * Telegram), MZGram, the account's username (its first name when it has
 * none), its first name, "Chats" or a text of one's own. The text is drawn
 * in the place of the logo, in the logo's colour, both in the bar and in the
 * header above the stories, so the emoji status stays right after it.
 *
 * Idea from exteraless (GPL-3.0), AppearanceConfig.titleText and
 * TypefaceHelper.getTitleText; written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mzgram.MZGramBranding;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;

public final class MZGramTitleText {

    public static final int LOGO = 0;
    public static final int APP_NAME = 1;
    public static final int USERNAME = 2;
    public static final int FIRST_NAME = 3;
    public static final int CHATS = 4;
    public static final int CUSTOM = 5;

    public static final int[] TITLES = {
            R.string.MZGramTitleTextLogo,
            R.string.MZGramTitleTextAppName,
            R.string.MZGramTitleTextUsername,
            R.string.MZGramTitleTextFirstName,
            R.string.MZGramTitleTextChats,
            R.string.MZGramTitleTextCustom,
    };

    // The longest text of one's own
    public static final int MAX_LENGTH = 32;

    private MZGramTitleText() {
    }

    public static int mode() {
        int mode = MZGramConfig.titleText;
        return mode < LOGO || mode > CUSTOM ? LOGO : mode;
    }

    // The text for the top of the chat list, or null for Telegram's logo
    @Nullable
    public static String text(int account) {
        switch (mode()) {
            case APP_NAME:
                return MZGramBranding.APP_NAME;
            case USERNAME:
            case FIRST_NAME: {
                TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
                String username = mode() == USERNAME ? UserObject.getPublicUsername(self) : null;
                if (!TextUtils.isEmpty(username)) {
                    return username;
                }
                String first = UserObject.getFirstName(self);
                return TextUtils.isEmpty(first) ? MZGramBranding.APP_NAME : first;
            }
            case CHATS:
                // Telegram's own text: the word of the chat list, in Telegram's language like the rest of it
                return LocaleController.getString(R.string.FilterChats);
            case CUSTOM: {
                String custom = clean(MZGramConfig.titleTextCustom);
                return custom.isEmpty() ? MZGramBranding.APP_NAME : custom;
            }
            default:
                return null;
        }
    }

    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        String result = value.replace('\n', ' ').trim();
        return result.length() > MAX_LENGTH ? result.substring(0, MAX_LENGTH).trim() : result;
    }

    // The text as a drawable in the size of the logo, white, for the colour
    // filter the logo gets; null for the logo itself
    @Nullable
    public static Drawable drawable(int account) {
        String text = text(account);
        return text == null ? null : new TextDrawable(text);
    }

    static final class TextDrawable extends Drawable {

        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final String text;
        private final int width;
        private final int height;

        TextDrawable(String text) {
            this.text = text;
            paint.setColor(0xFFFFFFFF);
            paint.setTypeface(AndroidUtilities.bold());
            paint.setTextSize(AndroidUtilities.dp(20));
            width = (int) Math.ceil(paint.measureText(text));
            height = AndroidUtilities.dp(22);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = bounds.top + (bounds.height() - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
            canvas.drawText(text, bounds.left, baseline, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return width;
        }

        @Override
        public int getIntrinsicHeight() {
            return height;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
