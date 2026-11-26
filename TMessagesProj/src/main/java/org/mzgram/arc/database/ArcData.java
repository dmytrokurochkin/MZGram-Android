/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package org.mzgram.arc.database;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.system.Os;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.mzgram.arc.ArcConstants;
import org.mzgram.arc.database.dao.DeletedMessageDao;
import org.mzgram.arc.database.dao.EditedMessageDao;
import org.mzgram.arc.database.dao.LastSeenDao;
import org.mzgram.arc.database.dao.SpyDao;
import org.mzgram.arc.database.dao.DeletedDialogDao;
import org.mzgram.arc.messages.ArcMessagesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.IOException;

import org.mzgram.core.helpers.AppRestartHelper;
import org.mzgram.core.settings.CoreExperimentalSettingsActivity;
import org.mzgram.core.utils.AndroidUtil;
import org.mzgram.core.utils.ShareUtil;

public class ArcData {
    private static final String IMPORT_DATABASE = ArcConstants.ARC_DATABASE + "-import";

    public static long dbSize, attachmentsSize, totalSize;
    private static ArcDatabase database;
    private static EditedMessageDao editedMessageDao;
    private static DeletedMessageDao deletedMessageDao;
    private static LastSeenDao lastSeenDao;
    private static SpyDao spyDao;
    private static DeletedDialogDao deletedDialogDao;

    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_messageId ON deletedmessage(userId, dialogId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_topicId_messageId ON deletedmessage(userId, dialogId, topicId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_replyMessageId_messageId ON deletedmessage(userId, dialogId, replyMessageId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_groupedId_messageId ON deletedmessage(userId, dialogId, groupedId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_dialogId ON deletedmessage(dialogId)");

            database.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_userId_dialogId_messageId_entityCreateDate ON editedmessage(userId, dialogId, messageId, entityCreateDate)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_userId_entityCreateDate ON editedmessage(userId, entityCreateDate)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_dialogId_messageId ON editedmessage(dialogId, messageId)");

            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessagereaction_deletedMessageId ON deletedmessagereaction(deletedMessageId)");
        }
    };

    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyQuote INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyQuoteText TEXT");
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyQuoteEntities BLOB");
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyFromSerialized BLOB");

            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyQuote INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyQuoteText TEXT");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyQuoteEntities BLOB");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyFromSerialized BLOB");
        }
    };

    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyMarkupSerialized BLOB");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyMarkupSerialized BLOB");
        }
    };

    private static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN forwards INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN forwards INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS LastSeenEntity (userId INTEGER NOT NULL, lastSeen INTEGER NOT NULL, PRIMARY KEY(userId))");
        }
    };

    private static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `SpyMessageRead` (`userId` INTEGER NOT NULL, `dialogId` INTEGER NOT NULL, `messageId` INTEGER NOT NULL, `date` INTEGER NOT NULL, PRIMARY KEY(`userId`, `dialogId`, `messageId`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `SpyMessageContentsRead` (`userId` INTEGER NOT NULL, `dialogId` INTEGER NOT NULL, `messageId` INTEGER NOT NULL, `date` INTEGER NOT NULL, PRIMARY KEY(`userId`, `dialogId`, `messageId`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `DeletedDialog` (`userId` INTEGER NOT NULL, `dialogId` INTEGER NOT NULL, `folderId` INTEGER NOT NULL, `topMessage` INTEGER NOT NULL, `lastMessageDate` INTEGER NOT NULL, `entityCreateDate` INTEGER NOT NULL, PRIMARY KEY(`userId`, `dialogId`))");
        }
    };

    static {
        create();
    }

    public static synchronized void create() {
        database = createDatabase(ArcConstants.ARC_DATABASE);

        editedMessageDao = database.editedMessageDao();
        deletedMessageDao = database.deletedMessageDao();
        lastSeenDao = database.lastSeenDao();
        spyDao = database.spyDao();
        deletedDialogDao = database.deletedDialogDao();
    }

    private static ArcDatabase createDatabase(String name) {
        return Room.databaseBuilder(ApplicationLoader.applicationContext, ArcDatabase.class, name)
                .allowMainThreadQueries()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addMigrations(MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
                .build();
    }

    public static ArcDatabase getDatabase() {
        return database;
    }

    public static EditedMessageDao getEditedMessageDao() {
        return editedMessageDao;
    }

    public static DeletedMessageDao getDeletedMessageDao() {
        return deletedMessageDao;
    }

    public static LastSeenDao getLastSeenDao() {
        return lastSeenDao;
    }

    public static SpyDao getSpyDao() {
        return spyDao;
    }

    public static DeletedDialogDao getDeletedDialogDao() {
        return deletedDialogDao;
    }

    public static synchronized void clean() {
        if (database != null) {
            try {
                database.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        clearReferences();
        ApplicationLoader.applicationContext.deleteDatabase(ArcConstants.ARC_DATABASE);
    }

    private static void clearReferences() {
        database = null;
        editedMessageDao = null;
        deletedMessageDao = null;
        lastSeenDao = null;
        spyDao = null;
        deletedDialogDao = null;
    }

    public static void exportArcDatabase(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER, fragment.getResourceProvider());
        progressDialog.setCanCancel(false);
        progressDialog.show();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File dbFile = ApplicationLoader.applicationContext.getDatabasePath(ArcConstants.ARC_DATABASE);
                File exportFile = new File(AndroidUtilities.getCacheDir(), ArcConstants.ARC_DATABASE_EXPORT);
                checkpointDatabase();
                if (!AndroidUtilities.copyFile(dbFile, exportFile)) {
                    if (!exportFile.delete()) {
                        exportFile.deleteOnExit();
                    }
                    throw new IOException("Failed to copy Arc database");
                }
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    Context activity = fragment.getParentActivity();
                    if (activity != null) {
                        ShareUtil.shareFile(activity, exportFile);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    if (fragment.getParentActivity() != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
                    }
                });
            }
        });
    }

    public static void importArcDatabase(BaseFragment fragment, File importFile) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER, fragment.getResourceProvider());
        progressDialog.setCanCancel(false);
        progressDialog.show();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                importDatabase(importFile);
                AndroidUtilities.runOnUIThread(() -> {
                    Context context = ApplicationLoader.applicationContext;
                    AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class));
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    if (fragment.getParentActivity() != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
                    }
                });
            }
        });
    }

    private static synchronized void importDatabase(File sourceFile) throws Exception {
        if (!sourceFile.isFile()) {
            throw new IOException("Arc database import file does not exist");
        }

        Context context = ApplicationLoader.applicationContext;
        File importFile = context.getDatabasePath(IMPORT_DATABASE);
        File databaseFile = context.getDatabasePath(ArcConstants.ARC_DATABASE);

        context.deleteDatabase(IMPORT_DATABASE);

        try {
            if (!AndroidUtilities.copyFile(sourceFile, importFile)) {
                throw new IOException("Failed to stage Arc database");
            }
            validateImportDatabase(importFile);

            ArcDatabase importDatabase = createDatabase(IMPORT_DATABASE);
            try {
                SupportSQLiteDatabase imported = importDatabase.getOpenHelper().getWritableDatabase();
                try (Cursor cursor = imported.query("PRAGMA wal_checkpoint(FULL)")) {
                    if (!cursor.moveToFirst() || cursor.getInt(0) != 0) {
                        throw new IOException("Arc database checkpoint is busy");
                    }
                }
            } finally {
                importDatabase.close();
            }

            checkpointDatabase();
            try {
                database.close();
            } finally {
                clearReferences();
            }
            deleteSidecars(databaseFile);
            Os.rename(importFile.getAbsolutePath(), databaseFile.getAbsolutePath());
        } catch (Exception e) {
            if (database == null) {
                try {
                    create();
                    database.getOpenHelper().getWritableDatabase();
                    ArcMessagesController.getInstance().refreshDaos();
                } catch (Exception restoreError) {
                    e.addSuppressed(restoreError);
                }
            }
            throw e;
        } finally {
            context.deleteDatabase(IMPORT_DATABASE);
        }
    }

    private static void validateImportDatabase(File importFile) throws IOException {
        try (SQLiteDatabase imported = SQLiteDatabase.openDatabase(importFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            if (!imported.isDatabaseIntegrityOk()) {
                throw new IOException("Arc database integrity check failed");
            }
            int version = imported.getVersion();
            if (version < ArcDatabase.MIN_SUPPORTED_VERSION || version > ArcDatabase.VERSION) {
                throw new IOException("Unsupported Arc database version: " + version);
            }
        }
    }

    private static void deleteSidecars(File databaseFile) throws IOException {
        String[] suffixes = {"-journal", "-shm", "-wal"};
        for (String suffix : suffixes) {
            File sidecar = new File(databaseFile.getAbsolutePath() + suffix);
            if (sidecar.exists() && !sidecar.delete()) {
                throw new IOException("Failed to delete Arc database sidecar");
            }
        }
    }

    public static synchronized void checkpointDatabase() throws IOException {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)")) {
            if (!cursor.moveToFirst() || cursor.getInt(0) != 0) {
                throw new IOException("Arc database checkpoint is busy");
            }
        }
    }

    public static long getDatabaseSize() {
        long size = 0;
        try {
            File dbFile = ApplicationLoader.applicationContext.getDatabasePath(ArcConstants.ARC_DATABASE);
            File shmCacheFile = new File(dbFile.getAbsolutePath() + "-shm");
            File walCacheFile = new File(dbFile.getAbsolutePath() + "-wal");
            if (dbFile.exists()) {
                size = dbFile.length();
            }
            if (shmCacheFile.exists()) {
                size += shmCacheFile.length();
            }
            if (walCacheFile.exists()) {
                size += walCacheFile.length();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return size;
    }

    public static long getAttachmentsDirSize() {
        long size = 0;
        try {
            if (ArcMessagesController.attachmentsPath.exists()) {
                size = AndroidUtil.getDirectorySize(ArcMessagesController.attachmentsPath);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return size;
    }

    public static void loadSizes(CoreExperimentalSettingsActivity bf) {
        loadSizes(bf::refreshArcDataSize);
    }

    public static void loadSizes(Runnable onLoaded) {
        Utilities.globalQueue.postRunnable(() -> {
            dbSize = getDatabaseSize();
            attachmentsSize = getAttachmentsDirSize();
            totalSize = dbSize + attachmentsSize;
            AndroidUtilities.runOnUIThread(onLoaded, 500);
        });
    }
}
