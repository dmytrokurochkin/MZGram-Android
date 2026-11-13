/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The Material 3 music player: with its switch on (MZGramConfig.musicPlayer)
 * a music track opens MZGramPlayerSheet instead of Telegram's
 * AudioPlayerAlert, and the player bar above the chat
 * (FragmentContextView) can show MZGramPlayerBarView, and the main tabs
 * the floating MZGramPlayerMiniView. Voice messages and the switch off keep
 * Telegram's player.
 *
 * Port of exteraless Md3Player (GPL-3.0), from exteraGram.
 * Changed for MZGram: own switches.
 */

package org.telegram.ui.mzgram.player;

import android.content.Context;
import android.os.Bundle;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.BaseFragment;
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

    // The floating mini player above the main tabs
    public static boolean miniEnabled() {
        return MZGramConfig.musicPlayer && MZGramConfig.musicPlayerMini;
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

    // A tap on the mini player: the player grows out of its card
    public static void open(BaseFragment fragment, MZGramPlayerMiniView source) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }
        BottomSheet sheet = create(fragment.getParentActivity(), fragment.getResourceProvider());
        if (sheet instanceof MZGramPlayerSheet && source != null && source.canTransition()) {
            ((MZGramPlayerSheet) sheet).setTransitionSource(source);
        }
        fragment.showDialog(sheet);
    }

    // On a page of the main tabs the mini player shows the track, so the
    // player row at the top of the page (FragmentContextView) stays hidden
    public static boolean hidesContextPlayer(BaseFragment fragment, MessageObject messageObject) {
        if (!miniEnabled() || fragment == null || messageObject == null || !messageObject.isMusic()) {
            return false;
        }
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean("hasMainTabs", false);
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
