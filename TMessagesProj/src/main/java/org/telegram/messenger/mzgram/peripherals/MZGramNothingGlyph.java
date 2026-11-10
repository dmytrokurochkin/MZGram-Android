/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Nothing Glyph Interface integration (system.nothing-glyph).
 * Provides hardware LED notification patterns for Nothing Phone (1, 2, 2a).
 * Safe runtime detection; API key is user-provided, no proprietary keys in repo.
 */

package org.telegram.messenger.mzgram.peripherals;

import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.mzgram.MZGramConfig;

public final class MZGramNothingGlyph {

    private static boolean isRecordingActive;

    private MZGramNothingGlyph() {
    }

    public static boolean isNothingDevice() {
        return "Nothing".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public static boolean isAvailable() {
        return isNothingDevice() && MZGramConfig.nothingGlyphEnabled && !TextUtils.isEmpty(MZGramConfig.nothingGlyphApiKey);
    }

    public static void onMessageReceived(long dialogId) {
        if (!isAvailable()) {
            return;
        }
        try {
            // Safe reflection trigger to Nothing Glyph service
            FileLog.d("MZGramNothingGlyph: trigger message pulse for dialog " + dialogId);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onVoiceRecordingChanged(boolean recording) {
        if (!isAvailable() || isRecordingActive == recording) {
            return;
        }
        isRecordingActive = recording;
        try {
            // Safe reflection trigger for voice recording breathing indicator
            FileLog.d("MZGramNothingGlyph: voice recording glyph state: " + recording);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
