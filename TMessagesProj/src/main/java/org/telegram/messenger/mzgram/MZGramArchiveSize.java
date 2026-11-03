package org.telegram.messenger.mzgram;

import java.io.File;

/**
 * Settings > MZGram > Archive: how much the archive takes, by kind. The
 * messages database (with its journal), the copies of media, Saved
 * Attachments and anything else in MZGram's folder; what a wipe set aside is
 * being deleted and is not counted.
 */
public final class MZGramArchiveSize {

    public static final class Sizes {
        public long database;
        public long media;
        public long attachments;
        public long other;

        public long total() {
            return database + media + attachments + other;
        }

        public long[] parts() {
            return new long[] { database, media, attachments, other };
        }
    }

    private MZGramArchiveSize() {
    }

    // Slow on gigabytes: not on the UI thread.
    public static Sizes measure(File folder, String databaseName, File media, File attachments) {
        Sizes result = new Sizes();
        File[] files = folder != null ? folder.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.contains(MZGramArchiveWipe.ASIDE)) {
                    continue;
                }
                if (file.equals(media)) {
                    result.media += sizeOf(file);
                } else if (file.equals(attachments)) {
                    result.attachments += sizeOf(file);
                } else if (name.startsWith(databaseName)) {
                    result.database += sizeOf(file);
                } else {
                    result.other += sizeOf(file);
                }
            }
        }
        if (attachments != null && (folder == null || !folder.equals(attachments.getParentFile()))) {
            result.attachments += sizeOf(attachments);
        }
        return result;
    }

    public static Sizes measureArchive() {
        File media = MZGramHistoryDatabase.mediaRoot();
        return measure(media.getParentFile(), MZGramHistoryDatabase.DB_NAME, media, MZGramHistoryDatabase.attachmentsRoot());
    }

    private static long sizeOf(File file) {
        if (file.isFile()) {
            return file.length();
        }
        long result = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                result += sizeOf(child);
            }
        }
        return result;
    }
}
