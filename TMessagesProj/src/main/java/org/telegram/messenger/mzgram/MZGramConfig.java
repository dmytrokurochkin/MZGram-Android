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
    // Bottom button of a channel one cannot post to: Mute as in Telegram, Discuss (opens the linked chat) or none (ChatActivity)
    public static final int CHANNEL_BOTTOM_MUTE = 0;
    public static final int CHANNEL_BOTTOM_DISCUSS = 1;
    public static final int CHANNEL_BOTTOM_HIDE = 2;
    public static int channelBottomButton = CHANNEL_BOTTOM_MUTE;
    // Swipe on a message: 0 replies as in Telegram, else MZGramSwipeAction (ChatActivity)
    public static int swipeAction = 0;
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
    // Local folders (MZGramLocalFolders, MZGramFolderSync): off, folders as in Telegram
    public static boolean localFolders = false;
    // Exact statuses also noted for the last seen line (MZGramLastSeen.recordStatus)
    public static boolean lastSeenExact = false;
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
    // 0: Telegram's own bitrate (SharedSettings.roundVideoVideoBitrate)
    public static int roundVideoBitrate = 0;
    // The Material 3 music player (MZGramMusicPlayer) and its bar above the chat; on by default, as in the reference
    public static boolean musicPlayer = true;
    public static boolean musicPlayerBar = true;
    // The floating mini player above the main tabs (MZGramPlayerMiniView), with the Material 3 player on
    public static boolean musicPlayerMini = true;
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
    // Profile: display saved or currently playing music as a card; on by default with the Material 3 player, as in the reference
    public static boolean profileMusicCard = true;
    // Third-party network services (DoH, OpenFreeMap map preview)
    public static int dohProvider = 0;
    public static boolean openFreeMapPreview = false;
    // Python plugins: allow pip packages to be downloaded from PyPI on device
    public static boolean pipEnabled = false;
    // Include plugins in settings backup
    public static boolean pluginsBackup = false;
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

    // Stickers in chats: size in percent (100 as in Telegram), shape, no time over stickers
    public static int stickerSize = 100;
    // Sticker shape: 0 as in Telegram, 1 rounded corners, 2 rounded like a message bubble
    public static final int STICKER_SHAPE_DEFAULT = 0;
    public static final int STICKER_SHAPE_ROUNDED = 1;
    public static final int STICKER_SHAPE_MESSAGE = 2;
    public static int stickerShape = STICKER_SHAPE_DEFAULT;
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

    // Video subtitles (N12): external and embedded subtitles in video player
    public static boolean videoSubtitles = false;

    // Persian calendar (N14): Solar Hijri (Shamsi) calendar formatting
    public static boolean persianCalendar = false;

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

    // Taps and buzzes of the app's own controls (MZGramHaptics); on as in Telegram
    public static boolean inAppVibration = true;

    // A new message notifies at once, not after Telegram's 1 s (3 s while online on another device) wait
    public static boolean disableNotificationDelay = false;

    // Settings > MZGram > General > Download boost: 0 off (Telegram), 1 fast, 2 ultra (FileLoadOperation)
    public static int downloadSpeedBoost = 0;

    // Upload in parts of 512 KB instead of 128 KB (FileUploadOperation)
    public static boolean uploadSpeedBoost = false;

    // Settings > MZGram > General > Save folder: the folder name inside Pictures, Movies, Download and Music (MZGramSaveFolder)
    public static String saveFolder = "Telegram";

    // Last seen within the last day as "5 minutes ago" (MZGramRelativeLastSeen)
    public static boolean relativeLastSeen = false;

    // The own phone number is not shown in Settings and the own profile (MZGramHidePhone)
    public static boolean hidePhoneNumber = false;

    // No Archived chats row in the chat list; the three-dots menu has it (MZGramArchiveRow)
    public static boolean hideArchiveFolder = false;

    // In the archive a swipe does nothing, no chat leaves it by accident
    public static boolean disableUnarchiveSwipe = false;

    // Titles in the middle of the action bar (ActionBar.mzgramCentered)
    public static boolean centerTitle = false;

    // No emoji status or Premium star next to the chat list title
    public static boolean hideActionBarStatus = false;

    // No new message and story buttons over the chat list
    public static boolean hideFloatingButton = false;

    // No search field over the idle chat list; the search button of the bar opens search
    public static boolean hideDialogsSearchBar = false;

    // Telegram's winter snowflakes all year: chat list bar and chat background (Theme.canStartHolidayAnimation)
    public static boolean forceSnow = false;

    // In the chat list, the last message line of a group starts with the sender's avatar (DialogCell)
    public static boolean senderMiniAvatars = false;

    // Settings > MZGram > Appearance > Header text: MZGramTitleText LOGO..CUSTOM, and the text of one's own
    public static int titleText = 0;
    public static String titleTextCustom = "";

    // Chats keep the theme and wallpaper chosen for them; off, every chat has the app's theme
    public static boolean customChatThemes = true;

    // Forums and other rounded squares of the avatars take the shape of the chats (MZGramAvatarShape.single)
    public static boolean singleCornerRadius = false;

    // Unread counters on the folder tabs, as in Telegram; off, the tabs show names only
    public static boolean folderTabCounters = true;

    // No All chats tab while there are other folders
    public static boolean hideAllChatsTab = false;

    // Settings > MZGram > Appearance > Sections: the radius of section cards in dp (RecyclerListView.setSections),
    // 16 as in Telegram, 0 square, SECTION_RADIUS_MAX the most
    public static final int SECTION_RADIUS_MAX = 28;
    public static int sectionRadius = 16;
    // Divider style: lines between rows (Telegram), none (Theme key_divider transparent)
    // or segments (every row of a settings card is a card of its own, no lines)
    public static final int DIVIDER_LINE = 0;
    public static final int DIVIDER_HIDDEN = 1;
    public static final int DIVIDER_SEGMENTS = 2;
    public static int dividerStyle = DIVIDER_LINE;

    // Blur in every theme, also where the server's flags turn it off (BlurredBackgroundProviderImpl)
    public static boolean forceBlur = false;

    // Message bubbles without the tail: every corner rounded (MessageDrawable, ChatMessageCell)
    public static boolean removeMessageTail = false;

    // No side Share button next to channel posts and bot messages (ChatMessageCell)
    public static boolean hideShareButton = false;

    // Open polls show their results before one votes, unless hidden until the poll closes
    public static boolean showResultsBeforeVoting = false;

    // Settings > MZGram > Chats > Double tap to seek: 5, 10 (Telegram), 15 or 30 seconds (PhotoViewer)
    public static final int[] DOUBLE_TAP_SEEK_SECONDS = {5, 10, 15, 30};
    public static int doubleTapSeekSeconds = 10;

    // A swipe up on a playing video in the viewer takes it to picture-in-picture (PhotoViewer.enableSwipeToPiP)
    public static boolean swipeToPip = false;

    // A volume button unmutes the video playing in the chat, as in Telegram (LaunchActivity)
    public static boolean unmuteWithVolumeButtons = true;

    // A playing voice message pauses when the app goes to the background (MZGramRoundPause.onAppMinimized)
    public static boolean pauseVoiceOnMinimize = false;

    // Recent stickers on this device keep up to UNLIMITED_RECENT_STICKERS instead of Telegram's limit (MediaDataController)
    public static boolean unlimitedRecentStickers = false;

    // A reply is drawn in the colour of its author, as in Telegram; off, every reply is the theme colour (ReplyMessageLine)
    public static boolean replyColors = true;

    // The emoji pattern of the author in a reply, as in Telegram (ReplyMessageLine)
    public static boolean replyEmoji = true;

    // The tinted background of a reply, as in Telegram (ReplyMessageLine)
    public static boolean replyBackground = true;

    // Scrolling a chat by hand closes the keyboard (ChatActivity)
    public static boolean hideKeyboardOnScroll = false;

    // A mention picked from the list is followed by a comma, not for bots (MentionsContainerView, ChatActivity)
    public static boolean addCommaAfterMention = false;

    // No Send as button by the field; messages go from the account chosen last (ChatActivityEnterView)
    public static boolean hideSendAsPeer = false;
    public static boolean chatMenuDeleteOwn = false;
    public static boolean chatMenuClearDeleted = false;
    public static boolean chatMenuHideTitle = false;
    public static boolean chatMenuGoToMessage = false;
    public static boolean chatMenuLinkedChat = false;
    public static boolean chatMenuBoost = true;
    public static boolean unlimitedSavedGifs = false;
    public static boolean chatMenuToBeginning = false;
    public static boolean adminShortcutStatistics = true;
    public static boolean adminShortcutMembers = true;
    public static boolean adminShortcutPermissions = true;
    public static boolean adminShortcutAdministrators = true;
    public static boolean adminShortcutRecentActions = true;
    public static boolean hideAttachMoney = false;
    public static boolean contentCapture = true;
    public static boolean confirmBotCommands = false;
    public static boolean confirmRepeat = false;
    public static boolean disableProximity = false;
    public static boolean dontAutoPlayNextVoice = false;
    public static boolean disableNotificationBubbles = false;
    public static boolean sortByUnread = false;
    public static boolean disableAvatarBlur = false;
    public static boolean hideGroupSticker = false;
    public static boolean disableTrending = false;
    public static boolean rememberAllReplies = false;
    public static boolean smallGifs = false;
    public static boolean forwardedDate = false;
    public static boolean hideCallButton = false;
    public static boolean hideChannelSearchButton = false;
    public static boolean hideChannelGiftButton = false;
    public static boolean disableGlobalSearch = false;

    // A pencil before the time of an edited message instead of the word (MZGramEditedIcon, ChatMessageCell)
    public static boolean replaceEditedWithIcon = false;

    // A dot on the sender's avatar beside messages: online, or last seen within an hour (MZGramOnlineDot)
    public static boolean showOnlineStatus = false;

    // How many times a channel post was forwarded, after its views (ChatMessageCell)
    public static boolean showForwardsCount = false;

    // Recent actions, Administrators and Permissions in the chat menu where one is an admin (MZGramAdminShortcuts)
    public static boolean quickAdminShortcuts = false;

    // Telegram's AI editor button in the message field, captions and the rich editor (MZGramTelegramAi)
    public static boolean telegramAiEditor = true;

    // Telegram's message summary button beside long messages (MZGramTelegramAi)
    public static boolean telegramAiSummary = true;

    // Telegram's AI summary block at the top of an Instant View page (MZGramTelegramAi)
    public static boolean telegramAiIvSummary = true;

    // At the end of a channel, pulling up opens the next unread channel, as in Telegram (ChatActivity)
    public static boolean quickTransitionChannels = true;

    // At the end of a topic, pulling up opens the next unread topic, as in Telegram (ChatActivity)
    public static boolean quickTransitionTopics = true;

    // Camera in video messages: the front one as in Telegram, or the back one (InstantCameraView)
    public static boolean roundVideoFrontCamera = true;

    // A switch of camera while recording a video message becomes the camera to start with (InstantCameraView)
    public static boolean rememberLastCamera = false;

    // The zoom of a video message stays where the fingers left it (InstantCameraView.finishZoom)
    public static boolean staticZoom = false;

    // "5 km in mi=" in the message field adds the conversion (MZGramUnitConvert)
    public static boolean inlineUnits = false;

    // "15:00 Kyiv in Tokyo=" in the message field adds the time there (MZGramUnitConvert)
    public static boolean inlineTimeZones = false;

    // The main actions of the message menu move to its bottom part after a gap (MZGramMenuGroup)
    public static boolean groupMessageMenu = false;

    // Ghost mode part: others do not see that you read their messages (MZGramGhostMode.blocksRead)
    public static boolean ghostNoRead = true;

    // Ghost mode part: stories are watched without telling the server (MZGramGhostMode.blocksStories)
    public static boolean ghostNoReadStories = true;

    // Ghost mode part: the app does not tell the server you are online (MZGramGhostMode.blocksOnline)
    public static boolean ghostNoOnline = true;

    // Ghost mode part: others do not see that you are typing (MZGramGhostMode.blocksTyping)
    public static boolean ghostNoTyping = true;

    // Ghost mode part: offline again right after a message is sent (MZGramGhostMode.afterSent)
    public static boolean ghostOfflineAuto = false;

    // In ghost mode a message you reply or react to is still marked read (MessagesController.mzgramMarkMessageReadDueToInteraction)
    public static boolean ghostReadOnInteraction = true;

    // A ghost after the title of the chat list while ghost mode is on (MZGramGhostTitle)
    public static boolean ghostStatusInTitle = false;

    // Size of the archive's files: the most its media and attachments take, in MB; -1 for no limit (MZGramArchiveCap)
    public static int archiveTotalLimitMb = -1;
    // Deleted mark: Telegram's trash icon instead of the text, in red or in the colour of the time
    public static final int DELETED_ICON_RED = 0;
    public static final int DELETED_ICON_TIME = 1;
    public static int deletedMarkIconColor = DELETED_ICON_RED;

    // Archive: keep when others read your messages, for the message's details (MZGramReadDates)
    public static boolean saveReadDates = true;

    // Archive: keep the last known online time of people who hide their last seen (MZGramLastSeen)
    public static boolean saveLastOnline = true;

    // Deleted mark: Telegram's trash icon before the time instead of the text mark (MZGramEditedIcon)
    public static boolean deletedMarkIcon = false;

    // Feed: how many of the last posts of every channel are asked (MZGramFeed.POSTS_PER_CHANNEL)
    public static int feedPostsPerChannel = 20;

    // The Feed as a bottom tab in the place of Contacts (MainTabsActivity)
    public static boolean feedTab = false;

    // The unread posts of the Feed on its tab and side menu item (MZGramFeed.unreadCount)
    public static boolean feedUnreadCounter = true;

    // Feed posts wide, with the channel's name above them (MZGramWidePosts)
    public static boolean feedWidePosts = true;

    // The Feed takes the channels of the archive too (MZGramFeed.allChannels)
    public static boolean feedIncludeArchived = false;

    // Section titles above their cards on the window background, not inside the card (RecyclerListView sections); always so with DIVIDER_SEGMENTS
    public static boolean separateHeaders = false;

    // Round spinners (RadialProgressView, CircularProgressDrawable) as a Material 3 indicator: wavy arc, faint track, 2 dp gaps (MZGramM3Loading)
    public static boolean m3Loading = false;

    // Plain sliders (SeekBarView) as a Material 3 slider: 8 dp track, 6 dp gaps, 3 x 24 dp thumb (MZGramM3Slider)
    public static boolean m3Slider = false;

    // The message menu and the reactions bar on frosted glass (Telegram's own, BlurredBackgroundProviderImpl.messageMenuPopupBackground); off: solid
    public static boolean glassMessageMenu = true;

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
            channelBottomButton = preferences.getInt("channelBottomButton", preferences.getBoolean("hideChannelBottomButtons", false) ? CHANNEL_BOTTOM_HIDE : CHANNEL_BOTTOM_MUTE);
            if (channelBottomButton < CHANNEL_BOTTOM_MUTE || channelBottomButton > CHANNEL_BOTTOM_HIDE) {
                channelBottomButton = CHANNEL_BOTTOM_MUTE;
            }
            swipeAction = preferences.getInt("swipeAction", 0);
            if (swipeAction < 0 || swipeAction > 5) {
                swipeAction = 0;
            }
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
            localFolders = preferences.getBoolean("localFolders", false);
            lastSeenExact = preferences.getBoolean("lastSeenExact", false);
            pillStack = preferences.getBoolean("pillStack", false);
            iconPacks = preferences.getBoolean("iconPacks", false);
            monetThemes = preferences.getBoolean("monetThemes", false);
            monetStyle = preferences.getInt("monetStyle", 0) == 1 ? 1 : 0;
            cameraX = preferences.getBoolean("useCameraX", false);
            cameraXWideLens = preferences.getBoolean("cameraXWideLens", false);
            cameraXZoomSlider = preferences.getBoolean("cameraXZoomSlider", true);
            roundVideoCamera2 = preferences.getBoolean("round_video_camera2_enabled", false);
            roundVideoHighFps = preferences.getBoolean("round_video_high_fps", false);
            roundVideoBitrate = preferences.getInt("round_video_bitrate", 0);
            musicPlayer = preferences.getBoolean("musicPlayer", true);
            musicPlayerBar = preferences.getBoolean("musicPlayerBar", true);
            musicPlayerMini = preferences.getBoolean("musicPlayerMini", true);
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
            profileMusicCard = preferences.getBoolean("profileMusicCard", true);
            dohProvider = preferences.getInt("dohProvider", 0);
            openFreeMapPreview = preferences.getBoolean("openFreeMapPreview", false);
            pipEnabled = preferences.getBoolean("pipEnabled", false);
            pluginsBackup = preferences.getBoolean("plugins_backup", false);
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
            stickerShape = preferences.getInt("stickerShape", preferences.getBoolean("stickerRoundCorners", false) ? STICKER_SHAPE_ROUNDED : STICKER_SHAPE_DEFAULT);
            if (stickerShape < STICKER_SHAPE_DEFAULT || stickerShape > STICKER_SHAPE_MESSAGE) {
                stickerShape = STICKER_SHAPE_DEFAULT;
            }
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
            videoSubtitles = preferences.getBoolean("videoSubtitles", false);
            persianCalendar = preferences.getBoolean("persianCalendar", false);
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
            inAppVibration = preferences.getBoolean("inAppVibration", true);
            disableNotificationDelay = preferences.getBoolean("disableNotificationDelay", false);
            downloadSpeedBoost = Math.max(0, Math.min(2, preferences.getInt("downloadSpeedBoost", 0)));
            uploadSpeedBoost = preferences.getBoolean("uploadSpeedBoost", false);
            saveFolder = preferences.getString("saveFolder", "Telegram");
            relativeLastSeen = preferences.getBoolean("relativeLastSeen", false);
            hidePhoneNumber = preferences.getBoolean("hidePhoneNumber", false);
            hideArchiveFolder = preferences.getBoolean("hideArchiveFolder", false);
            disableUnarchiveSwipe = preferences.getBoolean("disableUnarchiveSwipe", false);
            centerTitle = preferences.getBoolean("centerTitle", false);
            hideActionBarStatus = preferences.getBoolean("hideActionBarStatus", false);
            hideFloatingButton = preferences.getBoolean("hideFloatingButton", false);
            hideDialogsSearchBar = preferences.getBoolean("hideDialogsSearchBar", false);
            forceSnow = preferences.getBoolean("forceSnow", false);
            senderMiniAvatars = preferences.getBoolean("senderMiniAvatars", false);
            titleText = Math.max(0, Math.min(5, preferences.getInt("titleText", 0)));
            titleTextCustom = preferences.getString("titleTextCustom", "");
            customChatThemes = preferences.getBoolean("customChatThemes", true);
            singleCornerRadius = preferences.getBoolean("singleCornerRadius", false);
            folderTabCounters = preferences.getBoolean("folderTabCounters", true);
            hideAllChatsTab = preferences.getBoolean("hideAllChatsTab", false);
            sectionRadius = Math.max(0, Math.min(SECTION_RADIUS_MAX, preferences.getInt("sectionRadius", 16)));
            dividerStyle = validDividerStyle(preferences.getInt("dividerStyle", DIVIDER_LINE));
            forceBlur = preferences.getBoolean("forceBlur", false);
            removeMessageTail = preferences.getBoolean("removeMessageTail", false);
            hideShareButton = preferences.getBoolean("hideShareButton", false);
            showResultsBeforeVoting = preferences.getBoolean("showResultsBeforeVoting", false);
            doubleTapSeekSeconds = validSeek(preferences.getInt("doubleTapSeekSeconds", 10));
            swipeToPip = preferences.getBoolean("swipeToPip", false);
            unmuteWithVolumeButtons = preferences.getBoolean("unmuteWithVolumeButtons", true);
            pauseVoiceOnMinimize = preferences.getBoolean("pauseVoiceOnMinimize", false);
            unlimitedRecentStickers = preferences.getBoolean("unlimitedRecentStickers", false);
            replyColors = preferences.getBoolean("replyColors", true);
            replyEmoji = preferences.getBoolean("replyEmoji", true);
            replyBackground = preferences.getBoolean("replyBackground", true);
            hideKeyboardOnScroll = preferences.getBoolean("hideKeyboardOnScroll", false);
            addCommaAfterMention = preferences.getBoolean("addCommaAfterMention", false);
            hideSendAsPeer = preferences.getBoolean("hideSendAsPeer", false);
            chatMenuDeleteOwn = preferences.getBoolean("chatMenuDeleteOwn", false);
            chatMenuClearDeleted = preferences.getBoolean("chatMenuClearDeleted", false);
            chatMenuHideTitle = preferences.getBoolean("chatMenuHideTitle", false);
            chatMenuGoToMessage = preferences.getBoolean("chatMenuGoToMessage", false);
            chatMenuLinkedChat = preferences.getBoolean("chatMenuLinkedChat", false);
            chatMenuBoost = preferences.getBoolean("chatMenuBoost", true);
            unlimitedSavedGifs = preferences.getBoolean("unlimitedSavedGifs", false);
            chatMenuToBeginning = preferences.getBoolean("chatMenuToBeginning", false);
            adminShortcutStatistics = preferences.getBoolean("adminShortcutStatistics", true);
            adminShortcutMembers = preferences.getBoolean("adminShortcutMembers", true);
            adminShortcutPermissions = preferences.getBoolean("adminShortcutPermissions", true);
            adminShortcutAdministrators = preferences.getBoolean("adminShortcutAdministrators", true);
            adminShortcutRecentActions = preferences.getBoolean("adminShortcutRecentActions", true);
            hideAttachMoney = preferences.getBoolean("hideAttachMoney", false);
            contentCapture = preferences.getBoolean("contentCapture", true);
            confirmBotCommands = preferences.getBoolean("confirmBotCommands", false);
            confirmRepeat = preferences.getBoolean("confirmRepeat", false);
            disableProximity = preferences.getBoolean("disableProximity", false);
            dontAutoPlayNextVoice = preferences.getBoolean("dontAutoPlayNextVoice", false);
            disableNotificationBubbles = preferences.getBoolean("disableNotificationBubbles", false);
            sortByUnread = preferences.getBoolean("sortByUnread", false);
            disableAvatarBlur = preferences.getBoolean("disableAvatarBlur", false);
            hideGroupSticker = preferences.getBoolean("hideGroupSticker", false);
            disableTrending = preferences.getBoolean("disableTrending", false);
            rememberAllReplies = preferences.getBoolean("rememberAllReplies", false);
            smallGifs = preferences.getBoolean("smallGifs", false);
            forwardedDate = preferences.getBoolean("forwardedDate", false);
            hideCallButton = preferences.getBoolean("hideCallButton", false);
            hideChannelSearchButton = preferences.getBoolean("hideChannelSearchButton", false);
            hideChannelGiftButton = preferences.getBoolean("hideChannelGiftButton", false);
            disableGlobalSearch = preferences.getBoolean("disableGlobalSearch", false);
            replaceEditedWithIcon = preferences.getBoolean("replaceEditedWithIcon", false);
            showOnlineStatus = preferences.getBoolean("showOnlineStatus", false);
            showForwardsCount = preferences.getBoolean("showForwardsCount", false);
            quickAdminShortcuts = preferences.getBoolean("quickAdminShortcuts", false);
            telegramAiEditor = preferences.getBoolean("telegramAiEditor", true);
            telegramAiSummary = preferences.getBoolean("telegramAiSummary", true);
            telegramAiIvSummary = preferences.getBoolean("telegramAiIvSummary", true);
            quickTransitionChannels = preferences.getBoolean("quickTransitionChannels", true);
            quickTransitionTopics = preferences.getBoolean("quickTransitionTopics", true);
            roundVideoFrontCamera = preferences.getBoolean("roundVideoFrontCamera", true);
            rememberLastCamera = preferences.getBoolean("rememberLastCamera", false);
            staticZoom = preferences.getBoolean("staticZoom", false);
            inlineUnits = preferences.getBoolean("inlineUnits", false);
            inlineTimeZones = preferences.getBoolean("inlineTimeZones", false);
            groupMessageMenu = preferences.getBoolean("groupMessageMenu", false);
            ghostNoRead = preferences.getBoolean("ghostNoRead", true);
            ghostNoReadStories = preferences.getBoolean("ghostNoReadStories", true);
            ghostNoOnline = preferences.getBoolean("ghostNoOnline", true);
            ghostNoTyping = preferences.getBoolean("ghostNoTyping", true);
            ghostOfflineAuto = preferences.getBoolean("ghostOfflineAuto", false);
            ghostReadOnInteraction = preferences.getBoolean("ghostReadOnInteraction", true);
            ghostStatusInTitle = preferences.getBoolean("ghostStatusInTitle", false);
            archiveTotalLimitMb = preferences.getInt("archiveTotalLimitMb", -1);
            deletedMarkIconColor = preferences.getInt("deletedMarkIconColor", DELETED_ICON_RED) == DELETED_ICON_TIME ? DELETED_ICON_TIME : DELETED_ICON_RED;
            saveReadDates = preferences.getBoolean("saveReadDates", true);
            saveLastOnline = preferences.getBoolean("saveLastOnline", true);
            deletedMarkIcon = preferences.getBoolean("deletedMarkIcon", false);
            feedPostsPerChannel = preferences.getInt("feedPostsPerChannel", 20);
            feedTab = preferences.getBoolean("feedTab", false);
            feedUnreadCounter = preferences.getBoolean("feedUnreadCounter", true);
            feedWidePosts = preferences.getBoolean("feedWidePosts", true);
            feedIncludeArchived = preferences.getBoolean("feedIncludeArchived", false);
            separateHeaders = preferences.getBoolean("separateHeaders", false);
            m3Loading = preferences.getBoolean("m3Loading", false);
            m3Slider = preferences.getBoolean("m3Slider", false);
            glassMessageMenu = preferences.getBoolean("glassMessageMenu", true);
            configLoaded = true;
        }
    }

    public static void toggleExtendedPins() {
        extendedPins = !extendedPins;
        putBoolean("extendedPins", extendedPins);
    }

    public static void toggleLocalFolders() {
        localFolders = !localFolders;
        putBoolean("localFolders", localFolders);
    }

    public static void toggleLastSeenExact() {
        lastSeenExact = !lastSeenExact;
        putBoolean("lastSeenExact", lastSeenExact);
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

    public static void toggleMusicPlayerMini() {
        musicPlayerMini = !musicPlayerMini;
        putBoolean("musicPlayerMini", musicPlayerMini);
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

    public static void setChannelBottomButton(int value) {
        channelBottomButton = value < CHANNEL_BOTTOM_MUTE || value > CHANNEL_BOTTOM_HIDE ? CHANNEL_BOTTOM_MUTE : value;
        putInt("channelBottomButton", channelBottomButton);
    }

    // Remember last camera: a switch of camera while recording a video message becomes the camera to start with
    public static void rememberRoundVideoCamera(boolean front) {
        if (rememberLastCamera && roundVideoFrontCamera != front) {
            roundVideoFrontCamera = front;
            putBoolean("roundVideoFrontCamera", front);
        }
    }

    public static void setRoundVideoFrontCamera(boolean front) {
        roundVideoFrontCamera = front;
        putBoolean("roundVideoFrontCamera", front);
    }

    public static void setArchiveTotalLimitMb(int mb) {
        archiveTotalLimitMb = mb < 0 ? -1 : mb;
        putInt("archiveTotalLimitMb", archiveTotalLimitMb);
    }

    public static void setDeletedMarkIconColor(int color) {
        deletedMarkIconColor = color == DELETED_ICON_TIME ? DELETED_ICON_TIME : DELETED_ICON_RED;
        putInt("deletedMarkIconColor", deletedMarkIconColor);
    }

    public static void setFeedPostsPerChannel(int count) {
        feedPostsPerChannel = count;
        putInt("feedPostsPerChannel", count);
    }

    public static void setSwipeAction(int value) {
        swipeAction = value < 0 || value > 5 ? 0 : value;
        putInt("swipeAction", swipeAction);
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

    public static void togglePluginsBackup() {
        pluginsBackup = !pluginsBackup;
        putBoolean("plugins_backup", pluginsBackup);
    }

    public static void setPluginsBackup(boolean value) {
        pluginsBackup = value;
        putBoolean("plugins_backup", pluginsBackup);
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

    public static void toggleVideoSubtitles() {
        videoSubtitles = !videoSubtitles;
        putBoolean("videoSubtitles", videoSubtitles);
    }

    public static void togglePersianCalendar() {
        persianCalendar = !persianCalendar;
        putBoolean("persianCalendar", persianCalendar);
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

    public static void setStickerShape(int shape) {
        stickerShape = shape < STICKER_SHAPE_DEFAULT || shape > STICKER_SHAPE_MESSAGE ? STICKER_SHAPE_DEFAULT : shape;
        putInt("stickerShape", stickerShape);
    }

    // Unlimited recent stickers: the list on this device keeps up to this many instead of Telegram's limit
    public static final int UNLIMITED_RECENT_STICKERS = 200;

    public static int recentStickersLimit(int telegramLimit) {
        return unlimitedRecentStickers ? Math.max(telegramLimit, UNLIMITED_RECENT_STICKERS) : telegramLimit;
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

    public static void setSaveFolder(String value) {
        String clean = MZGramSaveFolder.clean(value);
        saveFolder = clean.isEmpty() ? MZGramSaveFolder.TELEGRAM : clean;
        putString("saveFolder", saveFolder);
    }

    public static void setTitleText(int value) {
        titleText = Math.max(0, Math.min(5, value));
        putInt("titleText", titleText);
    }

    public static void setTitleTextCustom(String value) {
        titleTextCustom = value == null ? "" : value;
        putString("titleTextCustom", titleTextCustom);
    }

    public static void setSectionRadius(int value) {
        sectionRadius = Math.max(0, Math.min(SECTION_RADIUS_MAX, value));
        putInt("sectionRadius", sectionRadius);
    }

    public static void setDividerStyle(int value) {
        dividerStyle = validDividerStyle(value);
        putInt("dividerStyle", dividerStyle);
    }

    private static int validDividerStyle(int value) {
        return value == DIVIDER_HIDDEN || value == DIVIDER_SEGMENTS ? value : DIVIDER_LINE;
    }

    // No lines between rows: None, and Segments, whose rows are cards of their own
    public static boolean hideDividers() {
        return dividerStyle != DIVIDER_LINE;
    }

    // Section titles stand above their cards: the switch, or always with Segments
    public static boolean separatedHeaders() {
        return separateHeaders || dividerStyle == DIVIDER_SEGMENTS;
    }

    private static int validSeek(int seconds) {
        for (int s : DOUBLE_TAP_SEEK_SECONDS) {
            if (s == seconds) {
                return seconds;
            }
        }
        return 10;
    }

    public static void setDoubleTapSeekSeconds(int seconds) {
        doubleTapSeekSeconds = validSeek(seconds);
        putInt("doubleTapSeekSeconds", doubleTapSeekSeconds);
    }

    public static void setDownloadSpeedBoost(int value) {
        downloadSpeedBoost = Math.max(0, Math.min(2, value));
        putInt("downloadSpeedBoost", downloadSpeedBoost);
    }

    public static void setDoubleTapOther(int value) {
        doubleTapOther = value;
        preferences().edit().putInt("doubleTapOther", value).apply();
    }

    public static void toggleInAppVibration() {
        inAppVibration = !inAppVibration;
        putBoolean("inAppVibration", inAppVibration);
    }

    public static void toggleDisableNotificationDelay() {
        disableNotificationDelay = !disableNotificationDelay;
        putBoolean("disableNotificationDelay", disableNotificationDelay);
    }

    public static void toggleUploadSpeedBoost() {
        uploadSpeedBoost = !uploadSpeedBoost;
        putBoolean("uploadSpeedBoost", uploadSpeedBoost);
    }

    public static void toggleRelativeLastSeen() {
        relativeLastSeen = !relativeLastSeen;
        putBoolean("relativeLastSeen", relativeLastSeen);
    }

    public static void toggleHidePhoneNumber() {
        hidePhoneNumber = !hidePhoneNumber;
        putBoolean("hidePhoneNumber", hidePhoneNumber);
    }

    public static void toggleHideArchiveFolder() {
        hideArchiveFolder = !hideArchiveFolder;
        putBoolean("hideArchiveFolder", hideArchiveFolder);
    }

    public static void toggleDisableUnarchiveSwipe() {
        disableUnarchiveSwipe = !disableUnarchiveSwipe;
        putBoolean("disableUnarchiveSwipe", disableUnarchiveSwipe);
    }

    public static void toggleCenterTitle() {
        centerTitle = !centerTitle;
        putBoolean("centerTitle", centerTitle);
    }

    public static void toggleHideActionBarStatus() {
        hideActionBarStatus = !hideActionBarStatus;
        putBoolean("hideActionBarStatus", hideActionBarStatus);
    }

    public static void toggleHideFloatingButton() {
        hideFloatingButton = !hideFloatingButton;
        putBoolean("hideFloatingButton", hideFloatingButton);
    }

    public static void toggleHideDialogsSearchBar() {
        hideDialogsSearchBar = !hideDialogsSearchBar;
        putBoolean("hideDialogsSearchBar", hideDialogsSearchBar);
    }

    public static void toggleForceSnow() {
        forceSnow = !forceSnow;
        putBoolean("forceSnow", forceSnow);
    }

    public static void toggleSenderMiniAvatars() {
        senderMiniAvatars = !senderMiniAvatars;
        putBoolean("senderMiniAvatars", senderMiniAvatars);
    }

    public static void toggleCustomChatThemes() {
        customChatThemes = !customChatThemes;
        putBoolean("customChatThemes", customChatThemes);
    }

    public static void toggleSingleCornerRadius() {
        singleCornerRadius = !singleCornerRadius;
        putBoolean("singleCornerRadius", singleCornerRadius);
        MZGramAvatarShape.changed();
    }

    public static void toggleFolderTabCounters() {
        folderTabCounters = !folderTabCounters;
        putBoolean("folderTabCounters", folderTabCounters);
    }

    public static void toggleHideAllChatsTab() {
        hideAllChatsTab = !hideAllChatsTab;
        putBoolean("hideAllChatsTab", hideAllChatsTab);
    }

    public static void toggleForceBlur() {
        forceBlur = !forceBlur;
        putBoolean("forceBlur", forceBlur);
    }

    public static void toggleRemoveMessageTail() {
        removeMessageTail = !removeMessageTail;
        putBoolean("removeMessageTail", removeMessageTail);
    }

    public static void toggleHideShareButton() {
        hideShareButton = !hideShareButton;
        putBoolean("hideShareButton", hideShareButton);
    }

    public static void toggleShowResultsBeforeVoting() {
        showResultsBeforeVoting = !showResultsBeforeVoting;
        putBoolean("showResultsBeforeVoting", showResultsBeforeVoting);
    }

    public static void toggleSwipeToPip() {
        swipeToPip = !swipeToPip;
        putBoolean("swipeToPip", swipeToPip);
    }

    public static void toggleUnmuteWithVolumeButtons() {
        unmuteWithVolumeButtons = !unmuteWithVolumeButtons;
        putBoolean("unmuteWithVolumeButtons", unmuteWithVolumeButtons);
    }

    public static void togglePauseVoiceOnMinimize() {
        pauseVoiceOnMinimize = !pauseVoiceOnMinimize;
        putBoolean("pauseVoiceOnMinimize", pauseVoiceOnMinimize);
    }

    public static void toggleUnlimitedRecentStickers() {
        unlimitedRecentStickers = !unlimitedRecentStickers;
        putBoolean("unlimitedRecentStickers", unlimitedRecentStickers);
    }

    public static void toggleReplyColors() {
        replyColors = !replyColors;
        putBoolean("replyColors", replyColors);
    }

    public static void toggleReplyEmoji() {
        replyEmoji = !replyEmoji;
        putBoolean("replyEmoji", replyEmoji);
    }

    public static void toggleReplyBackground() {
        replyBackground = !replyBackground;
        putBoolean("replyBackground", replyBackground);
    }

    public static void toggleHideKeyboardOnScroll() {
        hideKeyboardOnScroll = !hideKeyboardOnScroll;
        putBoolean("hideKeyboardOnScroll", hideKeyboardOnScroll);
    }

    public static void toggleAddCommaAfterMention() {
        addCommaAfterMention = !addCommaAfterMention;
        putBoolean("addCommaAfterMention", addCommaAfterMention);
    }

    public static void toggleAdminShortcutRecentActions() {
        adminShortcutRecentActions = !adminShortcutRecentActions;
        putBoolean("adminShortcutRecentActions", adminShortcutRecentActions);
    }

    public static void toggleAdminShortcutAdministrators() {
        adminShortcutAdministrators = !adminShortcutAdministrators;
        putBoolean("adminShortcutAdministrators", adminShortcutAdministrators);
    }

    public static void toggleAdminShortcutPermissions() {
        adminShortcutPermissions = !adminShortcutPermissions;
        putBoolean("adminShortcutPermissions", adminShortcutPermissions);
    }

    public static void toggleAdminShortcutMembers() {
        adminShortcutMembers = !adminShortcutMembers;
        putBoolean("adminShortcutMembers", adminShortcutMembers);
    }

    public static void toggleAdminShortcutStatistics() {
        adminShortcutStatistics = !adminShortcutStatistics;
        putBoolean("adminShortcutStatistics", adminShortcutStatistics);
    }

    public static void toggleHideSendAsPeer() {
        hideSendAsPeer = !hideSendAsPeer;
        putBoolean("hideSendAsPeer", hideSendAsPeer);
    }

    public static void toggleChatMenuDeleteOwn() {
        chatMenuDeleteOwn = !chatMenuDeleteOwn;
        putBoolean("chatMenuDeleteOwn", chatMenuDeleteOwn);
    }

    public static void toggleChatMenuClearDeleted() {
        chatMenuClearDeleted = !chatMenuClearDeleted;
        putBoolean("chatMenuClearDeleted", chatMenuClearDeleted);
    }

    public static void toggleChatMenuHideTitle() {
        chatMenuHideTitle = !chatMenuHideTitle;
        putBoolean("chatMenuHideTitle", chatMenuHideTitle);
    }

    public static void toggleChatMenuGoToMessage() {
        chatMenuGoToMessage = !chatMenuGoToMessage;
        putBoolean("chatMenuGoToMessage", chatMenuGoToMessage);
    }

    public static void toggleChatMenuLinkedChat() {
        chatMenuLinkedChat = !chatMenuLinkedChat;
        putBoolean("chatMenuLinkedChat", chatMenuLinkedChat);
    }

    public static void toggleChatMenuBoost() {
        chatMenuBoost = !chatMenuBoost;
        putBoolean("chatMenuBoost", chatMenuBoost);
    }

    public static void toggleUnlimitedSavedGifs() {
        unlimitedSavedGifs = !unlimitedSavedGifs;
        putBoolean("unlimitedSavedGifs", unlimitedSavedGifs);
    }

    public static void toggleChatMenuToBeginning() {
        chatMenuToBeginning = !chatMenuToBeginning;
        putBoolean("chatMenuToBeginning", chatMenuToBeginning);
    }

    public static void toggleHideAttachMoney() {
        hideAttachMoney = !hideAttachMoney;
        putBoolean("hideAttachMoney", hideAttachMoney);
    }

    public static void toggleContentCapture() {
        contentCapture = !contentCapture;
        putBoolean("contentCapture", contentCapture);
    }

    public static void toggleConfirmBotCommands() {
        confirmBotCommands = !confirmBotCommands;
        putBoolean("confirmBotCommands", confirmBotCommands);
    }

    public static void toggleConfirmRepeat() {
        confirmRepeat = !confirmRepeat;
        putBoolean("confirmRepeat", confirmRepeat);
    }

    public static void toggleDisableProximity() {
        disableProximity = !disableProximity;
        putBoolean("disableProximity", disableProximity);
    }

    public static void toggleDontAutoPlayNextVoice() {
        dontAutoPlayNextVoice = !dontAutoPlayNextVoice;
        putBoolean("dontAutoPlayNextVoice", dontAutoPlayNextVoice);
    }

    public static void toggleDisableNotificationBubbles() {
        disableNotificationBubbles = !disableNotificationBubbles;
        putBoolean("disableNotificationBubbles", disableNotificationBubbles);
    }

    public static void toggleSortByUnread() {
        sortByUnread = !sortByUnread;
        putBoolean("sortByUnread", sortByUnread);
    }

    public static void toggleDisableAvatarBlur() {
        disableAvatarBlur = !disableAvatarBlur;
        putBoolean("disableAvatarBlur", disableAvatarBlur);
    }

    public static void toggleHideGroupSticker() {
        hideGroupSticker = !hideGroupSticker;
        putBoolean("hideGroupSticker", hideGroupSticker);
    }

    public static void toggleDisableTrending() {
        disableTrending = !disableTrending;
        putBoolean("disableTrending", disableTrending);
    }

    public static void toggleRememberAllReplies() {
        rememberAllReplies = !rememberAllReplies;
        putBoolean("rememberAllReplies", rememberAllReplies);
    }

    public static void toggleSmallGifs() {
        smallGifs = !smallGifs;
        putBoolean("smallGifs", smallGifs);
    }

    public static void toggleForwardedDate() {
        forwardedDate = !forwardedDate;
        putBoolean("forwardedDate", forwardedDate);
    }

    public static void toggleHideCallButton() {
        hideCallButton = !hideCallButton;
        putBoolean("hideCallButton", hideCallButton);
    }

    public static void toggleHideChannelSearchButton() {
        hideChannelSearchButton = !hideChannelSearchButton;
        putBoolean("hideChannelSearchButton", hideChannelSearchButton);
    }

    public static void toggleHideChannelGiftButton() {
        hideChannelGiftButton = !hideChannelGiftButton;
        putBoolean("hideChannelGiftButton", hideChannelGiftButton);
    }

    public static void toggleDisableGlobalSearch() {
        disableGlobalSearch = !disableGlobalSearch;
        putBoolean("disableGlobalSearch", disableGlobalSearch);
    }

    public static void toggleReplaceEditedWithIcon() {
        replaceEditedWithIcon = !replaceEditedWithIcon;
        putBoolean("replaceEditedWithIcon", replaceEditedWithIcon);
    }

    public static void toggleShowOnlineStatus() {
        showOnlineStatus = !showOnlineStatus;
        putBoolean("showOnlineStatus", showOnlineStatus);
    }

    public static void toggleShowForwardsCount() {
        showForwardsCount = !showForwardsCount;
        putBoolean("showForwardsCount", showForwardsCount);
    }

    public static void toggleQuickAdminShortcuts() {
        quickAdminShortcuts = !quickAdminShortcuts;
        putBoolean("quickAdminShortcuts", quickAdminShortcuts);
    }

    public static void toggleTelegramAiEditor() {
        telegramAiEditor = !telegramAiEditor;
        putBoolean("telegramAiEditor", telegramAiEditor);
    }

    public static void toggleTelegramAiSummary() {
        telegramAiSummary = !telegramAiSummary;
        putBoolean("telegramAiSummary", telegramAiSummary);
    }

    public static void toggleTelegramAiIvSummary() {
        telegramAiIvSummary = !telegramAiIvSummary;
        putBoolean("telegramAiIvSummary", telegramAiIvSummary);
    }

    public static void toggleQuickTransitionChannels() {
        quickTransitionChannels = !quickTransitionChannels;
        putBoolean("quickTransitionChannels", quickTransitionChannels);
    }

    public static void toggleQuickTransitionTopics() {
        quickTransitionTopics = !quickTransitionTopics;
        putBoolean("quickTransitionTopics", quickTransitionTopics);
    }

    public static void toggleRememberLastCamera() {
        rememberLastCamera = !rememberLastCamera;
        putBoolean("rememberLastCamera", rememberLastCamera);
    }

    public static void toggleStaticZoom() {
        staticZoom = !staticZoom;
        putBoolean("staticZoom", staticZoom);
    }

    public static void toggleInlineUnits() {
        inlineUnits = !inlineUnits;
        putBoolean("inlineUnits", inlineUnits);
    }

    public static void toggleInlineTimeZones() {
        inlineTimeZones = !inlineTimeZones;
        putBoolean("inlineTimeZones", inlineTimeZones);
    }

    public static void toggleGroupMessageMenu() {
        groupMessageMenu = !groupMessageMenu;
        putBoolean("groupMessageMenu", groupMessageMenu);
    }

    public static void toggleGhostNoRead() {
        ghostNoRead = !ghostNoRead;
        putBoolean("ghostNoRead", ghostNoRead);
    }

    public static void toggleGhostNoReadStories() {
        ghostNoReadStories = !ghostNoReadStories;
        putBoolean("ghostNoReadStories", ghostNoReadStories);
    }

    public static void toggleGhostNoOnline() {
        ghostNoOnline = !ghostNoOnline;
        putBoolean("ghostNoOnline", ghostNoOnline);
    }

    public static void toggleGhostNoTyping() {
        ghostNoTyping = !ghostNoTyping;
        putBoolean("ghostNoTyping", ghostNoTyping);
    }

    public static void toggleGhostOfflineAuto() {
        ghostOfflineAuto = !ghostOfflineAuto;
        putBoolean("ghostOfflineAuto", ghostOfflineAuto);
    }

    public static void toggleGhostReadOnInteraction() {
        ghostReadOnInteraction = !ghostReadOnInteraction;
        putBoolean("ghostReadOnInteraction", ghostReadOnInteraction);
    }

    public static void toggleGhostStatusInTitle() {
        ghostStatusInTitle = !ghostStatusInTitle;
        putBoolean("ghostStatusInTitle", ghostStatusInTitle);
    }

    public static void toggleSaveReadDates() {
        saveReadDates = !saveReadDates;
        putBoolean("saveReadDates", saveReadDates);
    }

    public static void toggleSaveLastOnline() {
        saveLastOnline = !saveLastOnline;
        putBoolean("saveLastOnline", saveLastOnline);
    }

    public static void toggleDeletedMarkIcon() {
        deletedMarkIcon = !deletedMarkIcon;
        putBoolean("deletedMarkIcon", deletedMarkIcon);
    }

    public static void toggleFeedTab() {
        feedTab = !feedTab;
        putBoolean("feedTab", feedTab);
    }

    public static void toggleFeedUnreadCounter() {
        feedUnreadCounter = !feedUnreadCounter;
        putBoolean("feedUnreadCounter", feedUnreadCounter);
    }

    public static void toggleFeedWidePosts() {
        feedWidePosts = !feedWidePosts;
        putBoolean("feedWidePosts", feedWidePosts);
    }

    public static void toggleFeedIncludeArchived() {
        feedIncludeArchived = !feedIncludeArchived;
        putBoolean("feedIncludeArchived", feedIncludeArchived);
    }

    public static void toggleSeparateHeaders() {
        separateHeaders = !separateHeaders;
        putBoolean("separateHeaders", separateHeaders);
    }

    public static void toggleM3Loading() {
        m3Loading = !m3Loading;
        putBoolean("m3Loading", m3Loading);
    }

    public static void toggleM3Slider() {
        m3Slider = !m3Slider;
        putBoolean("m3Slider", m3Slider);
    }

    public static void toggleGlassMessageMenu() {
        glassMessageMenu = !glassMessageMenu;
        putBoolean("glassMessageMenu", glassMessageMenu);
    }
}
