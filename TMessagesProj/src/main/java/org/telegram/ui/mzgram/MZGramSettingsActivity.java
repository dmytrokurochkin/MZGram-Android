/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram: a list of topics; tapping one opens its own page with
 * that topic's switches. Every MZGram feature has its switch on one of them.
 */

package org.telegram.ui.mzgram;

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

    private static final int BUTTON_TEST_TOGGLE = 1;
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
    private static final int BUTTON_CONFIRM_AV_MESSAGE = 28;
    private static final int BUTTON_MEDIA_PREVIEW_ON_LONG_PRESS = 29;
    private static final int BUTTON_GHOST_AUTO_DELAY_SEND = 30;
    private static final int BUTTON_GHOST_SILENT_SEND = 31;
    private static final int BUTTON_OFFER_GHOST_MODE_BEFORE_STORIES = 32;
    private static final int BUTTON_WIPE_ARCHIVE = 33;
    private static final int BUTTON_EXPORT_ARCHIVE = 35;
    private static final int BUTTON_IMPORT_ARCHIVE = 36;
    private static final int REQUEST_CODE_IMPORT_ARCHIVE = 8842;
    private static final int BUTTON_DISABLE_SPONSORED_MESSAGES = 37;
    private static final int BUTTON_STRIP_ZALGO_TEXT = 38;
    private static final int BUTTON_FOLDER_TABS_AT_BOTTOM = 41;
    private static final int BUTTON_SAVE_PROTECTED_CONTENT = 42;

    // The topics, in the order the main page lists them.
    public static final int SECTION_MAIN = -1;
    public static final int SECTION_ARCHIVE = 0;
    public static final int SECTION_GHOST_MODE = 1;
    public static final int SECTION_MESSAGE_MENU = 2;
    public static final int SECTION_MEDIA_AND_CALLS = 3;
    public static final int SECTION_INTERFACE = 4;
    public static final int SECTION_ADS_AND_FILTERS = 5;
    public static final int SECTION_OTHER = 6;
    public static final int SECTIONS_COUNT = 7;
    // Row ids of the topics on the main page; clear of the BUTTON_ ids.
    public static final int SECTION_ROW_ID = 100;

    private static final int[] SECTION_TITLES = {
            R.string.MZGramSectionArchive,
            R.string.MZGramSectionGhostMode,
            R.string.MZGramSectionMessageMenu,
            R.string.MZGramSectionMediaAndCalls,
            R.string.MZGramSectionInterface,
            R.string.MZGramSectionAdsAndFilters,
            R.string.MZGramSectionOther,
    };
    private static final int[] SECTION_INFOS = {
            R.string.MZGramSectionArchiveInfo,
            R.string.MZGramSectionGhostModeInfo,
            R.string.MZGramSectionMessageMenuInfo,
            R.string.MZGramSectionMediaAndCallsInfo,
            R.string.MZGramSectionInterfaceInfo,
            R.string.MZGramSectionAdsAndFiltersInfo,
            R.string.MZGramSectionOtherInfo,
    };
    private static final int[] SECTION_ICONS = {
            R.drawable.settings_data,
            R.drawable.settings_account,
            R.drawable.settings_chat,
            R.drawable.settings_calls,
            R.drawable.settings_features,
            R.drawable.settings_policy,
            R.drawable.settings_faq,
    };
    private static final IconBackgroundColors[] SECTION_COLORS = {
            IconBackgroundColors.BLUE_DEEP,
            IconBackgroundColors.GRAY,
            IconBackgroundColors.ORANGE,
            IconBackgroundColors.CYAN,
            IconBackgroundColors.PURPLE,
            IconBackgroundColors.RED,
            IconBackgroundColors.BLUE_LIGHT,
    };

    private final int section;

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
            case SECTION_OTHER: fillOther(items); break;
            default: fillMain(items); break;
        }
    }

    // The main page: one row per topic, with its icon and what it holds.
    private void fillMain(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        for (int i = 0; i < SECTIONS_COUNT; i++) {
            items.add(SettingsActivity.SettingCell.Factory.of(SECTION_ROW_ID + i, SECTION_COLORS[i].top, SECTION_COLORS[i].bottom, SECTION_ICONS[i], getString(SECTION_TITLES[i]), getString(SECTION_INFOS[i])));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramSettingsInfo)));
    }

    private void fillArchive(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_SAVE_DELETED_MESSAGES, getString(R.string.MZGramSaveDeletedMessages)).setChecked(MZGramConfig.saveDeletedMessages));
        items.add(UItem.asCheck(BUTTON_SAVE_EDIT_HISTORY, getString(R.string.MZGramSaveEditHistory)).setChecked(MZGramConfig.saveEditHistory));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveMessageHistoryInfo)));
        items.add(UItem.asCheck(BUTTON_SAVE_ARCHIVE_MEDIA, getString(R.string.MZGramSaveArchiveMedia)).setChecked(MZGramConfig.saveArchiveMedia));
        items.add(UItem.asCheck(BUTTON_SAVE_FORMATTING, getString(R.string.MZGramSaveFormatting)).setChecked(MZGramConfig.saveFormatting));
        items.add(UItem.asCheck(BUTTON_SAVE_REACTIONS, getString(R.string.MZGramSaveReactions)).setChecked(MZGramConfig.saveReactions));
        items.add(UItem.asCheck(BUTTON_SAVE_FOR_BOTS, getString(R.string.MZGramSaveForBots)).setChecked(MZGramConfig.saveForBots));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveArchiveMediaInfo)));
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
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    setter.run(editText.getText() == null ? "" : editText.getText().toString());
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
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
    }

    private void fillMessageMenu(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_SHOW_COPY_PHOTO, getString(R.string.MZGramCopyPhoto)).setChecked(MZGramConfig.showCopyPhoto));
        items.add(UItem.asShadow(getString(R.string.MZGramShowCopyPhotoInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_DELETE_DOWNLOADED_FILE, getString(R.string.MZGramDeleteDownloadedFile)).setChecked(MZGramConfig.showDeleteDownloadedFile));
        items.add(UItem.asShadow(getString(R.string.MZGramShowDeleteDownloadedFileInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_ADD_TO_SAVED_MESSAGES, getString(R.string.MZGramSaveMessage)).setChecked(MZGramConfig.showAddToSavedMessages));
        items.add(UItem.asShadow(getString(R.string.MZGramShowSaveMessageInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_SET_REMINDER, getString(R.string.SetReminder)).setChecked(MZGramConfig.showSetReminder));
        items.add(UItem.asShadow(getString(R.string.MZGramShowSetReminderInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_REPEAT, getString(R.string.MZGramRepeat)).setChecked(MZGramConfig.showRepeat));
        items.add(UItem.asShadow(getString(R.string.MZGramShowRepeatInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_OPEN_IN, getString(R.string.OpenInExternalApp)).setChecked(MZGramConfig.showOpenIn));
        items.add(UItem.asShadow(getString(R.string.MZGramShowOpenInInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_MESSAGE_DETAILS, getString(R.string.MZGramMessageDetails)).setChecked(MZGramConfig.showMessageDetails));
        items.add(UItem.asShadow(getString(R.string.MZGramShowMessageDetailsInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_QR_CODE, getString(R.string.QrCode)).setChecked(MZGramConfig.showQrCode));
        items.add(UItem.asShadow(getString(R.string.MZGramShowQrCodeInfo)));
        items.add(UItem.asCheck(BUTTON_SHOW_NO_QUOTE_FORWARD, getString(R.string.MZGramForwardNoQuote)).setChecked(MZGramConfig.showNoQuoteForward));
        items.add(UItem.asShadow(getString(R.string.MZGramShowForwardNoQuoteInfo)));
        items.add(UItem.asCheck(BUTTON_SAVE_PROTECTED_CONTENT, getString(R.string.MZGramSaveProtectedContent)).setChecked(MZGramConfig.saveProtectedContent));
        items.add(UItem.asShadow(getString(R.string.MZGramSaveProtectedContentInfo)));
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
        items.add(UItem.asCheck(BUTTON_FOLDER_TABS_AT_BOTTOM, getString(R.string.MZGramFolderTabsAtBottom)).setChecked(MZGramConfig.folderTabsAtBottom));
        items.add(UItem.asShadow(getString(R.string.MZGramFolderTabsAtBottomInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_BOTTOM_NAVIGATION_BAR, getString(R.string.MZGramHideBottomNavigationBar)).setChecked(MZGramConfig.hideBottomNavigationBar));
        items.add(UItem.asShadow(getString(R.string.MZGramHideBottomNavigationBarInfo)));
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
    }

    private void fillAdsAndFilters(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_DISABLE_SPONSORED_MESSAGES, getString(R.string.MZGramDisableSponsoredMessages)).setChecked(MZGramConfig.disableSponsoredMessages));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableSponsoredMessagesInfo)));
        items.add(UItem.asCheck(BUTTON_STRIP_ZALGO_TEXT, getString(R.string.MZGramStripZalgoText)).setChecked(MZGramConfig.stripZalgoText));
        items.add(UItem.asShadow(getString(R.string.MZGramStripZalgoTextInfo)));
    }

    private void fillOther(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_TEST_TOGGLE, getString(R.string.MZGramTestToggle)).setChecked(MZGramConfig.testToggle));
        items.add(UItem.asShadow(getString(R.string.MZGramTestToggleInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= SECTION_ROW_ID && item.id < SECTION_ROW_ID + SECTIONS_COUNT) {
            presentFragment(new MZGramSettingsActivity(item.id - SECTION_ROW_ID));
        } else if (item.id == BUTTON_TEST_TOGGLE) {
            MZGramConfig.toggleTestToggle();
            ((TextCheckCell) view).setChecked(MZGramConfig.testToggle);
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
        } else if (item.id == BUTTON_HIDE_CAMERA_TILE) {
            MZGramConfig.toggleHideCameraTile();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideCameraTile);
        } else if (item.id == BUTTON_PREFER_ORIGINAL_QUALITY) {
            MZGramConfig.togglePreferOriginalQuality();
            ((TextCheckCell) view).setChecked(MZGramConfig.preferOriginalQuality);
        } else if (item.id == BUTTON_AUTO_PAUSE_VIDEO) {
            MZGramConfig.toggleAutoPauseVideo();
            ((TextCheckCell) view).setChecked(MZGramConfig.autoPauseVideo);
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
        } else if (item.id == BUTTON_SEMI_TRANSPARENT_DELETED) {
            MZGramConfig.toggleSemiTransparentDeleted();
            ((TextCheckCell) view).setChecked(MZGramConfig.semiTransparentDeleted);
        } else if (item.id == BUTTON_ERASE_LOCAL_DATABASE) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramEraseLocalDatabase))
                    .setMessage(getString(R.string.MZGramEraseLocalDatabaseConfirm))
                    .setPositiveButton(getString(R.string.CacheClear), (dialog, which) ->
                            org.telegram.messenger.mzgram.MZGramLocalDatabase.erase(getCurrentAccount()))
                    .setNegativeButton(getString(R.string.Cancel), null)
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
                    .setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                        org.telegram.messenger.Utilities.globalQueue.postRunnable(() ->
                                org.telegram.messenger.mzgram.MZGramHistoryController.getInstance().wipeArchive());
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
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
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                try {
                    java.io.File file = org.telegram.messenger.mzgram.MZGramHistoryController.getInstance().exportArchive();
                    android.app.Activity activity = getParentActivity();
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                        if (activity != null) {
                            org.telegram.messenger.AndroidUtilities.openForView(file, file.getName(), "application/octet-stream", activity, getResourceProvider(), false);
                        }
                    });
                } catch (java.io.IOException e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.exportArchive", e);
                }
            });
        } else if (item.id == BUTTON_IMPORT_ARCHIVE) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.MZGramImportArchive))
                    .setMessage(getString(R.string.MZGramImportArchiveConfirm))
                    .setPositiveButton(getString(R.string.Continue), (dialog, which) -> {
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        try {
                            startActivityForResult(intent, REQUEST_CODE_IMPORT_ARCHIVE);
                        } catch (Exception e) {
                            org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importArchive", e);
                        }
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQUEST_CODE_IMPORT_ARCHIVE && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                try (java.io.InputStream input = org.telegram.messenger.ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                    if (input != null) {
                        org.telegram.messenger.mzgram.MZGramHistoryController.getInstance().importArchive(input);
                    }
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e("MZGramSettingsActivity.importArchive", e);
                }
            });
        }
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
