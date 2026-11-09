/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Shadow ban list: the people whose messages are hidden in groups
 * (MZGramShadowBan). A tap offers to show their messages again or to open
 * the profile. People are added from the profile menu or the message menu.
 *
 * Idea from Nekogram ShadowBanListActivity (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramShadowBan;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

public class MZGramShadowBanActivity extends UniversalFragment {

    private final ArrayList<Long> peers = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramShadowBanList);
    }

    private String nameOf(long peerId) {
        String name = DialogObject.getName(currentAccount, peerId);
        return TextUtils.isEmpty(name) ? String.valueOf(peerId) : name;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        peers.clear();
        peers.addAll(MZGramShadowBan.list());
        if (peers.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.MZGramShadowBanEmpty)));
            return;
        }
        for (int i = 0, N = peers.size(); i < N; i++) {
            long peerId = peers.get(i);
            items.add(UItem.asButton(i + 1, nameOf(peerId), String.valueOf(peerId)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramShadowBanListInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - 1;
        if (index < 0 || index >= peers.size() || getParentActivity() == null) {
            return;
        }
        long peerId = peers.get(index);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(nameOf(peerId));
        builder.setMessage(getString(R.string.MZGramShadowBanRemoveConfirm));
        builder.setPositiveButton(getString(R.string.MZGramShadowBanRemove), (dialog, which) -> {
            MZGramShadowBan.set(peerId, false);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        if (getMessagesController().getUserOrChat(peerId) != null) {
            builder.setNeutralButton(getString(R.string.MZGramShadowBanOpenProfile), (dialog, which) -> presentFragment(ProfileActivity.of(peerId)));
        }
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        showDialog(builder.create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }
}
