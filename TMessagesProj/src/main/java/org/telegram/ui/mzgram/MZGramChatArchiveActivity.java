/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Tracked chats > (a chat): every saved deleted message
 * and edit revision for that one dialog, most recent first. A deleted
 * message stops appearing in the chat itself once the server removes it
 * (standard Telegram behavior is unchanged), so this screen is how MZGram
 * makes it visible again -- there is no in-chat placeholder for it.
 *
 * Own screen: AyuGram4A shows this inline in the chat itself, rebuilding a
 * full chat bubble per entry through its proprietary AyuMessageUtils (a
 * private submodule, not part of the public source). MZGram lists the
 * saved text as a plain settings-style list instead.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramHistoryController;
import org.telegram.messenger.mzgram.MZGramHistoryMessage;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

public class MZGramChatArchiveActivity extends UniversalFragment {

    private static final int MAX_ROWS = 500;

    private final int accountId;
    private final long dialogId;
    private final List<MZGramHistoryMessage> entries;

    public MZGramChatArchiveActivity(int accountId, long dialogId) {
        this.accountId = accountId;
        this.dialogId = dialogId;
        this.entries = MZGramHistoryController.getInstance().getArchive(accountId, dialogId, MAX_ROWS);
    }

    @Override
    protected CharSequence getTitle() {
        TLObject object = getMessagesController().getUserOrChat(dialogId);
        if (object instanceof TLRPC.Chat) {
            return ((TLRPC.Chat) object).title;
        }
        if (object instanceof TLRPC.User) {
            return org.telegram.messenger.ContactsController.formatName(((TLRPC.User) object).first_name, ((TLRPC.User) object).last_name);
        }
        return getString(R.string.MZGramTrackedChats);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.MZGramChatArchive)));
        if (entries.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.MZGramChatArchiveEmpty)));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            MZGramHistoryMessage entry = entries.get(i);
            String time = LocaleController.getInstance().getFormatterDayMonth().format((long) entry.entityCreateDate * 1000L)
                    + ", " + LocaleController.getInstance().getFormatterDay().format((long) entry.entityCreateDate * 1000L);
            String kind = entry.kind == MZGramHistoryMessage.KIND_DELETED
                    ? getString(R.string.MZGramChatArchiveDeleted)
                    : getString(R.string.MZGramChatArchiveEdited);
            String preview = entry.text != null && !entry.text.isEmpty() ? entry.text : getString(R.string.MZGramMessageHistoryNoText);
            items.add(UItem.asButton(i + 1, kind + " · " + time, preview));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramChatArchiveInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        // Read-only list; nothing to do on tap.
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
