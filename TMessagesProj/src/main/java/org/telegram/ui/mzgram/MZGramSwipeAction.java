/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Swipe action: what a swipe to the left on a message does. Reply is
 * Telegram's own; the others run the same action of the message menu
 * (ChatActivity.mzgramSwipeAction makes the checks the menu makes). Where
 * the chosen action is not allowed, for example Pin without admin rights,
 * the swipe replies as in Telegram.
 */

package org.telegram.ui.mzgram;

import org.telegram.messenger.R;

public final class MZGramSwipeAction {

    // The values stored in MZGramConfig.swipeAction
    public static final int REPLY = 0;
    public static final int FORWARD = 1;
    public static final int COPY = 2;
    public static final int EDIT = 3;
    public static final int PIN = 4;
    public static final int DELETE = 5;
    public static final int[] TITLES = {
            R.string.MZGramDoubleTapReply,
            R.string.MZGramSwipeForward,
            R.string.MZGramDoubleTapCopy,
            R.string.MZGramSwipeEdit,
            R.string.MZGramSwipePin,
            R.string.MZGramSwipeDelete,
    };

    private MZGramSwipeAction() {
    }
}
