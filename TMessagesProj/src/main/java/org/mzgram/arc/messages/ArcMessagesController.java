/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package org.mzgram.arc.messages;

import android.os.Environment;
import android.text.TextUtils;

import org.mzgram.arc.ArcConstants;
import org.mzgram.arc.ArcUtils;
import org.mzgram.arc.database.ArcData;
import org.mzgram.arc.database.dao.DeletedMessageDao;
import org.mzgram.arc.database.dao.EditedMessageDao;
import org.mzgram.arc.database.entities.DeletedMessage;
import org.mzgram.arc.database.entities.DeletedMessageFull;
import org.mzgram.arc.database.entities.DeletedMessageReaction;
import org.mzgram.arc.database.entities.EditedMessage;
import org.mzgram.arc.proprietary.ArcMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.mzgram.core.utils.FileUtil;

public class ArcMessagesController {
    public static final String attachmentsSubfolder = "Saved Attachments";
    public static final File attachmentsPath = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ArcConstants.APP_NAME), attachmentsSubfolder);
    private static ArcMessagesController instance;
    private EditedMessageDao editedMessageDao;
    private DeletedMessageDao deletedMessageDao;

    private ArcMessagesController() {
        initializeAttachmentsFolder();
        ArcSavePreferences.loadAllExclusions();

        refreshDaos();
    }

    public void refreshDaos() {
        editedMessageDao = ArcData.getEditedMessageDao();
        deletedMessageDao = ArcData.getDeletedMessageDao();
    }

    private <T> T withDaoRetry(String tag, Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            FileLog.e(tag, e);
        }

        try {
            refreshDaos();
            return callable.call();
        } catch (Exception e) {
            FileLog.e(tag, e);
        }

        return null;
    }

    private static void initializeAttachmentsFolder() {
        try {
            File nomediaFile = new File(attachmentsPath, ".nomedia");
            if (attachmentsPath.exists() || attachmentsPath.mkdirs()) {
                AndroidUtilities.createEmptyFile(nomediaFile);
            }
            if (!nomediaFile.exists()) {
                File randomFile = new File(attachmentsPath, ArcUtils.generateRandomString(4));
                AndroidUtilities.createEmptyFile(randomFile);
                if (!randomFile.renameTo(nomediaFile)) {
                    if (!randomFile.delete()) {
                        randomFile.deleteOnExit();
                    }
                    FileLog.e("Failed to rename random .nomedia file to the correct name");
                } else {
                    FileLog.d("Created .nomedia file in attachments folder by renaming a random file");
                }
            } else {
                FileLog.d(".nomedia file already exists in attachments folder");
            }
        } catch (Exception e) {
            FileLog.e("initializeAttachmentsFolder", e);
        }
    }

    public static ArcMessagesController getInstance() {
        if (instance == null) {
            instance = new ArcMessagesController();
        }
        return instance;
    }

    public void onMessageEdited(ArcSavePreferences prefs, TLRPC.Message newMessage) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                onMessageEditedInner(prefs, newMessage, false);
            } catch (Exception e) {
                FileLog.e("onMessageEdited", e);
            }
        });
    }

    public void onMessageEditedForce(ArcSavePreferences prefs) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                onMessageEditedInner(prefs, prefs.getMessage(), true);
            } catch (Exception e) {
                FileLog.e("onMessageEditedForce", e);
            }
        });
    }

    private void onMessageEditedInner(ArcSavePreferences prefs, TLRPC.Message newMessage, boolean force) {
        if (!ArcSavePreferences.saveEditedMessageFor(prefs.getDialogId(), prefs.getFromUserId())) {
            return;
        }
        if (!ArcSavePreferences.keepsMessage(prefs.getAccountId(), prefs.getDialogId(), prefs.getFromUserId())) {
            return;
        }
        var oldMessage = prefs.getMessage();

        boolean sameMedia = isSameMedia(newMessage, force, oldMessage);

        if (sameMedia && TextUtils.equals(oldMessage.message, newMessage.message)) {
            return;
        }

        var revision = new EditedMessage();
        ArcMessageUtils.map(prefs, revision);
        ArcMessageUtils.mapMedia(prefs, revision, !sameMedia);

        if (!sameMedia && !TextUtils.isEmpty(revision.mediaPath)) {
            var lastRevision = withDaoRetry(
                    "onMessageEditedInner#getLastRevision",
                    () -> editedMessageDao.getLastRevision(prefs.getUserId(), prefs.getDialogId(), prefs.getMessageId())
            );

            if (lastRevision != null && !TextUtils.equals(revision.mediaPath, lastRevision.mediaPath) && lastRevision.mediaPath != null && !lastRevision.mediaPath.contains(attachmentsSubfolder)) {
                // update previous revisions to reflect media change
                // like, there's no previous file, so replace it with one we copied before...
                withDaoRetry(
                        "onMessageEditedInner#updateAttachmentForRevisionsBetweenDates",
                        () -> {
                            editedMessageDao.updateAttachmentForRevisionsBetweenDates(prefs.getUserId(), prefs.getDialogId(), prefs.getMessageId(), lastRevision.mediaPath, revision.mediaPath);
                            return null;
                        }
                );
            }
        }

        withDaoRetry(
                "onMessageEditedInner#insert",
                () -> {
                    editedMessageDao.insert(revision);
                    return null;
                }
        );

        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(prefs.getAccountId()).postNotificationName(ArcConstants.MESSAGE_EDITED_NOTIFICATION, prefs.getDialogId(), prefs.getMessageId()));
    }

    private static boolean isSameMedia(TLRPC.Message newMessage, boolean force, TLRPC.Message oldMessage) {
        boolean sameMedia = oldMessage.media == newMessage.media ||
                (oldMessage.media != null && newMessage.media != null && oldMessage.media.getClass() == newMessage.media.getClass());
        if (oldMessage.media instanceof TLRPC.TL_messageMediaPhoto && newMessage.media instanceof TLRPC.TL_messageMediaPhoto && oldMessage.media.photo != null && newMessage.media.photo != null) {
            sameMedia = oldMessage.media.photo.id == newMessage.media.photo.id;
        } else if (oldMessage.media instanceof TLRPC.TL_messageMediaDocument && newMessage.media instanceof TLRPC.TL_messageMediaDocument && oldMessage.media.document != null && newMessage.media.document != null) {
            sameMedia = oldMessage.media.document.id == newMessage.media.document.id;
        }

        if (force) {
            sameMedia = false;
        }
        return sameMedia;
    }

    public void onMessageDeleted(ArcSavePreferences prefs) {
        onMessageDeleted(prefs, true);
    }

    public void onMessageDeleted(ArcSavePreferences prefs, boolean useQueue) {
        if (prefs.getMessage() == null) {
            return;
        }
        try {
            if (useQueue) {
                Utilities.globalQueue.postRunnable(() -> onMessageDeletedInner(prefs));
            } else {
                onMessageDeletedInner(prefs);
            }
        } catch (Exception e) {
            FileLog.e("onMessageDeleted", e);
        }
    }

    private void onMessageDeletedInner(ArcSavePreferences prefs) {
        if (!ArcSavePreferences.saveDeletedMessageFor(prefs.getAccountId(), prefs.getDialogId(), prefs.getFromUserId())) {
            return;
        }

        Boolean exists = withDaoRetry(
                "onMessageDeletedInner#exists",
                () -> deletedMessageDao.exists(prefs.getUserId(), prefs.getDialogId(), prefs.getTopicId(), prefs.getMessageId())
        );

        if (exists == null || exists) {
            return;
        }

        var deletedMessage = new DeletedMessage();
        deletedMessage.userId = prefs.getUserId();
        deletedMessage.dialogId = prefs.getDialogId();
        deletedMessage.messageId = prefs.getMessageId();
        deletedMessage.entityCreateDate = prefs.getRequestCatchTime();

        var msg = prefs.getMessage();

        FileLog.d("saving message " + prefs.getMessageId() + " for " + prefs.getDialogId() + " with topic " + prefs.getTopicId());

        ArcMessageUtils.map(prefs, deletedMessage);
        ArcMessageUtils.mapMedia(prefs, deletedMessage, true);

        Long fakeMsgId = withDaoRetry(
                "onMessageDeletedInner#insert",
                () -> deletedMessageDao.insert(deletedMessage)
        );

        if (fakeMsgId == null) {
            return;
        }

        if (msg != null && msg.reactions != null) {
            processDeletedReactions(fakeMsgId, msg.reactions);
        }

        // MZGram: count the file the device had, else download it now (Download in advance)
        org.mzgram.arc.ArcFetch.afterDeletion(prefs, deletedMessage.documentType, deletedMessage.mediaPath);
    }

    private void processDeletedReactions(long fakeMessageId, TLRPC.TL_messageReactions reactions) {
        for (var reaction : reactions.results) {
            if (reaction.reaction instanceof TLRPC.TL_reactionEmpty) {
                continue;
            }

            var deletedReaction = new DeletedMessageReaction();
            deletedReaction.deletedMessageId = fakeMessageId;
            deletedReaction.count = reaction.count;
            deletedReaction.selfSelected = reaction.chosen;

            if (reaction.reaction instanceof TLRPC.TL_reactionEmoji) {
                deletedReaction.emoticon = ((TLRPC.TL_reactionEmoji) reaction.reaction).emoticon;
            } else if (reaction.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                deletedReaction.documentId = ((TLRPC.TL_reactionCustomEmoji) reaction.reaction).document_id;
                deletedReaction.isCustom = true;
            } else {
                continue;
            }

            withDaoRetry(
                    "processDeletedReactions#insertReaction",
                    () -> {
                        deletedMessageDao.insertReaction(deletedReaction);
                        return null;
                    }
            );
        }
    }

    public boolean hasAnyRevisions(long userId, long dialogId, int messageId) {
        return editedMessageDao.hasAnyRevisions(userId, dialogId, messageId);
    }

    public List<EditedMessage> getRevisions(long userId, long dialogId, int messageId) {
        return editedMessageDao.getAllRevisions(userId, dialogId, messageId);
    }

    public DeletedMessageFull getMessage(long userId, long dialogId, int messageId) {
        return deletedMessageDao.getMessage(userId, dialogId, messageId);
    }

    public String getMediaPath(long userId, long dialogId, int messageId) {
        return deletedMessageDao.getMediaPath(userId, dialogId, messageId);
    }

    public List<DeletedMessageFull> getMessages(long userId, long dialogId, long startId, long endId, int limit) {
        return deletedMessageDao.getMessages(userId, dialogId, startId, endId, limit);
    }

    public List<DeletedMessageFull> getTopicMessages(long userId, long dialogId, long topicId, long startId, long endId, int limit) {
        return deletedMessageDao.getTopicMessages(userId, dialogId, topicId, startId, endId, limit);
    }

    public List<DeletedMessageFull> getThreadMessages(long userId, long dialogId, long threadMessageId, long startId, long endId, int limit) {
        return deletedMessageDao.getThreadMessages(userId, dialogId, threadMessageId, startId, endId, limit);
    }

    public List<DeletedMessageFull> getMessagesGroupedIn(long userId, long dialogId, List<Long> groupedIds) {
        if (groupedIds == null || groupedIds.isEmpty()) {
            return new ArrayList<>();
        }
        return deletedMessageDao.getMessagesGroupedIn(userId, dialogId, groupedIds);
    }

    public List<Integer> getExistingMessageIds(long userId, long dialogId, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return new ArrayList<>();
        }
        return deletedMessageDao.getExistingMessageIds(userId, dialogId, messageIds);
    }

    public List<DeletedMessageFull> getMessagesByIds(long userId, long dialogId, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return new ArrayList<>();
        }
        return deletedMessageDao.getMessagesByIds(userId, dialogId, messageIds);
    }

    public void delete(long userId, long dialogId, int messageId) {
        var msg = getMessage(userId, dialogId, messageId);
        if (msg == null) {
            return;
        }

        deletedMessageDao.delete(userId, dialogId, messageId);

        if (!TextUtils.isEmpty(msg.message.mediaPath)) {
            var p = new File(msg.message.mediaPath);
            try {
                if (p.exists() && !p.delete()) {
                    p.deleteOnExit();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void deleteMessages(long userId, long dialogId, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        deletedMessageDao.deleteMessages(userId, dialogId, messageIds);
        editedMessageDao.deleteByDialogIdAndMessageIds(dialogId, messageIds);

        for (int messageId : messageIds) {
            var msg = getMessage(userId, dialogId, messageId);
            if (msg == null) {
                continue;
            }

            if (!TextUtils.isEmpty(msg.message.mediaPath)) {
                var p = new File(msg.message.mediaPath);
                try {
                    if (p.exists() && !p.delete()) {
                        p.deleteOnExit();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void deleteRevision(long fakeId) {
        String mediaPath = editedMessageDao.getMediaPathByFakeId(fakeId);
        int deleted = editedMessageDao.deleteByFakeId(fakeId);
        if (deleted == 0) {
            return;
        }
        if (!TextUtils.isEmpty(mediaPath)) {
            File p = new File(mediaPath);
            try {
                if (p.exists() && !p.delete()) {
                    p.deleteOnExit();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void deleteCurrent(long dialogId, long mergeDialogId, Runnable callback) {
        List<DeletedMessageFull> messages = deletedMessageDao.getMessagesByDialog(dialogId);

        if (mergeDialogId != 0) {
            List<DeletedMessageFull> mergeMessages = deletedMessageDao.getMessagesByDialog(mergeDialogId);
            messages.addAll(mergeMessages);
        }

        // Delete messages and their edit history from database
        deletedMessageDao.delete(dialogId);
        editedMessageDao.delete(dialogId);

        if (mergeDialogId != 0) {
            deletedMessageDao.delete(mergeDialogId);
            editedMessageDao.delete(mergeDialogId);
        }

        // Clean up media files
        for (DeletedMessageFull msg : messages) {
            if (msg.message.mediaPath != null && !msg.message.mediaPath.isEmpty()) {
                File mediaFile = new File(msg.message.mediaPath);
                try {
                    if (mediaFile.exists() && !mediaFile.delete()) {
                        mediaFile.deleteOnExit();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        if (callback != null) {
            callback.run();
        }
    }

    public boolean isArcDeletedMessageId(long userId, long dialogId, int messageId) {
        if (userId == 0 || dialogId == 0 || messageId == 0) {
            return false;
        }
        return ArcMessagesController.getInstance().getMessage(userId, dialogId, messageId) != null;
    }

    public int getDeletedCount(long userId, long dialogId) {
        return deletedMessageDao.countByDialog(userId, dialogId);
    }

    public List<DeletedMessageFull> getLatestMessages(long userId, long dialogId, int limit) {
        return deletedMessageDao.getLatestMessages(userId, dialogId, limit);
    }

    public List<DeletedMessageFull> getOlderMessagesBefore(long userId, long dialogId, int before, int limit) {
        return deletedMessageDao.getOlderMessagesBefore(userId, dialogId, before, limit);
    }

    public void updateMediaPath(long userId, long dialogId, int messageId, String newPath) {
        deletedMessageDao.updateMediaPathIfEmpty(userId, dialogId, messageId, newPath);
    }

    public void clean() {
        ArcData.clean();
        ArcData.create();

        refreshDaos();

        cleanAttachmentsFolder();

        // force to recreate a database to avoid crash
        instance = null;
    }

    private void cleanAttachmentsFolder() {
        FileUtil.deleteDirectory(attachmentsPath);
        initializeAttachmentsFolder();
    }
}
