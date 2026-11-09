/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Icons of the chat folders: Telegram for Android drops the emoticon the
 * server keeps for each folder (set in Telegram Desktop or iOS), so MZGram
 * remembers it per account when the folders come from the server. Only read
 * here; nothing is sent back, the folders on the server stay as they are.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

public final class MZGramFolderEmoticons {

    private static final String FEATURE = "folder_icons";
    private static final String FETCHED = "fetched";

    private MZGramFolderEmoticons() {
    }

    private static boolean valid(int account) {
        return account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT;
    }

    /** Called on the storage queue for every folder that came from the server. */
    public static void remember(int account, int filterId, String emoticon) {
        if (!valid(account)) {
            return;
        }
        final MZGramAccountPrefs prefs = MZGramAccountPrefs.of(FEATURE, account);
        final String key = prefs.key("f" + filterId);
        final String old = prefs.preferences().getString(key, null);
        if (TextUtils.isEmpty(emoticon)) {
            if (old != null) {
                prefs.preferences().edit().remove(key).apply();
                redrawTabs(account);
            }
        } else if (!emoticon.equals(old)) {
            prefs.preferences().edit().putString(key, emoticon).apply();
            redrawTabs(account);
        }
        if (!prefs.preferences().getBoolean(prefs.key(FETCHED), false)) {
            prefs.preferences().edit().putBoolean(prefs.key(FETCHED), true).apply();
        }
    }

    private static final Runnable[] redraws = new Runnable[UserConfig.MAX_ACCOUNT_COUNT];

    // Telegram redraws the tabs only when a name or the chats change; a new
    // icon alone is shown with one coalesced update of the folders
    private static void redrawTabs(int account) {
        if (MZGramConfig.folderTabIcons == 0) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (redraws[account] != null) {
                AndroidUtilities.cancelRunOnUIThread(redraws[account]);
            }
            redraws[account] = () -> {
                redraws[account] = null;
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
            };
            AndroidUtilities.runOnUIThread(redraws[account], 300);
        });
    }

    public static String get(int account, int filterId) {
        if (!valid(account)) {
            return null;
        }
        final MZGramAccountPrefs prefs = MZGramAccountPrefs.of(FEATURE, account);
        return prefs.preferences().getString(prefs.key("f" + filterId), null);
    }

    /**
     * Telegram asks the server for the folders once and then only on changes,
     * so the emoticons of folders made before MZGram are read with one fresh
     * request per account, the first time the icons are shown.
     */
    public static void ensureLoaded(int account) {
        if (!valid(account)) {
            return;
        }
        final MZGramAccountPrefs prefs = MZGramAccountPrefs.of(FEATURE, account);
        if (prefs.preferences().getBoolean(prefs.key(FETCHED), false)) {
            return;
        }
        // Marked here as well, so a failed request is not repeated on every redraw
        prefs.preferences().edit().putBoolean(prefs.key(FETCHED), true).apply();
        MessagesController.getInstance(account).loadRemoteFilters(true);
    }
}
