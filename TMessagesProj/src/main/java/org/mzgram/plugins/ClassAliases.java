package org.mzgram.plugins;

public final class ClassAliases {

    public static final String ROOT = "org.mzgram.sdk";

    public static final String MEDIA_ROOT = "com.google.android.exoplayer2";

    private static final String[] ROOTS = {ROOT, MEDIA_ROOT};

    private static final String[][] EXACT = {
            {"org.mzgram.sdk.utils.chats.ChatUtils", "org.mzgram.sdk.utils.chats.ChatUtils"},
            {"org.mzgram.sdk.utils.ChatUtils", "org.mzgram.sdk.utils.chats.ChatUtils"},
            {"org.mzgram.sdk.utils.text.LocaleUtils", "org.mzgram.sdk.utils.text.LocaleUtils"},
            {"org.mzgram.sdk.utils.LocaleUtils", "org.mzgram.sdk.utils.text.LocaleUtils"},
            {"org.mzgram.sdk.utils.AppUtils", "org.mzgram.sdk.utils.AppUtils"},
            {"org.mzgram.sdk.R", "org.telegram.messenger.R"},
            {"org.mzgram.sdk.utils.system.VibratorUtils", "org.mzgram.sdk.utils.system.VibratorUtils"},
            {"org.mzgram.sdk.ai.AiConfig", "org.mzgram.ai.AiConfig"},
            {"org.mzgram.sdk.ai.AiController", "org.mzgram.sdk.ai.AiController"},
            {"org.mzgram.sdk.ai.ui.ResponseAlert", "org.mzgram.sdk.ai.ui.ResponseAlert"},
            {"org.mzgram.sdk.ai.ui.GenerateFromMessageBottomSheet", "org.mzgram.sdk.ai.ui.GenerateFromMessageBottomSheet"},
            {"org.mzgram.sdk.plugins.ui.components.InstallPluginBottomSheet", "org.mzgram.sdk.plugins.ui.components.InstallPluginBottomSheet"},
            {"org.mzgram.sdk.utils.system.SystemUtils", "org.mzgram.sdk.utils.system.SystemUtils"},
            {"org.mzgram.sdk.utils.SystemUtils", "org.mzgram.sdk.utils.system.SystemUtils"},
            {"org.mzgram.sdk.preferences.MainPreferencesActivity", "org.mzgram.settings.MZGramSettingsActivity"},
            {"org.mzgram.sdk.preferences.GeneralPreferencesActivity", "org.mzgram.settings.MZGramGeneralActivity"},
            {"org.mzgram.sdk.preferences.AppearancePreferencesActivity", "org.mzgram.settings.MZGramAppearanceActivity"},
            {"org.mzgram.sdk.preferences.ChatsPreferencesActivity", "org.mzgram.settings.MZGramChatsActivity"},
            {"org.mzgram.sdk.preferences.OtherPreferencesActivity", "org.mzgram.settings.MZGramOtherActivity"},
            {"org.mzgram.sdk.preferences.AppNavigationPreferencesActivity", "org.mzgram.settings.MZGramAppNavigationActivity"},
            {"org.mzgram.sdk.preferences.BasePreferencesActivity", "org.mzgram.sdk.preferences.BasePreferencesActivity"},
            {"org.mzgram.sdk.preferences.components.AltSeekbar", "org.mzgram.appearance.AltSeekbar"},
            {"org.mzgram.sdk.utils.chats.MainMenuHelper", "org.mzgram.drawer.MainMenuHelper"},
            {"org.mzgram.sdk.icons.ui.IconPacksActivity", "org.mzgram.icons.IconPacksActivity"},
            {"org.mzgram.sdk.pillstack.ui.pills.crypto.utils.ColoredBackground", "org.mzgram.pillstack.pills.ColoredBackground"},
            {"org.mzgram.sdk.pillstack.ui.pills.weather.WeatherPill", "org.mzgram.pillstack.pills.WeatherPill"},
            {"org.mzgram.sdk.pillstack.ui.PillStackPreferencesActivity", "org.mzgram.pillstack.PillStackSettingsActivity"},
            {"org.mzgram.sdk.pillstack.ui.PillStackLayout", "org.mzgram.pillstack.PillStackView"},
            {"org.mzgram.sdk.pillstack.ui.pills.weather.WeatherPreferencesActivity", "org.mzgram.pillstack.pills.weather.WeatherSettingsActivity"},
            {"org.mzgram.sdk.preferences.appearance.AppearancePreferencesActivity", "org.mzgram.settings.MZGramAppearanceActivity"},
            {"org.mzgram.sdk.nowplaying.ui.components.NowPlayingCard", "org.mzgram.components.ProfileMusicCard"},
            {"org.mzgram.sdk.nowplaying.NowPlayingController", "org.mzgram.nowplaying.NowPlayingController"},
            {"org.mzgram.sdk.proxy.ProxyController", "org.mzgram.proxy.ProxyController"},
            {"org.mzgram.sdk.utils.ui.UIUtil", "org.mzgram.utils.UIUtil"},
            {"org.mzgram.sdk.utils.ui.MainTabsUiHelper", "org.mzgram.appearance.MainTabsUiHelper"},
            {"com.google.android.exoplayer2.util.Consumer", "androidx.media3.common.util.Consumer"},
            {"com.google.android.exoplayer2.video.VideoSize", "androidx.media3.common.VideoSize"},
            {"com.google.android.exoplayer2.PlaybackException", "androidx.media3.common.PlaybackException"},
            {"com.google.android.exoplayer2.PlaybackParameters", "androidx.media3.common.PlaybackParameters"},
            {"com.google.android.exoplayer2.C", "androidx.media3.common.C"},
            {"org.telegram.ui.Gifts.ProfileGiftsContainer$SelectGiftsBottomSheet", "org.telegram.ui.Gifts.SelectGiftsBottomSheet"},
            {"org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode", "org.telegram.utils.glass.source.GlassSourceRenderNode"},
            {"org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor", "org.telegram.utils.glass.source.GlassSourceColor"},
            {"org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped", "org.telegram.utils.glass.source.GlassSourceWrapped"},
            {"org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap", "org.telegram.utils.glass.source.GlassSourceBitmap"},
            {"org.telegram.ui.Components.blur3.source.BlurredBackgroundSource", "org.telegram.utils.glass.source.GlassSource"},
    };

