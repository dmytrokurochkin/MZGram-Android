package org.mzgram.core.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ProfileActivity.sendLogs;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.PrivacySettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mzgram.ai.ui.AiSettingsActivity;
import org.mzgram.icons.IconPacksActivity;
import org.mzgram.pillstack.pills.weather.WeatherSettingsActivity;
import org.mzgram.settings.MZGramCloudActivity;
import org.mzgram.settings.MZGramGlyphActivity;
import org.mzgram.pillstack.PillStackSettingsActivity;
import org.mzgram.plugins.ui.PluginsActivity;
import org.mzgram.settings.MZGramAppNavigationActivity;
import org.mzgram.settings.MZGramAppearanceActivity;
import org.mzgram.settings.MZGramArcMomentsActivity;
import org.mzgram.settings.MZGramChatsActivity;
import org.mzgram.settings.MZGramGeneralActivity;
import org.mzgram.settings.MZGramOtherActivity;
import org.mzgram.settings.MZGramSettingsActivity;
import org.mzgram.core.settings.BaseCoreSettingsActivity;
import org.mzgram.core.settings.BaseCoreXSettingsActivity;
import org.mzgram.core.settings.CoreAboutActivity;
import org.mzgram.core.settings.CoreEmojiSettingsActivity;
import org.mzgram.core.settings.CorePasscodeSettingsActivity;
import org.mzgram.core.settings.CoreSettingsActivity;
import org.mzgram.core.settings.CoreTranslatorSettingsActivity;

public class SettingsHelper {

    private static final String HOST_CORE = "coresettings";
    private static final String HOST_MZGRAM = "mzgram";

    private static final Map<String, String> SEARCH_TITLE_ALIASES = new HashMap<>();
    private static final Map<String, String> TRANSLATOR_ROWS = new HashMap<>();
    private static final Map<String, String> MZGRAM_ROWS = new HashMap<>();
    private static final Map<String, String> MOVED_ROWS = new HashMap<>();

