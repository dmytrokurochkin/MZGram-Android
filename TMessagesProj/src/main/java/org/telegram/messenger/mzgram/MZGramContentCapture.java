/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Other > Share screen content with Android. Android's
 * content capture (Android 10 and later) lets system features such as
 * on-screen suggestions and live translation read what the app shows. Off,
 * MZGram turns it off for its window. From exteraless (GPL-3.0).
 */

package org.telegram.messenger.mzgram;

import android.app.Activity;
import android.os.Build;
import android.view.contentcapture.ContentCaptureManager;

import org.telegram.messenger.FileLog;

public class MZGramContentCapture {

    public static void apply(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            ContentCaptureManager manager = activity.getSystemService(ContentCaptureManager.class);
            // On is Android's own default: only a change from it is told
            if (manager != null && (!MZGramConfig.contentCapture || !manager.isContentCaptureEnabled())) {
                manager.setContentCaptureEnabled(MZGramConfig.contentCapture);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
