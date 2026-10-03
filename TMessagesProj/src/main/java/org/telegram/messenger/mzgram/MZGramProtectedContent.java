/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Message menu > "Forward and save protected content":
 * in chats and channels whose owner turned on "Restrict saving content",
 * messages and media can be forwarded, saved, copied and screenshotted like
 * anywhere else.
 *
 * The app's own checks ask MessagesController.isChatNoForwards /
 * isUserNoForwards and MessageObject.isNoforwards, which say "not
 * restricted" while the switch is on. The server still refuses to forward
 * such messages, so SendMessagesHelper hands them to sendCopies: each one is
 * sent again as a new message with its text and a fresh upload of its file,
 * downloading the file first when it is not on the device yet.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class MZGramProtectedContent {

    public static boolean isEnabled() {
        return MZGramConfig.saveProtectedContent;
    }

    // What the server enforces, whatever the switch says: the message's own
    // flag, or its chat's or user's "Restrict saving content".
    public static boolean isProtected(int account, MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return false;
        }
        if (message.messageOwner.noforwards) {
            return true;
        }
        return MessagesController.getInstance(account).isPeerNoForwardsOnServer(message.getDialogId());
    }

    // SendMessagesHelper.sendMessage, forwarding: sends the protected
    // messages as copies and returns the others, to be forwarded as usual.
    // With the switch off nothing is copied: the server refuses as before.
    public static ArrayList<MessageObject> sendCopies(int account, ArrayList<MessageObject> messages, long peer, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
        if (!isEnabled() || messages == null) {
            return messages;
        }
        ArrayList<MessageObject> rest = null;
        for (int a = 0, N = messages.size(); a < N; a++) {
            MessageObject message = messages.get(a);
            if (!isProtected(account, message)) {
                continue;
            }
            if (rest == null) {
                rest = new ArrayList<>(messages.subList(0, a));
            }
            sendCopy(account, message, peer, notify, scheduleDate, replyToTopMsg);
        }
        if (rest == null) {
            return messages;
        }
        for (int a = 0, N = messages.size(); a < N; a++) {
            MessageObject message = messages.get(a);
            if (!isProtected(account, message) && !rest.contains(message)) {
                rest.add(message);
            }
        }
        return rest;
    }

    private static void sendCopy(int account, MessageObject message, long peer, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
        TLRPC.Message owner = message.messageOwner;
        TLRPC.MessageMedia media = MessageObject.getMedia(owner);
        String text = owner.message != null ? owner.message : "";
        ArrayList<TLRPC.MessageEntity> entities = owner.entities != null && !owner.entities.isEmpty() ? new ArrayList<>(owner.entities) : null;
        boolean hasFile = media instanceof TLRPC.TL_messageMediaPhoto && media.photo instanceof TLRPC.TL_photo
                || media instanceof TLRPC.TL_messageMediaDocument && media.document instanceof TLRPC.TL_document;
        SendMessagesHelper helper = SendMessagesHelper.getInstance(account);
        if (!hasFile) {
            if (TextUtils.isEmpty(text)) {
                return; // nothing this can send again (a poll, a location...)
            }
            helper.sendMessage(SendMessagesHelper.SendMessageParams.of(text, peer, null, replyToTopMsg, null, true, entities, null, null, notify, scheduleDate, 0, null, false));
            log("sent a copy of the text of message " + owner.id + " to " + peer);
            return;
        }
        File file = localFile(account, owner);
        if (file != null) {
            sendFile(account, owner, file, text, entities, peer, notify, scheduleDate, replyToTopMsg);
            return;
        }
        // Not on the device yet: download it, then send.
        String name = FileLoader.getAttachFileName(fileObject(owner));
        Pending pending = new Pending(account, owner, text, entities, peer, notify, scheduleDate, replyToTopMsg);
        AndroidUtilities.runOnUIThread(() -> {
            pending.listen(name);
            FileLoader loader = FileLoader.getInstance(account);
            if (media.photo != null) {
                loader.loadFile(org.telegram.messenger.ImageLocation.getForPhoto(largestSize(media.photo), media.photo), message, "jpg", FileLoader.PRIORITY_HIGH, 0);
            } else {
                loader.loadFile(media.document, message, FileLoader.PRIORITY_HIGH, 0);
            }
            log("downloading message " + owner.id + " to send a copy to " + peer);
        });
    }

    private static TLRPC.PhotoSize largestSize(TLRPC.Photo photo) {
        return FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
    }

    private static TLObject fileObject(TLRPC.Message owner) {
        TLRPC.MessageMedia media = MessageObject.getMedia(owner);
        return media.photo != null ? largestSize(media.photo) : media.document;
    }

    // The downloaded file of the message, wherever the app keeps it.
    public static File localFile(int account, TLRPC.Message owner) {
        if (!TextUtils.isEmpty(owner.attachPath)) {
            File f = new File(owner.attachPath);
            if (f.exists() && f.length() > 0) {
                return f;
            }
        }
        FileLoader loader = FileLoader.getInstance(account);
        TLObject object = fileObject(owner);
        File[] candidates = {
            loader.getPathToMessage(owner),
            loader.getPathToMessage(owner, true, true),
            object != null ? loader.getPathToAttach(object, true) : null,
            object != null ? loader.getPathToAttach(object, false) : null,
        };
        for (File f : candidates) {
            if (f != null && f.exists() && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    // A new upload of the file, keeping what kind of media it is (photo,
    // video, voice, round video, sticker, file) and its caption.
    private static void sendFile(int account, TLRPC.Message owner, File file, String caption, ArrayList<TLRPC.MessageEntity> entities, long peer, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
        TLRPC.MessageMedia media = MessageObject.getMedia(owner);
        SendMessagesHelper helper = SendMessagesHelper.getInstance(account);
        String path = file.getAbsolutePath();
        if (media.photo != null) {
            TLRPC.TL_photo photo = helper.generatePhotoSizes(path, null);
            if (photo == null) {
                FileLog.e("MZGramProtectedContent: could not read photo of message " + owner.id);
                return;
            }
            helper.sendMessage(SendMessagesHelper.SendMessageParams.of(photo, path, peer, null, replyToTopMsg, caption, entities, null, null, notify, scheduleDate, 0, 0, null, false));
        } else {
            TLRPC.Document original = media.document;
            TLRPC.TL_document document = new TLRPC.TL_document();
            document.id = 0;
            document.access_hash = 0;
            document.file_reference = new byte[0];
            document.date = AccountInstance.getInstance(account).getConnectionsManager().getCurrentTime();
            document.mime_type = original.mime_type;
            document.size = file.length();
            document.dc_id = 0;
            document.attributes.addAll(original.attributes);
            HashMap<String, String> params = new HashMap<>();
            params.put("originalPath", path);
            helper.sendMessage(SendMessagesHelper.SendMessageParams.of(document, null, path, peer, null, replyToTopMsg, caption, entities, null, params, notify, scheduleDate, 0, 0, null, null, false));
        }
        log("sent a copy of message " + owner.id + " with its file to " + peer);
    }

    private static final class Pending implements NotificationCenter.NotificationCenterDelegate {
        final int account;
        final TLRPC.Message owner;
        final String text;
        final ArrayList<TLRPC.MessageEntity> entities;
        final long peer;
        final boolean notify;
        final int scheduleDate;
        final MessageObject replyToTopMsg;
        String name;

        Pending(int account, TLRPC.Message owner, String text, ArrayList<TLRPC.MessageEntity> entities, long peer, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
            this.account = account;
            this.owner = owner;
            this.text = text;
            this.entities = entities;
            this.peer = peer;
            this.notify = notify;
            this.scheduleDate = scheduleDate;
            this.replyToTopMsg = replyToTopMsg;
        }

        void listen(String name) {
            this.name = name;
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.addObserver(this, NotificationCenter.fileLoaded);
            center.addObserver(this, NotificationCenter.fileLoadFailed);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (args.length == 0 || !name.equals(args[0])) {
                return;
            }
            NotificationCenter center = NotificationCenter.getInstance(this.account);
            center.removeObserver(this, NotificationCenter.fileLoaded);
            center.removeObserver(this, NotificationCenter.fileLoadFailed);
            if (id != NotificationCenter.fileLoaded) {
                FileLog.e("MZGramProtectedContent: could not download message " + owner.id);
                return;
            }
            File file = args.length > 1 && args[1] instanceof File ? (File) args[1] : localFile(this.account, owner);
            if (file != null && file.exists()) {
                sendFile(this.account, owner, file, text, entities, peer, notify, scheduleDate, replyToTopMsg);
            }
        }
    }

    private static void log(String text) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramProtectedContent: " + text);
        }
    }
}
