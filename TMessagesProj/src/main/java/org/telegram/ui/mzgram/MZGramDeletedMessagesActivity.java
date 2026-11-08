/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * "Deleted messages" of one chat: every message other people deleted there
 * that the archive keeps, newest first, with a search over their text. A tap
 * goes to the message in the chat (shown there with the deleted mark), a
 * long press copies its text. Opened from the chat's menu while Settings >
 * MZGram > Archive > "Deleted messages in the chat menu" is on.
 *
 * Idea from AyuGram (GPL-3.0), com/radolyn/ayugram/ui/AyuViewDeleted.java
 * (as in exteraless); written anew for MZGram on UniversalFragment.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramHistoryController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Locale;

public class MZGramDeletedMessagesActivity extends UniversalFragment {

    // The newest this many; older ones stay in the archive (export has all).
    private static final int LIMIT = 2000;
    private static final int SEARCH_ITEM = 1;

    private final long dialogId;
    private final ChatActivity chat;

    private final ArrayList<MessageObject> all = new ArrayList<>();
    private boolean loading = true;
    private String query;

    public static boolean isAvailable(TLRPC.User user, int chatMode) {
        return MZGramConfig.deletedMessagesList && MZGramConfig.saveDeletedMessages && chatMode == 0 && !UserObject.isUserSelf(user);
    }

    public MZGramDeletedMessagesActivity(ChatActivity chat, long dialogId) {
        this.chat = chat;
        this.dialogId = dialogId;
    }

    @Override
    public boolean onFragmentCreate() {
        final int account = currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<TLRPC.Message> messages = MZGramHistoryController.getInstance().deletedMessagesForList(account, dialogId, LIMIT);
            ArrayList<MessageObject> objects = new ArrayList<>(messages.size());
            for (int a = 0, N = messages.size(); a < N; a++) {
                try {
                    objects.add(new MessageObject(account, messages.get(a), false, false));
                } catch (Exception ignore) {
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                all.clear();
                all.addAll(objects);
                loading = false;
                if (listView != null) {
                    listView.adapter.update(false);
                }
            });
        });
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        ActionBarMenuItem searchItem = actionBar.createMenu().addItem(SEARCH_ITEM, R.drawable.outline_header_search).setIsSearchField(true);
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchCollapse() {
                setQuery(null);
            }

            @Override
            public void onTextChanged(EditText editText) {
                setQuery(editText.getText().toString());
            }
        });
        return view;
    }

    private void setQuery(String text) {
        String q = TextUtils.isEmpty(text) ? null : text.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.equals(q, query)) {
            return;
        }
        query = TextUtils.isEmpty(q) ? null : q;
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    private boolean matches(MessageObject object) {
        if (query == null) {
            return true;
        }
        CharSequence text = object.messageOwner != null && !TextUtils.isEmpty(object.messageOwner.message) ? object.messageOwner.message : object.messageText;
        return text != null && text.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramDeletedMessages);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(UItem.asShadow(getString(R.string.Loading)));
            return;
        }
        int shown = 0;
        for (int i = 0, N = all.size(); i < N; i++) {
            MessageObject object = all.get(i);
            if (matches(object)) {
                items.add(UItem.asSearchMessage(i + 1, object));
                shown++;
            }
        }
        if (shown == 0) {
            items.add(UItem.asShadow(getString(query != null ? R.string.MZGramDeletedMessagesNotFound : R.string.MZGramDeletedMessagesEmpty)));
        } else {
            items.add(UItem.asShadow(getString(R.string.MZGramDeletedMessagesInfo)));
        }
    }

    private MessageObject objectOf(UItem item) {
        int index = item.id - 1;
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        MessageObject object = objectOf(item);
        if (object == null) {
            return;
        }
        if (chat != null && chat.getDialogId() == dialogId) {
            int id = object.getId();
            finishFragment();
            chat.scrollToMessageId(id, 0, true, 0, true, 0);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        MessageObject object = objectOf(item);
        if (object == null || object.messageOwner == null || TextUtils.isEmpty(object.messageOwner.message)) {
            return false;
        }
        AndroidUtilities.addToClipboard(object.messageOwner.message);
        BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramMessageHistoryCopied)).show();
        return true;
    }
}
