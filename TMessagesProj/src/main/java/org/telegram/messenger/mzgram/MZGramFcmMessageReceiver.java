/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Writes down every message Google Play Services (or microG) delivers to
 * the app, for the diagnostics screen, before the distributor library
 * looks at it: this tells a push that never reached the phone apart from
 * one the app lost on the way. Only looks; the library handles it.
 */

package org.telegram.messenger.mzgram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MZGramFcmMessageReceiver extends BroadcastReceiver {

    public static final String ACTION_RECEIVE = "com.google.android.c2dm.intent.RECEIVE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_RECEIVE.equals(intent.getAction())) {
            return;
        }
        byte[] data = intent.getByteArrayExtra("rawData");
        MZGramPushDiagnostics.onGmsMessage(data != null ? data.length : 0, intent.getStringExtra("subtype"));
    }
}
