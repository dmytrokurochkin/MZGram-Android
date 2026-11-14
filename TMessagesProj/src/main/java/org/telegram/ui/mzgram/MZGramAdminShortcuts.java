/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Quick admin actions: in a group or channel where you are an admin, the
 * three-dot menu of the chat gets Recent actions, Administrators and
 * Permissions (or Removed users in a channel), Members and Statistics, the
 * screens Telegram opens from the chat's Edit page; each can be turned off
 * on its own, as in exteraless. Off by default.
 *
 * "To the beginning" (its own switch, for every chat) jumps to the first
 * message.
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
import org.telegram.ui.StatisticActivity;

public final class MZGramAdminShortcuts {

    // Menu item ids in ChatActivity's three-dot menu
    public static final int RECENT_ACTIONS = 1104;
    public static final int ADMINISTRATORS = 1105;
    public static final int PERMISSIONS = 1106;
    public static final int MEMBERS = 1107;
    public static final int STATISTICS = 1108;
    public static final int TO_BEGINNING = 1109;

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
        if (ChatObject.isChannel(chat) && MZGramConfig.adminShortcutRecentActions) {
            // Telegram's own text: the names of the screens, as on the chat's Edit page
            menu.lazilyAddSubItem(RECENT_ACTIONS, R.drawable.msg_log, LocaleController.getString(R.string.EventLog));
        }
        if (MZGramConfig.adminShortcutAdministrators) {
            // Telegram's own text: the names of the screens, as on the chat's Edit page
            menu.lazilyAddSubItem(ADMINISTRATORS, R.drawable.msg_admins, LocaleController.getString(R.string.ChannelAdministrators));
        }
        if ((chat.creator || ChatObject.canBlockUsers(chat)) && MZGramConfig.adminShortcutPermissions) {
            // Telegram's own text: the names of the screens, as on the chat's Edit page
            menu.lazilyAddSubItem(PERMISSIONS, R.drawable.msg_permissions, LocaleController.getString(R.string.ChannelPermissions));
        }
        if (MZGramConfig.adminShortcutMembers) {
            // Telegram's own text: the names of the screens, as on the chat's Edit page
            menu.lazilyAddSubItem(MEMBERS, R.drawable.msg_groups, LocaleController.getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.ChannelSubscribers : R.string.GroupMembers));
        }
        if (ChatObject.isChannel(chat) && MZGramConfig.adminShortcutStatistics) {
            // Telegram's own text: the names of the screens, as on the chat's Edit page
            menu.lazilyAddSubItem(STATISTICS, R.drawable.msg_stats, LocaleController.getString(R.string.Statistics));
        }
    }

    /** "To the beginning": any chat, not a thread or the scheduled list. */
    public static void addToBeginning(ActionBarMenuItem menu, int chatMode, boolean thread) {
        if (MZGramConfig.chatMenuToBeginning && chatMode == ChatActivity.MODE_DEFAULT && !thread) {
            menu.lazilyAddSubItem(TO_BEGINNING, R.drawable.msg_go_up, LocaleController.getString(R.string.MZGramToTheBeginning));
        }
    }

    /** Opens the screen of a menu item; false when the id is not one of these. */
    public static boolean onItem(ChatActivity fragment, int id) {
        if (id == TO_BEGINNING) {
            fragment.scrollToMessageId(1, 0, false, 0, true, 0);
            return true;
        }
        TLRPC.Chat chat = fragment.getCurrentChat();
        if (chat == null || id != RECENT_ACTIONS && id != ADMINISTRATORS && id != PERMISSIONS && id != MEMBERS && id != STATISTICS) {
            return false;
        }
        if (id == RECENT_ACTIONS) {
            fragment.presentFragment(new ChannelAdminLogActivity(chat));
            return true;
        }
        if (id == STATISTICS) {
            fragment.presentFragment(StatisticActivity.create(chat, false));
            return true;
        }
        Bundle args = new Bundle();
        args.putLong("chat_id", chat.id);
        boolean broadcast = ChatObject.isChannelAndNotMegaGroup(chat);
        if (id == ADMINISTRATORS) {
            args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
        } else if (id == MEMBERS) {
            args.putInt("type", ChatUsersActivity.TYPE_USERS);
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
