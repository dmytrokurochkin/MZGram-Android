/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Archive > "Clear Telegram local database".
 *
 * Telegram's own "Clear local database" (Settings > Data and Storage >
 * Storage Usage) keeps the latest messages of every chat and the chat list
 * itself. This clears the same, then the rest of the cached messages
 * (messages_v2) and the chat list (dialogs), so all of it loads again from
 * the server.
 *
 * MZGram's archive is a separate database (mzgram_history.db) with its files
 * in Downloads/MZGram/Saved Attachments, and is not touched. The rows go
 * straight through SQL, not through the deletion path, so nothing is taken
 * for a deleted message.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;

public class MZGramLocalDatabase {

    public static void erase(int account) {
        MessagesController.getInstance(account).clearQueryTime();
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.clearLocalDatabase();
        // Same queue, so this runs after the clearing above.
        storage.getStorageQueue().postRunnable(() -> {
            try {
                storage.getDatabase().executeFast("DELETE FROM messages_v2").stepThis().dispose();
                storage.getDatabase().executeFast("DELETE FROM dialogs").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
            storage.reset();
        });
    }
}
