/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings of MZGram features. Flags keep the names Nekogram gives them in
 * NekoConfig, so a port from Nekogram only swaps the class name.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public class MZGramConfig {

    private static final String PREFERENCES_NAME = "mzgram_config";

    private static final Object sync = new Object();
    private static boolean configLoaded;

    // Placeholder that proves the settings survive a restart. Removed once the
    // first real feature flag lands.
    public static boolean testToggle = false;

    public static boolean disableNumberRounding = false;
    public static boolean formatTimeWithSeconds = false;
    public static boolean askBeforeCall = false;
    public static boolean hideStories = false;
    public static boolean disableGreetingSticker = false;
    public static boolean hideChannelBottomButtons = false;
    public static boolean openArchiveOnPull = false;
    public static boolean disableInstantCamera = false;
    public static boolean preferOriginalQuality = false;
    public static boolean autoPauseVideo = false;
    public static boolean showCopyPhoto = false;
    public static boolean showDeleteDownloadedFile = false;
    public static boolean showAddToSavedMessages = false;
    public static boolean showSetReminder = false;
    public static boolean showRepeat = false;
    public static boolean showOpenIn = false;
    public static boolean showMessageDetails = false;
    public static boolean showQrCode = false;
    public static boolean showNoQuoteForward = false;
    // On by default: upstream always has this animation, the switch only turns it off.
    public static boolean predictiveBackAnimation = true;
    // On by default: upstream always has this animation, the switch only turns it off.
    public static boolean gooeyAvatarAnimation = true;

    static {
        loadConfig(false);
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static void loadConfig(boolean force) {
        synchronized (sync) {
            if (configLoaded && !force) {
                return;
            }
            final SharedPreferences preferences = preferences();
            testToggle = preferences.getBoolean("testToggle", false);
            disableNumberRounding = preferences.getBoolean("disableNumberRounding", false);
            formatTimeWithSeconds = preferences.getBoolean("formatTimeWithSeconds", false);
            askBeforeCall = preferences.getBoolean("askBeforeCall", false);
            hideStories = preferences.getBoolean("hideStories", false);
            disableGreetingSticker = preferences.getBoolean("disableGreetingSticker", false);
            hideChannelBottomButtons = preferences.getBoolean("hideChannelBottomButtons", false);
            openArchiveOnPull = preferences.getBoolean("openArchiveOnPull", false);
            disableInstantCamera = preferences.getBoolean("disableInstantCamera", false);
            preferOriginalQuality = preferences.getBoolean("preferOriginalQuality", false);
            autoPauseVideo = preferences.getBoolean("autoPauseVideo", false);
            showCopyPhoto = preferences.getBoolean("showCopyPhoto", false);
            showDeleteDownloadedFile = preferences.getBoolean("showDeleteDownloadedFile", false);
            showAddToSavedMessages = preferences.getBoolean("showAddToSavedMessages", false);
            showSetReminder = preferences.getBoolean("showSetReminder", false);
            showRepeat = preferences.getBoolean("showRepeat", false);
            showOpenIn = preferences.getBoolean("showOpenIn", false);
            showMessageDetails = preferences.getBoolean("showMessageDetails", false);
            showQrCode = preferences.getBoolean("showQrCode", false);
            showNoQuoteForward = preferences.getBoolean("showNoQuoteForward", false);
            predictiveBackAnimation = preferences.getBoolean("predictiveBackAnimation", true);
            gooeyAvatarAnimation = preferences.getBoolean("gooeyAvatarAnimation", true);
            configLoaded = true;
        }
    }

    private static void putBoolean(String key, boolean value) {
        preferences().edit().putBoolean(key, value).apply();
    }

    public static void toggleTestToggle() {
        testToggle = !testToggle;
        putBoolean("testToggle", testToggle);
    }

    public static void toggleDisableNumberRounding() {
        disableNumberRounding = !disableNumberRounding;
        putBoolean("disableNumberRounding", disableNumberRounding);
    }

    public static void toggleFormatTimeWithSeconds() {
        formatTimeWithSeconds = !formatTimeWithSeconds;
        putBoolean("formatTimeWithSeconds", formatTimeWithSeconds);
    }

    public static void toggleAskBeforeCall() {
        askBeforeCall = !askBeforeCall;
        putBoolean("askBeforeCall", askBeforeCall);
    }

    public static void toggleHideStories() {
        hideStories = !hideStories;
        putBoolean("hideStories", hideStories);
    }

    public static void toggleDisableGreetingSticker() {
        disableGreetingSticker = !disableGreetingSticker;
        putBoolean("disableGreetingSticker", disableGreetingSticker);
    }

    public static void toggleHideChannelBottomButtons() {
        hideChannelBottomButtons = !hideChannelBottomButtons;
        putBoolean("hideChannelBottomButtons", hideChannelBottomButtons);
    }

    public static void toggleOpenArchiveOnPull() {
        openArchiveOnPull = !openArchiveOnPull;
        putBoolean("openArchiveOnPull", openArchiveOnPull);
    }

    public static void toggleDisableInstantCamera() {
        disableInstantCamera = !disableInstantCamera;
        putBoolean("disableInstantCamera", disableInstantCamera);
    }

    public static void togglePreferOriginalQuality() {
        preferOriginalQuality = !preferOriginalQuality;
        putBoolean("preferOriginalQuality", preferOriginalQuality);
    }

    public static void toggleAutoPauseVideo() {
        autoPauseVideo = !autoPauseVideo;
        putBoolean("autoPauseVideo", autoPauseVideo);
    }

    public static void toggleShowCopyPhoto() {
        showCopyPhoto = !showCopyPhoto;
        putBoolean("showCopyPhoto", showCopyPhoto);
    }

    public static void toggleShowDeleteDownloadedFile() {
        showDeleteDownloadedFile = !showDeleteDownloadedFile;
        putBoolean("showDeleteDownloadedFile", showDeleteDownloadedFile);
    }

    public static void toggleShowAddToSavedMessages() {
        showAddToSavedMessages = !showAddToSavedMessages;
        putBoolean("showAddToSavedMessages", showAddToSavedMessages);
    }

    public static void toggleShowSetReminder() {
        showSetReminder = !showSetReminder;
        putBoolean("showSetReminder", showSetReminder);
    }

    public static void toggleShowRepeat() {
        showRepeat = !showRepeat;
        putBoolean("showRepeat", showRepeat);
    }

    public static void toggleShowOpenIn() {
        showOpenIn = !showOpenIn;
        putBoolean("showOpenIn", showOpenIn);
    }

    public static void toggleShowMessageDetails() {
        showMessageDetails = !showMessageDetails;
        putBoolean("showMessageDetails", showMessageDetails);
    }

    public static void toggleShowQrCode() {
        showQrCode = !showQrCode;
        putBoolean("showQrCode", showQrCode);
    }

    public static void toggleShowNoQuoteForward() {
        showNoQuoteForward = !showNoQuoteForward;
        putBoolean("showNoQuoteForward", showNoQuoteForward);
    }

    public static void togglePredictiveBackAnimation() {
        predictiveBackAnimation = !predictiveBackAnimation;
        putBoolean("predictiveBackAnimation", predictiveBackAnimation);
    }

    public static void toggleGooeyAvatarAnimation() {
        gooeyAvatarAnimation = !gooeyAvatarAnimation;
        putBoolean("gooeyAvatarAnimation", gooeyAvatarAnimation);
    }
}
