package org.mzgram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.mzgram.MZGramConfig;
import org.mzgram.appearance.AppearanceConfig;
import org.mzgram.chats.ChatsConfig;
import org.mzgram.chats.DoubleTapCell;
import org.mzgram.chats.StickerShapeCell;
import org.mzgram.chats.TextStyleDialog;
import org.mzgram.chats.WideChannelPostsPreviewCell;
import org.mzgram.icons.BaseIconPacks;
import org.mzgram.links.LinkCleaner;
import kotlin.Unit;
import org.mzgram.core.CoreConfig;
import org.mzgram.core.config.ConfigItem;
import org.mzgram.core.helpers.AppRestartHelper;
import org.mzgram.core.helpers.TranscribeHelper;
import org.mzgram.core.settings.BaseCoreSettingsActivity;
import org.mzgram.core.ui.PopupBuilder;
import org.mzgram.core.ui.cells.HeaderCell;
import org.mzgram.core.ui.cells.StickerSizePreviewMessagesCell;
import org.mzgram.ext.ExtConfig;
import org.mzgram.ext.helper.DoubleTap;

/**
 * Экран «Chats» раздела mzGram — визуальный порт экрана MZGram
 * (org.mzgram.sdk.preferences.ChatsPreferencesActivity, читаемый исходник 10.10.1).
 *
 * Настройки, которые уже есть в MZGram, переиспользуются из {@link CoreConfig} / {@link ExtConfig};
 * то, чего нет — новые ConfigItem в {@link ChatsConfig} (помечены «только UI»).
 * Группы-мультивыбор (Replies, Hide Reactions, Quick Swipe Transition, Message Menu,
 * Extended Settings, Auto-Pause) сделаны разворачивающимися: строка «x/y» + чекбоксы.
 */
public class MZGramChatsActivity extends BaseCoreSettingsActivity {

    public static final int SCREEN_CHATS = 0;
    public static final int SCREEN_MENUS = 1;
    public static final int SCREEN_MEDIA = 2;
    public static final int SCREEN_HIDING = 3;

    private final int screen;

    private static final int TYPE_STICKER_SIZE = 100;
    private static final int TYPE_STICKER_SHAPE = 101;
    private static final int TYPE_DOUBLE_TAP = 102;
    private static final int TYPE_SET_REACTION = 103;
    /** Сворачиваемая группа со счётчиком и шевроном. */
    private static final int TYPE_EXPANDABLE_SWITCH = 104;
    /** Круглая галочка внутри группы. */
    private static final int TYPE_ROUND_CHECK = 105;
    private static final int TYPE_WIDE_CHANNEL_PREVIEW = 106;

    /** Размер стикеров по умолчанию: к нему возвращает кнопка сброса в шапке. */
    private static final float STICKER_SIZE_DEFAULT = 14.0f;

    private StickerSizeCell stickerSizeCell;
    private StickerShapeCell stickerShapeCell;
    private DoubleTapCell doubleTapCell;
    private WideChannelPostsPreviewCell wideChannelPostsPreviewCell;
    private ActionBarMenuItem resetItem;

    private boolean repliesExpanded;
    private boolean hideReactionsExpanded;
    private boolean unlimitedExpanded;
    private boolean quickTransitionExpanded;
    private boolean chatMenuExpanded;
    private boolean messageMenuExpanded;
    private boolean mediaViewerMenuExpanded;
    private boolean actionBarButtonsExpanded;
    private boolean extendedSettingsExpanded;
    private boolean pauseExpanded;
    private boolean premiumElementsExpanded;
    private boolean deleteMenuExpanded;
    private boolean askWhenExpanded;
    private boolean stripTrackingExpanded;
    private boolean hideAiExpanded;
    private boolean hideSettingsExpanded;

    // Sticker Size
    private int stickerSizeRow = -1;
    private int stickerTimeRow = -1;
    private int repliesGroupRow = -1;
    private int replyColorsRow = -1;
    private int replyEmojiRow = -1;
    private int replyBackgroundRow = -1;
    private int stickerSizeDividerRow = -1;

    // Sticker Shape
    private int stickerShapeHeaderRow = -1;
    private int stickerShapeRow = -1;
    private int stickerShapeDividerRow = -1;

    // Links
    private int linksHeaderRow = -1;
    private int menusRow = -1;
    private int mediaRow = -1;
    private int aiChatRow = -1;
    private int chatSettingsRow = -1;
    private int linksDividerRow = -1;

    // Stickers and Emoji
    private int stickersHeaderRow = -1;
    private int disableTrendingRow = -1;
    private int hideGroupStickerRow = -1;
    private int lockedEmojiAsStickerRow = -1;
    private int unlimitedGroupRow = -1;
    private int unlimitedStickersRow = -1;
    private int unlimitedGifsRow = -1;
    private int hideReactionsGroupRow = -1;
    private int hideReactionsChannelsRow = -1;
    private int hideReactionsGroupsRow = -1;
    private int hideReactionsPrivateRow = -1;
    private int stickersDividerRow = -1;

    // Double Tap
    private int doubleTapHeaderRow = -1;
    private int doubleTapRow = -1;
    private int doubleTapIncomingRow = -1;
    private int doubleTapOutgoingRow = -1;
    private int doubleTapReactionRow = -1;
    private int doubleTapDividerRow = -1;

    // Chats
    private int chatsHeaderRow = -1;
    private int bottomButtonRow = -1;
    private int adminShortcutsRow = -1;
    private int quickTransitionGroupRow = -1;
    private int quickTransitionChannelsRow = -1;
    private int quickTransitionTopicsRow = -1;
    private int disableGreetingRow = -1;
    private int deleteChatForBothSidesRow = -1;
    private int hideKeyboardOnScrollRow = -1;
    private int disableGlobalSearchRow = -1;
    private int searchHashtagChatRow = -1;
    private int searchHashtagChannelRow = -1;
    private int addCommaRow = -1;
    private int inlineMathRow = -1;
    private int inlineMathSettingsRow = -1;
    private int hideSendAsPeerRow = -1;
    private int tapToSwitchRecordRow = -1;
    private int keepAttachButtonRow = -1;
    private int chatsDividerRow = -1;

    private int inputHeaderRow = -1;
    private int inputDividerRow = -1;
    private int searchHeaderRow = -1;
    private int searchDividerRow = -1;

    // Messages
    private int messagesHeaderRow = -1;
    private int removeMessageTailRow = -1;
    private int unreadCapsuleRow = -1;
    private int replaceEditedRow = -1;
    private int showOnlineStatusRow = -1;
    private int hideShareButtonRow = -1;
    private int hideGiftButtonRow = -1;
    private int hideSearchButtonRow = -1;
    private int hideCallButtonRow = -1;
    private int hideMoneyButtonRow = -1;
    private int showResultsBeforeVotingRow = -1;
    private int dateOfForwardedMsgRow = -1;
    private int showTimeHintRow = -1;
    private int rememberAllRepliesRow = -1;
    private int chatMenuGroupRow = -1;
    private int chatMenuAdminsRow = -1;
    private int chatMenuRecentActionsRow = -1;
    private int chatMenuStatisticsRow = -1;
    private int chatMenuPermissionsRow = -1;
    private int chatMenuMembersRow = -1;
    private int chatMenuBoostRow = -1;
    private int chatMenuLinkedChatRow = -1;
    private int chatMenuToBeginningRow = -1;
    private int chatMenuGoToMessageRow = -1;
    private int chatMenuHideTitleRow = -1;
    private int chatMenuViewDeletedRow = -1;
    private int chatMenuClearDeletedRow = -1;
    private int chatMenuDeleteOwnRow = -1;
    private int messageMenuGroupRow = -1;
    private int menuCopyPhotoRow = -1;
    private int menuSaveRow = -1;
    private int menuRepeatRow = -1;
    private int menuClearRow = -1;
    private int menuHistoryRow = -1;
    private int menuReportRow = -1;
    private int menuDetailsRow = -1;
    private int menuReactionsRow = -1;
    private int menuReplyInPrivateRow = -1;
    private int menuCopyLinkRow = -1;
    private int menuCopyFrameRow = -1;
    private int menuCopyAsStickerRow = -1;
    private int menuAddToStickersRow = -1;
    private int menuAddToFavoritesRow = -1;
    private int menuNoQuoteForwardRow = -1;
    private int menuSetReminderRow = -1;
    private int menuBookmarkRow = -1;
    private int menuRepeatAsCopyRow = -1;
    private int menuTranslateRow = -1;
    private int menuTranslateLlmRow = -1;
    private int menuShareRow = -1;
    private int menuHideRow = -1;
    private int menuAdminActionsRow = -1;
    private int menuPermissionsRow = -1;
    private int mediaViewerMenuGroupRow = -1;
    private int mediaMenuForwardRow = -1;
    private int mediaMenuNoQuoteForwardRow = -1;
    private int mediaMenuCopyFrameRow = -1;
    private int mediaMenuCopyPhotoRow = -1;
    private int mediaMenuProfilePhotoRow = -1;
    private int mediaMenuQrRow = -1;
    private int actionBarButtonsGroupRow = -1;
    private int actionBarReplyRow = -1;
    private int actionBarEditRow = -1;
    private int actionBarSelectBetweenRow = -1;
    private int actionBarCopyRow = -1;
    private int actionBarForwardRow = -1;
    private int premiumElementsGroupRow = -1;
    private int premiumEmojiStatusRow = -1;
    private int premiumEmojiInRepliesRow = -1;
    private int premiumColorsInRepliesRow = -1;
    private int premiumWallpapersRow = -1;
    private int premiumVideoAvatarsRow = -1;
    private int premiumStarReactionsRow = -1;
    private int premiumStickerEffectsRow = -1;
    private int premiumBoostsRow = -1;
    private int deleteMenuGroupRow = -1;
    private int deleteMenuBanUsersRow = -1;
    private int deleteMenuReportSpamRow = -1;
    private int deleteMenuDeleteAllRow = -1;
    private int deleteMenuCommonGroupsRow = -1;
    private int groupedMessageMenuRow = -1;
    private int textStyleRow = -1;
    private int messagesDividerRow = -1;

    private int buttonsHeaderRow = -1;
    private int buttonsDividerRow = -1;
    private int menusHeaderRow = -1;
    private int menusDividerRow = -1;

    private int linkConfirmationsHeaderRow = -1;
    private int fixLinkPreviewRow = -1;
    private int disableLinkPreviewRow = -1;
    private int openLinkConfirmationRow = -1;
    private int askWhenGroupRow = -1;
    private int confirmAVMessageRow = -1;
    private int askBeforeCallRow = -1;
    private int repeatConfirmRow = -1;
    private int disableClickCommandToSendRow = -1;
    private int stripTrackingGroupRow = -1;
    private int stripTrackingOpenRow = -1;
    private int stripTrackingPasteRow = -1;
    private int trackingFilterRow = -1;
    private int linkConfirmationsDividerRow = -1;

    private int channelPostsHeaderRow = -1;
    private int wideChannelPostsPreviewRow = -1;
    private int wideChannelPostsRow = -1;
    private int wideFeedPostsRow = -1;
    private int channelPostsDividerRow = -1;

    // Camera
    private int cameraHeaderRow = -1;
    private int cameraTypeRow = -1;
    private int extendedSettingsGroupRow = -1;
    private int seamlessSwitchingRow = -1;
    private int extendedFpsRow = -1;
    private int cameraStabilizationRow = -1;
    private int cameraMirrorModeRow = -1;
    private int startWithWideAngleRow = -1;
    private int videoMessagesCameraRow = -1;
    private int rememberLastUsedCameraRow = -1;
    private int zoomSliderRow = -1;
    private int staticZoomRow = -1;
    private int cameraDividerRow = -1;

    // Photos
    private int photoHeaderRow = -1;
    private int alwaysSendHdRow = -1;
    private int hdrPhotosRow = -1;
    private int disableInstantCameraRow = -1;
    private int hideCameraTileRow = -1;
    private int photoDividerRow = -1;

    // Videos
    private int videosHeaderRow = -1;
    private int doubleTapSeekDurationRow = -1;
    private int preferOriginalQualityRow = -1;
    private int enhancedVideoBitrateRow = -1;
    private int videoPlayerDecoderRow = -1;
    private int swipeToPipRow = -1;
    private int unmuteWithVolumeButtonsRow = -1;
    private int dontAutoplayWidgetsRow = -1;
    private int showSmallGifRow = -1;
    private int dontAutoPlayNextVoiceRow = -1;
    private int disableProximityEventsRow = -1;
    private int pauseGroupRow = -1;
    private int pauseVideoRow = -1;
    private int pauseVoiceRow = -1;
    private int pauseRoundRow = -1;
    private int videosDividerRow = -1;
    private int playbackHeaderRow = -1;
    private int playbackDividerRow = -1;
    private int voiceHeaderRow = -1;
    private int voiceEnhancementsRow = -1;
    private int voiceDividerRow = -1;

    private int transcribeHeaderRow = -1;
    private int transcribeProviderRow = -1;
    private int cloudflareCredentialsRow = -1;
    private int geminiApiKeyRow = -1;
    private int openAiCredentialsRow = -1;
    private int voskModelsRow = -1;
    private int transcribeDividerRow = -1;

    private int hidingChatListHeaderRow = -1;
    private int hideActionBarStatusRow = -1;
    private int hideStoriesRow = -1;
    private int hideFloatingButtonRow = -1;
    private int hideSearchBarRow = -1;
    private int hideAllChatsRow = -1;
    private int hidingChatListDividerRow = -1;
    private int hidingChatsHeaderRow = -1;
    private int hidingChatsDividerRow = -1;
    private int hidingStickersHeaderRow = -1;
    private int hidingStickersDividerRow = -1;
    private int hidingAiHeaderRow = -1;
    private int hideAiGroupRow = -1;
    private int hideAiEditorRow = -1;
    private int hideAiSummaryRow = -1;
    private int hideAiIvRow = -1;
    private int hidingAiDividerRow = -1;
    private int hidingSettingsHeaderRow = -1;
    private int hideSettingsGroupRow = -1;
    private final int[] hideSettingsRows = new int[HiddenSettingsRows.IDS.length];
    private int hidingSettingsDividerRow = -1;

    public MZGramChatsActivity() {
        this(SCREEN_CHATS);
    }

    public MZGramChatsActivity(int screen) {
        super();
        this.screen = screen;
        ChatsConfig.ensureLoaded();
        if (screen == SCREEN_HIDING) {
            AppearanceConfig.ensureLoaded();
        }
    }

    @Override
    protected List<CollapsibleGroup> collapsibleGroups() {
        if (screen != SCREEN_HIDING) {
            return super.collapsibleGroups();
        }
        return Arrays.asList(
                new CollapsibleGroup(() -> hideAiExpanded, expanded -> hideAiExpanded = expanded),
                new CollapsibleGroup(() -> hideSettingsExpanded, expanded -> hideSettingsExpanded = expanded));
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        switch (screen) {
            case SCREEN_MENUS:
                updateMenuRows();
                break;
            case SCREEN_MEDIA:
                updateMediaRows();
                break;
            case SCREEN_HIDING:
                updateHidingRows();
                break;
            default:
                updateChatRows();
                break;
        }
    }

    private void updateChatRows() {
        stickerSizeRow = addRow("stickerSize");
        stickerTimeRow = addRow("stickerTime", "hideTimeOnStickers");
        repliesGroupRow = addRow("replies");
        if (repliesExpanded) {
            replyColorsRow = addRow();
            replyEmojiRow = addRow();
            replyBackgroundRow = addRow();
        } else {
            replyColorsRow = replyEmojiRow = replyBackgroundRow = -1;
        }
        stickerSizeDividerRow = addRow();

        stickerShapeHeaderRow = addRow("stickerShapeHeader");
        stickerShapeRow = addRow("stickerShape");
        stickerShapeDividerRow = addRow();

        stickersHeaderRow = addRow("stickersHeader");
        lockedEmojiAsStickerRow = addRow(ExtConfig.INSTANCE.getSendLockedCustomEmojiAsSticker().getKey());
        unlimitedGroupRow = addRow("unlimited", "unlimitedRecentStickers");
        if (unlimitedExpanded) {
            unlimitedStickersRow = addRow();
            unlimitedGifsRow = addRow();
        } else {
            unlimitedStickersRow = unlimitedGifsRow = -1;
        }
        stickersDividerRow = addRow();

        linksHeaderRow = addRow("linksHeader");
        menusRow = addRow("menus");
        mediaRow = addRow("media");
        aiChatRow = addRow("aiChat");
        chatSettingsRow = addRow("chatSettings");
        linksDividerRow = addRow();

        doubleTapHeaderRow = addRow("doubleTapHeader");
        doubleTapRow = addRow("doubleTapPreview");
        doubleTapIncomingRow = addRow("doubleTapIncoming");
        doubleTapOutgoingRow = addRow("doubleTapOutgoing");
        if (ExtConfig.INSTANCE.getDoubleTapAction().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS
                || ExtConfig.INSTANCE.getDoubleTapActionOut().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS) {
            doubleTapReactionRow = addRow("doubleTapReaction");
        } else {
            doubleTapReactionRow = -1;
        }
        doubleTapDividerRow = addRow();

        chatsHeaderRow = addRow("chatsHeader");
        quickTransitionGroupRow = addRow("quickTransition");
        if (quickTransitionExpanded) {
            quickTransitionChannelsRow = addRow();
            quickTransitionTopicsRow = addRow();
        } else {
            quickTransitionChannelsRow = quickTransitionTopicsRow = -1;
        }
        deleteChatForBothSidesRow = addRow("deleteChatForBothSides", "DeleteChatForBothSides");
        hideKeyboardOnScrollRow = addRow("hideKeyboardOnScroll");
        chatsDividerRow = addRow();

        inputHeaderRow = addRow("inputHeader");
        addCommaRow = addRow("addCommaAfterMention");
        inlineMathRow = addRow("inlineMathResult");
        inlineMathSettingsRow = addRow("inlineMathSettings");
        tapToSwitchRecordRow = addRow("tapToSwitchRecord", "UseChatAttachEnterMenu");
        keepAttachButtonRow = addRow("keepAttachButton");
        inputDividerRow = addRow();

        searchHeaderRow = addRow("searchHeader");
        disableGlobalSearchRow = addRow("disableGlobalSearch");
        searchHashtagChatRow = addRow("searchHashtagChat", "SearchHashtagDefaultPageChat");
        searchHashtagChannelRow = addRow("searchHashtagChannel", "SearchHashtagDefaultPageChannel");
        searchDividerRow = addRow();

        messagesHeaderRow = addRow("messagesHeader");
        removeMessageTailRow = addRow("removeMessageTail");
        unreadCapsuleRow = addRow("unreadCapsule");
        replaceEditedRow = addRow("replaceEdited");
        showOnlineStatusRow = addRow("showOnlineStatus");
        showResultsBeforeVotingRow = addRow("showResultsBeforeVoting");
        dateOfForwardedMsgRow = addRow("dateOfForwardedMsg", "DateOfForwardedMsg");
        showTimeHintRow = addRow("showTimeHint", "ShowTimeHint");
        rememberAllRepliesRow = addRow("rememberAllBackMessages", "rememberAllBackMessages");
        messagesDividerRow = addRow();

        linkConfirmationsHeaderRow = addRow("linkConfirmationsHeader");
        fixLinkPreviewRow = addRow("fixLinkPreview", "FixLinkPreview");
        disableLinkPreviewRow = addRow("disableLinkPreviewByDefault", "DisableLinkPreviewByDefault");
        openLinkConfirmationRow = addRow("openLinkConfirmation", "SkipOpenLinkConfirm", "ConfirmAllLinks");
        askWhenGroupRow = addRow("askWhen", "confirmAVMessage", "ConfirmAVMessage", "askBeforeCalling",
                "AskBeforeCalling", "repeatConfirm", "DisableClickCommandToSend");
        if (askWhenExpanded) {
            confirmAVMessageRow = CoreConfig.useChatAttachMediaMenu.Bool() ? -1 : addRow();
            askBeforeCallRow = addRow();
            repeatConfirmRow = addRow();
            disableClickCommandToSendRow = addRow();
        } else {
            confirmAVMessageRow = askBeforeCallRow = repeatConfirmRow = disableClickCommandToSendRow = -1;
        }
        stripTrackingGroupRow = addRow("stripTracking");
        if (stripTrackingExpanded) {
            stripTrackingOpenRow = addRow();
            stripTrackingPasteRow = addRow();
        } else {
            stripTrackingOpenRow = stripTrackingPasteRow = -1;
        }
        trackingFilterRow = addRow("trackingFilter");
        linkConfirmationsDividerRow = addRow();

        channelPostsHeaderRow = addRow("channelPostsHeader");
        wideChannelPostsPreviewRow = addRow("wideChannelPostsPreview");
        wideChannelPostsRow = addRow("wideChannelPosts");
        wideFeedPostsRow = addRow("wideFeedPosts");
        channelPostsDividerRow = addRow();
    }

