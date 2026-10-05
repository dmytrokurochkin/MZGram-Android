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
    public static boolean showCopyPhoto = false;
    public static boolean showDeleteDownloadedFile = false;
    public static boolean showAddToSavedMessages = false;
    public static boolean showSetReminder = false;
    public static boolean showRepeat = false;
    public static boolean showOpenIn = false;
    public static boolean showMessageDetails = false;
    public static boolean showQrCode = false;
    public static boolean showNoQuoteForward = false;
    // Forward, save, copy and screenshot in chats with "Restrict saving
    // content" (MZGramProtectedContent).
    public static boolean saveProtectedContent = false;
    // On by default: upstream always has this animation, the switch only turns it off.
    public static boolean predictiveBackAnimation = true;
    // On by default: upstream always has this animation, the switch only turns it off.
    public static boolean gooeyAvatarAnimation = true;
    public static boolean hideBottomNavigationBar = false;
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
    // Chats with bots.
    public static boolean saveForBots = true;
    // How a deleted message kept in the chat looks: drawn at 75% opacity,
    // and the marks shown before the time (any text, empty for none).
    public static boolean semiTransparentDeleted = true;
    public static final String DEFAULT_DELETED_MARK = "\uD83E\uDDF9";
    public static final String DEFAULT_EDITED_MARK = "\u270F\uFE0F";
    public static String deletedMark = DEFAULT_DELETED_MARK;
    public static String editedMark = DEFAULT_EDITED_MARK;
    // Stops sponsored
    // (ad) messages in channels from ever being requested.
    public static boolean disableSponsoredMessages = false;
    // Strips Zalgo-style combining-mark text corruption from display names.
    // See MZGramZalgoFilter.
    public static boolean stripZalgoText = false;

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
            showCopyPhoto = preferences.getBoolean("showCopyPhoto", false);
            showDeleteDownloadedFile = preferences.getBoolean("showDeleteDownloadedFile", false);
            showAddToSavedMessages = preferences.getBoolean("showAddToSavedMessages", false);
            showSetReminder = preferences.getBoolean("showSetReminder", false);
            showRepeat = preferences.getBoolean("showRepeat", false);
            showOpenIn = preferences.getBoolean("showOpenIn", false);
            showMessageDetails = preferences.getBoolean("showMessageDetails", false);
            showQrCode = preferences.getBoolean("showQrCode", false);
            showNoQuoteForward = preferences.getBoolean("showNoQuoteForward", false);
            saveProtectedContent = preferences.getBoolean("saveProtectedContent", false);
            predictiveBackAnimation = preferences.getBoolean("predictiveBackAnimation", true);
            gooeyAvatarAnimation = preferences.getBoolean("gooeyAvatarAnimation", true);
            hideBottomNavigationBar = preferences.getBoolean("hideBottomNavigationBar", false);
            folderTabsAtBottom = preferences.getBoolean("folderTabsAtBottom", false);
            confirmAVMessage = preferences.getBoolean("confirmAVMessage", false);
            mediaPreviewOnLongPress = preferences.getBoolean("mediaPreviewOnLongPress", false);
            ghostMode = preferences.getBoolean("ghostMode", false);
            ghostAutoDelaySend = preferences.getBoolean("ghostAutoDelaySend", false);
            ghostSilentSend = preferences.getBoolean("ghostSilentSend", false);
            offerGhostModeBeforeStories = preferences.getBoolean("offerGhostModeBeforeStories", false);
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
            saveFormatting = preferences.getBoolean("saveFormatting", true);
            saveReactions = preferences.getBoolean("saveReactions", true);
            saveForBots = preferences.getBoolean("saveForBots", true);
            semiTransparentDeleted = preferences.getBoolean("semiTransparentDeleted", true);
            deletedMark = preferences.getString("deletedMark", DEFAULT_DELETED_MARK);
            editedMark = preferences.getString("editedMark", DEFAULT_EDITED_MARK);
            if (preferences.contains("saveMessageHistory") || preferences.contains("historyTrackedDialogs")) {
                preferences.edit().remove("saveMessageHistory").remove("historyTrackedDialogs").apply();
            }
            disableSponsoredMessages = preferences.getBoolean("disableSponsoredMessages", false);
            stripZalgoText = preferences.getBoolean("stripZalgoText", false);
            // The archive keeps media of any size, with no total quota, and
            // never deletes saved files on its own; the old limit keys go.
            if (preferences.contains("historyMediaSizeLimitMb") || preferences.contains("historyTotalMediaCapMb")) {
                preferences.edit().remove("historyMediaSizeLimitMb").remove("historyTotalMediaCapMb").apply();
            }
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

    public static void toggleHideBottomNavigationBar() {
        hideBottomNavigationBar = !hideBottomNavigationBar;
        putBoolean("hideBottomNavigationBar", hideBottomNavigationBar);
    }

    public static void toggleFolderTabsAtBottom() {
        folderTabsAtBottom = !folderTabsAtBottom;
        putBoolean("folderTabsAtBottom", folderTabsAtBottom);
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

    public static void toggleStripZalgoText() {
        stripZalgoText = !stripZalgoText;
        putBoolean("stripZalgoText", stripZalgoText);
    }
}
