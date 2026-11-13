/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > General > Hide phone number: the account's own number
 * is no longer shown in Telegram's Settings header, in the own profile and
 * in the Phone number row, so it stays out of screenshots and screen shares.
 * Other people's numbers, and what the server shares, do not change. Off by
 * default.
 *
 * Idea from exteraless (GPL-3.0), "Hide phone number"; written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import org.telegram.tgnet.TLRPC;

public final class MZGramHidePhone {

    private MZGramHidePhone() {
    }

    // Whether the number of this user is the own one and is to be hidden
    public static boolean hides(TLRPC.User user, boolean myProfile) {
        return MZGramConfig.hidePhoneNumber && (myProfile || user != null && user.self);
    }

    public static boolean hidesOwn() {
        return MZGramConfig.hidePhoneNumber;
    }
}
