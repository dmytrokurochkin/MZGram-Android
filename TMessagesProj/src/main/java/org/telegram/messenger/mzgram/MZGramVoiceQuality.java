/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Bitrate of the voice messages MZGram records. Telegram encodes them with
 * Opus at 30 kbit/s; here it can be kept (the default, the native encoder is
 * not told anything) or raised to 64 or 128 kbit/s, for a clearer voice in
 * a bigger file. The value is handed to the Opus encoders in audio.c (both
 * recorders) and applies from the next recording.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public final class MZGramVoiceQuality {

    public static final int TELEGRAM = 0;
    public static final int HIGH = 1;
    public static final int HIGHEST = 2;

    public static final int[] TITLES = {
            R.string.MZGramVoiceQualityTelegram,
            R.string.MZGramVoiceQualityHigh,
            R.string.MZGramVoiceQualityHighest
    };

    private static final int[] BITRATES = {0, 64_000, 128_000};

    private MZGramVoiceQuality() {
    }

    public static int mode() {
        final int mode = MZGramConfig.voiceQuality;
        return mode == HIGH || mode == HIGHEST ? mode : TELEGRAM;
    }

    /** Bitrate in bit/s for the encoder, 0 keeps Telegram's. */
    public static int bitrate() {
        return BITRATES[mode()];
    }

    /** Hands the bitrate to the native encoders: at the start and after a change. */
    public static void apply() {
        final int bitrate = bitrate();
        if (bitrate == 0 && !applied) {
            // Nothing was changed in this process, the encoder keeps Telegram's own value
            return;
        }
        try {
            nSetVoiceBitrate(bitrate);
            applied = bitrate != 0;
        } catch (Throwable e) {
            FileLog.e("MZGramVoiceQuality.apply", e);
        }
    }

    private static boolean applied;

    private static native void nSetVoiceBitrate(int bitrate);
}
