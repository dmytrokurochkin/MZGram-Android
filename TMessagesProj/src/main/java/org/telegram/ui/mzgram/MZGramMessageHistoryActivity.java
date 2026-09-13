/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Shows every saved revision of one edited message, oldest first, each row
 * with the time it was captured and the text at that time. Opened from the
 * message's own long-press menu (MZGram: Message history), which only shows
 * up when MZGramHistoryController.hasHistory() finds something saved.
 *
 * Ported concept from AyuGram4A 7013145676d36d82ee13c02a89f72097b7490dcd
 * (ui/AyuMessageHistory.java, ui/AyuMessageCell.java). AyuGram4A rebuilds a
 * full TLRPC.Message/MessageObject per revision and renders it with the
 * normal chat message cell, through its proprietary AyuMessageUtils (a
 * private submodule, not part of the public source); MZGram instead lists
 * the saved text directly, without reconstructing a chat bubble.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramHistoryController;
import org.telegram.messenger.mzgram.MZGramHistoryMessage;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

public class MZGramMessageHistoryActivity extends UniversalFragment {

    private final int accountId;
    private final long dialogId;
    private final int messageId;
    private final List<MZGramHistoryMessage> revisions;

    public MZGramMessageHistoryActivity(int accountId, MessageObject messageObject) {
        this.accountId = accountId;
        this.dialogId = messageObject.getDialogId();
        this.messageId = messageObject.getId();
        this.revisions = MZGramHistoryController.getInstance().getRevisions(accountId, dialogId, messageId);
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramMessageHistory);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.MZGramMessageHistory)));
        if (revisions.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.MZGramMessageHistoryEmpty)));
            return;
        }
        for (int i = 0; i < revisions.size(); i++) {
            MZGramHistoryMessage revision = revisions.get(i);
            String time = LocaleController.getInstance().getFormatterDayMonth().format((long) revision.entityCreateDate * 1000L)
                    + ", " + LocaleController.getInstance().getFormatterDay().format((long) revision.entityCreateDate * 1000L);
            String preview = revision.text != null ? revision.text : getString(R.string.MZGramMessageHistoryNoText);
            items.add(UItem.asButton(i + 1, time, preview));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramMessageHistoryInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - 1;
        if (index < 0 || index >= revisions.size()) {
            return;
        }
        MZGramHistoryMessage revision = revisions.get(index);
        if (revision.text != null && !revision.text.isEmpty()) {
            AndroidUtilities.addToClipboard(revision.text);
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramMessageHistoryCopied)).show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