    static {
        SEARCH_TITLE_ALIASES.put("MZGeneral:botPrivacyAccount", "MZBotPrivacyAccount");
        SEARCH_TITLE_ALIASES.put("MZArc:arcGhost", "GhostMode");
        SEARCH_TITLE_ALIASES.put("MZArc:arcDisableAll", "MZGeneralArcMomentsDisableAll");
        SEARCH_TITLE_ALIASES.put("MZArc:arcClearDatabase", "ClearMessageDatabase");
        SEARCH_TITLE_ALIASES.put("MZAppearance:appNavigation", "MZAppearanceNavigation");
        SEARCH_TITLE_ALIASES.put("MZChats:hideStories", "MZAppearanceStories");
        SEARCH_TITLE_ALIASES.put("MZChats:hideActionBarStatus", "MZAppearanceHideActionBarStatus");
        SEARCH_TITLE_ALIASES.put("MZChats:hideFloatingButton", "MZAppearanceHideFloatingButton");
        SEARCH_TITLE_ALIASES.put("MZChats:hideSearchBar", "MZAppearanceHideSearchBar");
        SEARCH_TITLE_ALIASES.put("MZChats:hideAllChats", "HideAllTab");
        SEARCH_TITLE_ALIASES.put("MZChats:hideShareButton", "HideShareButtonInChannel");
        SEARCH_TITLE_ALIASES.put("MZChats:hideAi", "MZAppearanceHideAi");
        SEARCH_TITLE_ALIASES.put("MZChats:hideAiEditor", "MZAppearanceHideAiEditor");
        SEARCH_TITLE_ALIASES.put("MZChats:hideAiSummary", "MZAppearanceHideAiSummary");
        SEARCH_TITLE_ALIASES.put("MZChats:hideAiIv", "MZAppearanceHideAiIv");
        SEARCH_TITLE_ALIASES.put("MZChats:hideSettingsSections", "MZAppearanceHideSettingsSections");
        SEARCH_TITLE_ALIASES.put("MZChats:disableGreeting", "MZChatsDisableGreetingSticker");
        SEARCH_TITLE_ALIASES.put("MZChats:hideKeyboardOnScroll", "HideKeyboardOnChatScroll");
        SEARCH_TITLE_ALIASES.put("MZChats:transcribeProvider", "PremiumPreviewVoiceToText");
        SEARCH_TITLE_ALIASES.put("MZGeneral:translator", "TranslatorSettings");
        SEARCH_TITLE_ALIASES.put("MZGeneral:translateInSheet", "MZChatsTranslateInSheet");
        SEARCH_TITLE_ALIASES.put("MZGeneral:glyph", "MZGlyphTitle");
        SEARCH_TITLE_ALIASES.put("MZGram:channel", "ProfileChannel");
        SEARCH_TITLE_ALIASES.put("MZGeneral:downloadSpeed", "MZGeneralSpeedHeader");
        SEARCH_TITLE_ALIASES.put("MZAppearance:md3Styles", "MZAppearanceMaterialDesign3");
        SEARCH_TITLE_ALIASES.put("MZAppearance:md3Loading", "MZAppearanceNewLoadingStyle");
        SEARCH_TITLE_ALIASES.put("MZAppearance:md3Slider", "MZAppearanceSliderStyle");
        SEARCH_TITLE_ALIASES.put("MZAppearance:md3Switch", "MZAppearanceSwitchStyle");
        SEARCH_TITLE_ALIASES.put("MZAppearance:md3NavBar", "MZAppearanceNewNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("MZAppearance:md3ListItems", "MZAppearanceM3ListItems");
        SEARCH_TITLE_ALIASES.put("MZAppearance:iosStyles", "MZAppearanceIosDesign");
        SEARCH_TITLE_ALIASES.put("MZAppearance:iosNavBar", "MZAppearanceIosNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("MZChats:doubleTapReaction", "DoubleTapSetting");
        SEARCH_TITLE_ALIASES.put("MZChats:quickTransition", "MZChatsQuickTransitions");
        SEARCH_TITLE_ALIASES.put("MZChats:inlineMathResult", "MZChatsInlineMath");
        SEARCH_TITLE_ALIASES.put("MZChats:searchHashtagChat", "SearchHashtagDefaultPageChat");
        SEARCH_TITLE_ALIASES.put("MZChats:searchHashtagChannel", "SearchHashtagDefaultPageChannel");
        SEARCH_TITLE_ALIASES.put("MZChats:replaceEdited", "MZChatsReplaceEditedWithIcon");
        SEARCH_TITLE_ALIASES.put("MZChats:videoMessagesCamera", "CameraInVideoMessages");
        SEARCH_TITLE_ALIASES.put("MZChats:voskModels", "VoskModelsShort");
        MOVED_ROWS.put("chats:translateInSheet", "general");
        MOVED_ROWS.put("other:unlimitedPinnedDialogs", "general");
        MOVED_ROWS.put("other:glyph", "general");
        moveRows("chats", "menus", "bottomButton", "adminShortcuts", "chatMenu", "messageMenu",
                "groupedMessageMenu", "textStyle", "TextStyle", "mediaViewerMenu", "actionBarButtons",
                "defaultDeleteMenu", "DefaultDeleteMenu");
        moveRows("chats", "media", "cameraType", "extendedSettings", "videoMessagesCamera",
                "rememberLastUsedCamera", "zoomSlider", "staticZoom", "alwaysSendInHD", "hdrPhotos",
                "disableInstantCamera", "DisableInstantCamera", "doubleTapSeekDuration", "preferOriginalQuality",
                "videoPlayerDecoder", "VideoPlayerDecoder", "swipeToPip", "unmuteWithVolumeButtons", "dontAutoplayWidgets",
                "showSmallGIF", "ShowSmallGIF", "dontAutoPlayNextVoice", "DontAutoPlayNextVoice",
                "disableProximityEvents", "DisableProximityEvents", "pauseOnMinimize", "transcribeProvider",
                "TranscribeProviderShort", "cloudflareCredentials", "CloudflareCredentials", "llmProviderGeminiKey",
                "LlmProviderGeminiKey", "transcribeProviderOpenAI", "TranscribeProviderOpenAI", "voskModels",
                "VoskModelsShort");
        moveRows("chats", "hiding", "disableTrending", "DisableTrending", "hideGroupSticker", "hideReactions",
                "disableGreeting", "hideSendAsPeer", "hideShareButton", "hideGiftButton", "hideSearchButton",
                "hideCameraTile", "premiumElements", "PremiumElements");
        moveRows("other", "media", "noiseSuppressAndVoiceEnhance", "NoiseSuppressAndVoiceEnhance",
                "enhancedVideoBitrate", "EnhancedVideoBitrate");
        moveRows("appearance", "hiding", "hideActionBarStatus", "hideStories", "HideStoriesFromHeader",
                "DisableStories", "hideFloatingButton", "hideSearchBar", "hideAllChats", "hideAi", "hideAiEditor",
                "hideAiSummary", "hideAiIv", "hideSettingsSections", "hidePremiumSection", "HidePremiumSection",
                "hideHelpSection", "HideHelpSection");
        TRANSLATOR_ROWS.put("translateButton", "showTranslate");
        TRANSLATOR_ROWS.put("translateChatButton", "TelegramUIAutoTranslate");
        TRANSLATOR_ROWS.put("translationProvider", "translationProvider");
        TRANSLATOR_ROWS.put("translateToLang", "TranslateTo");
        TRANSLATOR_ROWS.put("doNotTranslate", "DoNotTranslate");
        MZGRAM_ROWS.put("HidePremiumSection", "appearance");
        MZGRAM_ROWS.put("HideHelpSection", "appearance");
        MZGRAM_ROWS.put("HideStoriesFromHeader", "appearance");
        MZGRAM_ROWS.put("DisableStories", "appearance");
        MZGRAM_ROWS.put("MainTabsHideTitles", "navigation");
        MZGRAM_ROWS.put("MainTabsHideContacts", "navigation");
        MZGRAM_ROWS.put("MainTabsHideCallsSettings", "navigation");
        MZGRAM_ROWS.put("MainTabsHideProfile", "navigation");
        MZGRAM_ROWS.put("AlwaysShowDownloadIcon", "navigation");
        MZGRAM_ROWS.put("DisableSystemAccount", "privacy");
        MZGRAM_ROWS.put("PerformanceClass", "powersaving");
        MZGRAM_ROWS.put("VideoPlayerDecoder", "chats");
    }

    private static void moveRows(String from, String to, String... rows) {
        for (String row : rows) {
            MOVED_ROWS.put(from + ":" + row, to);
        }
    }

    private static final Set<String> MZGRAM_SCREENS = new HashSet<>(Arrays.asList(
            "settings", "general", "appearance", "chats", "plugins", "pillstack", "other",
            "arcmoments", "navigation", "menus", "media", "hiding"));

    public static boolean isDeepLink(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith(HOST_CORE + "/")) {
            return true;
        }
        if (!path.startsWith(HOST_MZGRAM + "/")) {
            return false;
        }
        return MZGRAM_SCREENS.contains(path.substring(HOST_MZGRAM.length() + 1));
    }

    public static String linkPathFor(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "mzgram":
                return HOST_MZGRAM + "/settings";
            case "mzgram_general":
                return HOST_MZGRAM + "/general";
            case "mzgram_appearance":
                return HOST_MZGRAM + "/appearance";
            case "mzgram_chats":
                return HOST_MZGRAM + "/chats";
            case "mzgram_menus":
                return HOST_MZGRAM + "/menus";
            case "mzgram_media":
                return HOST_MZGRAM + "/media";
            case "mzgram_hiding":
                return HOST_MZGRAM + "/hiding";
            case "mzgram_other":
                return HOST_MZGRAM + "/other";
            case "mzgram_arcmoments":
                return HOST_MZGRAM + "/arcmoments";
            case "pillstack":
                return HOST_MZGRAM + "/pillstack";
            case "mzgram_navigation":
                return HOST_MZGRAM + "/navigation";
            default:
                return HOST_CORE + "/" + key;
        }
    }

