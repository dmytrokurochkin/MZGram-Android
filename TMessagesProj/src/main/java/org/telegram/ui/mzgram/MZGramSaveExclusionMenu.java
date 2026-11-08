/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The "Archive in this chat" item of a chat's menu: follow Keep messages,
 * always keep other people's deleted and edited messages here, or never keep
 * them here (MZGramSaveExclusions). Shown while Settings > MZGram > Archive >
 * "Archive rules per chat" is on.
 *
 * Idea from Nekogram (GPL-3.0), tw/nekomimi/nekogram/menu/saveDeleted/
 * SaveExclusionPopupWrapper.java; written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramSaveExclusions;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;

public final class MZGramSaveExclusionMenu {

    private MZGramSaveExclusionMenu() {
    }

    // Not in Saved Messages: nothing of other people is there to keep.
    public static boolean isAvailable(TLRPC.User user, int chatMode) {
        return MZGramConfig.saveExclusions && chatMode == 0 && !UserObject.isUserSelf(user);
    }

    public static void show(BaseFragment fragment, long dialogId) {
        if (fragment == null || fragment.getParentActivity() == null || dialogId == 0) {
            return;
        }
        String[] options = {
                getString(R.string.MZGramSaveExclusionDefault),
                getString(R.string.MZGramSaveExclusionAlways),
                getString(R.string.MZGramSaveExclusionNever),
        };
        int selected = MZGramSaveExclusions.getRule(dialogId);
        fragment.showDialog(AlertsCreator.createSingleChoiceDialog(fragment.getParentActivity(), options, getString(R.string.MZGramSaveExclusion), selected, (dialog, which) -> {
            MZGramSaveExclusions.setRule(dialogId, which);
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, options[which]).show();
        }, getString(R.string.MZGramCancel)));
    }
}
