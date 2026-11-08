/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Bot privacy (Settings > MZGram > Privacy > "Ask before starting bots"): a
 * link that starts a bot with a start code (t.me/bot?start=code) tells the
 * bot which link brought you, and Telegram even sends it by itself when
 * the chat with the bot already has messages. With the switch on, every such
 * start asks first: start with the code, start without it (a plain /start),
 * or start the bot from another signed-in account so it never sees this one.
 * The same start sent again within a few seconds (a flaky connection, the
 * automatic start) is dropped. Nothing is checked over the network.
 *
 * Idea from exteraless (GPL-3.0), app/exteraless/privacy/BotAccountRouter.java,
 * BotAccountPolicy.java and BotStartDeduplicator.java.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.os.SystemClock;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public final class MZGramBotStartGuard {

    private static final long DUPLICATE_WINDOW_MS = 10000;

    // Starts the owner already chose, let through once: account:botId:code,
    // or account:@username:code for a start opened in another account.
    private static final HashSet<String> allowed = new HashSet<>();
    private static final HashMap<String, Long> recent = new HashMap<>();
    private static boolean asking;

    private MZGramBotStartGuard() {
    }

    // MessagesController.sendBotStart: true when the start is held for the
    // owner's choice or dropped as a repeat; false lets it go now.
    public static boolean intercept(int account, TLRPC.User bot, String code) {
        if (!MZGramConfig.botStartConfirm || bot == null || TextUtils.isEmpty(code)) {
            return false;
        }
        final String key = account + ":" + bot.id + ":" + code;
        final String username = UserObject.getPublicUsername(bot);
        final String byName = username == null ? null : account + ":@" + username.toLowerCase() + ":" + code;
        final long now = SystemClock.elapsedRealtime();
        synchronized (allowed) {
            Long last = recent.get(key);
            if (last != null && now - last < DUPLICATE_WINDOW_MS) {
                return true;
            }
            if (allowed.remove(key) || byName != null && allowed.remove(byName)) {
                recent.put(key, now);
                return false;
            }
        }
        AndroidUtilities.runOnUIThread(() -> ask(account, bot, code, key, username));
        return true;
    }

    private static void ask(int account, TLRPC.User bot, String code, String key, String username) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (asking || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final ArrayList<CharSequence> items = new ArrayList<>();
        final ArrayList<Integer> otherAccounts = new ArrayList<>();
        items.add(getString(R.string.MZGramBotStartWithCode));
        items.add(getString(R.string.MZGramBotStartWithoutCode));
        if (username != null) {
            for (int other : MZGramAccountsMenu.accounts(account)) {
                if (other == account) {
                    continue;
                }
                TLRPC.User self = UserConfig.getInstance(other).getCurrentUser();
                items.add(formatString(R.string.MZGramBotStartFromAccount, self != null ? UserObject.getUserName(self) : String.valueOf(other)));
                otherAccounts.add(other);
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(formatString(R.string.MZGramBotStartTitle, UserObject.getUserName(bot), code));
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which == 0) {
                synchronized (allowed) {
                    allowed.add(key);
                }
                MessagesController.getInstance(account).sendBotStart(bot, code);
            } else if (which == 1) {
                SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of("/start", bot.id, null, null, null, false, null, null, null, true, 0, 0, null, false));
            } else if (which - 2 < otherAccounts.size()) {
                startFromAccount(otherAccounts.get(which - 2), username, code);
            }
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        asking = true;
        if (fragment.showDialog(builder.create(), d -> asking = false) == null) {
            asking = false;
        }
    }

    // Switches to the other account and opens the same start link there;
    // that start goes without asking again.
    private static void startFromAccount(int other, String username, String code) {
        if (LaunchActivity.instance == null) {
            return;
        }
        synchronized (allowed) {
            allowed.add(other + ":@" + username.toLowerCase() + ":" + code);
        }
        LaunchActivity.instance.switchToAccount(other, true);
        final String url = "https://t.me/" + username + "?start=" + android.net.Uri.encode(code);
        AndroidUtilities.runOnUIThread(() -> {
            if (LaunchActivity.instance != null) {
                Browser.openUrl(LaunchActivity.instance, url);
            }
        }, 500);
    }
}
