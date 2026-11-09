/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Privacy > Account passcodes > "Set up account
 * passcodes": every signed-in account with its own passcode for the app
 * lock (set, change, remove) and, once it has one, whether it is hidden.
 * The logic is in MZGramPasscodeAccounts.
 *
 * Ported from Nekogram (GPL-3.0),
 * TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/NekoPasscodeSettingsActivity.java
 * (account rows only: no panic code, no hidden settings link).
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mzgram.MZGramPasscodeAccounts;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.PasscodeActivity;

import java.util.ArrayList;
import java.util.Collections;

public class MZGramPasscodeAccountsActivity extends UniversalFragment {

    private static final int BUTTON_APP_PASSCODE = 1;
    // Rows of an account: ACCOUNT_ROWS + account * 10 + action
    private static final int ACCOUNT_ROWS = 100;
    private static final int ACTION_SET = 0;
    private static final int ACTION_HIDE = 1;
    private static final int ACTION_REMOVE = 2;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramPasscodeAccounts);
    }

    @Override
    public void onResume() {
        super.onResume();
        // The app passcode may have been set or changed meanwhile
        update();
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private static int rowId(int account, int action) {
        return ACCOUNT_ROWS + account * 10 + action;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (TextUtils.isEmpty(SharedConfig.passcodeHash)) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asButton(BUTTON_APP_PASSCODE, getString(R.string.MZGramPasscodeLock)));
            items.add(UItem.asShadow(getString(R.string.MZGramPasscodeAccountsNeedApp)));
            return;
        }
        ArrayList<Integer> accounts = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(a);
            }
        }
        Collections.sort(accounts, (o1, o2) -> Long.compare(UserConfig.getInstance(o1).loginTime, UserConfig.getInstance(o2).loginTime));
        items.add(UItem.asShadow(null));
        for (int i = 0; i < accounts.size(); i++) {
            int account = accounts.get(i);
            TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
            items.add(UItem.asHeader(user != null ? UserObject.getUserName(user) : String.valueOf(account + 1)));
            boolean hasCode = MZGramPasscodeAccounts.hasCode(account);
            items.add(UItem.asButton(rowId(account, ACTION_SET), getString(hasCode ? R.string.MZGramPasscodeChange : R.string.MZGramPasscodeSet)));
            if (hasCode) {
                items.add(UItem.asCheck(rowId(account, ACTION_HIDE), getString(R.string.MZGramPasscodeHide)).setChecked(MZGramPasscodeAccounts.isHideSet(account)));
                items.add(UItem.asButton(rowId(account, ACTION_REMOVE), getString(R.string.MZGramPasscodeRemove)).red());
            }
            items.add(UItem.asShadow(i == accounts.size() - 1 ? getString(R.string.MZGramPasscodeAccountsListInfo) : null));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_APP_PASSCODE) {
            presentFragment(PasscodeActivity.determineOpenFragment());
            return;
        }
        if (item.id < ACCOUNT_ROWS) {
            return;
        }
        int account = (item.id - ACCOUNT_ROWS) / 10;
        int action = (item.id - ACCOUNT_ROWS) % 10;
        if (account >= UserConfig.MAX_ACCOUNT_COUNT || !UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        if (action == ACTION_SET) {
            editCode(account);
        } else if (action == ACTION_HIDE) {
            boolean hide = !MZGramPasscodeAccounts.isHideSet(account);
            if (hide && !MZGramPasscodeAccounts.canHide(account)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.MZGramPasscodeLastVisible)).show();
                return;
            }
            MZGramPasscodeAccounts.setHidden(account, hide);
            ((TextCheckCell) view).setChecked(hide);
        } else if (action == ACTION_REMOVE) {
            MZGramPasscodeAccounts.forget(account);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramPasscodeRemoved)).show();
            update();
        }
    }

    private EditTextBoldCursor codeField(String hint) {
        boolean pin = SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN;
        EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
        editText.setSingleLine(true);
        if (pin) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MZGramPasscodeAccounts.PIN_LENGTH)});
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        editText.setHint(hint);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, getResourceProvider()));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setLineColors(
                Theme.getColor(Theme.key_dialogInputField, getResourceProvider()),
                Theme.getColor(Theme.key_dialogInputFieldActivated, getResourceProvider()),
                Theme.getColor(Theme.key_text_RedBold, getResourceProvider()));
        editText.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        return editText;
    }

    // The passcode twice, of the kind the app lock asks for (a 4-digit PIN or
    // a password); it must differ from the app passcode and from the other
    // accounts' passcodes.
    private void editCode(int account) {
        if (getContext() == null) {
            return;
        }
        EditTextBoldCursor first = codeField(getString(R.string.MZGramPasscodeEnter));
        EditTextBoldCursor second = codeField(getString(R.string.MZGramPasscodeRepeat));
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(first, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 0));
        layout.addView(second, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 0));
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(MZGramPasscodeAccounts.hasCode(account) ? R.string.MZGramPasscodeChange : R.string.MZGramPasscodeSet))
                .setView(layout)
                .setPositiveButton(getString(R.string.MZGramSave), (dialog, which) -> {
                    String code = first.getText() == null ? "" : first.getText().toString();
                    String repeat = second.getText() == null ? "" : second.getText().toString();
                    int error = check(account, code, repeat);
                    if (error != 0) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(error)).show();
                        return;
                    }
                    MZGramPasscodeAccounts.setCode(account, code);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramPasscodeSaved)).show();
                    update();
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
        AndroidUtilities.runOnUIThread(() -> {
            first.requestFocus();
            AndroidUtilities.showKeyboard(first);
        }, 200);
    }

    // The string of what is wrong with the new passcode, or 0
    private static int check(int account, String code, String repeat) {
        if (TextUtils.isEmpty(code)) {
            return R.string.MZGramPasscodeEmpty;
        }
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN
                && (code.length() != MZGramPasscodeAccounts.PIN_LENGTH || !TextUtils.isDigitsOnly(code))) {
            return R.string.MZGramPasscodePinLength;
        }
        if (!code.equals(repeat)) {
            return R.string.MZGramPasscodeMismatch;
        }
        if (SharedConfig.checkPasscode(code)) {
            return R.string.MZGramPasscodeSameAsApp;
        }
        if (MZGramPasscodeAccounts.isTakenByOther(account, code)) {
            return R.string.MZGramPasscodeTaken;
        }
        return 0;
    }
}
