/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The "Ghost mode in this chat" item of a chat's menu: follow the ghost mode
 * switch, always show reads and typing here, or never show them here
 * (MZGramGhostMode exclusions). Shown while Settings > MZGram > Ghost Mode >
 * "Ghost mode per chat" is on.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramGhostMode;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;

public final class MZGramGhostExclusionMenu {

    private MZGramGhostExclusionMenu() {
    }

    public static boolean isAvailable(TLRPC.User user, int chatMode) {
        return MZGramConfig.ghostModeExclusions && chatMode == 0 && !UserObject.isUserSelf(user);
    }

    public static void show(BaseFragment fragment, long dialogId) {
        if (fragment == null || fragment.getParentActivity() == null || dialogId == 0) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        String[] options = {
                getString(R.string.MZGramGhostExclusionDefault),
                getString(R.string.MZGramGhostExclusionAlwaysRead),
                getString(R.string.MZGramGhostExclusionNeverRead),
        };
        int selected = MZGramGhostMode.getExclusion(account, dialogId);
        fragment.showDialog(AlertsCreator.createSingleChoiceDialog(fragment.getParentActivity(), options, getString(R.string.MZGramGhostExclusion), selected, (dialog, which) -> {
            MZGramGhostMode.setExclusion(account, dialogId, which);
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, options[which]).show();
        }, getString(R.string.MZGramCancel)));
    }
}
