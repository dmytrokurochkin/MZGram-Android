/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Account order: the signed-in accounts can be put in any order by holding
 * and dragging a row. Every account list (Settings, the account switchers,
 * MZGram's accounts in the menu) sorts by UserConfig.loginTime, so the new
 * order is kept by handing the same loginTime values back out in the new
 * order. Nothing changes until a row is moved. Accounts hidden by account
 * passcodes are not shown and keep their values. After the first move the
 * account select dialog (opening links) follows the order too; before it,
 * that dialog keeps Telegram's slot order.
 *
 * Ported from Mercurygram MgAccountOrderActivity and
 * HiddenAccountHelper.setVisibleAccountOrder (GPL-2.0); adapted for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramPasscodeAccounts;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MZGramAccountOrderActivity extends UniversalFragment {

    private int orderSectionId = -1;

    /** The accounts shown from the given account, by loginTime as every list sorts them. */
    public static ArrayList<Integer> listedAccounts(int currentAccount) {
        ArrayList<Integer> accounts = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && MZGramPasscodeAccounts.isListed(a, currentAccount)) {
                accounts.add(a);
            }
        }
        Collections.sort(accounts, (o1, o2) -> Long.compare(UserConfig.getInstance(o1).loginTime, UserConfig.getInstance(o2).loginTime));
        return accounts;
    }

    /**
     * New loginTime values for the accounts in the given order: the same values
     * sorted, made strictly increasing so equal values cannot tie.
     */
    public static int[] orderedTimes(int[] currentTimes) {
        int[] times = currentTimes.clone();
        Arrays.sort(times);
        for (int i = 1; i < times.length; i++) {
            if (times[i] <= times[i - 1]) {
                times[i] = times[i - 1] + 1;
            }
        }
        return times;
    }

    /** Saves the order: the first account gets the smallest loginTime. */
    public static void setOrder(List<Integer> order) {
        int[] current = new int[order.size()];
        for (int i = 0; i < current.length; i++) {
            current[i] = UserConfig.getInstance(order.get(i)).loginTime;
        }
        int[] times = orderedTimes(current);
        for (int i = 0; i < times.length; i++) {
            UserConfig userConfig = UserConfig.getInstance(order.get(i));
            if (userConfig.loginTime != times[i]) {
                userConfig.loginTime = times[i];
                userConfig.saveConfig(false);
            }
        }
        MZGramConfig.setAccountOrderCustomised();
    }

    /**
     * Every account slot in the order a list walks them: by slot number as in
     * Telegram, or by loginTime once the accounts were put in a new order.
     */
    public static int[] slotOrder() {
        Integer[] slots = new Integer[UserConfig.MAX_ACCOUNT_COUNT];
        for (int a = 0; a < slots.length; a++) {
            slots[a] = a;
        }
        if (MZGramConfig.accountOrderCustomised) {
            Arrays.sort(slots, (o1, o2) -> Long.compare(UserConfig.getInstance(o1).loginTime, UserConfig.getInstance(o2).loginTime));
        }
        int[] result = new int[slots.length];
        for (int a = 0; a < slots.length; a++) {
            result[a] = slots[a];
        }
        return result;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramAccountOrder);
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        listView.listenReorder(this::whenReordered);
        listView.allowReorder(true);
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ArrayList<Integer> accounts = listedAccounts(currentAccount);
        orderSectionId = adapter.reorderSectionStart();
        for (int i = 0; i < accounts.size(); i++) {
            items.add(SettingsActivity.AccountCell.Factory.of(i + 1, accounts.get(i)));
        }
        adapter.reorderSectionEnd();
        items.add(UItem.asShadow(getString(R.string.MZGramAccountOrderInfo)));
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        if (id != orderSectionId || items == null) {
            return;
        }
        ArrayList<Integer> order = new ArrayList<>();
        for (UItem item : items) {
            order.add(item.intValue);
        }
        setOrder(order);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
