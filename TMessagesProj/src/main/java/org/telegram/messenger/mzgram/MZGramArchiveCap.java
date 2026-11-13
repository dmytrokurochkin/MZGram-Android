/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The largest size of the archive's files (Settings > MZGram > Archive >
 * Size of the archive's files): 300 MB, 1, 2, 5 or 16 GB, or no limit (the
 * default). When the files of kept media and attachments reach it, new
 * files are no longer downloaded into the archive; nothing kept is ever
 * deleted to make room. The size is measured on a background thread at
 * most every ten minutes and the files started since are added to it.
 */

package org.telegram.messenger.mzgram;

import android.os.SystemClock;

import org.telegram.messenger.Utilities;

public final class MZGramArchiveCap {

    public static final int NO_LIMIT = -1;
    // The steps of the slider, in MB
    public static final int[] STEPS_MB = {300, 1024, 2048, 5120, 16384, NO_LIMIT};

    private static final long MEASURE_EVERY_MS = 10 * 60 * 1000L;

    private static volatile long knownBytes = -1;
    private static volatile long measuredAt;
    private static volatile boolean measuring;

    private MZGramArchiveCap() {
    }

    public static int step() {
        for (int i = 0; i < STEPS_MB.length; i++) {
            if (STEPS_MB[i] == MZGramConfig.archiveTotalLimitMb) {
                return i;
            }
        }
        return STEPS_MB.length - 1;
    }

    public static void setStep(int step) {
        int index = Math.max(0, Math.min(STEPS_MB.length - 1, step));
        MZGramConfig.setArchiveTotalLimitMb(STEPS_MB[index]);
    }

    /** Whether a file of this many bytes may still go into the archive; it is counted when it may. */
    public static boolean allows(long size) {
        int limitMb = MZGramConfig.archiveTotalLimitMb;
        if (limitMb < 0) {
            return true;
        }
        refreshIfOld();
        long known = knownBytes;
        if (known < 0) {
            return true;  // not measured yet: the first measure is on its way
        }
        long add = Math.max(0, size);
        if (known + add > limitMb * 1024L * 1024L) {
            return false;
        }
        knownBytes = known + add;
        return true;
    }

    private static void refreshIfOld() {
        if (measuring || knownBytes >= 0 && SystemClock.elapsedRealtime() - measuredAt < MEASURE_EVERY_MS) {
            return;
        }
        measuring = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                MZGramArchiveSize.Sizes sizes = MZGramArchiveSize.measureArchive();
                knownBytes = sizes.media + sizes.attachments;
                measuredAt = SystemClock.elapsedRealtime();
            } finally {
                measuring = false;
            }
        });
    }

    /** For the tests: forget the measured size. */
    public static void reset() {
        knownBytes = -1;
        measuredAt = 0;
    }

    /** For the tests: a known size, as if just measured. */
    public static void setKnownBytes(long bytes) {
        knownBytes = bytes;
        measuredAt = SystemClock.elapsedRealtime();
    }
}
