/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package org.mzgram.arc.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import org.mzgram.arc.database.dao.DeletedMessageDao;
import org.mzgram.arc.database.dao.EditedMessageDao;
import org.mzgram.arc.database.dao.LastSeenDao;
import org.mzgram.arc.database.dao.SpyDao;
import org.mzgram.arc.database.dao.DeletedDialogDao;
import org.mzgram.arc.database.entities.DeletedMessage;
import org.mzgram.arc.database.entities.DeletedMessageReaction;
import org.mzgram.arc.database.entities.EditedMessage;
import org.mzgram.arc.database.entities.LastSeenEntity;
import org.mzgram.arc.database.entities.SpyMessageRead;
import org.mzgram.arc.database.entities.SpyMessageContentsRead;
import org.mzgram.arc.database.entities.DeletedDialog;

@Database(entities = {
        EditedMessage.class,
        DeletedMessage.class,
        DeletedMessageReaction.class,
        LastSeenEntity.class,
        SpyMessageRead.class,
        SpyMessageContentsRead.class,
        DeletedDialog.class
}, version = ArcDatabase.VERSION)
public abstract class ArcDatabase extends RoomDatabase {
    public static final int MIN_SUPPORTED_VERSION = 21;
    public static final int VERSION = 27;

    public abstract EditedMessageDao editedMessageDao();

    public abstract DeletedMessageDao deletedMessageDao();

    public abstract LastSeenDao lastSeenDao();

    public abstract SpyDao spyDao();

    public abstract DeletedDialogDao deletedDialogDao();
}
