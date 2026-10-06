/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The built-in Google FCM UnifiedPush distributor, for phones with Google
 * Play Services or microG and no distributor app. It asks Play Services
 * for a WebPush endpoint the way a browser does, with the gateway's VAPID
 * key, so the app needs no Google library and no Firebase project.
 *
 * Google FCM takes a push to that endpoint only when it is signed with the
 * same key, and Telegram does not sign, so the endpoint given to Telegram is
 * the gateway's /fcm/ route: the gateway signs and passes the push on. The
 * route and the key belong to one gateway and are changed together.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import org.unifiedpush.android.embedded_fcm_distributor.EmbeddedDistributorReceiver;
import org.unifiedpush.android.embedded_fcm_distributor.Gateway;

public class MZGramFcmDistributor extends EmbeddedDistributorReceiver {

    private static final String ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER";

    private static final Gateway GATEWAY = new Gateway() {
        @NonNull
        @Override
        public String getVapid() {
            return MZGramUnifiedPush.fcmVapidKey();
        }

        @NonNull
        @Override
        public String getEndpoint(@NonNull String token) {
            return MZGramUnifiedPush.fcmEndpoint(token);
        }
    };

    @Override
    public Gateway getGateway() {
        return GATEWAY;
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        if (ACTION_REGISTER.equals(intent.getAction())) {
            if (MZGramPushDiagnostics.playServices() == null) {
                MZGramPushDiagnostics.log("FCM: " + MZGramUnifiedPushRules.PLAY_SERVICES_PACKAGE + " is not installed");
            } else {
                MZGramPushDiagnostics.onFcmRequested();
            }
        }
        super.onReceive(context, intent);
    }
}
