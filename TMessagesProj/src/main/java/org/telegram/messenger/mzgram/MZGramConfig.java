/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings of MZGram features, stored in SharedPreferences.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Set;

public class MZGramConfig {

    private static final String PREFERENCES_NAME = "mzgram_config";

    private static final Object sync = new Object();
    private static boolean configLoaded;

    public static boolean disableNumberRounding = false;
    public static boolean formatTimeWithSeconds = false;
    public static boolean askBeforeCall = false;
    public static boolean hideStories = false;
    // With hideStories: no background loading of the stories list and story previews
    public static boolean storiesNoBackgroundLoad = false;
    public static boolean disableGreetingSticker = false;
    public static boolean hideChannelBottomButtons = false;
    public static boolean openArchiveOnPull = false;
    public static boolean disableInstantCamera = false;
    public static boolean hideCameraTile = false;
    // The banner about a newer MZGram release on GitHub (MZGramUpdateChecker)
    public static boolean checkUpdates = true;
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
    // "Ask AI" in the message menu (MZGramAiActivity)
    public static boolean showAskAi = true;
    // The AI site "Ask AI" starts with (MZGramAiRules)
    public static String aiProvider = MZGramAiRules.DEFAULT_PROVIDER;
    // The warning that the text goes to a service outside Telegram was
    // shown and accepted
    public static boolean aiWarned = false;
    // The AI sites whose button is in the chat list menu (MZGramAiRules
    // keys); none by default
    public static Set<String> aiMenuButtons = new java.util.HashSet<>();
    // The rows of Telegram's Settings that are hidden (MZGramSettingsRows
    // keys); none by default
    public static Set<String> hiddenSettingsRows = new java.util.HashSet<>();
    // Forward, save, copy and screenshot in chats with "Restrict saving
    // content" (MZGramProtectedContent).
    public static boolean saveProtectedContent = false;
    // The Delete box of messages comes with "Delete for everyone" (or "Also
    // delete for ...") already ticked; Telegram leaves it unticked.
    public static boolean deleteForAllByDefault = false;
    // On by default: upstream always has this animation, the switch only turns it off.
    public static boolean predictiveBackAnimation = true;
    // On by default: upstream always has this animation, the switch only turns it off.
    public static boolean gooeyAvatarAnimation = true;
    // Avatar shape (MZGramAvatarShape): MAX is the circle of Telegram, 0 a square
    public static int avatarCorners = MZGramAvatarShape.MAX;
    // Outline of the glass surfaces: 0 glare as in Telegram, 1 solid, 2 hidden (MZGramGlassOutline)
    public static int glassOutline = 0;
    // Folder tabs: 0 names only as in Telegram, 1 icons and names, 2 icons only (MZGramFolderIcons)
    public static int folderTabIcons = 0;
    // App font: 0 as in Telegram, 1 the font of the phone, 2 a font file (MZGramFonts)
    public static int appFont = 0;
    public static String appFontName = "";
    // Voice messages: 0 Telegram's bitrate, 1 64 kbit/s, 2 128 kbit/s (MZGramVoiceQuality)
    public static int voiceQuality = 0;
    // Crop photos out of the source at full resolution (MZGramPhotoCrop)
    public static boolean losslessPhotoCrop = false;
    // Write uncaught exceptions to a local file and show it on the next launch (MZGramCrashLog)
    public static boolean crashReports = false;
    public static boolean hideBottomNavigationBar = false;
    // The signed-in accounts at the end of the chat list menu (MZGramAccountsMenu)
    public static boolean accountsInMenu = true;
    // Set once the accounts were put in a new order (MZGramAccountOrderActivity)
    public static boolean accountOrderCustomised = false;
    // Holding Back in a chat shows the chats opened last (MZGramRecentChats)
    public static boolean recentChatsOnBack = false;
    // Up to MZGramLocalPins.MAX_EXTENDED pinned chats in the main list and the archive
    public static boolean extendedPins = false;
    // The pill bar of the chat list's search bar (MZGramPills, ui/mzgram/pills)
    public static boolean pillStack = false;
    // Icon packs and the built-in icon sets (MZGramIconPacks)
    public static boolean iconPacks = false;
    // The Monet themes in the theme list (MZGramMonet, Android 12+) and their style
    public static boolean monetThemes = false;
    public static int monetStyle = 0;
    // The round video camera on CameraX (MZGramCameraX), its start on the
    // ultra-wide lens and its zoom slider (MZGramRoundZoomSlider)
    public static boolean cameraX = false;
    public static boolean cameraXWideLens = false;
    public static boolean cameraXZoomSlider = true;
    // Native round video recorder pipeline (Camera2, RoundVideoSession)
    public static boolean roundVideoCamera2 = false;
    public static boolean roundVideoHighFps = false;
    public static int roundVideoBitrate = 1200000;
    // The Material 3 music player (MZGramMusicPlayer) and its bar above the chat
    public static boolean musicPlayer = false;
    public static boolean musicPlayerBar = true;
    // MZGramProxyAutoDisable: mask of VPN, MOBILE_DATA, WIFI; 0 = the proxy is never turned off by itself
    public static int proxyDisableConditions = 0;
    // MZGramPluginEngine: plugins that are on run only while this is on; outside the settings backup
    public static boolean pluginsRun = false;
    // MZGramLanguage: "" follows Telegram's language, MZGramLanguage.SYSTEM
    // the phone's, otherwise one of its codes
    public static String language = "";
    // Folder tabs above the bottom bars instead of under the search field.
    public static boolean folderTabsAtBottom = false;
    // Routes a released
    // record gesture into the existing "recorded, not yet sent" preview
    // panel instead of sending immediately, for both voice and round video.
    public static boolean confirmAVMessage = false;
    // MZGram's own code: long-press on a chat list row's avatar normally
    // opens the native Chat Preview peek. When the last message is a photo
    // or video, this shows that media instead; otherwise Chat Preview opens
    // as usual.
    public static boolean mediaPreviewOnLongPress = false;
    public static boolean ghostMode = false;
    // Ghost mode: delay the actual network send of an outgoing message by a
    // few seconds, so composing and sending does not create the burst of
    // activity that can make you look online. Off by default; the settings
    // screen warns this is not recommended on unreliable networks (a delayed
    // send can still be in flight if the app is killed or the network drops).
    public static boolean ghostAutoDelaySend = false;
    // Ghost mode: send outgoing messages silently (no sound/notification for
    // the recipient) for as long as ghost mode is on, without touching the
    // per-message "send without sound" option the user can already pick by
    // hand from the send button's long-press menu.
    public static boolean ghostSilentSend = false;
    // Ghost mode: before opening the story viewer for the first time (not on
    // swiping between already-open stories), offer to turn ghost mode on so
    // viewing does not mark the story as seen for the other side.
    public static boolean offerGhostModeBeforeStories = false;
    // Ghost mode: a chat's menu sets ghost mode for that chat only
    // (MZGramGhostMode exclusions); off ignores the saved exclusions.
    public static boolean ghostModeExclusions = false;

    // Settings > MZGram > Privacy (MZGramPrivacy); all off by default.
    // t.me and tg:// links ask before they open.
    public static boolean confirmInternalLinks = false;
    // The viewed mark of trending sticker packs is not sent to the server.
    public static boolean disableReadStickers = false;
    // A typed link is not sent to the server for a preview.
    public static boolean disableLinkPreviews = false;
    // Unsent text stays on this device: drafts are not sent to the server.
    public static boolean disableCloudDrafts = false;
    // Voice messages and round videos do not play from the lock screen.
    public static boolean lockScreenVoiceProtection = false;
    // The owner's own last seen, as others see it, noted on this device
    // (MZGramOwnLastSeen).
    public static boolean ownLastSeen = false;
    // Who read own messages in groups is kept for message Details
    // (MZGramGroupReads).
    public static boolean keepGroupReads = false;
    // A bot start link with a start code asks first (MZGramBotStartGuard).
    public static boolean botStartConfirm = false;
    // Files come from the main datacenter instead of a CDN, which only
    // knows the permanent auth key (MZGramPrivacy.refuseCdnRedirect).
    public static boolean reduceNetworkTracking = false;
    // Every account can have its own passcode for the app lock, and an
    // account with one can be hidden (MZGramPasscodeAccounts).
    public static boolean passcodeAccounts = false;

