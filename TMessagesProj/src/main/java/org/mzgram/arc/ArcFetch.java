/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.arc;

import org.mzgram.arc.messages.ArcMessagesController;
import org.mzgram.arc.messages.ArcSavePreferences;
import org.mzgram.arc.proprietary.ArcMessageUtils;
import org.mzgram.ext.ExtConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The files of the message archive (see ArcLimits).
 *
 * In advance: a file of a new message from someone else, in a chat the archive keeps
 * with media, is downloaded into Telegram's cache when it is not larger than the limit
 * of its source; when the message is deleted the archive copies it from there into
 * Saved Attachments as before.
 *
 * On deletion: a deleted message whose file is not on the device gets it downloaded
 * then, up to the limit plus ArcLimits.DELETION_EXTRA_MB, if the server still gives it.
 */
public final class ArcFetch {

    private ArcFetch() {
    }

    /** MessagesController.updateInterfaceWithMessages, on the UI thread: new messages of a chat. */
    public static void onNewMessages(int account, long dialogId, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty() || DialogObject.isEncryptedDialog(dialogId)
                || !ExtConfig.INSTANCE.getEnableSaveDeletedMessages().Bool()
                || !ExtConfig.INSTANCE.getMessageSavingSaveMedia().Bool()) {
            return;
        }
        final ArrayList<MessageObject> candidates = new ArrayList<>();
        for (MessageObject message : messages) {
            if (message != null && !message.isOut() && message.messageOwner != null && downloadable(message.messageOwner)) {
                candidates.add(message);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<MessageObject> toLoad = new ArrayList<>();
            for (MessageObject message : candidates) {
                try {
                    final long fromId = fromUserId(message.messageOwner);
                    if (!ArcMessageUtils.shouldSaveMedia(account, dialogId) || !ArcSavePreferences.saveDeletedMessageFor(account, dialogId, fromId)) {
                        continue;
                    }
                    final File path = FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
                    if (path != null && path.exists()) {
                        continue;
                    }
                    final int limit = ArcLimits.limitMb(sourceOf(account, dialogId, fromId));
                    if (limit != 0 && ArcLimits.fetches(fileSize(message.messageOwner), limit, false)) {
                        toLoad.add(message);
                    }
                } catch (Exception e) {
                    FileLog.e("ArcFetch.onNewMessages", e);
                }
            }
            if (!toLoad.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (MessageObject message : toLoad) {
                        load(account, message.messageOwner, message);
                    }
                });
            }
        });
    }

    /**
     * ArcMessagesController, after a deleted message was archived: counts a file the device
     * had, else downloads it now when the limit allows. documentType: ArcConstants.DOCUMENT_TYPE_*.
     */
    public static void afterDeletion(ArcSavePreferences prefs, int documentType, String mediaPath) {
        if (prefs == null || prefs.getMessage() == null
                || (documentType != ArcConstants.DOCUMENT_TYPE_PHOTO && documentType != ArcConstants.DOCUMENT_TYPE_FILE)) {
            return;
        }
        if (mediaPath != null) {
            ArcLimits.count(ArcLimits.SAVED_AHEAD);
            return;
        }
        final TLRPC.Message message = prefs.getMessage();
        final int account = prefs.getAccountId();
        if (!downloadable(message) || DialogObject.isEncryptedDialog(prefs.getDialogId())) {
            ArcLimits.count(ArcLimits.FAILED);
            return;
        }
        final int limit = ArcLimits.limitMb(sourceOf(account, prefs.getDialogId(), fromUserId(message)));
        if (!ArcLimits.fetches(fileSize(message), limit, true)) {
            return;
        }
        final String fileName = attachName(message);
        if (fileName == null) {
            ArcLimits.count(ArcLimits.FAILED);
            return;
        }
        pending.put(account + ":" + fileName, prefs);
        AndroidUtilities.runOnUIThread(() -> {
            addObserver(account);
            load(account, message, new MessageObject(account, message, false, false));
        });
    }

    /** FileRefController: the server link of a file could not be refreshed. */
    public static void noteExpired(String fileName) {
        ArcLimits.noteExpired(fileName);
    }

    // "account:fileName" -> the deleted message waiting for its file
    private static final ConcurrentHashMap<String, ArcSavePreferences> pending = new ConcurrentHashMap<>();
    private static final boolean[] observerAdded = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private static void addObserver(int account) {
        if (account < 0 || account >= observerAdded.length || observerAdded[account]) {
            return;
        }
        observerAdded[account] = true;
        NotificationCenter.NotificationCenterDelegate delegate = (id, acc, args) -> {
            if (args.length == 0 || !(args[0] instanceof String)) {
                return;
            }
            final String fileName = (String) args[0];
            if (id == NotificationCenter.fileLoadFailed && args.length > 1 && args[1] instanceof Integer && (Integer) args[1] == 1) {
                return; // canceled; it may start again
            }
            final ArcSavePreferences prefs = pending.remove(acc + ":" + fileName);
            if (prefs == null) {
                return;
            }
            if (id == NotificationCenter.fileLoadFailed) {
                ArcLimits.count(ArcLimits.failure(fileName));
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    final String path = ArcMessageUtils.saveFetchedAttachment(prefs);
                    if (path == null) {
                        ArcLimits.count(ArcLimits.FAILED);
                        return;
                    }
                    ArcMessagesController.getInstance().updateMediaPath(prefs.getUserId(), prefs.getDialogId(), prefs.getMessageId(), path);
                    ArcLimits.count(ArcLimits.SAVED_ON_DELETION);
                } catch (Exception e) {
                    FileLog.e("ArcFetch.saved", e);
                }
            });
        };
        NotificationCenter.getInstance(account).addObserver(delegate, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(account).addObserver(delegate, NotificationCenter.fileLoadFailed);
    }

    private static void load(int account, TLRPC.Message message, Object parent) {
        try {
            final TLRPC.MessageMedia media = message.media;
            if (media instanceof TLRPC.TL_messageMediaPhoto && media.photo != null) {
                final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize());
                if (size != null) {
                    FileLoader.getInstance(account).loadFile(ImageLocation.getForPhoto(size, media.photo), parent, "jpg", FileLoader.PRIORITY_LOW, 0);
                }
            } else if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
                FileLoader.getInstance(account).loadFile(media.document, parent, FileLoader.PRIORITY_LOW, 0);
            }
        } catch (Exception e) {
            FileLog.e("ArcFetch.load", e);
        }
    }

    /** A photo or a file the archive copies into Saved Attachments; not a sticker, a web page or a story. */
    private static boolean downloadable(TLRPC.Message message) {
        final TLRPC.MessageMedia media = message.media;
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return media.photo != null && !(media.photo instanceof TLRPC.TL_photoEmpty);
        }
        if (media instanceof TLRPC.TL_messageMediaDocument) {
            return media.document != null && !(media.document instanceof TLRPC.TL_documentEmpty)
                    && !MessageObject.isStickerMessage(message) && !MessageObject.isAnimatedStickerMessage(message)
                    && !"application/x-tgsticker".equals(media.document.mime_type);
        }
        return false;
    }

    private static long fileSize(TLRPC.Message message) {
        final TLRPC.MessageMedia media = message.media;
        if (media instanceof TLRPC.TL_messageMediaPhoto && media.photo != null) {
            final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize());
            return size != null ? size.size : 0;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            return media.document.size;
        }
        return 0;
    }

    private static String attachName(TLRPC.Message message) {
        final TLRPC.MessageMedia media = message.media;
        if (media instanceof TLRPC.TL_messageMediaPhoto && media.photo != null) {
            final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize());
            return size != null ? FileLoader.getAttachFileName(size) : null;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            return FileLoader.getAttachFileName(media.document);
        }
        return null;
    }

    private static long fromUserId(TLRPC.Message message) {
        return message != null && message.from_id != null ? message.from_id.user_id : 0;
    }

    /** ArcLimits' source: a bot's chat or a bot's message anywhere, else the kind of chat. */
    public static int sourceOf(int account, long dialogId, long fromUserId) {
        final MessagesController controller = MessagesController.getInstance(account);
        if (fromUserId > 0) {
            final TLRPC.User from = controller.getUser(fromUserId);
            if (from != null && from.bot) {
                return ArcLimits.BOT;
            }
        }
        if (dialogId > 0) {
            final TLRPC.User user = controller.getUser(dialogId);
            return user != null && user.bot ? ArcLimits.BOT : ArcLimits.USER;
        }
        final TLRPC.Chat chat = controller.getChat(-dialogId);
        return chat != null && ChatObject.isChannelAndNotMegaGroup(chat) ? ArcLimits.CHANNEL : ArcLimits.GROUP;
    }
}
