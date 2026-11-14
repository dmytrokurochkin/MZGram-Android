/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * More items of the three-dot menu of a chat, each with its own switch in
 * Settings > MZGram > Chats, as in exteraless (GPL-3.0): the linked chat
 * (a channel's discussion group or a group's channel), Go to message (by its
 * number), Hide title (the chat's name and photo at the top, until the chat
 * is opened again), Clear deleted (this chat's kept deleted messages leave
 * the archive) and Delete my messages (MZGramDeleteOwnMessages). Off by
 * default. Telegram's own Boost item can be turned off too (on by default).
 */

package org.telegram.ui.mzgram;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramHistoryController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.EditTextBoldCursor;

public final class MZGramChatMenu {

    public static final int LINKED_CHAT = 1110;
    public static final int GO_TO_MESSAGE = 1111;
    public static final int HIDE_TITLE = 1112;
    public static final int CLEAR_DELETED = 1113;
    public static final int DELETE_OWN = 1114;

    private MZGramChatMenu() {
    }

    /** Telegram's Boost item: on by default, as in Telegram. */
    public static boolean showsBoost() {
        return MZGramConfig.chatMenuBoost;
    }

    // A channel with its discussion group, or a group with its channel
    static boolean hasLinkedChat(TLRPC.Chat chat, TLRPC.ChatFull info) {
        return chat != null && (chat.has_link || info != null && info.linked_chat_id != 0);
    }

    // Groups only, the whole chat (not a thread or a topic): what was sent
    // in a channel is the channel's, not the person's
    static boolean canDeleteOwn(TLRPC.Chat chat, int chatMode, boolean thread) {
        return chat != null && chatMode == ChatActivity.MODE_DEFAULT && !thread && !ChatObject.isMonoForum(chat)
                && (ChatObject.isMegagroup(chat) || !ChatObject.isChannel(chat));
    }

    public static void addItems(ActionBarMenuItem menu, TLRPC.Chat chat, TLRPC.ChatFull info, long dialogId, int chatMode, boolean thread) {
        if (MZGramConfig.chatMenuLinkedChat && chatMode == ChatActivity.MODE_DEFAULT && hasLinkedChat(chat, info)) {
            // Telegram's own text: what the group's profile calls its linked channel
            if (chat.megagroup) {
                menu.lazilyAddSubItem(LINKED_CHAT, R.drawable.msg_channel, LocaleController.getString(R.string.LinkedChannel));
            } else {
                // Telegram's own text: what the channel's profile calls its discussion group
                menu.lazilyAddSubItem(LINKED_CHAT, R.drawable.msg_groups, LocaleController.getString(R.string.Discussion));
            }
        }
        if (MZGramConfig.chatMenuGoToMessage && chatMode == ChatActivity.MODE_DEFAULT && !thread) {
            menu.lazilyAddSubItem(GO_TO_MESSAGE, R.drawable.msg_go_up, LocaleController.getString(R.string.MZGramGoToMessage));
        }
        if (MZGramConfig.chatMenuHideTitle) {
            menu.lazilyAddSubItem(HIDE_TITLE, R.drawable.msg_stories_myhide, LocaleController.getString(R.string.MZGramHideTitle));
        }
        if (MZGramConfig.chatMenuClearDeleted && MZGramConfig.saveDeletedMessages && chatMode == ChatActivity.MODE_DEFAULT
                && dialogId != UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) {
            menu.lazilyAddSubItem(CLEAR_DELETED, R.drawable.msg_clear, LocaleController.getString(R.string.MZGramClearDeleted));
        }
        if (MZGramConfig.chatMenuDeleteOwn && canDeleteOwn(chat, chatMode, thread)) {
            menu.lazilyAddSubItem(DELETE_OWN, R.drawable.msg_delete, LocaleController.getString(R.string.MZGramDeleteOwn));
        }
    }

