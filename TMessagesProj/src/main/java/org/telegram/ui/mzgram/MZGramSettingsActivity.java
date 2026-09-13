/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram: every MZGram feature has its switch here.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
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

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGram);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.MZGramGeneral)));
        items.add(UItem.asCheck(BUTTON_TEST_TOGGLE, getString(R.string.MZGramTestToggle)).setChecked(MZGramConfig.testToggle));
        items.add(UItem.asShadow(getString(R.string.MZGramTestToggleInfo)));
        items.add(UItem.asCheck(BUTTON_ASK_BEFORE_CALL, getString(R.string.MZGramAskBeforeCall)).setChecked(MZGramConfig.askBeforeCall));
        items.add(UItem.asShadow(getString(R.string.MZGramAskBeforeCallInfo)));
        items.add(UItem.asCheck(BUTTON_OPEN_ARCHIVE_ON_PULL, getString(R.string.MZGramOpenArchiveOnPull)).setChecked(MZGramConfig.openArchiveOnPull));
        items.add(UItem.asShadow(getString(R.string.MZGramOpenArchiveOnPullInfo)));
        items.add(UItem.asCheck(BUTTON_DISABLE_INSTANT_CAMERA, getString(R.string.MZGramDisableInstantCamera)).setChecked(MZGramConfig.disableInstantCamera));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableInstantCameraInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramAppearance)));
        items.add(UItem.asCheck(BUTTON_DISABLE_NUMBER_ROUNDING, getString(R.string.MZGramDisableNumberRounding)).setChecked(MZGramConfig.disableNumberRounding));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableNumberRoundingInfo)));
        items.add(UItem.asCheck(BUTTON_FORMAT_TIME_WITH_SECONDS, getString(R.string.MZGramFormatTimeWithSeconds)).setChecked(MZGramConfig.formatTimeWithSeconds));
        items.add(UItem.asShadow(getString(R.string.MZGramFormatTimeWithSecondsInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_STORIES, getString(R.string.MZGramHideStories)).setChecked(MZGramConfig.hideStories));
        items.add(UItem.asShadow(getString(R.string.MZGramHideStoriesInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramChat)));
        items.add(UItem.asCheck(BUTTON_DISABLE_GREETING_STICKER, getString(R.string.MZGramDisableGreetingSticker)).setChecked(MZGramConfig.disableGreetingSticker));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableGreetingStickerInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_CHANNEL_BOTTOM_BUTTONS, getString(R.string.MZGramHideChannelBottomButtons)).setChecked(MZGramConfig.hideChannelBottomButtons));
        items.add(UItem.asShadow(getString(R.string.MZGramHideChannelBottomButtonsInfo)));
        items.add(UItem.asCheck(BUTTON_PREFER_ORIGINAL_QUALITY, getString(R.string.MZGramPreferOriginalQuality)).setChecked(MZGramConfig.preferOriginalQuality));
        items.add(UItem.asShadow(getString(R.string.MZGramPreferOriginalQualityInfo)));
        items.add(UItem.asCheck(BUTTON_AUTO_PAUSE_VIDEO, getString(R.string.MZGramAutoPauseVideo)).setChecked(MZGramConfig.autoPauseVideo));
        items.add(UItem.asShadow(getString(R.string.MZGramAutoPauseVideoInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramMessageMenu)));
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
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_TEST_TOGGLE) {
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
        } else if (item.id == BUTTON_PREFER_ORIGINAL_QUALITY) {
            MZGramConfig.togglePreferOriginalQuality();
            ((TextCheckCell) view).setChecked(MZGramConfig.preferOriginalQuality);
        } else if (item.id == BUTTON_AUTO_PAUSE_VIDEO) {
            MZGramConfig.toggleAutoPauseVideo();
            ((TextCheckCell) view).setChecked(MZGramConfig.autoPauseVideo);
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
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
