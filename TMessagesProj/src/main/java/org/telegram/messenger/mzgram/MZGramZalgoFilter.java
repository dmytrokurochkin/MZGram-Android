/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Strips Zalgo-style text corruption: stacked Unicode combining marks
 * (accents, diacritics) piled on top of ordinary characters to make text
 * look glitchy/distorted. Removing every combining mark from a string
 * leaves the original base characters untouched, since Zalgo text is
 * built by inserting extra combining codepoints, not by replacing the
 * base ones.
 */

package org.telegram.messenger.mzgram;

public class MZGramZalgoFilter {

    public static String strip(String text) {
        if (!MZGramConfig.stripZalgoText || text == null || text.isEmpty()) {
            return text;
        }
        boolean hasMark = false;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            if (isCombiningMark(text.charAt(i))) {
                hasMark = true;
                break;
            }
        }
        if (!hasMark) {
            return text;
        }
        StringBuilder sb = new StringBuilder(length);
        text.codePoints().forEach(codePoint -> {
            if (!isCombiningMark(codePoint)) {
                sb.appendCodePoint(codePoint);
            }
        });
        return sb.toString();
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
