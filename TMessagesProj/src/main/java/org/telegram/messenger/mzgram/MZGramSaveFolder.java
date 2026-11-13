/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > General > Save folder: the name of the folder that
 * "Save to gallery", "Save to downloads" and "Save to music" put files in,
 * inside Pictures, Movies, Download and Music. Telegram (the default, as in
 * Telegram), MZGram, or a name of one's own. Only the folder name changes;
 * files saved before stay where they are.
 *
 * Idea from exteraless (GPL-3.0), "Save folder"; written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

public final class MZGramSaveFolder {

    public static final String TELEGRAM = "Telegram";
    public static final String MZGRAM = "MZGram";
    public static final int MAX_LENGTH = 64;

    private MZGramSaveFolder() {
    }

    // The folder name to save into: the chosen one, or Telegram's when it is
    // empty or has nothing but characters a folder name cannot have
    public static String name() {
        String clean = clean(MZGramConfig.saveFolder);
        return clean.isEmpty() ? TELEGRAM : clean;
    }

    // A folder name without path separators and characters Android's shared
    // storage refuses, no dots at the ends, at most MAX_LENGTH characters
    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || "/\\:*?\"<>|".indexOf(c) >= 0) {
                continue;
            }
            out.append(c);
        }
        String result = out.toString().trim();
        while (result.startsWith(".")) {
            result = result.substring(1).trim();
        }
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH).trim();
        }
        return result;
    }
}
