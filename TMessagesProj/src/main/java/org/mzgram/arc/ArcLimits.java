/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.arc;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Message archive > Download in advance, and the counters of saved files.
 *
 * A file of a new message is downloaded as soon as the message comes when it is not
 * larger than the limit of its source (MB; NO_LIMIT for any size), so the archive has
 * it when the message is deleted. When a deleted message has no file on the device,
 * it is downloaded then, up to the limit plus DELETION_EXTRA_MB, if the server still
 * gives it. Files saved earlier stay whatever the limits are. The counters say what
 * happened to the files, to choose the limits on real data.
 */
public final class ArcLimits {

    public static final int USER = 0;
    public static final int BOT = 1;
    public static final int GROUP = 2;
    public static final int CHANNEL = 3;
    public static final int SOURCES = 4;

    public static final int NO_LIMIT = -1;
    public static final int DELETION_EXTRA_MB = 100;
    private static final int[] DEFAULT_MB = {300, 5, 20, 5};
    public static final int[] CHOICES = {0, 5, 20, 50, 100, 300, 500, 1000, 2000, NO_LIMIT};

    public static final int SAVED_AHEAD = 0;
    public static final int SAVED_ON_DELETION = 1;
    public static final int FAILED = 2;
    public static final int EXPIRED = 3;
    /** A deleted message this device had no copy of at all */
    public static final int DELETED_WITHOUT_CONTENT = 4;
    public static final int COUNTERS = 5;

    private static final String PREFERENCES = "mzgram_archive";

    /** Files whose server link could not be refreshed: the file of a deleted message the server no longer gives. */
    private static final Set<String> expired = Collections.synchronizedSet(new HashSet<>());

    private ArcLimits() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public static int defaultMb(int source) {
        return DEFAULT_MB[source >= 0 && source < DEFAULT_MB.length ? source : USER];
    }

    public static int limitMb(int source) {
        int value = preferences().getInt("limit" + source, defaultMb(source));
        return value < 0 ? NO_LIMIT : value;
    }

    public static void setLimitMb(int source, int mb) {
        preferences().edit().putInt("limit" + source, mb < 0 ? NO_LIMIT : mb).apply();
    }

    /** Whether a file of this many bytes is downloaded at that moment; a size of 0 (not known) counts as small. */
    public static boolean fetches(long size, int limitMb, boolean onDeletion) {
        if (limitMb < 0) {
            return true;
        }
        long extra = onDeletion ? DELETION_EXTRA_MB : 0;
        return size <= (limitMb + extra) * 1024L * 1024L;
    }

    public static int counter(int which) {
        return preferences().getInt("counter" + which, 0);
    }

    public static void count(int which) {
        count(which, 1);
    }

    public static synchronized void count(int which, int amount) {
        if (amount <= 0) {
            return;
        }
        SharedPreferences preferences = preferences();
        preferences.edit().putInt("counter" + which, preferences.getInt("counter" + which, 0) + amount).apply();
    }

    public static void resetCounters() {
        SharedPreferences.Editor editor = preferences().edit();
        for (int i = 0; i < COUNTERS; i++) {
            editor.putInt("counter" + i, 0);
        }
        editor.apply();
    }

    public static void noteExpired(String fileName) {
        if (fileName != null) {
            expired.add(fileName);
        }
    }

    /** A download that failed: its link expired, or any other reason. */
    public static int failure(String fileName) {
        return fileName != null && expired.remove(fileName) ? EXPIRED : FAILED;
    }
}