    private void updateMenuRows() {
        buttonsHeaderRow = addRow("buttonsHeader");
        bottomButtonRow = addRow("bottomButton");
        adminShortcutsRow = addRow("adminShortcuts");
        buttonsDividerRow = addRow();

        menusHeaderRow = addRow("menusHeader");
        chatMenuGroupRow = addRow("chatMenu");
        if (chatMenuExpanded) {
            chatMenuAdminsRow = addRow();
            chatMenuRecentActionsRow = addRow();
            chatMenuStatisticsRow = addRow();
            chatMenuPermissionsRow = addRow();
            chatMenuMembersRow = addRow();
            chatMenuBoostRow = addRow();
            chatMenuLinkedChatRow = addRow();
            chatMenuToBeginningRow = addRow();
            chatMenuGoToMessageRow = addRow();
            chatMenuHideTitleRow = addRow();
            chatMenuViewDeletedRow = addRow();
            chatMenuClearDeletedRow = addRow();
            chatMenuDeleteOwnRow = addRow();
        } else {
            chatMenuAdminsRow = chatMenuRecentActionsRow = chatMenuStatisticsRow = -1;
            chatMenuPermissionsRow = chatMenuMembersRow = chatMenuBoostRow = -1;
            chatMenuLinkedChatRow = chatMenuToBeginningRow = chatMenuGoToMessageRow = -1;
            chatMenuHideTitleRow = chatMenuViewDeletedRow = chatMenuClearDeletedRow = -1;
            chatMenuDeleteOwnRow = -1;
        }
        groupedMessageMenuRow = addRow("groupedMessageMenu");
        messageMenuGroupRow = addRow("messageMenu");
        if (messageMenuExpanded) {
            menuReactionsRow = addRow();
            menuReplyInPrivateRow = addRow();
            menuCopyLinkRow = addRow();
            menuCopyFrameRow = addRow();
            menuCopyPhotoRow = addRow();
            menuCopyAsStickerRow = addRow();
            menuAddToStickersRow = addRow();
            menuAddToFavoritesRow = addRow();
            menuNoQuoteForwardRow = addRow();
            menuSetReminderRow = addRow();
            menuSaveRow = addRow();
            menuBookmarkRow = addRow();
            menuRepeatRow = addRow();
            menuRepeatAsCopyRow = addRow();
            menuClearRow = addRow();
            menuHistoryRow = addRow();
            menuTranslateRow = addRow();
            menuTranslateLlmRow = addRow();
            menuShareRow = addRow();
            menuHideRow = addRow();
            menuReportRow = addRow();
            menuAdminActionsRow = addRow();
            menuPermissionsRow = addRow();
            menuDetailsRow = addRow();
        } else {
            menuReactionsRow = menuReplyInPrivateRow = menuCopyLinkRow = menuCopyFrameRow = -1;
            menuCopyPhotoRow = menuCopyAsStickerRow = menuAddToStickersRow = menuAddToFavoritesRow = -1;
            menuNoQuoteForwardRow = menuSetReminderRow = menuSaveRow = menuBookmarkRow = -1;
            menuRepeatRow = menuRepeatAsCopyRow = menuClearRow = menuHistoryRow = -1;
            menuTranslateRow = menuTranslateLlmRow = menuShareRow = menuHideRow = -1;
            menuReportRow = menuAdminActionsRow = menuPermissionsRow = menuDetailsRow = -1;
        }
        textStyleRow = addRow("textStyle", "TextStyle");
        mediaViewerMenuGroupRow = addRow("mediaViewerMenu");
        if (mediaViewerMenuExpanded) {
            mediaMenuForwardRow = addRow();
            mediaMenuNoQuoteForwardRow = addRow();
            mediaMenuCopyFrameRow = addRow();
            mediaMenuCopyPhotoRow = addRow();
            mediaMenuProfilePhotoRow = addRow();
            mediaMenuQrRow = addRow();
        } else {
            mediaMenuForwardRow = mediaMenuNoQuoteForwardRow = mediaMenuCopyFrameRow = -1;
            mediaMenuCopyPhotoRow = mediaMenuProfilePhotoRow = mediaMenuQrRow = -1;
        }
        actionBarButtonsGroupRow = addRow("actionBarButtons");
        if (actionBarButtonsExpanded) {
            actionBarReplyRow = addRow();
            actionBarEditRow = addRow();
            actionBarSelectBetweenRow = addRow();
            actionBarCopyRow = addRow();
            actionBarForwardRow = addRow();
        } else {
            actionBarReplyRow = actionBarEditRow = actionBarSelectBetweenRow = -1;
            actionBarCopyRow = actionBarForwardRow = -1;
        }
        deleteMenuGroupRow = addRow("defaultDeleteMenu", "DefaultDeleteMenu");
        if (deleteMenuExpanded) {
            deleteMenuBanUsersRow = addRow();
            deleteMenuReportSpamRow = addRow();
            deleteMenuDeleteAllRow = addRow();
            deleteMenuCommonGroupsRow = addRow();
        } else {
            deleteMenuBanUsersRow = deleteMenuReportSpamRow = deleteMenuDeleteAllRow = deleteMenuCommonGroupsRow = -1;
        }
        menusDividerRow = addRow();
    }

    private void updateMediaRows() {
        cameraHeaderRow = addRow("cameraHeader");
        cameraTypeRow = addRow("cameraType");
        seamlessSwitchingRow = extendedFpsRow = cameraStabilizationRow = -1;
        cameraMirrorModeRow = startWithWideAngleRow = -1;
        // При системной камере вся группа скрыта: расширенные настройки
        // относятся только к Camera2 и CameraX.
        if (cameraTypeIndex() != CAMERA_TYPE_SYSTEM) {
            extendedSettingsGroupRow = addRow("extendedSettings");
            if (extendedSettingsExpanded) {
                // Бесшовное переключение есть не на всяком железе.
                if (cameraTypeIndex() != CAMERA_TYPE_TELEGRAM && isSeamlessSwitchingAvailable()) {
                    seamlessSwitchingRow = addRow();
                }
                extendedFpsRow = addRow();
                cameraStabilizationRow = addRow();
                // Зеркало и широкий угол умеет только CameraX.
                if (cameraTypeIndex() == CAMERA_TYPE_CAMERA_X) {
                    cameraMirrorModeRow = addRow();
                    startWithWideAngleRow = addRow();
                }
            }
            if (!isSeamlessSwitchingAvailable() && isSeamlessSwitchingEnabled()) {
                // На устройстве без второй камеры флаг гасится принудительно.
                setSeamlessSwitching(false);
            }
        } else {
            extendedSettingsGroupRow = -1;
        }
        videoMessagesCameraRow = addRow("videoMessagesCamera");
        // Запоминать нечего, пока камера спрашивается каждый раз.
        if (ExtConfig.INSTANCE.getCameraInVideoMessages().Int() != VIDEO_CAMERA_ASK) {
            rememberLastUsedCameraRow = addRow("rememberLastUsedCamera");
        } else {
            rememberLastUsedCameraRow = -1;
        }
        zoomSliderRow = addRow("zoomSlider");
        staticZoomRow = addRow("staticZoom");
        cameraDividerRow = addRow();

        photoHeaderRow = addRow("photoHeader");
        alwaysSendHdRow = addRow("alwaysSendInHD");
        hdrPhotosRow = addRow("hdrPhotos");
        disableInstantCameraRow = addRow("disableInstantCamera", "DisableInstantCamera");
        photoDividerRow = addRow();

        videosHeaderRow = addRow("videosHeader");
        preferOriginalQualityRow = addRow("preferOriginalQuality");
        enhancedVideoBitrateRow = addRow("enhancedVideoBitrate", "EnhancedVideoBitrate");
        videoPlayerDecoderRow = addRow("videoPlayerDecoder", "VideoPlayerDecoder");
        showSmallGifRow = addRow("showSmallGIF", "ShowSmallGIF");
        videosDividerRow = addRow();

        playbackHeaderRow = addRow("playbackHeader");
        doubleTapSeekDurationRow = addRow("doubleTapSeekDuration");
        swipeToPipRow = addRow("swipeToPip");
        unmuteWithVolumeButtonsRow = addRow("unmuteWithVolumeButtons");
        dontAutoplayWidgetsRow = addRow("dontAutoplayWidgets");
        pauseGroupRow = addRow("pauseOnMinimize");
        if (pauseExpanded) {
            pauseVideoRow = addRow();
            pauseVoiceRow = addRow();
            pauseRoundRow = addRow();
        } else {
            pauseVideoRow = pauseVoiceRow = pauseRoundRow = -1;
        }
        playbackDividerRow = addRow();

        voiceHeaderRow = addRow("voiceHeader");
        voiceEnhancementsRow = addRow("noiseSuppressAndVoiceEnhance", "NoiseSuppressAndVoiceEnhance");
        dontAutoPlayNextVoiceRow = addRow("dontAutoPlayNextVoice", "DontAutoPlayNextVoice");
        disableProximityEventsRow = addRow("disableProximityEvents", "DisableProximityEvents");
        voiceDividerRow = addRow();

        transcribeHeaderRow = addRow("transcribeHeader");
        transcribeProviderRow = addRow("transcribeProvider", "TranscribeProviderShort");
        cloudflareCredentialsRow = addRow("cloudflareCredentials", "CloudflareCredentials");
        geminiApiKeyRow = addRow("llmProviderGeminiKey", "LlmProviderGeminiKey");
        openAiCredentialsRow = ExtConfig.INSTANCE.getTranscribeProvider().Int() == TranscribeHelper.TRANSCRIBE_OPENAI
                ? addRow("transcribeProviderOpenAI", "TranscribeProviderOpenAI") : -1;
        voskModelsRow = ExtConfig.INSTANCE.getTranscribeProvider().Int() == TranscribeHelper.TRANSCRIBE_VOSK
                ? addRow("voskModels", "VoskModelsShort") : -1;
        transcribeDividerRow = addRow();
    }

    private void updateHidingRows() {
        hidingChatListHeaderRow = addRow("hidingChatListHeader");
        hideActionBarStatusRow = getUserConfig().isPremium() ? addRow("hideActionBarStatus") : -1;
        hideStoriesRow = addRow("hideStories", "HideStoriesFromHeader", "DisableStories");
        hideFloatingButtonRow = addRow("hideFloatingButton");
        hideSearchBarRow = addRow("hideSearchBar");
        hideAllChatsRow = addRow("hideAllChats");
        hidingChatListDividerRow = addRow();

        hidingChatsHeaderRow = addRow("hidingChatsHeader");
        hideSendAsPeerRow = addRow("hideSendAsPeer");
        hideShareButtonRow = addRow("hideShareButton");
        hideGiftButtonRow = addRow("hideGiftButton");
        hideSearchButtonRow = addRow("hideSearchButton");
        hideCallButtonRow = addRow("hideCallButton");
        hideMoneyButtonRow = addRow("hideMoneyButton");
        hideReactionsGroupRow = addRow("hideReactions");
        if (hideReactionsExpanded) {
            hideReactionsChannelsRow = addRow();
            hideReactionsGroupsRow = addRow();
            hideReactionsPrivateRow = addRow();
        } else {
            hideReactionsChannelsRow = hideReactionsGroupsRow = hideReactionsPrivateRow = -1;
        }
        hideCameraTileRow = addRow("hideCameraTile");
        hidingChatsDividerRow = addRow();

        hidingStickersHeaderRow = addRow("hidingStickersHeader");
        disableTrendingRow = addRow("disableTrending", "DisableTrending");
        hideGroupStickerRow = addRow("hideGroupSticker");
        disableGreetingRow = addRow("disableGreeting");
        hidingStickersDividerRow = addRow();

        hidingAiHeaderRow = addRow("hidingAiHeader");
        hideAiGroupRow = addRow("hideAi");
        if (hideAiExpanded) {
            hideAiEditorRow = addRow("hideAiEditor");
            hideAiSummaryRow = addRow("hideAiSummary");
            hideAiIvRow = addRow("hideAiIv");
        } else {
            hideAiEditorRow = hideAiSummaryRow = hideAiIvRow = -1;
        }
        premiumElementsGroupRow = addRow("premiumElements", "PremiumElements");
        if (premiumElementsExpanded) {
            premiumEmojiStatusRow = addRow();
            premiumEmojiInRepliesRow = addRow();
            premiumColorsInRepliesRow = addRow();
            premiumWallpapersRow = addRow();
            premiumVideoAvatarsRow = addRow();
            premiumStarReactionsRow = addRow();
            premiumStickerEffectsRow = addRow();
            premiumBoostsRow = addRow();
        } else {
            premiumEmojiStatusRow = premiumEmojiInRepliesRow = premiumColorsInRepliesRow = premiumWallpapersRow = -1;
            premiumVideoAvatarsRow = premiumStarReactionsRow = premiumStickerEffectsRow = premiumBoostsRow = -1;
        }
        hidingAiDividerRow = addRow();

        hidingSettingsHeaderRow = addRow("hidingSettingsHeader");
        hideSettingsGroupRow = addRow("hideSettingsSections", "HideRowWallet", "HideRowPremium", "HideRowStars", "HideRowEarnings", "HideRowBusiness", "HideRowGift", "HideRowAsk", "HideRowFaq", "HideRowFeatures", "HideRowPolicy");
        if (hideSettingsExpanded) {
            final ConfigItem[] hideItems = HiddenSettingsRows.items();
            for (int i = 0; i < hideSettingsRows.length; i++) {
                hideSettingsRows[i] = addRow(hideItems[i].getKey());
            }
        } else {
            Arrays.fill(hideSettingsRows, -1);
        }
        hidingSettingsDividerRow = addRow();
    }

    // ---- MZGram: the main chats screen opens as a list of its sections ----

    @Override
    protected boolean mzUsesSections() {
        return screen == SCREEN_CHATS;
    }

    @Override
    protected BaseCoreSettingsActivity mzCreateSectionPage() {
        return new MZGramChatsActivity(SCREEN_CHATS);
    }

    /** Sticker size, the sticker shape and the sticker options are one page: "Stickers". */
    @Override
    protected String mzSectionGroup(String header) {
        if (MZ_TOP.equals(header) || "stickerShapeHeader".equals(header)) {
            return "stickersHeader";
        }
        return header;
    }

    /** The links already open screens of their own: they stay on the list. */
    @Override
    protected boolean mzSectionInline(String section) {
        return "linksHeader".equals(section);
    }

    /** The header texts; "Chats" inside Chats is the in-chat behaviour section, named so on its row. */
    @Override
    protected int mzSectionTitleRes(String section) {
        switch (section) {
            case "stickersHeader":
                return R.string.MZChatsStickersAndEmoji;
            case "doubleTapHeader":
                return R.string.MZChatsDoubleTap;
            case "chatsHeader":
                return R.string.MZAppearanceHidingInChats;
            case "inputHeader":
                return R.string.MZChatsInputHeader;
            case "searchHeader":
                return R.string.Search;
            case "messagesHeader":
                return R.string.MZChatsMessages;
            case "linkConfirmationsHeader":
                return R.string.MZChatsLinksAndConfirmations;
            case "channelPostsHeader":
                return R.string.MZChatsChannelPosts;
            case "buttonsHeader":
                return R.string.MZChatsButtonsHeader;
            default:
                return 0;
        }
    }

    @Override
    protected int mzSectionIcon(String section) {
        switch (section) {
            case "stickersHeader":
                return R.drawable.msg_sticker;
            case "doubleTapHeader":
                return R.drawable.msg_reactions;
            case "chatsHeader":
                return R.drawable.msg_viewchats;
            case "inputHeader":
                return R.drawable.msg_text_outlined;
            case "searchHeader":
                return R.drawable.msg_search;
            case "messagesHeader":
                return R.drawable.msg_msgbubble3;
            case "linkConfirmationsHeader":
                return R.drawable.msg_link;
            case "channelPostsHeader":
                return R.drawable.msg_channel;
            case "buttonsHeader":
                return R.drawable.msg_mini_customize;
            default:
                return R.drawable.msg_settings_old;
        }
    }

    @Override
    protected String getActionBarTitle() {
        switch (screen) {
            case SCREEN_MENUS:
                return getString(R.string.MZChatsMenus);
            case SCREEN_MEDIA:
                return getString(R.string.MZChatsMedia);
            case SCREEN_HIDING:
                return getString(R.string.MZAppearanceHiding);
            default:
                return getString(R.string.MZGramChats);
        }
    }

    @Override
    public int getSearchGuid() {
        switch (screen) {
            case SCREEN_MENUS:
                return 29100;
            case SCREEN_MEDIA:
                return 29200;
            case SCREEN_HIDING:
                return 29300;
            default:
                return 22000;
        }
    }

    @Override
    public int getSearchIcon() {
        switch (screen) {
            case SCREEN_MENUS:
                return R.drawable.msg_list;
            case SCREEN_MEDIA:
                return R.drawable.msg_camera;
            case SCREEN_HIDING:
                return R.drawable.msg_archive_hide;
            default:
                return R.drawable.msg_discussion;
        }
    }

    @Override
    public String getSearchPrefix() {
        return "MZChats";
    }

