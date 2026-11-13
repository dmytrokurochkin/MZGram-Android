/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Quick admin actions: in a group or channel where you are an admin, the
 * three-dot menu of the chat gets Recent actions, Administrators and
 * Permissions (or Removed users in a channel), the screens Telegram opens
 * from the chat's Edit page. Off by default.
 */

package org.telegram.ui.mzgram;

import android.os.Bundle;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ChannelAdminLogActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatUsersActivity;

public final class MZGramAdminShortcuts {

    // Menu item ids in ChatActivity's three-dot menu
    public static final int RECENT_ACTIONS = 1104;
    public static final int ADMINISTRATORS = 1105;
    public static final int PERMISSIONS = 1106;

    private MZGramAdminShortcuts() {
    }

    public static boolean isAvailable(TLRPC.Chat chat, int chatMode) {
        return MZGramConfig.quickAdminShortcuts && chat != null && chatMode == ChatActivity.MODE_DEFAULT
                && (chat.creator || ChatObject.hasAdminRights(chat));
    }

    public static void addItems(ActionBarMenuItem menu, TLRPC.Chat chat, int chatMode) {
        if (!isAvailable(chat, chatMode)) {
            return;
        }
        if (ChatObject.isChannel(chat)) {
            // Telegram's own text: the names of the screens, as on the chat's Edit page
            menu.lazilyAddSubItem(RECENT_ACTIONS, R.drawable.msg_log, LocaleController.getString(R.string.EventLog));
        }
        // Telegram's own text: the names of the screens, as on the chat's Edit page
        menu.lazilyAddSubItem(ADMINISTRATORS, R.drawable.msg_admins, LocaleController.getString(R.string.ChannelAdministrators));
        if (chat.creator || ChatObject.canBlockUsers(chat)) {
            // Telegram's own text: the names of the screens, as on the chat's Edit page
            menu.lazilyAddSubItem(PERMISSIONS, R.drawable.msg_permissions, LocaleController.getString(R.string.ChannelPermissions));
        }
    }

    /** Opens the screen of a menu item; false when the id is not one of these. */
    public static boolean onItem(ChatActivity fragment, int id) {
        TLRPC.Chat chat = fragment.getCurrentChat();
        if (chat == null || id != RECENT_ACTIONS && id != ADMINISTRATORS && id != PERMISSIONS) {
            return false;
        }
        if (id == RECENT_ACTIONS) {
            fragment.presentFragment(new ChannelAdminLogActivity(chat));
            return true;
        }
        Bundle args = new Bundle();
        args.putLong("chat_id", chat.id);
        boolean broadcast = ChatObject.isChannelAndNotMegaGroup(chat);
        if (id == ADMINISTRATORS) {
            args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
        } else {
            // As the chat's Edit page: permissions of a group, removed users of a channel
            args.putInt("type", !broadcast && !chat.gigagroup || ChatObject.isCommunity(chat) ? ChatUsersActivity.TYPE_KICKED : ChatUsersActivity.TYPE_BANNED);
        }
        ChatUsersActivity users = new ChatUsersActivity(args);
        users.setInfo(fragment.getCurrentChatInfo());
        fragment.presentFragment(users);
        return true;
    }
}
