/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > General > Chat archive:
 * - Hide archive from the chat list: the Archived chats row is not in the
 *   chat list at all (MessagesController.addDialogToItsFolder leaves it out,
 *   hasHiddenArchive is false, so there is nothing to pull out either); the
 *   three-dots menu of the chat list gets Archived chats instead. Chats stay
 *   archived as they are. Off by default.
 * - Disable "Unarchive" swipe: in the archive a swipe does nothing, so no
 *   chat leaves it by accident (DialogsActivity's swipe flags). Off by default.
 *
 * Idea from exteraless (GPL-3.0), "Hide archive" and "Disable unarchive
 * swipe"; written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

public final class MZGramArchiveRow {

    private MZGramArchiveRow() {
    }

    // The switch changed: every signed-in account sorts its chats again, so
    // the row comes or goes at once
    public static void apply() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            MessagesController controller = MessagesController.getInstance(a);
            controller.sortDialogs(null);
            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
        }
    }
}
