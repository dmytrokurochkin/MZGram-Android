/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Message history > Tracked chats: the allowlist of
 * dialogs MZGram archives deleted/edited messages for. Modeled on the
 * upstream CacheChatsExceptionsFragment (chat picker + removable list),
 * which is the established pattern in this codebase for "pick some chats"
 * screens; there is no AyuGram4A equivalent to port, since AyuGram4A saves
 * for every chat by default and excludes some, instead of the other way
 * around.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.List;

public class MZGramTrackedChatsActivity extends BaseFragment {

    private static final int VIEW_TYPE_ADD = 1;
    private static final int VIEW_TYPE_CHAT = 2;
    private static final int VIEW_TYPE_DIVIDER = 3;

    private Adapter adapter;
    private RecyclerListView recyclerListView;
    private final ArrayList<Long> trackedDialogs = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();

    @Override
    public View createView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.MZGramTrackedChats));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        recyclerListView = new RecyclerListView(context);
        recyclerListView.setLayoutManager(new LinearLayoutManager(context));
        recyclerListView.setAdapter(adapter = new Adapter());
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            if (items.get(position).viewType == VIEW_TYPE_ADD) {
                openChatPicker();
            } else if (items.get(position).viewType == VIEW_TYPE_CHAT) {
                // Tap opens the archive; long-press removes the chat from tracking.
                presentFragment(new MZGramChatArchiveActivity(getCurrentAccount(), items.get(position).dialogId));
            }
        });
        recyclerListView.setOnItemLongClickListener((view, position) -> {
            if (items.get(position).viewType != VIEW_TYPE_CHAT) {
                return false;
            }
            long dialogId = items.get(position).dialogId;
            AlertsCreator.createSimpleAlert(getContext(),
                    getString(R.string.MZGramTrackedChats),
                    LocaleController.formatString("MZGramStopTrackingConfirm", R.string.MZGramStopTrackingConfirm, titleFor(dialogId)),
                    getString(R.string.Remove),
                    () -> {
                        MZGramConfig.setDialogTracked(dialogId, false);
                        updateRows();
                    }, null).create().show();
            return true;
        });
        frameLayout.addView(recyclerListView);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        updateRows();
        return fragmentView;
    }

    private void openChatPicker() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("allowGlobalSearch", true);
        DialogsActivity activity = new DialogsActivity(args);
        activity.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            activity.finishFragment();
            for (int i = 0; i < dids.size(); i++) {
                MZGramConfig.setDialogTracked(dids.get(i).dialogId, true);
            }
            updateRows();
            return true;
        });
        presentFragment(activity);
    }

    private String titleFor(long dialogId) {
        TLObject object = getMessagesController().getUserOrChat(dialogId);
        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            return user.self ? getString(R.string.SavedMessages) : ContactsController.formatName(user.first_name, user.last_name);
        } else if (object instanceof TLRPC.Chat) {
            return ((TLRPC.Chat) object).title;
        }
        return "?";
    }

    private void updateRows() {
        boolean animated = !isPaused && adapter != null;
        ArrayList<Item> oldItems = null;
        if (animated) {
            oldItems = new ArrayList<>(items);
        }

        trackedDialogs.clear();
        List<Long> sorted = new ArrayList<>(MZGramConfig.getTrackedDialogs());
        java.util.Collections.sort(sorted);
        trackedDialogs.addAll(sorted);

        items.clear();
        items.add(new Item(VIEW_TYPE_ADD, 0));
        if (!trackedDialogs.isEmpty()) {
            items.add(new Item(VIEW_TYPE_DIVIDER, 0));
            for (long dialogId : trackedDialogs) {
                items.add(new Item(VIEW_TYPE_CHAT, dialogId));
            }
        }

        if (adapter != null) {
            if (oldItems != null) {
                adapter.setItems(oldItems, items);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private class Adapter extends AdapterWithDiffUtils {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_CHAT:
                    view = new UserCell(parent.getContext(), 4, 0, false, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_DIVIDER:
                    view = new ShadowSectionCell(parent.getContext());
                    break;
                default:
                    TextCell textCell = new TextCell(parent.getContext());
                    textCell.setTextAndIcon(getString(R.string.MZGramAddTrackedChat), R.drawable.msg_contact_add, false);
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    view = textCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (items.get(position).viewType == VIEW_TYPE_CHAT) {
                UserCell cell = (UserCell) holder.itemView;
                long dialogId = items.get(position).dialogId;
                TLObject object = getMessagesController().getUserOrChat(dialogId);
                cell.setSelfAsSavedMessages(true);
                cell.setData(object, titleFor(dialogId), null, 0, position != items.size() - 1);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_ADD || holder.getItemViewType() == VIEW_TYPE_CHAT;
        }
    }

    private static class Item extends AdapterWithDiffUtils.Item {
        final long dialogId;

        private Item(int viewType, long dialogId) {
            super(viewType, false);
            this.dialogId = dialogId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return viewType == item.viewType && dialogId == item.dialogId;
        }
    }
}
