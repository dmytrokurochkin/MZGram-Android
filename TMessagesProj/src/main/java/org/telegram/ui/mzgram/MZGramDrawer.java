/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The MZGram side menu (Settings > MZGram > Interface > Side menu, off by
 * default). While it is on, the three dots of the chat list open a panel
 * from the side of the screen instead of the popup menu: a header with the
 * avatar, the name and the username of the account, a QR button (log in on
 * another device), the signed-in accounts behind the arrow (a tap switches
 * at once, the others show their unread count), then the items of
 * MZGramDrawerItems in the chosen order. "Full menu" opens the popup menu of
 * Telegram as before, so its items (AI sites, plugins, bots, proxy) stay at
 * hand. A tap beside the panel, Back or a swipe toward the edge closes it.
 * The archive and the community lists keep the popup menu.
 *
 * Idea from exteraless DrawerContainer, DrawerHeaderView,
 * DrawerAccountPickerView and DrawerMenuView (GPL-3.0); written anew for
 * MZGram, as a panel over the chat list without a new edge gesture.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramDrawerItems;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.ChannelCreateActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.ThemeActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public final class MZGramDrawer {

    private static final int MAX_WIDTH = 300;
    private static final int EDGE_ROOM = 56;
    private static final long DURATION = 220;
    private static final int DIM_COLOR = 0x66000000;

    private static WeakReference<MZGramDrawer> shown;

    private final DialogsActivity fragment;
    private final int currentAccount;
    private final FrameLayout container;
    private final Root root;
    private final View dim;
    private final FrameLayout panel;
    private final LinearLayout accountsList;
    private ImageView arrowView;
    private final int panelWidth;
    private final boolean rtl = LocaleController.isRTL;

    private float progress;
    private ValueAnimator animator;
    private boolean closing;
    private boolean removed;

    private MZGramDrawer(DialogsActivity fragment, FrameLayout container) {
        this.fragment = fragment;
        this.currentAccount = fragment.getCurrentAccount();
        this.container = container;
        Context context = container.getContext();
        int screenWidth = container.getWidth() > 0 ? container.getWidth() : AndroidUtilities.displaySize.x;
        panelWidth = Math.max(dp(200), Math.min(dp(MAX_WIDTH), screenWidth - dp(EDGE_ROOM)));

        root = new Root(context);
        dim = new View(context);
        dim.setBackgroundColor(DIM_COLOR);
        dim.setAlpha(0f);
        dim.setOnClickListener(v -> close(true, null));
        root.addView(dim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        panel = new FrameLayout(context);
        panel.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        panel.setClickable(true);
        panel.setPadding(0, 0, 0, AndroidUtilities.navigationBarHeight);
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        root.addView(panel, new FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, rtl ? Gravity.RIGHT : Gravity.LEFT));

        content.addView(createHeader(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        accountsList = new LinearLayout(context);
        accountsList.setOrientation(LinearLayout.VERTICAL);
        accountsList.setVisibility(View.GONE);
        content.addView(accountsList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        View gap = new View(context);
        content.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        for (int id : MZGramDrawerItems.visible()) {
            View row = createItem(context, id);
            if (row != null) {
                content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            }
        }
        View end = new View(context);
        content.addView(end, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
    }

    // ---- open and close ----

    /** Opens the side menu over the chat list; false while it is off or cannot open. */
    public static boolean open(DialogsActivity fragment) {
        if (!MZGramConfig.customDrawer || fragment == null || fragment.getParentActivity() == null || fragment.getParentLayout() == null) {
            return false;
        }
        MZGramDrawer current = current();
        if (current != null) {
            if (!current.closing) {
                return true;
            }
            current.remove();
        }
        FrameLayout container = fragment.getParentLayout().getOverlayContainerView();
        if (container == null) {
            return false;
        }
        MZGramDrawer drawer = new MZGramDrawer(fragment, container);
        shown = new WeakReference<>(drawer);
        drawer.show();
        return true;
    }

    private static MZGramDrawer current() {
        MZGramDrawer drawer = shown != null ? shown.get() : null;
        return drawer != null && !drawer.removed ? drawer : null;
    }

    /** Whether the side menu is open and not closing. */
    public static boolean isOpen() {
        MZGramDrawer drawer = current();
        return drawer != null && !drawer.closing;
    }

    /** Closes the side menu, if it is open. */
    public static void close(boolean animated) {
        MZGramDrawer drawer = current();
        if (drawer != null) {
            if (animated) {
                drawer.close(true, null);
            } else {
                drawer.remove();
            }
        }
    }

    private void show() {
        container.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setProgress(0f);
        animateTo(1f, null);
    }

    private void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        panel.setTranslationX((rtl ? 1 : -1) * panelWidth * (1f - progress));
        dim.setAlpha(progress);
    }

    private void animateTo(float target, Runnable after) {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        animator.setDuration((long) (DURATION * Math.max(0.3f, Math.abs(target - progress))));
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animator = null;
                if (after != null) {
                    after.run();
                }
            }
        });
        animator.start();
    }

    /** Closes the panel, then runs the action (an item opens its screen once the panel is gone). */
    private void close(boolean animated, Runnable after) {
        if (removed) {
            return;
        }
        if (closing && animated) {
            return;
        }
        closing = true;
        if (!animated) {
            remove();
            if (after != null) {
                after.run();
            }
            return;
        }
        animateTo(0f, () -> {
            remove();
            if (after != null) {
                after.run();
            }
        });
    }

    private void remove() {
        if (removed) {
            return;
        }
        removed = true;
        closing = true;
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        if (root.getParent() instanceof ViewGroup) {
            ((ViewGroup) root.getParent()).removeView(root);
        }
    }

    // ---- the header and the accounts ----

    private View createHeader(Context context) {
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        boolean large = MZGramConfig.drawerLargeAvatar;
        int avatarSize = large ? 72 : 56;

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Theme.getColor(Theme.key_chats_menuTopBackground));
        header.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);

        FrameLayout top = new FrameLayout(context);
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(avatarSize / 2f));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(large ? 28 : 22));
        if (user != null) {
            avatarDrawable.setInfo(currentAccount, user);
            avatar.getImageReceiver().setCurrentAccount(currentAccount);
            avatar.setForUserOrChat(user, avatarDrawable);
        } else {
            avatar.setImageDrawable(avatarDrawable);
        }
        // Telegram's own text: the name of Telegram's own profile item
        avatar.setContentDescription(getString(R.string.MyProfile));
        avatar.setOnClickListener(v -> close(true, () -> run(MZGramDrawerItems.PROFILE, v)));
        top.addView(avatar, LayoutHelper.createFrame(avatarSize, avatarSize, Gravity.TOP | (rtl ? Gravity.RIGHT : Gravity.LEFT), 16, 16, 16, 0));

        if (MZGramConfig.drawerShowQr) {
            ImageView qr = new ImageView(context);
            qr.setScaleType(ImageView.ScaleType.CENTER);
            qr.setImageResource(R.drawable.msg_qrcode);
            qr.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuName), PorterDuff.Mode.SRC_IN));
            qr.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, dp(20)));
            // Telegram's own text: the name of Telegram's own QR login screen
            qr.setContentDescription(getString(R.string.AuthAnotherClient));
            qr.setOnClickListener(v -> close(true, () -> run(MZGramDrawerItems.QR_LOGIN, v)));
            top.addView(qr, LayoutHelper.createFrame(40, 40, Gravity.TOP | (rtl ? Gravity.LEFT : Gravity.RIGHT), 8, 12, 8, 0));
        }
        header.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout bottom = new FrameLayout(context);
        bottom.setBackground(Theme.getSelectorDrawable(false));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_chats_menuName));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        String userName = user != null ? UserObject.getUserName(user) : "";
        name.setText(Emoji.replaceEmoji(userName, name.getPaint().getFontMetricsInt(), false));
        texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        String username = user != null ? UserObject.getPublicUsername(user) : null;
        if (!TextUtils.isEmpty(username)) {
            TextView subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(Theme.getColor(Theme.key_chats_menuPhone));
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
            subtitle.setText("@" + username);
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        bottom.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, rtl ? 56 : 16, 10, rtl ? 16 : 56, 12));

        arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setImageResource(R.drawable.arrow_more);
        arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuName), PorterDuff.Mode.SRC_IN));
        bottom.addView(arrowView, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT), 4, 0, 4, 0));
        bottom.setContentDescription(getString(R.string.MZGramDrawerAccounts));
        bottom.setOnClickListener(v -> toggleAccounts());
        header.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        return header;
    }

    private void toggleAccounts() {
        if (accountsList.getVisibility() == View.VISIBLE) {
            accountsList.setVisibility(View.GONE);
            accountsList.removeAllViews();
            arrowView.animate().rotation(0).setDuration(180).start();
            return;
        }
        fillAccounts(accountsList.getContext());
        accountsList.setVisibility(View.VISIBLE);
        arrowView.animate().rotation(180).setDuration(180).start();
    }

    private void fillAccounts(Context context) {
        accountsList.removeAllViews();
        for (int account : MZGramAccountsMenu.accounts(currentAccount)) {
            accountsList.addView(createAccountRow(context, account), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        if (MZGramAccountsMenu.hasRoom()) {
            accountsList.addView(createRow(context, R.drawable.outline_add_account, getString(R.string.MZGramAddAccount),
                    v -> close(true, () -> MZGramAccountsMenu.addAccount(fragment))), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        accountsList.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 4, 0, 0));
    }

    private View createAccountRow(Context context, int account) {
        boolean current = account == currentAccount;
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        FrameLayout row = new FrameLayout(context);
        row.setBackground(Theme.getSelectorDrawable(false));

        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(17));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(12));
        if (user != null) {
            avatarDrawable.setInfo(account, user);
            avatar.getImageReceiver().setCurrentAccount(account);
            avatar.setForUserOrChat(user, avatarDrawable);
        } else {
            avatar.setImageDrawable(avatarDrawable);
        }
        row.addView(avatar, LayoutHelper.createFrame(34, 34, Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT), 14, 0, 14, 0));

        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
        String userName = user != null ? UserObject.getUserName(user) : "";
        name.setText(Emoji.replaceEmoji(userName, name.getPaint().getFontMetricsInt(), false));
        row.addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | (rtl ? Gravity.RIGHT : Gravity.LEFT), rtl ? 60 : 72, 0, rtl ? 72 : 60, 0));

        if (current) {
            ImageView check = new ImageView(context);
            check.setScaleType(ImageView.ScaleType.CENTER);
            check.setImageResource(R.drawable.account_check);
            check.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemCheck), PorterDuff.Mode.SRC_IN));
            row.addView(check, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT), 10, 0, 10, 0));
        } else {
            int unread = MessagesStorage.getInstance(account).getMainUnreadCount();
            if (unread > 0) {
                TextView counter = new TextView(context);
                counter.setPadding(dp(6.66f), 0, dp(6.66f), 0);
                counter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
                counter.setTypeface(AndroidUtilities.bold());
                counter.setGravity(Gravity.CENTER);
                counter.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                counter.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton)));
                counter.setText(LocaleController.formatNumber(unread, ','));
                row.addView(counter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT), 16, 0, 16, 0));
            }
        }
        row.setOnClickListener(v -> {
            if (current) {
                close(true, null);
            } else {
                // The chat list is rebuilt for the other account: no panel over it
                close(false, () -> MZGramAccountsMenu.switchTo(account, currentAccount));
            }
        });
        return row;
    }

    // ---- the items ----

    private View createRow(Context context, int icon, CharSequence text, View.OnClickListener onClick) {
        FrameLayout row = new FrameLayout(context);
        row.setBackground(Theme.getSelectorDrawable(false));
        ImageView iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setImageResource(icon);
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemIcon), PorterDuff.Mode.SRC_IN));
        row.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT), 19, 0, 19, 0));
        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
        textView.setText(text);
        row.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | (rtl ? Gravity.RIGHT : Gravity.LEFT), rtl ? 16 : 72, 0, rtl ? 72 : 16, 0));
        row.setOnClickListener(onClick);
        return row;
    }

    /** The icon of an item. */
    public static int iconOf(int id) {
        switch (id) {
            case MZGramDrawerItems.PROFILE: return R.drawable.left_status_profile;
            case MZGramDrawerItems.NEW_GROUP: return R.drawable.outline_groups_24;
            case MZGramDrawerItems.NEW_CHANNEL: return R.drawable.msg_channel;
            case MZGramDrawerItems.CONTACTS: return R.drawable.msg_contacts;
            case MZGramDrawerItems.CALLS: return R.drawable.msg_calls;
            case MZGramDrawerItems.SAVED: return R.drawable.outline_saved_24;
            case MZGramDrawerItems.ARCHIVE: return R.drawable.msg_archive;
            case MZGramDrawerItems.NIGHT_MODE: return Theme.isCurrentThemeDark() ? R.drawable.menu_day_mode_24 : R.drawable.menu_night_mode_24;
            case MZGramDrawerItems.SETTINGS: return R.drawable.msg_settings_old;
            case MZGramDrawerItems.MZGRAM: return R.drawable.settings_features;
            case MZGramDrawerItems.QR_LOGIN: return R.drawable.msg_qrcode;
            case MZGramDrawerItems.FULL_MENU: return R.drawable.ic_ab_other;
            default: return 0;
        }
    }

    /** The name of an item, as the menu shows it. */
    public static String titleOf(int id) {
        switch (id) {
            case MZGramDrawerItems.PROFILE: return getString(R.string.MyProfile); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.NEW_GROUP: return getString(R.string.NewGroup); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.NEW_CHANNEL: return getString(R.string.NewChannel); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.CONTACTS: return getString(R.string.Contacts); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.CALLS: return getString(R.string.Calls); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.SAVED: return getString(R.string.SavedMessages); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.ARCHIVE: return getString(R.string.ArchivedChats); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.NIGHT_MODE: return getString(Theme.isCurrentThemeDark() ? R.string.SwitchThemeToDay : R.string.SwitchThemeToNight); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.SETTINGS: return getString(R.string.Settings); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.MZGRAM: return getString(R.string.MZGram);
            case MZGramDrawerItems.QR_LOGIN: return getString(R.string.AuthAnotherClient); // Telegram's own text: the item as Telegram's menu names it
            case MZGramDrawerItems.FULL_MENU: return getString(R.string.MZGramDrawerFullMenu);
            default: return null;
        }
    }

    private View createItem(Context context, int id) {
        int icon = iconOf(id);
        String title = titleOf(id);
        if (icon == 0 || title == null) {
            return null;
        }
        if (id == MZGramDrawerItems.NIGHT_MODE) {
            // The theme changes with a circle from this row: the panel goes away first
            return createRow(context, icon, title, v -> {
                int[] position = new int[2];
                v.getLocationInWindow(position);
                position[0] += rtl ? v.getMeasuredWidth() - dp(19 + 12) : dp(19 + 12);
                position[1] += v.getMeasuredHeight() / 2;
                close(false, () -> switchNightMode(position));
            });
        }
        return createRow(context, icon, title, v -> close(true, () -> run(id, v)));
    }

    private void run(int id, View from) {
        if (fragment.getParentActivity() == null || fragment.isFinished) {
            return;
        }
        Bundle args;
        switch (id) {
            case MZGramDrawerItems.PROFILE:
                args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                args.putBoolean("my_profile", true);
                fragment.presentFragment(new ProfileActivity(args));
                break;
            case MZGramDrawerItems.NEW_GROUP:
                fragment.presentFragment(new GroupCreateActivity(new Bundle()));
                break;
            case MZGramDrawerItems.NEW_CHANNEL:
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                if (!preferences.getBoolean("channel_intro", false)) {
                    fragment.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                    preferences.edit().putBoolean("channel_intro", true).apply();
                } else {
                    args = new Bundle();
                    args.putInt("step", 0);
                    fragment.presentFragment(new ChannelCreateActivity(args));
                }
                break;
            case MZGramDrawerItems.CONTACTS:
                args = new Bundle();
                args.putBoolean("needPhonebook", true);
                args.putBoolean("needFinishFragment", false);
                fragment.presentFragment(new ContactsActivity(args));
                break;
            case MZGramDrawerItems.CALLS:
                fragment.presentFragment(new CallLogActivity());
                break;
            case MZGramDrawerItems.SAVED:
                args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                fragment.presentFragment(new ChatActivity(args));
                break;
            case MZGramDrawerItems.ARCHIVE:
                args = new Bundle();
                args.putInt("folderId", 1);
                fragment.presentFragment(new DialogsActivity(args));
                break;
            case MZGramDrawerItems.SETTINGS:
                fragment.presentFragment(new SettingsActivity());
                break;
            case MZGramDrawerItems.MZGRAM:
                fragment.presentFragment(new MZGramSettingsActivity());
                break;
            case MZGramDrawerItems.QR_LOGIN:
                // Telegram's own link: the scanner of "Link Desktop Device"
                Browser.openUrl(fragment.getParentActivity(), "tg://scanqr");
                break;
            case MZGramDrawerItems.FULL_MENU:
                fragment.mzgramShowItemOptions();
                break;
            default:
                break;
        }
    }

    // As the day and night item of the chat list menu (DialogsActivity.showItemOptions)
    private void switchNightMode(int[] position) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }
        boolean toDark = dayThemeName.equals(themeInfo.getKey());
        themeInfo = Theme.getTheme(toDark ? nightThemeName : dayThemeName);
        if (themeInfo == null) {
            return;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, position, -1, toDark, null, null, null, true);
        Theme.turnOffAutoNight(BulletinFactory.of(fragment), () -> fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)));
    }

    // ---- the swipe that closes the panel ----

    private class Root extends FrameLayout {

        private final int touchSlop;
        private float startX;
        private float startY;
        private float startProgress;
        private boolean tracking;
        private boolean dragging;
        private VelocityTracker velocityTracker;

        Root(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        private void track(MotionEvent ev) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(ev);
        }

        private void stopTracking() {
            tracking = false;
            dragging = false;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }

        // Toward the edge of the panel: left, or right in a right-to-left language
        private float closingDistance(MotionEvent ev) {
            float dx = ev.getX() - startX;
            return rtl ? dx : -dx;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (closing) {
                return true;
            }
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                stopTracking();
                tracking = true;
                startX = ev.getX();
                startY = ev.getY();
                track(ev);
                return false;
            }
            if (!tracking) {
                return false;
            }
            track(ev);
            if (action == MotionEvent.ACTION_MOVE && !dragging) {
                float distance = closingDistance(ev);
                float dy = Math.abs(ev.getY() - startY);
                if (distance > touchSlop && distance > dy) {
                    dragging = true;
                    startX = ev.getX();
                    startProgress = progress;
                    if (animator != null) {
                        animator.removeAllListeners();
                        animator.cancel();
                        animator = null;
                    }
                    return true;
                } else if (dy > touchSlop) {
                    stopTracking();
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                stopTracking();
            }
            return dragging;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (closing) {
                return true;
            }
            if (!dragging) {
                // Nothing under the panel and the dim takes a touch while it is open
                return true;
            }
            track(ev);
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) {
                setProgress(startProgress - closingDistance(ev) / panelWidth);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float velocity = 0;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocity = rtl ? velocityTracker.getXVelocity() : -velocityTracker.getXVelocity();
                }
                stopTracking();
                if (progress < 0.6f || velocity > dp(600)) {
                    close(true, null);
                } else {
                    animateTo(1f, null);
                }
            }
            return true;
        }
    }
}
