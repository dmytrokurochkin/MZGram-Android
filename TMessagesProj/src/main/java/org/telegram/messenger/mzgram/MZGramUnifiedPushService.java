/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Receives what the UnifiedPush distributor sends; MZGramUnifiedPush does
 * the work.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

public class MZGramUnifiedPushService extends PushService {

    @Override
    public void onNewEndpoint(PushEndpoint endpoint, String instance) {
        String url = endpoint.getUrl();
        Utilities.globalQueue.postRunnable(() -> MZGramUnifiedPush.onNewEndpoint(url));
    }

    @Override
    public void onMessage(PushMessage message, String instance) {
        MZGramUnifiedPush.onMessage(message.getContent());
    }

    @Override
    public void onRegistrationFailed(FailedReason reason, String instance) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("UnifiedPush registration failed: " + reason);
        }
        String why = String.valueOf(reason);
        Utilities.globalQueue.postRunnable(() -> MZGramUnifiedPush.onRegistrationFailed(why));
    }

    @Override
    public void onUnregistered(String instance) {
        MZGramPushDiagnostics.log("unregistered by the distributor");
        Utilities.globalQueue.postRunnable(MZGramUnifiedPush::onRegistrationLost);
    }
}
