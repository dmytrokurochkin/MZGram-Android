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
}
