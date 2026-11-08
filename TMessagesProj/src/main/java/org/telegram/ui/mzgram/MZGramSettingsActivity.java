/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram: a list of topics; tapping one opens its own page with
 * that topic's switches. Every MZGram feature has its switch on one of them.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
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
    private static final int BUTTON_HIDE_CHANNEL_BOTTOM_BUTTONS = 7;
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
    private static final int BUTTON_HIDE_BOTTOM_NAVIGATION_BAR = 23;
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

    // The topics. The id is the index in SECTION_TITLES, SECTION_INFOS,
    // SECTION_ICONS and SECTION_COLORS; MAIN_PAGE_ORDER says where the main
    // page lists each one.
    //
    // A new topic (CHATS, NAVIGATION, APPEARANCE, SYSTEM, PLUGINS come with
    // the domains that fill them): the next id, SECTIONS_COUNT + 1, a title
    // and an info string (MZGramSection<Name>, MZGramSection<Name>Info, 12
    // languages), an icon and a color appended to the four arrays, its place
    // in MAIN_PAGE_ORDER, a case in fillItems with a fill<Name> method. Only
    // together with its first switch: a topic is never empty.
    public static final int SECTION_MAIN = -1;
    public static final int SECTION_ARCHIVE = 0;
    public static final int SECTION_GHOST_MODE = 1;
    public static final int SECTION_MESSAGE_MENU = 2;
    public static final int SECTION_MEDIA_AND_CALLS = 3;
    public static final int SECTION_INTERFACE = 4;
    public static final int SECTION_ADS_AND_FILTERS = 5;
    public static final int SECTION_AI = 6;
    public static final int SECTION_OTHER = 7;
    public static final int SECTION_PRIVACY = 8;
    public static final int SECTIONS_COUNT = 9;
    // Row ids of the topics on the main page; clear of the BUTTON_ ids,
    // which stay below it.
    public static final int SECTION_ROW_ID = 1000;
    // "Other" stays last: new topics go before it.
    private static final int[] MAIN_PAGE_ORDER = {
            SECTION_ARCHIVE,
            SECTION_GHOST_MODE,
            SECTION_PRIVACY,
            SECTION_MESSAGE_MENU,
            SECTION_MEDIA_AND_CALLS,
            SECTION_INTERFACE,
            SECTION_ADS_AND_FILTERS,
            SECTION_AI,
            SECTION_OTHER,
    };

    private static final int[] SECTION_TITLES = {
            R.string.MZGramSectionArchive,
            R.string.MZGramSectionGhostMode,
            R.string.MZGramSectionMessageMenu,
            R.string.MZGramSectionMediaAndCalls,
            R.string.MZGramSectionInterface,
            R.string.MZGramSectionAdsAndFilters,
            R.string.MZGramSectionAi,
            R.string.MZGramSectionOther,
            R.string.MZGramSectionPrivacy,
    };
    private static final int[] SECTION_INFOS = {
            R.string.MZGramSectionArchiveInfo,
            R.string.MZGramSectionGhostModeInfo,
            R.string.MZGramSectionMessageMenuInfo,
            R.string.MZGramSectionMediaAndCallsInfo,
            R.string.MZGramSectionInterfaceInfo,
            R.string.MZGramSectionAdsAndFiltersInfo,
            R.string.MZGramSectionAiInfo,
            R.string.MZGramSectionOtherInfo,
            R.string.MZGramSectionPrivacyInfo,
    };
    private static final int[] SECTION_ICONS = {
            R.drawable.settings_data,
            R.drawable.settings_account,
            R.drawable.settings_chat,
            R.drawable.settings_calls,
            R.drawable.settings_features,
            R.drawable.settings_policy,
            R.drawable.settings_ask,
            R.drawable.settings_faq,
            R.drawable.settings_privacy,
    };
    private static final IconBackgroundColors[] SECTION_COLORS = {
            IconBackgroundColors.BLUE_DEEP,
            IconBackgroundColors.GRAY,
            IconBackgroundColors.ORANGE,
            IconBackgroundColors.CYAN,
            IconBackgroundColors.PURPLE,
            IconBackgroundColors.RED,
            IconBackgroundColors.GREEN,
            IconBackgroundColors.BLUE_LIGHT,
            IconBackgroundColors.ORANGE_DEEP,
    };

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
        return section == SECTION_MAIN ? getString(R.string.MZGram) : getString(SECTION_TITLES[section]);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        switch (section) {
            case SECTION_ARCHIVE: fillArchive(items); break;
            case SECTION_GHOST_MODE: fillGhostMode(items); break;
            case SECTION_MESSAGE_MENU: fillMessageMenu(items); break;
            case SECTION_MEDIA_AND_CALLS: fillMediaAndCalls(items); break;
            case SECTION_INTERFACE: fillInterface(items); break;
            case SECTION_ADS_AND_FILTERS: fillAdsAndFilters(items); break;
            case SECTION_AI: fillAi(items); break;
            case SECTION_OTHER: fillOther(items); break;
            case SECTION_PRIVACY: fillPrivacy(items); break;
            default: fillMain(items); break;
        }
    }

    // The main page: one row per topic, with its icon and what it holds.
    private void fillMain(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        for (int i : MAIN_PAGE_ORDER) {
            items.add(SettingsActivity.SettingCell.Factory.of(SECTION_ROW_ID + i, SECTION_COLORS[i].top, SECTION_COLORS[i].bottom, SECTION_ICONS[i], getString(SECTION_TITLES[i]), getString(SECTION_INFOS[i])));
        }
        // "Features of this client" is only the subtitle of the MZGram row in Settings
        items.add(UItem.asShadow(null));
    }

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
        items.add(UItem.asCheck(BUTTON_DELETED_MESSAGES_LIST, getString(R.string.MZGramDeletedMessagesList)).setChecked(MZGramConfig.deletedMessagesList));
        items.add(UItem.asShadow(getString(R.string.MZGramDeletedMessagesListInfo)));
        items.add(UItem.asCheck(BUTTON_DELETED_DIALOGS_LIST, getString(R.string.MZGramDeletedDialogsList)).setChecked(MZGramConfig.deletedDialogsList));
        if (MZGramConfig.deletedDialogsList) {
            items.add(UItem.asButton(BUTTON_DELETED_DIALOGS_OPEN, getString(R.string.MZGramDeletedDialogs)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramDeletedDialogsListInfo)));
        items.add(UItem.asButton(BUTTON_DELETED_MARK, getString(R.string.MZGramDeletedMarkText), MZGramConfig.deletedMark));
        items.add(UItem.asButton(BUTTON_EDITED_MARK, getString(R.string.MZGramEditedMarkText), MZGramConfig.editedMark));
        items.add(UItem.asCheck(BUTTON_SEMI_TRANSPARENT_DELETED, getString(R.string.MZGramSemiTransparentDeleted)).setChecked(MZGramConfig.semiTransparentDeleted));
        items.add(UItem.asShadow(getString(R.string.MZGramArchiveLookInfo)));
        items.add(UItem.asButton(BUTTON_WIPE_ARCHIVE, getString(R.string.MZGramWipeArchive)));
        items.add(UItem.asShadow(getString(R.string.MZGramWipeArchiveInfo)));
        items.add(UItem.asButton(BUTTON_EXPORT_ARCHIVE, getString(R.string.MZGramExportArchive)));
        items.add(UItem.asButton(BUTTON_IMPORT_ARCHIVE, getString(R.string.MZGramImportArchive)));
        items.add(UItem.asShadow(getString(R.string.MZGramExportImportArchiveInfo)));
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

    private static String languageValue() {
        if (org.telegram.messenger.mzgram.MZGramLanguage.SYSTEM.equals(MZGramConfig.language)) {
            return getString(R.string.MZGramLanguageSystem);
        }
        if (org.telegram.messenger.mzgram.MZGramLanguage.indexOf(MZGramConfig.language) < 0) {
            return getString(R.string.MZGramLanguageTelegram);
        }
        return org.telegram.messenger.mzgram.MZGramLanguage.name(MZGramConfig.language);
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
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_GHOST_MODE, getString(R.string.MZGramGhostMode)).setChecked(MZGramConfig.ghostMode));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostModeInfo)));
        items.add(UItem.asCheck(BUTTON_GHOST_AUTO_DELAY_SEND, getString(R.string.MZGramGhostAutoDelaySend)).setChecked(MZGramConfig.ghostAutoDelaySend));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostAutoDelaySendInfo)));
        items.add(UItem.asCheck(BUTTON_GHOST_SILENT_SEND, getString(R.string.MZGramGhostSilentSend)).setChecked(MZGramConfig.ghostSilentSend));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostSilentSendInfo)));
        items.add(UItem.asCheck(BUTTON_OFFER_GHOST_MODE_BEFORE_STORIES, getString(R.string.MZGramOfferGhostModeBeforeStoriesToggle)).setChecked(MZGramConfig.offerGhostModeBeforeStories));
        items.add(UItem.asShadow(getString(R.string.MZGramOfferGhostModeBeforeStoriesToggleInfo)));
        items.add(UItem.asCheck(BUTTON_GHOST_MODE_EXCLUSIONS, getString(R.string.MZGramGhostModeExclusions)).setChecked(MZGramConfig.ghostModeExclusions));
        items.add(UItem.asShadow(getString(R.string.MZGramGhostModeExclusionsInfo)));
    }

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
        items.add(UItem.asCheck(BUTTON_KEEP_GROUP_READS, getString(R.string.MZGramKeepGroupReads)).setChecked(MZGramConfig.keepGroupReads));
        items.add(UItem.asShadow(getString(R.string.MZGramKeepGroupReadsInfo)));
        items.add(UItem.asCheck(BUTTON_BOT_START_CONFIRM, getString(R.string.MZGramBotStartConfirm)).setChecked(MZGramConfig.botStartConfirm));
        items.add(UItem.asShadow(getString(R.string.MZGramBotStartConfirmInfo)));
        items.add(UItem.asCheck(BUTTON_REDUCE_NETWORK_TRACKING, getString(R.string.MZGramReduceNetworkTracking)).setChecked(MZGramConfig.reduceNetworkTracking));
        items.add(UItem.asShadow(getString(R.string.MZGramReduceNetworkTrackingInfo)));
        items.add(UItem.asCheck(BUTTON_PASSCODE_ACCOUNTS, getString(R.string.MZGramPasscodeAccounts)).setChecked(MZGramConfig.passcodeAccounts));
        if (MZGramConfig.passcodeAccounts) {
            items.add(UItem.asButton(BUTTON_PASSCODE_ACCOUNTS_SETUP, getString(R.string.MZGramPasscodeAccountsSetup)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramPasscodeAccountsInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_READ_STICKERS, getString(R.string.MZGramDisableReadStickers)).setChecked(MZGramConfig.disableReadStickers));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableReadStickersInfo)));
    }

    private void fillMessageMenu(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_SHOW_COPY_PHOTO, getString(R.string.MZGramCopyPhoto)).setChecked(MZGramConfig.showCopyPhoto));
        items.add(UItem.asShadow(getString(R.string.MZGramShowCopyPhotoInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_DELETE_DOWNLOADED_FILE, getString(R.string.MZGramDeleteDownloadedFile)).setChecked(MZGramConfig.showDeleteDownloadedFile));
        items.add(UItem.asShadow(getString(R.string.MZGramShowDeleteDownloadedFileInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_ADD_TO_SAVED_MESSAGES, getString(R.string.MZGramSaveMessage)).setChecked(MZGramConfig.showAddToSavedMessages));
        items.add(UItem.asShadow(getString(R.string.MZGramShowSaveMessageInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_SET_REMINDER, getString(R.string.MZGramSetReminder)).setChecked(MZGramConfig.showSetReminder));
        items.add(UItem.asShadow(getString(R.string.MZGramShowSetReminderInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_REPEAT, getString(R.string.MZGramRepeat)).setChecked(MZGramConfig.showRepeat));
        items.add(UItem.asShadow(getString(R.string.MZGramShowRepeatInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_OPEN_IN, getString(R.string.MZGramOpenIn)).setChecked(MZGramConfig.showOpenIn));
        items.add(UItem.asShadow(getString(R.string.MZGramShowOpenInInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_MESSAGE_DETAILS, getString(R.string.MZGramMessageDetails)).setChecked(MZGramConfig.showMessageDetails));
        items.add(UItem.asShadow(getString(R.string.MZGramShowMessageDetailsInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_QR_CODE, getString(R.string.MZGramQrCode)).setChecked(MZGramConfig.showQrCode));
        items.add(UItem.asShadow(getString(R.string.MZGramShowQrCodeInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_NO_QUOTE_FORWARD, getString(R.string.MZGramForwardNoQuote)).setChecked(MZGramConfig.showNoQuoteForward));
        items.add(UItem.asShadow(getString(R.string.MZGramShowForwardNoQuoteInfo)));
        items.add(UItem.asCheck(BUTTON_SAVE_PROTECTED_CONTENT, getString(R.string.MZGramSaveProtectedContent)).setChecked(MZGramConfig.saveProtectedContent));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveProtectedContentInfo)));
        items.add(UItem.asCheck(BUTTON_DELETE_FOR_ALL_BY_DEFAULT, getString(R.string.MZGramDeleteForAllByDefault)).setChecked(MZGramConfig.deleteForAllByDefault));
        items.add(UItem.asShadow(getString(R.string.MZGramDeleteForAllByDefaultInfo)));
    }

    private void fillMediaAndCalls(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_ASK_BEFORE_CALL, getString(R.string.MZGramAskBeforeCall)).setChecked(MZGramConfig.askBeforeCall));
        items.add(UItem.asShadow(getString(R.string.MZGramAskBeforeCallInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_INSTANT_CAMERA, getString(R.string.MZGramDisableInstantCamera)).setChecked(MZGramConfig.disableInstantCamera));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableInstantCameraInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_CAMERA_TILE, getString(R.string.MZGramHideCameraTile)).setChecked(MZGramConfig.hideCameraTile));
        items.add(UItem.asShadow(getString(R.string.MZGramHideCameraTileInfo)));
        items.add(UItem.asCheck(BUTTON_PREFER_ORIGINAL_QUALITY, getString(R.string.MZGramPreferOriginalQuality)).setChecked(MZGramConfig.preferOriginalQuality));
        items.add(UItem.asShadow(getString(R.string.MZGramPreferOriginalQualityInfo)));
        items.add(UItem.asCheck(BUTTON_AUTO_PAUSE_VIDEO, getString(R.string.MZGramAutoPauseVideo)).setChecked(MZGramConfig.autoPauseVideo));
        items.add(UItem.asShadow(getString(R.string.MZGramAutoPauseVideoInfo)));
        items.add(UItem.asCheck(BUTTON_CONFIRM_AV_MESSAGE, getString(R.string.MZGramConfirmAVMessage)).setChecked(MZGramConfig.confirmAVMessage));
        items.add(UItem.asShadow(getString(R.string.MZGramConfirmAVMessageInfo)));
        items.add(UItem.asCheck(BUTTON_MEDIA_PREVIEW_ON_LONG_PRESS, getString(R.string.MZGramMediaPreviewOnLongPress)).setChecked(MZGramConfig.mediaPreviewOnLongPress));
        items.add(UItem.asShadow(getString(R.string.MZGramMediaPreviewOnLongPressInfo)));
    }

    private void fillInterface(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(BUTTON_LANGUAGE, getString(R.string.MZGramLanguage), languageValue()));
        items.add(UItem.asShadow(getString(R.string.MZGramLanguageInfo)));
        items.add(UItem.asButton(BUTTON_PEER_ID, getString(R.string.MZGramPeerId), peerIdValue()));
        items.add(UItem.asShadow(getString(R.string.MZGramPeerIdInfo)));
        items.add(UItem.asCheck(BUTTON_FOLDER_TABS_AT_BOTTOM, getString(R.string.MZGramFolderTabsAtBottom)).setChecked(MZGramConfig.folderTabsAtBottom));
        items.add(UItem.asShadow(getString(R.string.MZGramFolderTabsAtBottomInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_BOTTOM_NAVIGATION_BAR, getString(R.string.MZGramHideBottomNavigationBar)).setChecked(MZGramConfig.hideBottomNavigationBar));
        items.add(UItem.asShadow(getString(R.string.MZGramHideBottomNavigationBarInfo)));
        items.add(UItem.asCheck(BUTTON_ACCOUNTS_IN_MENU, getString(R.string.MZGramAccountsInMenu)).setChecked(MZGramConfig.accountsInMenu));
        items.add(UItem.asShadow(getString(R.string.MZGramAccountsInMenuInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_STORIES, getString(R.string.MZGramHideStories)).setChecked(MZGramConfig.hideStories));
        items.add(UItem.asShadow(getString(R.string.MZGramHideStoriesInfo)));
        items.add(UItem.asCheck(BUTTON_OPEN_ARCHIVE_ON_PULL, getString(R.string.MZGramOpenArchiveOnPull)).setChecked(MZGramConfig.openArchiveOnPull));
        items.add(UItem.asShadow(getString(R.string.MZGramOpenArchiveOnPullInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_NUMBER_ROUNDING, getString(R.string.MZGramDisableNumberRounding)).setChecked(MZGramConfig.disableNumberRounding));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableNumberRoundingInfo)));
        items.add(UItem.asCheck(BUTTON_FORMAT_TIME_WITH_SECONDS, getString(R.string.MZGramFormatTimeWithSeconds)).setChecked(MZGramConfig.formatTimeWithSeconds));
        items.add(UItem.asShadow(getString(R.string.MZGramFormatTimeWithSecondsInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_GREETING_STICKER, getString(R.string.MZGramDisableGreetingSticker)).setChecked(MZGramConfig.disableGreetingSticker));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableGreetingStickerInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_CHANNEL_BOTTOM_BUTTONS, getString(R.string.MZGramHideChannelBottomButtons)).setChecked(MZGramConfig.hideChannelBottomButtons));
        items.add(UItem.asShadow(getString(R.string.MZGramHideChannelBottomButtonsInfo)));
        items.add(UItem.asCheck(BUTTON_PREDICTIVE_BACK_ANIMATION, getString(R.string.MZGramPredictiveBackAnimation)).setChecked(MZGramConfig.predictiveBackAnimation));
        items.add(UItem.asShadow(getString(R.string.MZGramPredictiveBackAnimationInfo)));
        items.add(UItem.asCheck(BUTTON_GOOEY_AVATAR_ANIMATION, getString(R.string.MZGramGooeyAvatarAnimation)).setChecked(MZGramConfig.gooeyAvatarAnimation));
        items.add(UItem.asShadow(getString(R.string.MZGramGooeyAvatarAnimationInfo)));
        items.add(UItem.asHeader(getString(R.string.MZGramHideRowsTitle)));
        java.util.List<org.telegram.messenger.mzgram.MZGramSettingsRows.Row> rows = org.telegram.messenger.mzgram.MZGramSettingsRows.rows();
        for (int i = 0; i < rows.size(); i++) {
            items.add(UItem.asCheck(HIDE_ROW_BUTTONS[i], getString(HIDE_ROW_TITLES[i])).setChecked(org.telegram.messenger.mzgram.MZGramSettingsRows.hidden(rows.get(i).key)));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramHideRowsInfo)));
    }

    private void fillAdsAndFilters(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_DISABLE_SPONSORED_MESSAGES, getString(R.string.MZGramDisableSponsoredMessages)).setChecked(MZGramConfig.disableSponsoredMessages));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableSponsoredMessagesInfo)));
        items.add(UItem.asCheck(BUTTON_STRIP_ZALGO_TEXT, getString(R.string.MZGramStripZalgoText)).setChecked(MZGramConfig.stripZalgoText));
        items.add(UItem.asShadow(getString(R.string.MZGramStripZalgoTextInfo)));
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
    }

    // The five AI sites, each by its name; the choice applies at once.
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

    private void fillOther(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_CHECK_UPDATES, getString(R.string.MZGramCheckUpdates)).setChecked(MZGramConfig.checkUpdates));
        items.add(UItem.asButton(BUTTON_CHECK_UPDATES_NOW, getString(R.string.MZGramUpdateCheckNow)));
        items.add(UItem.asShadow(getString(R.string.MZGramCheckUpdatesInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= SECTION_ROW_ID && item.id < SECTION_ROW_ID + SECTIONS_COUNT) {
            presentFragment(new MZGramSettingsActivity(item.id - SECTION_ROW_ID));
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
            ((TextCheckCell) view).setChecked(MZGramConfig.disableNumberRounding);
        } else if (item.id == BUTTON_FORMAT_TIME_WITH_SECONDS) {
            MZGramConfig.toggleFormatTimeWithSeconds();
            ((TextCheckCell) view).setChecked(MZGramConfig.formatTimeWithSeconds);
        } else if (item.id == BUTTON_ASK_BEFORE_CALL) {
            MZGramConfig.toggleAskBeforeCall();
            ((TextCheckCell) view).setChecked(MZGramConfig.askBeforeCall);
        } else if (item.id == BUTTON_HIDE_STORIES) {
            MZGramConfig.toggleHideStories();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideStories);
        } else if (item.id == BUTTON_DISABLE_GREETING_STICKER) {
            MZGramConfig.toggleDisableGreetingSticker();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableGreetingSticker);
        } else if (item.id == BUTTON_HIDE_CHANNEL_BOTTOM_BUTTONS) {
            MZGramConfig.toggleHideChannelBottomButtons();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideChannelBottomButtons);
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
        } else if (item.id == BUTTON_PEER_ID) {
            choosePeerId();
        } else if (item.id >= ARCHIVE_LIMIT_FIRST && item.id < ARCHIVE_LIMIT_FIRST + ARCHIVE_LIMIT_TITLES.length) {
            chooseLimit(item.id - ARCHIVE_LIMIT_FIRST);
        } else if (item.id == BUTTON_ARCHIVE_COUNTERS_RESET) {
            org.telegram.messenger.mzgram.MZGramArchiveLimits.resetCounters();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
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
        } else if (item.id == BUTTON_HIDE_BOTTOM_NAVIGATION_BAR) {
            MZGramConfig.toggleHideBottomNavigationBar();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideBottomNavigationBar);
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
        } else if (item.id == BUTTON_SAVE_PROTECTED_CONTENT) {
            MZGramConfig.toggleSaveProtectedContent();
            ((TextCheckCell) view).setChecked(MZGramConfig.saveProtectedContent);
        } else if (item.id == BUTTON_FOLDER_TABS_AT_BOTTOM) {
            MZGramConfig.toggleFolderTabsAtBottom();
            ((TextCheckCell) view).setChecked(MZGramConfig.folderTabsAtBottom);
        } else if (item.id == BUTTON_GHOST_AUTO_DELAY_SEND) {
            MZGramConfig.toggleGhostAutoDelaySend();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostAutoDelaySend);
        } else if (item.id == BUTTON_GHOST_SILENT_SEND) {
            MZGramConfig.toggleGhostSilentSend();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostSilentSend);
        } else if (item.id == BUTTON_OFFER_GHOST_MODE_BEFORE_STORIES) {
            MZGramConfig.toggleOfferGhostModeBeforeStories();
            ((TextCheckCell) view).setChecked(MZGramConfig.offerGhostModeBeforeStories);
        } else if (item.id == BUTTON_GHOST_MODE) {
            MZGramConfig.toggleGhostMode();
            ((TextCheckCell) view).setChecked(MZGramConfig.ghostMode);
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
        } else if (item.id == BUTTON_DELETED_MESSAGES_LIST) {
            MZGramConfig.toggleDeletedMessagesList();
            ((TextCheckCell) view).setChecked(MZGramConfig.deletedMessagesList);
        } else if (item.id == BUTTON_DELETED_DIALOGS_LIST) {
            MZGramConfig.toggleDeletedDialogsList();
            ((TextCheckCell) view).setChecked(MZGramConfig.deletedDialogsList);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_DELETED_DIALOGS_OPEN) {
            presentFragment(new MZGramDeletedDialogsActivity());
        } else if (item.id == BUTTON_SEMI_TRANSPARENT_DELETED) {
            MZGramConfig.toggleSemiTransparentDeleted();
            ((TextCheckCell) view).setChecked(MZGramConfig.semiTransparentDeleted);
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
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        android.content.ContentResolver resolver = org.telegram.messenger.ApplicationLoader.applicationContext.getContentResolver();
        if (requestCode == REQUEST_CODE_EXPORT_ARCHIVE) {
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
        // Shows switches changed elsewhere (for example on another page).
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