    // Settings > MZGram > Interface > Show ID (MZGramPeerId): hidden by
    // default, as in Telegram API or as in Bot API.
    public static int peerIdMode = MZGramPeerId.HIDDEN;

    // Notifications and Sounds > Reliable delivery (MZGramReliableService):
    // a permanent notification keeps the connection; off by default.
    public static boolean reliableDelivery = false;

    // Local message history archive (Settings > MZGram > Archive). Other
    // people's deleted and edited messages in every private chat, group,
    // channel and secret chat; each part has its own switch, all on by
    // default.
    public static boolean saveDeletedMessages = true;
    public static boolean saveEditHistory = true;
    // Files of archived messages, copied to Downloads/MZGram/Saved Attachments.
    public static boolean saveArchiveMedia = true;
    // Bold, italic, links and the like of archived messages.
    public static boolean saveFormatting = true;
    public static boolean saveReactions = true;
    // Keep messages: from users, from bots, in groups, in channels (see
    // MZGramKeepFrom). "From bots" keeps the key of the earlier "Save in
    // chats with bots", so its value carries over.
    public static boolean saveFromUsers = true;
    public static boolean saveForBots = true;
    public static boolean saveInGroups = true;
    public static boolean saveInChannels = true;
    // A chat's menu sets the archive rule for that chat only
    // (MZGramSaveExclusions); off ignores the saved rules.
    public static boolean saveExclusions = false;
    // Replies to deleted messages (MZGramDeletedReplies): the kept original
    // as the reply preview, and a reply to a kept message sent as a quote.
    public static boolean keptReplyPreviews = false;
    public static boolean replyToDeletedAsQuote = false;
    // "Deleted messages" in a chat's menu: the list of the chat's kept
    // deleted messages with search (MZGramDeletedMessagesActivity).
    public static boolean deletedMessagesList = false;
    // Deleted chats: chats that left the chat list while the archive keeps
    // their deleted messages (MZGramDeletedDialogs).
    public static boolean deletedDialogsList = false;
    // Keep deleted chats in the main chat list with their last kept message
    public static boolean deletedDialogsInMainList = false;
    // A kept deleted message is forwarded as a copy (MZGramProtectedContent).
    public static boolean forwardDeletedAsCopy = false;
    // How a deleted message kept in the chat looks: drawn at 75% opacity,
    // and the marks shown before the time (any text, empty for none).
    public static boolean semiTransparentDeleted = true;
    public static final String DEFAULT_DELETED_MARK = "\uD83E\uDDF9";
    public static final String DEFAULT_EDITED_MARK = "\u270F\uFE0F";
    public static String deletedMark = DEFAULT_DELETED_MARK;
    public static String editedMark = DEFAULT_EDITED_MARK;
    // Appearance: Material 3 switches and card-based list layout.
    public static boolean m3Switches = false;
    public static boolean cardsDesign = false;
    // Profile: display saved or currently playing music as a card
    public static boolean profileMusicCard = false;
    // Third-party network services (DoH, OpenFreeMap map preview)
    public static int dohProvider = 0;
    public static boolean openFreeMapPreview = false;
    // Python plugins: allow pip packages to be downloaded from PyPI on device
    public static boolean pipEnabled = false;
    // Plugins catalog: raw URL of index.json repository
    public static final String DEFAULT_PLUGINS_CATALOG_URL = "https://raw.githubusercontent.com/dmytrokurochkin/MZGram-Plugins/main/index.json";
    public static String pluginsCatalogUrl = DEFAULT_PLUGINS_CATALOG_URL;
    // Translation and AI suite
    public static int translationProvider = 0; // 0: Telegram, 1: Google, 2: DeepL, 3: AI, 4: AIDL Offline, 5: Mozhi
    public static String translationTargetLang = "";
    public static boolean chatTranslationBar = false;
    public static boolean translateBeforeSend = false;
    public static boolean translationAutoFallback = false;
    public static String deeplApiKey = "";
    public static String mozhiCustomInstance = "";
    public static int aiProviderType = 0; // 0: Gemini, 1: OpenAI, 2: Anthropic, 3: DeepSeek, 4: Custom
    public static String aiApiKey = "";
    public static String aiCustomEndpoint = "";
    public static String aiCustomModel = "";
    public static boolean speechRecognitionEnabled = false;
    public static int speechEngine = 0; // 0: System, 1: Vosk
    public static String voskModelLang = "uk";
    // Extended peripherals (Point 13 of OWNER-DECISIONS.md)
    public static boolean torCompanionEnabled = false;
    public static String torCompanionPackage = "org.torproject.android";
    public static int torIdleTimeoutMinutes = 5;
    public static boolean customBadgesEnabled = false;
    public static boolean nothingGlyphEnabled = false;
    public static String nothingGlyphApiKey = "";
    public static int nothingGlyphBrightness = 100;
    public static boolean customEmojiFontEnabled = false;
    public static String customEmojiFontPath = "";
    public static boolean syncedLyricsOnline = false;
    public static boolean iconPacksCatalogEnabled = false;
    // Stops sponsored
    // (ad) messages in channels from ever being requested.
    public static boolean disableSponsoredMessages = false;
    // Strips Zalgo-style combining-mark text corruption from display names.
    // See MZGramZalgoFilter.
    public static boolean stripZalgoText = false;
    // Push notifications through UnifiedPush (Notifications and Sounds >
    // UnifiedPush). Null until the user chooses: on by default, since
    // Telegram's own Google push cannot work in MZGram builds.
    public static Boolean useUnifiedPush = null;
    // Telegram's notifications pass through a gateway that moves their
    // encryption headers into the body. Off sends them straight to the
    // distributor, where they cannot be decrypted and only wake the app up.
    public static boolean unifiedPushGatewayEnabled = true;
    // The user's own gateway; empty for the default one.
    public static String unifiedPushGateway = "";
    // The VAPID public key of the user's own gateway, for the built-in
    // Google FCM distributor; empty for the default gateway's key.
    public static String unifiedPushVapidKey = "";

    // Search folds names in decorated Unicode fonts to plain letters
    // (MZGramUnicodeFold in LocaleController.getTranslitString).
    public static boolean foldFancyFontsInSearch = false;

    // A live count of the typed characters above the message field
    // (MZGramCharCounter).
    public static boolean characterCounter = false;

    // from:, date:, before:, after: and type: in a chat's search field
    // (MZGramSearchQuery in MediaDataController.searchMessagesInChat).
    public static boolean searchOperators = false;

    // Tracking parameters are cut from links when opened and pasted
    // (MZGramLinkCleaner, rules from assets only).
    public static boolean cleanLinks = false;

    // What a double tap on a message does, for own and other messages
    // (MZGramDoubleTap).
    public static boolean doubleTapActions = false;

    // "Copy without formatting" in the message menu.
    public static boolean copyPlainText = false;

    // Typing "=" after an expression in the message field adds the result (MZGramInlineMath).
    public static boolean inlineMath = false;

    // Stickers in chats: size in percent (100 as in Telegram), rounded corners, no time over stickers
    public static int stickerSize = 100;
    public static boolean stickerRoundCorners = false;
    public static boolean hideStickerTime = false;

    // Wide channel posts: posts of broadcast channels across the screen
    public static boolean wideChannelPosts = false;

    // granularGroupPermissions
    public static boolean granularGroupPermissions = false;
    // GIF tab and inline results in a member's chat by send_gifs / send_inline
    public static boolean splitGifInlineRights = false;

    // linkedCustomEmoji
    public static boolean linkedCustomEmoji = false;

    // linkTypeface
    public static boolean linkTypeface = false;

    // Reaction filter: hide all reactions by kind of chat, or only the listed emoji (space-separated)
    public static boolean hideReactionsPrivate = false;
    public static boolean hideReactionsGroups = false;
    public static boolean hideReactionsChannels = false;
    public static String hiddenReactions = "";

    // Shadow ban: hide messages of the listed peers (comma-separated ids) and, separately, of blocked users in groups
    public static boolean shadowBan = false;
    public static boolean hideBlockedInGroups = false;
    public static String shadowBanList = "";

