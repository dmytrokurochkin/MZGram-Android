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

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;

import java.io.File;

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
}
