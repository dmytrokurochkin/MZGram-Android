/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Account passcodes (Settings > MZGram > Privacy > "Account passcodes"):
 * every signed-in account can have its own passcode for the app lock.
 * Entering it on the lock screen unlocks the app and opens that account;
 * the app passcode (or the fingerprint) opens a visible one. An account with
 * its own passcode can be hidden: it is left out of every list of accounts
 * (Settings, the three-dots menu of the chat list, the account switcher, the
 * account choice of links and bots) and shows no notifications, so only its
 * passcode reaches it.
 *
 * Everything is on this device only, in "mzgram_passcode" (MZGramAccountPrefs):
 * per account a salted SHA-256 of the code, the same way Telegram keeps the
 * app passcode, the kind of code (PIN or password) it was made for and the
 * hide flag. A code works, and an account is hidden, only while the switch
 * is on, the app passcode is set and of the same kind; turning either off
 * shows hidden accounts again, so no account can be lost.
 *
 * Ported from Nekogram (GPL-3.0),
 * TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/PasscodeHelper.java.
 * The panic code of the original (a code that logs out of accounts) is not
 * here: see panicCodeMatches.
 */

package org.telegram.messenger.mzgram;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;

public final class MZGramPasscodeAccounts {

    private static final String FEATURE = "passcode";
    private static final String KEY_HASH = "hash";
    private static final String KEY_SALT = "salt";
    private static final String KEY_TYPE = "type";
    private static final String KEY_HIDE = "hide";

    // A PIN of the app lock has this many digits (PasscodeView)
    public static final int PIN_LENGTH = 4;

    private MZGramPasscodeAccounts() {
    }

    private static MZGramAccountPrefs prefs(int account) {
        return MZGramAccountPrefs.of(FEATURE, account);
    }

    private static boolean validAccount(int account) {
        return account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT;
    }

    // Account passcodes can work at all: the switch is on and the app has
    // its passcode.
    public static boolean isAvailable() {
        return MZGramConfig.passcodeAccounts && !TextUtils.isEmpty(SharedConfig.passcodeHash);
    }

    // The account has a passcode of the kind the app lock asks for now
    public static boolean hasCode(int account) {
        if (!validAccount(account)) {
            return false;
        }
        MZGramAccountPrefs prefs = prefs(account);
        return !TextUtils.isEmpty(prefs.getString(KEY_HASH, null))
                && !TextUtils.isEmpty(prefs.getString(KEY_SALT, null))
                && prefs.getInt(KEY_TYPE, -1) == SharedConfig.passcodeType;
    }

    // The hide flag as set, whether or not it works right now
    public static boolean isHideSet(int account) {
        return validAccount(account) && prefs(account).getBoolean(KEY_HIDE, false);
    }

    // The account is hidden: left out of account lists, no notifications
    public static boolean isHidden(int account) {
        return isAvailable() && hasCode(account) && isHideSet(account);
    }

    // The account is shown in a list of accounts opened from currentAccount:
    // a hidden account only while it is the current one.
    public static boolean isListed(int account, int currentAccount) {
        return account == currentAccount || !isHidden(account);
    }

    // Signed-in accounts a list shows from the selected account; for "is
    // there another account to switch to". Free slots are still counted with
    // UserConfig.getActivatedAccountsCount.
    public static int listedAccountsCount() {
        int count = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && isListed(a, UserConfig.selectedAccount)) {
                count++;
            }
        }
        return count;
    }

    // The first signed-in account that is not hidden, or fallback
    public static int firstVisibleAccount(int fallback) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && !isHidden(a)) {
                return a;
            }
        }
        return fallback;
    }

    // Hiding this account still leaves another signed-in account visible
    public static boolean canHide(int account) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a != account && UserConfig.getInstance(a).isClientActivated() && !isHidden(a)) {
                return true;
            }
        }
        return false;
    }

    public static void setHidden(int account, boolean hide) {
        if (validAccount(account)) {
            prefs(account).putBoolean(KEY_HIDE, hide);
        }
    }

    public static void setCode(int account, String code) {
        if (!validAccount(account) || TextUtils.isEmpty(code)) {
            return;
        }
        try {
            byte[] salt = new byte[16];
            Utilities.random.nextBytes(salt);
            MZGramAccountPrefs prefs = prefs(account);
            prefs.putString(KEY_HASH, hash(code, salt));
            prefs.putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
            prefs.putInt(KEY_TYPE, SharedConfig.passcodeType);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // The passcode and the hide flag of the account go; called when it is
    // removed in settings and when a new login takes the account slot.
    public static void forget(int account) {
        if (validAccount(account)) {
            prefs(account).clear();
        }
    }

    // The code is this account's own passcode
    public static boolean matches(int account, String code) {
        if (!hasCode(account) || TextUtils.isEmpty(code)) {
            return false;
        }
        try {
            MZGramAccountPrefs prefs = prefs(account);
            byte[] salt = Base64.decode(prefs.getString(KEY_SALT, ""), Base64.DEFAULT);
            if (salt.length != 16) {
                return false;
            }
            return hash(code, salt).equals(prefs.getString(KEY_HASH, ""));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    // Another signed-in account already uses this code
    public static boolean isTakenByOther(int account, String code) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a != account && UserConfig.getInstance(a).isClientActivated() && matches(a, code)) {
                return true;
            }
        }
        return false;
    }

    // As Telegram hashes the app passcode (PasscodeActivity): salt, code, salt
    private static String hash(String code, byte[] salt) {
        byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[32 + codeBytes.length];
        System.arraycopy(salt, 0, bytes, 0, 16);
        System.arraycopy(codeBytes, 0, bytes, 16, codeBytes.length);
        System.arraycopy(salt, 0, bytes, codeBytes.length + 16, 16);
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
    }

    private static LaunchActivity launchActivity(Context context) {
        Activity activity = context == null ? null : AndroidUtilities.findActivity(context);
        return activity instanceof LaunchActivity ? (LaunchActivity) activity : null;
    }

    // The lock screen (PasscodeView.processDone): the signed-in account whose
    // own passcode was entered, or -1 to check the app passcode as always.
    // Only the app lock of the main window opens accounts.
    public static int accountForCode(Context context, String code) {
        if (!isAvailable() || TextUtils.isEmpty(code) || launchActivity(context) == null) {
            return -1;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && matches(a, code)) {
                return a;
            }
        }
        return -1;
    }

    // Extension point for a panic code (the owner has not decided on it yet):
    // a code that, entered on the lock screen, would log out of the accounts
    // allowed for it (Nekogram: MessagesController.performLogout(1) for each)
    // and then fail as a wrong passcode. It would be checked in
    // PasscodeView.processDone right before accountForCode. Nothing is stored
    // for it and it never matches.
    public static boolean panicCodeMatches(String code) {
        return false;
    }

    // The lock screen was passed: with an account's own passcode, open that
    // account; with the app passcode or the fingerprint (account -1), leave a
    // hidden account for a visible one.
    public static void onUnlocked(Context context, int account) {
        LaunchActivity launchActivity = launchActivity(context);
        if (launchActivity == null) {
            return;
        }
        int current = UserConfig.selectedAccount;
        if (account >= 0) {
            if (account != current && UserConfig.getInstance(account).isClientActivated()) {
                launchActivity.switchToAccount(account, true);
            }
        } else if (isHidden(current)) {
            int visible = firstVisibleAccount(-1);
            if (visible >= 0 && visible != current) {
                launchActivity.switchToAccount(visible, true);
            }
        }
    }
}
