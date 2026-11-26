/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mzgram.appearance.AppearanceConfig;
import org.mzgram.ext.ExtConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;

import java.util.ArrayList;
import java.util.Collections;

/**
 * The accounts at the end of the chat list menu (the three dots at the top of the chat
 * list): every signed-in account with its avatar and name, the current one marked, the
 * unread count of the others; a tap switches to it at once. Then "Add Account" while
 * there is room for one.
 *
 * Only while the navigation drawer is off: the drawer lists the accounts itself.
 * Settings > Navigation turns it off.
 */
public final class AccountsMenu {

    private AccountsMenu() {
    }

    public static boolean isOn() {
        return ExtConfig.INSTANCE.getAccountsInMenu().Bool() && !AppearanceConfig.navigationDrawer();
    }

    /** The signed-in accounts: the current one first, then the others by the time they signed in. */
    public static ArrayList<Integer> accounts(int currentAccount) {
        final ArrayList<Integer> others = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a != currentAccount && UserConfig.getInstance(a).isClientActivated()) {
                others.add(a);
            }
        }
        Collections.sort(others, (o1, o2) -> Long.compare(UserConfig.getInstance(o1).loginTime, UserConfig.getInstance(o2).loginTime));
        final ArrayList<Integer> result = new ArrayList<>();
        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            result.add(currentAccount);
        }
        result.addAll(others);
        return result;
    }

    public static void addTo(ItemOptions io, BaseFragment fragment) {
        if (!isOn() || io == null || fragment == null) {
            return;
        }
        final int currentAccount = fragment.getCurrentAccount();
        final ArrayList<Integer> accounts = accounts(currentAccount);
        if (accounts.isEmpty()) {
            return;
        }
        io.addGap();
        for (int account : accounts) {
            final boolean current = account == currentAccount;
            io.addAccount(account, current, () -> switchTo(account, currentAccount));
            if (!current) {
                addCounter(io.getLastView(), MessagesStorage.getInstance(account).getMainUnreadCount());
            }
        }
        if (UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT) {
            io.add(R.drawable.outline_add_account, LocaleController.getString(R.string.AddAccount), () -> addAccount(fragment));
        }
    }

    private static void switchTo(int account, int currentAccount) {
        if (account != currentAccount && LaunchActivity.instance != null) {
            LaunchActivity.instance.switchToAccount(account, true);
        }
    }

    // As "Add Account" elsewhere: the login screen for a free slot, or the Premium limit
    // when only Premium would give one.
    private static void addAccount(BaseFragment fragment) {
        int freeAccounts = 0;
        Integer availableAccount = null;
        for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                freeAccounts++;
                if (availableAccount == null) {
                    availableAccount = a;
                }
            }
        }
        if (!UserConfig.hasPremiumOnAccounts()) {
            freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
        }
        if (freeAccounts > 0 && availableAccount != null) {
            fragment.presentFragment(new LoginActivity(availableAccount));
        } else if (!UserConfig.hasPremiumOnAccounts() && fragment.getContext() != null) {
            fragment.showDialog(new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_ACCOUNTS, fragment.getCurrentAccount(), null));
        }
    }

    // The unread count of another account at the end of its row.
    private static void addCounter(View row, int count) {
        if (count <= 0 || !(row instanceof ViewGroup)) {
            return;
        }
        final TextView counter = new TextView(row.getContext());
        counter.setPadding(dp(6.66f), 0, dp(6.66f), 0);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        counter.setTypeface(AndroidUtilities.bold());
        counter.setGravity(Gravity.CENTER);
        counter.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        counter.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton)));
        counter.setText(LocaleController.formatNumber(count, ','));
        ((ViewGroup) row).addView(counter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 8, 0, 8, 0));
    }
}
