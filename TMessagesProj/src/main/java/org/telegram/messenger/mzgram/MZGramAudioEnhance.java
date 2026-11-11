/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.telegram.messenger.mzgram;

import android.media.AudioRecord;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

/**
 * MZGram: turns on the system noise suppressor and automatic gain control for
 * the microphone while a voice message is recorded. Uses only what the phone
 * already offers (android.media.audiofx); nothing is downloaded and the audio
 * never leaves the device. Off by default.
 */
public class MZGramAudioEnhance {

    private static NoiseSuppressor noiseSuppressor;
    private static AutomaticGainControl gainControl;

    /** Called right after the voice recorder is created, before it starts. */
    public static synchronized void attach(AudioRecord recorder) {
        release();
        if (!MZGramConfig.audioEnhance || recorder == null) {
            return;
        }
        try {
            int session = recorder.getAudioSessionId();
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(session);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                }
            }
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(session);
                if (gainControl != null) {
                    gainControl.setEnabled(true);
                }
            }
        } catch (RuntimeException ignore) {
        }
    }

    public static synchronized void release() {
        try {
            if (noiseSuppressor != null) {
                noiseSuppressor.release();
            }
            if (gainControl != null) {
                gainControl.release();
            }
        } catch (RuntimeException ignore) {
        }
        noiseSuppressor = null;
        gainControl = null;
    }
}
