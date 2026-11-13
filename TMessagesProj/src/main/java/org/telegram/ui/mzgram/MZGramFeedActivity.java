/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The Feed screen: the posts of the feed's channels (MZGramFeed) in one
 * list, the newest at the bottom, on the chat wallpaper. A tap on a post
 * opens it in its channel; links open as in a chat. The top bar shows how
 * many channels are in the feed and has Mark all as read, the category (a
 * chat folder, or all channels) and the feed's settings. It opens from the
 * side menu, from Settings > MZGram > Chats > Feed, or as a bottom tab in
 * the place of Contacts. Idea from exteraless (GPL-3.0); written anew.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramFeed;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

public class MZGramFeedActivity extends BaseFragment {

    private static final int MENU_READ_ALL = 1;
    private static final int MENU_CATEGORY = 2;
    private static final int MENU_SETTINGS = 3;
    // A feed older than this is asked again when the screen comes back
    private static final long STALE_MS = 60 * 1000L;

    private final boolean hasMainTabs;
    private final ArrayList<MessageObject> posts = new ArrayList<>();
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private TextView emptyView;
    private RadialProgressView progressView;
    private int channelCount;
    private int loadId;
    private long loadedAt;

    public MZGramFeedActivity() {
        this(null);
    }

    public MZGramFeedActivity(Bundle args) {
        super(args);
        hasMainTabs = args != null && args.getBoolean("hasMainTabs", false);
    }

    @Override
    public View createView(Context context) {
        if (!hasMainTabs) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.MZGramFeed));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_READ_ALL) {
                    confirmReadAll();
                } else if (id == MENU_CATEGORY) {
                    chooseCategory();
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new MZGramSettingsActivity(MZGramSettingsActivity.SECTION_FEED));
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_READ_ALL, R.drawable.msg_markread).setContentDescription(getString(R.string.MZGramFeedMarkAllRead));
        menu.addItem(MENU_CATEGORY, R.drawable.msg_folders).setContentDescription(getString(R.string.MZGramFeedCategory));
        menu.addItem(MENU_SETTINGS, R.drawable.msg_settings_old).setContentDescription(getString(R.string.MZGramFeedSettings));

        SizeNotifierFrameLayout content = new SizeNotifierFrameLayout(context);
        content.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = content;

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setClipToPadding(false);
        int top = hasMainTabs ? ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) : 0;
        int bottom = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        listView.setPadding(0, top + dp(4), 0, bottom + dp(4));
        listView.setAdapter(new Adapter(context));
        listView.setOnItemClickListener((view, position) -> openPost(position));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        emptyView.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_chat_serviceBackground)));
        emptyView.setPadding(dp(14), dp(8), dp(14), dp(8));
        emptyView.setVisibility(View.GONE);
        content.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));

        progressView = new RadialProgressView(context);
        progressView.setProgressColor(Theme.getColor(Theme.key_chat_serviceText));
        content.addView(progressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        reload();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && SystemClock.elapsedRealtime() - loadedAt > STALE_MS) {
            reload();
        }
        updateSubtitle();
    }

    private void reload() {
        final int id = ++loadId;
        if (progressView != null && posts.isEmpty()) {
            progressView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
        MZGramFeed.load(currentAccount, (loaded, channels) -> {
            if (id != loadId || listView == null) {
                return;
            }
            loadedAt = SystemClock.elapsedRealtime();
            channelCount = channels;
            posts.clear();
            posts.addAll(loaded);
            listView.getAdapter().notifyDataSetChanged();
            if (!posts.isEmpty()) {
                layoutManager.scrollToPositionWithOffset(posts.size() - 1, 0);
            }
            progressView.setVisibility(View.GONE);
            emptyView.setText(getString(channels == 0 ? R.string.MZGramFeedNoChannels : R.string.MZGramFeedNoPosts));
            emptyView.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
            updateSubtitle();
        });
    }

    private void updateSubtitle() {
        if (actionBar == null) {
            return;
        }
        int unread = MZGramFeed.unreadCount(currentAccount);
        String subtitle = formatString(R.string.MZGramFeedChannelsCount, channelCount);
        if (unread > 0) {
            subtitle += ", " + formatString(R.string.MZGramFeedUnreadCount, unread);
        }
        actionBar.setSubtitle(subtitle);
    }

    private void confirmReadAll() {
        if (getParentActivity() == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.MZGramFeedMarkAllRead))
                .setMessage(getString(R.string.MZGramFeedMarkAllReadConfirm))
                .setPositiveButton(getString(R.string.MZGramContinue), (dialog, which) -> {
                    MZGramFeed.markAllRead(currentAccount);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramFeedMarkAllReadDone)).show();
                    AndroidUtilities.runOnUIThread(this::updateSubtitle, 500);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create());
    }

    // The category: all channels, or the channels of one chat folder
    private void chooseCategory() {
        if (getParentActivity() == null) {
            return;
        }
        ArrayList<MessagesController.DialogFilter> folders = new ArrayList<>();
        for (MessagesController.DialogFilter filter : getMessagesController().getDialogFilters()) {
            if (!filter.isDefault()) {
                folders.add(filter);
            }
        }
        String[] names = new String[folders.size() + 1];
        names[0] = getString(R.string.MZGramFeedAllChannels);
        int selected = 0;
        int current = MZGramFeed.category(currentAccount);
        for (int i = 0; i < folders.size(); i++) {
            names[i + 1] = folders.get(i).name;
            if (folders.get(i).id == current) {
                selected = i + 1;
            }
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), names, getString(R.string.MZGramFeedCategory), selected, (dialog, which) -> {
            MZGramFeed.setCategory(currentAccount, which == 0 ? 0 : folders.get(which - 1).id);
            posts.clear();
            if (listView != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            reload();
        }, getString(R.string.MZGramCancel)));
    }

    private void openPost(int position) {
        if (position < 0 || position >= posts.size()) {
            return;
        }
        openPost(posts.get(position));
    }

    private void openPost(MessageObject post) {
        if (post == null || post.messageOwner == null || post.messageOwner.peer_id == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("chat_id", post.messageOwner.peer_id.channel_id);
        args.putInt("message_id", post.getId());
        if (getMessagesController().checkCanOpenChat(args, this)) {
            presentFragment(new ChatActivity(args));
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return posts.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(context, currentAccount);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                    openPost(cell.getMessageObject());
                }

                @Override
                public void didPressInstantButton(ChatMessageCell cell, int type) {
                    openPost(cell.getMessageObject());
                }

                @Override
                public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY, boolean asForward) {
                    openPost(cell.getMessageObject());
                }

                @Override
                public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
                    if (longPress || getParentActivity() == null) {
                        return;
                    }
                    if (url instanceof URLSpan) {
                        Browser.openUrl(getParentActivity(), ((URLSpan) url).getURL());
                    } else if (url instanceof ClickableSpan) {
                        ((ClickableSpan) url).onClick(fragmentView);
                    }
                }
            });
            cell.isChat = true;
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            cell.isChat = true;
            cell.setMessageObject(posts.get(position), null, false, false, false);
            cell.setHighlighted(false);
        }
    }
}