    @Override
    protected String getKey() {
        switch (screen) {
            case SCREEN_MENUS:
                return "mzgram_menus";
            case SCREEN_MEDIA:
                return "mzgram_media";
            case SCREEN_HIDING:
                return "mzgram_hiding";
            default:
                return "mzgram_chats";
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private void reloadList() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    /** Пересобрать открытые чаты: часть настроек видна прямо в них. */
    private void rebuildChats() {
        if (parentLayout != null) {
            parentLayout.rebuildFragments(0);
        }
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        // Кнопка сброса размера стикеров в шапке: появляется, как только
        // размер отличается от стандартного.
        if (actionBar != null && screen == SCREEN_CHATS) {
            resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset);
            resetItem.setContentDescription(getString(R.string.Reset));
            // Только через updateViewVisibilityAnimated: она проставляет и видимость,
            // и тег. Прямой setVisibility оставлял тег от addItem (id = 0, то есть
            // не null), а показ в updateViewVisibilityAnimated идёт по ветке
            // tag == null — кнопка навсегда оставалась скрытой.
            AndroidUtilities.updateViewVisibilityAnimated(resetItem,
                    CoreConfig.stickerSize.Float() != STICKER_SIZE_DEFAULT, 0.5f, false);
            resetItem.setOnClickListener(v -> resetStickerSize());
        }
        return view;
    }

    /** Плавный возврат слайдера к стандартному размеру за 200 мс. */
    private void resetStickerSize() {
        if (resetItem != null) {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, true);
        }
        ValueAnimator animator = ValueAnimator.ofFloat(CoreConfig.stickerSize.Float(), STICKER_SIZE_DEFAULT);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            CoreConfig.stickerSize.setConfigFloat(value);
            if (stickerSizeCell != null) {
                stickerSizeCell.setStickerSize(value);
            }
        });
        animator.start();
    }

    /** Кнопка возвращается при первом же движении слайдера. */
    private void showResetItem() {
        if (resetItem != null && resetItem.getVisibility() != View.VISIBLE) {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, true, 0.5f, true);
        }
    }

    // ---- Сворачиваемые группы: счётчики и мастер-переключатели ----

    private static final int REPLIES_TOTAL = 3;
    private static final int HIDE_REACTIONS_TOTAL = 3;
    private static final int UNLIMITED_TOTAL = 2;
    private static final int QUICK_TRANSITIONS_TOTAL = 2;
    private static final int CHAT_MENU_TOTAL = 13;
    private static final int MESSAGE_MENU_TOTAL = 24;
    private static final int MEDIA_VIEWER_MENU_TOTAL = 6;
    private static final int ACTION_BAR_BUTTONS_TOTAL = 5;
    private static final int PAUSE_TOTAL = 3;

    private static int repliesSelectedCount() {
        return count(ChatsConfig.replyColors.Bool(), ChatsConfig.replyEmoji.Bool(), ChatsConfig.replyBackground.Bool());
    }

    private static int hideReactionsSelectedCount() {
        return count(ChatsConfig.hideReactionsInChannels.Bool(), ChatsConfig.hideReactionsInGroups.Bool(),
                ChatsConfig.hideReactionsInPrivate.Bool());
    }

    private static int unlimitedSelectedCount() {
        return count(isUnlimitedRecentStickers(), ExtConfig.INSTANCE.getUnlimitedSavedGifs().Bool());
    }

    private static int quickTransitionsSelectedCount() {
        return count(quickTransitionForChannels(), quickTransitionForTopics());
    }

    private static int chatMenuSelectedCount() {
        return count(ExtConfig.INSTANCE.getShortcutsAdministrators().Bool(),
                ExtConfig.INSTANCE.getShortcutsRecentActions().Bool(),
                ExtConfig.INSTANCE.getShortcutsStatistics().Bool(),
                ExtConfig.INSTANCE.getShortcutsPermissions().Bool(),
                ExtConfig.INSTANCE.getShortcutsMembers().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemBoostGroup().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemLinkedChat().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemToBeginning().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemGoToMessage().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemHideTitle().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemViewDeleted().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemClearDeleted().Bool(),
                ExtConfig.INSTANCE.getChatMenuItemDeleteOwnMessages().Bool());
    }

    private static int messageMenuSelectedCount() {
        return count(ExtConfig.INSTANCE.getShowReactions().Bool(), ExtConfig.INSTANCE.getShowReplyInPrivate().Bool(),
                ExtConfig.INSTANCE.getShowCopyLink().Bool(), ExtConfig.INSTANCE.getShowCopyFrame().Bool(),
                ExtConfig.INSTANCE.getShowCopyPhoto().Bool(), ExtConfig.INSTANCE.getShowCopyAsSticker().Bool(),
                ExtConfig.INSTANCE.getShowAddToStickers().Bool(), ExtConfig.INSTANCE.getShowAddToFavorites().Bool(),
                ExtConfig.INSTANCE.getShowNoQuoteForward().Bool(), ExtConfig.INSTANCE.getShowSetReminder().Bool(),
                CoreConfig.showAddToSavedMessages.Bool(), ExtConfig.INSTANCE.getShowAddToBookmark().Bool(),
                CoreConfig.showRepeat.Bool(), ExtConfig.INSTANCE.getShowRepeatAsCopy().Bool(),
                CoreConfig.showDeleteDownloadedFile.Bool(), CoreConfig.showViewHistory.Bool(),
                CoreConfig.showTranslate.Bool(), ExtConfig.INSTANCE.getShowTranslateMessageLLM().Bool(),
                CoreConfig.showShareMessages.Bool(), CoreConfig.showMessageHide.Bool(),
                CoreConfig.showReport.Bool(), CoreConfig.showAdminActions.Bool(),
                CoreConfig.showChangePermissions.Bool(), CoreConfig.showMessageDetails.Bool());
    }

    private static int mediaViewerMenuSelectedCount() {
        return count(ExtConfig.INSTANCE.getMediaViewerMenuItemForward().Bool(),
                ExtConfig.INSTANCE.getMediaViewerMenuItemNoQuoteForward().Bool(),
                ExtConfig.INSTANCE.getMediaViewerMenuItemCopyFrame().Bool(),
                ExtConfig.INSTANCE.getMediaViewerMenuItemCopyPhoto().Bool(),
                ExtConfig.INSTANCE.getMediaViewerMenuItemSetProfilePhoto().Bool(),
                ExtConfig.INSTANCE.getMediaViewerMenuItemScanQRCode().Bool());
    }

    private static int actionBarButtonsSelectedCount() {
        return count(ExtConfig.INSTANCE.getActionBarButtonReply().Bool(),
                ExtConfig.INSTANCE.getActionBarButtonEdit().Bool(),
                ExtConfig.INSTANCE.getActionBarButtonSelectBetween().Bool(),
                ExtConfig.INSTANCE.getActionBarButtonCopy().Bool(),
                ExtConfig.INSTANCE.getActionBarButtonForward().Bool());
    }

    private static int pauseSelectedCount() {
        return count(CoreConfig.autoPauseVideo.Bool(), ChatsConfig.pauseOnMinimizeVoice.Bool(),
                ChatsConfig.pauseOnMinimizeRound.Bool());
    }

    /**
     * Знаменатель «N/M» считается по реально показанным строкам. Зеркалирование и
     * широкоугольная камера считаются только при CameraX: обе настройки применяет
     * CameraXSession, другим движкам их передать некуда.
     */
    private static int cameraSettingsTotal() {
        if (ChatsConfig.cameraType() == CAMERA_TYPE_TELEGRAM) {
            return 2;
        }
        int total = isSeamlessSwitchingAvailable() ? 3 : 2;
        if (ChatsConfig.cameraType() == CAMERA_TYPE_CAMERA_X) {
            total += 2;  // зеркало и широкий угол
        }
        return total;
    }

    private static int cameraSettingsSelected() {
        int selected = count(ChatsConfig.extendedFramesPerSecond.Bool(), ChatsConfig.cameraStabilization.Bool());
        if (ChatsConfig.cameraType() == CAMERA_TYPE_TELEGRAM) {
            return selected;
        }
        if (isSeamlessSwitchingAvailable() && isSeamlessSwitchingEnabled()) {
            selected++;
        }
        if (ChatsConfig.cameraType() == CAMERA_TYPE_CAMERA_X) {
            selected += count(ChatsConfig.cameraMirrorMode.Bool(), ChatsConfig.startWithWideAngleCamera.Bool());
        }
        return selected;
    }

    private void toggleAllReplies() {
        boolean enable = repliesSelectedCount() == 0;
        ChatsConfig.replyColors.setConfigBool(enable);
        ChatsConfig.replyEmoji.setConfigBool(enable);
        ChatsConfig.replyBackground.setConfigBool(enable);
        if (stickerSizeCell != null) {
            stickerSizeCell.invalidate();
        }
        rebuildChats();
        reloadList();
    }

    private void toggleAllHideReactions() {
        boolean enable = hideReactionsSelectedCount() == 0;
        ChatsConfig.hideReactionsInChannels.setConfigBool(enable);
        ChatsConfig.hideReactionsInGroups.setConfigBool(enable);
        ChatsConfig.hideReactionsInPrivate.setConfigBool(enable);
        rebuildChats();
        reloadList();
    }

    private void toggleAllUnlimited() {
        boolean enable = unlimitedSelectedCount() == 0;
        setUnlimitedRecentStickers(enable);
        ExtConfig.INSTANCE.getUnlimitedSavedGifs().setConfigBool(enable);
        reloadList();
    }

    private void toggleAllQuickTransitions() {
        boolean enable = quickTransitionsSelectedCount() == 0;
        CoreConfig.disableSwipeToNext.setConfigBool(!enable);
        CoreConfig.disableSwipeToNextTopic.setConfigBool(!enable);
        reloadList();
    }

    private void toggleAllChatMenu() {
        boolean enable = chatMenuSelectedCount() == 0;
        ExtConfig.INSTANCE.getShortcutsAdministrators().setConfigBool(enable);
        ExtConfig.INSTANCE.getShortcutsRecentActions().setConfigBool(enable);
        ExtConfig.INSTANCE.getShortcutsStatistics().setConfigBool(enable);
        ExtConfig.INSTANCE.getShortcutsPermissions().setConfigBool(enable);
        ExtConfig.INSTANCE.getShortcutsMembers().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemBoostGroup().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemLinkedChat().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemToBeginning().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemGoToMessage().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemHideTitle().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemViewDeleted().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemClearDeleted().setConfigBool(enable);
        ExtConfig.INSTANCE.getChatMenuItemDeleteOwnMessages().setConfigBool(enable);
        rebuildChats();
        reloadList();
    }

    private void toggleAllMessageMenu() {
        boolean enable = messageMenuSelectedCount() == 0;
        ExtConfig.INSTANCE.getShowReactions().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowReplyInPrivate().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowCopyLink().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowCopyFrame().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowCopyPhoto().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowCopyAsSticker().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowAddToStickers().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowAddToFavorites().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowNoQuoteForward().setConfigBool(enable);
        ExtConfig.INSTANCE.getShowSetReminder().setConfigBool(enable);
        CoreConfig.showAddToSavedMessages.setConfigBool(enable);
        ExtConfig.INSTANCE.getShowAddToBookmark().setConfigBool(enable);
        CoreConfig.showRepeat.setConfigBool(enable);
        ExtConfig.INSTANCE.getShowRepeatAsCopy().setConfigBool(enable);
        CoreConfig.showDeleteDownloadedFile.setConfigBool(enable);
        CoreConfig.showViewHistory.setConfigBool(enable);
        CoreConfig.showTranslate.setConfigBool(enable);
        ExtConfig.INSTANCE.getShowTranslateMessageLLM().setConfigBool(enable);
        CoreConfig.showShareMessages.setConfigBool(enable);
        CoreConfig.showMessageHide.setConfigBool(enable);
        CoreConfig.showReport.setConfigBool(enable);
        CoreConfig.showAdminActions.setConfigBool(enable);
        CoreConfig.showChangePermissions.setConfigBool(enable);
        CoreConfig.showMessageDetails.setConfigBool(enable);
        rebuildChats();
        reloadList();
    }

    private void toggleAllMediaViewerMenu() {
        boolean enable = mediaViewerMenuSelectedCount() == 0;
        ExtConfig.INSTANCE.getMediaViewerMenuItemForward().setConfigBool(enable);
        ExtConfig.INSTANCE.getMediaViewerMenuItemNoQuoteForward().setConfigBool(enable);
        ExtConfig.INSTANCE.getMediaViewerMenuItemCopyFrame().setConfigBool(enable);
        ExtConfig.INSTANCE.getMediaViewerMenuItemCopyPhoto().setConfigBool(enable);
        ExtConfig.INSTANCE.getMediaViewerMenuItemSetProfilePhoto().setConfigBool(enable);
        ExtConfig.INSTANCE.getMediaViewerMenuItemScanQRCode().setConfigBool(enable);
        reloadList();
    }

    private void toggleAllActionBarButtons() {
        boolean enable = actionBarButtonsSelectedCount() == 0;
        ExtConfig.INSTANCE.getActionBarButtonReply().setConfigBool(enable);
        ExtConfig.INSTANCE.getActionBarButtonEdit().setConfigBool(enable);
        ExtConfig.INSTANCE.getActionBarButtonSelectBetween().setConfigBool(enable);
        ExtConfig.INSTANCE.getActionBarButtonCopy().setConfigBool(enable);
        ExtConfig.INSTANCE.getActionBarButtonForward().setConfigBool(enable);
        rebuildChats();
        reloadList();
    }

    private void toggleAllCameraSettings() {
        boolean enable = cameraSettingsSelected() == 0;
        if (ChatsConfig.cameraType() != CAMERA_TYPE_TELEGRAM) {
            // При недоступном железе флаг всегда гасится.
            setSeamlessSwitching(enable && isSeamlessSwitchingAvailable());
        }
        ChatsConfig.extendedFramesPerSecond.setConfigBool(enable);
        ChatsConfig.cameraStabilization.setConfigBool(enable);
        reloadList();
    }

    private void toggleAllPause() {
        boolean enable = pauseSelectedCount() == 0;
        CoreConfig.autoPauseVideo.setConfigBool(enable);
        ChatsConfig.pauseOnMinimizeVoice.setConfigBool(enable);
        ChatsConfig.pauseOnMinimizeRound.setConfigBool(enable);
        reloadList();
    }

    private static ConfigItem[] premiumElementItems() {
        return new ConfigItem[]{
                ExtConfig.INSTANCE.getPremiumItemEmojiStatus(),
                ExtConfig.INSTANCE.getPremiumItemEmojiInReplies(),
                ExtConfig.INSTANCE.getPremiumItemCustomColorInReplies(),
                ExtConfig.INSTANCE.getPremiumItemCustomWallpaper(),
                ExtConfig.INSTANCE.getPremiumItemVideoAvatar(),
                ExtConfig.INSTANCE.getPremiumItemStarInReactions(),
                ExtConfig.INSTANCE.getPremiumItemStickerEffects(),
                ExtConfig.INSTANCE.getPremiumItemBoosts()
        };
    }

    private static ConfigItem[] deleteMenuItems() {
        return new ConfigItem[]{
                ExtConfig.INSTANCE.getDefaultDeleteMenuBanUsers(),
                ExtConfig.INSTANCE.getDefaultDeleteMenReportSpam(),
                ExtConfig.INSTANCE.getDefaultDeleteMenuDeleteAll(),
                ExtConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups()
        };
    }

    private static int selectedCount(ConfigItem[] items) {
        int selected = 0;
        for (ConfigItem item : items) {
            if (item.Bool()) {
                selected++;
            }
        }
        return selected;
    }

    private static void setAll(ConfigItem[] items, boolean value) {
        for (ConfigItem item : items) {
            item.setConfigBool(value);
        }
    }

    private void toggleAllPremiumElements() {
        ConfigItem[] items = premiumElementItems();
        setAll(items, selectedCount(items) == 0);
        if (stickerSizeCell != null) {
            stickerSizeCell.invalidate();
        }
        rebuildChats();
        reloadList();
    }

    private void toggleAllDeleteMenu() {
        ConfigItem[] items = deleteMenuItems();
        setAll(items, selectedCount(items) == 0);
        reloadList();
    }

    private static ConfigItem[] askWhenItems() {
        if (CoreConfig.useChatAttachMediaMenu.Bool()) {
            return new ConfigItem[]{
                    CoreConfig.askBeforeCall,
                    CoreConfig.repeatConfirm,
                    ExtConfig.INSTANCE.getDisableClickCommandToSend()
            };
        }
        return new ConfigItem[]{
                CoreConfig.confirmAVMessage,
                CoreConfig.askBeforeCall,
                CoreConfig.repeatConfirm,
                ExtConfig.INSTANCE.getDisableClickCommandToSend()
        };
    }

    private void toggleAllAskWhen() {
        ConfigItem[] items = askWhenItems();
        setAll(items, selectedCount(items) == 0);
        reloadList();
    }

    private static ConfigItem[] stripTrackingItems() {
        return new ConfigItem[]{ChatsConfig.stripTrackingOnOpen, ChatsConfig.stripTrackingOnPaste};
    }

    private void toggleAllStripTracking() {
        ConfigItem[] items = stripTrackingItems();
        setAll(items, selectedCount(items) == 0);
        reloadList();
        LinkCleaner.preloadIfEnabled();
    }

    private static ConfigItem[] hideAiItems() {
        return new ConfigItem[]{AppearanceConfig.hideAiEditor, AppearanceConfig.hideMessageSummary,
                AppearanceConfig.hideIvSummary};
    }

    private void toggleAllHideAi() {
        ConfigItem[] items = hideAiItems();
        setAll(items, selectedCount(items) == 0);
        rebuildChats();
        reloadList();
    }

    private static ConfigItem[] hideSettingsItems() {
        return HiddenSettingsRows.items();
    }

    private void toggleAllHideSettings() {
        ConfigItem[] items = hideSettingsItems();
        setAll(items, selectedCount(items) == 0);
        rebuildChats();
        reloadList();
        showRestartHint();
    }

    private void showRestartHint() {
        if (getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.info, getString(R.string.MZAppearanceNeedRestart),
                        getString(R.string.MZAppearanceRestartNow),
                        () -> {
                            Activity activity = getParentActivity();
                            if (activity != null) {
                                AppRestartHelper.triggerRebirth(activity, new Intent(activity, LaunchActivity.class));
                            }
                        })
                .show();
    }

    private CharSequence[] storiesOptions() {
        return new CharSequence[]{
                getString(R.string.MZAppearanceStoriesShow),
                getString(R.string.MZAppearanceStoriesHideHeader),
                getString(R.string.MZAppearanceStoriesDisable)
        };
    }

    private static int storiesIndex() {
        if (ExtConfig.INSTANCE.getDisableStories().Bool()) {
            return 2;
        }
        return ExtConfig.INSTANCE.getHideStoriesFromHeader().Bool() ? 1 : 0;
    }

    private void showStoriesOptions(View view, int position) {
        showOptions(view, storiesOptions(), index -> {
            boolean wasDisabled = ExtConfig.INSTANCE.getDisableStories().Bool();
            ExtConfig.INSTANCE.getHideStoriesFromHeader().setConfigBool(index != 0);
            ExtConfig.INSTANCE.getDisableStories().setConfigBool(index == 2);
            listAdapter.notifyItemChanged(position);
            rebuildChats();
            if (wasDisabled != (index == 2)) {
                showRestartHint();
            }
        });
    }

    private void showTrackingFilterOptions(View view) {
        boolean override = LinkCleaner.isUsingOverride();
        CharSequence[] options = override
                ? new CharSequence[]{getString(R.string.MZChatsTrackingFilterUpdate), getString(R.string.MZChatsTrackingFilterRevert)}
                : new CharSequence[]{getString(R.string.MZChatsTrackingFilterUpdate)};
        showOptions(view, options, index -> {
            if (index == 0) {
                updateTrackingFilter();
            } else {
                Utilities.globalQueue.postRunnable(() -> {
                    LinkCleaner.resetToBundled();
                    AndroidUtilities.runOnUIThread(this::onTrackingFilterChanged);
                });
            }
        });
    }

    private void updateTrackingFilter() {
        Utilities.globalQueue.postRunnable(() -> {
            int result;
            try {
                result = LinkCleaner.fetchLatest() ? R.string.MZChatsTrackingFilterUpdated : R.string.MZChatsTrackingFilterLatest;
            } catch (Exception e) {
                FileLog.e(e);
                result = R.string.MZChatsTrackingFilterFailed;
            }
            final int text = result;
            AndroidUtilities.runOnUIThread(() -> {
                onTrackingFilterChanged();
                BulletinFactory.of(this).createSimpleBulletin(text == R.string.MZChatsTrackingFilterFailed ? R.raw.error : R.raw.done, getString(text)).show();
            });
        });
    }

    private void onTrackingFilterChanged() {
        if (listAdapter != null && trackingFilterRow >= 0) {
            listAdapter.notifyItemChanged(trackingFilterRow);
        }
    }

    // ---- Настройки, которые лежат не в ConfigItem ----

    /** «Быстрый свайп-переход» — это ключи CoreConfig.disableSwipeToNext*, только наоборот. */
    private static boolean quickTransitionForChannels() {
        return !CoreConfig.disableSwipeToNext.Bool();
    }

    private static boolean quickTransitionForTopics() {
        return !CoreConfig.disableSwipeToNextTopic.Bool();
    }

    /** Вторая камера для «бесшовного переключения» есть не на всех устройствах. */
    private static boolean isSeamlessSwitchingAvailable() {
        Context context = ApplicationLoader.applicationContext;
        return context != null && org.mzgram.camera.CameraXSession.isSeamlessSwitchingAvailable(context);
    }

    private static boolean isSeamlessSwitchingEnabled() {
        Context context = ApplicationLoader.applicationContext;
        return context != null && DualCameraView.roundDualAvailableStatic(context);
    }

    /**
     * Настройка живёт в глобальных префах, а не в ChatsConfig: этот же ключ
     * читает InstantCameraView.
     */
    private static void setSeamlessSwitching(boolean value) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("rounddual_available", value).apply();
    }

    private static final int CAMERA_TYPE_SYSTEM = 0;
    private static final int CAMERA_TYPE_CAMERA2 = 1;
    private static final int CAMERA_TYPE_CAMERA_X = 2;
    private static final int CAMERA_TYPE_TELEGRAM = 3;
    /** Индекс варианта «спрашивать каждый раз» в ExtConfig.cameraInVideoMessages. */
    private static final int VIDEO_CAMERA_ASK = 2;

    /**
     * Тип камеры для кружков. Системный вариант оставлен за SharedConfig — там же, где
     * его переключает отладочное меню Telegram; два других выбираются явно и живут в
     * своём ключе, потому что булевым флагом три состояния не выразить.
     */
    private int cameraTypeIndex() {
        return ChatsConfig.cameraType();
    }

    private void setCameraTypeIndex(int index) {
        if (cameraTypeIndex() != index) {
            ChatsConfig.cameraType.setConfigInt(index);
        }
    }

    /**
     * Иконка строки AI-чата. Варианты под иконпаки нужны потому, что BaseIconPacks
     * подменяет иконки по всему приложению — здесь должно быть так же.
     */
    private static int aiChatIcon() {
        switch (BaseIconPacks.getSelected()) {
            case BaseIconPacks.BASE_SOLAR:
                return R.drawable.ai_chat_solar;
            case BaseIconPacks.BASE_REMIX:
                return R.drawable.ai_chat_remix;
            default:
                return R.drawable.ai_chat;
        }
    }

    // ---- Значения для строк с выбором ----

    private void setBottomButton(int index) {
        ChatsConfig.bottomButton.setConfigInt(index);
        ExtConfig.INSTANCE.getDisableChannelMuteButton()
                .setConfigBool(index == ChatsConfig.BOTTOM_BUTTON_HIDE);
    }

    private CharSequence[] stickerTimeOptions() {
        return new CharSequence[]{
                getString(R.string.Default),
                getString(R.string.MZChatsStickerTimeSide),
                getString(R.string.MZChatsStickerTimeHidden)
        };
    }

    private CharSequence[] bottomButtonOptions() {
        return new CharSequence[]{
                getString(R.string.Hide),
                getString(R.string.ChannelMuteNoCaps),
                getString(R.string.MZChatsBottomButtonDiscuss)
        };
    }

    private static final int OPEN_LINK_CONFIRM_HIDDEN = 0;
    private static final int OPEN_LINK_CONFIRM_ALL = 1;
    private static final int OPEN_LINK_CONFIRM_NEVER = 2;

    private static int openLinkConfirmation() {
        if (ExtConfig.INSTANCE.getConfirmAllLinks().Bool()) {
            return OPEN_LINK_CONFIRM_ALL;
        }
        return CoreConfig.skipOpenLinkConfirm.Bool() ? OPEN_LINK_CONFIRM_NEVER : OPEN_LINK_CONFIRM_HIDDEN;
    }

    private static void setOpenLinkConfirmation(int index) {
        ExtConfig.INSTANCE.getConfirmAllLinks().setConfigBool(index == OPEN_LINK_CONFIRM_ALL);
        CoreConfig.skipOpenLinkConfirm.setConfigBool(index == OPEN_LINK_CONFIRM_NEVER);
    }

    private CharSequence[] openLinkConfirmationOptions() {
        return new CharSequence[]{
                getString(R.string.MZChatsOpenLinkConfirmationHidden),
                getString(R.string.MZChatsOpenLinkConfirmationAll),
                getString(R.string.MZChatsOpenLinkConfirmationNever)
        };
    }

    private CharSequence[] transcribeProviderOptions() {
        return new CharSequence[]{
                getString(R.string.TranscribeProviderAuto),
                getString(R.string.TelegramPremium),
                getString(R.string.TranscribeProviderWorkersAI),
                getString(R.string.TranscribeProviderGemini),
                getString(R.string.TranscribeProviderOpenAI),
                getString(R.string.TranscribeProviderVosk)
        };
    }

    private CharSequence[] cameraTypeOptions() {
        return new CharSequence[]{
                getString(R.string.Default),
                getString(R.string.MZChatsCameraTypeCamera2),
                getString(R.string.MZChatsCameraTypeCameraX),
                getString(R.string.MZChatsCameraTypeTelegram)
        };
    }

    private CharSequence[] videoMessagesCameraOptions() {
        return new CharSequence[]{
                getString(R.string.CameraInVideoMessagesFront),
                getString(R.string.CameraInVideoMessagesRear),
                getString(R.string.CameraInVideoMessagesAsk)
        };
    }

    private CharSequence[] seekDurationOptions() {
        int[] durations = ChatsConfig.SEEK_DURATIONS;
        CharSequence[] result = new CharSequence[durations.length];
        for (int a = 0; a < durations.length; a++) {
            result[a] = LocaleController.formatPluralString("Seconds", durations[a]);
        }
        return result;
    }

    private static int clampIndex(int value, int size) {
        return value >= 0 && value < size ? value : 0;
    }

    private static int count(boolean... values) {
        int c = 0;
        for (boolean v : values) {
            if (v) c++;
        }
        return c;
    }

    private static String ratio(int selected, int total) {
        return String.format(Locale.getDefault(), "%d/%d", selected, total);
    }

    private void showOptions(View view, int position, CharSequence[] options, ConfigItem item) {
        showOptions(view, options, index -> {
            item.setConfigInt(index);
            listAdapter.notifyItemChanged(position);
        });
    }

    private CharSequence[] searchHashtagPageOptions() {
        return new CharSequence[]{
                getString(R.string.SearchThisChat),
                getString(R.string.SearchMyMessages),
                getString(R.string.SearchPublicPosts),
        };
    }

    private CharSequence[] videoPlayerDecoderOptions() {
        return new CharSequence[]{
                getString(R.string.VideoPlayerDecoderHardware),
                getString(R.string.VideoPlayerDecoderPreferHW),
                getString(R.string.VideoPlayerDecoderPreferSW),
        };
    }

    private interface OnIndexSelected {
        void run(int index);
    }

    private void showOptions(View view, CharSequence[] options, OnIndexSelected onSelected) {
        PopupBuilder builder = new PopupBuilder(view);
        builder.setItems(new ArrayList<CharSequence>(Arrays.asList(options)), (index, text) -> {
            onSelected.run(index);
            return Unit.INSTANCE;
        });
        builder.show();
    }

    /**
     * Иконки действий двойного тапа по индексам {@link DoubleTap} (0..10).
     * Такая же таблица есть в DoubleTapCell, но она приватная — при случае стоит открыть её там
     * и удалить этот дубль.
     */
    private static final int[] DOUBLE_TAP_ICONS = new int[]{
            R.drawable.msg_block,
            R.drawable.msg_reactions,
            R.drawable.msg_reactions2,
            R.drawable.msg_translate,
            R.drawable.msg_reply_small,
            R.drawable.msg_saved,
            R.drawable.msg_repeat,
            R.drawable.msg_copy,
            R.drawable.msg_edit,
            R.drawable.msg_translate,
            R.drawable.msg_delete,
            R.drawable.msg_forward,
            R.drawable.msg_download
    };

    /**
     * Список с заголовком и иконками действий, а не голый попап. Набор действий
     * наш, из MZGram: TRANSLATE_LLM и REPEAT_AS_COPY.
     */
    private void showDoubleTapOptions(int position, boolean outgoing) {
        if (getParentActivity() == null) {
            return;
        }
        List<Integer> types = new ArrayList<>();
        types.add(DoubleTap.DOUBLE_TAP_ACTION_NONE);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_SHOW_REACTIONS);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE_LLM);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_REPLY);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_FORWARD);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_SAVE);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_DOWNLOAD);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT);
        types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT_AS_COPY);
        if (outgoing) {
            types.add(DoubleTap.DOUBLE_TAP_ACTION_EDIT);
        }
        types.add(DoubleTap.DOUBLE_TAP_ACTION_DELETE);

        CharSequence[] titles = new CharSequence[types.size()];
        int[] icons = new int[types.size()];
        for (int a = 0; a < types.size(); a++) {
            int action = types.get(a);
            titles[a] = DoubleTap.doubleTapActionMap.get(action);
            icons[a] = action >= 0 && action < DOUBLE_TAP_ICONS.length
                    ? DOUBLE_TAP_ICONS[action] : DOUBLE_TAP_ICONS[0];
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(outgoing ? R.string.DoubleTapOutgoing : R.string.DoubleTapIncoming));
        builder.setItems(titles, icons, (dialog, which) -> {
            boolean hadReaction = doubleTapReactionRow != -1;
            int action = types.get(which);
            if (outgoing) {
                ExtConfig.INSTANCE.getDoubleTapActionOut().setConfigInt(action);
            } else {
                ExtConfig.INSTANCE.getDoubleTapAction().setConfigInt(action);
            }
            if (doubleTapCell != null) {
                // 1 — обновить только входящее сообщение, 2 — только исходящее.
                doubleTapCell.updateIcons(outgoing ? 2 : 1, true);
                doubleTapCell.invalidate();
            }
            boolean hasReaction = ExtConfig.INSTANCE.getDoubleTapAction().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS
                    || ExtConfig.INSTANCE.getDoubleTapActionOut().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS;
            if (hadReaction != hasReaction) {
                reloadList();
            } else {
                listAdapter.notifyItemChanged(position);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /**
     * «Безлимит недавних стикеров» из MZGram — один тумблер поверх двух рабочих ключей
     * MZGram: лимита недавних ({@link CoreConfig#maxRecentStickerCount}, шкала 20…200) и
     * безлимитных избранных ({@link CoreConfig#unlimitedFavedStickers}).
     */
    private static final int RECENT_STICKERS_DEFAULT = 20;
    private static final int RECENT_STICKERS_MAX = 200;

    private static boolean isUnlimitedRecentStickers() {
        return CoreConfig.maxRecentStickerCount.Int() > RECENT_STICKERS_DEFAULT
                || CoreConfig.unlimitedFavedStickers.Bool();
    }

    private static void setUnlimitedRecentStickers(boolean value) {
        CoreConfig.maxRecentStickerCount.setConfigInt(value ? RECENT_STICKERS_MAX : RECENT_STICKERS_DEFAULT);
        CoreConfig.unlimitedFavedStickers.setConfigBool(value);
    }

    /**
     * «Быстрые действия администратора» из MZGram — один тумблер поверх пяти пунктов меню чата
     * MZGram ({@code ExtConfig.shortcuts*}) и пункта «Права администратора» в меню сообщения
     * ({@link CoreConfig#showAdminActions}). Включён, пока включён хотя бы один пункт.
     */
    private static ConfigItem[] adminShortcutItems() {
        return new ConfigItem[]{
                ExtConfig.INSTANCE.getShortcutsAdministrators(),
                ExtConfig.INSTANCE.getShortcutsRecentActions(),
                ExtConfig.INSTANCE.getShortcutsStatistics(),
                ExtConfig.INSTANCE.getShortcutsPermissions(),
                ExtConfig.INSTANCE.getShortcutsMembers(),
                CoreConfig.showAdminActions
        };
    }

    private static boolean isQuickAdminShortcuts() {
        for (ConfigItem item : adminShortcutItems()) {
            if (item.Bool()) {
                return true;
            }
        }
        return false;
    }

    private void toggleQuickAdminShortcuts(View view) {
        boolean value = !isQuickAdminShortcuts();
        for (ConfigItem item : adminShortcutItems()) {
            item.setConfigBool(value);
        }
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
    }

    private void toggleHighQualityPhoto(View view) {
        boolean value = !ChatsConfig.alwaysSendInHD.Bool();
        ChatsConfig.alwaysSendInHD.setConfigBool(value);
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        // Клик по телу строки-группы только сворачивает её; мастер-переключатель живёт
        // в правой зоне TextCheckCell2 за разделителем.
        if (position == repliesGroupRow) {
            repliesExpanded = !repliesExpanded;
            reloadList();
            return;
        } else if (position == hideReactionsGroupRow) {
            hideReactionsExpanded = !hideReactionsExpanded;
            reloadList();
            return;
        } else if (position == unlimitedGroupRow) {
            unlimitedExpanded = !unlimitedExpanded;
            reloadList();
            return;
        } else if (position == quickTransitionGroupRow) {
            quickTransitionExpanded = !quickTransitionExpanded;
            reloadList();
            return;
        } else if (position == chatMenuGroupRow) {
            chatMenuExpanded = !chatMenuExpanded;
            reloadList();
            return;
        } else if (position == messageMenuGroupRow) {
            messageMenuExpanded = !messageMenuExpanded;
            reloadList();
            return;
        } else if (position == mediaViewerMenuGroupRow) {
            mediaViewerMenuExpanded = !mediaViewerMenuExpanded;
            reloadList();
            return;
        } else if (position == actionBarButtonsGroupRow) {
            actionBarButtonsExpanded = !actionBarButtonsExpanded;
            reloadList();
            return;
        } else if (position == extendedSettingsGroupRow) {
            extendedSettingsExpanded = !extendedSettingsExpanded;
            reloadList();
            return;
        } else if (position == pauseGroupRow) {
            pauseExpanded = !pauseExpanded;
            reloadList();
            return;
        } else if (position == premiumElementsGroupRow) {
            premiumElementsExpanded = !premiumElementsExpanded;
            reloadList();
            return;
        } else if (position == deleteMenuGroupRow) {
            deleteMenuExpanded = !deleteMenuExpanded;
            reloadList();
            return;
        } else if (position == askWhenGroupRow) {
            askWhenExpanded = !askWhenExpanded;
            reloadList();
            return;
        } else if (position == stripTrackingGroupRow) {
            stripTrackingExpanded = !stripTrackingExpanded;
            reloadList();
            return;
        } else if (position == hideAiGroupRow) {
            hideAiExpanded = !hideAiExpanded;
            reloadList();
            return;
        } else if (position == hideSettingsGroupRow) {
            hideSettingsExpanded = !hideSettingsExpanded;
            reloadList();
            return;
        } else if (position == trackingFilterRow) {
            showTrackingFilterOptions(view);
            return;
        }

        if (groupHeaderFor(position) != -1) {
            onGroupItemClick(view, position);
            return;
        }

        // Строки с выбором из списка
        if (position == doubleTapIncomingRow) {
            showDoubleTapOptions(position, false);
            return;
        } else if (position == doubleTapOutgoingRow) {
            showDoubleTapOptions(position, true);
            return;
        } else if (position == doubleTapReactionRow) {
            DoubleTapCell.SetReactionCell.showSelectStatusDialog((DoubleTapCell.SetReactionCell) view, this);
            return;
        } else if (position == stickerTimeRow) {
            showOptions(view, stickerTimeOptions(), index -> {
                ChatsConfig.setStickerTimeMode(index);
                listAdapter.notifyItemChanged(position);
                if (stickerSizeCell != null) {
                    stickerSizeCell.invalidate();
                }
            });
            return;
        } else if (position == bottomButtonRow) {
            showOptions(view, bottomButtonOptions(), index -> {
                setBottomButton(index);
                listAdapter.notifyItemChanged(position);
                rebuildChats();
            });
            return;
        } else if (position == cameraTypeRow) {
            showOptions(view, cameraTypeOptions(), index -> {
                setCameraTypeIndex(index);
                // от типа камеры зависит, показывать ли группу расширенных настроек
                reloadList();
            });
            return;
        } else if (position == videoMessagesCameraRow) {
            showOptions(view, videoMessagesCameraOptions(), index -> {
                ExtConfig.INSTANCE.getCameraInVideoMessages().setConfigInt(index);
                // от варианта зависит, показывать ли «запоминать последнюю камеру»
                reloadList();
            });
            return;
        } else if (position == doubleTapSeekDurationRow) {
            showOptions(view, position, seekDurationOptions(), ChatsConfig.doubleTapSeekDuration);
            return;
        } else if (position == videoPlayerDecoderRow) {
            showOptions(view, position, videoPlayerDecoderOptions(), ExtConfig.INSTANCE.getPlayerDecoder());
            return;
        } else if (position == searchHashtagChatRow) {
            showOptions(view, position, searchHashtagPageOptions(), ExtConfig.INSTANCE.getSearchHashtagDefaultPageChat());
            return;
        } else if (position == searchHashtagChannelRow) {
            showOptions(view, position, searchHashtagPageOptions(), ExtConfig.INSTANCE.getSearchHashtagDefaultPageChannel());
            return;
        } else if (position == alwaysSendHdRow) {
            toggleHighQualityPhoto(view);
            return;
        } else if (position == adminShortcutsRow) {
            toggleQuickAdminShortcuts(view);
            return;
        } else if (position == inlineMathSettingsRow) {
            presentFragment(new InlineResultsActivity());
            return;
        } else if (position == menusRow) {
            presentFragment(new MZGramChatsActivity(SCREEN_MENUS));
            return;
        } else if (position == mediaRow) {
            presentFragment(new MZGramChatsActivity(SCREEN_MEDIA));
            return;
        } else if (position == hideStoriesRow) {
            showStoriesOptions(view, position);
            return;
        } else if (position == chatSettingsRow) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            return;
        } else if (position == aiChatRow) {
            presentFragment(new org.mzgram.ai.ui.AiSettingsActivity());
            return;
        } else if (position == textStyleRow) {
            showDialog(TextStyleDialog.create(getParentActivity()));
            return;
        } else if (position == openLinkConfirmationRow) {
            showOptions(view, openLinkConfirmationOptions(), index -> {
                setOpenLinkConfirmation(index);
                listAdapter.notifyItemChanged(position);
            });
            return;
        } else if (position == transcribeProviderRow) {
            showOptions(view, transcribeProviderOptions(), index -> {
                ExtConfig.INSTANCE.getTranscribeProvider().setConfigInt(index);
                reloadList();
            });
            return;
        } else if (position == cloudflareCredentialsRow) {
            TranscribeHelper.showCfCredentialsDialog(this);
            return;
        } else if (position == geminiApiKeyRow) {
            TranscribeHelper.showGeminiApiKeyDialog(this);
            return;
        } else if (position == openAiCredentialsRow) {
            TranscribeHelper.showOpenAiCredentialsDialog(this);
            return;
        } else if (position == voskModelsRow) {
            presentFragment(new org.mzgram.speech.VoskSettingsActivity());
            return;
        }

        if (position == tapToSwitchRecordRow) {
            boolean tapToSwitch = CoreConfig.useChatAttachMediaMenu.Bool();
            CoreConfig.useChatAttachMediaMenu.setConfigBool(!tapToSwitch);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(tapToSwitch);
            }
            int wasConfirmAVMessageRow = confirmAVMessageRow;
            updateRows();
            if (listAdapter != null) {
                if (wasConfirmAVMessageRow != -1 && confirmAVMessageRow == -1) {
                    listAdapter.notifyItemRemoved(wasConfirmAVMessageRow);
                } else if (wasConfirmAVMessageRow == -1 && confirmAVMessageRow != -1) {
                    listAdapter.notifyItemInserted(confirmAVMessageRow);
                }
                listAdapter.notifyItemChanged(askWhenGroupRow);
            }
            return;
        }

        ConfigItem item = configForRow(position);
        if (item == null) {
            return;
        }
        boolean value = item.toggleConfigBool();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
        if (position == removeMessageTailRow) {
            // Пузырь рисуется закешированным drawable — без сброса эффекта не видно.
            // Обнуление и пересоздание — строго вместе: Theme.createChatResources
            // восстанавливает весь блок именно по условию chat_msgInDrawable == null.
            // Обнулить без активити означало бы оставить статик null и уронить
            // отрисовку первого же пузыря.
            Activity activity = getParentActivity();
            if (activity != null) {
                Theme.chat_msgInDrawable = null;
                Theme.createChatResources(activity, false);
            }
            rebuildChats();
        } else if (position == showResultsBeforeVotingRow) {
            rebuildChats();
        } else if (position == dontAutoplayWidgetsRow) {
            if (value) {
                org.telegram.utils.twidget.WidgetMedia.onAutoplayDisabled();
            }
            rebuildChats();
        } else if (position == wideChannelPostsRow) {
            if (wideChannelPostsPreviewCell != null) {
                wideChannelPostsPreviewCell.setWide(value, true);
            }
            rebuildChats();
        } else if (position == wideFeedPostsRow) {
            rebuildChats();
        } else if (position == showSmallGifRow || position == dateOfForwardedMsgRow) {
            rebuildChats();
        } else if (position == disableProximityEventsRow) {
            MediaController.getInstance().recreateProximityWakeLock();
        } else if (position == hideActionBarStatusRow || position == hideFloatingButtonRow
                || position == hideSearchBarRow) {
            if (position == hideSearchBarRow && !ExtConfig.INSTANCE.getHideDialogsSearchField().Bool()) {
                // MZGram: bringing the search bar back also undoes "search hidden" in Appearance.
                AppearanceConfig.hideDialogsSearch.setConfigBool(false);
            }
            rebuildChats();
        } else if (position == hideAllChatsRow) {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            rebuildChats();
        }
    }

    /** Клик по вложенному пункту группы: переключить, обновить счётчик заголовка, применить. */
    private void onGroupItemClick(View view, int position) {
        boolean value = !isRowChecked(position);
        setRowChecked(position, value);
        if (view instanceof CheckBoxCell) {
            ((CheckBoxCell) view).setChecked(value, true);
        }
        int header = groupHeaderFor(position);
        if (header != -1 && listAdapter != null) {
            listAdapter.notifyItemChanged(header);
        }
        if (header == repliesGroupRow || header == hideReactionsGroupRow) {
            if (stickerSizeCell != null) {
                stickerSizeCell.invalidate();
            }
            rebuildChats();
        } else if (header == chatMenuGroupRow || header == messageMenuGroupRow) {
            // Меню сообщения собирается при создании фрагмента чата.
            rebuildChats();
        } else if (header == premiumElementsGroupRow) {
            if (stickerSizeCell != null) {
                stickerSizeCell.invalidate();
            }
            rebuildChats();
        } else if (header == stripTrackingGroupRow) {
            LinkCleaner.preloadIfEnabled();
        } else if (header == hideAiGroupRow) {
            rebuildChats();
        } else if (header == hideSettingsGroupRow) {
            rebuildChats();
            showRestartHint();
        }
    }

    /** Заголовок группы, которой принадлежит строка, либо -1 для одиночных строк. */
    private int groupHeaderFor(int position) {
        if (position < 0) {
            return -1;
        }
        if (position == replyColorsRow || position == replyEmojiRow || position == replyBackgroundRow) {
            return repliesGroupRow;
        } else if (position == hideReactionsChannelsRow || position == hideReactionsGroupsRow
                || position == hideReactionsPrivateRow) {
            return hideReactionsGroupRow;
        } else if (position == unlimitedStickersRow || position == unlimitedGifsRow) {
            return unlimitedGroupRow;
        } else if (position == quickTransitionChannelsRow || position == quickTransitionTopicsRow) {
            return quickTransitionGroupRow;
        } else if (position == menuCopyPhotoRow || position == menuSaveRow || position == menuRepeatRow
                || position == menuClearRow || position == menuHistoryRow || position == menuReportRow
                || position == menuDetailsRow || position == menuReactionsRow
                || position == menuReplyInPrivateRow || position == menuCopyLinkRow
                || position == menuCopyFrameRow || position == menuCopyAsStickerRow
                || position == menuAddToStickersRow || position == menuAddToFavoritesRow
                || position == menuNoQuoteForwardRow || position == menuSetReminderRow
                || position == menuBookmarkRow || position == menuRepeatAsCopyRow
                || position == menuTranslateRow || position == menuTranslateLlmRow
                || position == menuShareRow || position == menuHideRow
                || position == menuAdminActionsRow || position == menuPermissionsRow) {
            return messageMenuGroupRow;
        } else if (position == chatMenuAdminsRow || position == chatMenuRecentActionsRow
                || position == chatMenuStatisticsRow || position == chatMenuPermissionsRow
                || position == chatMenuMembersRow || position == chatMenuBoostRow
                || position == chatMenuLinkedChatRow || position == chatMenuToBeginningRow
                || position == chatMenuGoToMessageRow || position == chatMenuHideTitleRow
                || position == chatMenuViewDeletedRow || position == chatMenuClearDeletedRow
                || position == chatMenuDeleteOwnRow) {
            return chatMenuGroupRow;
        } else if (position == mediaMenuForwardRow || position == mediaMenuNoQuoteForwardRow
                || position == mediaMenuCopyFrameRow || position == mediaMenuCopyPhotoRow
                || position == mediaMenuProfilePhotoRow || position == mediaMenuQrRow) {
            return mediaViewerMenuGroupRow;
        } else if (position == actionBarReplyRow || position == actionBarEditRow
                || position == actionBarSelectBetweenRow || position == actionBarCopyRow
                || position == actionBarForwardRow) {
            return actionBarButtonsGroupRow;
        } else if (position == seamlessSwitchingRow || position == extendedFpsRow
                || position == cameraStabilizationRow || position == cameraMirrorModeRow
                || position == startWithWideAngleRow) {
            return extendedSettingsGroupRow;
        } else if (position == pauseVideoRow || position == pauseVoiceRow || position == pauseRoundRow) {
            return pauseGroupRow;
        } else if (position == premiumEmojiStatusRow || position == premiumEmojiInRepliesRow
                || position == premiumColorsInRepliesRow || position == premiumWallpapersRow
                || position == premiumVideoAvatarsRow || position == premiumStarReactionsRow
                || position == premiumStickerEffectsRow || position == premiumBoostsRow) {
            return premiumElementsGroupRow;
        } else if (position == deleteMenuBanUsersRow || position == deleteMenuReportSpamRow
                || position == deleteMenuDeleteAllRow || position == deleteMenuCommonGroupsRow) {
            return deleteMenuGroupRow;
        } else if (position == confirmAVMessageRow || position == askBeforeCallRow
                || position == repeatConfirmRow || position == disableClickCommandToSendRow) {
            return askWhenGroupRow;
        } else if (position == stripTrackingOpenRow || position == stripTrackingPasteRow) {
            return stripTrackingGroupRow;
        } else if (position == hideAiEditorRow || position == hideAiSummaryRow || position == hideAiIvRow) {
            return hideAiGroupRow;
        } else if (hideSettingsIndex(position) >= 0) {
            return hideSettingsGroupRow;
        }
        return -1;
    }

    private boolean isRowChecked(int position) {
        if (position == unlimitedStickersRow) return isUnlimitedRecentStickers();
        if (position == quickTransitionChannelsRow) return quickTransitionForChannels();
        if (position == quickTransitionTopicsRow) return quickTransitionForTopics();
        if (position == seamlessSwitchingRow) return isSeamlessSwitchingEnabled();
        ConfigItem item = configForRow(position);
        return item != null && item.Bool();
    }

    private void setRowChecked(int position, boolean value) {
        if (position == unlimitedStickersRow) {
            setUnlimitedRecentStickers(value);
            return;
        }
        if (position == quickTransitionChannelsRow) {
            CoreConfig.disableSwipeToNext.setConfigBool(!value);
            return;
        }
        if (position == quickTransitionTopicsRow) {
            CoreConfig.disableSwipeToNextTopic.setConfigBool(!value);
            return;
        }
        if (position == seamlessSwitchingRow) {
            setSeamlessSwitching(value);
            return;
        }
        ConfigItem item = configForRow(position);
        if (item != null) {
            item.setConfigBool(value);
        }
    }

    private ConfigItem configForRow(int position) {
        if (position == replyColorsRow) return ChatsConfig.replyColors;
        if (position == replyEmojiRow) return ChatsConfig.replyEmoji;
        if (position == replyBackgroundRow) return ChatsConfig.replyBackground;
        if (position == hideReactionsChannelsRow) return ChatsConfig.hideReactionsInChannels;
        if (position == hideReactionsGroupsRow) return ChatsConfig.hideReactionsInGroups;
        if (position == hideReactionsPrivateRow) return ChatsConfig.hideReactionsInPrivate;
        if (position == unlimitedGifsRow) return ExtConfig.INSTANCE.getUnlimitedSavedGifs();
        if (position == disableGreetingRow) return CoreConfig.dontSendGreetingSticker;
        if (position == hideKeyboardOnScrollRow) return CoreConfig.hideKeyboardOnChatScroll;
        if (position == disableGlobalSearchRow) return ExtConfig.INSTANCE.getDisableGlobalSearch();
        if (position == addCommaRow) return MZGramConfig.addCommaAfterMention;
        if (position == inlineMathRow) return ChatsConfig.inlineMathResult;
        if (position == hideSendAsPeerRow) return CoreConfig.hideSendAsChannel;
        if (position == keepAttachButtonRow) return ChatsConfig.keepAttachButton;
        if (position == removeMessageTailRow) return ChatsConfig.removeMessageTail;
        if (position == unreadCapsuleRow) return ChatsConfig.unreadCapsule;
        if (position == replaceEditedRow) return ExtConfig.INSTANCE.getUseEditedIcon();
        if (position == showOnlineStatusRow) return ExtConfig.INSTANCE.getShowOnlineStatus();
        if (position == hideShareButtonRow) return ExtConfig.INSTANCE.getHideShareButtonInChannel();
        if (position == hideGiftButtonRow) return ExtConfig.INSTANCE.getHideGiftButtonInChannel();
        if (position == hideSearchButtonRow) return ChatsConfig.hideChannelSearchButton;
        if (position == hideCallButtonRow) return ChatsConfig.hideCallButton;
        if (position == hideMoneyButtonRow) return ChatsConfig.hideMoneyButton;
        if (position == showResultsBeforeVotingRow) return ChatsConfig.showResultsBeforeVoting;
        if (position == menuCopyPhotoRow) return ExtConfig.INSTANCE.getShowCopyPhoto();
        if (position == menuSaveRow) return CoreConfig.showAddToSavedMessages;
        if (position == menuRepeatRow) return CoreConfig.showRepeat;
        if (position == menuClearRow) return CoreConfig.showDeleteDownloadedFile;
        if (position == menuHistoryRow) return CoreConfig.showViewHistory;
        if (position == menuReportRow) return CoreConfig.showReport;
        if (position == menuDetailsRow) return CoreConfig.showMessageDetails;
        if (position == chatMenuAdminsRow) return ExtConfig.INSTANCE.getShortcutsAdministrators();
        if (position == chatMenuRecentActionsRow) return ExtConfig.INSTANCE.getShortcutsRecentActions();
        if (position == chatMenuStatisticsRow) return ExtConfig.INSTANCE.getShortcutsStatistics();
        if (position == chatMenuPermissionsRow) return ExtConfig.INSTANCE.getShortcutsPermissions();
        if (position == chatMenuMembersRow) return ExtConfig.INSTANCE.getShortcutsMembers();
        if (position == chatMenuBoostRow) return ExtConfig.INSTANCE.getChatMenuItemBoostGroup();
        if (position == chatMenuLinkedChatRow) return ExtConfig.INSTANCE.getChatMenuItemLinkedChat();
        if (position == chatMenuToBeginningRow) return ExtConfig.INSTANCE.getChatMenuItemToBeginning();
        if (position == chatMenuGoToMessageRow) return ExtConfig.INSTANCE.getChatMenuItemGoToMessage();
        if (position == chatMenuHideTitleRow) return ExtConfig.INSTANCE.getChatMenuItemHideTitle();
        if (position == chatMenuViewDeletedRow) return ExtConfig.INSTANCE.getChatMenuItemViewDeleted();
        if (position == chatMenuClearDeletedRow) return ExtConfig.INSTANCE.getChatMenuItemClearDeleted();
        if (position == chatMenuDeleteOwnRow) return ExtConfig.INSTANCE.getChatMenuItemDeleteOwnMessages();
        if (position == menuReactionsRow) return ExtConfig.INSTANCE.getShowReactions();
        if (position == menuReplyInPrivateRow) return ExtConfig.INSTANCE.getShowReplyInPrivate();
        if (position == menuCopyLinkRow) return ExtConfig.INSTANCE.getShowCopyLink();
        if (position == menuCopyFrameRow) return ExtConfig.INSTANCE.getShowCopyFrame();
        if (position == menuCopyAsStickerRow) return ExtConfig.INSTANCE.getShowCopyAsSticker();
        if (position == menuAddToStickersRow) return ExtConfig.INSTANCE.getShowAddToStickers();
        if (position == menuAddToFavoritesRow) return ExtConfig.INSTANCE.getShowAddToFavorites();
        if (position == menuNoQuoteForwardRow) return ExtConfig.INSTANCE.getShowNoQuoteForward();
        if (position == menuSetReminderRow) return ExtConfig.INSTANCE.getShowSetReminder();
        if (position == menuBookmarkRow) return ExtConfig.INSTANCE.getShowAddToBookmark();
        if (position == menuRepeatAsCopyRow) return ExtConfig.INSTANCE.getShowRepeatAsCopy();
        if (position == menuTranslateRow) return CoreConfig.showTranslate;
        if (position == menuTranslateLlmRow) return ExtConfig.INSTANCE.getShowTranslateMessageLLM();
        if (position == menuShareRow) return CoreConfig.showShareMessages;
        if (position == menuHideRow) return CoreConfig.showMessageHide;
        if (position == menuAdminActionsRow) return CoreConfig.showAdminActions;
        if (position == menuPermissionsRow) return CoreConfig.showChangePermissions;
        if (position == mediaMenuForwardRow) return ExtConfig.INSTANCE.getMediaViewerMenuItemForward();
        if (position == mediaMenuNoQuoteForwardRow) return ExtConfig.INSTANCE.getMediaViewerMenuItemNoQuoteForward();
        if (position == mediaMenuCopyFrameRow) return ExtConfig.INSTANCE.getMediaViewerMenuItemCopyFrame();
        if (position == mediaMenuCopyPhotoRow) return ExtConfig.INSTANCE.getMediaViewerMenuItemCopyPhoto();
        if (position == mediaMenuProfilePhotoRow) return ExtConfig.INSTANCE.getMediaViewerMenuItemSetProfilePhoto();
        if (position == mediaMenuQrRow) return ExtConfig.INSTANCE.getMediaViewerMenuItemScanQRCode();
        if (position == actionBarReplyRow) return ExtConfig.INSTANCE.getActionBarButtonReply();
        if (position == actionBarEditRow) return ExtConfig.INSTANCE.getActionBarButtonEdit();
        if (position == actionBarSelectBetweenRow) return ExtConfig.INSTANCE.getActionBarButtonSelectBetween();
        if (position == actionBarCopyRow) return ExtConfig.INSTANCE.getActionBarButtonCopy();
        if (position == actionBarForwardRow) return ExtConfig.INSTANCE.getActionBarButtonForward();
        if (position == groupedMessageMenuRow) return ExtConfig.INSTANCE.getGroupedMessageMenu();
        if (position == wideChannelPostsRow) return ChatsConfig.wideChannelPosts;
        if (position == wideFeedPostsRow) return ChatsConfig.wideFeedPosts;
        if (position == extendedFpsRow) return ChatsConfig.extendedFramesPerSecond;
        if (position == cameraStabilizationRow) return ChatsConfig.cameraStabilization;
        if (position == cameraMirrorModeRow) return ChatsConfig.cameraMirrorMode;
        if (position == startWithWideAngleRow) return ChatsConfig.startWithWideAngleCamera;
        if (position == rememberLastUsedCameraRow) return ChatsConfig.rememberLastUsedCamera;
        if (position == zoomSliderRow) return ChatsConfig.zoomSlider;
        if (position == staticZoomRow) return ChatsConfig.staticZoom;
        if (position == hideCameraTileRow) return ChatsConfig.hideCameraTile;
        if (position == hdrPhotosRow) return ChatsConfig.hdrPhotos;
        if (position == preferOriginalQualityRow) return ChatsConfig.preferOriginalQuality;
        if (position == swipeToPipRow) return ChatsConfig.swipeToPip;
        if (position == unmuteWithVolumeButtonsRow) return ChatsConfig.unmuteWithVolumeButtons;
        if (position == dontAutoplayWidgetsRow) return ChatsConfig.dontAutoplayWidgets;
        if (position == pauseVideoRow) return CoreConfig.autoPauseVideo;
        if (position == pauseVoiceRow) return ChatsConfig.pauseOnMinimizeVoice;
        if (position == pauseRoundRow) return ChatsConfig.pauseOnMinimizeRound;
        if (position == disableTrendingRow) return CoreConfig.disableTrending;
        if (position == hideGroupStickerRow) return CoreConfig.hideGroupSticker;
        if (position == lockedEmojiAsStickerRow) return ExtConfig.INSTANCE.getSendLockedCustomEmojiAsSticker();
        if (position == deleteChatForBothSidesRow) return ExtConfig.INSTANCE.getDeleteChatForBothSides();
        if (position == dateOfForwardedMsgRow) return ExtConfig.INSTANCE.getDateOfForwardedMsg();
        if (position == showTimeHintRow) return ExtConfig.INSTANCE.getShowTimeHint();
        if (position == rememberAllRepliesRow) return CoreConfig.rememberAllBackMessages;
        if (position == premiumEmojiStatusRow) return ExtConfig.INSTANCE.getPremiumItemEmojiStatus();
        if (position == premiumEmojiInRepliesRow) return ExtConfig.INSTANCE.getPremiumItemEmojiInReplies();
        if (position == premiumColorsInRepliesRow) return ExtConfig.INSTANCE.getPremiumItemCustomColorInReplies();
        if (position == premiumWallpapersRow) return ExtConfig.INSTANCE.getPremiumItemCustomWallpaper();
        if (position == premiumVideoAvatarsRow) return ExtConfig.INSTANCE.getPremiumItemVideoAvatar();
        if (position == premiumStarReactionsRow) return ExtConfig.INSTANCE.getPremiumItemStarInReactions();
        if (position == premiumStickerEffectsRow) return ExtConfig.INSTANCE.getPremiumItemStickerEffects();
        if (position == premiumBoostsRow) return ExtConfig.INSTANCE.getPremiumItemBoosts();
        if (position == deleteMenuBanUsersRow) return ExtConfig.INSTANCE.getDefaultDeleteMenuBanUsers();
        if (position == deleteMenuReportSpamRow) return ExtConfig.INSTANCE.getDefaultDeleteMenReportSpam();
        if (position == deleteMenuDeleteAllRow) return ExtConfig.INSTANCE.getDefaultDeleteMenuDeleteAll();
        if (position == deleteMenuCommonGroupsRow) return ExtConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups();
        if (position == fixLinkPreviewRow) return ExtConfig.INSTANCE.getFixLinkPreview();
        if (position == disableLinkPreviewRow) return CoreConfig.disableLinkPreviewByDefault;
        if (position == confirmAVMessageRow) return CoreConfig.confirmAVMessage;
        if (position == stripTrackingOpenRow) return ChatsConfig.stripTrackingOnOpen;
        if (position == stripTrackingPasteRow) return ChatsConfig.stripTrackingOnPaste;
        if (position == askBeforeCallRow) return CoreConfig.askBeforeCall;
        if (position == repeatConfirmRow) return CoreConfig.repeatConfirm;
        if (position == disableClickCommandToSendRow) return ExtConfig.INSTANCE.getDisableClickCommandToSend();
        if (position == disableInstantCameraRow) return CoreConfig.disableInstantCamera;
        if (position == showSmallGifRow) return ExtConfig.INSTANCE.getShowSmallGIF();
        if (position == dontAutoPlayNextVoiceRow) return ExtConfig.INSTANCE.getDontAutoPlayNextVoice();
        if (position == disableProximityEventsRow) return CoreConfig.disableProximityEvents;
        if (position == enhancedVideoBitrateRow) return ExtConfig.INSTANCE.getEnhancedVideoBitrate();
        if (position == voiceEnhancementsRow) return ExtConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance();
        if (position == hideActionBarStatusRow) return AppearanceConfig.hideActionBarStatus;
        if (position == hideFloatingButtonRow) return ExtConfig.INSTANCE.getDisableDialogsFloatingButton();
        if (position == hideSearchBarRow) return ExtConfig.INSTANCE.getHideDialogsSearchField();
        if (position == hideAllChatsRow) return CoreConfig.hideAllTab;
        if (position == hideAiEditorRow) return AppearanceConfig.hideAiEditor;
        if (position == hideAiSummaryRow) return AppearanceConfig.hideMessageSummary;
        if (position == hideAiIvRow) return AppearanceConfig.hideIvSummary;
        if (hideSettingsIndex(position) >= 0) return HiddenSettingsRows.items()[hideSettingsIndex(position)];
        return null;
    }

    /** Hide in Telegram's Settings: the index of the row's switch, or -1. */
    private int hideSettingsIndex(int position) {
        if (position < 0) {
            return -1;
        }
        for (int i = 0; i < hideSettingsRows.length; i++) {
            if (hideSettingsRows[i] == position) {
                return i;
            }
        }
        return -1;
    }

    /** Слайдер размера стикеров с живым превью переписки. */
    /**
     * Слайдер размера стикеров. Оформление перенесено из MZGram 12.9.0
     * (AltSeekbar): синий жирный заголовок 15sp, рядом плашка со значением
     * (12sp bold, фон — тот же цвет с alpha 0.15, скругление 4dp), под слайдером
     * серые подписи краёв 13sp. Диапазон 4..20.
     */
    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final SeekBarView sizeBar;
        private final TextView headerValue;
        private final int startStickerSize = 4;
        private final int endStickerSize = 20;

        public StickerSizeCell(Context context) {
            super(context);
            setWillNotDraw(false);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            title.setText(getString(R.string.StickerSize));
            header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            headerValue = new TextView(context);
            headerValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            headerValue.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            headerValue.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            headerValue.setPadding(dp(5.33f), dp(2), dp(5.33f), dp(2));
            headerValue.setBackground(Theme.createRoundRectDrawable(dp(4),
                    Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f)));
            header.addView(headerValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

            addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 17, 21, 0));

            FrameLayout edges = new FrameLayout(context);
            TextView left = new TextView(context);
            left.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            left.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            left.setText(getString(R.string.MZStickerSizeSmall));
            edges.addView(left, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            TextView right = new TextView(context);
            right.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            right.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            right.setText(getString(R.string.MZStickerSizeLarge));
            edges.addView(right, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            addView(edges, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 52, 21, 0));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endStickerSize - startStickerSize + 1);
            sizeBar.setDelegate((stop, progress) -> {
                CoreConfig.stickerSize.setConfigFloat(startStickerSize
                        + (endStickerSize - startStickerSize) * progress);
                updateValueText();
                StickerSizeCell.this.invalidate();
                showResetItem();
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38,
                    Gravity.LEFT | Gravity.TOP, 9, 78, 9, 0));

            messagesCell = new StickerSizePreviewMessagesCell(context, MZGramChatsActivity.this);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 126, 0, 0));

            updateValueText();
        }

        private void updateValueText() {
            headerValue.setText(String.valueOf(Math.round(CoreConfig.stickerSize.Float())));
        }

        /** Программная установка размера — ею пользуется кнопка сброса в шапке. */
        void setStickerSize(float size) {
            CoreConfig.stickerSize.setConfigFloat(size);
            sizeBar.setProgress((size - startStickerSize) / (float) (endStickerSize - startStickerSize));
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((CoreConfig.stickerSize.Float() - startStickerSize)
                    / (float) (endStickerSize - startStickerSize));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (messagesCell != null) messagesCell.invalidate();
            if (sizeBar != null) sizeBar.invalidate();
            updateValueText();
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_STICKER_SIZE:
                    stickerSizeCell = new StickerSizeCell(mContext);
                    stickerSizeCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = stickerSizeCell;
                    break;
                case TYPE_STICKER_SHAPE:
                    stickerShapeCell = new StickerShapeCell(mContext) {
                        @Override
                        protected void updateStickerPreview() {
                            if (stickerSizeCell != null) {
                                stickerSizeCell.invalidate();
                            }
                        }
                    };
                    view = stickerShapeCell;
                    break;
                case TYPE_DOUBLE_TAP:
                    doubleTapCell = new DoubleTapCell(mContext);
                    view = doubleTapCell;
                    break;
                case TYPE_WIDE_CHANNEL_PREVIEW:
                    wideChannelPostsPreviewCell = new WideChannelPostsPreviewCell(mContext,
                            MZGramChatsActivity.this);
                    wideChannelPostsPreviewCell.setOnResized(() -> {
                        if (listView != null && !listView.getSelectorRect().isEmpty()) {
                            listView.updateSelector();
                        }
                    });
                    view = wideChannelPostsPreviewCell;
                    break;
                case TYPE_SET_REACTION:
                    view = new DoubleTapCell.SetReactionCell(mContext);
                    break;
                case TYPE_EXPANDABLE_SWITCH:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_ROUND_CHECK: {
                    // Тип 4 — круглая галочка с отступом под вложенный пункт.
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, 4, 21, resourcesProvider);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked,
                            Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = checkBoxCell;
                    break;
                }
                default:
                    return super.onCreateViewHolder(parent, viewType);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_STICKER_SIZE || type == TYPE_STICKER_SHAPE || type == TYPE_DOUBLE_TAP
                    || type == TYPE_WIDE_CHANNEL_PREVIEW) {
                return false;
            }
            if (type == TYPE_SET_REACTION || type == TYPE_EXPANDABLE_SWITCH || type == TYPE_ROUND_CHECK) {
                return true;
            }
            return super.isEnabled(holder);
        }

        /**
         * Без этого карточка-секция обрывается на каждой строке-группе: базовый класс
         * знает только про свои типы, а 104/105 — обычные строки той же секции.
         */
        @Override
        protected boolean isSectionContent(int viewType) {
            if (viewType == TYPE_EXPANDABLE_SWITCH || viewType == TYPE_ROUND_CHECK
                    || viewType == TYPE_WIDE_CHANNEL_PREVIEW) {
                return true;
            }
            return super.isSectionContent(viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_SET_REACTION:
                    ((DoubleTapCell.SetReactionCell) holder.itemView).update(false);
                    break;
                case TYPE_WIDE_CHANNEL_PREVIEW:
                    ((WideChannelPostsPreviewCell) holder.itemView)
                            .setWide(ChatsConfig.wideChannelPosts.Bool(), false);
                    break;
                case TYPE_HEADER:
                    bindHeader((HeaderCell) holder.itemView, position);
                    break;
                case TYPE_CHECK:
                    bindCheck((TextCheckCell) holder.itemView, position);
                    break;
                case TYPE_EXPANDABLE_SWITCH:
                    bindGroupHeader((TextCheckCell2) holder.itemView, position);
                    break;
                case TYPE_ROUND_CHECK:
                    bindRoundCheck((CheckBoxCell) holder.itemView, position);
                    break;
                case TYPE_TEXT:
                    bindText((TextCell) holder.itemView, position);
                    break;
                case TYPE_SETTINGS:
                    bindSettings((TextSettingsCell) holder.itemView, position);
                    break;
                case TYPE_INFO_PRIVACY:
                    bindInfo((TextInfoPrivacyCell) holder.itemView, position);
                    break;
            }
        }

        private void bindHeader(HeaderCell cell, int position) {
            if (position == stickerShapeHeaderRow) {
                cell.setText(getString(R.string.MZChatsStickerShape));
            } else if (position == linksHeaderRow) {
                cell.setText(getString(R.string.MZChatsSections));
            } else if (position == stickersHeaderRow) {
                cell.setText(getString(R.string.MZChatsStickersAndEmoji));
            } else if (position == doubleTapHeaderRow) {
                cell.setText(getString(R.string.MZChatsDoubleTap));
            } else if (position == chatsHeaderRow) {
                cell.setText(getString(R.string.MZGramChats));
            } else if (position == messagesHeaderRow) {
                cell.setText(getString(R.string.MZChatsMessages));
            } else if (position == channelPostsHeaderRow) {
                cell.setText(getString(R.string.MZChatsChannelPosts));
            } else if (position == cameraHeaderRow) {
                cell.setText(getString(R.string.VoipCamera));
            } else if (position == photoHeaderRow) {
                cell.setText(getString(R.string.MZChatsPhoto));
            } else if (position == videosHeaderRow) {
                cell.setText(getString(R.string.MZChatsVideos));
            } else if (position == linkConfirmationsHeaderRow) {
                cell.setText(getString(R.string.MZChatsLinksAndConfirmations));
            } else if (position == transcribeHeaderRow) {
                cell.setText(getString(R.string.PremiumPreviewVoiceToText));
            } else if (position == inputHeaderRow) {
                cell.setText(getString(R.string.MZChatsInputHeader));
            } else if (position == searchHeaderRow) {
                cell.setText(getString(R.string.Search));
            } else if (position == buttonsHeaderRow) {
                cell.setText(getString(R.string.MZChatsButtonsHeader));
            } else if (position == menusHeaderRow) {
                cell.setText(getString(R.string.MZChatsMenusHeader));
            } else if (position == playbackHeaderRow) {
                cell.setText(getString(R.string.MZChatsPlaybackHeader));
            } else if (position == voiceHeaderRow) {
                cell.setText(getString(R.string.MZChatsVoiceHeader));
            } else if (position == hidingChatListHeaderRow) {
                cell.setText(getString(R.string.MZAppearanceChatList));
            } else if (position == hidingChatsHeaderRow) {
                cell.setText(getString(R.string.MZAppearanceHidingInChats));
            } else if (position == hidingStickersHeaderRow) {
                cell.setText(getString(R.string.StickersName));
            } else if (position == hidingAiHeaderRow) {
                cell.setText(getString(R.string.MZAppearanceHidingAiPremium));
            } else if (position == hidingSettingsHeaderRow) {
                cell.setText(getString(R.string.Settings));
            }
        }

        /**
         * Заголовок сворачиваемой группы: мастер-переключатель слева от шеврона,
         * счётчик «N/M» рядом с ним.
         */
        private void bindGroupHeader(TextCheckCell2 cell, int position) {
            // Иначе заголовок группы красный, пока в ней ничего не выбрано:
            // Switch по умолчанию идёт в «разрешительных» цветах экрана прав.
            cell.useStandardSwitchColors();
            // Третий аргумент setTextAndCheck — разделитель. У свёрнутой группы
            // заголовок оказывается последней строкой карточки, и линия под ним
            // висела бы в воздухе; поэтому разделитель = «группа раскрыта».
            if (position == repliesGroupRow) {
                int selected = repliesSelectedCount();
                cell.setTextAndCheck(getString(R.string.MZChatsReplies), selected > 0, repliesExpanded);
                cell.setCollapseArrow(ratio(selected, REPLIES_TOTAL), !repliesExpanded, sameGroup(cell, R.string.MZChatsReplies),
                        MZGramChatsActivity.this::toggleAllReplies);
            } else if (position == hideReactionsGroupRow) {
                int selected = hideReactionsSelectedCount();
                cell.setTextAndCheck(getString(R.string.MZChatsHideReactions), selected > 0, hideReactionsExpanded);
                cell.setCollapseArrow(ratio(selected, HIDE_REACTIONS_TOTAL), !hideReactionsExpanded, sameGroup(cell, R.string.MZChatsHideReactions),
                        MZGramChatsActivity.this::toggleAllHideReactions);
            } else if (position == unlimitedGroupRow) {
                int selected = unlimitedSelectedCount();
                cell.setTextAndCheck(getString(R.string.MZChatsUnlimited), selected > 0, unlimitedExpanded);
                cell.setCollapseArrow(ratio(selected, UNLIMITED_TOTAL), !unlimitedExpanded, sameGroup(cell, R.string.MZChatsUnlimited),
                        MZGramChatsActivity.this::toggleAllUnlimited);
            } else if (position == quickTransitionGroupRow) {
                int selected = quickTransitionsSelectedCount();
                cell.setTextAndCheck(getString(R.string.MZChatsQuickTransitions), selected > 0, quickTransitionExpanded);
                cell.setCollapseArrow(ratio(selected, QUICK_TRANSITIONS_TOTAL), !quickTransitionExpanded, sameGroup(cell, R.string.MZChatsQuickTransitions),
                        MZGramChatsActivity.this::toggleAllQuickTransitions);
            } else if (position == chatMenuGroupRow) {
                int selected = chatMenuSelectedCount();
                cell.setTextAndCheck(getString(R.string.ChatMenu), selected > 0, chatMenuExpanded);
                cell.setCollapseArrow(ratio(selected, CHAT_MENU_TOTAL), !chatMenuExpanded, sameGroup(cell, R.string.ChatMenu),
                        MZGramChatsActivity.this::toggleAllChatMenu);
            } else if (position == messageMenuGroupRow) {
                int selected = messageMenuSelectedCount();
                cell.setTextAndCheck(getString(R.string.MessageMenu), selected > 0, messageMenuExpanded);
                cell.setCollapseArrow(ratio(selected, MESSAGE_MENU_TOTAL), !messageMenuExpanded, sameGroup(cell, R.string.MessageMenu),
                        MZGramChatsActivity.this::toggleAllMessageMenu);
            } else if (position == mediaViewerMenuGroupRow) {
                int selected = mediaViewerMenuSelectedCount();
                cell.setTextAndCheck(getString(R.string.MediaViewerMenu), selected > 0, mediaViewerMenuExpanded);
                cell.setCollapseArrow(ratio(selected, MEDIA_VIEWER_MENU_TOTAL), !mediaViewerMenuExpanded, sameGroup(cell, R.string.MediaViewerMenu),
                        MZGramChatsActivity.this::toggleAllMediaViewerMenu);
            } else if (position == actionBarButtonsGroupRow) {
                int selected = actionBarButtonsSelectedCount();
                cell.setTextAndCheck(getString(R.string.ActionBarButtons), selected > 0, actionBarButtonsExpanded);
                cell.setCollapseArrow(ratio(selected, ACTION_BAR_BUTTONS_TOTAL), !actionBarButtonsExpanded, sameGroup(cell, R.string.ActionBarButtons),
                        MZGramChatsActivity.this::toggleAllActionBarButtons);
            } else if (position == extendedSettingsGroupRow) {
                int selected = cameraSettingsSelected();
                cell.setTextAndCheck(getString(R.string.MZChatsExtendedSettings), selected > 0, extendedSettingsExpanded);
                cell.setCollapseArrow(ratio(selected, cameraSettingsTotal()), !extendedSettingsExpanded, sameGroup(cell, R.string.MZChatsExtendedSettings),
                        MZGramChatsActivity.this::toggleAllCameraSettings);
            } else if (position == pauseGroupRow) {
                int selected = pauseSelectedCount();
                cell.setTextAndCheck(getString(R.string.MZChatsPauseOnMinimize), selected > 0, pauseExpanded);
                cell.setCollapseArrow(ratio(selected, PAUSE_TOTAL), !pauseExpanded, sameGroup(cell, R.string.MZChatsPauseOnMinimize),
                        MZGramChatsActivity.this::toggleAllPause);
            } else if (position == premiumElementsGroupRow) {
                ConfigItem[] items = premiumElementItems();
                int selected = selectedCount(items);
                cell.setTextAndCheck(getString(R.string.PremiumElements), selected > 0, premiumElementsExpanded);
                cell.setCollapseArrow(ratio(selected, items.length), !premiumElementsExpanded, sameGroup(cell, R.string.PremiumElements),
                        MZGramChatsActivity.this::toggleAllPremiumElements);
            } else if (position == deleteMenuGroupRow) {
                ConfigItem[] items = deleteMenuItems();
                int selected = selectedCount(items);
                cell.setTextAndCheck(getString(R.string.DefaultDeleteMenu), selected > 0, deleteMenuExpanded);
                cell.setCollapseArrow(ratio(selected, items.length), !deleteMenuExpanded, sameGroup(cell, R.string.DefaultDeleteMenu),
                        MZGramChatsActivity.this::toggleAllDeleteMenu);
            } else if (position == askWhenGroupRow) {
                ConfigItem[] items = askWhenItems();
                int selected = selectedCount(items);
                cell.setTextAndCheck(getString(R.string.MZChatsAskWhen), selected > 0, askWhenExpanded);
                cell.setCollapseArrow(ratio(selected, items.length), !askWhenExpanded, sameGroup(cell, R.string.MZChatsAskWhen),
                        MZGramChatsActivity.this::toggleAllAskWhen);
            } else if (position == stripTrackingGroupRow) {
                ConfigItem[] items = stripTrackingItems();
                int selected = selectedCount(items);
                cell.setTextAndCheck(getString(R.string.MZChatsStripTracking), selected > 0, stripTrackingExpanded);
                cell.setCollapseArrow(ratio(selected, items.length), !stripTrackingExpanded, sameGroup(cell, R.string.MZChatsStripTracking),
                        MZGramChatsActivity.this::toggleAllStripTracking);
            } else if (position == hideAiGroupRow) {
                ConfigItem[] items = hideAiItems();
                int selected = selectedCount(items);
                cell.setTextAndCheck(getString(R.string.MZAppearanceHideAi), selected > 0, true);
                cell.setCollapseArrow(ratio(selected, items.length), !hideAiExpanded, sameGroup(cell, R.string.MZAppearanceHideAi),
                        MZGramChatsActivity.this::toggleAllHideAi);
            } else if (position == hideSettingsGroupRow) {
                ConfigItem[] items = hideSettingsItems();
                int selected = selectedCount(items);
                cell.setTextAndCheck(getString(R.string.MZAppearanceHideSettingsSections), selected > 0, hideSettingsExpanded);
                cell.setCollapseArrow(ratio(selected, items.length), !hideSettingsExpanded, sameGroup(cell, R.string.MZAppearanceHideSettingsSections),
                        MZGramChatsActivity.this::toggleAllHideSettings);
            }
        }

        private boolean sameGroup(TextCheckCell2 cell, int titleRes) {
            final boolean same = Integer.valueOf(titleRes).equals(cell.getTag());
            cell.setTag(titleRes);
            return same;
        }

        private void bindRoundCheck(CheckBoxCell cell, int position) {
            if (position == replyColorsRow) {
                cell.setText(getString(R.string.MZChatsReplyColors), "", ChatsConfig.replyColors.Bool(), true, true);
            } else if (position == replyEmojiRow) {
                cell.setText(getString(R.string.MZChatsReplyEmoji), "", ChatsConfig.replyEmoji.Bool(), true, true);
            } else if (position == replyBackgroundRow) {
                cell.setText(getString(R.string.MZChatsReplyBackground), "", ChatsConfig.replyBackground.Bool(), false, true);
            } else if (position == hideReactionsChannelsRow) {
                cell.setText(getString(R.string.MZChatsHideReactionsChannels), "", ChatsConfig.hideReactionsInChannels.Bool(), true, true);
            } else if (position == hideReactionsGroupsRow) {
                cell.setText(getString(R.string.MZChatsHideReactionsGroups), "", ChatsConfig.hideReactionsInGroups.Bool(), true, true);
            } else if (position == hideReactionsPrivateRow) {
                cell.setText(getString(R.string.MZChatsHideReactionsPrivate), "", ChatsConfig.hideReactionsInPrivate.Bool(), true, true);
            } else if (position == unlimitedStickersRow) {
                cell.setText(getString(R.string.MZChatsUnlimitedStickers), "", isUnlimitedRecentStickers(), true, true);
            } else if (position == unlimitedGifsRow) {
                cell.setText(getString(R.string.MZChatsUnlimitedGifs), "", ExtConfig.INSTANCE.getUnlimitedSavedGifs().Bool(), false, true);
            } else if (position == quickTransitionChannelsRow) {
                cell.setText(getString(R.string.MZChatsQuickTransitionChannels), "", quickTransitionForChannels(), true, true);
            } else if (position == quickTransitionTopicsRow) {
                cell.setText(getString(R.string.MZChatsQuickTransitionTopics), "", quickTransitionForTopics(), true, true);
            } else if (position == menuCopyPhotoRow) {
                cell.setText(getString(R.string.MZChatsMenuCopyPhoto), "", ExtConfig.INSTANCE.getShowCopyPhoto().Bool(), true, true);
            } else if (position == menuSaveRow) {
                cell.setText(getString(R.string.MZChatsMenuSave), "", CoreConfig.showAddToSavedMessages.Bool(), true, true);
            } else if (position == menuRepeatRow) {
                cell.setText(getString(R.string.MZChatsMenuRepeat), "", CoreConfig.showRepeat.Bool(), true, true);
            } else if (position == menuClearRow) {
                cell.setText(getString(R.string.MZChatsMenuClear), "", CoreConfig.showDeleteDownloadedFile.Bool(), true, true);
            } else if (position == menuHistoryRow) {
                cell.setText(getString(R.string.MZChatsMenuHistory), "", CoreConfig.showViewHistory.Bool(), true, true);
            } else if (position == menuReportRow) {
                cell.setText(getString(R.string.MZChatsMenuReport), "", CoreConfig.showReport.Bool(), true, true);
            } else if (position == menuDetailsRow) {
                cell.setText(getString(R.string.MZChatsMenuDetails), "", CoreConfig.showMessageDetails.Bool(), true, true);
            } else if (position == chatMenuAdminsRow) {
                cell.setText(getString(R.string.ChannelAdministrators), "", ExtConfig.INSTANCE.getShortcutsAdministrators().Bool(), true, true);
            } else if (position == chatMenuRecentActionsRow) {
                cell.setText(getString(R.string.EventLog), "", ExtConfig.INSTANCE.getShortcutsRecentActions().Bool(), true, true);
            } else if (position == chatMenuStatisticsRow) {
                cell.setText(getString(R.string.Statistics), "", ExtConfig.INSTANCE.getShortcutsStatistics().Bool(), true, true);
            } else if (position == chatMenuPermissionsRow) {
                cell.setText(getString(R.string.ChannelPermissions), "", ExtConfig.INSTANCE.getShortcutsPermissions().Bool(), true, true);
            } else if (position == chatMenuMembersRow) {
                cell.setText(getString(R.string.GroupMembers), "", ExtConfig.INSTANCE.getShortcutsMembers().Bool(), true, true);
            } else if (position == chatMenuBoostRow) {
                cell.setText(getString(R.string.BoostingBoostGroupMenu), "", ExtConfig.INSTANCE.getChatMenuItemBoostGroup().Bool(), true, true);
            } else if (position == chatMenuLinkedChatRow) {
                cell.setText(getString(R.string.LinkedGroupChat), "", ExtConfig.INSTANCE.getChatMenuItemLinkedChat().Bool(), true, true);
            } else if (position == chatMenuToBeginningRow) {
                cell.setText(getString(R.string.ToTheBeginning), "", ExtConfig.INSTANCE.getChatMenuItemToBeginning().Bool(), true, true);
            } else if (position == chatMenuGoToMessageRow) {
                cell.setText(getString(R.string.ToTheMessage), "", ExtConfig.INSTANCE.getChatMenuItemGoToMessage().Bool(), true, true);
            } else if (position == chatMenuHideTitleRow) {
                cell.setText(getString(R.string.HideTitle), "", ExtConfig.INSTANCE.getChatMenuItemHideTitle().Bool(), true, true);
            } else if (position == chatMenuViewDeletedRow) {
                cell.setText(getString(R.string.ViewDeleted), "", ExtConfig.INSTANCE.getChatMenuItemViewDeleted().Bool(), true, true);
            } else if (position == chatMenuClearDeletedRow) {
                cell.setText(getString(R.string.ClearDeleted), "", ExtConfig.INSTANCE.getChatMenuItemClearDeleted().Bool(), true, true);
            } else if (position == chatMenuDeleteOwnRow) {
                cell.setText(getString(R.string.DeleteAllFromSelf), "", ExtConfig.INSTANCE.getChatMenuItemDeleteOwnMessages().Bool(), true, true);
            } else if (position == menuReactionsRow) {
                cell.setText(getString(R.string.Reactions), "", ExtConfig.INSTANCE.getShowReactions().Bool(), true, true);
            } else if (position == menuReplyInPrivateRow) {
                cell.setText(getString(R.string.ReplyInPrivate), "", ExtConfig.INSTANCE.getShowReplyInPrivate().Bool(), true, true);
            } else if (position == menuCopyLinkRow) {
                cell.setText(getString(R.string.CopyLink), "", ExtConfig.INSTANCE.getShowCopyLink().Bool(), true, true);
            } else if (position == menuCopyFrameRow) {
                cell.setText(getString(R.string.CopyVideoFrame), "", ExtConfig.INSTANCE.getShowCopyFrame().Bool(), true, true);
            } else if (position == menuCopyAsStickerRow) {
                cell.setText(getString(R.string.CopyPhotoAsSticker), "", ExtConfig.INSTANCE.getShowCopyAsSticker().Bool(), true, true);
            } else if (position == menuAddToStickersRow) {
                cell.setText(getString(R.string.AddToStickers), "", ExtConfig.INSTANCE.getShowAddToStickers().Bool(), true, true);
            } else if (position == menuAddToFavoritesRow) {
                cell.setText(getString(R.string.AddToFavorites), "", ExtConfig.INSTANCE.getShowAddToFavorites().Bool(), true, true);
            } else if (position == menuNoQuoteForwardRow) {
                cell.setText(getString(R.string.NoQuoteForward), "", ExtConfig.INSTANCE.getShowNoQuoteForward().Bool(), true, true);
            } else if (position == menuSetReminderRow) {
                cell.setText(getString(R.string.SetReminder), "", ExtConfig.INSTANCE.getShowSetReminder().Bool(), true, true);
            } else if (position == menuBookmarkRow) {
                cell.setText(getString(R.string.AddBookmark), "", ExtConfig.INSTANCE.getShowAddToBookmark().Bool(), true, true);
            } else if (position == menuRepeatAsCopyRow) {
                cell.setText(getString(R.string.RepeatAsCopy), "", ExtConfig.INSTANCE.getShowRepeatAsCopy().Bool(), true, true);
            } else if (position == menuTranslateRow) {
                cell.setText(getString(R.string.Translate), "", CoreConfig.showTranslate.Bool(), true, true);
            } else if (position == menuTranslateLlmRow) {
                cell.setText(getString(R.string.TranslateMessageLLM), "", ExtConfig.INSTANCE.getShowTranslateMessageLLM().Bool(), true, true);
            } else if (position == menuShareRow) {
                cell.setText(getString(R.string.ShareMessages), "", CoreConfig.showShareMessages.Bool(), true, true);
            } else if (position == menuHideRow) {
                cell.setText(getString(R.string.Hide), "", CoreConfig.showMessageHide.Bool(), true, true);
            } else if (position == menuAdminActionsRow) {
                cell.setText(getString(R.string.EditAdminRights), "", CoreConfig.showAdminActions.Bool(), true, true);
            } else if (position == menuPermissionsRow) {
                cell.setText(getString(R.string.ChangePermissions), "", CoreConfig.showChangePermissions.Bool(), true, true);
            } else if (position == mediaMenuForwardRow) {
                cell.setText(getString(R.string.Forward), "", ExtConfig.INSTANCE.getMediaViewerMenuItemForward().Bool(), true, true);
            } else if (position == mediaMenuNoQuoteForwardRow) {
                cell.setText(getString(R.string.NoQuoteForward), "", ExtConfig.INSTANCE.getMediaViewerMenuItemNoQuoteForward().Bool(), true, true);
            } else if (position == mediaMenuCopyFrameRow) {
                cell.setText(getString(R.string.CopyVideoFrame), "", ExtConfig.INSTANCE.getMediaViewerMenuItemCopyFrame().Bool(), true, true);
            } else if (position == mediaMenuCopyPhotoRow) {
                cell.setText(getString(R.string.CopyPhoto), "", ExtConfig.INSTANCE.getMediaViewerMenuItemCopyPhoto().Bool(), true, true);
            } else if (position == mediaMenuProfilePhotoRow) {
                cell.setText(getString(R.string.SetProfilePhoto), "", ExtConfig.INSTANCE.getMediaViewerMenuItemSetProfilePhoto().Bool(), true, true);
            } else if (position == mediaMenuQrRow) {
                cell.setText(getString(R.string.ScanQRCode), "", ExtConfig.INSTANCE.getMediaViewerMenuItemScanQRCode().Bool(), true, true);
            } else if (position == actionBarReplyRow) {
                cell.setText(getString(R.string.Reply), "", ExtConfig.INSTANCE.getActionBarButtonReply().Bool(), true, true);
            } else if (position == actionBarEditRow) {
                cell.setText(getString(R.string.Edit), "", ExtConfig.INSTANCE.getActionBarButtonEdit().Bool(), true, true);
            } else if (position == actionBarSelectBetweenRow) {
                cell.setText(getString(R.string.SelectBetween), "", ExtConfig.INSTANCE.getActionBarButtonSelectBetween().Bool(), true, true);
            } else if (position == actionBarCopyRow) {
                cell.setText(getString(R.string.Copy), "", ExtConfig.INSTANCE.getActionBarButtonCopy().Bool(), true, true);
            } else if (position == actionBarForwardRow) {
                cell.setText(getString(R.string.Forward), "", ExtConfig.INSTANCE.getActionBarButtonForward().Bool(), true, true);
            } else if (position == seamlessSwitchingRow) {
                cell.setText(getString(R.string.MZChatsSeamlessSwitching), "", isSeamlessSwitchingEnabled(), true, true);
            } else if (position == extendedFpsRow) {
                cell.setText(getString(R.string.MZChatsExtendedFps), "", ChatsConfig.extendedFramesPerSecond.Bool(), true, true);
            } else if (position == cameraStabilizationRow) {
                cell.setText(getString(R.string.MZChatsCameraStabilization), "", ChatsConfig.cameraStabilization.Bool(), true, true);
            } else if (position == cameraMirrorModeRow) {
                cell.setText(getString(R.string.MZChatsCameraMirrorMode), "", ChatsConfig.cameraMirrorMode.Bool(), true, true);
            } else if (position == startWithWideAngleRow) {
                cell.setText(getString(R.string.MZChatsStartWithWideAngle), "", ChatsConfig.startWithWideAngleCamera.Bool(), true, true);
            } else if (position == pauseVideoRow) {
                cell.setText(getString(R.string.MZChatsPauseVideo), "", CoreConfig.autoPauseVideo.Bool(), true, true);
            } else if (position == pauseVoiceRow) {
                cell.setText(getString(R.string.MZChatsPauseVoice), "", ChatsConfig.pauseOnMinimizeVoice.Bool(), true, true);
            } else if (position == pauseRoundRow) {
                cell.setText(getString(R.string.MZChatsPauseRound), "", ChatsConfig.pauseOnMinimizeRound.Bool(), false, true);
            } else if (position == premiumEmojiStatusRow) {
                cell.setText(getString(R.string.PremiumItemEmojiStatus), "", ExtConfig.INSTANCE.getPremiumItemEmojiStatus().Bool(), true, true);
            } else if (position == premiumEmojiInRepliesRow) {
                cell.setText(getString(R.string.PremiumItemEmojiInReplies), "", ExtConfig.INSTANCE.getPremiumItemEmojiInReplies().Bool(), true, true);
            } else if (position == premiumColorsInRepliesRow) {
                cell.setText(getString(R.string.PremiumItemCustomColorInReplies), "", ExtConfig.INSTANCE.getPremiumItemCustomColorInReplies().Bool(), true, true);
            } else if (position == premiumWallpapersRow) {
                cell.setText(getString(R.string.PremiumItemCustomWallpaper), "", ExtConfig.INSTANCE.getPremiumItemCustomWallpaper().Bool(), true, true);
            } else if (position == premiumVideoAvatarsRow) {
                cell.setText(getString(R.string.PremiumItemVideoAvatar), "", ExtConfig.INSTANCE.getPremiumItemVideoAvatar().Bool(), true, true);
            } else if (position == premiumStarReactionsRow) {
                cell.setText(getString(R.string.PremiumItemStarInReactions), "", ExtConfig.INSTANCE.getPremiumItemStarInReactions().Bool(), true, true);
            } else if (position == premiumStickerEffectsRow) {
                cell.setText(getString(R.string.PremiumItemStickerEffects), "", ExtConfig.INSTANCE.getPremiumItemStickerEffects().Bool(), true, true);
            } else if (position == premiumBoostsRow) {
                cell.setText(getString(R.string.PremiumItemBoosts), "", ExtConfig.INSTANCE.getPremiumItemBoosts().Bool(), false, true);
            } else if (position == deleteMenuBanUsersRow) {
                cell.setText(getString(R.string.DeleteBanUsers), "", ExtConfig.INSTANCE.getDefaultDeleteMenuBanUsers().Bool(), true, true);
            } else if (position == deleteMenuReportSpamRow) {
                cell.setText(getString(R.string.DeleteReportSpam), "", ExtConfig.INSTANCE.getDefaultDeleteMenReportSpam().Bool(), true, true);
            } else if (position == deleteMenuDeleteAllRow) {
                cell.setText(getString(R.string.DeleteAll), "", ExtConfig.INSTANCE.getDefaultDeleteMenuDeleteAll().Bool(), true, true);
            } else if (position == deleteMenuCommonGroupsRow) {
                cell.setText(getString(R.string.DoActionsInCommonGroups), "", ExtConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups().Bool(), false, true);
            } else if (position == confirmAVMessageRow) {
                cell.setText(getString(R.string.MZChatsAskWhenVoiceVideo), "", CoreConfig.confirmAVMessage.Bool(), true, true);
            } else if (position == askBeforeCallRow) {
                cell.setText(getString(R.string.MZChatsAskWhenCalling), "", CoreConfig.askBeforeCall.Bool(), true, true);
            } else if (position == repeatConfirmRow) {
                cell.setText(getString(R.string.MZChatsAskWhenRepeating), "", CoreConfig.repeatConfirm.Bool(), true, true);
            } else if (position == disableClickCommandToSendRow) {
                cell.setText(getString(R.string.MZChatsAskWhenBotCommands), "", ExtConfig.INSTANCE.getDisableClickCommandToSend().Bool(), false, true);
            } else if (position == stripTrackingOpenRow) {
                cell.setText(getString(R.string.MZChatsStripTrackingOnOpen), "", ChatsConfig.stripTrackingOnOpen.Bool(), true, true);
            } else if (position == stripTrackingPasteRow) {
                cell.setText(getString(R.string.MZChatsStripTrackingOnPaste), "", ChatsConfig.stripTrackingOnPaste.Bool(), false, true);
            } else if (position == hideAiEditorRow) {
                cell.setText(getString(R.string.MZAppearanceHideAiEditor), "", AppearanceConfig.hideAiEditor.Bool(), true, true);
            } else if (position == hideAiSummaryRow) {
                cell.setText(getString(R.string.MZAppearanceHideAiSummary), "", AppearanceConfig.hideMessageSummary.Bool(), true, true);
            } else if (position == hideAiIvRow) {
                cell.setText(getString(R.string.MZAppearanceHideAiIv), "", AppearanceConfig.hideIvSummary.Bool(), true, true);
            } else if (hideSettingsIndex(position) >= 0) {
                final int i = hideSettingsIndex(position);
                cell.setText(getString(HiddenSettingsRows.TITLES[i]), "", HiddenSettingsRows.items()[i].Bool(), i < hideSettingsRows.length - 1, true);
            }
            cell.setPad(1);
            // По умолчанию ячейка этого типа красит текст серым; вложенные пункты
            // должны быть того же цвета, что и обычные строки.
            cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        }

        private void bindCheck(TextCheckCell cell, int position) {
            if (position == adminShortcutsRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsAdminShortcuts), isQuickAdminShortcuts(), false);
            } else if (position == disableGreetingRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsDisableGreetingSticker), CoreConfig.dontSendGreetingSticker.Bool(), false);
            } else if (position == hideKeyboardOnScrollRow) {
                cell.setTextAndCheck(getString(R.string.HideKeyboardOnChatScroll), CoreConfig.hideKeyboardOnChatScroll.Bool(), false);
            } else if (position == disableGlobalSearchRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsDisableGlobalSearch), ExtConfig.INSTANCE.getDisableGlobalSearch().Bool(), true);
            } else if (position == addCommaRow) {
                cell.setTextAndCheck(getString(R.string.AddCommaAfterMention), MZGramConfig.addCommaAfterMention.Bool(), true);
            } else if (position == inlineMathRow) {
                cell.setTextAndValueAndCheck(getString(R.string.MZChatsInlineMath),
                        getString(R.string.MZChatsInlineMathInfo),
                        ChatsConfig.inlineMathResult.Bool(), true, true);
            } else if (position == hideSendAsPeerRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsHideSendAsPeer), CoreConfig.hideSendAsChannel.Bool(), true);
            } else if (position == tapToSwitchRecordRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsTapToSwitchRecord), !CoreConfig.useChatAttachMediaMenu.Bool(), true);
            } else if (position == keepAttachButtonRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsKeepAttachButton), ChatsConfig.keepAttachButton.Bool(), false);
            } else if (position == removeMessageTailRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsRemoveMessageTail), ChatsConfig.removeMessageTail.Bool(), true);
            } else if (position == unreadCapsuleRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsUnreadCapsule), ChatsConfig.unreadCapsule.Bool(), true);
            } else if (position == replaceEditedRow) {
                // В подпись подставляется локализованное «edited».
                cell.setTextAndCheck(LocaleController.formatString(R.string.MZChatsReplaceEditedWithIcon,
                        getString(R.string.EditedMessage)), ExtConfig.INSTANCE.getUseEditedIcon().Bool(), true);
            } else if (position == showOnlineStatusRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsShowOnlineStatus), ExtConfig.INSTANCE.getShowOnlineStatus().Bool(), true);
            } else if (position == hideShareButtonRow) {
                // В подпись подставляется название кнопки «Share».
                cell.setTextAndCheck(LocaleController.formatString(R.string.MZChatsHideShareButton,
                        getString(R.string.ShareFile)), ExtConfig.INSTANCE.getHideShareButtonInChannel().Bool(), true);
            } else if (position == hideGiftButtonRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsHideGiftButton),
                        ExtConfig.INSTANCE.getHideGiftButtonInChannel().Bool(), true);
            } else if (position == hideSearchButtonRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsHideSearchButton),
                        ChatsConfig.hideChannelSearchButton.Bool(), true);
            } else if (position == hideCallButtonRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsHideCallButton),
                        ChatsConfig.hideCallButton.Bool(), true);
            } else if (position == hideMoneyButtonRow) {
                cell.setTextAndCheck(LocaleController.formatString(R.string.MZChatsHideMoneyButton,
                        getString(R.string.WalletAttachMoney)), ChatsConfig.hideMoneyButton.Bool(), true);
            } else if (position == showResultsBeforeVotingRow) {
                cell.setTextAndValueAndCheck(getString(R.string.MZChatsShowResultsBeforeVoting),
                        getString(R.string.MZChatsShowResultsBeforeVotingInfo),
                        ChatsConfig.showResultsBeforeVoting.Bool(), true, true);
            } else if (position == wideChannelPostsRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsWideChannelPosts),
                        ChatsConfig.wideChannelPosts.Bool(), true, true);
            } else if (position == wideFeedPostsRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsWideFeedPosts),
                        ChatsConfig.wideFeedPosts.Bool(), false, true);
            } else if (position == groupedMessageMenuRow) {
                cell.setTextAndCheck(getString(R.string.GroupedMessageMenu), ExtConfig.INSTANCE.getGroupedMessageMenu().Bool(), true);
            } else if (position == rememberLastUsedCameraRow) {
                cell.setTextAndValueAndCheck(getString(R.string.MZChatsRememberLastUsedCamera),
                        getString(R.string.MZChatsRememberLastUsedCameraInfo),
                        ChatsConfig.rememberLastUsedCamera.Bool(), true, true);
            } else if (position == zoomSliderRow) {
                cell.setTextAndValueAndCheck(getString(R.string.MZChatsZoomSlider),
                        getString(R.string.MZChatsZoomSliderInfo),
                        ChatsConfig.zoomSlider.Bool(), true, true);
            } else if (position == staticZoomRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsStaticZoom), ChatsConfig.staticZoom.Bool(), false);
            } else if (position == alwaysSendHdRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsAlwaysSendInHD), ChatsConfig.alwaysSendInHD.Bool(), true);
            } else if (position == hdrPhotosRow) {
                cell.setTextAndValueAndCheck(getString(R.string.MZChatsHdrPhotos),
                        getString(R.string.MZChatsHdrPhotosInfo),
                        ChatsConfig.hdrPhotos.Bool(), true, true);
            } else if (position == hideCameraTileRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsHideCameraTile), ChatsConfig.hideCameraTile.Bool(), false);
            } else if (position == preferOriginalQualityRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsPreferOriginalQuality), ChatsConfig.preferOriginalQuality.Bool(), true);
            } else if (position == swipeToPipRow) {
                cell.setTextAndCheck(getString(R.string.MZChatsSwipeToPip), ChatsConfig.swipeToPip.Bool(), true);
            } else if (position == unmuteWithVolumeButtonsRow) {
                cell.setTextAndValueAndCheck(getString(R.string.MZChatsUnmuteWithVolumeButtons),
                        getString(R.string.MZChatsUnmuteWithVolumeButtonsInfo),
                        ChatsConfig.unmuteWithVolumeButtons.Bool(), true, true);
            } else if (position == dontAutoplayWidgetsRow) {
                cell.setTextAndValueAndCheck(getString(R.string.MZChatsDontAutoplayWidgets),
                        getString(R.string.MZChatsDontAutoplayWidgetsInfo),
                        ChatsConfig.dontAutoplayWidgets.Bool(), true, true);
            } else if (position == disableTrendingRow) {
                cell.setTextAndCheck(getString(R.string.DisableTrending), CoreConfig.disableTrending.Bool(), true);
            } else if (position == hideGroupStickerRow) {
                cell.setTextAndCheck(getString(R.string.hideGroupSticker), CoreConfig.hideGroupSticker.Bool(), true);
            } else if (position == lockedEmojiAsStickerRow) {
                cell.setTextAndValueAndCheck(getString(R.string.SendLockedCustomEmojiAsSticker),
                        getString(R.string.SendLockedCustomEmojiAsStickerInfo),
                        ExtConfig.INSTANCE.getSendLockedCustomEmojiAsSticker().Bool(), true, true);
            } else if (position == deleteChatForBothSidesRow) {
                cell.setTextAndCheck(getString(R.string.DeleteChatForBothSides), ExtConfig.INSTANCE.getDeleteChatForBothSides().Bool(), true);
            } else if (position == dateOfForwardedMsgRow) {
                cell.setTextAndCheck(getString(R.string.DateOfForwardedMsg), ExtConfig.INSTANCE.getDateOfForwardedMsg().Bool(), true);
            } else if (position == showTimeHintRow) {
                cell.setTextAndCheck(getString(R.string.ShowTimeHint), ExtConfig.INSTANCE.getShowTimeHint().Bool(), true);
            } else if (position == rememberAllRepliesRow) {
                cell.setTextAndCheck(getString(R.string.rememberAllBackMessages), CoreConfig.rememberAllBackMessages.Bool(), false);
            } else if (position == fixLinkPreviewRow) {
                cell.setTextAndValueAndCheck(getString(R.string.FixLinkPreview), "x.com → fixupx.com",
                        ExtConfig.INSTANCE.getFixLinkPreview().Bool(), false, true);
            } else if (position == disableLinkPreviewRow) {
                cell.setTextAndCheck(getString(R.string.DisableLinkPreviewByDefault), CoreConfig.disableLinkPreviewByDefault.Bool(), true);
            } else if (position == disableInstantCameraRow) {
                cell.setTextAndCheck(getString(R.string.DisableInstantCamera), CoreConfig.disableInstantCamera.Bool(), false);
            } else if (position == showSmallGifRow) {
                cell.setTextAndCheck(getString(R.string.ShowSmallGIF), ExtConfig.INSTANCE.getShowSmallGIF().Bool(), false);
            } else if (position == dontAutoPlayNextVoiceRow) {
                cell.setTextAndCheck(getString(R.string.DontAutoPlayNextVoice), ExtConfig.INSTANCE.getDontAutoPlayNextVoice().Bool(), true);
            } else if (position == disableProximityEventsRow) {
                cell.setTextAndCheck(getString(R.string.DisableProximityEvents), CoreConfig.disableProximityEvents.Bool(), false);
            } else if (position == enhancedVideoBitrateRow) {
                cell.setTextAndCheck(getString(R.string.EnhancedVideoBitrate), ExtConfig.INSTANCE.getEnhancedVideoBitrate().Bool(), true);
            } else if (position == voiceEnhancementsRow) {
                cell.setTextAndCheck(getString(R.string.NoiseSuppressAndVoiceEnhance),
                        ExtConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance().Bool(), true);
            } else if (position == hideActionBarStatusRow) {
                cell.setTextAndCheck(getString(R.string.MZAppearanceHideActionBarStatus), AppearanceConfig.hideActionBarStatus.Bool(), true);
            } else if (position == hideFloatingButtonRow) {
                cell.setTextAndCheck(getString(R.string.MZAppearanceHideFloatingButton),
                        ExtConfig.INSTANCE.getDisableDialogsFloatingButton().Bool(), true);
            } else if (position == hideSearchBarRow) {
                cell.setTextAndCheck(getString(R.string.MZAppearanceHideSearchBar), ExtConfig.INSTANCE.getHideDialogsSearchField().Bool(), true);
            } else if (position == hideAllChatsRow) {
                cell.setTextAndCheck(LocaleController.formatString(R.string.MZAppearanceHideAllChats,
                        getString(R.string.FilterAllChats)), CoreConfig.hideAllTab.Bool(), false);
            }
        }

        private void bindText(TextCell cell, int position) {
            if (position == inlineMathSettingsRow) {
                cell.setTextAndIcon(getString(R.string.MZInlineTitle), R.drawable.msg_settings, true);
                cell.setSubtitle(getString(R.string.MZInlineTitleInfo));
            } else if (position == menusRow) {
                cell.setTextAndIcon(getString(R.string.MZChatsMenus), R.drawable.msg_list, true);
                cell.setSubtitle(getString(R.string.MZChatsMenusInfo));
            } else if (position == mediaRow) {
                cell.setTextAndIcon(getString(R.string.MZChatsMedia), R.drawable.msg_camera, true);
                cell.setSubtitle(getString(R.string.MZChatsMediaInfo));
            } else if (position == aiChatRow) {
                cell.setTextAndIcon(getString(R.string.MZChatsAiChat), aiChatIcon(), true);
                cell.setSubtitle(getString(R.string.MZChatsAiChatInfo));
            } else if (position == chatSettingsRow) {
                cell.setTextAndIcon(getString(R.string.MZChatsChatSettings), R.drawable.msg_settings_old, false);
                cell.setSubtitle(getString(R.string.MZChatsChatSettingsInfo));
            }
            // Обе строки двухстрочные: подпись под заголовком, высота 64,
            // отступ текста от иконки 60. Ставится после setTextAndIcon —
            // тот сбрасывает offsetFromImage.
            cell.heightDp = 64;
            cell.offsetFromImage = 60;
        }

        private void bindSettings(TextSettingsCell cell, int position) {
            if (position == textStyleRow) {
                cell.setText(getString(R.string.TextStyle), true);
            } else if (position == trackingFilterRow) {
                String updated = LinkCleaner.lastUpdated();
                cell.setTextAndValue(getString(R.string.MZChatsTrackingFilter),
                        updated == null ? "AdGuard" : LocaleController.formatString(R.string.MZChatsTrackingFilterValue, updated), false);
            } else if (position == doubleTapIncomingRow) {
                cell.setTextAndValue(getString(R.string.DoubleTapIncoming),
                        DoubleTap.doubleTapActionMap.get(ExtConfig.INSTANCE.getDoubleTapAction().Int()), true);
            } else if (position == doubleTapOutgoingRow) {
                cell.setTextAndValue(getString(R.string.DoubleTapOutgoing),
                        DoubleTap.doubleTapActionMap.get(ExtConfig.INSTANCE.getDoubleTapActionOut().Int()), doubleTapReactionRow != -1);
            } else if (position == stickerTimeRow) {
                CharSequence[] options = stickerTimeOptions();
                cell.setTextAndValue(getString(R.string.MZChatsStickerTime),
                        options[clampIndex(ChatsConfig.stickerTimeMode(), options.length)], true);
            } else if (position == bottomButtonRow) {
                CharSequence[] options = bottomButtonOptions();
                cell.setTextAndValue(getString(R.string.MZChatsBottomButton),
                        options[clampIndex(ChatsConfig.bottomButton(), options.length)], true);
            } else if (position == cameraTypeRow) {
                CharSequence[] options = cameraTypeOptions();
                cell.setTextAndValue(getString(R.string.MZChatsCameraType),
                        options[clampIndex(cameraTypeIndex(), options.length)], true);
            } else if (position == videoMessagesCameraRow) {
                CharSequence[] options = videoMessagesCameraOptions();
                cell.setTextAndValue(getString(R.string.CameraInVideoMessages),
                        options[clampIndex(ExtConfig.INSTANCE.getCameraInVideoMessages().Int(), options.length)], true);
            } else if (position == doubleTapSeekDurationRow) {
                CharSequence[] options = seekDurationOptions();
                cell.setTextAndValue(getString(R.string.MZChatsDoubleTapSeekDuration),
                        options[clampIndex(ChatsConfig.doubleTapSeekDuration.Int(), options.length)], true);
            } else if (position == videoPlayerDecoderRow) {
                CharSequence[] options = videoPlayerDecoderOptions();
                cell.setTextAndValue(getString(R.string.VideoPlayerDecoder),
                        options[clampIndex(ExtConfig.INSTANCE.getPlayerDecoder().Int(), options.length)], true);
            } else if (position == searchHashtagChatRow) {
                CharSequence[] options = searchHashtagPageOptions();
                cell.setTextAndValue(getString(R.string.SearchHashtagDefaultPageChat),
                        options[clampIndex(ExtConfig.INSTANCE.getSearchHashtagDefaultPageChat().Int(), options.length)], true);
            } else if (position == searchHashtagChannelRow) {
                CharSequence[] options = searchHashtagPageOptions();
                cell.setTextAndValue(getString(R.string.SearchHashtagDefaultPageChannel),
                        options[clampIndex(ExtConfig.INSTANCE.getSearchHashtagDefaultPageChannel().Int(), options.length)], false);
            } else if (position == openLinkConfirmationRow) {
                cell.setTextAndValue(getString(R.string.MZChatsOpenLinkConfirmation),
                        openLinkConfirmationOptions()[openLinkConfirmation()], true);
            } else if (position == transcribeProviderRow) {
                CharSequence[] options = transcribeProviderOptions();
                cell.setTextAndValue(getString(R.string.TranscribeProviderShort),
                        options[clampIndex(ExtConfig.INSTANCE.getTranscribeProvider().Int(), options.length)], true);
            } else if (position == cloudflareCredentialsRow) {
                cell.setText(getString(R.string.CloudflareCredentials), true);
            } else if (position == geminiApiKeyRow) {
                cell.setText(getString(R.string.LlmProviderGeminiKey),
                        openAiCredentialsRow != -1 || voskModelsRow != -1);
            } else if (position == openAiCredentialsRow) {
                cell.setText(getString(R.string.TranscribeProviderOpenAI), false);
            } else if (position == voskModelsRow) {
                cell.setText(getString(R.string.VoskModelsShort), false);
            } else if (position == hideStoriesRow) {
                cell.setTextAndValue(getString(R.string.MZAppearanceStories), storiesOptions()[storiesIndex()], true);
            }
        }

        private void bindInfo(TextInfoPrivacyCell cell, int position) {
            boolean bottom = position == rowCount - 1;
            cell.setFixedSize(0);
            if (position == doubleTapDividerRow) {
                cell.setText(getString(R.string.MZChatsDoubleTapInfo));
            } else if (position == hidingChatsDividerRow) {
                cell.setText(getString(R.string.MZChatsHideSendAsPeerInfo));
            } else if (position == linkConfirmationsDividerRow) {
                cell.setText(getString(R.string.MZChatsStripTrackingInfo));
            } else if (position == menusDividerRow) {
                cell.setText(getString(R.string.MZChatsGlassMessageMenuInfo));
            } else if (position == channelPostsDividerRow) {
                cell.setText(getString(R.string.MZChatsWideChannelPostsFooter));
            } else if (position == cameraDividerRow) {
                cell.setText(getString(R.string.MZChatsStaticZoomInfo));
            } else if (position == playbackDividerRow) {
                cell.setText(getString(R.string.MZChatsPauseOnMinimizeInfo));
            } else {
                cell.setText(null);
                // Без текста у TextInfoPrivacyCell остаются футерные отступы 10+17dp
                // и пустая строка — между карточками зияет дыра. Фиксируем высоту.
                cell.setFixedSize(12);
            }
            cell.setBackground(Theme.getThemedDrawable(mContext,
                    bottom ? R.drawable.greydivider_bottom : R.drawable.greydivider,
                    Theme.key_windowBackgroundGrayShadow));
        }

        @Override
        public int getItemViewType(int position) {
            final int mzType = mzSectionItemViewType(position);
            if (mzType != -1) {
                return mzType;
            }
            if (position == stickerSizeRow) return TYPE_STICKER_SIZE;
            if (position == stickerShapeRow) return TYPE_STICKER_SHAPE;
            if (position == doubleTapRow) return TYPE_DOUBLE_TAP;
            if (position == wideChannelPostsPreviewRow) return TYPE_WIDE_CHANNEL_PREVIEW;
            if (position == doubleTapReactionRow) return TYPE_SET_REACTION;
            if (isHeader(position)) return TYPE_HEADER;
            if (isDivider(position)) return TYPE_INFO_PRIVACY;
            if (position == inlineMathSettingsRow || position == aiChatRow || position == chatSettingsRow || position == menusRow || position == mediaRow) {
                return TYPE_TEXT;
            }
            if (isGroupHeader(position)) return TYPE_EXPANDABLE_SWITCH;
            if (groupHeaderFor(position) != -1) return TYPE_ROUND_CHECK;
            if (isSettings(position)) return TYPE_SETTINGS;
            return TYPE_CHECK;
        }

        private boolean isHeader(int position) {
            return position == stickerShapeHeaderRow
                    || position == linksHeaderRow
                    || position == stickersHeaderRow || position == doubleTapHeaderRow
                    || position == chatsHeaderRow || position == messagesHeaderRow
                    || position == channelPostsHeaderRow
                    || position == cameraHeaderRow
                    || position == photoHeaderRow || position == videosHeaderRow
                    || position == linkConfirmationsHeaderRow || position == transcribeHeaderRow
                    || position == inputHeaderRow || position == searchHeaderRow
                    || position == buttonsHeaderRow || position == menusHeaderRow
                    || position == playbackHeaderRow || position == voiceHeaderRow
                    || position == hidingChatListHeaderRow || position == hidingChatsHeaderRow
                    || position == hidingStickersHeaderRow || position == hidingAiHeaderRow
                    || position == hidingSettingsHeaderRow;
        }

        private boolean isDivider(int position) {
            return position == stickerSizeDividerRow || position == stickerShapeDividerRow
                    || position == linksDividerRow || position == stickersDividerRow
                    || position == doubleTapDividerRow || position == chatsDividerRow
                    || position == messagesDividerRow
                    || position == channelPostsDividerRow
                    || position == cameraDividerRow || position == photoDividerRow
                    || position == videosDividerRow || position == linkConfirmationsDividerRow
                    || position == transcribeDividerRow
                    || position == inputDividerRow || position == searchDividerRow
                    || position == buttonsDividerRow || position == menusDividerRow
                    || position == playbackDividerRow || position == voiceDividerRow
                    || position == hidingChatListDividerRow || position == hidingChatsDividerRow
                    || position == hidingStickersDividerRow || position == hidingAiDividerRow
                    || position == hidingSettingsDividerRow;
        }

        private boolean isGroupHeader(int position) {
            return position == repliesGroupRow || position == hideReactionsGroupRow
                    || position == unlimitedGroupRow
                    || position == quickTransitionGroupRow || position == chatMenuGroupRow
                    || position == messageMenuGroupRow
                    || position == mediaViewerMenuGroupRow || position == actionBarButtonsGroupRow
                    || position == extendedSettingsGroupRow || position == pauseGroupRow
                    || position == premiumElementsGroupRow || position == deleteMenuGroupRow
                    || position == askWhenGroupRow || position == stripTrackingGroupRow
                    || position == hideAiGroupRow || position == hideSettingsGroupRow;
        }

        private boolean isSettings(int position) {
            return position == doubleTapIncomingRow || position == doubleTapOutgoingRow
                    || position == bottomButtonRow || position == cameraTypeRow || position == stickerTimeRow
                    || position == videoMessagesCameraRow || position == doubleTapSeekDurationRow
                    || position == videoPlayerDecoderRow
                    || position == searchHashtagChatRow || position == searchHashtagChannelRow
                    || position == openLinkConfirmationRow || position == transcribeProviderRow
                    || position == cloudflareCredentialsRow || position == geminiApiKeyRow
                    || position == openAiCredentialsRow || position == voskModelsRow
                    || position == textStyleRow || position == trackingFilterRow
                    || position == hideStoriesRow;
        }
    }
}