    /** Does what a menu item of these asks; false when the id is not one of them. */
    public static boolean onItem(ChatActivity fragment, int id) {
        if (id == LINKED_CHAT) {
            openLinkedChat(fragment);
        } else if (id == GO_TO_MESSAGE) {
            askMessageNumber(fragment);
        } else if (id == HIDE_TITLE) {
            hideTitle(fragment);
        } else if (id == CLEAR_DELETED) {
            askClearDeleted(fragment);
        } else if (id == DELETE_OWN) {
            MZGramDeleteOwnMessages.ask(fragment, fragment.getCurrentChat(), fragment.getMergeDialogId());
        } else {
            return false;
        }
        return true;
    }

    private static void openLinkedChat(ChatActivity fragment) {
        TLRPC.ChatFull info = fragment.getCurrentChatInfo();
        if (info == null || info.linked_chat_id == 0) {
            return; // the chat's full info is not loaded yet
        }
        Bundle args = new Bundle();
        args.putLong("chat_id", info.linked_chat_id);
        if (MessagesController.getInstance(fragment.getCurrentAccount()).checkCanOpenChat(args, fragment)) {
            fragment.presentFragment(new ChatActivity(args));
        }
    }

    // Ported from exteraless: a message number, then the chat jumps to it
    private static void askMessageNumber(ChatActivity fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        Theme.ResourcesProvider resources = fragment.getResourceProvider();
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resources);
        builder.setTitle(LocaleController.getString(R.string.MZGramGoToMessage));
        EditTextBoldCursor editText = new EditTextBoldCursor(fragment.getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        editText.setHintText(LocaleController.getString(R.string.MZGramMessageNumber));
        editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resources));
        editText.setSingleLine(true);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resources),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resources),
                Theme.getColor(Theme.key_text_RedRegular, resources));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackground(null);
        editText.setPadding(0, 0, 0, 0);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        FrameLayout frame = new FrameLayout(fragment.getParentActivity());
        frame.addView(editText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(36), Gravity.CENTER_VERTICAL));
        frame.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        builder.setView(frame);
        builder.setPositiveButton(LocaleController.getString(R.string.MZGramOk), (dialog, which) -> {
            int number = messageNumber(editText.getText() == null ? null : editText.getText().toString());
            if (number > 0) {
                fragment.scrollToMessageId(number, 0, true, 0, true, 0);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        fragment.showDialog(dialog);
    }

    /** The number typed in Go to message; 0 when it is not one. */
    public static int messageNumber(String text) {
        if (text == null) {
            return 0;
        }
        try {
            int number = Integer.parseInt(text.trim());
            return Math.max(number, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // The name and photo at the top are hidden until the chat is opened
    // again; the status line stays (for screenshots)
    private static void hideTitle(ChatActivity fragment) {
        ChatAvatarContainer container = fragment.getAvatarContainer();
        if (container == null) {
            return;
        }
        if (container.getTitleTextView() != null) {
            container.getTitleTextView().setVisibility(View.INVISIBLE);
        }
        BackupImageView avatar = container.getAvatarImageView();
        if (avatar != null) {
            avatar.setVisibility(View.GONE);
        }
    }

    private static void askClearDeleted(ChatActivity fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        Theme.ResourcesProvider resources = fragment.getResourceProvider();
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resources);
        builder.setTitle(LocaleController.getString(R.string.MZGramClearDeleted));
        builder.setMessage(LocaleController.getString(R.string.MZGramClearDeletedText));
        builder.setPositiveButton(LocaleController.getString(R.string.MZGramDelete), (dialog, which) -> {
            int account = fragment.getCurrentAccount();
            long dialogId = fragment.getDialogId();
            long mergeDialogId = fragment.getMergeDialogId();
            MZGramHistoryController.getInstance().clearDeletedInChat(account, dialogId, mergeDialogId, count -> {
                // The chat shows them until it is opened again: close it
                if (!fragment.isFinishing()) {
                    fragment.finishFragment();
                }
                // On the screen below, once the chat has gone
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.global().createSimpleBulletin(R.raw.ic_delete,
                        LocaleController.formatString(R.string.MZGramClearDeletedDone, count)).show(), 400);
            });
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resources));
        }
    }
}