    private static final String[][] PREFIXES = {
            {"org.mzgram.sdk.ai.data.", "org.mzgram.ai.data."},
            {"org.mzgram.sdk.ai.network.", "org.mzgram.ai.network."},
            {"org.mzgram.sdk.pillstack.core.", "org.mzgram.pillstack."},
            {"org.mzgram.sdk.pillstack.ui.pills.", "org.mzgram.pillstack.pills."},
            {"org.mzgram.sdk.pillstack.ui.", "org.mzgram.pillstack."},
            {"org.mzgram.sdk.preferences.", "org.mzgram.settings."},
            {"org.mzgram.sdk.plugins.", "org.mzgram.plugins."},
            {"org.mzgram.sdk.icons.", "org.mzgram.icons."},
            {"org.mzgram.sdk.camera.", "org.mzgram.camera."},
            {"org.mzgram.sdk.backup.", "org.mzgram.backup."},
            {"org.mzgram.sdk.feed.", "org.mzgram.feed."},
            {"org.mzgram.sdk.drawer.", "org.mzgram.drawer."},
            {"org.mzgram.sdk.components.", "org.mzgram.components."},
            {"org.mzgram.sdk.utils.", "org.mzgram.utils."},
    };

    private ClassAliases() {
    }

    public static String resolve(String name) {
        if (name == null) {
            return null;
        }
        String exact = lookup(name);
        if (exact != null) {
            return exact;
        }
        if (!underRoot(name)) {
            return name;
        }
        int dollar = name.indexOf('$');
        String outer = dollar < 0 ? name : name.substring(0, dollar);
        String nested = dollar < 0 ? "" : name.substring(dollar);
        exact = lookup(outer);
        if (exact != null) {
            return exact + nested;
        }
        for (String[] pair : PREFIXES) {
            if (outer.startsWith(pair[0])) {
                return pair[1] + outer.substring(pair[0].length()) + nested;
            }
        }
        return name;
    }

    private static boolean underRoot(String name) {
        for (String root : ROOTS) {
            if (name.startsWith(root + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String lookup(String name) {
        for (String[] pair : EXACT) {
            if (pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }
}
