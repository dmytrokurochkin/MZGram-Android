/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Message actions for the MZGram message menu. Ported from Nekogram
 * (tw.nekomimi.nekogram.helpers.MessageHelper, commit d769499).
 */

package org.telegram.messenger.mzgram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

public class MZGramMessageHelper {

    // Puts the downloaded media file of the message on the clipboard as a
    // content URI, so other apps can paste the image itself.
    public static void addMessageToClipboard(MessageObject messageObject, Runnable callback) {
        final String path = getPathToMessage(messageObject);
        if (!TextUtils.isEmpty(path)) {
            addFileToClipboard(new File(path), callback);
        }
    }

    public static void addFileToClipboard(File file, Runnable callback) {
        try {
            final Context context = ApplicationLoader.applicationContext;
            final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            final android.net.Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            final ClipData clip = ClipData.newUri(context.getContentResolver(), "label", uri);
            clipboard.setPrimaryClip(clip);
            callback.run();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // The first existing local file of the message: the sent file, the
    // downloaded message file, or the cached document.
    public static String getPathToMessage(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path) && !new File(path).exists()) {
            path = null;
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(messageObject.messageOwner).toString();
            if (!new File(path).exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument(), true).toString();
            if (!new File(path).exists()) {
                return null;
            }
        }
        return path;
    }

    // Deletes every local copy of the message media on a background thread,
    // then runs done on the UI thread so the cell can show its download button.
    public static void clearMessageFiles(MessageObject messageObject, Runnable done) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                for (File file : getFilesToMessage(messageObject)) {
                    if (file.exists() && !file.delete()) {
                        file.deleteOnExit();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            messageObject.checkMediaExistance();
            AndroidUtilities.runOnUIThread(done);
        });
    }

    private static ArrayList<File> getFilesToMessage(MessageObject messageObject) {
        final FileLoader fileLoader = FileLoader.getInstance(messageObject.currentAccount);
        final ArrayList<File> files = new ArrayList<>();
        if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
            files.add(new File(messageObject.messageOwner.attachPath));
        }
        files.add(fileLoader.getPathToMessage(messageObject.messageOwner));
        final TLRPC.Document document = messageObject.getDocument();
        if (document != null) {
            files.add(fileLoader.getPathToAttach(document, false));
            files.add(fileLoader.getPathToAttach(document, true));
        }
        final TLRPC.MessageMedia media = messageObject.messageOwner.media;
        if (media != null && media.alt_documents != null) {
            for (TLRPC.Document alt : media.alt_documents) {
                files.add(fileLoader.getPathToAttach(alt, false));
                files.add(fileLoader.getPathToAttach(alt, true));
            }
        }
        return files;
    }
}
