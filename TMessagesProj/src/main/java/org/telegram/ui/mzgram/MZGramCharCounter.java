/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Chats > "Character counter": a live count of the
 * typed characters above the right end of the message field, for groups
 * where a bot limits the length of a message. Telegram shows its own
 * remaining-characters counter only near its limits; that one takes the
 * same place and wins. The count is in code points, as the Bot API counts
 * the length of a message.
 *
 * Ported from Mercurygram (GPL-2.0), TMessagesProj/src/main/java/it/
 * belloworld/mercurygram/ui/MgCharCounter.java; the switch is
 * MZGramConfig.characterCounter instead of a per-account one.
 */

package org.telegram.ui.mzgram;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;

public final class MZGramCharCounter {

    private static final String TAG = "mzgram_char_counter";

    private MZGramCharCounter() {
    }

    // limitVisible: Telegram's own counter shows (or is being shown by this
    // same change); it takes the same place.
    public static void update(FrameLayout parent, Theme.ResourcesProvider resourcesProvider, int count, boolean limitVisible) {
        final boolean show = count > 0 && !limitVisible && MZGramConfig.characterCounter;
        NumberTextView view = parent.findViewWithTag(TAG);
        if (view == null) {
            if (!show) {
                return;
            }
            view = new NumberTextView(parent.getContext());
            view.setTag(TAG);
            view.setTextSize(15);
            view.setTypeface(AndroidUtilities.bold());
            view.setCenterAlign(true);
            view.setVisibility(View.GONE);
            parent.addView(view, Math.min(2, parent.getChildCount()), LayoutHelper.createFrame(ChatActivityEnterView.DEFAULT_HEIGHT, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 0, ChatActivityEnterView.DEFAULT_HEIGHT));
        }
        if (show) {
            view.setNumber(count, view.getVisibility() == View.VISIBLE);
            view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }
}
