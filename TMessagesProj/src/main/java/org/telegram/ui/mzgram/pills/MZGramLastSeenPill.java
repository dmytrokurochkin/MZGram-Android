/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Pill: your own last seen, as Telegram's server tells it (online, or the
 * last seen time). Asks Telegram's server for your own user
 * (users.getUsers) when the pill comes on screen, on a tap and once a
 * minute while it stays on screen; no other server is asked. Green while
 * online.
 *
 * Idea from exteraless LastSeenPill (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;

@SuppressLint("ViewConstructor")
public class MZGramLastSeenPill extends MZGramPill {

    private boolean online;
    private int generation;

    public MZGramLastSeenPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setIcon(R.drawable.msg_online);
        showLocal(false);
    }

    @Override
    public int pillId() {
        return MZGramPills.LAST_SEEN;
    }

    @Override
    public long refreshInterval() {
        return 60_000;
    }

    @Override
    protected int accentColor() {
        return online ? Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, resourcesProvider) : super.accentColor();
    }

    // What this device already knows about you, until the server answers
    private void showLocal(boolean animated) {
        int account = UserConfig.selectedAccount;
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        show(account, self != null ? MessagesController.getInstance(account).getUser(self.id) : null, animated);
    }

    private void show(int account, TLRPC.User user, boolean animated) {
        boolean now = user != null && user.status instanceof TLRPC.TL_userStatusOnline
                && user.status.expires > ConnectionsManager.getInstance(account).getCurrentTime();
        String text;
        if (user == null || user.status == null) {
            text = "...";
        } else if (now) {
            text = LocaleController.getString(R.string.Online); // Telegram's own text: "online"
        } else {
            text = LocaleController.formatUserStatus(account, user);
        }
        if (now != online) {
            online = now;
            updateColors();
        }
        setText(text, animated);
    }

    @Override
    public void update(boolean animated) {
        final int account = UserConfig.selectedAccount;
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self == null) {
            show(account, null, animated);
            return;
        }
        TLRPC.InputUser inputUser = MessagesController.getInstance(account).getInputUser(self);
        if (inputUser == null) {
            return;
        }
        final int request = ++generation;
        final long selfId = self.id;
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        req.id.add(inputUser);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (request != generation || !isAttachedToWindow()) {
                return;
            }
            TLRPC.User user = null;
            if (response instanceof Vector) {
                for (Object object : ((Vector) response).objects) {
                    if (object instanceof TLRPC.User && ((TLRPC.User) object).id == selfId) {
                        user = (TLRPC.User) object;
                    }
                }
            }
            if (user != null) {
                show(account, user, true);
            }
        }));
    }

    @Override
    public void onTap() {
        update(true);
    }

    @Override
    protected void addOptions(ItemOptions options, BaseFragment fragment) {
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh), () -> update(true)); // Telegram's own text: "Refresh"
    }
}
