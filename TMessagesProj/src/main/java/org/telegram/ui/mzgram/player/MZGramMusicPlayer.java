/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The Material 3 music player: with its switch on (MZGramConfig.musicPlayer)
 * a music track opens MZGramPlayerSheet instead of Telegram's
 * AudioPlayerAlert, and the player bar above the chat
 * (FragmentContextView) can show MZGramPlayerBarView. Voice messages and
 * the switch off keep Telegram's player.
 *
 * Port of exteraless Md3Player (GPL-3.0), from exteraGram.
 * Changed for MZGram: own switches, no floating mini player.
 */

package org.telegram.ui.mzgram.player;

import android.content.Context;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;

public final class MZGramMusicPlayer {

    private MZGramMusicPlayer() {
    }

    public static boolean enabled() {
        return MZGramConfig.musicPlayer;
    }

    public static boolean barEnabled() {
        return MZGramConfig.musicPlayer && MZGramConfig.musicPlayerBar;
    }

    private static boolean handles(MessageObject messageObject) {
        return enabled() && messageObject != null && messageObject.isMusic();
    }

    // Telegram's player when the switch is off or a voice message plays
    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        if (handles(MediaController.getInstance().getPlayingMessageObject())) {
            if (MZGramPlayerSheet.instance != null) {
                MZGramPlayerSheet.instance.dismissImmediately();
            }
            return new MZGramPlayerSheet(context, resourcesProvider);
        }
        return new AudioPlayerAlert(context, resourcesProvider);
    }

    public static void dismissSheets() {
        if (MZGramPlayerSheet.instance != null) {
            MZGramPlayerSheet.instance.dismissImmediately();
        }
    }

    public static void onModesChanged() {
        MZGramPlayerSheet.onModesChanged();
    }
}
