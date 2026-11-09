/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Recent chats on Back: holding the Back arrow in a chat shows the chats
 * opened last, to jump between them without going back to the chat list.
 * Off by default. Only while the option is on are opened chats written down
 * (up to 25 per account, on the device only); secret chats are never written
 * down. Turning the option off or signing out of the account erases the list.
 * With an empty list Telegram's own Back menu is shown as before.
 *
 * Ported from Nekogram BackButtonMenuRecent (GPL-3.0); adapted for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

public final class MZGramRecentChats {

    public static final int MAX_RECENT_CHATS = 25;
    private static final String PREFS = "mzgram_recent_chats";

    private static final SparseArray<LinkedList<Long>> cache = new SparseArray<>();
    private static final SparseArray<Long> cacheOwner = new SparseArray<>();

    private MZGramRecentChats() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int account, long userId) {
        return "recents_" + account + "_" + userId;
    }

    /** The list of an account: newest first, written down for the signed-in user only. */
    public static synchronized LinkedList<Long> get(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        LinkedList<Long> list = cache.get(account);
        Long owner = cacheOwner.get(account);
        if (list == null || owner == null || owner != userId) {
            list = parse(userId == 0 ? null : preferences().getString(key(account, userId), null));
            cache.put(account, list);
            cacheOwner.put(account, userId);
        }
        return list;
    }

    static LinkedList<Long> parse(String value) {
        LinkedList<Long> list = new LinkedList<>();
        if (TextUtils.isEmpty(value)) {
            return list;
        }
        for (String part : value.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id != 0 && !list.contains(id) && list.size() < MAX_RECENT_CHATS) {
                    list.add(id);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return list;
    }

    static String join(LinkedList<Long> list) {
        StringBuilder sb = new StringBuilder();
        for (Long id : list) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** Puts the dialog first: a user id, or minus the chat id. Does nothing with the option off. */
    public static synchronized void record(int account, long dialogId) {
        if (!MZGramConfig.recentChatsOnBack || dialogId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId == 0) {
            return;
        }
        LinkedList<Long> list = get(account);
        list.remove(dialogId);
        list.addFirst(dialogId);
        while (list.size() > MAX_RECENT_CHATS) {
            list.removeLast();
        }
        preferences().edit().putString(key(account, userId), join(list)).apply();
    }

    /** Erases the list of the account's signed-in user, as on sign-out. */
    public static synchronized void clear(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        cache.remove(account);
        cacheOwner.remove(account);
        if (userId != 0) {
            preferences().edit().remove(key(account, userId)).apply();
        }
    }

    /** Erases every list, as when the option is turned off. */
    public static synchronized void clearAll() {
        cache.clear();
        cacheOwner.clear();
        preferences().edit().clear().apply();
    }

    private static boolean isNavigating(INavigationLayout layout) {
        if (layout == null) {
            return true;
        }
        if (layout.isTransitionAnimationInProgress() || layout.isSwipeInProgress()) {
            return true;
        }
        // Fix 1.4: no menu while a predictive Back gesture is running
        return layout instanceof ActionBarLayout && ((ActionBarLayout) layout).mzgramIsPredictiveBackInProgress();
    }

    /**
     * The menu of recent chats for the Back arrow, or null to keep Telegram's
     * Back menu (option off, a navigation running, or nothing to show).
     */
    public static ActionBarPopupWindow show(BaseFragment fragment, View backButton, long currentDialogId, Theme.ResourcesProvider resourcesProvider) {
        if (!MZGramConfig.recentChatsOnBack || fragment == null || backButton == null) {
            return null;
        }
        final Context context = fragment.getParentActivity();
        final View fragmentView = fragment.getFragmentView();
        if (context == null || fragmentView == null || isNavigating(fragment.getParentLayout())) {
            return null;
        }
        final int account = fragment.getCurrentAccount();
        final MessagesController controller = MessagesController.getInstance(account);
        ArrayList<Long> dialogs = new ArrayList<>();
        for (Long dialogId : new ArrayList<>(get(account))) {
            if (dialogId == currentDialogId) {
                continue;
            }
            if (dialogId < 0 ? controller.getChat(-dialogId) != null : controller.getUser(dialogId) != null) {
                dialogs.add(dialogId);
            }
        }
        if (dialogs.isEmpty()) {
            return null;
        }

        ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert4, resourcesProvider);
        Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.popup_fixed_alert4).mutate();
        shadowDrawable.getPadding(backgroundPaddings);
        layout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));

        AtomicReference<ActionBarPopupWindow> popupRef = new AtomicReference<>();

        FrameLayout headerView = new FrameLayout(context);
        TextView headerTitle = new TextView(context);
        headerTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlue, resourcesProvider));
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        headerTitle.setText(getString(R.string.MZGramRecentChats));
        headerTitle.setTypeface(AndroidUtilities.bold());
        headerView.addView(headerTitle, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 24, Gravity.START | Gravity.CENTER_VERTICAL, 0, 0, 36, 0));

        ImageView clearView = new ImageView(context);
        clearView.setScaleType(ImageView.ScaleType.CENTER);
        clearView.setColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
        clearView.setImageResource(R.drawable.msg_close);
        clearView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
        clearView.setContentDescription(getString(R.string.MZGramRecentChatsClear));
        clearView.setOnClickListener(v -> new AlertDialog.Builder(context, resourcesProvider)
                .setTitle(getString(R.string.MZGramRecentChatsClear))
                .setMessage(getString(R.string.MZGramRecentChatsClearInfo))
                .setPositiveButton(getString(R.string.ClearButton), (dialogInterface, i) -> {
                    ActionBarPopupWindow popup = popupRef.getAndSet(null);
                    if (popup != null) {
                        popup.dismiss();
                    }
                    clear(account);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show());
        headerView.addView(clearView, LayoutHelper.createFrameRelatively(24, 24, Gravity.END | Gravity.CENTER_VERTICAL));
        headerView.setPadding(dp(12), dp(8), dp(8), dp(8));
        layout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        for (int i = 0, N = dialogs.size(); i < N; i++) {
            final long dialogId = dialogs.get(i);
            final boolean last = i == N - 1;
            final TLRPC.Chat chat = dialogId < 0 ? controller.getChat(-dialogId) : null;
            final TLRPC.User user = dialogId > 0 ? controller.getUser(dialogId) : null;

            FrameLayout cell = new FrameLayout(context);
            cell.setMinimumWidth(dp(200));

            BackupImageView imageView = new BackupImageView(context);
            imageView.setRoundRadius(chat != null && chat.forum ? dp(8) : dp(16));
            cell.addView(imageView, LayoutHelper.createFrameRelatively(32, 32, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            TextView titleView = new TextView(context);
            titleView.setLines(1);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            cell.addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 52, 0, 8, 0));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setScaleSize(.8f);
            Drawable thumb = avatarDrawable;
            CharSequence name;
            if (chat != null) {
                avatarDrawable.setInfo(account, chat);
                if (chat.photo != null && chat.photo.strippedBitmap != null) {
                    thumb = chat.photo.strippedBitmap;
                }
                imageView.setImage(ImageLocation.getForChat(account, chat, ImageLocation.TYPE_SMALL), "50_50", thumb, chat);
                name = chat.title;
            } else {
                if (user.photo != null && user.photo.strippedBitmap != null) {
                    thumb = user.photo.strippedBitmap;
                }
                if (UserObject.isUserSelf(user)) {
                    name = getString(R.string.SavedMessages);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    imageView.setImageDrawable(avatarDrawable);
                } else if (UserObject.isReplyUser(user)) {
                    name = getString(R.string.RepliesTitle);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                    imageView.setImageDrawable(avatarDrawable);
                } else if (UserObject.isDeleted(user)) {
                    name = getString(R.string.HiddenName);
                    avatarDrawable.setInfo(account, user);
                    imageView.setImage(ImageLocation.getForUser(account, user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user);
                } else {
                    name = UserObject.getUserName(user);
                    avatarDrawable.setInfo(account, user);
                    imageView.setImage(ImageLocation.getForUser(account, user, ImageLocation.TYPE_SMALL), "50_50", thumb, user);
                }
            }
            titleView.setText(Emoji.replaceEmoji(name, titleView.getPaint().getFontMetricsInt(), false));

            cell.setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), false));
            cell.setOnClickListener(v -> {
                ActionBarPopupWindow popup = popupRef.getAndSet(null);
                if (popup != null) {
                    popup.dismiss();
                }
                Bundle bundle = new Bundle();
                if (dialogId < 0) {
                    bundle.putLong("chat_id", -dialogId);
                    if (controller.isForum(dialogId)) {
                        fragment.presentFragment(TopicsFragment.getTopicsOrChat(fragment, bundle));
                        return;
                    }
                } else {
                    bundle.putLong("user_id", dialogId);
                }
                fragment.presentFragment(new ChatActivity(bundle));
            });
            cell.setOnLongClickListener(v -> {
                ActionBarPopupWindow popup = popupRef.getAndSet(null);
                if (popup != null) {
                    popup.dismiss();
                }
                Bundle bundle = new Bundle();
                bundle.putLong("dialog_id", dialogId);
                fragment.presentFragment(new ProfileActivity(bundle));
                return true;
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, Gravity.NO_GRAVITY, 0, 0, 0, last ? 3 : 0));
        }

        ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupRef.set(popupWindow);
        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        layout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        layout.setFitItems(true);

        int popupX = dp(7) - backgroundPaddings.left;
        if (AndroidUtilities.isTablet()) {
            int[] location = new int[2];
            fragmentView.getLocationInWindow(location);
            popupX += location[0];
        }
        int popupY = backButton.getBottom() - backgroundPaddings.top - dp(1);
        popupWindow.showAtLocation(fragmentView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
        return popupWindow;
    }
}
