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
}
