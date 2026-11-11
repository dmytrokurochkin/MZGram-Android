/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Deleted chats: the chats that left the chat list while the archive keeps
 * deleted messages of them (MZGramDeletedDialogs), the most recently gone
 * first, each with the number of kept messages. A tap opens the chat, where
 * the kept messages show; when Telegram no longer knows the chat, the list
 * of its kept messages opens instead. A long press takes the chat off this
 * list (the messages stay in the archive).
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramDeletedDialogs;
import org.telegram.messenger.mzgram.MZGramHistoryDatabase;
import org.telegram.messenger.mzgram.MZGramLanguage;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MZGramDeletedDialogsActivity extends UniversalFragment {

    private final ArrayList<MZGramHistoryDatabase.DeletedDialog> dialogs = new ArrayList<>();
    private boolean loading = true;

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!loading) {
            load();
        }
    }

    private void load() {
        final int account = currentAccount;
        // The chats in the chat list now: those are not gone (any more)
        final HashSet<Long> shown = new HashSet<>();
        ArrayList<TLRPC.Dialog> all = MessagesController.getInstance(account).getAllDialogs();
        for (int a = 0, N = all.size(); a < N; a++) {
            TLRPC.Dialog dialog = all.get(a);
            if (dialog != null && dialog.top_message != 0) {
                ArrayList<MessageObject> msgs = MessagesController.getInstance(account).dialogMessage.get(dialog.id);
                if (msgs != null && !msgs.isEmpty() && msgs.get(0) != null && msgs.get(0).messageOwner != null && msgs.get(0).messageOwner.mzgramDeleted) {
                    continue;
                }
                shown.add(dialog.id);
            }
        }
        Utilities.globalQueue.postRunnable(() -> {
            List<MZGramHistoryDatabase.DeletedDialog> found = MZGramDeletedDialogs.list(account, shown);
            AndroidUtilities.runOnUIThread(() -> {
                dialogs.clear();
                dialogs.addAll(found);
                loading = false;
                if (listView != null) {
                    listView.adapter.update(false);
                }
            });
        });
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramDeletedDialogs);
    }

    private String titleOf(MZGramHistoryDatabase.DeletedDialog dialog) {
        String title = MZGramDeletedDialogs.titleOf(currentAccount, dialog.dialogId);
        if (TextUtils.isEmpty(title)) {
            title = dialog.title;
        }
        return TextUtils.isEmpty(title) ? String.valueOf(dialog.dialogId) : title;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(UItem.asShadow(getString(R.string.MZGramLoading)));
            return;
        }
        if (dialogs.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.MZGramDeletedDialogsEmpty)));
            return;
        }
        items.add(UItem.asHeader(getString(R.string.MZGramDeletedDialogsKept)));
        for (int i = 0, N = dialogs.size(); i < N; i++) {
            MZGramHistoryDatabase.DeletedDialog dialog = dialogs.get(i);
            items.add(UItem.asButton(i + 1, titleOf(dialog), String.valueOf(dialog.keptCount)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramDeletedDialogsInfo)));
    }

    private MZGramHistoryDatabase.DeletedDialog dialogOf(UItem item) {
        int index = item.id - 1;
        return index >= 0 && index < dialogs.size() ? dialogs.get(index) : null;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        MZGramHistoryDatabase.DeletedDialog dialog = dialogOf(item);
        if (dialog == null) {
            return;
        }
        long dialogId = dialog.dialogId;
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        // Telegram may not know the chat any more: then its kept messages
        if (!presentFragment(new ChatActivity(args))) {
            presentFragment(new MZGramDeletedMessagesActivity(null, dialogId));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        MZGramHistoryDatabase.DeletedDialog dialog = dialogOf(item);
        if (dialog == null || getParentActivity() == null) {
            return false;
        }
        final int account = currentAccount;
        final long dialogId = dialog.dialogId;
        String text = LocaleController.formatString(R.string.MZGramDeletedDialogsForget, titleOf(dialog))
                + "\n\n" + LocaleController.formatString(R.string.MZGramDeletedDialogsGoneAt, MZGramLanguage.formatDayMonthTime((long) dialog.deletedAt * 1000L));
        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.MZGramDeletedDialogs))
                .setMessage(text)
                .setPositiveButton(getString(R.string.MZGramDeletedDialogsForgetButton), (d, w) -> Utilities.globalQueue.postRunnable(() -> {
                    MZGramDeletedDialogs.forget(account, dialogId);
                    AndroidUtilities.runOnUIThread(this::load);
                }))
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
        return true;
    }
}
