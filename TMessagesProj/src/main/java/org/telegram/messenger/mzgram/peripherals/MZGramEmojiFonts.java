/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Custom emoji fonts loader (system.custom-emoji-fonts).
 * Allows loading user-provided emoji font files (.ttf/.otf)
 * without bundling proprietary fonts in the APK.
 */

package org.telegram.messenger.mzgram.peripherals;

import android.graphics.Typeface;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.mzgram.MZGramConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class MZGramEmojiFonts {

    public static final String FONT_FILENAME = "mzgram_emoji_font.ttf";

    private static Typeface customEmojiTypeface;

    private MZGramEmojiFonts() {
    }

    public static File getCustomEmojiFontFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), FONT_FILENAME);
    }

    public static boolean hasCustomEmojiFont() {
        File file = getCustomEmojiFontFile();
        return file.isFile() && file.length() > 0;
    }

    public static Typeface getCustomEmojiTypeface() {
        if (!MZGramConfig.customEmojiFontEnabled) {
            return null;
        }
        if (customEmojiTypeface != null) {
            return customEmojiTypeface;
        }
        File file = getCustomEmojiFontFile();
        if (file.isFile() && file.length() > 0) {
            try {
                customEmojiTypeface = Typeface.createFromFile(file);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return customEmojiTypeface;
    }

    public static boolean installCustomEmojiFont(File source) {
        if (source == null || !source.isFile() || source.length() < 1024) {
            return false;
        }
        // Verify font signature (TrueType: 0x00010000 or OpenType: 'OTTO' or 'true')
        try (InputStream is = new FileInputStream(source)) {
            byte[] header = new byte[4];
            if (is.read(header) != 4) {
                return false;
            }
            boolean valid = (header[0] == 0 && header[1] == 1 && header[2] == 0 && header[3] == 0)
                    || (header[0] == 'O' && header[1] == 'T' && header[2] == 'T' && header[3] == 'O')
                    || (header[0] == 't' && header[1] == 'r' && header[2] == 'u' && header[3] == 'e');
            if (!valid) {
                return false;
            }

            File dest = getCustomEmojiFontFile();
            try (FileOutputStream fos = new FileOutputStream(dest); FileInputStream fis = new FileInputStream(source)) {
                byte[] buf = new byte[16384];
                int len;
                while ((len = fis.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            }
            customEmojiTypeface = null;
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void removeCustomEmojiFont() {
        File file = getCustomEmojiFontFile();
        if (file.exists()) {
            file.delete();
        }
        customEmojiTypeface = null;
    }
}
