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

    // Moves each existing folder aside as "<name>.mzgram-deleting-<n>" and
    // returns where they went. A folder that cannot be moved (shared storage
    // may refuse it) is returned as it is, to be deleted where it is.
    public static List<File> setAside(List<File> folders) {
        List<File> result = new ArrayList<>();
        for (File folder : folders) {
            if (folder == null || !folder.isDirectory()) {
                continue;
            }
            String stamp = String.valueOf(System.currentTimeMillis());
            File aside = new File(folder.getParentFile(), folder.getName() + ASIDE + stamp);
            for (int n = 1; aside.exists(); n++) {
                aside = new File(folder.getParentFile(), folder.getName() + ASIDE + stamp + "-" + n);
            }
            result.add(folder.renameTo(aside) ? aside : folder);
        }
        return result;
    }

    // Deletes the folders with everything in them. Slow: not on the UI thread.
    public static Result remove(List<File> folders) {
        Result result = new Result();
        for (File folder : folders) {
            if (folder != null && folder.exists()) {
                removeRecursively(folder, result);
            }
        }
        return result;
    }

    private static void removeRecursively(File file, Result result) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                removeRecursively(child, result);
            }
        }
        boolean folder = file.isDirectory();
        long size = folder ? 0 : file.length();
        if (file.delete()) {
            if (!folder) {
                result.files++;
                result.bytes += size;
            }
        } else if (file.exists()) {
            result.complete = false;
        }
    }

    // The folders an earlier wipe set aside and did not delete.
    public static List<File> leftovers(List<File> folders) {
        List<File> result = new ArrayList<>();
        for (File folder : folders) {
            File parent = folder != null ? folder.getParentFile() : null;
            File[] near = parent != null ? parent.listFiles() : null;
            if (near == null) {
                continue;
            }
            String prefix = folder.getName() + ASIDE;
            for (File file : near) {
                if (file.isDirectory() && file.getName().startsWith(prefix)) {
                    result.add(file);
                }
            }
        }
        return result;
    }
}
