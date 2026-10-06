/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Writes down what Google Play Services (or microG) answered to the
 * built-in Google FCM registration, for the diagnostics screen. The
 * distributor library handles the same answer; this only looks at it.
 */

package org.telegram.messenger.mzgram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MZGramFcmRegistrationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"com.google.android.c2dm.intent.REGISTRATION".equals(intent.getAction())) {
            return;
        }
        String registration = intent.getStringExtra("registration_id");
        if (registration != null) {
            // "<gateway flag>:<registration>:<token>" for the distributor's
            // requests; anything else is not one of them.
            if (registration.split(":", 3).length == 3) {
                MZGramPushDiagnostics.onFcmAnswer(true, null);
            }
            return;
        }
        String error = intent.getStringExtra("error");
        if (error == null && intent.hasExtra("unregistered")) {
            error = "unregistered";
        }
        MZGramPushDiagnostics.onFcmAnswer(false, error);
    }
}