    // Message filters: rules with regular expressions hide other people's
    // messages (MZGramRegexFilters, rules in prefs mzgram_regex_filters);
    // the common rules also in private chats, media of hidden messages not
    // downloaded on its own.
    public static boolean regexFilters = false;
    public static boolean regexFiltersInPrivate = false;
    public static boolean regexFiltersNoDownload = false;
    // The MZGram side menu (MZGramDrawer) in place of the popup menu of the
    // three dots of the chat list; its QR button and the larger avatar.
    public static boolean customDrawer = false;
    public static boolean drawerShowQr = true;
    public static boolean drawerLargeAvatar = false;
    // Its look: the background of the header (MZGramDrawer.HEADER_*) and the glass panel
    public static int drawerHeaderStyle = 0;
    public static boolean drawerGlass = false;

    // plainCodeBlocks
    public static boolean plainCodeBlocks = false;

    // hexColorSwatch
    public static boolean hexColorSwatch = false;

    // scheduleSlider
    public static boolean scheduleSlider = false;

    // sendSilentByDefault
    public static boolean sendSilentByDefault = false;

    // showRegistrationDate
    public static boolean showRegistrationDate = false;

    // audioEnhance
    public static boolean audioEnhance = false;

    // cameraStabilization
    public static boolean cameraStabilization = false;

    // pauseRoundOnLeave
    public static boolean pauseRoundOnLeave = false;

    // alwaysSendHd
    public static boolean alwaysSendHd = false;

    // hideChatStatus
    public static boolean hideChatStatus = false;

    // Compact chat header: smaller avatar and text to save screen space
    public static boolean compactChatHeader = false;

    // staticInputIcons
    public static boolean staticInputIcons = false;

    // panguSpacing
    public static boolean panguSpacing = false;

    // Double tap on own messages: 0 reaction, 1 reply, 2 copy text, 3 nothing.
    public static int doubleTapOwn = 0;

    // Shape of the chat-list new-chat button: 0 circle, 1 rounded square, 2 squircle (MZGramFabShape).
    public static int fabShape = 0;

    // Default delay (minutes) offered by the schedule dialog while scheduleSlider is on.
    public static int scheduleDefaultMinutes = 60;

    // Own list of quick reactions (JSON arrays of {"emoticon"} or {"document_id"}), separately for chats and channels.
    public static boolean usePinnedReactionsChats = false;
    public static boolean usePinnedReactionsChannels = false;
    public static String pinnedReactionsChats = "[]";
    public static String pinnedReactionsChannels = "[]";
    // Formatting menu of the input field: custom order and hidden styles (comma-separated keys, empty = Telegram default)
    public static String textStyleOrder = "";
    public static String textStyleHidden = "";

