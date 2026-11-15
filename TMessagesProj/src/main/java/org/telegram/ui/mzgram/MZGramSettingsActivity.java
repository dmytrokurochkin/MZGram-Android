/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram: the app with its version, a list of categories and
 * links; tapping a category opens its own page with that category's
 * switches, grouped in cards under headers. Every MZGram feature has its
 * switch on one of them.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramDoH;
import org.telegram.messenger.mzgram.translate.MZGramTranslator;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MZGramSettingsActivity extends UniversalFragment {

    // Row id of the entry in Settings; clear of the ids SettingsActivity uses.
    public static final int SETTINGS_ROW_ID = 1000;

    private static final int BUTTON_DISABLE_NUMBER_ROUNDING = 2;
    private static final int BUTTON_FORMAT_TIME_WITH_SECONDS = 3;
    private static final int BUTTON_ASK_BEFORE_CALL = 4;
    private static final int BUTTON_HIDE_STORIES = 5;
    private static final int BUTTON_DISABLE_GREETING_STICKER = 6;
    private static final int BUTTON_CHANNEL_BOTTOM_BUTTON = 7;
    private static final int BUTTON_OPEN_ARCHIVE_ON_PULL = 8;
    private static final int BUTTON_DISABLE_INSTANT_CAMERA = 9;
    private static final int BUTTON_PREFER_ORIGINAL_QUALITY = 10;
    private static final int BUTTON_AUTO_PAUSE_VIDEO = 11;
    private static final int BUTTON_SHOW_COPY_PHOTO = 12;
    private static final int BUTTON_SHOW_DELETE_DOWNLOADED_FILE = 13;
    private static final int BUTTON_SHOW_ADD_TO_SAVED_MESSAGES = 14;
    private static final int BUTTON_SHOW_SET_REMINDER = 15;
    private static final int BUTTON_SHOW_REPEAT = 16;
    private static final int BUTTON_SHOW_OPEN_IN = 17;
    private static final int BUTTON_SHOW_MESSAGE_DETAILS = 18;
    private static final int BUTTON_SHOW_QR_CODE = 19;
    private static final int BUTTON_SHOW_NO_QUOTE_FORWARD = 20;
    private static final int BUTTON_PREDICTIVE_BACK_ANIMATION = 21;
    private static final int BUTTON_GOOEY_AVATAR_ANIMATION = 22;
    private static final int BUTTON_BOTTOM_NAV_MODE = 23;
    private static final int BUTTON_GHOST_MODE = 25;
    private static final int BUTTON_SAVE_DELETED_MESSAGES = 26;
    private static final int BUTTON_SAVE_EDIT_HISTORY = 43;
    private static final int BUTTON_SAVE_ARCHIVE_MEDIA = 44;
    private static final int BUTTON_SAVE_FORMATTING = 45;
    private static final int BUTTON_SAVE_REACTIONS = 46;
    private static final int BUTTON_SAVE_FOR_BOTS = 47;
    private static final int BUTTON_DELETED_MARK = 48;
    private static final int BUTTON_EDITED_MARK = 49;
    private static final int BUTTON_SEMI_TRANSPARENT_DELETED = 50;
    private static final int BUTTON_ERASE_LOCAL_DATABASE = 51;
    private static final int BUTTON_HIDE_CAMERA_TILE = 52;
    private static final int BUTTON_CHECK_UPDATES = 53;
    private static final int BUTTON_ACCOUNTS_IN_MENU = 54;
    private static final int BUTTON_CHECK_UPDATES_NOW = 55;
    private static final int BUTTON_LANGUAGE = 56;
    private static final int BUTTON_DELETE_FOR_ALL_BY_DEFAULT = 57;
    private static final int BUTTON_CONFIRM_AV_MESSAGE = 28;
    private static final int BUTTON_MEDIA_PREVIEW_ON_LONG_PRESS = 29;
    private static final int BUTTON_GHOST_AUTO_DELAY_SEND = 30;
    private static final int BUTTON_GHOST_SILENT_SEND = 31;
    private static final int BUTTON_OFFER_GHOST_MODE_BEFORE_STORIES = 32;
    private static final int BUTTON_WIPE_ARCHIVE = 33;
    private static final int BUTTON_EXPORT_ARCHIVE = 35;
    private static final int BUTTON_IMPORT_ARCHIVE = 36;
    private static final int REQUEST_CODE_IMPORT_ARCHIVE = 8842;
    private static final int REQUEST_CODE_EXPORT_ARCHIVE = 8843;
    private static final int REQUEST_CODE_IMPORT_FOREIGN_ARCHIVE = 8844;
    private static final int REQUEST_CODE_APP_FONT = 8845;
    private static final int REQUEST_CODE_EXPORT_SETTINGS = 8848;
    private static final int REQUEST_CODE_IMPORT_SETTINGS = 8849;
    private static final String EXPORT_FILE_NAME = "mzgram_archive_export.db";
    private static final int BUTTON_DISABLE_SPONSORED_MESSAGES = 37;
    private static final int BUTTON_STRIP_ZALGO_TEXT = 38;
    private static final int BUTTON_FOLDER_TABS_AT_BOTTOM = 41;
    private static final int BUTTON_SAVE_PROTECTED_CONTENT = 42;
    private static final int BUTTON_SHOW_ASK_AI = 58;
    private static final int BUTTON_AI_PROVIDER = 59;
    private static final int BUTTON_AI_SIGN_OUT = 60;
    // Interface > Hide in Telegram's Settings, in MZGramSettingsRows' order
    private static final int BUTTON_HIDE_ROW_PREMIUM = 61;
    private static final int BUTTON_HIDE_ROW_STARS = 62;
    private static final int BUTTON_HIDE_ROW_EARNINGS = 63;
    private static final int BUTTON_HIDE_ROW_BUSINESS = 64;
    private static final int BUTTON_HIDE_ROW_GIFT = 65;
    private static final int BUTTON_HIDE_ROW_WALLET = 66;
    private static final int BUTTON_HIDE_ROW_ASK = 67;
    private static final int BUTTON_HIDE_ROW_FAQ = 68;
    private static final int BUTTON_HIDE_ROW_FEATURES = 69;
    private static final int BUTTON_HIDE_ROW_POLICY = 70;
    private static final int[] HIDE_ROW_BUTTONS = {
            BUTTON_HIDE_ROW_PREMIUM, BUTTON_HIDE_ROW_STARS, BUTTON_HIDE_ROW_EARNINGS, BUTTON_HIDE_ROW_BUSINESS,
            BUTTON_HIDE_ROW_GIFT, BUTTON_HIDE_ROW_WALLET, BUTTON_HIDE_ROW_ASK, BUTTON_HIDE_ROW_FAQ,
            BUTTON_HIDE_ROW_FEATURES, BUTTON_HIDE_ROW_POLICY,
    };
    private static final int[] HIDE_ROW_TITLES = {
            R.string.MZGramHideRowPremium, R.string.MZGramHideRowStars, R.string.MZGramHideRowEarnings,
            R.string.MZGramHideRowBusiness, R.string.MZGramHideRowGift, R.string.MZGramHideRowWallet,
            R.string.MZGramHideRowAsk, R.string.MZGramHideRowFaq, R.string.MZGramHideRowFeatures,
            R.string.MZGramHideRowPolicy,
    };
    // AI > Buttons in the chat list menu, in MZGramAiRules' order
    private static final int BUTTON_AI_MENU_GEMINI = 71;
    private static final int BUTTON_AI_MENU_CLAUDE = 72;
    private static final int BUTTON_AI_MENU_CHATGPT = 73;
    private static final int BUTTON_AI_MENU_DEEPSEEK = 74;
    private static final int BUTTON_AI_MENU_GROK = 75;
    private static final int BUTTON_SAVE_FROM_USERS = 76;
    private static final int BUTTON_SAVE_IN_GROUPS = 77;
    private static final int BUTTON_SAVE_IN_CHANNELS = 78;
    // Download in advance: users, bots, groups, channels (MZGramKeepFrom)
    private static final int BUTTON_ARCHIVE_LIMIT_USERS = 79;
    private static final int BUTTON_ARCHIVE_LIMIT_BOTS = 80;
    private static final int BUTTON_ARCHIVE_LIMIT_GROUPS = 81;
    private static final int BUTTON_ARCHIVE_LIMIT_CHANNELS = 82;
    private static final int ARCHIVE_LIMIT_FIRST = BUTTON_ARCHIVE_LIMIT_USERS;
    private static final int BUTTON_ARCHIVE_COUNTERS_RESET = 83;
    private static final int BUTTON_PEER_ID = 84;
    // Privacy topic (MZGramPrivacy)
    private static final int BUTTON_CONFIRM_INTERNAL_LINKS = 85;
    private static final int BUTTON_DISABLE_READ_STICKERS = 86;
    private static final int BUTTON_DISABLE_LINK_PREVIEWS = 87;
    private static final int BUTTON_DISABLE_CLOUD_DRAFTS = 88;
    private static final int BUTTON_LOCK_SCREEN_VOICE_PROTECTION = 89;
    private static final int BUTTON_GHOST_MODE_EXCLUSIONS = 90;
    private static final int BUTTON_OWN_LAST_SEEN = 91;
    private static final int BUTTON_OWN_LAST_SEEN_VALUE = 92;
    private static final int BUTTON_KEEP_GROUP_READS = 93;
    private static final int BUTTON_BOT_START_CONFIRM = 94;
    private static final int BUTTON_REDUCE_NETWORK_TRACKING = 95;
    private static final int BUTTON_PASSCODE_ACCOUNTS = 96;
    private static final int BUTTON_PASSCODE_ACCOUNTS_SETUP = 97;
    // Archive: rules per chat (MZGramSaveExclusions)
    private static final int BUTTON_SAVE_EXCLUSIONS = 98;
    // Archive: replies to deleted messages (MZGramDeletedReplies)
    private static final int BUTTON_KEPT_REPLY_PREVIEWS = 99;
    private static final int BUTTON_REPLY_TO_DELETED_AS_QUOTE = 100;
    // Archive: a chat's deleted messages list (MZGramDeletedMessagesActivity)
    private static final int BUTTON_DELETED_MESSAGES_LIST = 101;
    // Archive: Deleted chats (MZGramDeletedDialogsActivity)
    private static final int BUTTON_DELETED_DIALOGS_LIST = 102;
    private static final int BUTTON_DELETED_DIALOGS_OPEN = 103;
    private static final int BUTTON_DELETED_DIALOGS_IN_MAIN_LIST = 193;
    // Appearance: Material 3 switches and card-based lists
    private static final int BUTTON_M3_SWITCHES = 194;
    private static final int BUTTON_CARDS_DESIGN = 195;
    // Media: profile music card
    private static final int BUTTON_PROFILE_MUSIC_CARD = 196;
    // Third-party network services
    // 197 was the DoH provider: no request used it, the row is gone (MZGramDoH stays for later)
    private static final int BUTTON_OPENFREEMAP_PREVIEW = 198;
    // Translation and AI suite
    private static final int BUTTON_TRANSLATION_AI = 199;
    // Extended peripherals (Point 13)
    private static final int BUTTON_EXTENDED_PERIPHERALS = 200;
    // Plugins backup in settings
    private static final int BUTTON_PLUGINS_BACKUP = 201;
    // Media: video player subtitles (N12)
    private static final int BUTTON_VIDEO_SUBTITLES = 202;
    // Interface: Solar Hijri (Persian) calendar (N14)
    private static final int BUTTON_PERSIAN_CALENDAR = 203;
    // Archive: import another client's message database (MZGramForeignArchiveImport)
    private static final int BUTTON_IMPORT_FOREIGN_ARCHIVE = 104;
    // Archive: forward kept deleted messages as copies (MZGramProtectedContent)
    private static final int BUTTON_FORWARD_DELETED_AS_COPY = 105;
    // messages (topic Chats)
    private static final int BUTTON_FOLD_FANCY_FONTS = 106;
    private static final int BUTTON_CHARACTER_COUNTER = 107;
    private static final int BUTTON_SEARCH_OPERATORS = 108;
    private static final int BUTTON_CLEAN_LINKS = 109;
    private static final int BUTTON_COPY_PLAIN = 113;
    private static final int BUTTON_INLINE_MATH = 114;
    private static final int BUTTON_STICKER_SHAPE = 142;
    private static final int BUTTON_STICKER_TIME = 143;
    private static final int BUTTON_GRANULAR_PERMISSIONS = 141;
    private static final int BUTTON_SPLIT_GIF_INLINE = 144;
    private static final int BUTTON_WIDE_CHANNEL_POSTS = 145;
    private static final int BUTTON_ACCOUNT_ORDER = 146;
    private static final int BUTTON_RECENT_CHATS_ON_BACK = 147;
    private static final int BUTTON_GLASS_OUTLINE = 148;
    private static final int BUTTON_FOLDER_TAB_ICONS = 149;
    private static final int BUTTON_APP_FONT = 150;
    private static final int BUTTON_VOICE_QUALITY = 151;
    private static final int BUTTON_LOSSLESS_PHOTO_CROP = 152;
    private static final int BUTTON_CRASH_REPORTS = 153;
    private static final int BUTTON_JANK_PROFILER = 154;
    private static final int BUTTON_JANK_REPORT = 155;
    private static final int BUTTON_ENERGY_PROFILER = 156;
    private static final int BUTTON_ENERGY_REPORT = 157;
    private static final int BUTTON_PYTHON_RUNTIME = 158;
    private static final int BUTTON_STORIES_NO_BACKGROUND_LOAD = 160;
    private static final int BUTTON_LINKED_EMOJI = 139;
    private static final int BUTTON_LINK_TYPEFACE = 140;
    private static final int BUTTON_PLAIN_CODE = 130;
    private static final int BUTTON_HEX_COLOR = 129;
    private static final int BUTTON_SCHEDULE_SLIDER = 127;
    private static final int BUTTON_PINNED_REACTIONS = 128;
    private static final int BUTTON_TEXT_STYLE_MENU = 131;
    private static final int BUTTON_HIDE_REACTIONS_PRIVATE = 132;
    private static final int BUTTON_HIDE_REACTIONS_GROUPS = 133;
    private static final int BUTTON_HIDE_REACTIONS_CHANNELS = 134;
    private static final int BUTTON_HIDDEN_REACTIONS = 135;
    private static final int BUTTON_SHADOW_BAN = 136;
    private static final int BUTTON_HIDE_BLOCKED_IN_GROUPS = 137;
    private static final int BUTTON_SHADOW_BAN_LIST = 138;
    private static final int BUTTON_REGEX_FILTERS = 161;
    private static final int BUTTON_REGEX_FILTERS_RULES = 162;
    private static final int BUTTON_CUSTOM_DRAWER = 163;
    private static final int BUTTON_DRAWER_ITEMS = 164;
    private static final int BUTTON_DRAWER_SHOW_QR = 165;
    private static final int BUTTON_DRAWER_LARGE_AVATAR = 166;
    private static final int BUTTON_DRAWER_HEADER = 167;
    private static final int BUTTON_DRAWER_GLASS = 168;
    private static final int BUTTON_EXTENDED_PINS = 169;
    private static final int BUTTON_LOCAL_FOLDERS = 204;
    private static final int BUTTON_LAST_SEEN_EXACT = 205;
    // The message menu page, from its row in Chats
    private static final int BUTTON_MESSAGE_MENU = 206;
    private static final int BUTTON_IN_APP_VIBRATION = 207;
    private static final int BUTTON_DISABLE_NOTIFICATION_DELAY = 208;
    private static final int BUTTON_UPLOAD_SPEED_BOOST = 209;
    private static final int BUTTON_SAVE_FOLDER = 210;
    private static final int BUTTON_RELATIVE_LAST_SEEN = 211;
    private static final int BUTTON_HIDE_PHONE_NUMBER = 212;
    private static final int BUTTON_HIDE_ARCHIVE_FOLDER = 213;
    private static final int BUTTON_DISABLE_UNARCHIVE_SWIPE = 214;
    private static final int BUTTON_CENTER_TITLE = 215;
    private static final int BUTTON_HIDE_ACTION_BAR_STATUS = 216;
    private static final int BUTTON_HIDE_FLOATING_BUTTON = 217;
    private static final int BUTTON_HIDE_DIALOGS_SEARCH_BAR = 218;
    private static final int BUTTON_FORCE_SNOW = 219;
    private static final int BUTTON_SENDER_MINI_AVATARS = 220;
    private static final int BUTTON_TITLE_TEXT = 221;
    private static final int BUTTON_CUSTOM_CHAT_THEMES = 222;
    private static final int BUTTON_SINGLE_CORNER_RADIUS = 223;
    private static final int BUTTON_FOLDER_TAB_COUNTERS = 224;
    private static final int BUTTON_HIDE_ALL_CHATS_TAB = 225;
    private static final int BUTTON_DIVIDER_STYLE = 226;
    private static final int BUTTON_FORCE_BLUR = 227;
    private static final int BUTTON_REMOVE_MESSAGE_TAIL = 228;
    private static final int BUTTON_HIDE_SHARE_BUTTON = 229;
    private static final int BUTTON_SHOW_RESULTS_BEFORE_VOTING = 230;
    private static final int BUTTON_DOUBLE_TAP_SEEK = 231;
    private static final int BUTTON_SWIPE_TO_PIP = 232;
    private static final int BUTTON_UNMUTE_WITH_VOLUME_BUTTONS = 233;
    private static final int BUTTON_PAUSE_VOICE_ON_MINIMIZE = 234;
    private static final int BUTTON_UNLIMITED_RECENT_STICKERS = 235;
    private static final int BUTTON_REPLY_COLORS = 236;
    private static final int BUTTON_REPLY_EMOJI = 237;
    private static final int BUTTON_REPLY_BACKGROUND = 238;
    private static final int BUTTON_SWIPE_ACTION = 239;
    private static final int BUTTON_HIDE_KEYBOARD_ON_SCROLL = 240;
    private static final int BUTTON_ADD_COMMA_AFTER_MENTION = 241;
    private static final int BUTTON_HIDE_SEND_AS_PEER = 242;
    private static final int BUTTON_CHAT_MENU_DELETE_OWN = 314;
    private static final int BUTTON_CHAT_MENU_CLEAR_DELETED = 313;
    private static final int BUTTON_CHAT_MENU_HIDE_TITLE = 312;
    private static final int BUTTON_CHAT_MENU_GO_TO_MESSAGE = 311;
    private static final int BUTTON_CHAT_MENU_LINKED_CHAT = 310;
    private static final int BUTTON_CHAT_MENU_BOOST = 309;
    private static final int BUTTON_UNLIMITED_SAVED_GIFS = 308;
    private static final int BUTTON_ADMIN_SHORTCUT_STATISTICS = 307;
    private static final int BUTTON_ADMIN_SHORTCUT_MEMBERS = 306;
    private static final int BUTTON_ADMIN_SHORTCUT_PERMISSIONS = 305;
    private static final int BUTTON_ADMIN_SHORTCUT_ADMINS = 304;
    private static final int BUTTON_ADMIN_SHORTCUT_RECENT = 303;
    private static final int BUTTON_CHAT_MENU_TO_BEGINNING = 302;
    private static final int BUTTON_HIDE_ATTACH_MONEY = 301;
    private static final int BUTTON_CONTENT_CAPTURE = 300;
    private static final int BUTTON_CONFIRM_BOT_COMMANDS = 299;
    private static final int BUTTON_CONFIRM_REPEAT = 298;
    private static final int BUTTON_DISABLE_PROXIMITY = 297;
    private static final int BUTTON_DONT_AUTOPLAY_NEXT_VOICE = 296;
    private static final int BUTTON_DISABLE_NOTIFICATION_BUBBLES = 295;
    private static final int BUTTON_SORT_BY_UNREAD = 294;
    private static final int BUTTON_DISABLE_AVATAR_BLUR = 293;
    private static final int BUTTON_HIDE_GROUP_STICKER = 292;
    private static final int BUTTON_DISABLE_TRENDING = 291;
    private static final int BUTTON_REMEMBER_ALL_REPLIES = 290;
    private static final int BUTTON_SMALL_GIFS = 289;
    private static final int BUTTON_FORWARDED_DATE = 288;
    private static final int BUTTON_HIDE_CALL_BUTTON = 287;
    private static final int BUTTON_HIDE_CHANNEL_SEARCH = 286;
    private static final int BUTTON_HIDE_CHANNEL_GIFT = 285;
    private static final int BUTTON_DISABLE_GLOBAL_SEARCH = 284;
    private static final int BUTTON_REPLACE_EDITED_WITH_ICON = 243;
    private static final int BUTTON_SHOW_ONLINE_STATUS = 244;
    private static final int BUTTON_SHOW_FORWARDS_COUNT = 245;
    private static final int BUTTON_QUICK_ADMIN_SHORTCUTS = 246;
    private static final int BUTTON_TELEGRAM_AI_EDITOR = 247;
    private static final int BUTTON_TELEGRAM_AI_SUMMARY = 248;
    private static final int BUTTON_TELEGRAM_AI_IV_SUMMARY = 249;
    private static final int BUTTON_QUICK_TRANSITION_CHANNELS = 250;
    private static final int BUTTON_QUICK_TRANSITION_TOPICS = 251;
    private static final int BUTTON_ROUND_VIDEO_CAMERA = 252;
    private static final int BUTTON_REMEMBER_LAST_CAMERA = 253;
    private static final int BUTTON_STATIC_ZOOM = 254;
    private static final int BUTTON_INLINE_UNITS = 255;
    private static final int BUTTON_INLINE_TIME_ZONES = 256;
    private static final int BUTTON_GROUP_MESSAGE_MENU = 257;
    private static final int BUTTON_GHOST_NO_READ = 258;
    private static final int BUTTON_GHOST_NO_READ_STORIES = 259;
    private static final int BUTTON_GHOST_NO_ONLINE = 260;
    private static final int BUTTON_GHOST_NO_TYPING = 261;
    private static final int BUTTON_GHOST_OFFLINE_AUTO = 262;
    private static final int BUTTON_GHOST_READ_ON_INTERACTION = 263;
    private static final int BUTTON_GHOST_STATUS_IN_TITLE = 264;
    private static final int BUTTON_DELETED_ICON_COLOR = 265;
    private static final int BUTTON_SAVE_READ_DATES = 266;
    private static final int BUTTON_SAVE_LAST_ONLINE = 267;
    private static final int BUTTON_DELETED_MARK_ICON = 268;
    private static final int BUTTON_RESET_SETTINGS = 269;
    private static final int BUTTON_DELETE_ACCOUNT = 270;
    private static final int BUTTON_FEED_PAGE = 271;
    private static final int BUTTON_FEED_OPEN = 272;
    private static final int BUTTON_FEED_POSTS = 273;
    private static final int BUTTON_FEED_CHANNELS = 274;
    private static final int BUTTON_FEED_TAB = 275;
    private static final int BUTTON_FEED_UNREAD_COUNTER = 276;
    private static final int BUTTON_FEED_WIDE_POSTS = 277;
    private static final int BUTTON_FEED_INCLUDE_ARCHIVED = 278;
    private static final int BUTTON_SEPARATE_HEADERS = 279;
    private static final int BUTTON_M3_LOADING = 280;
    private static final int BUTTON_M3_SLIDER = 281;
    private static final int BUTTON_GLASS_MESSAGE_MENU = 282;
    private static final int BUTTON_MUSIC_PLAYER_MINI = 283;
    private static final int BUTTON_PILL_STACK = 170;
    private static final int BUTTON_PILLS = 171;
    private static final int BUTTON_ICON_PACKS_ON = 172;
    private static final int BUTTON_ICON_PACKS = 173;
    private static final int BUTTON_MONET_THEMES = 174;
    private static final int BUTTON_MONET_STYLE = 175;
    private static final int BUTTON_MONET_PICK = 176;
    private static final int BUTTON_CAMERA_X = 177;
    private static final int BUTTON_CAMERA_X_WIDE = 178;
    private static final int BUTTON_CAMERA_X_ZOOM = 179;
    private static final int BUTTON_ROUND_VIDEO_CAMERA2 = 188;
    private static final int BUTTON_ROUND_VIDEO_HIGH_FPS = 189;
    private static final int BUTTON_ROUND_VIDEO_BITRATE = 190;
    private static final int BUTTON_ROUND_VIDEO_ADVANCED = 191;
    private static final int BUTTON_COMPACT_CHAT_HEADER = 192;
    private static final int BUTTON_MUSIC_PLAYER = 180;
    private static final int BUTTON_MUSIC_PLAYER_BAR = 181;
    private static final int BUTTON_MUSIC_PLAYER_STYLE = 182;
    private static final int BUTTON_SETTINGS_EXPORT = 183;
    private static final int BUTTON_SETTINGS_IMPORT = 184;
    private static final int BUTTON_PROXY_OFF_VPN = 185;
    private static final int BUTTON_PROXY_OFF_MOBILE = 186;
    private static final int BUTTON_PROXY_OFF_WIFI = 187;
    private static final int BUTTON_SILENT_DEFAULT = 126;
    private static final int BUTTON_SHOW_REGISTRATION_DATE = 125;
    private static final int BUTTON_AUDIO_ENHANCE = 124;
    private static final int BUTTON_FAB_SHAPE = 123;
    private static final int BUTTON_LAUNCH_FOLDER = 122;
    private static final int BUTTON_CAMERA_STABILIZATION = 121;
    private static final int BUTTON_PAUSE_ROUND = 120;
    private static final int BUTTON_ALWAYS_SEND_HD = 119;
    private static final int BUTTON_HIDE_CHAT_STATUS = 118;
    private static final int BUTTON_STATIC_INPUT_ICONS = 117;
    private static final int BUTTON_PANGU = 116;
    private static final int BUTTON_OPEN_BY_ID = 115;
    private static final int BUTTON_DOUBLE_TAP_ACTIONS = 110;
    private static final int BUTTON_DOUBLE_TAP_OWN = 111;
    private static final int BUTTON_DOUBLE_TAP_OTHER = 112;
    private static final int BUTTON_PRIVACY_SECTION = 315;
    private static final int BUTTON_UNIFIED_PUSH = 316;
    private static final int BUTTON_DEVICE_CHECK = 317;
    private static final int BUTTON_PERSONALIZATION = 318;
    private static final int BUTTON_SHOW_TRANSLATE_BUTTON = 320;
    private static final int BUTTON_TELEGRAM_AUTO_TRANSLATE = 321;
    private static final int BUTTON_TRANSLATION_PROVIDER = 322;
    private static final int BUTTON_TRANSLATION_TARGET_LANG = 323;
    private static final int BUTTON_DO_NOT_TRANSLATE = 324;
    private static final int BUTTON_TRANSLATE_IN_SHEET = 325;
    private static final int BUTTON_TRANSLATION_SETTINGS = 326;
    private static final int BUTTON_DOH_PROVIDER = 327;
    private static final int BUTTON_CUSTOM_DOH = 328;
    private static final int BUTTON_MAP_PROVIDER = 329;
    private static final int BUTTON_MAP_DRIFTING_FIX = 330;
    private static final int BUTTON_MAP_PREVIEW_PROVIDER = 331;
    private static final int BUTTON_PUSH_STATUS = 332;
    private static final int BUTTON_BATTERY_OPTIMIZATION = 333;
    private static final int BUTTON_GLYPH = 334;
    private static final int BUTTON_M3_GROUP = 335;
    private static final int BUTTON_M3_CHAT_HEADER = 336;
    private static final int BUTTON_M3_NAV_BAR = 337;
    private static final int BUTTON_IOS_GROUP = 338;
    private static final int BUTTON_IOS_NAV_BAR = 339;
    private static final int BUTTON_IOS_CHAT_HEADER = 340;
    private static final int BUTTON_NAVIGATION_SECTION = 341;
    private static final int BUTTON_EMOJI_SETS = 342;
    private static final int BUTTON_PLAYER_STYLE = 343;
    private static final int BUTTON_SURFACE_HIDING = 344;
    private static final int BUTTON_USE_SYSTEM_FONTS = 345;
    private static final int BUTTON_MD3_PLAYER = 346;
    private static final int BUTTON_MD3_MINI_PLAYER = 347;
    private static final int BUTTON_TABLET_MODE = 348;
    private static final int BUTTON_ALWAYS_SHOW_DOWNLOADS = 349;
    private static final int BUTTON_BACK_ANIMATION_STYLE = 350;
    private static final int BUTTON_PREDICTIVE_INTENSITY = 351;
    private static final int BUTTON_MAIN_TABS_HIDE_TITLES = 352;
    private static final int BUTTON_MAIN_TABS_HIDE_CONTACTS = 353;
    private static final int BUTTON_MAIN_TABS_HIDE_CALLS = 354;
    private static final int BUTTON_MAIN_TABS_HIDE_PROFILE = 355;
    private static final int BUTTON_DRAWER_IMMERSIVE = 356;
    private static final int[] ARCHIVE_LIMIT_TITLES = {
            R.string.MZGramArchiveLimitUsers,
            R.string.MZGramArchiveLimitBots,
            R.string.MZGramArchiveLimitGroups,
            R.string.MZGramArchiveLimitChannels,
    };
    private static final int[] AI_MENU_BUTTONS = {
            BUTTON_AI_MENU_GEMINI, BUTTON_AI_MENU_CLAUDE, BUTTON_AI_MENU_CHATGPT, BUTTON_AI_MENU_DEEPSEEK, BUTTON_AI_MENU_GROK,
    };

    private static int indexOf(int[] ids, int id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        return -1;
    }

    // The categories. The id is the index in SECTION_TITLES and
    // SECTION_ICONS; MAIN_PAGE_ORDER says where the main page lists each one.
    // The message menu is a page of Chats, opened from its row there, so it
    // is not on the main page.
    //
    // A new category: the next id, SECTIONS_COUNT + 1, a title
    // (MZGramSection<Name>, 12 languages) and an icon appended to the two
    // arrays, its place in MAIN_PAGE_ORDER, a case in fillItems with a
    // fill<Name> method. Only together with its first switch: a category is
    // never empty.
    public static final int SECTION_MAIN = -1;
    public static final int SECTION_GENERAL = 0;
    public static final int SECTION_APPEARANCE = 1;
    public static final int SECTION_CHATS = 2;
    public static final int SECTION_PLUGINS = 3;
    public static final int SECTION_OTHER = 4;
    public static final int SECTIONS_COUNT = 5;

    public static final int SECTION_ARCHIVE = 5;
    public static final int SECTION_GHOST_MODE = 6;
    public static final int SECTION_ADS_AND_FILTERS = 7;
    public static final int SECTION_PRIVACY = 8;
    public static final int SECTION_AI = 9;
    public static final int SECTION_FEED = 10;
    public static final int SECTION_MESSAGE_MENU = 11;
    public static final int SECTION_NAVIGATION = 12;
    public static final int SECTION_HIDING = 13;

    // Row ids of the categories on the main page; clear of the BUTTON_ ids,
    // which stay below it.
    public static final int SECTION_ROW_ID = 1000;
    public static final int ROW_PLUGINS = SECTION_ROW_ID + SECTION_PLUGINS;
    public static final int ROW_CHANNEL = SECTION_ROW_ID + 100;
    public static final int ROW_SOURCE_CODE = SECTION_ROW_ID + 101;
    public static final int ROW_CHATS_GROUP = SECTION_ROW_ID + 102;
    public static final int ROW_WEBSITE = SECTION_ROW_ID + 103;

    private static final String CHANNEL_USERNAME = "@mzgram";
    private static final String CHATS_USERNAME = "@mzgram_chat";
    private static final String WEBSITE_URL = "mzgram.org";

    public static final int[] MAIN_PAGE_ORDER = {
            SECTION_GENERAL,
            SECTION_APPEARANCE,
            SECTION_CHATS,
            SECTION_PLUGINS,
            SECTION_OTHER
    };

    private static final int[] SECTION_TITLES = {
            R.string.MZGramSectionGeneral,
            R.string.MZGramSectionAppearance,
            R.string.MZGramSectionChats,
            R.string.MZGramPlugins,
            R.string.MZGramSectionOther
    };

    private static final int[] SECTION_ICONS = {
            R.drawable.msg_media,
            R.drawable.msg_theme,
            R.drawable.msg_discussion,
            R.drawable.msg_plugins,
            R.drawable.msg_fave
    };

    // The switches of the message menu page, for the "6/11" of its row in Chats
    private static boolean[] messageMenuSwitches() {
        return new boolean[] {
                MZGramConfig.showCopyPhoto,
                MZGramConfig.showDeleteDownloadedFile,
                MZGramConfig.showAddToSavedMessages,
                MZGramConfig.showSetReminder,
                MZGramConfig.showRepeat,
                MZGramConfig.showOpenIn,
                MZGramConfig.showMessageDetails,
                MZGramConfig.showQrCode,
                MZGramConfig.showNoQuoteForward,
                MZGramConfig.saveProtectedContent,
                MZGramConfig.deleteForAllByDefault,
        };
    }

    private static String messageMenuValue() {
        boolean[] switches = messageMenuSwitches();
        int on = 0;
        for (boolean value : switches) {
            if (value) {
                on++;
            }
        }
        return on + "/" + switches.length;
    }

    // A switch with what it does in grey under its title: one row of a card,
    // as the reference client shows its switches
    // MZGramConfig.DIVIDER_LINE, DIVIDER_HIDDEN, DIVIDER_SEGMENTS
    private static final int[] DIVIDER_STYLE_TITLES = {R.string.MZGramDividerLine, R.string.MZGramDividerHidden, R.string.MZGramDividerSegments};

    private UItem check(int id, int title, boolean checked, int info) {
        UItem item = UItem.asCheck(id, getString(title)).setChecked(checked);
        item.subtext = getString(info);
        return item;
    }

    private final int section;
    private MZGramArchiveSizeView sizeView;
    private int sizeCount;

    public MZGramSettingsActivity() {
        this(SECTION_MAIN);
    }

    public MZGramSettingsActivity(int section) {
        this.section = section;
    }

    @Override
    protected CharSequence getTitle() {
        if (section == SECTION_MAIN) {
            return getString(R.string.MZGram);
        }
        if (section >= 0 && section < SECTION_TITLES.length) {
            return getString(SECTION_TITLES[section]);
        }
        if (section == SECTION_ARCHIVE) return getString(R.string.MZGramSectionArchive);
        if (section == SECTION_GHOST_MODE) return getString(R.string.MZGramSectionGhostMode);
        if (section == SECTION_ADS_AND_FILTERS) return getString(R.string.MZGramSectionAdsAndFilters);
        if (section == SECTION_PRIVACY) return getString(R.string.MZGramSectionPrivacy);
        if (section == SECTION_AI) return getString(R.string.MZGramSectionAi);
        if (section == SECTION_MESSAGE_MENU) return getString(R.string.MZGramSectionMessageMenu);
        if (section == SECTION_FEED) return getString(R.string.MZGramFeed);
        if (section == SECTION_NAVIGATION) return getString(R.string.MZGramAppNavigation);
        if (section == SECTION_HIDING) return getString(R.string.MZGramAppearanceHiding);
        return "";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        switch (section) {
            case SECTION_GENERAL: fillGeneral(items); break;
            case SECTION_APPEARANCE: fillAppearance(items); break;
            case SECTION_CHATS: fillChats(items); break;
            case SECTION_OTHER: fillOther(items); break;
            case SECTION_ARCHIVE: fillArchive(items); break;
            case SECTION_GHOST_MODE: fillGhostMode(items); break;
            case SECTION_ADS_AND_FILTERS: fillAdsAndFilters(items); break;
            case SECTION_PRIVACY: fillPrivacy(items); break;
            case SECTION_AI: fillAi(items); break;
            case SECTION_MESSAGE_MENU: fillMessageMenu(items); break;
            case SECTION_FEED: fillFeed(items); break;
            case SECTION_NAVIGATION: fillNavigation(items); break;
            case SECTION_HIDING: fillHiding(items); break;
            case SECTION_PLUGINS:
                items.add(UItem.asButton(ROW_PLUGINS, R.drawable.msg_plugins, getString(R.string.MZGramPlugins)));
                break;
            default: fillMain(items); break;
        }
    }

    // The main page: the app with its version, the categories, Plugins in a
    // build with Python, and the link to the source code.
    private void fillMain(ArrayList<UItem> items) {
        if (aboutHeader == null) {
            // The page can be filled before the fragment has a context (tests)
            aboutHeader = new MZGramAboutHeader(getContext() != null ? getContext() : org.telegram.messenger.ApplicationLoader.applicationContext);
        }
        items.add(UItem.asCustomShadow(aboutHeader));

        items.add(UItem.asHeader(getString(R.string.MZGramCategories)));
        for (int i : MAIN_PAGE_ORDER) {
            if (i == SECTION_PLUGINS) {
                final int plugins = org.telegram.messenger.mzgram.plugins.MZGramPlugins.count();
                items.add(UItem.asButton(SECTION_ROW_ID + i, SECTION_ICONS[i], getString(SECTION_TITLES[i]), plugins > 0 ? org.telegram.messenger.LocaleController.formatNumber(plugins, ' ') : null));
            } else {
                items.add(UItem.asButton(SECTION_ROW_ID + i, SECTION_ICONS[i], getString(SECTION_TITLES[i])));
            }
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.MZGramLinks)));
        // Telegram's own text: link value
        items.add(UItem.asButton(ROW_CHANNEL, R.drawable.msg_channel, getString(R.string.MZGramChannel), CHANNEL_USERNAME));
        items.add(UItem.asButton(ROW_SOURCE_CODE, R.drawable.msg_language, getString(R.string.MZGramSourceCode), getString(R.string.MZGramSourceCodeValue)));
        // Telegram's own text: link value
        items.add(UItem.asButton(ROW_CHATS_GROUP, R.drawable.msg_groups, getString(R.string.MZGramChatsDiscussion), CHATS_USERNAME));
        // Telegram's own text: link value
        items.add(UItem.asButton(ROW_WEBSITE, R.drawable.msg_link, getString(R.string.MZGramWebsite), WEBSITE_URL));
        items.add(UItem.asShadow(null));
    }

    private MZGramAvatarShapePreview avatarShapePreview;
    private MZGramAvatarCornersPreviewCell avatarCornersPreviewCell;
    private MZGramChatListPreviewCell chatListPreviewCell;
    private MZGramFoldersPreviewCell foldersPreviewCell;
    private org.telegram.ui.Cells.MZGramFabShapeCell fabShapeCell;
    private MZGramAvatarCornersSeekBar sectionRadiusSeekBar;
    private MZGramAvatarCornersSeekBar predictiveIntensitySeekBar;
    private boolean md3Expanded = false;
    private boolean iosExpanded = false;
    private MZGramAboutHeader aboutHeader;

    private void fillArchive(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        if (sizeView == null) {
            // The page can be filled before the fragment has a context (tests)
            sizeView = new MZGramArchiveSizeView(getContext() != null ? getContext() : org.telegram.messenger.ApplicationLoader.applicationContext);
            countArchiveSize();
        }
        items.add(UItem.asCustom(sizeView, MZGramArchiveSizeView.HEIGHT_DP));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_SAVE_DELETED_MESSAGES, getString(R.string.MZGramSaveDeletedMessages)).setChecked(MZGramConfig.saveDeletedMessages));
        items.add(UItem.asCheck(BUTTON_SAVE_EDIT_HISTORY, getString(R.string.MZGramSaveEditHistory)).setChecked(MZGramConfig.saveEditHistory));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveMessageHistoryInfo)));
        items.add(UItem.asCheck(BUTTON_SAVE_ARCHIVE_MEDIA, getString(R.string.MZGramSaveArchiveMedia)).setChecked(MZGramConfig.saveArchiveMedia));
        items.add(UItem.asCheck(BUTTON_SAVE_FORMATTING, getString(R.string.MZGramSaveFormatting)).setChecked(MZGramConfig.saveFormatting));
        items.add(UItem.asCheck(BUTTON_SAVE_REACTIONS, getString(R.string.MZGramSaveReactions)).setChecked(MZGramConfig.saveReactions));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveArchiveMediaInfo)));
        // Download in advance: the largest file for each kind of chat; when a
        // message is deleted a file up to 100 MB larger is downloaded then
        items.add(UItem.asHeader(getString(R.string.MZGramArchiveLimits)));
        for (int i = 0; i < ARCHIVE_LIMIT_TITLES.length; i++) {
            items.add(UItem.asButton(ARCHIVE_LIMIT_FIRST + i, getString(ARCHIVE_LIMIT_TITLES[i]), limitText(org.telegram.messenger.mzgram.MZGramArchiveLimits.limitMb(i))));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramArchiveLimitsInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramArchiveTotalLimit)));
        items.add(UItem.asIntSlideView(1, 0, org.telegram.messenger.mzgram.MZGramArchiveCap.step(), org.telegram.messenger.mzgram.MZGramArchiveCap.STEPS_MB.length - 1,
                step -> totalLimitText(org.telegram.messenger.mzgram.MZGramArchiveCap.STEPS_MB[step]), org.telegram.messenger.mzgram.MZGramArchiveCap::setStep));
        items.add(UItem.asShadow(getString(R.string.MZGramArchiveTotalLimitInfo)));
        items.add(UItem.asCheck(BUTTON_SAVE_READ_DATES, getString(R.string.MZGramSaveReadDates)).setChecked(MZGramConfig.saveReadDates));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveReadDatesInfo)));
        items.add(UItem.asCheck(BUTTON_SAVE_LAST_ONLINE, getString(R.string.MZGramSaveLastOnline)).setChecked(MZGramConfig.saveLastOnline));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveLastOnlineInfo)));
        // What happened to the downloads, to choose the limits on real data
        items.add(UItem.asHeader(getString(R.string.MZGramArchiveCounters)));
        items.add(UItem.asShadow(countersText()));
        items.add(UItem.asButton(BUTTON_ARCHIVE_COUNTERS_RESET, getString(R.string.MZGramArchiveCountersReset)));
        items.add(UItem.asShadow(getString(R.string.MZGramArchiveCountersInfo)));
        // Whose messages are kept; turning one off keeps what was saved
        items.add(UItem.asHeader(getString(R.string.MZGramKeepFromTitle)));
        items.add(UItem.asCheck(BUTTON_SAVE_FROM_USERS, getString(R.string.MZGramKeepFromUsers)).setChecked(MZGramConfig.saveFromUsers));
        items.add(UItem.asCheck(BUTTON_SAVE_FOR_BOTS, getString(R.string.MZGramKeepFromBots)).setChecked(MZGramConfig.saveForBots));
        items.add(UItem.asCheck(BUTTON_SAVE_IN_GROUPS, getString(R.string.MZGramKeepFromGroups)).setChecked(MZGramConfig.saveInGroups));
        items.add(UItem.asCheck(BUTTON_SAVE_IN_CHANNELS, getString(R.string.MZGramKeepFromChannels)).setChecked(MZGramConfig.saveInChannels));
        items.add(UItem.asShadow(getString(R.string.MZGramKeepFromInfo)));
        items.add(UItem.asCheck(BUTTON_SAVE_EXCLUSIONS, getString(R.string.MZGramSaveExclusions)).setChecked(MZGramConfig.saveExclusions));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveExclusionsInfo)));
        items.add(UItem.asCheck(BUTTON_KEPT_REPLY_PREVIEWS, getString(R.string.MZGramKeptReplyPreviews)).setChecked(MZGramConfig.keptReplyPreviews));
        items.add(UItem.asCheck(BUTTON_REPLY_TO_DELETED_AS_QUOTE, getString(R.string.MZGramReplyToDeletedAsQuote)).setChecked(MZGramConfig.replyToDeletedAsQuote));
        items.add(UItem.asShadow(getString(R.string.MZGramDeletedRepliesInfo)));
        items.add(UItem.asCheck(BUTTON_FORWARD_DELETED_AS_COPY, getString(R.string.MZGramForwardDeletedAsCopy)).setChecked(MZGramConfig.forwardDeletedAsCopy));
        items.add(UItem.asShadow(getString(R.string.MZGramForwardDeletedAsCopyInfo)));
        items.add(UItem.asCheck(BUTTON_DELETED_MESSAGES_LIST, getString(R.string.MZGramDeletedMessagesList)).setChecked(MZGramConfig.deletedMessagesList));
        items.add(UItem.asShadow(getString(R.string.MZGramDeletedMessagesListInfo)));
        items.add(UItem.asCheck(BUTTON_DELETED_DIALOGS_LIST, getString(R.string.MZGramDeletedDialogsList)).setChecked(MZGramConfig.deletedDialogsList));
        if (MZGramConfig.deletedDialogsList) {
            items.add(UItem.asButton(BUTTON_DELETED_DIALOGS_OPEN, getString(R.string.MZGramDeletedDialogs)));
            items.add(UItem.asCheck(BUTTON_DELETED_DIALOGS_IN_MAIN_LIST, getString(R.string.MZGramDeletedDialogsInMainList)).setChecked(MZGramConfig.deletedDialogsInMainList));
            items.add(UItem.asShadow(getString(R.string.MZGramDeletedDialogsInMainListInfo)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramDeletedDialogsListInfo)));
        items.add(UItem.asCheck(BUTTON_DELETED_MARK_ICON, getString(R.string.MZGramDeletedMarkIcon)).setChecked(MZGramConfig.deletedMarkIcon));
        if (MZGramConfig.deletedMarkIcon) {
            items.add(UItem.asButton(BUTTON_DELETED_ICON_COLOR, getString(R.string.MZGramDeletedIconColor), getString(MZGramConfig.deletedMarkIconColor == MZGramConfig.DELETED_ICON_TIME ? R.string.MZGramDeletedIconTime : R.string.MZGramDeletedIconRed)));
        }
        if (!MZGramConfig.deletedMarkIcon) {
            items.add(UItem.asButton(BUTTON_DELETED_MARK, getString(R.string.MZGramDeletedMarkText), MZGramConfig.deletedMark));
        }
        items.add(UItem.asButton(BUTTON_EDITED_MARK, getString(R.string.MZGramEditedMarkText), MZGramConfig.editedMark));
        items.add(UItem.asCheck(BUTTON_SEMI_TRANSPARENT_DELETED, getString(R.string.MZGramSemiTransparentDeleted)).setChecked(MZGramConfig.semiTransparentDeleted));
        items.add(UItem.asShadow(getString(R.string.MZGramArchiveLookInfo)));
        items.add(UItem.asButton(BUTTON_WIPE_ARCHIVE, getString(R.string.MZGramWipeArchive)));
        items.add(UItem.asShadow(getString(R.string.MZGramWipeArchiveInfo)));
        items.add(UItem.asButton(BUTTON_EXPORT_ARCHIVE, getString(R.string.MZGramExportArchive)));
        items.add(UItem.asButton(BUTTON_IMPORT_ARCHIVE, getString(R.string.MZGramImportArchive)));
        items.add(UItem.asShadow(getString(R.string.MZGramExportImportArchiveInfo)));
        items.add(UItem.asButton(BUTTON_IMPORT_FOREIGN_ARCHIVE, getString(R.string.MZGramImportForeignArchive)));
        items.add(UItem.asShadow(getString(R.string.MZGramImportForeignArchiveInfo)));
        items.add(UItem.asButton(BUTTON_ERASE_LOCAL_DATABASE, getString(R.string.MZGramEraseLocalDatabase)).red());
        items.add(UItem.asShadow(getString(R.string.MZGramEraseLocalDatabaseInfo)));
    }

    // A limit of Download in advance, as it reads: the size, or No limit.
    public static String limitText(int mb) {
        return mb < 0 ? getString(R.string.MZGramArchiveLimitNone) : AndroidUtilities.formatFileSize(mb * 1024L * 1024L);
    }

    // Saved in advance and when deleted, not downloaded, link expired,
    // deleted without content.
    public static String countersText() {
        return org.telegram.messenger.LocaleController.formatString(R.string.MZGramArchiveCounterSaved,
                        org.telegram.messenger.mzgram.MZGramArchiveLimits.counter(org.telegram.messenger.mzgram.MZGramArchiveLimits.SAVED_AHEAD),
                        org.telegram.messenger.mzgram.MZGramArchiveLimits.counter(org.telegram.messenger.mzgram.MZGramArchiveLimits.SAVED_ON_DELETION))
                + "\n" + org.telegram.messenger.LocaleController.formatString(R.string.MZGramArchiveCounterFailed,
                        org.telegram.messenger.mzgram.MZGramArchiveLimits.counter(org.telegram.messenger.mzgram.MZGramArchiveLimits.FAILED))
                + "\n" + org.telegram.messenger.LocaleController.formatString(R.string.MZGramArchiveCounterExpired,
                        org.telegram.messenger.mzgram.MZGramArchiveLimits.counter(org.telegram.messenger.mzgram.MZGramArchiveLimits.EXPIRED))
                + "\n" + org.telegram.messenger.LocaleController.formatString(R.string.MZGramArchiveCounterEmpty,
                        org.telegram.messenger.mzgram.MZGramArchiveLimits.counter(org.telegram.messenger.mzgram.MZGramArchiveLimits.DELETED_WITHOUT_CONTENT));
    }

    // The largest file downloaded in advance for one kind of chat; the
    // choice applies at once.
    private void chooseLimit(int source) {
        if (getParentActivity() == null) {
            return;
        }
        int[] choices = org.telegram.messenger.mzgram.MZGramArchiveLimits.CHOICES;
        String[] options = new String[choices.length];
        int selected = 0;
        for (int i = 0; i < choices.length; i++) {
            options[i] = limitText(choices[i]);
            if (choices[i] == org.telegram.messenger.mzgram.MZGramArchiveLimits.limitMb(source)) {
                selected = i;
            }
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(ARCHIVE_LIMIT_TITLES[source]), selected, (dialog, which) -> {
            org.telegram.messenger.mzgram.MZGramArchiveLimits.setLimitMb(source, choices[which]);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    public static String peerIdValue() {
        switch (MZGramConfig.peerIdMode) {
            case org.telegram.messenger.mzgram.MZGramPeerId.TELEGRAM_API: return getString(R.string.MZGramPeerIdTelegram);
            case org.telegram.messenger.mzgram.MZGramPeerId.BOT_API: return getString(R.string.MZGramPeerIdBot);
            default: return getString(R.string.MZGramPeerIdHidden);
        }
    }

    // A switch that changes how other screens look: the screens under this
    // page are built again, so it shows when going back
    private void rebuildBelow() {
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    private String titleTextValue() {
        int mode = MZGramTitleText.mode();
        if (mode == MZGramTitleText.CUSTOM) {
            String text = MZGramTitleText.text(currentAccount);
            return text == null ? getString(MZGramTitleText.TITLES[mode]) : text;
        }
        return getString(MZGramTitleText.TITLES[mode]);
    }

    // Header text: Telegram's logo, MZGram, the username, the first name,
    // "Chats" or a text of one's own
    private void chooseTitleText() {
        if (getParentActivity() == null) {
            return;
        }
        String[] options = new String[MZGramTitleText.TITLES.length];
        for (int i = 0; i < options.length; i++) {
            options[i] = getString(MZGramTitleText.TITLES[i]);
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramTitleText), MZGramTitleText.mode(), (dialog, which) -> {
            if (which == MZGramTitleText.CUSTOM) {
                editMark(getString(R.string.MZGramTitleText), MZGramConfig.titleTextCustom, text -> {
                    MZGramConfig.setTitleTextCustom(MZGramTitleText.clean(text));
                    MZGramConfig.setTitleText(MZGramTitleText.clean(text).isEmpty() ? MZGramTitleText.LOGO : MZGramTitleText.CUSTOM);
                    if (chatListPreviewCell != null) {
                        chatListPreviewCell.updateTitle(true);
                    }
                    rebuildBelow();
                });
                return;
            }
            MZGramConfig.setTitleText(which);
            if (chatListPreviewCell != null) {
                chatListPreviewCell.updateTitle(true);
            }
            rebuildBelow();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private static final int[] CHANNEL_BOTTOM_TITLES = {
            R.string.MZGramChannelBottomMute,
            R.string.MZGramChannelBottomDiscuss,
            R.string.MZGramChannelBottomHide,
    };

    // A single-choice dialog over string titles; the choice is saved and the page refreshed
    private void chooseFrom(int title, int[] titles, int selected, org.telegram.messenger.Utilities.Callback<Integer> setter) {
        if (getParentActivity() == null) {
            return;
        }
        String[] options = new String[titles.length];
        for (int i = 0; i < titles.length; i++) {
            options[i] = getString(titles[i]);
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(title), selected, (dialog, which) -> {
            setter.run(which);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    // 300 MB, 1 GB .. 16 GB, or the infinity sign for no limit
    private static String totalLimitText(int mb) {
        if (mb < 0) {
            return "\u221E";
        }
        return mb < 1024 ? mb + " MB" : (mb / 1024) + " GB";
    }

    private static String stickerShapeTitle(int shape) {
        switch (shape) {
            case MZGramConfig.STICKER_SHAPE_ROUNDED: return getString(R.string.MZGramStickerShapeRounded);
            case MZGramConfig.STICKER_SHAPE_MESSAGE: return getString(R.string.MZGramStickerShapeMessage);
            default: return getString(R.string.MZGramStickerShapeDefault);
        }
    }

    private static String seekTitle(int seconds) {
        switch (seconds) {
            case 5: return getString(R.string.MZGramSeek5);
            case 15: return getString(R.string.MZGramSeek15);
            case 30: return getString(R.string.MZGramSeek30);
            default: return getString(R.string.MZGramSeek10);
        }
    }

    // Save folder: Telegram, MZGram or a name of one's own
    private void chooseSaveFolder() {
        if (getParentActivity() == null) {
            return;
        }
        final String current = org.telegram.messenger.mzgram.MZGramSaveFolder.name();
        String[] options = {
                org.telegram.messenger.mzgram.MZGramSaveFolder.TELEGRAM,
                org.telegram.messenger.mzgram.MZGramSaveFolder.MZGRAM,
                getString(R.string.MZGramSaveFolderOwn),
        };
        int selected = org.telegram.messenger.mzgram.MZGramSaveFolder.TELEGRAM.equals(current) ? 0
                : org.telegram.messenger.mzgram.MZGramSaveFolder.MZGRAM.equals(current) ? 1 : 2;
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramSaveFolder), selected, (dialog, which) -> {
            if (which == 2) {
                editMark(getString(R.string.MZGramSaveFolder), selected == 2 ? current : "", MZGramConfig::setSaveFolder);
                return;
            }
            MZGramConfig.setSaveFolder(options[which]);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    // Show ID: hidden, as in Telegram API or as in Bot API; applies at once.
    private void choosePeerId() {
        if (getParentActivity() == null) {
            return;
        }
        String[] options = {
                getString(R.string.MZGramPeerIdHidden),
                getString(R.string.MZGramPeerIdTelegram),
                getString(R.string.MZGramPeerIdBot),
        };
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramPeerId), MZGramConfig.peerIdMode, (dialog, which) -> {
            MZGramConfig.setPeerIdMode(which);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private void chooseTranslationProvider() {
        if (getParentActivity() == null) {
            return;
        }
        String[] options = {
                LocaleController.getString(R.string.MZGramTranslatorTelegram),
                LocaleController.getString(R.string.MZGramTranslatorGoogle),
                LocaleController.getString(R.string.MZGramTranslatorDeepL),
                LocaleController.getString(R.string.MZGramTranslatorAi),
                LocaleController.getString(R.string.MZGramTranslatorAidl),
                LocaleController.getString(R.string.MZGramTranslatorMozhi)
        };
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramGeneralTranslationProvider), MZGramConfig.translationProvider, (dialog, which) -> {
            MZGramConfig.setTranslationProvider(which);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private void chooseTranslationTargetLang() {
        if (getParentActivity() == null) {
            return;
        }
        String[] codes = {"", "en", "uk", "ru", "de", "es", "fr", "pl", "pt", "tr", "ar", "kk"};
        String[] names = new String[codes.length];
        names[0] = getString(R.string.MZGramGeneralTranslationTargetDefault);
        int selected = 0;
        for (int i = 1; i < codes.length; i++) {
            names[i] = new java.util.Locale(codes[i]).getDisplayName();
            if (codes[i].equalsIgnoreCase(MZGramConfig.translationTargetLang)) {
                selected = i;
            }
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), names, getString(R.string.MZGramGeneralTranslationTarget), selected, (dialog, which) -> {
            MZGramConfig.setTranslationTargetLang(codes[which]);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private void chooseDohProvider() {
        if (getParentActivity() == null) {
            return;
        }
        String[] options = new String[MZGramDoH.PROVIDERS.length];
        for (int i = 0; i < options.length; i++) {
            options[i] = MZGramDoH.getProviderTitle(MZGramDoH.PROVIDERS[i]);
        }
        int selected = Math.max(0, Math.min(options.length - 1, MZGramConfig.dohProvider));
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramDnsType), selected, (dialog, which) -> {
            MZGramConfig.setDohProvider(which);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private void chooseCustomDoh() {
        if (getParentActivity() == null) {
            return;
        }
        editMark(getString(R.string.MZGramCustomDoH), MZGramConfig.customDoH, MZGramConfig::setCustomDoH);
    }

    private void chooseMapProvider() {
        if (getParentActivity() == null) {
            return;
        }
        String[] options = {
                getString(R.string.MZGramMapProviderGoogle),
                getString(R.string.MZGramMapProviderOsm)
        };
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramGeneralMapProvider), MZGramConfig.mapProvider, (dialog, which) -> {
            MZGramConfig.setMapProvider(which);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private void chooseMapPreviewProvider() {
        if (getParentActivity() == null) {
            return;
        }
        String[] options = {
                "Telegram",
                "Yandex",
                "OpenFreeMap",
                "Nobody"
        };
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramGeneralMapPreview), MZGramConfig.mapPreviewProvider, (dialog, which) -> {
            MZGramConfig.setMapPreviewProvider(which);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private void openBatteryOptimization() {
        try {
            if (getParentActivity() != null) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                getParentActivity().startActivity(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String getPushStatusSubtitle() {
        if (org.telegram.messenger.mzgram.MZGramUnifiedPush.isActive()) {
            String acked = org.telegram.messenger.mzgram.MZGramUnifiedPush.ackedDistributor();
            if (acked != null) {
                String label = org.telegram.messenger.mzgram.MZGramUnifiedPush.label(acked);
                return label != null ? label : acked;
            }
            return "UnifiedPush";
        }
        return "Telegram Push";
    }

    private static String translationTargetLangName() {
        if (TextUtils.isEmpty(MZGramConfig.translationTargetLang)) {
            return getString(R.string.MZGramGeneralTranslationTargetDefault);
        }
        return new java.util.Locale(MZGramConfig.translationTargetLang).getDisplayName();
    }

    private String doNotTranslateValue() {
        try {
            java.util.HashSet<String> langCodes = org.telegram.ui.RestrictedLanguagesSelectActivity.getRestrictedLanguages();
            if (langCodes == null || langCodes.isEmpty()) {
                return "";
            }
            if (langCodes.size() == 1) {
                return org.telegram.ui.Components.TranslateAlert2.capitalFirst(org.telegram.ui.Components.TranslateAlert2.languageName(langCodes.iterator().next(), new boolean[1]));
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String code : langCodes) {
                String name = org.telegram.ui.Components.TranslateAlert2.capitalFirst(org.telegram.ui.Components.TranslateAlert2.languageName(code, new boolean[1]));
                if (name != null) {
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(name);
                    first = false;
                    if (sb.length() > 25) {
                        break;
                    }
                }
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static String mapProviderName() {
        return MZGramConfig.mapProvider == 1 ? getString(R.string.MZGramMapProviderOsm) : getString(R.string.MZGramMapProviderGoogle);
    }

    private static String mapPreviewProviderName() {
        switch (MZGramConfig.mapPreviewProvider) {
            case 1: return "Yandex";
            case 2: return "OpenFreeMap";
            case 3: return "Nobody";
            case 0:
            default: return "Telegram";
        }
    }

    private static String languageValue() {
        if (org.telegram.messenger.mzgram.MZGramLanguage.SYSTEM.equals(MZGramConfig.language)) {
            return getString(R.string.MZGramLanguageSystem);
        }
        if (org.telegram.messenger.mzgram.MZGramLanguage.indexOf(MZGramConfig.language) < 0) {
            return getString(R.string.MZGramLanguageTelegram);
        }
        return org.telegram.messenger.mzgram.MZGramLanguage.name(MZGramConfig.language);
    }

    private static String formatRoundBitrate(int bitrate) {
        if (bitrate <= 0) {
            return getString(R.string.MZGramRoundBitrateTelegram);
        }
        if (bitrate >= 1_000_000) {
            final String value = bitrate % 1_000_000 == 0 ? String.valueOf(bitrate / 1_000_000)
                    : String.format(org.telegram.messenger.mzgram.MZGramLanguage.locale(), "%.1f", bitrate / 1_000_000f);
            return formatString(R.string.MZGramBitrateMbps, value);
        }
        return formatString(R.string.MZGramBitrateKbps, String.valueOf(bitrate / 1_000));
    }

    // Same as Telegram, System and the languages, each by its own name; the
    // choice applies at once.
    private void chooseLanguage() {
        if (getParentActivity() == null) {
            return;
        }
        String[] codes = org.telegram.messenger.mzgram.MZGramLanguage.LANGUAGES;
        String[] options = new String[codes.length + 2];
        options[0] = getString(R.string.MZGramLanguageTelegram);
        options[1] = getString(R.string.MZGramLanguageSystem);
        System.arraycopy(org.telegram.messenger.mzgram.MZGramLanguage.NAMES, 0, options, 2, codes.length);
        int selected;
        if (org.telegram.messenger.mzgram.MZGramLanguage.SYSTEM.equals(MZGramConfig.language)) {
            selected = 1;
        } else if (org.telegram.messenger.mzgram.MZGramLanguage.indexOf(MZGramConfig.language) >= 0) {
            selected = org.telegram.messenger.mzgram.MZGramLanguage.indexOf(MZGramConfig.language) + 2;
        } else {
            selected = 0;
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramLanguage), selected, (dialog, which) -> {
            setLanguage(which == 0 ? org.telegram.messenger.mzgram.MZGramLanguage.TELEGRAM : which == 1 ? org.telegram.messenger.mzgram.MZGramLanguage.SYSTEM : codes[which - 2]);
        }, getString(R.string.MZGramCancel)));
    }

    // Also used by the tests
    public void setLanguage(String code) {
        MZGramConfig.setLanguage(code);
        // Every open MZGram page and its title in the new language: the pages
        // under this one are built again, this one is filled again
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        // The update bar lives outside the pages: its texts are set again too
        MZGramUpdateBanner banner = MZGramUpdateBanner.current();
        if (banner != null) {
            banner.refreshTexts();
        }
    }

    // The text shown before the time of a deleted or edited message; any
    // text, empty for none.
    private void editMark(String title, String current, org.telegram.messenger.Utilities.Callback<String> setter) {
        if (getContext() == null) {
            return;
        }
        org.telegram.ui.Components.EditTextBoldCursor editText = new org.telegram.ui.Components.EditTextBoldCursor(getContext());
        editText.setText(current);
        editText.setSingleLine(true);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setCursorColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setLineColors(
                org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogInputField, getResourceProvider()),
                org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogInputFieldActivated, getResourceProvider()),
                org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_text_RedBold, getResourceProvider()));
        editText.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        android.widget.FrameLayout frame = new android.widget.FrameLayout(getContext());
        frame.addView(editText, org.telegram.ui.Components.LayoutHelper.createFrame(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(title)
                .setView(frame)
                .setPositiveButton(getString(R.string.MZGramSave), (dialog, which) -> {
                    setter.run(editText.getText() == null ? "" : editText.getText().toString());
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void fillGhostMode(ArrayList<UItem> items) {
        items.add(UItem.asHeader(getString(R.string.MZGramGhostMode)));
        items.add(UItem.asCheck(BUTTON_GHOST_MODE, getString(R.string.MZGramGhostMode)).setChecked(MZGramConfig.ghostMode));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostModeInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramGhostParts) + " " + org.telegram.messenger.mzgram.MZGramGhostMode.partsOn() + "/" + org.telegram.messenger.mzgram.MZGramGhostMode.PARTS));
        items.add(UItem.asCheck(BUTTON_GHOST_NO_READ, getString(R.string.MZGramGhostNoRead)).setChecked(MZGramConfig.ghostNoRead));
        items.add(UItem.asCheck(BUTTON_GHOST_NO_READ_STORIES, getString(R.string.MZGramGhostNoReadStories)).setChecked(MZGramConfig.ghostNoReadStories));
        items.add(UItem.asCheck(BUTTON_GHOST_NO_ONLINE, getString(R.string.MZGramGhostNoOnline)).setChecked(MZGramConfig.ghostNoOnline));
        items.add(UItem.asCheck(BUTTON_GHOST_NO_TYPING, getString(R.string.MZGramGhostNoTyping)).setChecked(MZGramConfig.ghostNoTyping));
        items.add(UItem.asCheck(BUTTON_GHOST_OFFLINE_AUTO, getString(R.string.MZGramGhostOfflineAuto)).setChecked(MZGramConfig.ghostOfflineAuto));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostPartsInfo)));
        items.add(UItem.asCheck(BUTTON_GHOST_READ_ON_INTERACTION, getString(R.string.MZGramGhostReadOnInteraction)).setChecked(MZGramConfig.ghostReadOnInteraction));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostReadOnInteractionInfo)));
        items.add(UItem.asShadow(getString(R.string.MZGramQuickTilesInfo)));
        items.add(UItem.asCheck(BUTTON_GHOST_AUTO_DELAY_SEND, getString(R.string.MZGramGhostAutoDelaySend)).setChecked(MZGramConfig.ghostAutoDelaySend));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostAutoDelaySendInfo)));
        items.add(UItem.asButton(BUTTON_GHOST_SILENT_SEND, getString(R.string.MZGramGhostSilentSend), getString(GHOST_SILENT_TITLES[org.telegram.messenger.mzgram.MZGramGhostMode.silentSend()])));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostSilentSendInfo)));
        items.add(UItem.asCheck(BUTTON_OFFER_GHOST_MODE_BEFORE_STORIES, getString(R.string.MZGramOfferGhostModeBeforeStoriesToggle)).setChecked(MZGramConfig.offerGhostModeBeforeStories));
        items.add(UItem.asShadow(getString(R.string.MZGramOfferGhostModeBeforeStoriesToggleInfo)));
        items.add(UItem.asCheck(BUTTON_GHOST_MODE_EXCLUSIONS, getString(R.string.MZGramGhostModeExclusions)).setChecked(MZGramConfig.ghostModeExclusions));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostModeExclusionsInfo)));
        items.add(UItem.asCheck(BUTTON_GHOST_STATUS_IN_TITLE, getString(R.string.MZGramGhostStatusInTitle)).setChecked(MZGramConfig.ghostStatusInTitle));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostStatusInTitleInfo)));
    }

    private static final int[] GHOST_SILENT_TITLES = {
            R.string.MZGramSilentNever,
            R.string.MZGramSilentInGhostMode,
            R.string.MZGramSilentAlways,
    };

    private void fillPrivacy(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_CONFIRM_INTERNAL_LINKS, getString(R.string.MZGramConfirmInternalLinks)).setChecked(MZGramConfig.confirmInternalLinks));
        items.add(UItem.asShadow(getString(R.string.MZGramConfirmInternalLinksInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_LINK_PREVIEWS, getString(R.string.MZGramDisableLinkPreviews)).setChecked(MZGramConfig.disableLinkPreviews));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableLinkPreviewsInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_CLOUD_DRAFTS, getString(R.string.MZGramDisableCloudDrafts)).setChecked(MZGramConfig.disableCloudDrafts));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableCloudDraftsInfo)));
        items.add(UItem.asCheck(BUTTON_LOCK_SCREEN_VOICE_PROTECTION, getString(R.string.MZGramLockScreenVoiceProtection)).setChecked(MZGramConfig.lockScreenVoiceProtection));
        items.add(UItem.asShadow(getString(R.string.MZGramLockScreenVoiceProtectionInfo)));
        items.add(UItem.asCheck(BUTTON_OWN_LAST_SEEN, getString(R.string.MZGramOwnLastSeen)).setChecked(MZGramConfig.ownLastSeen));
        if (MZGramConfig.ownLastSeen) {
            items.add(UItem.asButton(BUTTON_OWN_LAST_SEEN_VALUE, getString(R.string.MZGramOwnLastSeenValue), org.telegram.messenger.mzgram.MZGramOwnLastSeen.describe(currentAccount, getConnectionsManager().getCurrentTime())));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramOwnLastSeenInfo)));
        items.add(UItem.asCheck(BUTTON_LAST_SEEN_EXACT, getString(R.string.MZGramLastSeenExact)).setChecked(MZGramConfig.lastSeenExact));
        items.add(UItem.asShadow(getString(R.string.MZGramLastSeenExactInfo)));
        items.add(UItem.asCheck(BUTTON_KEEP_GROUP_READS, getString(R.string.MZGramKeepGroupReads)).setChecked(MZGramConfig.keepGroupReads));
        items.add(UItem.asShadow(getString(R.string.MZGramKeepGroupReadsInfo)));
        items.add(UItem.asCheck(BUTTON_BOT_START_CONFIRM, getString(R.string.MZGramBotStartConfirm)).setChecked(MZGramConfig.botStartConfirm));
        items.add(UItem.asShadow(getString(R.string.MZGramBotStartConfirmInfo)));
        items.add(UItem.asCheck(BUTTON_REDUCE_NETWORK_TRACKING, getString(R.string.MZGramReduceNetworkTracking)).setChecked(MZGramConfig.reduceNetworkTracking));
        items.add(UItem.asShadow(getString(R.string.MZGramReduceNetworkTrackingInfo)));
        if (org.telegram.messenger.mzgram.MZGramProxyAutoDisable.available()) {
            items.add(UItem.asHeader(getString(R.string.MZGramProxyAutoDisable)));
            items.add(UItem.asCheck(BUTTON_PROXY_OFF_VPN, getString(R.string.MZGramProxyOffVpn))
                    .setChecked(org.telegram.messenger.mzgram.MZGramProxyAutoDisable.isOn(org.telegram.messenger.mzgram.MZGramProxyAutoDisable.VPN)));
            items.add(UItem.asCheck(BUTTON_PROXY_OFF_MOBILE, getString(R.string.MZGramProxyOffMobile))
                    .setChecked(org.telegram.messenger.mzgram.MZGramProxyAutoDisable.isOn(org.telegram.messenger.mzgram.MZGramProxyAutoDisable.MOBILE_DATA)));
            items.add(UItem.asCheck(BUTTON_PROXY_OFF_WIFI, getString(R.string.MZGramProxyOffWifi))
                    .setChecked(org.telegram.messenger.mzgram.MZGramProxyAutoDisable.isOn(org.telegram.messenger.mzgram.MZGramProxyAutoDisable.WIFI)));
            items.add(UItem.asShadow(getString(R.string.MZGramProxyAutoDisableInfo)));
        }
        items.add(UItem.asCheck(BUTTON_PASSCODE_ACCOUNTS, getString(R.string.MZGramPasscodeAccounts)).setChecked(MZGramConfig.passcodeAccounts));
        if (MZGramConfig.passcodeAccounts) {
            items.add(UItem.asButton(BUTTON_PASSCODE_ACCOUNTS_SETUP, getString(R.string.MZGramPasscodeAccountsSetup)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramPasscodeAccountsInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_READ_STICKERS, getString(R.string.MZGramDisableReadStickers)).setChecked(MZGramConfig.disableReadStickers));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableReadStickersInfo)));
        items.add(UItem.asCheck(BUTTON_OPENFREEMAP_PREVIEW, getString(R.string.MZGramOpenFreeMapPreview)).setChecked(MZGramConfig.openFreeMapPreview));
        items.add(UItem.asShadow(getString(R.string.MZGramOpenFreeMapPreviewInfo)));
    }

    // What a double tap does on own or other people's messages; applies at once.
    private void chooseDoubleTap(int title, int current, org.telegram.messenger.Utilities.Callback<Integer> setter) {
        if (getParentActivity() == null) {
            return;
        }
        int[] titles = org.telegram.ui.mzgram.MZGramDoubleTap.TITLES;
        String[] options = new String[titles.length];
        for (int i = 0; i < titles.length; i++) {
            options[i] = getString(titles[i]);
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(title), current, (dialog, which) -> {
            setter.run(which);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    // General: 8 structured cards (Translation, General/Legacy, Download Boost/DoH, Storage, Profile, Chat List & Archive, Maps, Notifications & Glyph)
    private void fillGeneral(ArrayList<UItem> items) {
        // Card 1: Translation
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralTranslateHeader)));
        items.add(UItem.asCheck(BUTTON_SHOW_TRANSLATE_BUTTON, getString(R.string.MZGramGeneralTranslateButton)).setChecked(MZGramConfig.chatTranslationBar));
        items.add(UItem.asCheck(BUTTON_TELEGRAM_AUTO_TRANSLATE, getString(R.string.MZGramGeneralTranslateWholeChat)).setChecked(MZGramConfig.telegramUIAutoTranslate));
        items.add(UItem.asButton(BUTTON_TRANSLATION_PROVIDER, getString(R.string.MZGramGeneralTranslationProvider), org.telegram.messenger.mzgram.translate.MZGramTranslator.getProviderName(MZGramConfig.translationProvider)));
        items.add(UItem.asButton(BUTTON_TRANSLATION_TARGET_LANG, getString(R.string.MZGramGeneralTranslationTarget), translationTargetLangName()));
        items.add(UItem.asButton(BUTTON_DO_NOT_TRANSLATE, getString(R.string.MZGramGeneralDoNotTranslate), doNotTranslateValue()));
        items.add(UItem.asCheck(BUTTON_TRANSLATE_IN_SHEET, getString(R.string.MZGramChatsTranslateInSheet)).setChecked(MZGramConfig.translateInSheet));
        items.add(UItem.asButton(BUTTON_TRANSLATION_SETTINGS, getString(R.string.MZGramTranslatorSettings)));
        items.add(UItem.asShadow(getString(R.string.MZGramGeneralTranslateInfo)));

        // Card 2: General & Legacy
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralSectionHeader)));
        items.add(UItem.asButton(BUTTON_PRIVACY_SECTION, R.drawable.msg_secret, getString(R.string.MZGramSectionPrivacy)));
        items.add(UItem.asButton(BUTTON_UNIFIED_PUSH, R.drawable.msg_notifications, getString(R.string.MZGramUnifiedPush)));
        items.add(UItem.asButton(BUTTON_DEVICE_CHECK, R.drawable.msg_info, getString(R.string.MZGramDeviceCheck)));
        items.add(UItem.asButton(BUTTON_LANGUAGE, getString(R.string.MZGramLanguage), languageValue()));
        items.add(check(BUTTON_PERSIAN_CALENDAR, R.string.MZGramPersianCalendar, MZGramConfig.persianCalendar, R.string.MZGramPersianCalendarInfo));
        items.add(UItem.asButtonCheck(BUTTON_DISABLE_NUMBER_ROUNDING, getString(R.string.MZGramGeneralDisableNumberRounding), getString(R.string.MZGramGeneralDisableNumberRoundingValue)).setChecked(MZGramConfig.disableNumberRounding));
        items.add(UItem.asButtonCheck(BUTTON_FORMAT_TIME_WITH_SECONDS, getString(R.string.MZGramGeneralFormatTimeWithSeconds), getString(R.string.MZGramGeneralFormatTimeWithSecondsValue)).setChecked(MZGramConfig.formatTimeWithSeconds));
        items.add(UItem.asCheck(BUTTON_IN_APP_VIBRATION, getString(R.string.MZGramGeneralInAppVibration)).setChecked(MZGramConfig.inAppVibration));
        items.add(UItem.asCheck(BUTTON_DISABLE_NOTIFICATION_DELAY, getString(R.string.MZGramDisableNotificationDelay)).setChecked(MZGramConfig.disableNotificationDelay));
        items.add(UItem.asCheck(BUTTON_STRIP_ZALGO_TEXT, getString(R.string.MZGramGeneralFilterZalgo)).setChecked(MZGramConfig.stripZalgoText));
        items.add(UItem.asShadow(formatString(R.string.MZGramGeneralFilterZalgoInfo, "Z\u0335\u0321a\u0336\u0327l\u0336\u0327g\u0338\u0327o\u0335\u0327")));

        // Card 3: Download Boost & DoH
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralSpeedHeader)));
        items.add(UItem.asSlideView(new String[] {getString(R.string.MZGramOff), getString(R.string.MZGramDownloadBoostFast), getString(R.string.MZGramDownloadBoostUltra)},
                MZGramConfig.downloadSpeedBoost, MZGramConfig::setDownloadSpeedBoost));
        items.add(UItem.asCheck(BUTTON_UPLOAD_SPEED_BOOST, getString(R.string.MZGramGeneralUploadBoost)).setChecked(MZGramConfig.uploadSpeedBoost));
        items.add(UItem.asButton(BUTTON_DOH_PROVIDER, getString(R.string.MZGramDnsType), org.telegram.messenger.mzgram.MZGramDoH.getProviderTitle(MZGramConfig.dohProvider)));
        if (MZGramConfig.dohProvider == org.telegram.messenger.mzgram.MZGramDoH.PROVIDER_CUSTOM) {
            String customUrl = TextUtils.isEmpty(MZGramConfig.customDoH) ? getString(R.string.MZGramGeneralSavePathDefault) : MZGramConfig.customDoH;
            items.add(UItem.asButton(BUTTON_CUSTOM_DOH, getString(R.string.MZGramCustomDoH), customUrl));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramGeneralSpeedBoostInfo)));

        // Card 4: Storage
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralStorageHeader)));
        items.add(UItem.asButton(BUTTON_SAVE_FOLDER, getString(R.string.MZGramGeneralSavePath), org.telegram.messenger.mzgram.MZGramSaveFolder.name()));
        items.add(UItem.asShadow(formatString(R.string.MZGramGeneralSavePathInfoFolder, org.telegram.messenger.mzgram.MZGramSaveFolder.name())));

        // Card 5: Profile
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralProfileHeader)));
        items.add(UItem.asCheck(BUTTON_RELATIVE_LAST_SEEN, getString(R.string.MZGramGeneralRelativeLastSeen)).setChecked(MZGramConfig.relativeLastSeen));
        items.add(UItem.asCheck(BUTTON_HIDE_PHONE_NUMBER, getString(R.string.MZGramGeneralHidePhone)).setChecked(MZGramConfig.hidePhoneNumber));
        items.add(UItem.asCheck(BUTTON_SHOW_REGISTRATION_DATE, getString(R.string.MZGramShowRegistrationDate)).setChecked(MZGramConfig.showRegistrationDate));
        items.add(UItem.asButton(BUTTON_PEER_ID, getString(R.string.MZGramGeneralShowIdAndDc), peerIdValue()));
        items.add(UItem.asShadow(getString(R.string.MZGramGeneralShowIdAndDcInfo)));

        // Card 6: Chat List & Archive
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralArchiveHeader)));
        items.add(UItem.asCheck(BUTTON_HIDE_ARCHIVE_FOLDER, getString(R.string.MZGramGeneralHideArchive)).setChecked(MZGramConfig.hideArchiveFolder));
        if (!MZGramConfig.hideArchiveFolder) {
            items.add(UItem.asCheck(BUTTON_OPEN_ARCHIVE_ON_PULL, getString(R.string.MZGramGeneralArchiveOnPull)).setChecked(MZGramConfig.openArchiveOnPull));
        }
        items.add(UItem.asCheck(BUTTON_DISABLE_UNARCHIVE_SWIPE, getString(R.string.MZGramGeneralDisableUnarchiveSwipe)).setChecked(MZGramConfig.disableUnarchiveSwipe));
        items.add(UItem.asCheck(BUTTON_SORT_BY_UNREAD, getString(R.string.MZGramSortByUnread)).setChecked(MZGramConfig.sortByUnread));
        items.add(UItem.asShadow(getString(R.string.MZGramGeneralDisableUnarchiveSwipeInfo)));

        // Card 7: Maps
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralMapsHeader)));
        items.add(UItem.asButton(BUTTON_MAP_PROVIDER, getString(R.string.MZGramGeneralMapProvider), mapProviderName()));
        if (MZGramConfig.mapProvider == 0) {
            items.add(UItem.asCheck(BUTTON_MAP_DRIFTING_FIX, getString(R.string.MZGramGeneralMapDriftingFix)).setChecked(MZGramConfig.mapDriftingFix));
        }
        items.add(UItem.asButton(BUTTON_MAP_PREVIEW_PROVIDER, getString(R.string.MZGramGeneralMapPreview), mapPreviewProviderName()));
        items.add(UItem.asShadow(getString(R.string.MZGramGeneralUseOsmMapInfo)));

        // Card 8: Notifications & Glyph
        items.add(UItem.asHeader(getString(R.string.MZGramGeneralNotificationsHeader)));
        items.add(UItem.asCheck(BUTTON_DISABLE_NOTIFICATION_BUBBLES, getString(R.string.MZGramDisableNotificationBubbles)).setChecked(MZGramConfig.disableNotificationBubbles));
        items.add(UItem.asButton(BUTTON_PUSH_STATUS, getString(R.string.MZGramGeneralPushStatus), getPushStatusSubtitle()));
        items.add(UItem.asButton(BUTTON_BATTERY_OPTIMIZATION, getString(R.string.MZGramGeneralBatteryOptimization)));
        items.add(UItem.asButton(BUTTON_GLYPH, getString(R.string.MZGramGlyphTitle)));
        items.add(UItem.asShadow(getString(R.string.MZGramGeneralNotificationsInfo)));
    }

    // Appearance: avatars, the chat list, folders, navigation, style, glass
    private void fillAppearance(ArrayList<UItem> items) {
        Context context = getContext() != null ? getContext() : org.telegram.messenger.ApplicationLoader.applicationContext;

        // ==========================================
        // Card 1: Avatar Corners
        // ==========================================
        if (avatarCornersPreviewCell == null) {
            avatarCornersPreviewCell = new MZGramAvatarCornersPreviewCell(context, null);
        }
        items.add(UItem.asCustom(avatarCornersPreviewCell, 222));
        items.add(UItem.asCheck(BUTTON_SINGLE_CORNER_RADIUS, getString(R.string.MZGramSingleCornerRadius)).setChecked(MZGramConfig.singleCornerRadius));
        items.add(UItem.asShadow(getString(R.string.MZGramSingleCornerRadiusInfo)));

        // ==========================================
        // Card 2: Chat List
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramHeaderChatList)));
        if (chatListPreviewCell == null) {
            chatListPreviewCell = new MZGramChatListPreviewCell(context);
        }
        items.add(UItem.asCustom(chatListPreviewCell, 100));
        items.add(check(BUTTON_FORCE_SNOW, R.string.MZGramForceSnow, MZGramConfig.forceSnow, R.string.MZGramForceSnowInfo));
        items.add(UItem.asCheck(BUTTON_CENTER_TITLE, getString(R.string.MZGramCenterTitle)).setChecked(MZGramConfig.centerTitle));
        items.add(UItem.asCheck(BUTTON_SENDER_MINI_AVATARS, getString(R.string.MZGramSenderMiniAvatars)).setChecked(MZGramConfig.senderMiniAvatars));
        items.add(UItem.asButton(BUTTON_TITLE_TEXT, getString(R.string.MZGramTitleText), titleTextValue()));
        items.add(UItem.asShadow(getString(R.string.MZGramChatListInfo)));

        // ==========================================
        // Card 3: Folders
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramHeaderFolders)));
        if (foldersPreviewCell == null) {
            foldersPreviewCell = new MZGramFoldersPreviewCell(context);
        }
        items.add(UItem.asCustom(foldersPreviewCell, 50));
        items.add(UItem.asButton(BUTTON_FOLDER_TAB_ICONS, getString(R.string.MZGramFolderTabs), getString(MZGramFolderIcons.MODE_TITLES[MZGramFolderIcons.mode()])));
        items.add(UItem.asButton(BUTTON_FOLDER_TAB_COUNTERS, getString(R.string.MZGramFolderTabCounters), folderTabCounterValue()));
        items.add(UItem.asCheck(BUTTON_HIDE_ALL_CHATS_TAB, getString(R.string.MZGramHideAllChatsTab)).setChecked(MZGramConfig.hideAllChatsTab));
        items.add(check(BUTTON_FOLDER_TABS_AT_BOTTOM, R.string.MZGramFolderTabsAtBottom, MZGramConfig.folderTabsAtBottom, R.string.MZGramFolderTabsAtBottomInfo));
        items.add(UItem.asButton(BUTTON_LAUNCH_FOLDER, getString(R.string.MZGramLaunchFolder), MZGramLaunchFolder.valueFor(currentAccount)));
        items.add(check(BUTTON_LOCAL_FOLDERS, R.string.MZGramLocalFoldersSwitch, MZGramConfig.localFolders, R.string.MZGramLocalFoldersSwitchInfo));
        items.add(check(BUTTON_EXTENDED_PINS, R.string.MZGramExtendedPins, MZGramConfig.extendedPins, R.string.MZGramExtendedPinsInfo));
        items.add(UItem.asShadow(getString(R.string.MZGramFolderTabsInfo)));

        // ==========================================
        // Card 4: Interface Links
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramSectionInterface)));
        items.add(UItem.asButton(BUTTON_NAVIGATION_SECTION, R.drawable.msg_newphone, getString(R.string.MZGramAppNavigation), getString(R.string.MZGramAppNavigationSub)));
        items.add(UItem.asButton(BUTTON_ICON_PACKS, R.drawable.msg_sticker, getString(R.string.MZGramIconPacks), getString(R.string.MZGramIconPacksInfo)));
        items.add(UItem.asButton(BUTTON_EMOJI_SETS, R.drawable.msg_emoji_smiles, getString(R.string.MZGramEmojiSets), getString(R.string.MZGramEmojiSetsInfo)));
        items.add(UItem.asButton(BUTTON_PILL_STACK, R.drawable.outline_header_search, getString(R.string.MZGramPillStack), getString(R.string.MZGramPillStackInfo)));
        items.add(UItem.asButton(BUTTON_PLAYER_STYLE, R.drawable.msg_filled_data_music, getString(R.string.MZGramPlayerStyleTitle), getString(R.string.MZGramPlayerStyleInfo)));
        items.add(UItem.asButton(BUTTON_SURFACE_HIDING, R.drawable.msg_archive_hide, getString(R.string.MZGramAppearanceHiding), getString(R.string.MZGramAppearanceHidingInfo)));
        items.add(UItem.asShadow(null));

        // ==========================================
        // Card 5: Appearance General
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramSectionAppearance)));
        if (fabShapeCell == null) {
            fabShapeCell = new org.telegram.ui.Cells.MZGramFabShapeCell(context);
        }
        items.add(UItem.asCustom(fabShapeCell, 110));
        items.add(UItem.asCheck(BUTTON_USE_SYSTEM_FONTS, getString(R.string.MZGramUseSystemFonts)).setChecked(MZGramConfig.useSystemFonts));
        items.add(UItem.asButton(BUTTON_APP_FONT, getString(R.string.MZGramAppFont), MZGramAppFont.title()));
        items.add(UItem.asExpandableSwitch(BUTTON_M3_GROUP, getString(R.string.MZGramHeaderMaterial3), md3SelectedCount() + "/8")
                .setChecked(md3SelectedCount() > 0)
                .setCollapsed(!md3Expanded)
                .setClickCallback(v -> toggleAllMd3Styles()));
        if (md3Expanded) {
            items.add(UItem.asRoundCheckbox(BUTTON_M3_LOADING, getString(R.string.MZGramM3Loading)).setChecked(MZGramConfig.m3Loading).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_M3_SLIDER, getString(R.string.MZGramM3Slider)).setChecked(MZGramConfig.m3Slider).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_M3_SWITCHES, getString(R.string.MZGramM3Switches)).setChecked(MZGramConfig.m3Switches).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_M3_CHAT_HEADER, getString(R.string.MZGramM3ChatHeader)).setChecked(MZGramConfig.m3ChatHeader).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_M3_NAV_BAR, getString(R.string.MZGramM3NavBar)).setChecked(MZGramConfig.m3NavBar).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_CARDS_DESIGN, getString(R.string.MZGramCardsDesign)).setChecked(MZGramConfig.cardsDesign).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_MD3_PLAYER, getString(R.string.MZGramM3Player)).setChecked(MZGramConfig.musicPlayer).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_MD3_MINI_PLAYER, getString(R.string.MZGramM3MiniPlayer)).setChecked(MZGramConfig.musicPlayerMini).pad());
        }
        items.add(UItem.asExpandableSwitch(BUTTON_IOS_GROUP, getString(R.string.MZGramIosDesign), iosSelectedCount() + "/2")
                .setChecked(iosSelectedCount() > 0)
                .setCollapsed(!iosExpanded)
                .setClickCallback(v -> toggleAllIosStyles()));
        if (iosExpanded) {
            items.add(UItem.asRoundCheckbox(BUTTON_IOS_NAV_BAR, getString(R.string.MZGramIosNavBar)).setChecked(MZGramConfig.iosNavBar).pad());
            items.add(UItem.asRoundCheckbox(BUTTON_IOS_CHAT_HEADER, getString(R.string.MZGramIosChatHeader)).setChecked(MZGramConfig.iosChatHeader).pad());
        }
        items.add(check(BUTTON_GOOEY_AVATAR_ANIMATION, R.string.MZGramGooeyAvatarAnimation, MZGramConfig.gooeyAvatarAnimation, R.string.MZGramGooeyAvatarAnimationInfo));
        items.add(check(BUTTON_CUSTOM_CHAT_THEMES, R.string.MZGramCustomChatThemes, MZGramConfig.customChatThemes, R.string.MZGramCustomChatThemesInfo));
        items.add(UItem.asShadow(getString(R.string.MZGramCustomChatThemesInfo)));

        // ==========================================
        // Card 6: Sections
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramHeaderSections)));
        if (sectionRadiusSeekBar == null) {
            sectionRadiusSeekBar = new MZGramAvatarCornersSeekBar(context, val -> {
                MZGramConfig.setSectionRadius(val);
            }, 0, MZGramConfig.SECTION_RADIUS_MAX,
                    getString(R.string.MZGramSectionRadius),
                    getString(R.string.MZGramOff),
                    getString(R.string.MZGramSectionRadiusMax));
            sectionRadiusSeekBar.setValueSuffix("dp");
        }
        sectionRadiusSeekBar.setValue(MZGramConfig.sectionRadius);
        items.add(UItem.asCustom(sectionRadiusSeekBar, 112));
        items.add(UItem.asButton(BUTTON_DIVIDER_STYLE, getString(R.string.MZGramDividerStyle), getString(DIVIDER_STYLE_TITLES[MZGramConfig.dividerStyle])));
        items.add(check(BUTTON_SEPARATE_HEADERS, R.string.MZGramSeparateHeaders, MZGramConfig.separatedHeaders(), R.string.MZGramSeparateHeadersInfo)
                .setEnabled(MZGramConfig.dividerStyle != MZGramConfig.DIVIDER_SEGMENTS));
        items.add(UItem.asShadow(getString(R.string.MZGramSectionsInfo)));

        // ==========================================
        // Card 7: Blur Options
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramHeaderGlass)));
        items.add(UItem.asButton(BUTTON_GLASS_OUTLINE, getString(R.string.MZGramGlassOutline), getString(MZGramGlassOutline.TITLES[MZGramGlassOutline.style()])));
        items.add(check(BUTTON_GLASS_MESSAGE_MENU, R.string.MZGramGlassMessageMenu, MZGramConfig.glassMessageMenu, R.string.MZGramGlassMessageMenuInfo));
        items.add(UItem.asCheck(BUTTON_FORCE_BLUR, getString(R.string.MZGramForceBlur)).setChecked(MZGramConfig.forceBlur));
        items.add(check(BUTTON_DISABLE_AVATAR_BLUR, R.string.MZGramDisableAvatarBlur, MZGramConfig.disableAvatarBlur, R.string.MZGramDisableAvatarBlurInfo));
        items.add(UItem.asShadow(getString(R.string.MZGramBlurInfo)));

        // ==========================================
        // Card 8: MZGram Monet & Themes
        // ==========================================
        items.add(check(BUTTON_PREDICTIVE_BACK_ANIMATION, R.string.MZGramPredictiveBackAnimation, MZGramConfig.predictiveBackAnimation, R.string.MZGramPredictiveBackAnimationInfo));
        if (org.telegram.messenger.mzgram.MZGramMonet.available()) {
            items.add(check(BUTTON_MONET_THEMES, R.string.MZGramMonetThemes, MZGramConfig.monetThemes, R.string.MZGramMonetThemesInfo));
            if (MZGramConfig.monetThemes) {
                items.add(UItem.asButton(BUTTON_MONET_STYLE, getString(R.string.MZGramMonetStyle), org.telegram.messenger.mzgram.MZGramMonet.styleTitle(MZGramConfig.monetStyle)));
                items.add(UItem.asButton(BUTTON_MONET_PICK, getString(R.string.MZGramMonetPick)));
            }
        }
        items.add(UItem.asShadow(null));
    }

    private void fillNavigation(ArrayList<UItem> items) {
        // ==========================================
        // Card 1: General
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramNavGeneral)));
        String tabletModeTitle;
        if (MZGramConfig.tabletMode == 1) {
            tabletModeTitle = getString(R.string.MZGramTabletModeOn);
        } else if (MZGramConfig.tabletMode == 2) {
            tabletModeTitle = getString(R.string.MZGramTabletModeOff);
        } else {
            tabletModeTitle = getString(R.string.MZGramTabletModeAuto);
        }
        items.add(UItem.asButton(BUTTON_TABLET_MODE, getString(R.string.MZGramTabletMode), tabletModeTitle));
        items.add(UItem.asCheck(BUTTON_ALWAYS_SHOW_DOWNLOADS, getString(R.string.MZGramAlwaysShowDownloadIcon)).setChecked(MZGramConfig.alwaysShowDownloadIcon));
        String backAnimationTitle;
        if (MZGramConfig.backAnimationStyle == 1) {
            backAnimationTitle = getString(R.string.MZGramBackAnimationSpring);
        } else if (MZGramConfig.backAnimationStyle == 2) {
            backAnimationTitle = getString(R.string.MZGramBackAnimationPredictive);
        } else if (MZGramConfig.backAnimationStyle == 3 && android.os.Build.VERSION.SDK_INT >= 34) {
            backAnimationTitle = getString(R.string.MZGramBackAnimationSlide);
        } else {
            backAnimationTitle = getString(R.string.MZGramBackAnimationClassic);
        }
        items.add(UItem.asButton(BUTTON_BACK_ANIMATION_STYLE, getString(R.string.MZGramBackAnimation), backAnimationTitle));
        items.add(UItem.asShadow(getString(R.string.MZGramBackAnimationInfo)));
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            if (predictiveIntensitySeekBar == null) {
                predictiveIntensitySeekBar = new MZGramAvatarCornersSeekBar(getContext(), val -> {
                    MZGramConfig.setPredictiveBackIntensity(val);
                }, 0, 200,
                        getString(R.string.MZGramPredictiveBackIntensity),
                        getString(R.string.MZGramPredictiveBackOff),
                        getString(R.string.MZGramPredictiveBackMax));
                predictiveIntensitySeekBar.setValueSuffix("%");
            }
            predictiveIntensitySeekBar.setValue(MZGramConfig.predictiveBackIntensity);
            items.add(UItem.asCustom(BUTTON_PREDICTIVE_INTENSITY, predictiveIntensitySeekBar));
            items.add(UItem.asShadow(getString(R.string.MZGramPredictiveBackInfo)));
        }

        // ==========================================
        // Card 2: Bottom Navigation Bar
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramBottomNavigationBar)));
        String bottomNavModeTitle;
        if (MZGramConfig.bottomNavigationMode == 1) {
            bottomNavModeTitle = getString(R.string.MZGramBottomNavigationModeHide);
        } else if (MZGramConfig.bottomNavigationMode == 2) {
            bottomNavModeTitle = getString(R.string.MZGramBottomNavigationModeFloating);
        } else {
            bottomNavModeTitle = getString(R.string.MZGramBottomNavigationModeShow);
        }
        items.add(UItem.asButton(BUTTON_BOTTOM_NAV_MODE, getString(R.string.MZGramBottomNavigationBarMode), bottomNavModeTitle));
        if (MZGramConfig.bottomNavigationMode != 1) {
            items.add(UItem.asCheck(BUTTON_MAIN_TABS_HIDE_TITLES, getString(R.string.MZGramMainTabsHideTitles)).setChecked(MZGramConfig.mainTabsHideTitles));
            items.add(UItem.asCheck(BUTTON_MAIN_TABS_HIDE_CONTACTS, getString(R.string.MZGramMainTabsHideContacts)).setChecked(MZGramConfig.mainTabsHideContacts));
            items.add(UItem.asCheck(BUTTON_MAIN_TABS_HIDE_CALLS, getString(R.string.MZGramMainTabsHideCallsSettings)).setChecked(MZGramConfig.mainTabsHideCallsSettings));
            items.add(UItem.asCheck(BUTTON_MAIN_TABS_HIDE_PROFILE, getString(R.string.MZGramMainTabsHideProfile)).setChecked(MZGramConfig.mainTabsHideProfile));
        }
        items.add(UItem.asShadow(null));

        // ==========================================
        // Card 3: Navigation Drawer
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramNavigationDrawer)));
        items.add(UItem.asCheck(BUTTON_CUSTOM_DRAWER, getString(R.string.MZGramNavigationDrawer)).setChecked(MZGramConfig.customDrawer));
        if (MZGramConfig.customDrawer) {
            items.add(UItem.asCheck(BUTTON_DRAWER_IMMERSIVE, getString(R.string.MZGramNavigationDrawerImmersive)).setChecked(MZGramConfig.drawerImmersiveAnimation));
            items.add(UItem.asCheck(BUTTON_DRAWER_SHOW_QR, getString(R.string.MZGramDrawerShowQr)).setChecked(MZGramConfig.drawerShowQr));
            items.add(UItem.asCheck(BUTTON_DRAWER_LARGE_AVATAR, getString(R.string.MZGramDrawerLargeAvatar)).setChecked(MZGramConfig.drawerLargeAvatar));
            items.add(UItem.asButton(BUTTON_DRAWER_HEADER, getString(R.string.MZGramDrawerHeader), MZGramDrawer.headerStyleTitle()));
            items.add(UItem.asCheck(BUTTON_DRAWER_GLASS, getString(R.string.MZGramDrawerGlass)).setChecked(MZGramConfig.drawerGlass));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramNavigationDrawerInfo)));

        // ==========================================
        // Card 4: Menu Items & Actions
        // ==========================================
        items.add(UItem.asHeader(getString(R.string.MZGramMainMenuItems)));
        items.add(UItem.asButton(BUTTON_DRAWER_ITEMS, getString(R.string.MZGramDrawerItems)));
        items.add(UItem.asCheck(BUTTON_ACCOUNTS_IN_MENU, getString(R.string.MZGramAccountsInMenu)).setChecked(MZGramConfig.accountsInMenu));
        items.add(UItem.asButton(BUTTON_ACCOUNT_ORDER, getString(R.string.MZGramAccountOrder)));
        items.add(UItem.asCheck(BUTTON_RECENT_CHATS_ON_BACK, getString(R.string.MZGramRecentChatsOnBack)).setChecked(MZGramConfig.recentChatsOnBack));
        items.add(UItem.asShadow(getString(R.string.MZGramMainMenuItemsInfo)));
    }

    private void fillHiding(ArrayList<UItem> items) {
        items.add(UItem.asHeader(getString(R.string.MZGramAppearanceHiding)));
        items.add(UItem.asCheck(BUTTON_HIDE_ACTION_BAR_STATUS, getString(R.string.MZGramHideActionBarStatus)).setChecked(MZGramConfig.hideActionBarStatus));
        items.add(check(BUTTON_HIDE_STORIES, R.string.MZGramHideStories, MZGramConfig.hideStories, R.string.MZGramHideStoriesInfo));
        items.add(check(BUTTON_STORIES_NO_BACKGROUND_LOAD, R.string.MZGramStoriesNoBackgroundLoad, MZGramConfig.storiesNoBackgroundLoad, R.string.MZGramStoriesNoBackgroundLoadInfo));
        items.add(UItem.asCheck(BUTTON_HIDE_FLOATING_BUTTON, getString(R.string.MZGramHideFloatingButton)).setChecked(MZGramConfig.hideFloatingButton));
        items.add(UItem.asCheck(BUTTON_HIDE_DIALOGS_SEARCH_BAR, getString(R.string.MZGramHideDialogsSearchBar)).setChecked(MZGramConfig.hideDialogsSearchBar));
        items.add(UItem.asShadow(null));
    }

    private int md3SelectedCount() {
        int count = 0;
        if (MZGramConfig.m3Loading) count++;
        if (MZGramConfig.m3Slider) count++;
        if (MZGramConfig.m3Switches) count++;
        if (MZGramConfig.m3ChatHeader) count++;
        if (MZGramConfig.m3NavBar) count++;
        if (MZGramConfig.cardsDesign) count++;
        if (MZGramConfig.musicPlayer) count++;
        if (MZGramConfig.musicPlayerMini) count++;
        return count;
    }

    private void toggleAllMd3Styles() {
        boolean enable = md3SelectedCount() == 0;
        MZGramConfig.setM3Loading(enable);
        MZGramConfig.setM3Slider(enable);
        MZGramConfig.setM3Switches(enable);
        MZGramConfig.setM3ChatHeader(enable);
        MZGramConfig.setM3NavBar(enable);
        MZGramConfig.setCardsDesign(enable);
        MZGramConfig.setMusicPlayer(enable);
        MZGramConfig.setMusicPlayerMini(enable);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private int iosSelectedCount() {
        int count = 0;
        if (MZGramConfig.iosNavBar) count++;
        if (MZGramConfig.iosChatHeader) count++;
        return count;
    }

    private void toggleAllIosStyles() {
        boolean enable = iosSelectedCount() == 0;
        MZGramConfig.setIosNavBar(enable);
        MZGramConfig.setIosChatHeader(enable);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private String folderTabCounterValue() {
        if (MZGramConfig.folderTabCounterMode == 1) {
            return getString(R.string.MZGramFolderTabCounterUnmuted);
        } else if (MZGramConfig.folderTabCounterMode == 2) {
            return getString(R.string.MZGramFolderTabCounterOff);
        }
        return getString(R.string.MZGramFolderTabCounterAll);
    }

    // Chats: stickers, gestures, the chat and its messages, camera, photo,
    // video, music and calls
    private void fillChats(ArrayList<UItem> items) {
        items.add(UItem.asHeader(getString(R.string.MZGramStickers)));
        items.add(UItem.asIntSlideView(1, MZGramStickers.SIZE_MIN, MZGramConfig.stickerSize, MZGramStickers.SIZE_MAX,
                val -> val + "%", MZGramConfig::setStickerSize));
        items.add(UItem.asButton(BUTTON_STICKER_SHAPE, getString(R.string.MZGramStickerShape), stickerShapeTitle(MZGramConfig.stickerShape)));
        items.add(UItem.asCheck(BUTTON_STICKER_TIME, getString(R.string.MZGramStickerTime)).setChecked(MZGramConfig.hideStickerTime));
        items.add(check(BUTTON_UNLIMITED_RECENT_STICKERS, R.string.MZGramUnlimitedRecentStickers, MZGramConfig.unlimitedRecentStickers, R.string.MZGramUnlimitedRecentStickersInfo));
        items.add(check(BUTTON_UNLIMITED_SAVED_GIFS, R.string.MZGramUnlimitedSavedGifs, MZGramConfig.unlimitedSavedGifs, R.string.MZGramUnlimitedSavedGifsInfo));
        items.add(check(BUTTON_DISABLE_TRENDING, R.string.MZGramDisableTrending, MZGramConfig.disableTrending, R.string.MZGramDisableTrendingInfo));
        items.add(UItem.asCheck(BUTTON_HIDE_GROUP_STICKER, getString(R.string.MZGramHideGroupSticker)).setChecked(MZGramConfig.hideGroupSticker));
        items.add(UItem.asShadow(getString(R.string.MZGramStickersInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderReplies)));
        items.add(UItem.asCheck(BUTTON_REPLY_COLORS, getString(R.string.MZGramReplyColors)).setChecked(MZGramConfig.replyColors));
        items.add(UItem.asCheck(BUTTON_REPLY_EMOJI, getString(R.string.MZGramReplyEmoji)).setChecked(MZGramConfig.replyEmoji));
        items.add(UItem.asCheck(BUTTON_REPLY_BACKGROUND, getString(R.string.MZGramReplyBackground)).setChecked(MZGramConfig.replyBackground));
        items.add(UItem.asShadow(getString(R.string.MZGramRepliesInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderGestures)));
        items.add(check(BUTTON_DOUBLE_TAP_ACTIONS, R.string.MZGramDoubleTapActions, MZGramConfig.doubleTapActions, R.string.MZGramDoubleTapActionsInfo));
        if (MZGramConfig.doubleTapActions) {
            items.add(UItem.asButton(BUTTON_DOUBLE_TAP_OWN, getString(R.string.MZGramDoubleTapOwn), getString(org.telegram.ui.mzgram.MZGramDoubleTap.TITLES[MZGramConfig.doubleTapOwn])));
            items.add(UItem.asButton(BUTTON_DOUBLE_TAP_OTHER, getString(R.string.MZGramDoubleTapOther), getString(org.telegram.ui.mzgram.MZGramDoubleTap.TITLES[MZGramConfig.doubleTapOther])));
        }
        items.add(UItem.asButton(BUTTON_SWIPE_ACTION, getString(R.string.MZGramSwipeAction), getString(org.telegram.ui.mzgram.MZGramSwipeAction.TITLES[MZGramConfig.swipeAction])));
        items.add(UItem.asShadow(getString(R.string.MZGramSwipeActionInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramSectionChats)));
        items.add(UItem.asButton(BUTTON_FEED_PAGE, getString(R.string.MZGramFeed)));
        items.add(UItem.asButton(BUTTON_CHANNEL_BOTTOM_BUTTON, getString(R.string.MZGramChannelBottomButton), getString(CHANNEL_BOTTOM_TITLES[MZGramConfig.channelBottomButton])));
        items.add(check(BUTTON_QUICK_ADMIN_SHORTCUTS, R.string.MZGramQuickAdminShortcuts, MZGramConfig.quickAdminShortcuts, R.string.MZGramQuickAdminShortcutsInfo));
        if (MZGramConfig.quickAdminShortcuts) {
            // Telegram's own text: the name of the screen this item opens
            items.add(UItem.asCheck(BUTTON_ADMIN_SHORTCUT_RECENT, getString(R.string.EventLog)).setChecked(MZGramConfig.adminShortcutRecentActions));
            // Telegram's own text: the name of the screen this item opens
            items.add(UItem.asCheck(BUTTON_ADMIN_SHORTCUT_ADMINS, getString(R.string.ChannelAdministrators)).setChecked(MZGramConfig.adminShortcutAdministrators));
            // Telegram's own text: the name of the screen this item opens
            items.add(UItem.asCheck(BUTTON_ADMIN_SHORTCUT_PERMISSIONS, getString(R.string.ChannelPermissions)).setChecked(MZGramConfig.adminShortcutPermissions));
            // Telegram's own text: the name of the screen this item opens
            items.add(UItem.asCheck(BUTTON_ADMIN_SHORTCUT_MEMBERS, getString(R.string.GroupMembers)).setChecked(MZGramConfig.adminShortcutMembers));
            // Telegram's own text: the name of the screen this item opens
            items.add(UItem.asCheck(BUTTON_ADMIN_SHORTCUT_STATISTICS, getString(R.string.Statistics)).setChecked(MZGramConfig.adminShortcutStatistics));
        }
        items.add(check(BUTTON_CHAT_MENU_TO_BEGINNING, R.string.MZGramChatMenuToBeginning, MZGramConfig.chatMenuToBeginning, R.string.MZGramChatMenuToBeginningInfo));
        items.add(UItem.asCheck(BUTTON_CHAT_MENU_BOOST, getString(R.string.MZGramChatMenuBoost)).setChecked(MZGramConfig.chatMenuBoost));
        items.add(check(BUTTON_CHAT_MENU_LINKED_CHAT, R.string.MZGramChatMenuLinkedChat, MZGramConfig.chatMenuLinkedChat, R.string.MZGramChatMenuLinkedChatInfo));
        items.add(check(BUTTON_CHAT_MENU_GO_TO_MESSAGE, R.string.MZGramChatMenuGoToMessage, MZGramConfig.chatMenuGoToMessage, R.string.MZGramChatMenuGoToMessageInfo));
        items.add(check(BUTTON_CHAT_MENU_HIDE_TITLE, R.string.MZGramChatMenuHideTitle, MZGramConfig.chatMenuHideTitle, R.string.MZGramChatMenuHideTitleInfo));
        items.add(check(BUTTON_CHAT_MENU_CLEAR_DELETED, R.string.MZGramChatMenuClearDeleted, MZGramConfig.chatMenuClearDeleted, R.string.MZGramChatMenuClearDeletedInfo));
        items.add(check(BUTTON_CHAT_MENU_DELETE_OWN, R.string.MZGramChatMenuDeleteOwn, MZGramConfig.chatMenuDeleteOwn, R.string.MZGramChatMenuDeleteOwnInfo));
        items.add(check(BUTTON_WIDE_CHANNEL_POSTS, R.string.MZGramWideChannelPosts, MZGramConfig.wideChannelPosts, R.string.MZGramWideChannelPostsInfo));
        items.add(check(BUTTON_DISABLE_GREETING_STICKER, R.string.MZGramDisableGreetingSticker, MZGramConfig.disableGreetingSticker, R.string.MZGramDisableGreetingStickerInfo));
        items.add(UItem.asCheck(BUTTON_HIDE_KEYBOARD_ON_SCROLL, getString(R.string.MZGramHideKeyboardOnScroll)).setChecked(MZGramConfig.hideKeyboardOnScroll));
        items.add(UItem.asCheck(BUTTON_ADD_COMMA_AFTER_MENTION, getString(R.string.MZGramAddCommaAfterMention)).setChecked(MZGramConfig.addCommaAfterMention));
        items.add(check(BUTTON_HIDE_SEND_AS_PEER, R.string.MZGramHideSendAsPeer, MZGramConfig.hideSendAsPeer, R.string.MZGramHideSendAsPeerInfo));
        items.add(check(BUTTON_DISABLE_GLOBAL_SEARCH, R.string.MZGramDisableGlobalSearch, MZGramConfig.disableGlobalSearch, R.string.MZGramDisableGlobalSearchInfo));
        items.add(check(BUTTON_GRANULAR_PERMISSIONS, R.string.MZGramGranularPermissions, MZGramConfig.granularGroupPermissions, R.string.MZGramGranularPermissionsInfo));
        items.add(check(BUTTON_SPLIT_GIF_INLINE, R.string.MZGramSplitGifInline, MZGramConfig.splitGifInlineRights, R.string.MZGramSplitGifInlineInfo));
        items.add(check(BUTTON_SCHEDULE_SLIDER, R.string.MZGramScheduleSlider, MZGramConfig.scheduleSlider, R.string.MZGramScheduleSliderInfo));
        items.add(check(BUTTON_SILENT_DEFAULT, R.string.MZGramSilentDefault, MZGramConfig.sendSilentByDefault, R.string.MZGramSilentDefaultInfo));
        items.add(check(BUTTON_CHARACTER_COUNTER, R.string.MZGramCharacterCounter, MZGramConfig.characterCounter, R.string.MZGramCharacterCounterInfo));
        items.add(check(BUTTON_PANGU, R.string.MZGramPangu, MZGramConfig.panguSpacing, R.string.MZGramPanguInfo));
        items.add(UItem.asButton(BUTTON_PINNED_REACTIONS, getString(R.string.MZGramPinnedReactions), MZGramPinnedReactions.getRowDescription()));
        items.add(UItem.asButton(BUTTON_TEXT_STYLE_MENU, getString(R.string.MZGramTextStyleMenu)));
        items.add(UItem.asButton(BUTTON_OPEN_BY_ID, getString(R.string.MZGramOpenById)));
        items.add(UItem.asShadow(getString(R.string.MZGramOpenByIdInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderTelegramAi)));
        items.add(UItem.asCheck(BUTTON_TELEGRAM_AI_EDITOR, getString(R.string.MZGramTelegramAiEditor)).setChecked(MZGramConfig.telegramAiEditor));
        items.add(UItem.asCheck(BUTTON_TELEGRAM_AI_SUMMARY, getString(R.string.MZGramTelegramAiSummary)).setChecked(MZGramConfig.telegramAiSummary));
        items.add(UItem.asCheck(BUTTON_TELEGRAM_AI_IV_SUMMARY, getString(R.string.MZGramTelegramAiIvSummary)).setChecked(MZGramConfig.telegramAiIvSummary));
        items.add(UItem.asShadow(getString(R.string.MZGramTelegramAiInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramQuickTransition)));
        items.add(UItem.asCheck(BUTTON_QUICK_TRANSITION_CHANNELS, getString(R.string.MZGramQuickTransitionChannels)).setChecked(MZGramConfig.quickTransitionChannels));
        items.add(UItem.asCheck(BUTTON_QUICK_TRANSITION_TOPICS, getString(R.string.MZGramQuickTransitionTopics)).setChecked(MZGramConfig.quickTransitionTopics));
        items.add(UItem.asShadow(getString(R.string.MZGramQuickTransitionInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderChatScreen)));
        items.add(check(BUTTON_HIDE_CHAT_STATUS, R.string.MZGramHideChatStatus, MZGramConfig.hideChatStatus, R.string.MZGramHideChatStatusInfo));
        items.add(check(BUTTON_COMPACT_CHAT_HEADER, R.string.MZGramCompactChatHeader, MZGramConfig.compactChatHeader, R.string.MZGramCompactChatHeaderInfo));
        items.add(check(BUTTON_STATIC_INPUT_ICONS, R.string.MZGramStaticInputIcons, MZGramConfig.staticInputIcons, R.string.MZGramStaticInputIconsInfo));
        items.add(UItem.asCheck(BUTTON_HIDE_CHANNEL_GIFT, getString(R.string.MZGramHideChannelGiftButton)).setChecked(MZGramConfig.hideChannelGiftButton));
        items.add(UItem.asCheck(BUTTON_HIDE_CHANNEL_SEARCH, getString(R.string.MZGramHideChannelSearchButton)).setChecked(MZGramConfig.hideChannelSearchButton));
        items.add(check(BUTTON_HIDE_CALL_BUTTON, R.string.MZGramHideCallButton, MZGramConfig.hideCallButton, R.string.MZGramHideCallButtonInfo));
        // The tab's name in the MZGram language: Telegram's own one would mix two languages in the row
        items.add(UItem.asCheck(BUTTON_HIDE_ATTACH_MONEY, getString(R.string.MZGramHideAttachMoney)).setChecked(MZGramConfig.hideAttachMoney));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderMessages)));
        items.add(UItem.asCheck(BUTTON_REMOVE_MESSAGE_TAIL, getString(R.string.MZGramRemoveMessageTail)).setChecked(MZGramConfig.removeMessageTail));
        items.add(UItem.asCheck(BUTTON_REPLACE_EDITED_WITH_ICON, getString(R.string.MZGramReplaceEditedWithIcon)).setChecked(MZGramConfig.replaceEditedWithIcon));
        items.add(check(BUTTON_SHOW_ONLINE_STATUS, R.string.MZGramShowOnlineStatus, MZGramConfig.showOnlineStatus, R.string.MZGramShowOnlineStatusInfo));
        items.add(UItem.asCheck(BUTTON_SHOW_FORWARDS_COUNT, getString(R.string.MZGramShowForwardsCount)).setChecked(MZGramConfig.showForwardsCount));
        items.add(UItem.asCheck(BUTTON_HIDE_SHARE_BUTTON, getString(R.string.MZGramHideShareButton)).setChecked(MZGramConfig.hideShareButton));
        items.add(check(BUTTON_SHOW_RESULTS_BEFORE_VOTING, R.string.MZGramShowResultsBeforeVoting, MZGramConfig.showResultsBeforeVoting, R.string.MZGramShowResultsBeforeVotingInfo));
        items.add(UItem.asCheck(BUTTON_FORWARDED_DATE, getString(R.string.MZGramForwardedDate)).setChecked(MZGramConfig.forwardedDate));
        items.add(UItem.asCheck(BUTTON_SMALL_GIFS, getString(R.string.MZGramSmallGifs)).setChecked(MZGramConfig.smallGifs));
        items.add(check(BUTTON_REMEMBER_ALL_REPLIES, R.string.MZGramRememberAllReplies, MZGramConfig.rememberAllReplies, R.string.MZGramRememberAllRepliesInfo));
        items.add(UItem.asButton(BUTTON_MESSAGE_MENU, getString(R.string.MZGramSectionMessageMenu), messageMenuValue()));
        items.add(check(BUTTON_GROUP_MESSAGE_MENU, R.string.MZGramGroupMessageMenu, MZGramConfig.groupMessageMenu, R.string.MZGramGroupMessageMenuInfo));
        items.add(check(BUTTON_LINKED_EMOJI, R.string.MZGramLinkedEmoji, MZGramConfig.linkedCustomEmoji, R.string.MZGramLinkedEmojiInfo));
        items.add(check(BUTTON_LINK_TYPEFACE, R.string.MZGramLinkTypeface, MZGramConfig.linkTypeface, R.string.MZGramLinkTypefaceInfo));
        items.add(check(BUTTON_PLAIN_CODE, R.string.MZGramPlainCode, MZGramConfig.plainCodeBlocks, R.string.MZGramPlainCodeInfo));
        items.add(check(BUTTON_HEX_COLOR, R.string.MZGramHexColorSwatch, MZGramConfig.hexColorSwatch, R.string.MZGramHexColorSwatchInfo));
        items.add(check(BUTTON_INLINE_MATH, R.string.MZGramInlineMath, MZGramConfig.inlineMath, R.string.MZGramInlineMathInfo));
        items.add(check(BUTTON_INLINE_UNITS, R.string.MZGramInlineUnits, MZGramConfig.inlineUnits, R.string.MZGramInlineUnitsInfo));
        items.add(check(BUTTON_INLINE_TIME_ZONES, R.string.MZGramInlineTimeZones, MZGramConfig.inlineTimeZones, R.string.MZGramInlineTimeZonesInfo));
        items.add(check(BUTTON_COPY_PLAIN, R.string.MZGramCopyPlain, MZGramConfig.copyPlainText, R.string.MZGramCopyPlainInfo));
        items.add(check(BUTTON_CLEAN_LINKS, R.string.MZGramCleanLinks, MZGramConfig.cleanLinks, R.string.MZGramCleanLinksInfo));
        items.add(UItem.asShadow(getString(R.string.MZGramSectionMessageMenuInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderSearch)));
        items.add(check(BUTTON_SEARCH_OPERATORS, R.string.MZGramSearchOperators, MZGramConfig.searchOperators, R.string.MZGramSearchOperatorsInfo));
        items.add(check(BUTTON_FOLD_FANCY_FONTS, R.string.MZGramFoldFancyFonts, MZGramConfig.foldFancyFontsInSearch, R.string.MZGramFoldFancyFontsInfo));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderCamera)));
        items.add(check(BUTTON_CAMERA_X, R.string.MZGramCameraX, MZGramConfig.cameraX, R.string.MZGramCameraXInfo));
        if (MZGramConfig.cameraX) {
            items.add(UItem.asCheck(BUTTON_CAMERA_X_WIDE, getString(R.string.MZGramCameraXWide)).setChecked(MZGramConfig.cameraXWideLens));
            items.add(UItem.asCheck(BUTTON_CAMERA_X_ZOOM, getString(R.string.MZGramCameraXZoom)).setChecked(MZGramConfig.cameraXZoomSlider));
        }
        items.add(check(BUTTON_CAMERA_STABILIZATION, R.string.MZGramCameraStabilization, MZGramConfig.cameraStabilization, R.string.MZGramCameraStabilizationInfo));
        items.add(check(BUTTON_ROUND_VIDEO_CAMERA2, R.string.MZGramRoundVideoCamera2, MZGramConfig.roundVideoCamera2, R.string.MZGramRoundVideoCamera2Info));
        if (MZGramConfig.roundVideoCamera2) {
            items.add(check(BUTTON_ROUND_VIDEO_HIGH_FPS, R.string.MZGramRoundVideoHighFps, MZGramConfig.roundVideoHighFps, R.string.MZGramRoundVideoHighFpsInfo));
            items.add(UItem.asButton(BUTTON_ROUND_VIDEO_BITRATE, getString(R.string.MZGramRoundVideoBitrate), formatRoundBitrate(MZGramConfig.roundVideoBitrate)));
            items.add(UItem.asButton(BUTTON_ROUND_VIDEO_ADVANCED, getString(R.string.MZGramRoundVideoAdvanced)));
        }
        items.add(check(BUTTON_DISABLE_INSTANT_CAMERA, R.string.MZGramDisableInstantCamera, MZGramConfig.disableInstantCamera, R.string.MZGramDisableInstantCameraInfo));
        items.add(UItem.asButton(BUTTON_ROUND_VIDEO_CAMERA, getString(R.string.MZGramRoundVideoCamera), getString(MZGramConfig.roundVideoFrontCamera ? R.string.MZGramCameraFront : R.string.MZGramCameraBack)));
        items.add(check(BUTTON_REMEMBER_LAST_CAMERA, R.string.MZGramRememberLastCamera, MZGramConfig.rememberLastCamera, R.string.MZGramRememberLastCameraInfo));
        items.add(check(BUTTON_STATIC_ZOOM, R.string.MZGramStaticZoom, MZGramConfig.staticZoom, R.string.MZGramStaticZoomInfo));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderPhoto)));
        items.add(check(BUTTON_ALWAYS_SEND_HD, R.string.MZGramAlwaysSendHd, MZGramConfig.alwaysSendHd, R.string.MZGramAlwaysSendHdInfo));
        items.add(check(BUTTON_HIDE_CAMERA_TILE, R.string.MZGramHideCameraTile, MZGramConfig.hideCameraTile, R.string.MZGramHideCameraTileInfo));
        items.add(check(BUTTON_LOSSLESS_PHOTO_CROP, R.string.MZGramLosslessPhotoCrop, MZGramConfig.losslessPhotoCrop, R.string.MZGramLosslessPhotoCropInfo));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderVideo)));
        items.add(UItem.asButton(BUTTON_DOUBLE_TAP_SEEK, getString(R.string.MZGramDoubleTapSeek), seekTitle(MZGramConfig.doubleTapSeekSeconds)));
        items.add(check(BUTTON_PREFER_ORIGINAL_QUALITY, R.string.MZGramPreferOriginalQuality, MZGramConfig.preferOriginalQuality, R.string.MZGramPreferOriginalQualityInfo));
        items.add(UItem.asCheck(BUTTON_SWIPE_TO_PIP, getString(R.string.MZGramSwipeToPip)).setChecked(MZGramConfig.swipeToPip));
        items.add(check(BUTTON_UNMUTE_WITH_VOLUME_BUTTONS, R.string.MZGramUnmuteWithVolumeButtons, MZGramConfig.unmuteWithVolumeButtons, R.string.MZGramUnmuteWithVolumeButtonsInfo));
        items.add(check(BUTTON_AUTO_PAUSE_VIDEO, R.string.MZGramAutoPauseVideo, MZGramConfig.autoPauseVideo, R.string.MZGramAutoPauseVideoInfo));
        items.add(UItem.asCheck(BUTTON_PAUSE_VOICE_ON_MINIMIZE, getString(R.string.MZGramPauseVoiceOnMinimize)).setChecked(MZGramConfig.pauseVoiceOnMinimize));
        items.add(UItem.asCheck(BUTTON_DONT_AUTOPLAY_NEXT_VOICE, getString(R.string.MZGramDontAutoPlayNextVoice)).setChecked(MZGramConfig.dontAutoPlayNextVoice));
        items.add(check(BUTTON_DISABLE_PROXIMITY, R.string.MZGramDisableProximity, MZGramConfig.disableProximity, R.string.MZGramDisableProximityInfo));
        items.add(check(BUTTON_PAUSE_ROUND, R.string.MZGramPauseRound, MZGramConfig.pauseRoundOnLeave, R.string.MZGramPauseRoundInfo));
        items.add(check(BUTTON_VIDEO_SUBTITLES, R.string.MZGramVideoSubtitles, MZGramConfig.videoSubtitles, R.string.MZGramVideoSubtitlesInfo));
        items.add(check(BUTTON_MEDIA_PREVIEW_ON_LONG_PRESS, R.string.MZGramMediaPreviewOnLongPress, MZGramConfig.mediaPreviewOnLongPress, R.string.MZGramMediaPreviewOnLongPressInfo));
        items.add(check(BUTTON_CONFIRM_AV_MESSAGE, R.string.MZGramConfirmAVMessage, MZGramConfig.confirmAVMessage, R.string.MZGramConfirmAVMessageInfo));
        items.add(UItem.asCheck(BUTTON_CONFIRM_REPEAT, getString(R.string.MZGramConfirmRepeat)).setChecked(MZGramConfig.confirmRepeat));
        items.add(UItem.asCheck(BUTTON_CONFIRM_BOT_COMMANDS, getString(R.string.MZGramConfirmBotCommands)).setChecked(MZGramConfig.confirmBotCommands));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderAudio)));
        items.add(check(BUTTON_MUSIC_PLAYER, R.string.MZGramMusicPlayer, MZGramConfig.musicPlayer, R.string.MZGramMusicPlayerInfo));
        if (MZGramConfig.musicPlayer) {
            items.add(UItem.asCheck(BUTTON_MUSIC_PLAYER_BAR, getString(R.string.MZGramMusicPlayerBar)).setChecked(MZGramConfig.musicPlayerBar));
            items.add(UItem.asCheck(BUTTON_MUSIC_PLAYER_MINI, getString(R.string.MZGramMusicPlayerMini)).setChecked(MZGramConfig.musicPlayerMini));
            items.add(UItem.asButton(BUTTON_MUSIC_PLAYER_STYLE, getString(R.string.MZGramMusicPlayerStyle)));
        }
        items.add(check(BUTTON_PROFILE_MUSIC_CARD, R.string.MZGramProfileMusicCard, MZGramConfig.profileMusicCard, R.string.MZGramProfileMusicCardInfo));
        items.add(check(BUTTON_AUDIO_ENHANCE, R.string.MZGramAudioEnhance, MZGramConfig.audioEnhance, R.string.MZGramAudioEnhanceInfo));
        items.add(UItem.asButton(BUTTON_VOICE_QUALITY, getString(R.string.MZGramVoiceQuality), getString(org.telegram.messenger.mzgram.MZGramVoiceQuality.TITLES[org.telegram.messenger.mzgram.MZGramVoiceQuality.mode()])));
        items.add(UItem.asShadow(getString(R.string.MZGramVoiceQualityInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramHeaderCalls)));
        items.add(check(BUTTON_ASK_BEFORE_CALL, R.string.MZGramAskBeforeCall, MZGramConfig.askBeforeCall, R.string.MZGramAskBeforeCallInfo));
        items.add(UItem.asShadow(null));
    }

    // The message menu: a page of Chats
    private void fillMessageMenu(ArrayList<UItem> items) {
        items.add(UItem.asHeader(getString(R.string.MZGramSectionMessageMenu)));
        items.add(check(BUTTON_SHOW_COPY_PHOTO, R.string.MZGramCopyPhoto, MZGramConfig.showCopyPhoto, R.string.MZGramShowCopyPhotoInfo));
        items.add(check(BUTTON_SHOW_DELETE_DOWNLOADED_FILE, R.string.MZGramDeleteDownloadedFile, MZGramConfig.showDeleteDownloadedFile, R.string.MZGramShowDeleteDownloadedFileInfo));
        items.add(check(BUTTON_SHOW_ADD_TO_SAVED_MESSAGES, R.string.MZGramSaveMessage, MZGramConfig.showAddToSavedMessages, R.string.MZGramShowSaveMessageInfo));
        items.add(check(BUTTON_SHOW_SET_REMINDER, R.string.MZGramSetReminder, MZGramConfig.showSetReminder, R.string.MZGramShowSetReminderInfo));
        items.add(check(BUTTON_SHOW_REPEAT, R.string.MZGramRepeat, MZGramConfig.showRepeat, R.string.MZGramShowRepeatInfo));
        items.add(check(BUTTON_SHOW_OPEN_IN, R.string.MZGramOpenIn, MZGramConfig.showOpenIn, R.string.MZGramShowOpenInInfo));
        items.add(check(BUTTON_SHOW_MESSAGE_DETAILS, R.string.MZGramMessageDetails, MZGramConfig.showMessageDetails, R.string.MZGramShowMessageDetailsInfo));
        items.add(check(BUTTON_SHOW_QR_CODE, R.string.MZGramQrCode, MZGramConfig.showQrCode, R.string.MZGramShowQrCodeInfo));
        items.add(check(BUTTON_SHOW_NO_QUOTE_FORWARD, R.string.MZGramForwardNoQuote, MZGramConfig.showNoQuoteForward, R.string.MZGramShowForwardNoQuoteInfo));
        items.add(UItem.asShadow(null));
        items.add(check(BUTTON_SAVE_PROTECTED_CONTENT, R.string.MZGramSaveProtectedContent, MZGramConfig.saveProtectedContent, R.string.MZGramSaveProtectedContentInfo));
        items.add(check(BUTTON_DELETE_FOR_ALL_BY_DEFAULT, R.string.MZGramDeleteForAllByDefault, MZGramConfig.deleteForAllByDefault, R.string.MZGramDeleteForAllByDefaultInfo));
        items.add(UItem.asShadow(null));
    }

    private void fillAdsAndFilters(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_DISABLE_SPONSORED_MESSAGES, getString(R.string.MZGramDisableSponsoredMessages)).setChecked(MZGramConfig.disableSponsoredMessages));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableSponsoredMessagesInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramReactionFilter)));
        items.add(UItem.asCheck(BUTTON_HIDE_REACTIONS_PRIVATE, getString(R.string.MZGramHideReactionsPrivate)).setChecked(MZGramConfig.hideReactionsPrivate));
        items.add(UItem.asCheck(BUTTON_HIDE_REACTIONS_GROUPS, getString(R.string.MZGramHideReactionsGroups)).setChecked(MZGramConfig.hideReactionsGroups));
        items.add(UItem.asCheck(BUTTON_HIDE_REACTIONS_CHANNELS, getString(R.string.MZGramHideReactionsChannels)).setChecked(MZGramConfig.hideReactionsChannels));
        items.add(UItem.asButton(BUTTON_HIDDEN_REACTIONS, getString(R.string.MZGramHiddenReactions), android.text.TextUtils.isEmpty(MZGramConfig.hiddenReactions) ? getString(R.string.MZGramOff) : MZGramConfig.hiddenReactions));
        items.add(UItem.asShadow(getString(R.string.MZGramReactionFilterInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramShadowBan)));
        items.add(UItem.asCheck(BUTTON_SHADOW_BAN, getString(R.string.MZGramShadowBanEnable)).setChecked(MZGramConfig.shadowBan));
        if (MZGramConfig.shadowBan) {
            items.add(UItem.asButton(BUTTON_SHADOW_BAN_LIST, getString(R.string.MZGramShadowBanList), String.valueOf(org.telegram.messenger.mzgram.MZGramShadowBan.count())));
        }
        items.add(UItem.asCheck(BUTTON_HIDE_BLOCKED_IN_GROUPS, getString(R.string.MZGramHideBlockedInGroups)).setChecked(MZGramConfig.hideBlockedInGroups));
        items.add(UItem.asShadow(getString(R.string.MZGramShadowBanInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramRegexFilters)));
        items.add(UItem.asCheck(BUTTON_REGEX_FILTERS, getString(R.string.MZGramRegexFiltersEnable)).setChecked(MZGramConfig.regexFilters));
        if (MZGramConfig.regexFilters) {
            items.add(UItem.asButton(BUTTON_REGEX_FILTERS_RULES, getString(R.string.MZGramRegexFiltersRules), String.valueOf(org.telegram.messenger.mzgram.MZGramRegexFilters.count(org.telegram.messenger.mzgram.MZGramRegexFilters.COMMON))));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramRegexFiltersInfo)));
    }

    private void fillAi(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_SHOW_ASK_AI, getString(R.string.MZGramShowAskAi)).setChecked(MZGramConfig.showAskAi));
        items.add(UItem.asButton(BUTTON_AI_PROVIDER, getString(R.string.MZGramAiDefaultProvider), org.telegram.messenger.mzgram.MZGramAiRules.defaultProvider().name));
        items.add(UItem.asShadow(getString(R.string.MZGramAiInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramAiMenuButtons)));
        java.util.List<org.telegram.messenger.mzgram.MZGramAiRules.Provider> providers = org.telegram.messenger.mzgram.MZGramAiRules.providers();
        for (int i = 0; i < providers.size(); i++) {
            items.add(UItem.asCheck(AI_MENU_BUTTONS[i], providers.get(i).name).setChecked(org.telegram.messenger.mzgram.MZGramAiRules.menuButton(providers.get(i))));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramAiMenuButtonsInfo)));
        items.add(UItem.asButton(BUTTON_AI_SIGN_OUT, getString(R.string.MZGramAiSignOut)).red());
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.MZGramTranslationAiHeader)));
        items.add(UItem.asButton(BUTTON_TRANSLATION_AI, getString(R.string.MZGramTranslationAiSettings), getString(R.string.MZGramTranslationAiSettingsSub)));
        items.add(UItem.asShadow(getString(R.string.MZGramTranslationAiInfo)));
    }

    private static final String DELETE_ACCOUNT_URL = "https://my.telegram.org/deactivate";

    private void confirmResetSettings() {
        if (getParentActivity() == null) {
            return;
        }
        org.telegram.ui.ActionBar.AlertDialog dialog = new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.MZGramResetSettings))
                .setMessage(getString(R.string.MZGramResetSettingsConfirm))
                .setPositiveButton(getString(R.string.MZGramResetSettingsButton), (d, which) -> org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                    int removed = 0;
                    try {
                        removed = org.telegram.messenger.mzgram.MZGramSettingsBackup.reset();
                    } catch (Exception e) {
                        org.telegram.messenger.FileLog.e("MZGramSettingsActivity.resetSettings", e);
                    }
                    final int count = removed;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() == null) {
                            return;
                        }
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                                org.telegram.messenger.LocaleController.formatString(R.string.MZGramResetSettingsDone, count)).show();
                        rebuildBelow();
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    });
                }))
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        showDialog(dialog);
        dialog.redPositive();
    }

    // Telegram's own page for deleting an account, opened in the browser after a warning
    private void confirmDeleteAccount() {
        if (getParentActivity() == null) {
            return;
        }
        org.telegram.ui.ActionBar.AlertDialog dialog = new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.MZGramDeleteAccount))
                .setMessage(getString(R.string.MZGramDeleteAccountConfirm))
                .setPositiveButton(getString(R.string.MZGramContinue), (d, which) -> org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), DELETE_ACCOUNT_URL))
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        showDialog(dialog);
        dialog.redPositive();
    }

    private void fillFeed(ArrayList<UItem> items) {
        items.add(UItem.asHeader(getString(R.string.MZGramFeed)));
        items.add(UItem.asButton(BUTTON_FEED_OPEN, getString(R.string.MZGramFeedOpen)));
        items.add(UItem.asShadow(getString(R.string.MZGramFeedInfo)));
        items.add(check(BUTTON_FEED_TAB, R.string.MZGramFeedTab, MZGramConfig.feedTab, R.string.MZGramFeedTabInfo));
        items.add(UItem.asCheck(BUTTON_FEED_UNREAD_COUNTER, getString(R.string.MZGramFeedUnreadCounter)).setChecked(MZGramConfig.feedUnreadCounter));
        items.add(check(BUTTON_FEED_WIDE_POSTS, R.string.MZGramFeedWidePosts, MZGramConfig.feedWidePosts, R.string.MZGramFeedWidePostsInfo));
        items.add(UItem.asCheck(BUTTON_FEED_INCLUDE_ARCHIVED, getString(R.string.MZGramFeedIncludeArchived)).setChecked(MZGramConfig.feedIncludeArchived));
        items.add(UItem.asButton(BUTTON_FEED_POSTS, getString(R.string.MZGramFeedPostsPerChannel), Integer.toString(org.telegram.messenger.mzgram.MZGramFeed.postsPerChannel())));
        items.add(UItem.asButton(BUTTON_FEED_CHANNELS, getString(R.string.MZGramFeedChannels), Integer.toString(org.telegram.messenger.mzgram.MZGramFeed.channels(currentAccount).size())));
        items.add(UItem.asShadow(getString(R.string.MZGramFeedChannelsInfo)));
    }

    private void fillOther(ArrayList<UItem> items) {
        items.add(UItem.asHeader(getString(R.string.MZGramHeaderUpdates)));
        items.add(check(BUTTON_CHECK_UPDATES, R.string.MZGramCheckUpdates, MZGramConfig.checkUpdates, R.string.MZGramCheckUpdatesInfo));
        items.add(UItem.asButton(BUTTON_CHECK_UPDATES_NOW, getString(R.string.MZGramUpdateCheckNow)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.MZGramSettingsBackup)));
        items.add(check(BUTTON_PLUGINS_BACKUP, R.string.MZGramPluginsBackupSwitch, MZGramConfig.pluginsBackup, R.string.MZGramPluginsBackupSwitchInfo));
        items.add(UItem.asButton(BUTTON_SETTINGS_EXPORT, getString(R.string.MZGramSettingsExport)));
        items.add(UItem.asButton(BUTTON_SETTINGS_IMPORT, getString(R.string.MZGramSettingsImport)));
        items.add(UItem.asButton(BUTTON_RESET_SETTINGS, getString(R.string.MZGramResetSettings)));
        items.add(UItem.asShadow(getString(R.string.MZGramSettingsBackupInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramDiagnostics)));
        items.add(UItem.asCheck(BUTTON_CRASH_REPORTS, getString(R.string.MZGramCrashReports)).setChecked(MZGramConfig.crashReports));
        items.add(check(BUTTON_CONTENT_CAPTURE, R.string.MZGramContentCapture, MZGramConfig.contentCapture, R.string.MZGramContentCaptureInfo));
        final boolean jankRunning = org.telegram.messenger.mzgram.MZGramJankProfiler.isRunning();
        items.add(UItem.asButton(BUTTON_JANK_PROFILER, getString(R.string.MZGramJankProfiler), getString(jankRunning ? R.string.MZGramProfilerStop : R.string.MZGramProfilerStart)));
        if (!jankRunning && org.telegram.messenger.mzgram.MZGramJankProfiler.getLastReport() != null) {
            items.add(UItem.asButton(BUTTON_JANK_REPORT, getString(R.string.MZGramJankProfilerShare)));
        }
        final boolean energyRunning = org.telegram.messenger.mzgram.MZGramEnergyProfiler.isRunning();
        items.add(UItem.asButton(BUTTON_ENERGY_PROFILER, getString(R.string.MZGramEnergyProfiler), getString(energyRunning ? R.string.MZGramProfilerStop : R.string.MZGramProfilerStart)));
        if (energyRunning || org.telegram.messenger.mzgram.MZGramEnergyProfiler.getLastReport() != null) {
            items.add(UItem.asButton(BUTTON_ENERGY_REPORT, getString(R.string.MZGramEnergyProfilerShare)));
        }
        if (org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.isAvailable()) {
            // Only in a build with the Python runtime (-PmzgramPlugins=true)
            items.add(UItem.asButton(BUTTON_PYTHON_RUNTIME, getString(R.string.MZGramPythonRuntime),
                    getString(org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.isStarted() ? R.string.MZGramPythonRuntimeReady : R.string.MZGramProfilerStart)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramDiagnosticsInfo)));
        items.add(UItem.asButton(BUTTON_PERSONALIZATION, R.drawable.msg_stories_stealth, getString(R.string.MZGramPersonalization)));
        items.add(UItem.asHeader(getString(R.string.MZGramPeripheralsHeader)));
        items.add(UItem.asButton(BUTTON_EXTENDED_PERIPHERALS, R.drawable.msg_customize, getString(R.string.MZGramPeripheralsTitle), getString(R.string.MZGramPeripheralsSub)));
        items.add(UItem.asShadow(getString(R.string.MZGramPeripheralsInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramHideRowsTitle)));
        java.util.List<org.telegram.messenger.mzgram.MZGramSettingsRows.Row> rows = org.telegram.messenger.mzgram.MZGramSettingsRows.rows();
        for (int i = 0; i < rows.size(); i++) {
            items.add(UItem.asCheck(HIDE_ROW_BUTTONS[i], getString(HIDE_ROW_TITLES[i])).setChecked(org.telegram.messenger.mzgram.MZGramSettingsRows.hidden(rows.get(i).key)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramHideRowsInfo)));
        items.add(UItem.asButton(BUTTON_DELETE_ACCOUNT, getString(R.string.MZGramDeleteAccount)).red());
        items.add(UItem.asShadow(getString(R.string.MZGramDeleteAccountInfo)));
    }

    // The five AI sites, each by its name; the choice applies at once.
    // The style of the Monet themes: Android's surfaces or Telegram's layout
    private void chooseMonetStyle() {
        if (getParentActivity() == null) {
            return;
        }
        int[] styles = {org.telegram.messenger.mzgram.MZGramMonet.STYLE_ANDROID, org.telegram.messenger.mzgram.MZGramMonet.STYLE_TELEGRAM};
        CharSequence[] names = new CharSequence[styles.length];
        for (int i = 0; i < styles.length; i++) {
            names[i] = org.telegram.messenger.mzgram.MZGramMonet.styleTitle(styles[i]);
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.MZGramMonetStyle));
        builder.setItems(names, (dialog, which) -> {
            org.telegram.messenger.mzgram.MZGramMonet.setStyle(styles[which]);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        showDialog(builder.create());
    }

    private void chooseAiProvider() {
        if (getParentActivity() == null) {
            return;
        }
        java.util.List<org.telegram.messenger.mzgram.MZGramAiRules.Provider> providers = org.telegram.messenger.mzgram.MZGramAiRules.providers();
        String[] options = new String[providers.size()];
        int selected = 0;
        for (int i = 0; i < options.length; i++) {
            options[i] = providers.get(i).name;
            if (providers.get(i) == org.telegram.messenger.mzgram.MZGramAiRules.defaultProvider()) {
                selected = i;
            }
        }
        showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramAiDefaultProvider), selected, (dialog, which) -> {
            org.telegram.messenger.mzgram.MZGramAiRules.setDefaultProvider(providers.get(which).key);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }, getString(R.string.MZGramCancel)));
    }

    private void onProfilerStarted(boolean running, int startedText) {
        if (running) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(startedText)).show();
        } else {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramProfilerFailed)).show();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void onProfilerStopped(java.io.File file) {
        if (isFinishing() || getParentActivity() == null) {
            return;
        }
        if (file != null && file.exists()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramProfilerSaved)).show();
        } else {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramProfilerFailed)).show();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void showPythonRuntime(boolean ok) {
        if (isFinishing() || getParentActivity() == null) {
            return;
        }
        if (ok) {
            // "Python 3.11.x" on the first line, the bundled packages after it
            final String description = org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.getDescription();
            String text = getString(R.string.MZGramPythonRuntime);
            if (!android.text.TextUtils.isEmpty(description)) {
                final int firstLine = description.indexOf('\n');
                text = firstLine < 0 ? description
                        : description.substring(0, firstLine) + "\n" + description.substring(firstLine + 1).replace("\n", ", ");
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, text, 4).show();
        } else {
            final String error = org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.getLastError();
            if (android.text.TextUtils.isEmpty(error)) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPythonRuntimeFailed)).show();
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.MZGramPythonRuntimeFailed), error).show();
            }
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void shareProfilerReport(java.io.File file) {
        if (isFinishing() || getParentActivity() == null) {
            return;
        }
        if (!org.telegram.messenger.mzgram.MZGramProfilerReports.share(getParentActivity(), file)) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramProfilerFailed)).show();
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= SECTION_ROW_ID && item.id < SECTION_ROW_ID + SECTIONS_COUNT) {
            int targetSection = item.id - SECTION_ROW_ID;
            if (targetSection == SECTION_PLUGINS) {
                presentFragment(new MZGramPluginsActivity());
            } else {
                presentFragment(new MZGramSettingsActivity(targetSection));
            }
        } else if (item.id == ROW_CHANNEL) {
            getMessagesController().openByUserName("mzgram", this, 1);
        } else if (item.id == ROW_SOURCE_CODE) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), org.telegram.messenger.mzgram.MZGramBranding.REPOSITORY_URL);
        } else if (item.id == ROW_CHATS_GROUP) {
            getMessagesController().openByUserName("mzgram_chat", this, 1);
        } else if (item.id == ROW_WEBSITE) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), "https://mzgram.org");
        } else if (item.id == BUTTON_PRIVACY_SECTION) {
            presentFragment(new MZGramSettingsActivity(SECTION_PRIVACY));
        } else if (item.id == BUTTON_UNIFIED_PUSH) {
            presentFragment(new MZGramUnifiedPushActivity());
        } else if (item.id == BUTTON_DEVICE_CHECK) {
            presentFragment(new MZGramDeviceCheckActivity());
        } else if (item.id == BUTTON_SHOW_TRANSLATE_BUTTON) {
            MZGramConfig.toggleChatTranslationBar();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.chatTranslationBar);
            }
        } else if (item.id == BUTTON_TELEGRAM_AUTO_TRANSLATE) {
            MZGramConfig.toggleTelegramUIAutoTranslate();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.telegramUIAutoTranslate);
            }
        } else if (item.id == BUTTON_TRANSLATION_PROVIDER) {
            chooseTranslationProvider();
        } else if (item.id == BUTTON_TRANSLATION_TARGET_LANG) {
            chooseTranslationTargetLang();
        } else if (item.id == BUTTON_DO_NOT_TRANSLATE) {
            presentFragment(new org.telegram.ui.RestrictedLanguagesSelectActivity());
        } else if (item.id == BUTTON_TRANSLATE_IN_SHEET) {
            MZGramConfig.toggleTranslateInSheet();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.translateInSheet);
            }
        } else if (item.id == BUTTON_TRANSLATION_SETTINGS) {
            presentFragment(new MZGramTranslationAiActivity());
        } else if (item.id == BUTTON_DOH_PROVIDER) {
            chooseDohProvider();
        } else if (item.id == BUTTON_CUSTOM_DOH) {
            chooseCustomDoh();
        } else if (item.id == BUTTON_MAP_PROVIDER) {
            chooseMapProvider();
        } else if (item.id == BUTTON_MAP_DRIFTING_FIX) {
            MZGramConfig.toggleMapDriftingFix();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.mapDriftingFix);
            }
        } else if (item.id == BUTTON_MAP_PREVIEW_PROVIDER) {
            chooseMapPreviewProvider();
        } else if (item.id == BUTTON_PUSH_STATUS) {
            AndroidUtilities.addToClipboard(MZGramUnifiedPushActivity.diagnosticsText());
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramTextCopied)).show();
        } else if (item.id == BUTTON_BATTERY_OPTIMIZATION) {
            openBatteryOptimization();
        } else if (item.id == BUTTON_GLYPH) {
            presentFragment(new MZGramGlyphActivity());
        } else if (item.id == BUTTON_PERSONALIZATION) {
            presentFragment(new MZGramSettingsActivity(SECTION_GHOST_MODE));
        } else if (item.id == BUTTON_MESSAGE_MENU) {
            presentFragment(new MZGramSettingsActivity(SECTION_MESSAGE_MENU));
        } else if (item.id == BUTTON_GLASS_MESSAGE_MENU) {
            MZGramConfig.toggleGlassMessageMenu();
            ((TextCheckCell) view).setChecked(MZGramConfig.glassMessageMenu);
        } else if (item.id == BUTTON_M3_SLIDER) {
            MZGramConfig.toggleM3Slider();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.m3Slider, true);
            } else if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.m3Slider);
            }
            rebuildBelow();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            if (listView != null) {
                listView.invalidateViews();
            }
        } else if (item.id == BUTTON_M3_LOADING) {
            MZGramConfig.toggleM3Loading();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.m3Loading, true);
            } else if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.m3Loading);
            }
            rebuildBelow();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_SEPARATE_HEADERS) {
            if (MZGramConfig.dividerStyle == MZGramConfig.DIVIDER_SEGMENTS) {
                return;
            }
            MZGramConfig.toggleSeparateHeaders();
            ((TextCheckCell) view).setChecked(MZGramConfig.separateHeaders);
            listView.invalidateItemDecorations();
            listView.adapter.update(true);
            rebuildBelow();
        } else if (item.id == BUTTON_FEED_INCLUDE_ARCHIVED) {
            MZGramConfig.toggleFeedIncludeArchived();
            ((TextCheckCell) view).setChecked(MZGramConfig.feedIncludeArchived);
        } else if (item.id == BUTTON_FEED_WIDE_POSTS) {
            MZGramConfig.toggleFeedWidePosts();
            ((TextCheckCell) view).setChecked(MZGramConfig.feedWidePosts);
        } else if (item.id == BUTTON_FEED_UNREAD_COUNTER) {
            MZGramConfig.toggleFeedUnreadCounter();
            ((TextCheckCell) view).setChecked(MZGramConfig.feedUnreadCounter);
        } else if (item.id == BUTTON_FEED_TAB) {
            MZGramConfig.toggleFeedTab();
            ((TextCheckCell) view).setChecked(MZGramConfig.feedTab);
            rebuildBelow();
        } else if (item.id == BUTTON_DELETED_MARK_ICON) {
            MZGramConfig.toggleDeletedMarkIcon();
            ((TextCheckCell) view).setChecked(MZGramConfig.deletedMarkIcon);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_SAVE_LAST_ONLINE) {
            MZGramConfig.toggleSaveLastOnline();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveLastOnline);
        } else if (item.id == BUTTON_SAVE_READ_DATES) {
            MZGramConfig.toggleSaveReadDates();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveReadDates);
        } else if (item.id == BUTTON_GHOST_STATUS_IN_TITLE) {
            MZGramConfig.toggleGhostStatusInTitle();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostStatusInTitle);
        } else if (item.id == BUTTON_GHOST_READ_ON_INTERACTION) {
            MZGramConfig.toggleGhostReadOnInteraction();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostReadOnInteraction);
        } else if (item.id == BUTTON_GHOST_OFFLINE_AUTO) {
            MZGramConfig.toggleGhostOfflineAuto();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostOfflineAuto);
            rebuildBelow();
        } else if (item.id == BUTTON_GHOST_NO_TYPING) {
            MZGramConfig.toggleGhostNoTyping();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostNoTyping);
            rebuildBelow();
        } else if (item.id == BUTTON_GHOST_NO_ONLINE) {
            MZGramConfig.toggleGhostNoOnline();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostNoOnline);
            rebuildBelow();
        } else if (item.id == BUTTON_GHOST_NO_READ_STORIES) {
            MZGramConfig.toggleGhostNoReadStories();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostNoReadStories);
            rebuildBelow();
        } else if (item.id == BUTTON_GHOST_NO_READ) {
            MZGramConfig.toggleGhostNoRead();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostNoRead);
            rebuildBelow();
        } else if (item.id == BUTTON_GROUP_MESSAGE_MENU) {
            MZGramConfig.toggleGroupMessageMenu();
            ((TextCheckCell) view).setChecked(MZGramConfig.groupMessageMenu);
        } else if (item.id == BUTTON_INLINE_TIME_ZONES) {
            MZGramConfig.toggleInlineTimeZones();
            ((TextCheckCell) view).setChecked(MZGramConfig.inlineTimeZones);
        } else if (item.id == BUTTON_INLINE_UNITS) {
            MZGramConfig.toggleInlineUnits();
            ((TextCheckCell) view).setChecked(MZGramConfig.inlineUnits);
        } else if (item.id == BUTTON_STATIC_ZOOM) {
            MZGramConfig.toggleStaticZoom();
            ((TextCheckCell) view).setChecked(MZGramConfig.staticZoom);
        } else if (item.id == BUTTON_REMEMBER_LAST_CAMERA) {
            MZGramConfig.toggleRememberLastCamera();
            ((TextCheckCell) view).setChecked(MZGramConfig.rememberLastCamera);
        } else if (item.id == BUTTON_QUICK_TRANSITION_TOPICS) {
            MZGramConfig.toggleQuickTransitionTopics();
            ((TextCheckCell) view).setChecked(MZGramConfig.quickTransitionTopics);
        } else if (item.id == BUTTON_QUICK_TRANSITION_CHANNELS) {
            MZGramConfig.toggleQuickTransitionChannels();
            ((TextCheckCell) view).setChecked(MZGramConfig.quickTransitionChannels);
        } else if (item.id == BUTTON_TELEGRAM_AI_IV_SUMMARY) {
            MZGramConfig.toggleTelegramAiIvSummary();
            ((TextCheckCell) view).setChecked(MZGramConfig.telegramAiIvSummary);
        } else if (item.id == BUTTON_TELEGRAM_AI_SUMMARY) {
            MZGramConfig.toggleTelegramAiSummary();
            ((TextCheckCell) view).setChecked(MZGramConfig.telegramAiSummary);
        } else if (item.id == BUTTON_TELEGRAM_AI_EDITOR) {
            MZGramConfig.toggleTelegramAiEditor();
            ((TextCheckCell) view).setChecked(MZGramConfig.telegramAiEditor);
        } else if (item.id == BUTTON_QUICK_ADMIN_SHORTCUTS) {
            MZGramConfig.toggleQuickAdminShortcuts();
            ((TextCheckCell) view).setChecked(MZGramConfig.quickAdminShortcuts);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true); // the items below show while it is on
            }
        } else if (item.id == BUTTON_ADMIN_SHORTCUT_RECENT) {
            MZGramConfig.toggleAdminShortcutRecentActions();
            ((TextCheckCell) view).setChecked(MZGramConfig.adminShortcutRecentActions);
        } else if (item.id == BUTTON_ADMIN_SHORTCUT_ADMINS) {
            MZGramConfig.toggleAdminShortcutAdministrators();
            ((TextCheckCell) view).setChecked(MZGramConfig.adminShortcutAdministrators);
        } else if (item.id == BUTTON_ADMIN_SHORTCUT_PERMISSIONS) {
            MZGramConfig.toggleAdminShortcutPermissions();
            ((TextCheckCell) view).setChecked(MZGramConfig.adminShortcutPermissions);
        } else if (item.id == BUTTON_ADMIN_SHORTCUT_MEMBERS) {
            MZGramConfig.toggleAdminShortcutMembers();
            ((TextCheckCell) view).setChecked(MZGramConfig.adminShortcutMembers);
        } else if (item.id == BUTTON_ADMIN_SHORTCUT_STATISTICS) {
            MZGramConfig.toggleAdminShortcutStatistics();
            ((TextCheckCell) view).setChecked(MZGramConfig.adminShortcutStatistics);
        } else if (item.id == BUTTON_SHOW_FORWARDS_COUNT) {
            MZGramConfig.toggleShowForwardsCount();
            ((TextCheckCell) view).setChecked(MZGramConfig.showForwardsCount);
        } else if (item.id == BUTTON_SHOW_ONLINE_STATUS) {
            MZGramConfig.toggleShowOnlineStatus();
            ((TextCheckCell) view).setChecked(MZGramConfig.showOnlineStatus);
        } else if (item.id == BUTTON_REPLACE_EDITED_WITH_ICON) {
            MZGramConfig.toggleReplaceEditedWithIcon();
            ((TextCheckCell) view).setChecked(MZGramConfig.replaceEditedWithIcon);
        } else if (item.id == BUTTON_DISABLE_GLOBAL_SEARCH) {
            MZGramConfig.toggleDisableGlobalSearch();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableGlobalSearch);
        } else if (item.id == BUTTON_HIDE_CHANNEL_GIFT) {
            MZGramConfig.toggleHideChannelGiftButton();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideChannelGiftButton);
        } else if (item.id == BUTTON_HIDE_CHANNEL_SEARCH) {
            MZGramConfig.toggleHideChannelSearchButton();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideChannelSearchButton);
        } else if (item.id == BUTTON_HIDE_CALL_BUTTON) {
            MZGramConfig.toggleHideCallButton();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideCallButton);
        } else if (item.id == BUTTON_FORWARDED_DATE) {
            MZGramConfig.toggleForwardedDate();
            ((TextCheckCell) view).setChecked(MZGramConfig.forwardedDate);
        } else if (item.id == BUTTON_SMALL_GIFS) {
            MZGramConfig.toggleSmallGifs();
            ((TextCheckCell) view).setChecked(MZGramConfig.smallGifs);
        } else if (item.id == BUTTON_REMEMBER_ALL_REPLIES) {
            MZGramConfig.toggleRememberAllReplies();
            ((TextCheckCell) view).setChecked(MZGramConfig.rememberAllReplies);
        } else if (item.id == BUTTON_DISABLE_TRENDING) {
            MZGramConfig.toggleDisableTrending();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableTrending);
        } else if (item.id == BUTTON_HIDE_GROUP_STICKER) {
            MZGramConfig.toggleHideGroupSticker();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideGroupSticker);
        } else if (item.id == BUTTON_DISABLE_AVATAR_BLUR) {
            MZGramConfig.toggleDisableAvatarBlur();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableAvatarBlur);
        } else if (item.id == BUTTON_SORT_BY_UNREAD) {
            MZGramConfig.toggleSortByUnread();
            ((TextCheckCell) view).setChecked(MZGramConfig.sortByUnread);
            getMessagesController().sortDialogs(null);
            getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        } else if (item.id == BUTTON_DISABLE_NOTIFICATION_BUBBLES) {
            MZGramConfig.toggleDisableNotificationBubbles();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableNotificationBubbles);
        } else if (item.id == BUTTON_DONT_AUTOPLAY_NEXT_VOICE) {
            MZGramConfig.toggleDontAutoPlayNextVoice();
            ((TextCheckCell) view).setChecked(MZGramConfig.dontAutoPlayNextVoice);
        } else if (item.id == BUTTON_DISABLE_PROXIMITY) {
            MZGramConfig.toggleDisableProximity();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableProximity);
        } else if (item.id == BUTTON_CONFIRM_REPEAT) {
            MZGramConfig.toggleConfirmRepeat();
            ((TextCheckCell) view).setChecked(MZGramConfig.confirmRepeat);
        } else if (item.id == BUTTON_CONFIRM_BOT_COMMANDS) {
            MZGramConfig.toggleConfirmBotCommands();
            ((TextCheckCell) view).setChecked(MZGramConfig.confirmBotCommands);
        } else if (item.id == BUTTON_CONTENT_CAPTURE) {
            MZGramConfig.toggleContentCapture();
            ((TextCheckCell) view).setChecked(MZGramConfig.contentCapture);
            org.telegram.messenger.mzgram.MZGramContentCapture.apply(getParentActivity());
        } else if (item.id == BUTTON_HIDE_ATTACH_MONEY) {
            MZGramConfig.toggleHideAttachMoney();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideAttachMoney);
        } else if (item.id == BUTTON_CHAT_MENU_TO_BEGINNING) {
            MZGramConfig.toggleChatMenuToBeginning();
            ((TextCheckCell) view).setChecked(MZGramConfig.chatMenuToBeginning);
        } else if (item.id == BUTTON_UNLIMITED_SAVED_GIFS) {
            MZGramConfig.toggleUnlimitedSavedGifs();
            ((TextCheckCell) view).setChecked(MZGramConfig.unlimitedSavedGifs);
        } else if (item.id == BUTTON_CHAT_MENU_BOOST) {
            MZGramConfig.toggleChatMenuBoost();
            ((TextCheckCell) view).setChecked(MZGramConfig.chatMenuBoost);
        } else if (item.id == BUTTON_CHAT_MENU_LINKED_CHAT) {
            MZGramConfig.toggleChatMenuLinkedChat();
            ((TextCheckCell) view).setChecked(MZGramConfig.chatMenuLinkedChat);
        } else if (item.id == BUTTON_CHAT_MENU_GO_TO_MESSAGE) {
            MZGramConfig.toggleChatMenuGoToMessage();
            ((TextCheckCell) view).setChecked(MZGramConfig.chatMenuGoToMessage);
        } else if (item.id == BUTTON_CHAT_MENU_HIDE_TITLE) {
            MZGramConfig.toggleChatMenuHideTitle();
            ((TextCheckCell) view).setChecked(MZGramConfig.chatMenuHideTitle);
        } else if (item.id == BUTTON_CHAT_MENU_CLEAR_DELETED) {
            MZGramConfig.toggleChatMenuClearDeleted();
            ((TextCheckCell) view).setChecked(MZGramConfig.chatMenuClearDeleted);
        } else if (item.id == BUTTON_CHAT_MENU_DELETE_OWN) {
            MZGramConfig.toggleChatMenuDeleteOwn();
            ((TextCheckCell) view).setChecked(MZGramConfig.chatMenuDeleteOwn);
        } else if (item.id == BUTTON_HIDE_SEND_AS_PEER) {
            MZGramConfig.toggleHideSendAsPeer();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideSendAsPeer);
            if (getParentLayout() != null) {
                rebuildBelow();
            }
        } else if (item.id == BUTTON_ADD_COMMA_AFTER_MENTION) {
            MZGramConfig.toggleAddCommaAfterMention();
            ((TextCheckCell) view).setChecked(MZGramConfig.addCommaAfterMention);
        } else if (item.id == BUTTON_HIDE_KEYBOARD_ON_SCROLL) {
            MZGramConfig.toggleHideKeyboardOnScroll();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideKeyboardOnScroll);
        } else if (item.id == BUTTON_REPLY_BACKGROUND) {
            MZGramConfig.toggleReplyBackground();
            ((TextCheckCell) view).setChecked(MZGramConfig.replyBackground);
        } else if (item.id == BUTTON_REPLY_EMOJI) {
            MZGramConfig.toggleReplyEmoji();
            ((TextCheckCell) view).setChecked(MZGramConfig.replyEmoji);
        } else if (item.id == BUTTON_REPLY_COLORS) {
            MZGramConfig.toggleReplyColors();
            ((TextCheckCell) view).setChecked(MZGramConfig.replyColors);
        } else if (item.id == BUTTON_UNLIMITED_RECENT_STICKERS) {
            MZGramConfig.toggleUnlimitedRecentStickers();
            ((TextCheckCell) view).setChecked(MZGramConfig.unlimitedRecentStickers);
        } else if (item.id == BUTTON_PAUSE_VOICE_ON_MINIMIZE) {
            MZGramConfig.togglePauseVoiceOnMinimize();
            ((TextCheckCell) view).setChecked(MZGramConfig.pauseVoiceOnMinimize);
        } else if (item.id == BUTTON_UNMUTE_WITH_VOLUME_BUTTONS) {
            MZGramConfig.toggleUnmuteWithVolumeButtons();
            ((TextCheckCell) view).setChecked(MZGramConfig.unmuteWithVolumeButtons);
        } else if (item.id == BUTTON_SWIPE_TO_PIP) {
            MZGramConfig.toggleSwipeToPip();
            ((TextCheckCell) view).setChecked(MZGramConfig.swipeToPip);
        } else if (item.id == BUTTON_SHOW_RESULTS_BEFORE_VOTING) {
            MZGramConfig.toggleShowResultsBeforeVoting();
            ((TextCheckCell) view).setChecked(MZGramConfig.showResultsBeforeVoting);
        } else if (item.id == BUTTON_HIDE_SHARE_BUTTON) {
            MZGramConfig.toggleHideShareButton();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideShareButton);
        } else if (item.id == BUTTON_REMOVE_MESSAGE_TAIL) {
            MZGramConfig.toggleRemoveMessageTail();
            ((TextCheckCell) view).setChecked(MZGramConfig.removeMessageTail);
        } else if (item.id == BUTTON_FORCE_BLUR) {
            MZGramConfig.toggleForceBlur();
            ((TextCheckCell) view).setChecked(MZGramConfig.forceBlur);
            rebuildBelow();
        } else if (item.id == BUTTON_HIDE_ALL_CHATS_TAB) {
            MZGramConfig.toggleHideAllChatsTab();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideAllChatsTab);
            if (foldersPreviewCell != null) {
                foldersPreviewCell.setShowAllTab(!MZGramConfig.hideAllChatsTab, true);
            }
            rebuildBelow();
        } else if (item.id == BUTTON_FOLDER_TAB_COUNTERS) {
            if (getParentActivity() == null) {
                return;
            }
            String[] options = new String[]{
                    getString(R.string.MZGramFolderTabCounterAll),
                    getString(R.string.MZGramFolderTabCounterUnmuted),
                    getString(R.string.MZGramFolderTabCounterOff)
            };
            showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramFolderTabCounters), MZGramConfig.folderTabCounterMode, (dialog, which) -> {
                MZGramConfig.setFolderTabCounterMode(which);
                MZGramConfig.folderTabCounters = (which != 2);
                if (foldersPreviewCell != null) {
                    foldersPreviewCell.updateTabCounter(true);
                }
                rebuildBelow();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }, getString(R.string.MZGramCancel)));
        } else if (item.id == BUTTON_SINGLE_CORNER_RADIUS) {
            MZGramConfig.toggleSingleCornerRadius();
            ((TextCheckCell) view).setChecked(MZGramConfig.singleCornerRadius);
            rebuildBelow();
        } else if (item.id == BUTTON_CUSTOM_CHAT_THEMES) {
            MZGramConfig.toggleCustomChatThemes();
            ((TextCheckCell) view).setChecked(MZGramConfig.customChatThemes);
        } else if (item.id == BUTTON_SENDER_MINI_AVATARS) {
            MZGramConfig.toggleSenderMiniAvatars();
            ((TextCheckCell) view).setChecked(MZGramConfig.senderMiniAvatars);
            rebuildBelow();
        } else if (item.id == BUTTON_FORCE_SNOW) {
            MZGramConfig.toggleForceSnow();
            ((TextCheckCell) view).setChecked(MZGramConfig.forceSnow);
            rebuildBelow();
        } else if (item.id == BUTTON_HIDE_DIALOGS_SEARCH_BAR) {
            MZGramConfig.toggleHideDialogsSearchBar();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideDialogsSearchBar);
            rebuildBelow();
        } else if (item.id == BUTTON_HIDE_FLOATING_BUTTON) {
            MZGramConfig.toggleHideFloatingButton();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideFloatingButton);
            rebuildBelow();
        } else if (item.id == BUTTON_HIDE_ACTION_BAR_STATUS) {
            MZGramConfig.toggleHideActionBarStatus();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideActionBarStatus);
            rebuildBelow();
        } else if (item.id == BUTTON_CENTER_TITLE) {
            MZGramConfig.toggleCenterTitle();
            ((TextCheckCell) view).setChecked(MZGramConfig.centerTitle);
            if (chatListPreviewCell != null) {
                chatListPreviewCell.updateCentered(true);
            }
            rebuildBelow();
        } else if (item.id == BUTTON_DISABLE_UNARCHIVE_SWIPE) {
            MZGramConfig.toggleDisableUnarchiveSwipe();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableUnarchiveSwipe);
        } else if (item.id == BUTTON_HIDE_ARCHIVE_FOLDER) {
            MZGramConfig.toggleHideArchiveFolder();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideArchiveFolder);
            org.telegram.messenger.mzgram.MZGramArchiveRow.apply();
        } else if (item.id == BUTTON_HIDE_PHONE_NUMBER) {
            MZGramConfig.toggleHidePhoneNumber();
            ((TextCheckCell) view).setChecked(MZGramConfig.hidePhoneNumber);
        } else if (item.id == BUTTON_RELATIVE_LAST_SEEN) {
            MZGramConfig.toggleRelativeLastSeen();
            if (view instanceof org.telegram.ui.Cells.NotificationsCheckCell) {
                ((org.telegram.ui.Cells.NotificationsCheckCell) view).setChecked(MZGramConfig.relativeLastSeen);
            } else if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.relativeLastSeen);
            }
        } else if (item.id == BUTTON_SAVE_FOLDER) {
            chooseSaveFolder();
        } else if (item.id == BUTTON_TITLE_TEXT) {
            chooseTitleText();
        } else if (item.id == BUTTON_DOUBLE_TAP_SEEK) {
            if (getParentActivity() == null) {
                return;
            }
            int[] seconds = MZGramConfig.DOUBLE_TAP_SEEK_SECONDS;
            String[] options = new String[seconds.length];
            int selected = 1;
            for (int i = 0; i < seconds.length; i++) {
                options[i] = seekTitle(seconds[i]);
                if (seconds[i] == MZGramConfig.doubleTapSeekSeconds) {
                    selected = i;
                }
            }
            showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramDoubleTapSeek), selected, (dialog, which) -> {
                MZGramConfig.setDoubleTapSeekSeconds(seconds[which]);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }, getString(R.string.MZGramCancel)));
        } else if (item.id == BUTTON_DIVIDER_STYLE) {
            if (getParentActivity() == null) {
                return;
            }
            String[] options = new String[DIVIDER_STYLE_TITLES.length];
            for (int i = 0; i < options.length; i++) {
                options[i] = getString(DIVIDER_STYLE_TITLES[i]);
            }
            showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramDividerStyle), MZGramConfig.dividerStyle, (dialog, which) -> {
                MZGramConfig.setDividerStyle(which);
                // The lines drawn with Telegram's shared paint take the colour at once
                org.telegram.ui.ActionBar.Theme.dividerPaint.setColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_divider));
                rebuildBelow();
                if (listView != null && listView.adapter != null) {
                    // Segments add a gap between the rows of a card
                    listView.invalidateItemDecorations();
                    listView.adapter.update(true);
                }
            }, getString(R.string.MZGramCancel)));
        } else if (item.id == BUTTON_UPLOAD_SPEED_BOOST) {
            MZGramConfig.toggleUploadSpeedBoost();
            ((TextCheckCell) view).setChecked(MZGramConfig.uploadSpeedBoost);
        } else if (item.id == BUTTON_DISABLE_NOTIFICATION_DELAY) {
            MZGramConfig.toggleDisableNotificationDelay();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableNotificationDelay);
        } else if (item.id == BUTTON_IN_APP_VIBRATION) {
            MZGramConfig.toggleInAppVibration();
            ((TextCheckCell) view).setChecked(MZGramConfig.inAppVibration);
            org.telegram.messenger.mzgram.MZGramHaptics.apply();
        } else if (item.id == BUTTON_CHARACTER_COUNTER) {
            MZGramConfig.toggleCharacterCounter();
            ((TextCheckCell) view).setChecked(MZGramConfig.characterCounter);
        } else if (item.id == BUTTON_OPEN_BY_ID) {
            org.telegram.ui.mzgram.MZGramOpenById.show(this);
        } else if (item.id == BUTTON_INLINE_MATH) {
            MZGramConfig.toggleInlineMath();
            ((TextCheckCell) view).setChecked(MZGramConfig.inlineMath);
        } else if (item.id == BUTTON_SILENT_DEFAULT) {
            MZGramConfig.toggleSendSilentByDefault();
            ((TextCheckCell) view).setChecked(MZGramConfig.sendSilentByDefault);
        } else if (item.id == BUTTON_TEXT_STYLE_MENU) {
            if (getParentActivity() != null) {
                showDialog(MZGramTextStyleMenu.createDialog(getParentActivity()));
            }
        } else if (item.id == BUTTON_PINNED_REACTIONS) {
            presentFragment(new MZGramPinnedReactionsActivity());
        } else if (item.id == BUTTON_HEX_COLOR) {
            MZGramConfig.toggleHexColorSwatch();
            ((TextCheckCell) view).setChecked(MZGramConfig.hexColorSwatch);
        } else if (item.id == BUTTON_LINK_TYPEFACE) {
            MZGramConfig.toggleLinkTypeface();
            ((TextCheckCell) view).setChecked(MZGramConfig.linkTypeface);
        } else if (item.id == BUTTON_LINKED_EMOJI) {
            MZGramConfig.toggleLinkedCustomEmoji();
            ((TextCheckCell) view).setChecked(MZGramConfig.linkedCustomEmoji);
        } else if (item.id == BUTTON_WIDE_CHANNEL_POSTS) {
            MZGramConfig.toggleWideChannelPosts();
            ((TextCheckCell) view).setChecked(MZGramConfig.wideChannelPosts);
        } else if (item.id == BUTTON_SPLIT_GIF_INLINE) {
            MZGramConfig.toggleSplitGifInlineRights();
            ((TextCheckCell) view).setChecked(MZGramConfig.splitGifInlineRights);
        } else if (item.id == BUTTON_GRANULAR_PERMISSIONS) {
            MZGramConfig.toggleGranularGroupPermissions();
            ((TextCheckCell) view).setChecked(MZGramConfig.granularGroupPermissions);
        } else if (item.id == BUTTON_STICKER_SHAPE) {
            if (getParentActivity() == null) {
                return;
            }
            String[] options = new String[3];
            for (int i = 0; i < options.length; i++) {
                options[i] = stickerShapeTitle(i);
            }
            showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramStickerShape), MZGramConfig.stickerShape, (dialog, which) -> {
                MZGramConfig.setStickerShape(which);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }, getString(R.string.MZGramCancel)));
        } else if (item.id == BUTTON_STICKER_TIME) {
            MZGramConfig.toggleHideStickerTime();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideStickerTime);
        } else if (item.id == BUTTON_PLAIN_CODE) {
            MZGramConfig.togglePlainCodeBlocks();
            ((TextCheckCell) view).setChecked(MZGramConfig.plainCodeBlocks);
        } else if (item.id == BUTTON_SCHEDULE_SLIDER) {
            MZGramConfig.toggleScheduleSlider();
            ((TextCheckCell) view).setChecked(MZGramConfig.scheduleSlider);
        } else if (item.id == BUTTON_PANGU) {
            MZGramConfig.togglePanguSpacing();
            ((TextCheckCell) view).setChecked(MZGramConfig.panguSpacing);
        } else if (item.id == BUTTON_ALWAYS_SEND_HD) {
            MZGramConfig.toggleAlwaysSendHd();
            ((TextCheckCell) view).setChecked(MZGramConfig.alwaysSendHd);
        } else if (item.id == BUTTON_PAUSE_ROUND) {
            MZGramConfig.togglePauseRoundOnLeave();
            ((TextCheckCell) view).setChecked(MZGramConfig.pauseRoundOnLeave);
        } else if (item.id == BUTTON_CAMERA_STABILIZATION) {
            MZGramConfig.toggleCameraStabilization();
            ((TextCheckCell) view).setChecked(MZGramConfig.cameraStabilization);
        } else if (item.id == BUTTON_CAMERA_X) {
            MZGramConfig.toggleCameraX();
            ((TextCheckCell) view).setChecked(MZGramConfig.cameraX);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_CAMERA_X_WIDE) {
            MZGramConfig.toggleCameraXWideLens();
            ((TextCheckCell) view).setChecked(MZGramConfig.cameraXWideLens);
        } else if (item.id == BUTTON_CAMERA_X_ZOOM) {
            MZGramConfig.toggleCameraXZoomSlider();
            ((TextCheckCell) view).setChecked(MZGramConfig.cameraXZoomSlider);
        } else if (item.id == BUTTON_ROUND_VIDEO_CAMERA2) {
            MZGramConfig.toggleRoundVideoCamera2();
            ((TextCheckCell) view).setChecked(MZGramConfig.roundVideoCamera2);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_ROUND_VIDEO_HIGH_FPS) {
            MZGramConfig.toggleRoundVideoHighFps();
            ((TextCheckCell) view).setChecked(MZGramConfig.roundVideoHighFps);
        } else if (item.id == BUTTON_ROUND_VIDEO_BITRATE) {
            int[] bitrates = new int[]{0, 750_000, 1_000_000, 1_200_000, 2_000_000};
            CharSequence[] labels = new CharSequence[bitrates.length];
            for (int i = 0; i < bitrates.length; i++) {
                labels[i] = formatRoundBitrate(bitrates[i]);
            }
            org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString(R.string.MZGramRoundVideoBitrate));
            builder.setItems(labels, (dialog, index) -> {
                MZGramConfig.setRoundVideoBitrate(bitrates[index]);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
            showDialog(builder.create());
        } else if (item.id == BUTTON_ROUND_VIDEO_ADVANCED) {
            presentFragment(new org.telegram.ui.RoundVideoSettingsActivity());
        } else if (item.id == BUTTON_MUSIC_PLAYER) {
            MZGramConfig.toggleMusicPlayer();
            ((TextCheckCell) view).setChecked(MZGramConfig.musicPlayer);
            org.telegram.ui.mzgram.player.MZGramPlayerMiniView.switchChanged();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_MUSIC_PLAYER_STYLE) {
            org.telegram.ui.mzgram.player.MZGramPlayerSheet.openStyleEditor(this);
        } else if (item.id == BUTTON_MUSIC_PLAYER_BAR) {
            MZGramConfig.toggleMusicPlayerBar();
            ((TextCheckCell) view).setChecked(MZGramConfig.musicPlayerBar);
        } else if (item.id == BUTTON_MUSIC_PLAYER_MINI) {
            MZGramConfig.toggleMusicPlayerMini();
            ((TextCheckCell) view).setChecked(MZGramConfig.musicPlayerMini);
            org.telegram.ui.mzgram.player.MZGramPlayerMiniView.switchChanged();
        } else if (item.id == BUTTON_AUDIO_ENHANCE) {
            MZGramConfig.toggleAudioEnhance();
            ((TextCheckCell) view).setChecked(MZGramConfig.audioEnhance);
        } else if (item.id == BUTTON_COPY_PLAIN) {
            MZGramConfig.toggleCopyPlainText();
            ((TextCheckCell) view).setChecked(MZGramConfig.copyPlainText);
        } else if (item.id == BUTTON_DOUBLE_TAP_ACTIONS) {
            MZGramConfig.toggleDoubleTapActions();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_DOUBLE_TAP_OWN) {
            chooseDoubleTap(R.string.MZGramDoubleTapOwn, MZGramConfig.doubleTapOwn, MZGramConfig::setDoubleTapOwn);
        } else if (item.id == BUTTON_DOUBLE_TAP_OTHER) {
            chooseDoubleTap(R.string.MZGramDoubleTapOther, MZGramConfig.doubleTapOther, MZGramConfig::setDoubleTapOther);
        } else if (item.id == BUTTON_CLEAN_LINKS) {
            MZGramConfig.toggleCleanLinks();
            ((TextCheckCell) view).setChecked(MZGramConfig.cleanLinks);
        } else if (item.id == BUTTON_SEARCH_OPERATORS) {
            MZGramConfig.toggleSearchOperators();
            ((TextCheckCell) view).setChecked(MZGramConfig.searchOperators);
        } else if (item.id == BUTTON_FOLD_FANCY_FONTS) {
            MZGramConfig.toggleFoldFancyFontsInSearch();
            ((TextCheckCell) view).setChecked(MZGramConfig.foldFancyFontsInSearch);
        } else if (item.id == BUTTON_CONFIRM_INTERNAL_LINKS) {
            MZGramConfig.toggleConfirmInternalLinks();
            ((TextCheckCell) view).setChecked(MZGramConfig.confirmInternalLinks);
        } else if (item.id == BUTTON_DISABLE_READ_STICKERS) {
            MZGramConfig.toggleDisableReadStickers();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableReadStickers);
        } else if (item.id == BUTTON_DISABLE_LINK_PREVIEWS) {
            MZGramConfig.toggleDisableLinkPreviews();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableLinkPreviews);
        } else if (item.id == BUTTON_DISABLE_CLOUD_DRAFTS) {
            MZGramConfig.toggleDisableCloudDrafts();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableCloudDrafts);
        } else if (item.id == BUTTON_LOCK_SCREEN_VOICE_PROTECTION) {
            MZGramConfig.toggleLockScreenVoiceProtection();
            ((TextCheckCell) view).setChecked(MZGramConfig.lockScreenVoiceProtection);
        } else if (item.id == BUTTON_GHOST_MODE_EXCLUSIONS) {
            MZGramConfig.toggleGhostModeExclusions();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostModeExclusions);
        } else if (item.id == BUTTON_OWN_LAST_SEEN) {
            MZGramConfig.toggleOwnLastSeen();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_KEEP_GROUP_READS) {
            MZGramConfig.toggleKeepGroupReads();
            ((TextCheckCell) view).setChecked(MZGramConfig.keepGroupReads);
        } else if (item.id == BUTTON_BOT_START_CONFIRM) {
            MZGramConfig.toggleBotStartConfirm();
            ((TextCheckCell) view).setChecked(MZGramConfig.botStartConfirm);
        } else if (item.id == BUTTON_PROXY_OFF_VPN || item.id == BUTTON_PROXY_OFF_MOBILE || item.id == BUTTON_PROXY_OFF_WIFI) {
            final int condition = item.id == BUTTON_PROXY_OFF_VPN ? org.telegram.messenger.mzgram.MZGramProxyAutoDisable.VPN
                    : item.id == BUTTON_PROXY_OFF_MOBILE ? org.telegram.messenger.mzgram.MZGramProxyAutoDisable.MOBILE_DATA
                    : org.telegram.messenger.mzgram.MZGramProxyAutoDisable.WIFI;
            final boolean on = !org.telegram.messenger.mzgram.MZGramProxyAutoDisable.isOn(condition);
            org.telegram.messenger.mzgram.MZGramProxyAutoDisable.set(condition, on);
            ((TextCheckCell) view).setChecked(on);
        } else if (item.id == BUTTON_REDUCE_NETWORK_TRACKING) {
            MZGramConfig.toggleReduceNetworkTracking();
            ((TextCheckCell) view).setChecked(MZGramConfig.reduceNetworkTracking);
        } else if (item.id == BUTTON_PASSCODE_ACCOUNTS) {
            MZGramConfig.togglePasscodeAccounts();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_PASSCODE_ACCOUNTS_SETUP) {
            presentFragment(new MZGramPasscodeAccountsActivity());
        } else if (item.id == BUTTON_DISABLE_NUMBER_ROUNDING) {
            MZGramConfig.toggleDisableNumberRounding();
            if (view instanceof org.telegram.ui.Cells.NotificationsCheckCell) {
                ((org.telegram.ui.Cells.NotificationsCheckCell) view).setChecked(MZGramConfig.disableNumberRounding);
            } else if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.disableNumberRounding);
            }
        } else if (item.id == BUTTON_FORMAT_TIME_WITH_SECONDS) {
            MZGramConfig.toggleFormatTimeWithSeconds();
            if (view instanceof org.telegram.ui.Cells.NotificationsCheckCell) {
                ((org.telegram.ui.Cells.NotificationsCheckCell) view).setChecked(MZGramConfig.formatTimeWithSeconds);
            } else if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.formatTimeWithSeconds);
            }
        } else if (item.id == BUTTON_ASK_BEFORE_CALL) {
            MZGramConfig.toggleAskBeforeCall();
            ((TextCheckCell) view).setChecked(MZGramConfig.askBeforeCall);
        } else if (item.id == BUTTON_STORIES_NO_BACKGROUND_LOAD) {
            MZGramConfig.toggleStoriesNoBackgroundLoad();
            ((TextCheckCell) view).setChecked(MZGramConfig.storiesNoBackgroundLoad);
            if (!MZGramConfig.storiesNoBackgroundLoad) {
                // Loading is allowed again: fetch what was skipped
                for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) {
                        org.telegram.messenger.MessagesController.getInstance(a).getStoriesController().loadStories();
                    }
                }
            }
        } else if (item.id == BUTTON_HIDE_STORIES) {
            MZGramConfig.toggleHideStories();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideStories);
        } else if (item.id == BUTTON_DISABLE_GREETING_STICKER) {
            MZGramConfig.toggleDisableGreetingSticker();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableGreetingSticker);
        } else if (item.id == BUTTON_CHANNEL_BOTTOM_BUTTON) {
            chooseFrom(R.string.MZGramChannelBottomButton, CHANNEL_BOTTOM_TITLES, MZGramConfig.channelBottomButton, MZGramConfig::setChannelBottomButton);
        } else if (item.id == BUTTON_ROUND_VIDEO_CAMERA) {
            chooseFrom(R.string.MZGramRoundVideoCamera, new int[]{R.string.MZGramCameraFront, R.string.MZGramCameraBack},
                    MZGramConfig.roundVideoFrontCamera ? 0 : 1, which -> MZGramConfig.setRoundVideoFrontCamera(which == 0));
        } else if (item.id == BUTTON_DELETED_ICON_COLOR) {
            chooseFrom(R.string.MZGramDeletedIconColor, new int[]{R.string.MZGramDeletedIconRed, R.string.MZGramDeletedIconTime},
                    MZGramConfig.deletedMarkIconColor, MZGramConfig::setDeletedMarkIconColor);
        } else if (item.id == BUTTON_RESET_SETTINGS) {
            confirmResetSettings();
        } else if (item.id == BUTTON_DELETE_ACCOUNT) {
            confirmDeleteAccount();
        } else if (item.id == BUTTON_FEED_PAGE) {
            presentFragment(new MZGramSettingsActivity(SECTION_FEED));
        } else if (item.id == BUTTON_FEED_OPEN) {
            presentFragment(new MZGramFeedActivity());
        } else if (item.id == BUTTON_FEED_CHANNELS) {
            presentFragment(new MZGramFeedChannelsActivity());
        } else if (item.id == BUTTON_FEED_POSTS) {
            int[] counts = org.telegram.messenger.mzgram.MZGramFeed.POSTS_PER_CHANNEL;
            String[] options = new String[counts.length];
            int selected = 1;
            for (int i = 0; i < counts.length; i++) {
                options[i] = Integer.toString(counts[i]);
                if (counts[i] == org.telegram.messenger.mzgram.MZGramFeed.postsPerChannel()) {
                    selected = i;
                }
            }
            showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, getString(R.string.MZGramFeedPostsPerChannel), selected, (dialog, which) -> {
                MZGramConfig.setFeedPostsPerChannel(counts[which]);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }, getString(R.string.MZGramCancel)));
        } else if (item.id == BUTTON_SWIPE_ACTION) {
            chooseFrom(R.string.MZGramSwipeAction, org.telegram.ui.mzgram.MZGramSwipeAction.TITLES, MZGramConfig.swipeAction, MZGramConfig::setSwipeAction);
        } else if (item.id == BUTTON_OPEN_ARCHIVE_ON_PULL) {
            MZGramConfig.toggleOpenArchiveOnPull();
            ((TextCheckCell) view).setChecked(MZGramConfig.openArchiveOnPull);
        } else if (item.id == BUTTON_DISABLE_INSTANT_CAMERA) {
            MZGramConfig.toggleDisableInstantCamera();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableInstantCamera);
        } else if (item.id == BUTTON_CHECK_UPDATES) {
            MZGramConfig.toggleCheckUpdates();
            ((TextCheckCell) view).setChecked(MZGramConfig.checkUpdates);
            MZGramUpdateBanner banner = MZGramUpdateBanner.current();
            if (!MZGramConfig.checkUpdates && banner != null) {
                banner.hide();
            }
        } else if (item.id == BUTTON_LANGUAGE) {
            chooseLanguage();
        } else if (item.id == BUTTON_LAUNCH_FOLDER) {
            MZGramLaunchFolder.choose(this, currentAccount, () -> {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        } else if (item.id == BUTTON_FOLDER_TAB_ICONS) {
            MZGramFolderIcons.choose(this, () -> {
                if (foldersPreviewCell != null) {
                    foldersPreviewCell.updateTabTitle(true);
                    foldersPreviewCell.updateTabIcons(true);
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        } else if (item.id == BUTTON_LOSSLESS_PHOTO_CROP) {
            MZGramConfig.toggleLosslessPhotoCrop();
            ((TextCheckCell) view).setChecked(MZGramConfig.losslessPhotoCrop);
        } else if (item.id == BUTTON_VOICE_QUALITY) {
            if (getParentActivity() == null) {
                return;
            }
            final int[] titles = org.telegram.messenger.mzgram.MZGramVoiceQuality.TITLES;
            final CharSequence[] names = new CharSequence[titles.length];
            for (int i = 0; i < names.length; i++) {
                names[i] = getString(titles[i]);
            }
            org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString(R.string.MZGramVoiceQuality));
            builder.setItems(names, (dialog, which) -> {
                MZGramConfig.setVoiceQuality(which);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
            builder.setNegativeButton(getString(R.string.MZGramCancel), null);
            showDialog(builder.create());
        } else if (item.id == BUTTON_APP_FONT) {
            MZGramAppFont.choose(this, () -> {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }, () -> {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, REQUEST_CODE_APP_FONT);
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.pickAppFont", e);
                }
            });
        } else if (item.id == BUTTON_GLASS_OUTLINE) {
            MZGramGlassOutline.choose(this, () -> {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        } else if (item.id == BUTTON_FAB_SHAPE) {
            MZGramFabShape.choose(this, () -> {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        } else if (item.id == BUTTON_PEER_ID) {
            choosePeerId();
        } else if (item.id >= ARCHIVE_LIMIT_FIRST && item.id < ARCHIVE_LIMIT_FIRST + ARCHIVE_LIMIT_TITLES.length) {
            chooseLimit(item.id - ARCHIVE_LIMIT_FIRST);
        } else if (item.id == BUTTON_ARCHIVE_COUNTERS_RESET) {
            org.telegram.messenger.mzgram.MZGramArchiveLimits.resetCounters();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_CRASH_REPORTS) {
            MZGramConfig.toggleCrashReports();
            ((TextCheckCell) view).setChecked(MZGramConfig.crashReports);
        } else if (item.id == BUTTON_JANK_PROFILER) {
            if (org.telegram.messenger.mzgram.MZGramJankProfiler.isRunning()) {
                org.telegram.messenger.mzgram.MZGramJankProfiler.stop(file -> onProfilerStopped(file));
            } else if (getParentActivity() != null) {
                org.telegram.messenger.mzgram.MZGramJankProfiler.start(getParentActivity());
                onProfilerStarted(org.telegram.messenger.mzgram.MZGramJankProfiler.isRunning(), R.string.MZGramJankProfilerStarted);
            }
        } else if (item.id == BUTTON_JANK_REPORT) {
            shareProfilerReport(org.telegram.messenger.mzgram.MZGramJankProfiler.getLastReport());
        } else if (item.id == BUTTON_ENERGY_PROFILER) {
            if (org.telegram.messenger.mzgram.MZGramEnergyProfiler.isRunning()) {
                org.telegram.messenger.mzgram.MZGramEnergyProfiler.stop(file -> onProfilerStopped(file));
            } else if (getParentActivity() != null) {
                org.telegram.messenger.mzgram.MZGramEnergyProfiler.start(getParentActivity());
                onProfilerStarted(org.telegram.messenger.mzgram.MZGramEnergyProfiler.isRunning(), R.string.MZGramEnergyProfilerStarted);
            }
        } else if (item.id == BUTTON_ENERGY_REPORT) {
            if (org.telegram.messenger.mzgram.MZGramEnergyProfiler.isRunning()) {
                org.telegram.messenger.mzgram.MZGramEnergyProfiler.shareCurrent(file -> shareProfilerReport(file));
            } else {
                shareProfilerReport(org.telegram.messenger.mzgram.MZGramEnergyProfiler.getLastReport());
            }
        } else if (item.id == BUTTON_PYTHON_RUNTIME) {
            if (org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.isStarted()) {
                showPythonRuntime(true);
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MZGramPythonRuntimeStarting)).show();
                org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.start(this::showPythonRuntime);
            }
        } else if (item.id == ROW_PLUGINS) {
            presentFragment(new MZGramPluginsActivity());
        } else if (item.id == BUTTON_CHECK_UPDATES_NOW) {
            MZGramUpdateBanner.checkByHand(this, null);
        } else if (item.id == BUTTON_HIDE_CAMERA_TILE) {
            MZGramConfig.toggleHideCameraTile();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideCameraTile);
        } else if (item.id == BUTTON_PREFER_ORIGINAL_QUALITY) {
            MZGramConfig.togglePreferOriginalQuality();
            ((TextCheckCell) view).setChecked(MZGramConfig.preferOriginalQuality);
        } else if (item.id == BUTTON_AUTO_PAUSE_VIDEO) {
            MZGramConfig.toggleAutoPauseVideo();
            ((TextCheckCell) view).setChecked(MZGramConfig.autoPauseVideo);
        } else if (item.id == BUTTON_DELETE_FOR_ALL_BY_DEFAULT) {
            MZGramConfig.toggleDeleteForAllByDefault();
            ((TextCheckCell) view).setChecked(MZGramConfig.deleteForAllByDefault);
        } else if (item.id == BUTTON_CONFIRM_AV_MESSAGE) {
            MZGramConfig.toggleConfirmAVMessage();
            ((TextCheckCell) view).setChecked(MZGramConfig.confirmAVMessage);
        } else if (item.id == BUTTON_MEDIA_PREVIEW_ON_LONG_PRESS) {
            MZGramConfig.toggleMediaPreviewOnLongPress();
            ((TextCheckCell) view).setChecked(MZGramConfig.mediaPreviewOnLongPress);
        } else if (item.id == BUTTON_PROFILE_MUSIC_CARD) {
            MZGramConfig.toggleProfileMusicCard();
            ((TextCheckCell) view).setChecked(MZGramConfig.profileMusicCard);
        } else if (item.id == BUTTON_OPENFREEMAP_PREVIEW) {
            MZGramConfig.toggleOpenFreeMapPreview();
            ((TextCheckCell) view).setChecked(MZGramConfig.openFreeMapPreview);
        } else if (item.id == BUTTON_SHOW_COPY_PHOTO) {
            MZGramConfig.toggleShowCopyPhoto();
            ((TextCheckCell) view).setChecked(MZGramConfig.showCopyPhoto);
        } else if (item.id == BUTTON_SHOW_DELETE_DOWNLOADED_FILE) {
            MZGramConfig.toggleShowDeleteDownloadedFile();
            ((TextCheckCell) view).setChecked(MZGramConfig.showDeleteDownloadedFile);
        } else if (item.id == BUTTON_SHOW_ADD_TO_SAVED_MESSAGES) {
            MZGramConfig.toggleShowAddToSavedMessages();
            ((TextCheckCell) view).setChecked(MZGramConfig.showAddToSavedMessages);
        } else if (item.id == BUTTON_SHOW_SET_REMINDER) {
            MZGramConfig.toggleShowSetReminder();
            ((TextCheckCell) view).setChecked(MZGramConfig.showSetReminder);
        } else if (item.id == BUTTON_SHOW_REPEAT) {
            MZGramConfig.toggleShowRepeat();
            ((TextCheckCell) view).setChecked(MZGramConfig.showRepeat);
        } else if (item.id == BUTTON_SHOW_OPEN_IN) {
            MZGramConfig.toggleShowOpenIn();
            ((TextCheckCell) view).setChecked(MZGramConfig.showOpenIn);
        } else if (item.id == BUTTON_SHOW_MESSAGE_DETAILS) {
            MZGramConfig.toggleShowMessageDetails();
            ((TextCheckCell) view).setChecked(MZGramConfig.showMessageDetails);
        } else if (item.id == BUTTON_SHOW_QR_CODE) {
            MZGramConfig.toggleShowQrCode();
            ((TextCheckCell) view).setChecked(MZGramConfig.showQrCode);
        } else if (item.id == BUTTON_SHOW_NO_QUOTE_FORWARD) {
            MZGramConfig.toggleShowNoQuoteForward();
            ((TextCheckCell) view).setChecked(MZGramConfig.showNoQuoteForward);
        } else if (item.id == BUTTON_PREDICTIVE_BACK_ANIMATION) {
            MZGramConfig.togglePredictiveBackAnimation();
            ((TextCheckCell) view).setChecked(MZGramConfig.predictiveBackAnimation);
        } else if (item.id == BUTTON_GOOEY_AVATAR_ANIMATION) {
            MZGramConfig.toggleGooeyAvatarAnimation();
            ((TextCheckCell) view).setChecked(MZGramConfig.gooeyAvatarAnimation);
        } else if (item.id == BUTTON_TABLET_MODE) {
            String[] itemsArray = new String[] {
                    getString(R.string.MZGramTabletModeAuto),
                    getString(R.string.MZGramTabletModeOn),
                    getString(R.string.MZGramTabletModeOff),
            };
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramTabletMode))
                    .setItems(itemsArray, (dialog, which) -> {
                        MZGramConfig.setTabletMode(which);
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (item.id == BUTTON_ALWAYS_SHOW_DOWNLOADS) {
            MZGramConfig.toggleAlwaysShowDownloadIcon();
            ((TextCheckCell) view).setChecked(MZGramConfig.alwaysShowDownloadIcon);
        } else if (item.id == BUTTON_BACK_ANIMATION_STYLE) {
            ArrayList<String> options = new ArrayList<>();
            options.add(getString(R.string.MZGramBackAnimationClassic));
            options.add(getString(R.string.MZGramBackAnimationSpring));
            options.add(getString(R.string.MZGramBackAnimationPredictive));
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                options.add(getString(R.string.MZGramBackAnimationSlide));
            }
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramBackAnimation))
                    .setItems(options.toArray(new String[0]), (dialog, which) -> {
                        MZGramConfig.setBackAnimationStyle(which);
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (item.id == BUTTON_BOTTOM_NAV_MODE) {
            String[] itemsArray = new String[] {
                    getString(R.string.MZGramBottomNavigationModeShow),
                    getString(R.string.MZGramBottomNavigationModeHide),
                    getString(R.string.MZGramBottomNavigationModeFloating),
            };
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramBottomNavigationBarMode))
                    .setItems(itemsArray, (dialog, which) -> {
                        MZGramConfig.setBottomNavigationMode(which);
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                        if (getParentLayout() != null) {
                            getParentLayout().rebuildAllFragmentViews(false, false);
                        }
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (item.id == BUTTON_MAIN_TABS_HIDE_TITLES) {
            MZGramConfig.toggleMainTabsHideTitles();
            ((TextCheckCell) view).setChecked(MZGramConfig.mainTabsHideTitles);
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
        } else if (item.id == BUTTON_MAIN_TABS_HIDE_CONTACTS) {
            MZGramConfig.toggleMainTabsHideContacts();
            ((TextCheckCell) view).setChecked(MZGramConfig.mainTabsHideContacts);
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
        } else if (item.id == BUTTON_MAIN_TABS_HIDE_CALLS) {
            MZGramConfig.toggleMainTabsHideCallsSettings();
            ((TextCheckCell) view).setChecked(MZGramConfig.mainTabsHideCallsSettings);
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
        } else if (item.id == BUTTON_MAIN_TABS_HIDE_PROFILE) {
            MZGramConfig.toggleMainTabsHideProfile();
            ((TextCheckCell) view).setChecked(MZGramConfig.mainTabsHideProfile);
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
        } else if (item.id == BUTTON_RECENT_CHATS_ON_BACK) {
            MZGramConfig.toggleRecentChatsOnBack();
            if (!MZGramConfig.recentChatsOnBack) {
                MZGramRecentChats.clearAll();
            }
            ((TextCheckCell) view).setChecked(MZGramConfig.recentChatsOnBack);
        } else if (item.id == BUTTON_PILL_STACK) {
            MZGramConfig.togglePillStack();
            ((TextCheckCell) view).setChecked(MZGramConfig.pillStack);
            org.telegram.messenger.mzgram.MZGramPills.changed();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_PILLS) {
            presentFragment(new MZGramPillsActivity());
        } else if (item.id == BUTTON_ICON_PACKS_ON) {
            MZGramConfig.toggleIconPacks();
            ((TextCheckCell) view).setChecked(MZGramConfig.iconPacks);
            org.telegram.messenger.mzgram.MZGramIconPacks.changed();
            MZGramIconPacksActivity.rebuildScreens(this);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_ICON_PACKS) {
            presentFragment(new MZGramIconPacksActivity());
        } else if (item.id == BUTTON_MONET_THEMES) {
            MZGramConfig.toggleMonetThemes();
            ((TextCheckCell) view).setChecked(MZGramConfig.monetThemes);
            if (MZGramConfig.monetThemes) {
                org.telegram.messenger.mzgram.MZGramMonet.addThemes();
            } else {
                org.telegram.messenger.mzgram.MZGramMonet.removeThemes();
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_MONET_STYLE) {
            chooseMonetStyle();
        } else if (item.id == BUTTON_MONET_PICK) {
            presentFragment(new org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC));
        } else if (item.id == BUTTON_EXTENDED_PINS) {
            MZGramConfig.toggleExtendedPins();
            ((TextCheckCell) view).setChecked(MZGramConfig.extendedPins);
        } else if (item.id == BUTTON_LOCAL_FOLDERS) {
            MZGramConfig.toggleLocalFolders();
            ((TextCheckCell) view).setChecked(MZGramConfig.localFolders);
            if (MZGramConfig.localFolders) {
                org.telegram.messenger.mzgram.MZGramFolderSync.startAll();
            }
        } else if (item.id == BUTTON_LAST_SEEN_EXACT) {
            MZGramConfig.toggleLastSeenExact();
            ((TextCheckCell) view).setChecked(MZGramConfig.lastSeenExact);
        } else if (item.id == BUTTON_CUSTOM_DRAWER) {
            MZGramConfig.toggleCustomDrawer();
            ((TextCheckCell) view).setChecked(MZGramConfig.customDrawer);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_DRAWER_IMMERSIVE) {
            MZGramConfig.toggleDrawerImmersiveAnimation();
            ((TextCheckCell) view).setChecked(MZGramConfig.drawerImmersiveAnimation);
        } else if (item.id == BUTTON_DRAWER_ITEMS) {
            presentFragment(new MZGramDrawerItemsActivity());
        } else if (item.id == BUTTON_DRAWER_SHOW_QR) {
            MZGramConfig.toggleDrawerShowQr();
            ((TextCheckCell) view).setChecked(MZGramConfig.drawerShowQr);
        } else if (item.id == BUTTON_DRAWER_HEADER) {
            MZGramDrawer.chooseHeaderStyle(this, () -> {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        } else if (item.id == BUTTON_DRAWER_GLASS) {
            MZGramConfig.toggleDrawerGlass();
            ((TextCheckCell) view).setChecked(MZGramConfig.drawerGlass);
        } else if (item.id == BUTTON_DRAWER_LARGE_AVATAR) {
            MZGramConfig.toggleDrawerLargeAvatar();
            ((TextCheckCell) view).setChecked(MZGramConfig.drawerLargeAvatar);
        } else if (item.id == BUTTON_ACCOUNT_ORDER) {
            presentFragment(new MZGramAccountOrderActivity());
        } else if (item.id == BUTTON_ACCOUNTS_IN_MENU) {
            MZGramConfig.toggleAccountsInMenu();
            ((TextCheckCell) view).setChecked(MZGramConfig.accountsInMenu);
        } else if (item.id == BUTTON_SHOW_ASK_AI) {
            MZGramConfig.toggleShowAskAi();
            ((TextCheckCell) view).setChecked(MZGramConfig.showAskAi);
        } else if (item.id == BUTTON_AI_PROVIDER) {
            chooseAiProvider();
        } else if (indexOf(HIDE_ROW_BUTTONS, item.id) >= 0) {
            String key = org.telegram.messenger.mzgram.MZGramSettingsRows.rows().get(indexOf(HIDE_ROW_BUTTONS, item.id)).key;
            boolean hidden = !org.telegram.messenger.mzgram.MZGramSettingsRows.hidden(key);
            org.telegram.messenger.mzgram.MZGramSettingsRows.setHidden(key, hidden);
            ((TextCheckCell) view).setChecked(hidden);
            // Telegram's Settings under this page show it at once
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
        } else if (indexOf(AI_MENU_BUTTONS, item.id) >= 0) {
            org.telegram.messenger.mzgram.MZGramAiRules.Provider provider = org.telegram.messenger.mzgram.MZGramAiRules.providers().get(indexOf(AI_MENU_BUTTONS, item.id));
            boolean shown = !org.telegram.messenger.mzgram.MZGramAiRules.menuButton(provider);
            org.telegram.messenger.mzgram.MZGramAiRules.setMenuButton(provider, shown);
            ((TextCheckCell) view).setChecked(shown);
        } else if (item.id == BUTTON_AI_SIGN_OUT) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramAiSignOut))
                    .setMessage(getString(R.string.MZGramAiSignOutConfirm))
                    .setPositiveButton(getString(R.string.MZGramAiSignOut), (dialog, which) -> {
                        MZGramAiActivity.signOut();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramAiSignedOut)).show();
                    })
                    .setNegativeButton(getString(R.string.MZGramCancel), null)
                    .makeRed(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE)
                    .show();
        } else if (item.id == BUTTON_TRANSLATION_AI) {
            presentFragment(new MZGramTranslationAiActivity());
        } else if (item.id == BUTTON_EXTENDED_PERIPHERALS) {
            presentFragment(new MZGramPeripheralsActivity());
        } else if (item.id == BUTTON_SAVE_PROTECTED_CONTENT) {
            MZGramConfig.toggleSaveProtectedContent();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveProtectedContent);
        } else if (item.id == BUTTON_STATIC_INPUT_ICONS) {
            MZGramConfig.toggleStaticInputIcons();
            ((TextCheckCell) view).setChecked(MZGramConfig.staticInputIcons);
        } else if (item.id == BUTTON_HIDE_CHAT_STATUS) {
            MZGramConfig.toggleHideChatStatus();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideChatStatus);
        } else if (item.id == BUTTON_COMPACT_CHAT_HEADER) {
            MZGramConfig.toggleCompactChatHeader();
            ((TextCheckCell) view).setChecked(MZGramConfig.compactChatHeader);
        } else if (item.id == BUTTON_SHOW_REGISTRATION_DATE) {
            MZGramConfig.toggleShowRegistrationDate();
            ((TextCheckCell) view).setChecked(MZGramConfig.showRegistrationDate);
        } else if (item.id == BUTTON_FOLDER_TABS_AT_BOTTOM) {
            MZGramConfig.toggleFolderTabsAtBottom();
            ((TextCheckCell) view).setChecked(MZGramConfig.folderTabsAtBottom);
        } else if (item.id == BUTTON_GHOST_AUTO_DELAY_SEND) {
            MZGramConfig.toggleGhostAutoDelaySend();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostAutoDelaySend);
        } else if (item.id == BUTTON_GHOST_SILENT_SEND) {
            chooseFrom(R.string.MZGramGhostSilentSend, GHOST_SILENT_TITLES, org.telegram.messenger.mzgram.MZGramGhostMode.silentSend(),
                    org.telegram.messenger.mzgram.MZGramGhostMode::setSilentSend);
        } else if (item.id == BUTTON_OFFER_GHOST_MODE_BEFORE_STORIES) {
            MZGramConfig.toggleOfferGhostModeBeforeStories();
            ((TextCheckCell) view).setChecked(MZGramConfig.offerGhostModeBeforeStories);
        } else if (item.id == BUTTON_GHOST_MODE) {
            MZGramConfig.toggleGhostMode();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostMode);
            rebuildBelow();
        } else if (item.id == BUTTON_SAVE_DELETED_MESSAGES) {
            MZGramConfig.toggleSaveDeletedMessages();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveDeletedMessages);
        } else if (item.id == BUTTON_SAVE_EDIT_HISTORY) {
            MZGramConfig.toggleSaveEditHistory();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveEditHistory);
        } else if (item.id == BUTTON_SAVE_ARCHIVE_MEDIA) {
            MZGramConfig.toggleSaveArchiveMedia();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveArchiveMedia);
        } else if (item.id == BUTTON_SAVE_FORMATTING) {
            MZGramConfig.toggleSaveFormatting();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveFormatting);
        } else if (item.id == BUTTON_SAVE_REACTIONS) {
            MZGramConfig.toggleSaveReactions();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveReactions);
        } else if (item.id == BUTTON_SAVE_FOR_BOTS) {
            MZGramConfig.toggleSaveForBots();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveForBots);
        } else if (item.id == BUTTON_SAVE_FROM_USERS) {
            MZGramConfig.toggleSaveFromUsers();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveFromUsers);
        } else if (item.id == BUTTON_SAVE_IN_GROUPS) {
            MZGramConfig.toggleSaveInGroups();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveInGroups);
        } else if (item.id == BUTTON_SAVE_IN_CHANNELS) {
            MZGramConfig.toggleSaveInChannels();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveInChannels);
        } else if (item.id == BUTTON_SAVE_EXCLUSIONS) {
            MZGramConfig.toggleSaveExclusions();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveExclusions);
        } else if (item.id == BUTTON_KEPT_REPLY_PREVIEWS) {
            MZGramConfig.toggleKeptReplyPreviews();
            ((TextCheckCell) view).setChecked(MZGramConfig.keptReplyPreviews);
        } else if (item.id == BUTTON_REPLY_TO_DELETED_AS_QUOTE) {
            MZGramConfig.toggleReplyToDeletedAsQuote();
            ((TextCheckCell) view).setChecked(MZGramConfig.replyToDeletedAsQuote);
        } else if (item.id == BUTTON_FORWARD_DELETED_AS_COPY) {
            MZGramConfig.toggleForwardDeletedAsCopy();
            ((TextCheckCell) view).setChecked(MZGramConfig.forwardDeletedAsCopy);
        } else if (item.id == BUTTON_DELETED_MESSAGES_LIST) {
            MZGramConfig.toggleDeletedMessagesList();
            ((TextCheckCell) view).setChecked(MZGramConfig.deletedMessagesList);
        } else if (item.id == BUTTON_DELETED_DIALOGS_LIST) {
            MZGramConfig.toggleDeletedDialogsList();
            ((TextCheckCell) view).setChecked(MZGramConfig.deletedDialogsList);
            if (!MZGramConfig.deletedDialogsList) {
                org.telegram.messenger.mzgram.MZGramDeletedDialogs.removeRestored(currentAccount);
            } else if (MZGramConfig.deletedDialogsInMainList) {
                org.telegram.messenger.mzgram.MZGramDeletedDialogs.restore(currentAccount);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_DELETED_DIALOGS_IN_MAIN_LIST) {
            MZGramConfig.toggleDeletedDialogsInMainList();
            ((TextCheckCell) view).setChecked(MZGramConfig.deletedDialogsInMainList);
            if (MZGramConfig.deletedDialogsInMainList) {
                org.telegram.messenger.mzgram.MZGramDeletedDialogs.restore(currentAccount);
            } else {
                org.telegram.messenger.mzgram.MZGramDeletedDialogs.removeRestored(currentAccount);
            }
        } else if (item.id == BUTTON_DELETED_DIALOGS_OPEN) {
            presentFragment(new MZGramDeletedDialogsActivity());
        } else if (item.id == BUTTON_SEMI_TRANSPARENT_DELETED) {
            MZGramConfig.toggleSemiTransparentDeleted();
            ((TextCheckCell) view).setChecked(MZGramConfig.semiTransparentDeleted);
        } else if (item.id == BUTTON_M3_SWITCHES) {
            MZGramConfig.toggleM3Switches();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.m3Switches, true);
            } else if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.m3Switches);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            if (listView != null) {
                listView.invalidate();
            }
        } else if (item.id == BUTTON_CARDS_DESIGN) {
            MZGramConfig.toggleCardsDesign();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.cardsDesign, true);
            } else if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.cardsDesign);
            }
            if (listView != null && listView.adapter != null) {
                if (MZGramConfig.cardsDesign) {
                    listView.setSections();
                }
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_ERASE_LOCAL_DATABASE) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramEraseLocalDatabase))
                    .setMessage(getString(R.string.MZGramEraseLocalDatabaseConfirm))
                    .setPositiveButton(getString(R.string.MZGramClear), (dialog, which) ->
                            org.telegram.messenger.mzgram.MZGramLocalDatabase.erase(getCurrentAccount()))
                    .setNegativeButton(getString(R.string.MZGramCancel), null)
                    .makeRed(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE)
                    .show();
        } else if (item.id == BUTTON_DELETED_MARK) {
            editMark(getString(R.string.MZGramDeletedMarkText), MZGramConfig.deletedMark, MZGramConfig::setDeletedMark);
        } else if (item.id == BUTTON_EDITED_MARK) {
            editMark(getString(R.string.MZGramEditedMarkText), MZGramConfig.editedMark, MZGramConfig::setEditedMark);
        } else if (item.id == BUTTON_WIPE_ARCHIVE) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.MZGramWipeArchive))
                    .setMessage(getString(R.string.MZGramWipeArchiveConfirm))
                    .setPositiveButton(getString(R.string.MZGramDelete), (dialog, which) -> {
                        // The button shows progress while the archive goes,
                        // then the box closes and a message says what was
                        // done. Own thread: gigabytes of files take a while
                        // and globalQueue is shared.
                        org.telegram.messenger.browser.Browser.Progress progress = ((org.telegram.ui.ActionBar.AlertDialog) dialog).makeButtonLoading(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE);
                        progress.init();
                        new Thread(() -> {
                            org.telegram.messenger.mzgram.MZGramArchiveWipe.Result result = null;
                            try {
                                result = org.telegram.messenger.mzgram.MZGramHistoryController.getInstance().wipeArchive();
                            } catch (Exception e) {
                                org.telegram.messenger.FileLog.e("MZGramSettingsActivity.wipeArchive", e);
                            }
                            org.telegram.messenger.mzgram.MZGramArchiveWipe.Result done = result;
                            AndroidUtilities.runOnUIThread(() -> {
                                progress.end();
                                // Nothing left of the archive: 0 at once
                                sizeCount++;
                                if (sizeView != null) {
                                    sizeView.setSizes(new org.telegram.messenger.mzgram.MZGramArchiveSize.Sizes());
                                }
                                showWipeResult(done);
                            });
                        }, "MZGramArchiveWipe").start();
                    })
                    .setNegativeButton(getString(R.string.MZGramCancel), null)
                    .makeRed(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE)
                    .show();
        } else if (item.id == BUTTON_DISABLE_SPONSORED_MESSAGES) {
            MZGramConfig.toggleDisableSponsoredMessages();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableSponsoredMessages);
            if (MZGramConfig.disableSponsoredMessages) {
                // Clears an already-showing chat-list promo/proxy banner
                // immediately, not just future ones.
                getMessagesController().hidePromoDialog();
            }
        } else if (item.id == BUTTON_HIDE_REACTIONS_PRIVATE) {
            MZGramConfig.toggleHideReactionsPrivate();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideReactionsPrivate);
        } else if (item.id == BUTTON_HIDE_REACTIONS_GROUPS) {
            MZGramConfig.toggleHideReactionsGroups();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideReactionsGroups);
        } else if (item.id == BUTTON_HIDE_REACTIONS_CHANNELS) {
            MZGramConfig.toggleHideReactionsChannels();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideReactionsChannels);
        } else if (item.id == BUTTON_SHADOW_BAN) {
            MZGramConfig.toggleShadowBan();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_HIDE_BLOCKED_IN_GROUPS) {
            MZGramConfig.toggleHideBlockedInGroups();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideBlockedInGroups);
        } else if (item.id == BUTTON_REGEX_FILTERS) {
            MZGramConfig.toggleRegexFilters();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_REGEX_FILTERS_RULES) {
            presentFragment(new MZGramRegexFiltersActivity());
        } else if (item.id == BUTTON_SHADOW_BAN_LIST) {
            presentFragment(new MZGramShadowBanActivity());
        } else if (item.id == BUTTON_HIDDEN_REACTIONS) {
            editMark(getString(R.string.MZGramHiddenReactions), MZGramConfig.hiddenReactions, org.telegram.messenger.mzgram.MZGramReactionFilter::setHiddenList);
        } else if (item.id == BUTTON_STRIP_ZALGO_TEXT) {
            MZGramConfig.toggleStripZalgoText();
            ((TextCheckCell) view).setChecked(MZGramConfig.stripZalgoText);
        } else if (item.id == BUTTON_EXPORT_ARCHIVE) {
            // Android's "save as": the user picks where the file goes (Downloads,
            // a file manager, a cloud drive), and the archive is written there
            // (onActivityResultFragment). Before, the copy in the cache folder was
            // opened for viewing through the FileProvider, which has no root for
            // that folder, so Export crashed the app.
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, EXPORT_FILE_NAME);
            try {
                startActivityForResult(intent, REQUEST_CODE_EXPORT_ARCHIVE);
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e("MZGramSettingsActivity.exportArchive", e);
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramArchiveExportFailed)).show();
            }
        } else if (item.id == BUTTON_PLUGINS_BACKUP) {
            MZGramConfig.togglePluginsBackup();
            ((TextCheckCell) view).setChecked(MZGramConfig.pluginsBackup);
        } else if (item.id == BUTTON_VIDEO_SUBTITLES) {
            MZGramConfig.toggleVideoSubtitles();
            ((TextCheckCell) view).setChecked(MZGramConfig.videoSubtitles);
        } else if (item.id == BUTTON_PERSIAN_CALENDAR) {
            MZGramConfig.togglePersianCalendar();
            ((TextCheckCell) view).setChecked(MZGramConfig.persianCalendar);
        } else if (item.id == BUTTON_SETTINGS_EXPORT) {
            // Android's "save as", as for the archive: the user picks where the file goes
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType(org.telegram.messenger.mzgram.MZGramSettingsBackup.MIME_TYPE);
            intent.putExtra(android.content.Intent.EXTRA_TITLE, org.telegram.messenger.mzgram.MZGramSettingsBackup.FILE_NAME);
            try {
                startActivityForResult(intent, REQUEST_CODE_EXPORT_SETTINGS);
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e("MZGramSettingsActivity.exportSettings", e);
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramSettingsExportFailed)).show();
            }
        } else if (item.id == BUTTON_SETTINGS_IMPORT) {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQUEST_CODE_IMPORT_SETTINGS);
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importSettings", e);
            }
        } else if (item.id == BUTTON_IMPORT_FOREIGN_ARCHIVE) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.MZGramImportForeignArchive))
                    .setMessage(getString(R.string.MZGramImportForeignArchiveConfirm))
                    .setPositiveButton(getString(R.string.MZGramContinue), (dialog, which) -> {
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        try {
                            startActivityForResult(intent, REQUEST_CODE_IMPORT_FOREIGN_ARCHIVE);
                        } catch (Exception e) {
                            org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importForeignArchive", e);
                        }
                    })
                    .setNegativeButton(getString(R.string.MZGramCancel), null)
                    .show();
        } else if (item.id == BUTTON_IMPORT_ARCHIVE) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.MZGramImportArchive))
                    .setMessage(getString(R.string.MZGramImportArchiveConfirm))
                    .setPositiveButton(getString(R.string.MZGramContinue), (dialog, which) -> {
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        try {
                            startActivityForResult(intent, REQUEST_CODE_IMPORT_ARCHIVE);
                        } catch (Exception e) {
                            org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importArchive", e);
                        }
                    })
                    .setNegativeButton(getString(R.string.MZGramCancel), null)
                    .show();
        } else if (item.id == BUTTON_M3_GROUP) {
            md3Expanded = !md3Expanded;
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_M3_CHAT_HEADER) {
            MZGramConfig.toggleM3ChatHeader();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.m3ChatHeader, true);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_M3_NAV_BAR) {
            MZGramConfig.toggleM3NavBar();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.m3NavBar, true);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_MD3_PLAYER) {
            MZGramConfig.toggleMusicPlayer();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.musicPlayer, true);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_MD3_MINI_PLAYER) {
            MZGramConfig.toggleMusicPlayerMini();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.musicPlayerMini, true);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_IOS_GROUP) {
            iosExpanded = !iosExpanded;
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_IOS_NAV_BAR) {
            MZGramConfig.toggleIosNavBar();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.iosNavBar, true);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_IOS_CHAT_HEADER) {
            MZGramConfig.toggleIosChatHeader();
            if (view instanceof org.telegram.ui.Cells.CheckBoxCell) {
                ((org.telegram.ui.Cells.CheckBoxCell) view).setChecked(MZGramConfig.iosChatHeader, true);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_NAVIGATION_SECTION) {
            presentFragment(new MZGramSettingsActivity(SECTION_NAVIGATION));
        } else if (item.id == BUTTON_SURFACE_HIDING) {
            presentFragment(new MZGramSettingsActivity(SECTION_HIDING));
        } else if (item.id == BUTTON_EMOJI_SETS) {
            if (getParentActivity() != null) {
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                        .setTitle(getString(R.string.MZGramEmojiSets))
                        .setMessage(getString(R.string.MZGramEmojiSetsInfo))
                        .setPositiveButton(getString(R.string.MZGramOk), null)
                        .show();
            }
        } else if (item.id == BUTTON_PLAYER_STYLE) {
            org.telegram.ui.mzgram.player.MZGramPlayerSheet.openStyleEditor(this);
        } else if (item.id == BUTTON_USE_SYSTEM_FONTS) {
            MZGramConfig.toggleUseSystemFonts();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.useSystemFonts);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }
    }

    // The settings of a backup file: how many, then written over the current ones (MZGramSettingsBackup)
    private void confirmSettingsImport(java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, Object>> backup, String fullJson) {
        if (getParentActivity() == null) {
            return;
        }
        final int count = org.telegram.messenger.mzgram.MZGramSettingsBackup.count(backup);
        if (count == 0) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramSettingsImportEmpty)).show();
            return;
        }
        final int pluginCount = org.telegram.messenger.mzgram.MZGramConfig.pluginsBackup
                ? org.telegram.messenger.mzgram.plugins.MZGramPluginsBackup.countPluginsInJson(fullJson)
                : 0;
        String message = org.telegram.messenger.LocaleController.formatString(R.string.MZGramSettingsImportConfirm, count);
        if (pluginCount > 0) {
            message += "\n\n" + org.telegram.messenger.LocaleController.formatString(R.string.MZGramSettingsImportPluginsCount, pluginCount);
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.MZGramSettingsImport))
                .setMessage(message)
                .setPositiveButton(getString(R.string.MZGramContinue), (dialog, which) -> org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                    int written = 0;
                    try {
                        written = org.telegram.messenger.mzgram.MZGramSettingsBackup.restore(backup, fullJson);
                    } catch (Exception e) {
                        org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importSettings", e);
                    }
                    final int restored = written;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() == null) {
                            return;
                        }
                        if (restored > 0) {
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                                    org.telegram.messenger.LocaleController.formatString(R.string.MZGramSettingsImported, restored)).show();
                        } else {
                            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramSettingsImportFailed)).show();
                        }
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    });
                }))
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        android.content.ContentResolver resolver = org.telegram.messenger.ApplicationLoader.applicationContext.getContentResolver();
        if (requestCode == REQUEST_CODE_EXPORT_SETTINGS) {
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                boolean done = false;
                try {
                    String text = org.telegram.messenger.mzgram.MZGramSettingsBackup.export();
                    if (text != null) {
                        try (java.io.OutputStream out = resolver.openOutputStream(uri, "wt")) {
                            if (out == null) {
                                throw new java.io.IOException("no output for " + uri);
                            }
                            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                        done = true;
                    }
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.exportSettings", e);
                }
                final boolean exported = done;
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (exported) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramSettingsExported)).show();
                    } else {
                        BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramSettingsExportFailed)).show();
                    }
                });
            });
        } else if (requestCode == REQUEST_CODE_IMPORT_SETTINGS) {
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, Object>> values = null;
                String fullText = null;
                try (java.io.InputStream input = resolver.openInputStream(uri)) {
                    if (input != null) {
                        fullText = org.telegram.messenger.mzgram.MZGramSettingsBackup.read(input);
                        values = org.telegram.messenger.mzgram.MZGramSettingsBackup.parse(fullText);
                    }
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importSettings", e);
                }
                final java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, Object>> backup = values;
                final String textCopy = fullText;
                AndroidUtilities.runOnUIThread(() -> confirmSettingsImport(backup, textCopy));
            });
        } else if (requestCode == REQUEST_CODE_APP_FONT) {
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                final String name = org.telegram.messenger.mzgram.MZGramFonts.importFont(uri);
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    MZGramAppFont.fileImported(this, name, () -> {
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    });
                });
            });
        } else if (requestCode == REQUEST_CODE_EXPORT_ARCHIVE) {
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                boolean done = false;
                java.io.File file = null;
                try {
                    file = org.telegram.messenger.mzgram.MZGramHistoryController.getInstance().exportArchive();
                    try (java.io.InputStream in = new java.io.FileInputStream(file); java.io.OutputStream out = resolver.openOutputStream(uri, "wt")) {
                        if (out == null) {
                            throw new java.io.IOException("no output for " + uri);
                        }
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            out.write(buffer, 0, read);
                        }
                    }
                    done = true;
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.exportArchive", e);
                } finally {
                    if (file != null) {
                        file.delete();
                    }
                }
                showArchiveResult(done, R.string.MZGramArchiveExported, R.string.MZGramArchiveExportFailed);
            });
        } else if (requestCode == REQUEST_CODE_IMPORT_ARCHIVE) {
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                boolean done = false;
                try (java.io.InputStream input = resolver.openInputStream(uri)) {
                    if (input != null) {
                        org.telegram.messenger.mzgram.MZGramHistoryController.getInstance().importArchive(input);
                        done = true;
                    }
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importArchive", e);
                }
                showArchiveResult(done, R.string.MZGramArchiveImported, R.string.MZGramArchiveImportFailed);
                AndroidUtilities.runOnUIThread(this::countArchiveSize);
            });
        } else if (requestCode == REQUEST_CODE_IMPORT_FOREIGN_ARCHIVE) {
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                org.telegram.messenger.mzgram.MZGramForeignArchiveImport.Result result = null;
                try (java.io.InputStream input = resolver.openInputStream(uri)) {
                    if (input != null) {
                        result = org.telegram.messenger.mzgram.MZGramForeignArchiveImport.importFrom(input);
                    }
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importForeignArchive", e);
                }
                final org.telegram.messenger.mzgram.MZGramForeignArchiveImport.Result done = result;
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (done != null) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                                org.telegram.messenger.LocaleController.formatString(R.string.MZGramImportForeignArchiveDone, done.deleted, done.edited)).show();
                    } else {
                        BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramImportForeignArchiveFailed)).show();
                    }
                    countArchiveSize();
                });
            });
        }
    }

    // The archive's size, counted on a thread of its own: the media of an
    // archive can take gigabytes
    private void countArchiveSize() {
        if (sizeView == null) {
            return;
        }
        sizeView.setCounting();
        int count = ++sizeCount;
        new Thread(() -> {
            org.telegram.messenger.mzgram.MZGramArchiveSize.Sizes sizes = org.telegram.messenger.mzgram.MZGramArchiveSize.measureArchive();
            AndroidUtilities.runOnUIThread(() -> {
                if (count == sizeCount && sizeView != null) {
                    sizeView.setSizes(sizes);
                }
            });
        }, "MZGramArchiveSize").start();
    }

    public MZGramArchiveSizeView sizeViewForTest() {
        return sizeView;
    }

    // After Clear archive: cleared and how much was freed, or what could
    // not be deleted
    private void showWipeResult(org.telegram.messenger.mzgram.MZGramArchiveWipe.Result result) {
        if (getParentActivity() == null) {
            return;
        }
        if (result == null || !result.complete) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramArchiveFilesLeft)).show();
        } else if (result.files > 0) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramArchiveCleared),
                    formatString(R.string.MZGramArchiveFilesDeleted, AndroidUtilities.formatFileSize(result.bytes))).show();
        } else {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramArchiveCleared)).show();
        }
    }

    private void showArchiveResult(boolean done, int doneText, int failedText) {
        AndroidUtilities.runOnUIThread(() -> {
            if (getParentActivity() == null) {
                return;
            }
            if (done) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(doneText)).show();
            } else {
                BulletinFactory.of(this).createErrorBulletin(getString(failedText)).show();
            }
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (avatarCornersPreviewCell != null) {
            avatarCornersPreviewCell.invalidate();
        }
        if (chatListPreviewCell != null) {
            chatListPreviewCell.invalidate();
        }
        if (foldersPreviewCell != null) {
            foldersPreviewCell.invalidate();
        }
        if (fabShapeCell != null) {
            fabShapeCell.updateSelection(false);
            fabShapeCell.invalidate();
        }
        // Shows switches changed elsewhere (for example on another page).
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
