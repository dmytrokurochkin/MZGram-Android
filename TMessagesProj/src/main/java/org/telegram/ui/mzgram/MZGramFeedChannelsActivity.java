/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The channels of the feed (Settings > MZGram > Feed > Channels): every
 * channel of the chat list with a check; an unchecked channel's posts are
 * left out of the feed. New channels are checked.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramFeed;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Set;

public class MZGramFeedChannelsActivity extends UniversalFragment {

    private final ArrayList<TLRPC.Dialog> channels = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramFeedChannels);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        channels.clear();
        channels.addAll(MZGramFeed.allChannels(currentAccount));
        Set<Long> hidden = MZGramFeed.hidden(currentAccount);
        items.add(UItem.asShadow(getString(R.string.MZGramFeedChannelsInfo)));
        for (int i = 0; i < channels.size(); i++) {
            TLRPC.Chat chat = getMessagesController().getChat(-channels.get(i).id);
            if (chat == null) {
                continue;
            }
            items.add(UItem.asUserCheckbox(i, chat).pad().setChecked(!hidden.contains(channels.get(i).id)));
        }
        if (channels.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.MZGramFeedNoChannels)));
        } else {
            items.add(UItem.asShadow(null));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 0 || item.id >= channels.size()) {
            return;
        }
        long dialogId = channels.get(item.id).id;
        boolean hidden = MZGramFeed.hidden(currentAccount).contains(dialogId);
        MZGramFeed.setHidden(currentAccount, dialogId, !hidden);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