    // Double tap on other people's messages, same values.
    public static int doubleTapOther = 0;

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
            disableNumberRounding = preferences.getBoolean("disableNumberRounding", false);
            formatTimeWithSeconds = preferences.getBoolean("formatTimeWithSeconds", false);
            askBeforeCall = preferences.getBoolean("askBeforeCall", false);
            hideStories = preferences.getBoolean("hideStories", false);
            storiesNoBackgroundLoad = preferences.getBoolean("storiesNoBackgroundLoad", false);
            disableGreetingSticker = preferences.getBoolean("disableGreetingSticker", false);
            hideChannelBottomButtons = preferences.getBoolean("hideChannelBottomButtons", false);
            openArchiveOnPull = preferences.getBoolean("openArchiveOnPull", false);
            disableInstantCamera = preferences.getBoolean("disableInstantCamera", false);
            hideCameraTile = preferences.getBoolean("hideCameraTile", false);
            checkUpdates = preferences.getBoolean("checkUpdates", true);
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
            showAskAi = preferences.getBoolean("showAskAi", true);
            aiProvider = preferences.getString("aiProvider", MZGramAiRules.DEFAULT_PROVIDER);
            aiWarned = preferences.getBoolean("aiWarned", false);
            aiMenuButtons = new java.util.HashSet<>(preferences.getStringSet("aiMenuButtons", java.util.Collections.emptySet()));
            hiddenSettingsRows = new java.util.HashSet<>(preferences.getStringSet("hiddenSettingsRows", java.util.Collections.emptySet()));
            saveProtectedContent = preferences.getBoolean("saveProtectedContent", false);
            deleteForAllByDefault = preferences.getBoolean("deleteForAllByDefault", false);
            predictiveBackAnimation = preferences.getBoolean("predictiveBackAnimation", true);
            gooeyAvatarAnimation = preferences.getBoolean("gooeyAvatarAnimation", true);
            avatarCorners = Math.max(MZGramAvatarShape.MIN, Math.min(MZGramAvatarShape.MAX, preferences.getInt("avatarCorners", MZGramAvatarShape.MAX)));
            MZGramAvatarShape.changed();
            glassOutline = preferences.getInt("glassOutline", 0);
            folderTabIcons = preferences.getInt("folderTabIcons", 0);
            appFont = preferences.getInt("appFont", 0);
            appFontName = preferences.getString("appFontName", "");
            voiceQuality = preferences.getInt("voiceQuality", 0);
            losslessPhotoCrop = preferences.getBoolean("losslessPhotoCrop", false);
            crashReports = preferences.getBoolean("crashReports", false);
            hideBottomNavigationBar = preferences.getBoolean("hideBottomNavigationBar", false);
            accountsInMenu = preferences.getBoolean("accountsInMenu", true);
            accountOrderCustomised = preferences.getBoolean("accountOrderCustomised", false);
            recentChatsOnBack = preferences.getBoolean("recentChatsOnBack", false);
            extendedPins = preferences.getBoolean("extendedPins", false);
            pillStack = preferences.getBoolean("pillStack", false);
            iconPacks = preferences.getBoolean("iconPacks", false);
            monetThemes = preferences.getBoolean("monetThemes", false);
            monetStyle = preferences.getInt("monetStyle", 0) == 1 ? 1 : 0;
            cameraX = preferences.getBoolean("useCameraX", false);
            cameraXWideLens = preferences.getBoolean("cameraXWideLens", false);
            cameraXZoomSlider = preferences.getBoolean("cameraXZoomSlider", true);
            roundVideoCamera2 = preferences.getBoolean("round_video_camera2_enabled", false);
            roundVideoHighFps = preferences.getBoolean("round_video_high_fps", false);
            roundVideoBitrate = preferences.getInt("round_video_bitrate", 1200000);
            musicPlayer = preferences.getBoolean("musicPlayer", false);
            musicPlayerBar = preferences.getBoolean("musicPlayerBar", true);
            proxyDisableConditions = preferences.getInt("proxyDisableConditions", 0) & MZGramProxyAutoDisable.ALL;
            pluginsRun = preferences.getBoolean("pluginsRun", false);
            language = preferences.getString("language", "");
            folderTabsAtBottom = preferences.getBoolean("folderTabsAtBottom", false);
            confirmAVMessage = preferences.getBoolean("confirmAVMessage", false);
            mediaPreviewOnLongPress = preferences.getBoolean("mediaPreviewOnLongPress", false);
            ghostMode = preferences.getBoolean("ghostMode", false);
            ghostAutoDelaySend = preferences.getBoolean("ghostAutoDelaySend", false);
            ghostSilentSend = preferences.getBoolean("ghostSilentSend", false);
            offerGhostModeBeforeStories = preferences.getBoolean("offerGhostModeBeforeStories", false);
            ghostModeExclusions = preferences.getBoolean("ghostModeExclusions", false);
            confirmInternalLinks = preferences.getBoolean("confirmInternalLinks", false);
            disableReadStickers = preferences.getBoolean("disableReadStickers", false);
            disableLinkPreviews = preferences.getBoolean("disableLinkPreviews", false);
            disableCloudDrafts = preferences.getBoolean("disableCloudDrafts", false);
            lockScreenVoiceProtection = preferences.getBoolean("lockScreenVoiceProtection", false);
            ownLastSeen = preferences.getBoolean("ownLastSeen", false);
            keepGroupReads = preferences.getBoolean("keepGroupReads", false);
            botStartConfirm = preferences.getBoolean("botStartConfirm", false);
            reduceNetworkTracking = preferences.getBoolean("reduceNetworkTracking", false);
            passcodeAccounts = preferences.getBoolean("passcodeAccounts", false);
            // The removed "hide own online status" and "hide own phone
            // number" switches: Telegram's own privacy settings do this.
            if (preferences.contains("hideOwnOnlineStatus") || preferences.contains("savedLastSeenPrivacyState") || preferences.contains("hideOwnPhoneNumber")) {
                preferences.edit().remove("hideOwnOnlineStatus").remove("savedLastSeenPrivacyState").remove("hideOwnPhoneNumber").apply();
            }
            // The removed "hide others' online status" switch.
            if (preferences.contains("hideOthersOnlineStatus")) {
                preferences.edit().remove("hideOthersOnlineStatus").apply();
            }
            // The switch is on by default now; the old key kept "off" for
            // everyone who never touched it, so it is read under a new key.
            // The single archive switch of the previous version is now two;
            // its value carries over to both.
            boolean oldArchiveSwitch = preferences.getBoolean("saveDeletedAndEdited", true);
            saveDeletedMessages = preferences.getBoolean("saveDeletedMessages", oldArchiveSwitch);
            saveEditHistory = preferences.getBoolean("saveEditHistory", oldArchiveSwitch);
            if (preferences.contains("saveDeletedAndEdited")) {
                preferences.edit()
                        .putBoolean("saveDeletedMessages", saveDeletedMessages)
                        .putBoolean("saveEditHistory", saveEditHistory)
                        .remove("saveDeletedAndEdited")
                        .apply();
            }
            saveArchiveMedia = preferences.getBoolean("saveArchiveMedia", true);
            peerIdMode = preferences.getInt("peerIdMode", MZGramPeerId.HIDDEN);
            reliableDelivery = preferences.getBoolean("reliableDelivery", false);
            saveFormatting = preferences.getBoolean("saveFormatting", true);
            saveReactions = preferences.getBoolean("saveReactions", true);
            saveFromUsers = preferences.getBoolean("saveFromUsers", true);
            saveForBots = preferences.getBoolean("saveForBots", true);
            saveInGroups = preferences.getBoolean("saveInGroups", true);
            saveInChannels = preferences.getBoolean("saveInChannels", true);
            saveExclusions = preferences.getBoolean("saveExclusions", false);
            keptReplyPreviews = preferences.getBoolean("keptReplyPreviews", false);
            replyToDeletedAsQuote = preferences.getBoolean("replyToDeletedAsQuote", false);
            deletedMessagesList = preferences.getBoolean("deletedMessagesList", false);
            deletedDialogsList = preferences.getBoolean("deletedDialogsList", false);
            deletedDialogsInMainList = preferences.getBoolean("deletedDialogsInMainList", false);
            m3Switches = preferences.getBoolean("m3Switches", false);
            cardsDesign = preferences.getBoolean("cardsDesign", false);
            profileMusicCard = preferences.getBoolean("profileMusicCard", false);
            dohProvider = preferences.getInt("dohProvider", 0);
            openFreeMapPreview = preferences.getBoolean("openFreeMapPreview", false);
            pipEnabled = preferences.getBoolean("pipEnabled", false);
            pluginsCatalogUrl = preferences.getString("pluginsCatalogUrl", DEFAULT_PLUGINS_CATALOG_URL);
            translationProvider = preferences.getInt("translationProvider", 0);
            translationTargetLang = preferences.getString("translationTargetLang", "");
            chatTranslationBar = preferences.getBoolean("chatTranslationBar", false);
            translateBeforeSend = preferences.getBoolean("translateBeforeSend", false);
            translationAutoFallback = preferences.getBoolean("translationAutoFallback", false);
            deeplApiKey = preferences.getString("deeplApiKey", "");
            mozhiCustomInstance = preferences.getString("mozhiCustomInstance", "");
            aiProviderType = preferences.getInt("aiProviderType", 0);
            aiApiKey = preferences.getString("aiApiKey", "");
            aiCustomEndpoint = preferences.getString("aiCustomEndpoint", "");
            aiCustomModel = preferences.getString("aiCustomModel", "");
            speechRecognitionEnabled = preferences.getBoolean("speechRecognitionEnabled", false);
            speechEngine = preferences.getInt("speechEngine", 0);
            voskModelLang = preferences.getString("voskModelLang", "uk");
            torCompanionEnabled = preferences.getBoolean("torCompanionEnabled", false);
            torCompanionPackage = preferences.getString("torCompanionPackage", "org.torproject.android");
            torIdleTimeoutMinutes = preferences.getInt("torIdleTimeoutMinutes", 5);
            customBadgesEnabled = preferences.getBoolean("customBadgesEnabled", false);
            nothingGlyphEnabled = preferences.getBoolean("nothingGlyphEnabled", false);
            nothingGlyphApiKey = preferences.getString("nothingGlyphApiKey", "");
            nothingGlyphBrightness = preferences.getInt("nothingGlyphBrightness", 100);
            customEmojiFontEnabled = preferences.getBoolean("customEmojiFontEnabled", false);
            customEmojiFontPath = preferences.getString("customEmojiFontPath", "");
            syncedLyricsOnline = preferences.getBoolean("syncedLyricsOnline", false);
            iconPacksCatalogEnabled = preferences.getBoolean("iconPacksCatalogEnabled", false);
            forwardDeletedAsCopy = preferences.getBoolean("forwardDeletedAsCopy", false);
            semiTransparentDeleted = preferences.getBoolean("semiTransparentDeleted", true);
            deletedMark = preferences.getString("deletedMark", DEFAULT_DELETED_MARK);
            editedMark = preferences.getString("editedMark", DEFAULT_EDITED_MARK);
            if (preferences.contains("saveMessageHistory") || preferences.contains("historyTrackedDialogs")) {
                preferences.edit().remove("saveMessageHistory").remove("historyTrackedDialogs").apply();
            }
            disableSponsoredMessages = preferences.getBoolean("disableSponsoredMessages", false);
            stripZalgoText = preferences.getBoolean("stripZalgoText", false);
            useUnifiedPush = preferences.contains("useUnifiedPush") ? preferences.getBoolean("useUnifiedPush", false) : null;
            unifiedPushGatewayEnabled = preferences.getBoolean("unifiedPushGatewayEnabled", true);
            unifiedPushGateway = preferences.getString("unifiedPushGateway", "");
            unifiedPushVapidKey = preferences.getString("unifiedPushVapidKey", "");
            // The archive keeps media of any size, with no total quota, and
            // never deletes saved files on its own; the old limit keys go.
            if (preferences.contains("historyMediaSizeLimitMb") || preferences.contains("historyTotalMediaCapMb")) {
                preferences.edit().remove("historyMediaSizeLimitMb").remove("historyTotalMediaCapMb").apply();
            }
            foldFancyFontsInSearch = preferences.getBoolean("foldFancyFontsInSearch", false);
            characterCounter = preferences.getBoolean("characterCounter", false);
            searchOperators = preferences.getBoolean("searchOperators", false);
            cleanLinks = preferences.getBoolean("cleanLinks", false);
            doubleTapActions = preferences.getBoolean("doubleTapActions", false);
            copyPlainText = preferences.getBoolean("copyPlainText", false);
            inlineMath = preferences.getBoolean("inlineMath", false);
            stickerSize = preferences.getInt("stickerSize", 100);
            stickerRoundCorners = preferences.getBoolean("stickerRoundCorners", false);
            hideStickerTime = preferences.getBoolean("hideStickerTime", false);
            wideChannelPosts = preferences.getBoolean("wideChannelPosts", false);
            granularGroupPermissions = preferences.getBoolean("granularGroupPermissions", false);
            splitGifInlineRights = preferences.getBoolean("splitGifInlineRights", false);
            linkedCustomEmoji = preferences.getBoolean("linkedCustomEmoji", false);
            linkTypeface = preferences.getBoolean("linkTypeface", false);
            hideReactionsPrivate = preferences.getBoolean("hideReactionsPrivate", false);
            hideReactionsGroups = preferences.getBoolean("hideReactionsGroups", false);
            hideReactionsChannels = preferences.getBoolean("hideReactionsChannels", false);
            hiddenReactions = preferences.getString("hiddenReactions", "");
            shadowBan = preferences.getBoolean("shadowBan", false);
            hideBlockedInGroups = preferences.getBoolean("hideBlockedInGroups", false);
            shadowBanList = preferences.getString("shadowBanList", "");
            regexFilters = preferences.getBoolean("regexFilters", false);
            regexFiltersInPrivate = preferences.getBoolean("regexFiltersInPrivate", false);
            regexFiltersNoDownload = preferences.getBoolean("regexFiltersNoDownload", false);
            customDrawer = preferences.getBoolean("customDrawer", false);
            drawerShowQr = preferences.getBoolean("drawerShowQr", true);
            drawerLargeAvatar = preferences.getBoolean("drawerLargeAvatar", false);
            drawerHeaderStyle = preferences.getInt("drawerHeaderStyle", 0);
            drawerGlass = preferences.getBoolean("drawerGlass", false);
            plainCodeBlocks = preferences.getBoolean("plainCodeBlocks", false);
            hexColorSwatch = preferences.getBoolean("hexColorSwatch", false);
            scheduleSlider = preferences.getBoolean("scheduleSlider", false);
            sendSilentByDefault = preferences.getBoolean("sendSilentByDefault", false);
            showRegistrationDate = preferences.getBoolean("showRegistrationDate", false);
            audioEnhance = preferences.getBoolean("audioEnhance", false);
            cameraStabilization = preferences.getBoolean("cameraStabilization", false);
            pauseRoundOnLeave = preferences.getBoolean("pauseRoundOnLeave", false);
            alwaysSendHd = preferences.getBoolean("alwaysSendHd", false);
            hideChatStatus = preferences.getBoolean("hideChatStatus", false);
            compactChatHeader = preferences.getBoolean("chat_header_compact", false);
            staticInputIcons = preferences.getBoolean("staticInputIcons", false);
            panguSpacing = preferences.getBoolean("panguSpacing", false);
            doubleTapOwn = preferences.getInt("doubleTapOwn", 0);
            fabShape = preferences.getInt("fabShape", 0);
            scheduleDefaultMinutes = preferences.getInt("scheduleDefaultMinutes", 60);
            usePinnedReactionsChats = preferences.getBoolean("usePinnedReactionsChats", false);
            usePinnedReactionsChannels = preferences.getBoolean("usePinnedReactionsChannels", false);
            pinnedReactionsChats = preferences.getString("pinnedReactionsChats", "[]");
            pinnedReactionsChannels = preferences.getString("pinnedReactionsChannels", "[]");
            textStyleOrder = preferences.getString("textStyleOrder", "");
            textStyleHidden = preferences.getString("textStyleHidden", "");
            doubleTapOther = preferences.getInt("doubleTapOther", 0);
            configLoaded = true;
        }
    }

    public static void toggleExtendedPins() {
        extendedPins = !extendedPins;
        putBoolean("extendedPins", extendedPins);
    }

    public static void togglePillStack() {
        pillStack = !pillStack;
        putBoolean("pillStack", pillStack);
    }

    public static void toggleIconPacks() {
        iconPacks = !iconPacks;
        putBoolean("iconPacks", iconPacks);
    }

    public static void toggleMonetThemes() {
        monetThemes = !monetThemes;
        putBoolean("monetThemes", monetThemes);
    }

    public static void setMonetStyle(int style) {
        monetStyle = style == 1 ? 1 : 0;
        preferences().edit().putInt("monetStyle", monetStyle).apply();
    }

    public static void toggleCameraX() {
        cameraX = !cameraX;
        putBoolean("useCameraX", cameraX);
    }

    public static void toggleCameraXWideLens() {
        cameraXWideLens = !cameraXWideLens;
        putBoolean("cameraXWideLens", cameraXWideLens);
    }

    public static void toggleCameraXZoomSlider() {
        cameraXZoomSlider = !cameraXZoomSlider;
        putBoolean("cameraXZoomSlider", cameraXZoomSlider);
    }

    public static void toggleRoundVideoCamera2() {
        roundVideoCamera2 = !roundVideoCamera2;
        putBoolean("round_video_camera2_enabled", roundVideoCamera2);
    }

    public static void toggleRoundVideoHighFps() {
        roundVideoHighFps = !roundVideoHighFps;
        putBoolean("round_video_high_fps", roundVideoHighFps);
    }

    public static void setRoundVideoBitrate(int bitrate) {
        roundVideoBitrate = bitrate;
        preferences().edit().putInt("round_video_bitrate", roundVideoBitrate).apply();
    }

    public static void setProxyDisableConditions(int mask) {
        proxyDisableConditions = mask & MZGramProxyAutoDisable.ALL;
        preferences().edit().putInt("proxyDisableConditions", proxyDisableConditions).apply();
    }

    public static void togglePluginsRun() {
        pluginsRun = !pluginsRun;
        putBoolean("pluginsRun", pluginsRun);
    }

    public static void toggleMusicPlayer() {
        musicPlayer = !musicPlayer;
        putBoolean("musicPlayer", musicPlayer);
    }

    public static void toggleMusicPlayerBar() {
        musicPlayerBar = !musicPlayerBar;
        putBoolean("musicPlayerBar", musicPlayerBar);
    }

    public static void toggleRecentChatsOnBack() {
        recentChatsOnBack = !recentChatsOnBack;
        putBoolean("recentChatsOnBack", recentChatsOnBack);
    }

    public static void setAccountOrderCustomised() {
        if (!accountOrderCustomised) {
            accountOrderCustomised = true;
            putBoolean("accountOrderCustomised", true);
        }
    }

    private static void putBoolean(String key, boolean value) {
        preferences().edit().putBoolean(key, value).apply();
    }

    private static void putInt(String key, int value) {
        preferences().edit().putInt(key, value).apply();
    }

    private static void putString(String key, String value) {
        preferences().edit().putString(key, value).apply();
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

    public static void toggleStoriesNoBackgroundLoad() {
        storiesNoBackgroundLoad = !storiesNoBackgroundLoad;
        putBoolean("storiesNoBackgroundLoad", storiesNoBackgroundLoad);
    }

    /** Stories are hidden and must not load in the background (fix 1.2). */
    public static boolean skipStoriesLoad() {
        return hideStories && storiesNoBackgroundLoad;
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

    public static void toggleCheckUpdates() {
        checkUpdates = !checkUpdates;
        putBoolean("checkUpdates", checkUpdates);
    }

    public static void toggleHideCameraTile() {
        hideCameraTile = !hideCameraTile;
        putBoolean("hideCameraTile", hideCameraTile);
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

    public static void toggleShowAskAi() {
        showAskAi = !showAskAi;
        putBoolean("showAskAi", showAskAi);
    }

    public static void setAiProvider(String key) {
        aiProvider = key;
        preferences().edit().putString("aiProvider", key).commit();
    }

    public static void setAiWarned(boolean warned) {
        aiWarned = warned;
        preferences().edit().putBoolean("aiWarned", warned).commit();
    }

    public static void setAiMenuButton(String key, boolean shown) {
        Set<String> result = new java.util.HashSet<>(aiMenuButtons);
        if (shown) {
            result.add(key);
        } else {
            result.remove(key);
        }
        aiMenuButtons = result;
        preferences().edit().putStringSet("aiMenuButtons", new java.util.HashSet<>(result)).commit();
    }

    public static void setSettingsRowHidden(String key, boolean hidden) {
        Set<String> result = new java.util.HashSet<>(hiddenSettingsRows);
        if (hidden) {
            result.add(key);
        } else {
            result.remove(key);
        }
        hiddenSettingsRows = result;
        preferences().edit().putStringSet("hiddenSettingsRows", new java.util.HashSet<>(result)).commit();
    }

    public static void toggleSaveProtectedContent() {
        saveProtectedContent = !saveProtectedContent;
        putBoolean("saveProtectedContent", saveProtectedContent);
    }

    public static void togglePredictiveBackAnimation() {
        predictiveBackAnimation = !predictiveBackAnimation;
        putBoolean("predictiveBackAnimation", predictiveBackAnimation);
    }

    public static void toggleGooeyAvatarAnimation() {
        gooeyAvatarAnimation = !gooeyAvatarAnimation;
        putBoolean("gooeyAvatarAnimation", gooeyAvatarAnimation);
    }

    public static void setAvatarCorners(int corners) {
        avatarCorners = Math.max(MZGramAvatarShape.MIN, Math.min(MZGramAvatarShape.MAX, corners));
        preferences().edit().putInt("avatarCorners", avatarCorners).apply();
        MZGramAvatarShape.changed();
    }

    public static void setGlassOutline(int style) {
        glassOutline = style == 1 || style == 2 ? style : 0;
        preferences().edit().putInt("glassOutline", glassOutline).apply();
    }

    public static void setFolderTabIcons(int mode) {
        folderTabIcons = mode == 1 || mode == 2 ? mode : 0;
        preferences().edit().putInt("folderTabIcons", folderTabIcons).apply();
    }

    public static void setAppFont(int mode, String name) {
        appFont = (mode >= 1 && mode <= 4) ? mode : 0;
        appFontName = appFont == 2 && name != null ? name : "";
        preferences().edit().putInt("appFont", appFont).putString("appFontName", appFontName).apply();
    }

    public static void toggleCrashReports() {
        crashReports = !crashReports;
        putBoolean("crashReports", crashReports);
        if (crashReports) {
            MZGramCrashLog.install();
        } else {
            MZGramCrashLog.clear();
        }
    }

    public static void toggleLosslessPhotoCrop() {
        losslessPhotoCrop = !losslessPhotoCrop;
        putBoolean("losslessPhotoCrop", losslessPhotoCrop);
    }

    public static void setVoiceQuality(int mode) {
        voiceQuality = mode == 1 || mode == 2 ? mode : 0;
        preferences().edit().putInt("voiceQuality", voiceQuality).apply();
        MZGramVoiceQuality.apply();
    }

    public static void toggleHideBottomNavigationBar() {
        hideBottomNavigationBar = !hideBottomNavigationBar;
        putBoolean("hideBottomNavigationBar", hideBottomNavigationBar);
    }

    public static void setPeerIdMode(int mode) {
        peerIdMode = mode;
        preferences().edit().putInt("peerIdMode", mode).apply();
    }

    public static void setReliableDelivery(boolean enabled) {
        reliableDelivery = enabled;
        preferences().edit().putBoolean("reliableDelivery", enabled).commit();
    }

    public static void setLanguage(String code) {
        language = code == null ? "" : code;
        preferences().edit().putString("language", language).apply();
        MZGramLanguage.reset();
    }

    public static void toggleAccountsInMenu() {
        accountsInMenu = !accountsInMenu;
        putBoolean("accountsInMenu", accountsInMenu);
    }

    public static void toggleFolderTabsAtBottom() {
        folderTabsAtBottom = !folderTabsAtBottom;
        putBoolean("folderTabsAtBottom", folderTabsAtBottom);
    }

    public static void toggleDeleteForAllByDefault() {
        deleteForAllByDefault = !deleteForAllByDefault;
        putBoolean("deleteForAllByDefault", deleteForAllByDefault);
    }

    public static void toggleConfirmAVMessage() {
        confirmAVMessage = !confirmAVMessage;
        putBoolean("confirmAVMessage", confirmAVMessage);
    }

    public static void toggleMediaPreviewOnLongPress() {
        mediaPreviewOnLongPress = !mediaPreviewOnLongPress;
        putBoolean("mediaPreviewOnLongPress", mediaPreviewOnLongPress);
    }

    public static void toggleGhostAutoDelaySend() {
        ghostAutoDelaySend = !ghostAutoDelaySend;
        putBoolean("ghostAutoDelaySend", ghostAutoDelaySend);
    }

    public static void toggleGhostSilentSend() {
        ghostSilentSend = !ghostSilentSend;
        putBoolean("ghostSilentSend", ghostSilentSend);
    }

    public static void toggleOfferGhostModeBeforeStories() {
        offerGhostModeBeforeStories = !offerGhostModeBeforeStories;
        putBoolean("offerGhostModeBeforeStories", offerGhostModeBeforeStories);
    }

    public static void toggleConfirmInternalLinks() {
        confirmInternalLinks = !confirmInternalLinks;
        putBoolean("confirmInternalLinks", confirmInternalLinks);
    }

    public static void toggleDisableReadStickers() {
        disableReadStickers = !disableReadStickers;
        putBoolean("disableReadStickers", disableReadStickers);
    }

    public static void toggleDisableLinkPreviews() {
        disableLinkPreviews = !disableLinkPreviews;
        putBoolean("disableLinkPreviews", disableLinkPreviews);
    }

    public static void toggleDisableCloudDrafts() {
        disableCloudDrafts = !disableCloudDrafts;
        putBoolean("disableCloudDrafts", disableCloudDrafts);
    }

    public static void toggleLockScreenVoiceProtection() {
        lockScreenVoiceProtection = !lockScreenVoiceProtection;
        putBoolean("lockScreenVoiceProtection", lockScreenVoiceProtection);
    }

    public static void toggleGhostModeExclusions() {
        ghostModeExclusions = !ghostModeExclusions;
        putBoolean("ghostModeExclusions", ghostModeExclusions);
    }

    public static void toggleOwnLastSeen() {
        ownLastSeen = !ownLastSeen;
        putBoolean("ownLastSeen", ownLastSeen);
    }

    public static void toggleKeepGroupReads() {
        keepGroupReads = !keepGroupReads;
        putBoolean("keepGroupReads", keepGroupReads);
    }

    public static void toggleBotStartConfirm() {
        botStartConfirm = !botStartConfirm;
        putBoolean("botStartConfirm", botStartConfirm);
    }

    public static void toggleReduceNetworkTracking() {
        reduceNetworkTracking = !reduceNetworkTracking;
        putBoolean("reduceNetworkTracking", reduceNetworkTracking);
    }

    public static void togglePasscodeAccounts() {
        passcodeAccounts = !passcodeAccounts;
        putBoolean("passcodeAccounts", passcodeAccounts);
    }

    public static void toggleGhostMode() {
        ghostMode = !ghostMode;
        putBoolean("ghostMode", ghostMode);
    }

    public static void toggleSaveDeletedMessages() {
        saveDeletedMessages = !saveDeletedMessages;
        putBoolean("saveDeletedMessages", saveDeletedMessages);
    }

    public static void toggleSaveEditHistory() {
        saveEditHistory = !saveEditHistory;
        putBoolean("saveEditHistory", saveEditHistory);
    }

    public static void toggleSaveArchiveMedia() {
        saveArchiveMedia = !saveArchiveMedia;
        putBoolean("saveArchiveMedia", saveArchiveMedia);
    }

    public static void toggleSaveFormatting() {
        saveFormatting = !saveFormatting;
        putBoolean("saveFormatting", saveFormatting);
    }

    public static void toggleSaveReactions() {
        saveReactions = !saveReactions;
        putBoolean("saveReactions", saveReactions);
    }

    public static void toggleSaveForBots() {
        saveForBots = !saveForBots;
        putBoolean("saveForBots", saveForBots);
    }

    public static void toggleSaveFromUsers() {
        saveFromUsers = !saveFromUsers;
        putBoolean("saveFromUsers", saveFromUsers);
    }

    public static void toggleSaveInGroups() {
        saveInGroups = !saveInGroups;
        putBoolean("saveInGroups", saveInGroups);
    }

    public static void toggleSaveInChannels() {
        saveInChannels = !saveInChannels;
        putBoolean("saveInChannels", saveInChannels);
    }

    public static void toggleSaveExclusions() {
        saveExclusions = !saveExclusions;
        putBoolean("saveExclusions", saveExclusions);
    }

    public static void toggleKeptReplyPreviews() {
        keptReplyPreviews = !keptReplyPreviews;
        putBoolean("keptReplyPreviews", keptReplyPreviews);
    }

    public static void toggleReplyToDeletedAsQuote() {
        replyToDeletedAsQuote = !replyToDeletedAsQuote;
        putBoolean("replyToDeletedAsQuote", replyToDeletedAsQuote);
    }

    public static void toggleDeletedMessagesList() {
        deletedMessagesList = !deletedMessagesList;
        putBoolean("deletedMessagesList", deletedMessagesList);
    }

    public static void toggleDeletedDialogsList() {
        deletedDialogsList = !deletedDialogsList;
        putBoolean("deletedDialogsList", deletedDialogsList);
    }

    public static void toggleDeletedDialogsInMainList() {
        deletedDialogsInMainList = !deletedDialogsInMainList;
        putBoolean("deletedDialogsInMainList", deletedDialogsInMainList);
    }

    public static void toggleM3Switches() {
        m3Switches = !m3Switches;
        putBoolean("m3Switches", m3Switches);
    }

    public static void toggleCardsDesign() {
        cardsDesign = !cardsDesign;
        putBoolean("cardsDesign", cardsDesign);
    }

    public static void toggleProfileMusicCard() {
        profileMusicCard = !profileMusicCard;
        putBoolean("profileMusicCard", profileMusicCard);
    }

    public static void setDohProvider(int provider) {
        dohProvider = provider;
        putInt("dohProvider", dohProvider);
        MZGramDoH.clearCache();
    }

    public static void toggleOpenFreeMapPreview() {
        openFreeMapPreview = !openFreeMapPreview;
        putBoolean("openFreeMapPreview", openFreeMapPreview);
    }

    public static void togglePipEnabled() {
        pipEnabled = !pipEnabled;
        putBoolean("pipEnabled", pipEnabled);
    }

    public static void setPipEnabled(boolean value) {
        pipEnabled = value;
        putBoolean("pipEnabled", pipEnabled);
    }

    public static void setPluginsCatalogUrl(String url) {
        pluginsCatalogUrl = url != null ? url.trim() : "";
        putString("pluginsCatalogUrl", pluginsCatalogUrl);
    }

    public static void setTranslationProvider(int provider) {
        translationProvider = provider;
        preferences().edit().putInt("translationProvider", provider).apply();
    }

    public static void setTranslationTargetLang(String lang) {
        translationTargetLang = lang != null ? lang : "";
        putString("translationTargetLang", translationTargetLang);
    }

    public static void toggleChatTranslationBar() {
        chatTranslationBar = !chatTranslationBar;
        putBoolean("chatTranslationBar", chatTranslationBar);
    }

    public static void toggleTranslateBeforeSend() {
        translateBeforeSend = !translateBeforeSend;
        putBoolean("translateBeforeSend", translateBeforeSend);
    }

    public static void toggleTranslationAutoFallback() {
        translationAutoFallback = !translationAutoFallback;
        putBoolean("translationAutoFallback", translationAutoFallback);
    }

    public static void setDeeplApiKey(String key) {
        deeplApiKey = key != null ? key.trim() : "";
        putString("deeplApiKey", deeplApiKey);
    }

    public static void setMozhiCustomInstance(String url) {
        mozhiCustomInstance = url != null ? url.trim() : "";
        putString("mozhiCustomInstance", mozhiCustomInstance);
    }

    public static void setAiProviderType(int type) {
        aiProviderType = type;
        preferences().edit().putInt("aiProviderType", type).apply();
    }

    public static void setAiApiKey(String key) {
        aiApiKey = key != null ? key.trim() : "";
        putString("aiApiKey", aiApiKey);
    }

    public static void setAiCustomEndpoint(String endpoint) {
        aiCustomEndpoint = endpoint != null ? endpoint.trim() : "";
        putString("aiCustomEndpoint", aiCustomEndpoint);
    }

    public static void setAiCustomModel(String model) {
        aiCustomModel = model != null ? model.trim() : "";
        putString("aiCustomModel", aiCustomModel);
    }

    public static void toggleSpeechRecognitionEnabled() {
        speechRecognitionEnabled = !speechRecognitionEnabled;
        putBoolean("speechRecognitionEnabled", speechRecognitionEnabled);
    }

    public static void setSpeechEngine(int engine) {
        speechEngine = engine;
        preferences().edit().putInt("speechEngine", engine).apply();
    }

    public static void setVoskModelLang(String lang) {
        voskModelLang = lang != null ? lang.trim() : "uk";
        putString("voskModelLang", voskModelLang);
    }

    public static void toggleTorCompanionEnabled() {
        torCompanionEnabled = !torCompanionEnabled;
        putBoolean("torCompanionEnabled", torCompanionEnabled);
    }

    public static void setTorCompanionPackage(String pkg) {
        torCompanionPackage = pkg != null ? pkg : "org.torproject.android";
        putString("torCompanionPackage", torCompanionPackage);
    }

    public static void setTorIdleTimeoutMinutes(int minutes) {
        torIdleTimeoutMinutes = minutes;
        preferences().edit().putInt("torIdleTimeoutMinutes", minutes).apply();
    }

    public static void toggleCustomBadgesEnabled() {
        customBadgesEnabled = !customBadgesEnabled;
        putBoolean("customBadgesEnabled", customBadgesEnabled);
    }

    public static void toggleNothingGlyphEnabled() {
        nothingGlyphEnabled = !nothingGlyphEnabled;
        putBoolean("nothingGlyphEnabled", nothingGlyphEnabled);
    }

    public static void setNothingGlyphApiKey(String key) {
        nothingGlyphApiKey = key != null ? key.trim() : "";
        putString("nothingGlyphApiKey", nothingGlyphApiKey);
    }

    public static void setNothingGlyphBrightness(int brightness) {
        nothingGlyphBrightness = brightness;
        preferences().edit().putInt("nothingGlyphBrightness", brightness).apply();
    }

    public static void toggleCustomEmojiFontEnabled() {
        customEmojiFontEnabled = !customEmojiFontEnabled;
        putBoolean("customEmojiFontEnabled", customEmojiFontEnabled);
    }

    public static void setCustomEmojiFontPath(String path) {
        customEmojiFontPath = path != null ? path : "";
        putString("customEmojiFontPath", customEmojiFontPath);
    }

    public static void toggleSyncedLyricsOnline() {
        syncedLyricsOnline = !syncedLyricsOnline;
        putBoolean("syncedLyricsOnline", syncedLyricsOnline);
    }

    public static void toggleIconPacksCatalogEnabled() {
        iconPacksCatalogEnabled = !iconPacksCatalogEnabled;
        putBoolean("iconPacksCatalogEnabled", iconPacksCatalogEnabled);
    }

    public static void toggleForwardDeletedAsCopy() {
        forwardDeletedAsCopy = !forwardDeletedAsCopy;
        putBoolean("forwardDeletedAsCopy", forwardDeletedAsCopy);
    }

    public static void toggleSemiTransparentDeleted() {
        semiTransparentDeleted = !semiTransparentDeleted;
        putBoolean("semiTransparentDeleted", semiTransparentDeleted);
    }

    public static void setDeletedMark(String mark) {
        deletedMark = mark == null ? "" : mark;
        preferences().edit().putString("deletedMark", deletedMark).apply();
    }

    public static void setEditedMark(String mark) {
        editedMark = mark == null ? "" : mark;
        preferences().edit().putString("editedMark", editedMark).apply();
    }

    public static void toggleDisableSponsoredMessages() {
        disableSponsoredMessages = !disableSponsoredMessages;
        putBoolean("disableSponsoredMessages", disableSponsoredMessages);
    }

    public static void setUseUnifiedPush(boolean use) {
        useUnifiedPush = use;
        preferences().edit().putBoolean("useUnifiedPush", use).commit();
    }

    public static void toggleUnifiedPushGatewayEnabled() {
        unifiedPushGatewayEnabled = !unifiedPushGatewayEnabled;
        preferences().edit().putBoolean("unifiedPushGatewayEnabled", unifiedPushGatewayEnabled).commit();
    }

    // Saves the user's own gateway; empty or null goes back to the default.
    // An address that cannot be used is not saved and gives false.
    public static boolean setUnifiedPushGateway(String url) {
        String gateway = "";
        if (url != null && !url.trim().isEmpty()) {
            gateway = MZGramUnifiedPushRules.normalizeGateway(url);
            if (gateway == null) {
                return false;
            }
        }
        unifiedPushGateway = gateway;
        preferences().edit().putString("unifiedPushGateway", gateway).commit();
        return true;
    }

    // Saves the VAPID key of the user's own gateway; empty, null or the
    // default key go back to the default. A key that is not a VAPID key is
    // not saved and gives false.
    public static boolean setUnifiedPushVapidKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.equals(MZGramUnifiedPushRules.DEFAULT_VAPID_KEY)) {
            value = "";
        }
        if (!value.isEmpty() && !MZGramUnifiedPushRules.isValidVapidKey(value)) {
            return false;
        }
        unifiedPushVapidKey = value;
        preferences().edit().putString("unifiedPushVapidKey", value).commit();
        return true;
    }

    public static void toggleStripZalgoText() {
        stripZalgoText = !stripZalgoText;
        putBoolean("stripZalgoText", stripZalgoText);
    }

    public static void toggleFoldFancyFontsInSearch() {
        foldFancyFontsInSearch = !foldFancyFontsInSearch;
        putBoolean("foldFancyFontsInSearch", foldFancyFontsInSearch);
    }

    public static void togglePanguSpacing() {
        panguSpacing = !panguSpacing;
        putBoolean("panguSpacing", panguSpacing);
    }

    public static void toggleStaticInputIcons() {
        staticInputIcons = !staticInputIcons;
        putBoolean("staticInputIcons", staticInputIcons);
    }

    public static void toggleHideChatStatus() {
        hideChatStatus = !hideChatStatus;
        putBoolean("hideChatStatus", hideChatStatus);
    }

    public static void toggleCompactChatHeader() {
        compactChatHeader = !compactChatHeader;
        putBoolean("chat_header_compact", compactChatHeader);
    }

    public static void toggleAlwaysSendHd() {
        alwaysSendHd = !alwaysSendHd;
        putBoolean("alwaysSendHd", alwaysSendHd);
    }

    public static void togglePauseRoundOnLeave() {
        pauseRoundOnLeave = !pauseRoundOnLeave;
        putBoolean("pauseRoundOnLeave", pauseRoundOnLeave);
    }

    public static void toggleCameraStabilization() {
        cameraStabilization = !cameraStabilization;
        putBoolean("cameraStabilization", cameraStabilization);
    }

    public static void toggleHideReactionsPrivate() {
        hideReactionsPrivate = !hideReactionsPrivate;
        putBoolean("hideReactionsPrivate", hideReactionsPrivate);
    }

    public static void toggleHideReactionsGroups() {
        hideReactionsGroups = !hideReactionsGroups;
        putBoolean("hideReactionsGroups", hideReactionsGroups);
    }

    public static void toggleHideReactionsChannels() {
        hideReactionsChannels = !hideReactionsChannels;
        putBoolean("hideReactionsChannels", hideReactionsChannels);
    }

    public static void toggleRegexFilters() {
        regexFilters = !regexFilters;
        putBoolean("regexFilters", regexFilters);
        MZGramRegexFilters.clearCache();
    }

    public static void toggleRegexFiltersInPrivate() {
        regexFiltersInPrivate = !regexFiltersInPrivate;
        putBoolean("regexFiltersInPrivate", regexFiltersInPrivate);
        MZGramRegexFilters.clearCache();
    }

    public static void toggleRegexFiltersNoDownload() {
        regexFiltersNoDownload = !regexFiltersNoDownload;
        putBoolean("regexFiltersNoDownload", regexFiltersNoDownload);
    }

    public static void toggleCustomDrawer() {
        customDrawer = !customDrawer;
        putBoolean("customDrawer", customDrawer);
    }

    public static void toggleDrawerShowQr() {
        drawerShowQr = !drawerShowQr;
        putBoolean("drawerShowQr", drawerShowQr);
    }

    public static void toggleDrawerLargeAvatar() {
        drawerLargeAvatar = !drawerLargeAvatar;
        putBoolean("drawerLargeAvatar", drawerLargeAvatar);
    }

    public static void setDrawerHeaderStyle(int value) {
        drawerHeaderStyle = value;
        preferences().edit().putInt("drawerHeaderStyle", value).apply();
    }

    public static void toggleDrawerGlass() {
        drawerGlass = !drawerGlass;
        putBoolean("drawerGlass", drawerGlass);
    }

    public static void toggleShadowBan() {
        shadowBan = !shadowBan;
        putBoolean("shadowBan", shadowBan);
    }

    public static void toggleHideBlockedInGroups() {
        hideBlockedInGroups = !hideBlockedInGroups;
        putBoolean("hideBlockedInGroups", hideBlockedInGroups);
    }

    public static void setShadowBanList(String list) {
        shadowBanList = list == null ? "" : list;
        preferences().edit().putString("shadowBanList", shadowBanList).apply();
    }

    public static void setHiddenReactions(String list) {
        hiddenReactions = list == null ? "" : list;
        preferences().edit().putString("hiddenReactions", hiddenReactions).apply();
    }

    public static void setTextStyleMenu(String order, String hidden) {
        textStyleOrder = order == null ? "" : order;
        textStyleHidden = hidden == null ? "" : hidden;
        preferences().edit()
            .putString("textStyleOrder", textStyleOrder)
            .putString("textStyleHidden", textStyleHidden)
            .apply();
    }

    public static void setPinnedReactions(boolean channels, boolean enabled, String json) {
        if (channels) {
            usePinnedReactionsChannels = enabled;
            pinnedReactionsChannels = json;
        } else {
            usePinnedReactionsChats = enabled;
            pinnedReactionsChats = json;
        }
        preferences().edit()
            .putBoolean(channels ? "usePinnedReactionsChannels" : "usePinnedReactionsChats", enabled)
            .putString(channels ? "pinnedReactionsChannels" : "pinnedReactionsChats", json)
            .apply();
    }

    public static void setScheduleDefaultMinutes(int value) {
        scheduleDefaultMinutes = value;
        preferences().edit().putInt("scheduleDefaultMinutes", value).apply();
    }

    public static void setFabShape(int value) {
        fabShape = value;
        preferences().edit().putInt("fabShape", value).apply();
    }

    public static void toggleAudioEnhance() {
        audioEnhance = !audioEnhance;
        putBoolean("audioEnhance", audioEnhance);
    }

    public static void toggleShowRegistrationDate() {
        showRegistrationDate = !showRegistrationDate;
        putBoolean("showRegistrationDate", showRegistrationDate);
    }

    public static void toggleSendSilentByDefault() {
        sendSilentByDefault = !sendSilentByDefault;
        putBoolean("sendSilentByDefault", sendSilentByDefault);
    }

    public static void toggleScheduleSlider() {
        scheduleSlider = !scheduleSlider;
        putBoolean("scheduleSlider", scheduleSlider);
    }

    public static void toggleHexColorSwatch() {
        hexColorSwatch = !hexColorSwatch;
        putBoolean("hexColorSwatch", hexColorSwatch);
    }

    public static void togglePlainCodeBlocks() {
        plainCodeBlocks = !plainCodeBlocks;
        putBoolean("plainCodeBlocks", plainCodeBlocks);
    }

    public static void toggleLinkTypeface() {
        linkTypeface = !linkTypeface;
        putBoolean("linkTypeface", linkTypeface);
    }

    public static void toggleLinkedCustomEmoji() {
        linkedCustomEmoji = !linkedCustomEmoji;
        putBoolean("linkedCustomEmoji", linkedCustomEmoji);
    }

    public static void toggleSplitGifInlineRights() {
        splitGifInlineRights = !splitGifInlineRights;
        putBoolean("splitGifInlineRights", splitGifInlineRights);
    }

    public static void toggleGranularGroupPermissions() {
        granularGroupPermissions = !granularGroupPermissions;
        putBoolean("granularGroupPermissions", granularGroupPermissions);
    }

    public static void setStickerSize(int size) {
        stickerSize = size;
        preferences().edit().putInt("stickerSize", size).apply();
    }

    public static void toggleStickerRoundCorners() {
        stickerRoundCorners = !stickerRoundCorners;
        putBoolean("stickerRoundCorners", stickerRoundCorners);
    }

    public static void toggleWideChannelPosts() {
        wideChannelPosts = !wideChannelPosts;
        putBoolean("wideChannelPosts", wideChannelPosts);
    }

    public static void toggleHideStickerTime() {
        hideStickerTime = !hideStickerTime;
        putBoolean("hideStickerTime", hideStickerTime);
    }

    public static void toggleInlineMath() {
        inlineMath = !inlineMath;
        putBoolean("inlineMath", inlineMath);
    }

    public static void toggleCopyPlainText() {
        copyPlainText = !copyPlainText;
        putBoolean("copyPlainText", copyPlainText);
    }

    public static void toggleCharacterCounter() {
        characterCounter = !characterCounter;
        putBoolean("characterCounter", characterCounter);
    }

    public static void toggleSearchOperators() {
        searchOperators = !searchOperators;
        putBoolean("searchOperators", searchOperators);
    }

    public static void toggleCleanLinks() {
        cleanLinks = !cleanLinks;
        putBoolean("cleanLinks", cleanLinks);
    }

    public static void toggleDoubleTapActions() {
        doubleTapActions = !doubleTapActions;
        putBoolean("doubleTapActions", doubleTapActions);
    }

    public static void setDoubleTapOwn(int value) {
        doubleTapOwn = value;
        preferences().edit().putInt("doubleTapOwn", value).apply();
    }

    public static void setDoubleTapOther(int value) {
        doubleTapOther = value;
        preferences().edit().putInt("doubleTapOther", value).apply();
    }
}
