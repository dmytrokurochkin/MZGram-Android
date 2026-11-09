/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Preview of the avatar shape in the MZGram settings: your own avatar, a
 * letter avatar and Saved Messages, drawn the way the chat list draws them,
 * so the slider shows the result at once.
 *
 * Idea from exteraless AvatarCornersPreviewCell (GPL-3.0), written anew.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class MZGramAvatarShapePreview extends LinearLayout {

    public static final int HEIGHT_DP = 88;
    private static final int SIZE_DP = 56;

    private final BackupImageView[] avatars = new BackupImageView[3];

    public MZGramAvatarShapePreview(Context context, int account) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        for (int i = 0; i < avatars.length; i++) {
            BackupImageView view = new BackupImageView(context);
            view.setRoundRadius(dp(SIZE_DP / 2f));
            AvatarDrawable drawable = new AvatarDrawable();
            if (i == 0 && self != null) {
                drawable.setInfo(account, self);
                view.setForUserOrChat(self, drawable);
            } else if (i == 2) {
                drawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                view.setImageDrawable(drawable);
            } else {
                drawable.setInfo(7, "MZ", "Gram");
                view.setImageDrawable(drawable);
            }
            avatars[i] = view;
            addView(view, LayoutHelper.createLinear(SIZE_DP, SIZE_DP, Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
        }
    }

    // After a change of the slider: the avatars pick the new shape when drawn
    public void update() {
        for (BackupImageView view : avatars) {
            view.invalidate();
        }
    }
}
