package org.telegram.messenger.mzgram;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings > MZGram > Archive > Clear archive. The archive's files can take
 * gigabytes, and deleting them takes a while: the folders are first moved
 * aside, next to themselves, which is instant, then deleted on a background
 * thread; what an earlier wipe left (the app was closed before it ended) is
 * deleted at the next start.
 */
public final class MZGramArchiveWipe {

    public static final String ASIDE = ".mzgram-deleting-";

    public static final class Result {
        public int files;
        public long bytes;
        public boolean complete = true; // false: some files could not be deleted
    }

    private MZGramArchiveWipe() {
    }

    // Not done yet: the tests come first.

    // Moves each existing folder aside as "<name>.mzgram-deleting-<n>" and
    // returns where they went. A folder that cannot be moved is returned as
    // it is, to be deleted where it is.
    public static List<File> setAside(List<File> folders) {
        return new ArrayList<>();
    }

    // Deletes the folders with everything in them. Slow: not on the UI thread.
    public static Result remove(List<File> folders) {
        Result result = new Result();
        result.complete = false;
        return result;
    }

    // The folders an earlier wipe set aside and did not delete.
    public static List<File> leftovers(List<File> folders) {
        return new ArrayList<>();
    }
}