    public static void processDeepLink(Activity activity, Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2) {
            unknown.run();
            return;
        }
        final boolean mzgram = HOST_MZGRAM.equals(segments.get(0));
        if (!mzgram && !HOST_CORE.equals(segments.get(0))) {
            unknown.run();
            return;
        }
        if (mzgram && segments.size() < 2) {
            unknown.run();
            return;
        }
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        BaseFragment fragment;
        BaseCoreSettingsActivity core_fragment = null;
        BaseCoreXSettingsActivity corex_fragment = null;
        MZGramAppNavigationActivity navigation_fragment = null;
        String screen = mzgram ? segments.get(1) : mzgramScreen(segments.size() == 1 ? null : segments.get(1), row);
        if (screen == null && segments.size() == 2 && segments.get(1).startsWith("mzgram_")) {
            String base = segments.get(1).substring("mzgram_".length());
            if (row != null && MOVED_ROWS.containsKey(base + ":" + row)) {
                screen = base;
            }
        }
        if (screen != null && row != null) {
            String moved = MOVED_ROWS.get(screen + ":" + row);
            if (moved != null) {
                screen = moved;
            }
        }
        if (screen != null) {
            switch (screen) {
                case "settings":
                    fragment = core_fragment = new MZGramSettingsActivity();
                    break;
                case "general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = corex_fragment = new CoreTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = core_fragment = new MZGramGeneralActivity();
                    }
                    break;
                case "appearance":
                    fragment = core_fragment = "useSystemEmoji".equals(row)
                            ? new CoreEmojiSettingsActivity() : new MZGramAppearanceActivity();
                    break;
                case "chats":
                    fragment = core_fragment = new MZGramChatsActivity();
                    break;
                case "menus":
                    fragment = core_fragment = new MZGramChatsActivity(MZGramChatsActivity.SCREEN_MENUS);
                    break;
                case "media":
                    fragment = core_fragment = new MZGramChatsActivity(MZGramChatsActivity.SCREEN_MEDIA);
                    break;
                case "hiding":
                    fragment = core_fragment = new MZGramChatsActivity(MZGramChatsActivity.SCREEN_HIDING);
                    break;
                case "other":
                    fragment = core_fragment = new MZGramOtherActivity();
                    break;
                case "arcmoments":
                    fragment = core_fragment = new MZGramArcMomentsActivity();
                    break;
                case "pillstack":
                    fragment = core_fragment = new PillStackSettingsActivity();
                    break;
                case "plugins":
                    fragment = new PluginsActivity();
                    break;
                case "navigation":
                    fragment = navigation_fragment = new MZGramAppNavigationActivity();
                    break;
                case "privacy":
                    fragment = new PrivacySettingsActivity();
                    break;
                case "powersaving":
                    fragment = new LiteModeSettingsActivity();
                    break;
                default:
                    unknown.run();
                    return;
            }
        } else if (PasscodeHelper.getSettingsKey().equals(segments.get(1))) {
            fragment = core_fragment = new CorePasscodeSettingsActivity();
        } else {
            switch (segments.get(1)) {
                case "about":
                    // MZGram: the About screen of another app is not shown.
                    fragment = new CoreSettingsActivity();
                    break;
                case "emoji":
                    fragment = core_fragment = new CoreEmojiSettingsActivity();
                    break;
                case "translator":
                case "translate":
                case "t":
                    fragment = corex_fragment = new CoreTranslatorSettingsActivity();
                    break;
                case "mzgram":
                    fragment = core_fragment = new MZGramSettingsActivity();
                    break;
                case "mzgram_general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = corex_fragment = new CoreTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = core_fragment = new MZGramGeneralActivity();
                    }
                    break;
                case "mzgram_appearance":
                    fragment = core_fragment = "useSystemEmoji".equals(row)
                            ? new CoreEmojiSettingsActivity() : new MZGramAppearanceActivity();
                    break;
                case "mzgram_chats":
                    fragment = core_fragment = new MZGramChatsActivity();
                    break;
                case "mzgram_other":
                    fragment = core_fragment = new MZGramOtherActivity();
                    break;
                case "mzgram_arcmoments":
                    fragment = core_fragment = new MZGramArcMomentsActivity();
                    break;
                case "pillstack":
                    fragment = core_fragment = new PillStackSettingsActivity();
                    break;
                case "pillstackweather":
                    fragment = core_fragment = new WeatherSettingsActivity();
                    break;
                case "iconpacks":
                    fragment = core_fragment = new IconPacksActivity();
                    break;
                case "mzgram_ai":
                    fragment = core_fragment = new AiSettingsActivity();
                    break;
                case "mzgram_cloud":
                    fragment = core_fragment = new MZGramCloudActivity();
                    break;
                case "mzgram_glyph":
                    fragment = core_fragment = new MZGramGlyphActivity();
                    break;
                case "send_logs":
                    sendLogs(activity, false);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var value = uri.getQueryParameter("v");
        if (TextUtils.isEmpty(value)) {
            value = uri.getQueryParameter("value");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            if (core_fragment != null) {
                BaseCoreSettingsActivity finalCore_fragment = core_fragment;
                AndroidUtilities.runOnUIThread(() -> finalCore_fragment.scrollToRow(rowFinal, unknown));
            } else if (navigation_fragment != null) {
                MZGramAppNavigationActivity finalNavigation_fragment = navigation_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNavigation_fragment.scrollToRow(rowFinal, unknown));
            } else if (corex_fragment != null) {
                BaseCoreXSettingsActivity finalCoreX_fragment = corex_fragment;
                if (!TextUtils.isEmpty(value)) {
                    String finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> finalCoreX_fragment.importToRow(rowFinal, finalValue, unknown));
                } else {
                    AndroidUtilities.runOnUIThread(() -> finalCoreX_fragment.scrollToRow(rowFinal, unknown));
                }
            }
        }
    }

    private static String mzgramScreen(String segment, String row) {
        if (segment == null) {
            return "settings";
        }
        String fallback;
        switch (segment) {
            case "general":
            case "g":
                fallback = "general";
                break;
            case "chat":
            case "chats":
            case "c":
                fallback = "chats";
                break;
            case "experimental":
            case "e":
                fallback = "other";
                break;
            default:
                return null;
        }
        String moved = row == null ? null : MZGRAM_ROWS.get(row);
        return moved != null ? moved : fallback;
    }

    public static String rowTitle(BaseCoreSettingsActivity fragment, String key) {
        String title = getString(key);
        if (title != null && !title.isEmpty() && !title.equals(key)) {
            return title;
        }
        if (key.isEmpty()) {
            return null;
        }
        String capitalized = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        String prefix = fragment.getSearchPrefix();
        if (prefix != null) {
            String alias = SEARCH_TITLE_ALIASES.get(prefix + ":" + key);
            if (alias != null) {
                title = resolved(alias);
                if (title != null) {
                    return title;
                }
            }
            title = resolved(prefix + capitalized);
            if (title != null) {
                return title;
            }
        }
        return resolved(capitalized);
    }

    /** Titles that are templates, with the word their screen puts in, so summaries and search read like the row. */
    private static final Map<String, String> TITLE_ARGS = new HashMap<>();

    static {
        TITLE_ARGS.put("MZChatsReplaceEditedWithIcon", "EditedMessage");
        TITLE_ARGS.put("MZChatsHideShareButton", "ShareFile");
        TITLE_ARGS.put("MZChatsHideMoneyButton", "WalletAttachMoney");
        TITLE_ARGS.put("MZAppearanceHideAllChats", "FilterAllChats");
    }

    private static String resolved(String name) {
        if (LocaleController.getStringResId(name) == 0) {
            return null;
        }
        String value = getString(name);
        if (value == null || value.isEmpty()) {
            return null;
        }
        String arg = TITLE_ARGS.get(name);
        if (arg != null && value.contains("%1$s")) {
            value = value.replace("%1$s", getString(arg));
        }
        return value;
    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }

    public static ArrayList<SettingsSearchResult> onCreateSearchArray(Callback callback) {
        ArrayList<SettingsSearchResult> items = new ArrayList<>();
        ArrayList<BaseCoreXSettingsActivity> fragments = new ArrayList<>();
        fragments.add(new CoreTranslatorSettingsActivity());

        ArrayList<BaseCoreSettingsActivity> mzgramFragments = new ArrayList<>();
        mzgramFragments.add(new MZGramSettingsActivity());
        mzgramFragments.add(new MZGramGeneralActivity());
        mzgramFragments.add(new MZGramAppearanceActivity());
        mzgramFragments.add(new MZGramChatsActivity());
        mzgramFragments.add(new MZGramChatsActivity(MZGramChatsActivity.SCREEN_MENUS));
        mzgramFragments.add(new MZGramChatsActivity(MZGramChatsActivity.SCREEN_MEDIA));
        mzgramFragments.add(new MZGramChatsActivity(MZGramChatsActivity.SCREEN_HIDING));
        mzgramFragments.add(new MZGramOtherActivity());
        mzgramFragments.add(new MZGramArcMomentsActivity());
        mzgramFragments.add(new PillStackSettingsActivity());
        mzgramFragments.add(new AiSettingsActivity());

        String e_title = getString(R.string.MZGram);
        for (BaseCoreSettingsActivity fragment : mzgramFragments) {
            try {
                fragment.buildRowsForSearch();
            } catch (Exception e) {
                continue;
            }
            int uid = fragment.getSearchGuid();
            int drawable = fragment.getSearchIcon();
            String f_title = fragment.getSearchTitle();
            for (Map.Entry<Integer, String> entry : fragment.getSearchRows().entrySet()) {
                String key = entry.getValue();
                if (key == null || key.endsWith("Header") || key.equals(String.valueOf(entry.getKey()))) {
                    continue;
                }
                String title = rowTitle(fragment, key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                items.add(new SettingsSearchResult(
                        uid + entry.getKey(), title, e_title, f_title, drawable, open));
            }
        }

        items.add(new SettingsSearchResult(29000, getString(R.string.MZAppearanceUseSystemEmoji), e_title,
                getString(R.string.EmojiSets), R.drawable.msg_emoji_smiles, () -> {
                    CoreEmojiSettingsActivity emoji = new CoreEmojiSettingsActivity();
                    callback.presentFragment(emoji);
                    AndroidUtilities.runOnUIThread(() -> emoji.scrollToRow("useSystemEmoji", null));
                }));

        String n_title = getString(R.string.Language);
        for (BaseCoreXSettingsActivity fragment: fragments) {
            int uid = fragment.getBaseGuid();
            int drawable = fragment.getDrawable();
            String f_title = fragment.getTitle();
            for (Map.Entry<Integer, String> entry : fragment.getRowMapReverse().entrySet()) {
                Integer i = entry.getKey();
                String key = entry.getValue();
                if (key.equals(String.valueOf(i))) {
                    continue;
                }
                int guid = uid + i;
                String title = getString(key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                SettingsSearchResult result = new SettingsSearchResult(
                        guid, title, n_title, f_title, drawable, open
                );
                items.add(result);
            }
        }
        return items;
    }
}
