package org.mzgram.backup;

import android.text.TextUtils;

import org.telegram.messenger.FileLog;

import java.nio.charset.StandardCharsets;

public final class InvisibleEncryptor {

    private static final String PREFIX = "\u2001\u2002";
    private static final String SEPARATOR = "\u2000";
    private static final String ALPHABET =
            "\u200a\u200b\u200c\u200f\u202f\u206a\u206b\u206c\u206d\u206e\u206f";
    private static final int BASE = 11;

    private static final String PATTERN =
            "^" + PREFIX + "([" + ALPHABET + SEPARATOR + "\\s]*)";

    private InvisibleEncryptor() {
    }

    public static String encode(String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            StringBuilder builder = new StringBuilder(PREFIX);
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) {
                    builder.append(SEPARATOR);
                }
                builder.append(toStr(bytes[i] & 0xff));
            }
            return builder.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return text;
        }
    }

    public static String decode(String text) {
        try {
            String[] parts = text.replaceFirst("^" + PREFIX, "").split(SEPARATOR);
            byte[] bytes = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                bytes[i] = (byte) toNum(parts[i]);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            FileLog.e(e);
            return text;
        }
    }

    public static boolean isEncrypted(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return text.matches(PATTERN);
    }

    private static int toNum(String text) {
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            int position = text.length() - i;
            int digit = ALPHABET.indexOf(text.substring(position - 1, position));
            value += (int) (digit * Math.pow(BASE, i));
        }
        return value;
    }

    private static String toStr(int value) {
        StringBuilder builder = new StringBuilder();
        while (value > 0) {
            builder.insert(0, ALPHABET.charAt(value % BASE));
            value /= BASE;
        }
        return builder.toString();
    }
}
