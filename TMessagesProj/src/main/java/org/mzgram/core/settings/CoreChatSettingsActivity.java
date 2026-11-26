package org.mzgram.core.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Unit;
import org.mzgram.core.CoreConfig;
import org.mzgram.core.config.CellGroup;
import org.mzgram.core.config.cell.AbstractConfigCell;
import org.mzgram.core.config.cell.ConfigCellCheckBox;
import org.mzgram.core.config.cell.ConfigCellCustom;
import org.mzgram.core.config.cell.ConfigCellDivider;
import org.mzgram.core.config.cell.ConfigCellHeader;
import org.mzgram.core.config.cell.ConfigCellNotice;
import org.mzgram.core.config.cell.ConfigCellSelectBox;
import org.mzgram.core.config.cell.ConfigCellTextCheck;
import org.mzgram.core.config.cell.ConfigCellTextCheck2;
import org.mzgram.core.config.cell.ConfigCellTextCheckIcon;
import org.mzgram.core.config.cell.ConfigCellTextInput;
import org.mzgram.core.helpers.ChatsHelper;
import org.mzgram.core.helpers.TranscribeHelper;
import org.mzgram.core.helpers.remote.EmojiHelper;
import org.mzgram.core.ui.PopupBuilder;
import org.mzgram.core.ui.cells.EmojiSetCell;
import org.mzgram.core.ui.cells.StickerSizePreviewMessagesCell;
import org.mzgram.ext.ExtConfig;
import org.mzgram.ext.helper.DoubleTap;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class CoreChatSettingsActivity extends BaseCoreXSettingsActivity implements NotificationCenter.NotificationCenterDelegate, EmojiHelper.EmojiPacksLoadedListener {

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "chat";
    }

    private final CellGroup cellGroup = new CellGroup(this);

    // Sticker Size
    private final AbstractConfigCell headerStickerSize = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.StickerSize)));
    private final AbstractConfigCell stickerSizeRow = cellGroup.appendCell(new ConfigCellCustom("StickerSize", ConfigCellCustom.CUSTOM_ITEM_StickerSize, false));
    private final AbstractConfigCell showTimeHintRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getShowTimeHint()));
    private final AbstractConfigCell hideTimeForStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.hideTimeForSticker));
    private final AbstractConfigCell disableReplyBackgroundRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getMessageColoredBackground()));
    private final AbstractConfigCell dividerStickerSize = cellGroup.appendCell(new ConfigCellDivider());

    // Chats
    private final AbstractConfigCell headerChats = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Chat)));
    private final AbstractConfigCell emojiSetsRow = cellGroup.appendCell(new ConfigCellCustom("EmojiSets", ConfigCellCustom.CUSTOM_ITEM_EmojiSet, true));
    private final AbstractConfigCell premiumElementsToggleRow = cellGroup.appendCell(new ConfigCellTextCheck2("PremiumElements", getString(R.string.PremiumElements), new ArrayList<>() {{
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemEmojiStatus()));
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemEmojiInReplies()));
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemCustomColorInReplies()));
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemCustomWallpaper()));
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemVideoAvatar()));
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemStarInReactions()));
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemStickerEffects()));
        add(new ConfigCellCheckBox(ExtConfig.INSTANCE.getPremiumItemBoosts()));
    }}, null));
    ArrayList<ConfigCellCheckBox> premiumElementsRows = ((ConfigCellTextCheck2) premiumElementsToggleRow).getCheckBox();
    private final AbstractConfigCell unreadBadgeOnBackButton = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.unreadBadgeOnBackButton));
    private final AbstractConfigCell sendCommentAfterForwardRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.sendCommentAfterForward));
    private final AbstractConfigCell useChatAttachMediaMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.useChatAttachMediaMenu, getString(R.string.UseChatAttachEnterMenuNotice)));
    private final AbstractConfigCell fixLinkPreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getFixLinkPreview(), "x.com -> fixupx.com"));
    private final AbstractConfigCell disableLinkPreviewByDefaultRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.disableLinkPreviewByDefault));
    private final AbstractConfigCell deleteChatForBothSidesRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDeleteChatForBothSides()));
    private final AbstractConfigCell showMessageIDRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getShowMessageID()));
    private final AbstractConfigCell showSeconds = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.showSeconds));
    private final AbstractConfigCell useEditedIconRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getUseEditedIcon()));
    private final AbstractConfigCell customEditedMessageRow = cellGroup.appendCell(new ConfigCellTextInput(null, ExtConfig.INSTANCE.getCustomEditedMessage(), "", null));
    private final AbstractConfigCell dateOfForwardMsgRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDateOfForwardedMsg()));
    private final AbstractConfigCell showFullAboutRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getShowFullAbout()));
    private final AbstractConfigCell disableTrendingRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.disableTrending));
    private final AbstractConfigCell disableZalgoSymbolsRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getZalgoFilter(), getString(R.string.ZalgoFilterNotice)));
    private final AbstractConfigCell showOnlineStatusRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getShowOnlineStatus(), getString(R.string.ShowOnlineStatusNotice)));
    private final AbstractConfigCell leftButtonActionRow = cellGroup.appendCell(new ConfigCellSelectBox(null, ExtConfig.INSTANCE.getLeftBottomButton(), new String[]{
            getString(R.string.Reply),
            getString(R.string.AddToSavedMessages),
            getString(R.string.DirectShare),
            getString(R.string.SelectBetween),
            getString(R.string.NoCaptionForward),
            getString(R.string.NoQuoteForward),
    }, new int[]{
            ChatsHelper.LEFT_BUTTON_REPLY,
            ChatsHelper.LEFT_BUTTON_SAVE_MESSAGE,
            ChatsHelper.LEFT_BUTTON_DIRECT_SHARE,
            ChatsHelper.LEFT_BUTTON_SELECT_BETWEEN,
            ChatsHelper.LEFT_BUTTON_NOCAPTION,
            ChatsHelper.LEFT_BUTTON_NOQUOTE,
    }, null));
    private final AbstractConfigCell leftButtonActionHintRow = cellGroup.appendCell(
            new ConfigCellNotice(getString(R.string.MZChatsLeftButtonActionHint)));
    private final AbstractConfigCell markdownParserRow = cellGroup.appendCell(new ConfigCellSelectBox(null, ExtConfig.INSTANCE.getMarkdownParser(), new String[]{
            getString(R.string.Official),
            getString(R.string.MZMarkdownParserExtended),
    }, null));
    private final AbstractConfigCell dividerChats = cellGroup.appendCell(new ConfigCellDivider());

    // Double Tap
    private final AbstractConfigCell headerDoubleTap = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.DoubleTapAction)));
    private final AbstractConfigCell doubleTapActionRow = cellGroup.appendCell(new ConfigCellCustom("DoubleTapIncoming", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell doubleTapActionOutRow = cellGroup.appendCell(new ConfigCellCustom("DoubleTapOutgoing", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerDoubleTap = cellGroup.appendCell(new ConfigCellDivider());

    // Camera
    private final AbstractConfigCell headerCamera = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.CameraSettings)));
    private final AbstractConfigCell disableInstantCameraRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.disableInstantCamera));
    private final AbstractConfigCell cameraInVideoMessages = cellGroup.appendCell(new ConfigCellSelectBox("CameraInVideoMessages", ExtConfig.INSTANCE.getCameraInVideoMessages(), new String[]{
            getString(R.string.CameraInVideoMessagesFront),
            getString(R.string.CameraInVideoMessagesRear),
            getString(R.string.CameraInVideoMessagesAsk)
    }, null));
    private final AbstractConfigCell dividerCamera = cellGroup.appendCell(new ConfigCellDivider());

    // Media
    private final AbstractConfigCell headerMedia = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MediaSettings)));
    private final AbstractConfigCell showSmallGifRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getShowSmallGIF()));
    private final AbstractConfigCell takeGIFasVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.takeGIFasVideo));
    private final AbstractConfigCell autoPauseVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.autoPauseVideo, getString(R.string.AutoPauseVideoAbout)));
    private final AbstractConfigCell disablePreviewVideoSoundShortcutRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDisablePreviewVideoSoundShortcut(), getString(R.string.DisablePreviewVideoSoundShortcutNotice)));
    private final AbstractConfigCell dontAutoPlayNextVoiceRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDontAutoPlayNextVoice()));
    private final AbstractConfigCell showSpoilersDirectlyRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.showSpoilersDirectly));
    private final AbstractConfigCell dividerMedia = cellGroup.appendCell(new ConfigCellDivider());

    // Stickers
    private final AbstractConfigCell headerSticker = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.StickerSettings)));
    private final AbstractConfigCell dontSendGreetingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.dontSendGreetingSticker));
    private final AbstractConfigCell hideGroupStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.hideGroupSticker));
    private final AbstractConfigCell maxRecentStickerCountRow = cellGroup.appendCell(new ConfigCellCustom("maxRecentStickerCount", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerSticker = cellGroup.appendCell(new ConfigCellDivider());

    // Transcribe
    private final AbstractConfigCell headerTranscribe = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.PremiumPreviewVoiceToText)));
    private final AbstractConfigCell transcribeProviderRow = cellGroup.appendCell(new ConfigCellSelectBox("TranscribeProviderShort", ExtConfig.INSTANCE.getTranscribeProvider(), new String[]{
            getString(R.string.TranscribeProviderAuto),
            getString(R.string.TelegramPremium),
            getString(R.string.TranscribeProviderWorkersAI),
            getString(R.string.TranscribeProviderGemini),
            getString(R.string.TranscribeProviderOpenAI),
            getString(R.string.TranscribeProviderVosk),
    }, null));
    private final AbstractConfigCell transcribeProviderCfCredentialsRow = cellGroup.appendCell(new ConfigCellCustom("CloudflareCredentials", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell transcribeProviderGeminiApiKeyRow = cellGroup.appendCell(new ConfigCellCustom("LlmProviderGeminiKey", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell transcribeProviderOpenAiRow = cellGroup.appendCell(new ConfigCellCustom("TranscribeProviderOpenAI", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerTranscribe = cellGroup.appendCell(new ConfigCellDivider());

    // MenuAndButtons
    private final AbstractConfigCell headerMenuAndButtons = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MenuAndButtons)));
    private final AbstractConfigCell chatMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "ChatMenu", null, R.drawable.menu_chats, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.ChatMenu, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShortcutsAdministrators(), getString(R.string.ChannelAdministrators), R.drawable.msg_admins));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShortcutsRecentActions(), getString(R.string.EventLog), R.drawable.msg_log));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShortcutsStatistics(), getString(R.string.Statistics), R.drawable.msg_stats));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShortcutsPermissions(), getString(R.string.ChannelPermissions), R.drawable.msg_permissions));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShortcutsMembers(), getString(R.string.GroupMembers), R.drawable.msg_groups, true));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemBoostGroup(), getString(R.string.BoostingBoostGroupMenu), R.drawable.boost_channel_solar));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemLinkedChat(), getString(R.string.LinkedGroupChat), R.drawable.msg_discussion));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemToBeginning(), getString(R.string.ToTheBeginning), R.drawable.ic_upward));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemGoToMessage(), getString(R.string.ToTheMessage), R.drawable.msg_go_up));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemHideTitle(), getString(R.string.HideTitle), R.drawable.hide_title));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemViewDeleted(), getString(R.string.ViewDeleted), R.drawable.msg_view_file));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemClearDeleted(), getString(R.string.ClearDeleted), R.drawable.msg_clear));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getChatMenuItemDeleteOwnMessages(), getString(R.string.DeleteAllFromSelf), R.drawable.msg_delete));
            }}))
    ));
    private final AbstractConfigCell messageMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "MessageMenu", null, R.drawable.msg_list, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.MessageMenu, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowReactions(), R.drawable.msg_reactions2));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowReplyInPrivate(), R.drawable.menu_reply));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowCopyLink(), R.drawable.msg_link));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowCopyFrame(), getString(R.string.CopyVideoFrame), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowCopyPhoto(), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowCopyAsSticker(), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowAddToStickers(), R.drawable.msg_sticker));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowAddToFavorites(), R.drawable.msg_fave));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowNoQuoteForward(), R.drawable.msg_forward_noquote));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowSetReminder(), R.drawable.msg_calendar2));
                add(new ConfigCellTextCheckIcon(CoreConfig.showAddToSavedMessages, getString(R.string.AddToSavedMessages), R.drawable.msg_saved));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowAddToBookmark(), getString(R.string.AddBookmark), R.drawable.msg_fave));
                add(new ConfigCellTextCheckIcon(CoreConfig.showRepeat, getString(R.string.Repeat), R.drawable.msg_repeat));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowRepeatAsCopy(), R.drawable.msg_repeat));
                add(new ConfigCellTextCheckIcon(CoreConfig.showDeleteDownloadedFile, getString(R.string.DeleteDownloadedFile), R.drawable.msg_clear));
                add(new ConfigCellTextCheckIcon(CoreConfig.showViewHistory, getString(R.string.ViewHistory), R.drawable.menu_recent));
                add(new ConfigCellTextCheckIcon(CoreConfig.showTranslate, getString(R.string.Translate), R.drawable.msg_translate));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getShowTranslateMessageLLM(), R.drawable.magic_stick_solar));
                add(new ConfigCellTextCheckIcon(CoreConfig.showShareMessages, getString(R.string.ShareMessages), R.drawable.msg_shareout));
                add(new ConfigCellTextCheckIcon(CoreConfig.showMessageHide, getString(R.string.Hide), R.drawable.msg_disable));
                add(new ConfigCellTextCheckIcon(CoreConfig.showReport, getString(R.string.ReportChat), R.drawable.msg_report));
                add(new ConfigCellTextCheckIcon(CoreConfig.showAdminActions, getString(R.string.EditAdminRights), R.drawable.profile_admin));
                add(new ConfigCellTextCheckIcon(CoreConfig.showChangePermissions, getString(R.string.ChangePermissions), R.drawable.msg_permissions));
                add(new ConfigCellTextCheckIcon(CoreConfig.showMessageDetails, getString(R.string.MessageDetails), R.drawable.msg_info));
            }}))
    ));
    private final AbstractConfigCell mediaViewerMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "MediaViewerMenu", null, R.drawable.msg_photos, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.MediaViewerMenu, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getMediaViewerMenuItemForward(), getString(R.string.Forward), R.drawable.msg_forward));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getMediaViewerMenuItemNoQuoteForward(), getString(R.string.NoQuoteForward), R.drawable.msg_forward_noquote));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getMediaViewerMenuItemCopyFrame(), getString(R.string.CopyVideoFrame), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getMediaViewerMenuItemCopyPhoto(), getString(R.string.CopyPhoto), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getMediaViewerMenuItemSetProfilePhoto(), getString(R.string.SetProfilePhoto), R.drawable.msg_openprofile));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getMediaViewerMenuItemScanQRCode(), getString(R.string.ScanQRCode), R.drawable.msg_qrcode));
            }}))
    ));
    private final AbstractConfigCell actionBarButtonRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "ActionBarButtons", null, R.drawable.msg_media, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.ActionBarButtons, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getActionBarButtonReply(), R.drawable.menu_reply));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getActionBarButtonEdit(), R.drawable.msg_edit));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getActionBarButtonSelectBetween(), R.drawable.ic_select_between));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getActionBarButtonCopy(), R.drawable.msg_copy));
                add(new ConfigCellTextCheckIcon(ExtConfig.INSTANCE.getActionBarButtonForward(), R.drawable.msg_forward_noquote));
            }}))
    ));
    private final AbstractConfigCell defaultDeleteMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "DefaultDeleteMenu", null, R.drawable.msg_admins, false, () -> {
        if (getParentActivity() == null) return;
        showDialog(showConfigMenuAlert(getParentActivity(), ExtConfig.INSTANCE.getDefaultDeleteMenu().getKey(), new ArrayList<>() {{
            add(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDefaultDeleteMenuBanUsers()));
            add(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDefaultDeleteMenReportSpam()));
            add(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDefaultDeleteMenuDeleteAll()));
            add(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups()));
        }}));
    }));

    private final AbstractConfigCell textStyleRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "TextStyle", null, R.drawable.msg_photo_text_framed3, false, () -> {
        if (getParentActivity() == null) return;
        showDialog(org.mzgram.chats.TextStyleDialog.create(getParentActivity()));
    }));
    private final AbstractConfigCell dividerMenuAndButtons = cellGroup.appendCell(new ConfigCellDivider());

    // Interactions
    private final AbstractConfigCell headerInteractions = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.InteractionSettings)));
    private final AbstractConfigCell groupedMessageMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getGroupedMessageMenu(), getString(R.string.GroupedMessageMenuNotice)));
    private final AbstractConfigCell hideKeyboardOnChatScrollRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.hideKeyboardOnChatScroll));
    private final AbstractConfigCell disableVibrationRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.disableVibration));
    private final AbstractConfigCell disableMarkdownRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDisableMarkdown()));
    private final AbstractConfigCell disableProximityEventsRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.disableProximityEvents));
    private final AbstractConfigCell rememberAllBackMessagesRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.rememberAllBackMessages));
    private final AbstractConfigCell typeMessageHintUseGroupNameRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getTypeMessageHintUseGroupName()));
    private final AbstractConfigCell showSendAsUnderMessageHintRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getShowSendAsUnderMessageHint()));
    private final AbstractConfigCell showQuickReplyInBotCommandsRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getShowQuickReplyInBotCommands()));
    private final AbstractConfigCell hideBotButtonInInputFieldRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getHideBotButtonInInputField()));
    private final AbstractConfigCell hideReactionsRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getHideReactions()));
    private final AbstractConfigCell dividerInteractions = cellGroup.appendCell(new ConfigCellDivider());

    // Channels
    private final AbstractConfigCell headerChannels = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.ChannelsTab)));
    private final AbstractConfigCell hideSendAsChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.hideSendAsChannel));
    private final AbstractConfigCell hideShareButtonInChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getHideShareButtonInChannel()));
    private final AbstractConfigCell disableSwipeToNextRow = cellGroup.appendCell(new ConfigCellTextCheck2("DisableSwipeToNext", getString(R.string.DisableSwipeToNext), new ArrayList<>() {{
        add(new ConfigCellCheckBox(CoreConfig.disableSwipeToNext, null, getString(R.string.ChannelsTab), 0, true));
        add(new ConfigCellCheckBox(CoreConfig.disableSwipeToNextTopic, null, getString(R.string.Topics), 0, true));
    }}, null));
    private final ArrayList<ConfigCellCheckBox> disableSwipeToNextRows = ((ConfigCellTextCheck2) disableSwipeToNextRow).getCheckBox();
    private final AbstractConfigCell dividerChannels = cellGroup.appendCell(new ConfigCellDivider());

    // Confirmations
    private final AbstractConfigCell headerConfirmation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.ConfirmSettings)));
    private final AbstractConfigCell skipOpenLinkConfirmRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.skipOpenLinkConfirm));
    private final AbstractConfigCell askBeforeCallRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.askBeforeCall));
    private final AbstractConfigCell repeatConfirmRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.repeatConfirm));
    private final AbstractConfigCell disableClickCommandToSendRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getDisableClickCommandToSend()));
    private final AbstractConfigCell confirmAVRow = cellGroup.appendCell(new ConfigCellTextCheck(CoreConfig.confirmAVMessage));
    private final AbstractConfigCell confirmAllLinksRow = cellGroup.appendCell(new ConfigCellTextCheck(ExtConfig.INSTANCE.getConfirmAllLinks(), getString(R.string.ConfirmAllLinksDescription)));
    private final AbstractConfigCell dividerConfirmation = cellGroup.appendCell(new ConfigCellDivider());

    // Search tag
    private final AbstractConfigCell headerSearchTag = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.SavedTagSearchHint)));
    private final String[] searchPagesString = new String[]{
            getString(R.string.SearchThisChat),
            getString(R.string.SearchMyMessages),
            getString(R.string.SearchPublicPosts),
    };
    private final AbstractConfigCell searchHashtagDefaultPageChannelRow = cellGroup.appendCell(new ConfigCellSelectBox(null, ExtConfig.INSTANCE.getSearchHashtagDefaultPageChannel(), searchPagesString, null));
    private final AbstractConfigCell searchHashtagDefaultPageChatRow = cellGroup.appendCell(new ConfigCellSelectBox(null, ExtConfig.INSTANCE.getSearchHashtagDefaultPageChat(), searchPagesString, null));
    private final AbstractConfigCell dividerSearchTag  = cellGroup.appendCell(new ConfigCellDivider());

    private ListAdapter listAdapter;
    private ActionBarMenuItem menuItem;
    private StickerSizeCell stickerSizeCell;

    public CoreChatSettingsActivity() {
        if (ExtConfig.INSTANCE.getUseEditedIcon().Bool()) {
            cellGroup.rows.remove(customEditedMessageRow);
        }
        if (ExtConfig.INSTANCE.getTranscribeProvider().Int() != TranscribeHelper.TRANSCRIBE_OPENAI) {
            cellGroup.rows.remove(transcribeProviderOpenAiRow);
        }
        if (!BuildVars.LOGS_ENABLED) {
            cellGroup.rows.remove(markdownParserRow);
        }
        checkSkipOpenLinkConfirmRows();
        checkConfirmAVRows();
        cellGroup.rows.removeAll(Arrays.asList(
                headerStickerSize, stickerSizeRow, showTimeHintRow, hideTimeForStickerRow,
                disableReplyBackgroundRow, dividerStickerSize,
                useChatAttachMediaMenuRow, showSeconds, useEditedIconRow,
                disableZalgoSymbolsRow, showOnlineStatusRow,
                headerDoubleTap, doubleTapActionRow, doubleTapActionOutRow, dividerDoubleTap,
                cameraInVideoMessages, autoPauseVideoRow, dontSendGreetingStickerRow,
                messageMenuRow, mediaViewerMenuRow, actionBarButtonRow,
                groupedMessageMenuRow, hideKeyboardOnChatScrollRow, disableVibrationRow,
                headerChannels, hideSendAsChannelRow, hideShareButtonInChannelRow,
                disableSwipeToNextRow, dividerChannels));
        cellGroup.rows.addAll(cellGroup.rows.indexOf(headerChats) + 1,
                Arrays.asList(showTimeHintRow, disableReplyBackgroundRow));
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        EmojiHelper.getInstance().loadEmojisInfo(this);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return super.onFragmentCreate();
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);

        setupDefaultListeners();

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(CoreConfig.disableProximityEvents.getKey())) {
                MediaController.getInstance().recreateProximityWakeLock();
            } else if (key.equals(CoreConfig.showSeconds.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(ExtConfig.INSTANCE.getConfirmAllLinks().getKey())) {
                checkSkipOpenLinkConfirmRows();
            } else if (key.equals(CoreConfig.useChatAttachMediaMenu.getKey())) {
                checkConfirmAVRows();
            } else if (key.equals(ExtConfig.INSTANCE.getUseEditedIcon().getKey())) {
                if ((boolean) newValue) {
                    if (cellGroup.rows.contains(customEditedMessageRow)) {
                        final int index = cellGroup.rows.indexOf(customEditedMessageRow);
                        cellGroup.rows.remove(customEditedMessageRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(customEditedMessageRow)) {
                        final int index = cellGroup.rows.indexOf(useEditedIconRow) + 1;
                        cellGroup.rows.add(index, customEditedMessageRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
            } else if (key.equals(ExtConfig.INSTANCE.getMessageColoredBackground().getKey())) {
                if (stickerSizeCell != null) stickerSizeCell.invalidate();
            } else if (key.equals(CoreConfig.hideTimeForSticker.getKey())) {
                if (stickerSizeCell != null) stickerSizeCell.invalidate();
            } else if (key.equals("PremiumElements" + "_check")) {
                if (stickerSizeCell != null) stickerSizeCell.invalidate();
            } else if (key.equals(ExtConfig.INSTANCE.getPremiumItemEmojiInReplies().getKey())) {
                if (stickerSizeCell != null) stickerSizeCell.invalidate();
            } else if (key.equals(ExtConfig.INSTANCE.getPremiumItemCustomColorInReplies().getKey())) {
                if (stickerSizeCell != null) stickerSizeCell.invalidate();
            } else if (key.equals(ExtConfig.INSTANCE.getTranscribeProvider().getKey())) {
                if ((int) newValue == TranscribeHelper.TRANSCRIBE_OPENAI) {
                    if (!cellGroup.rows.contains(transcribeProviderOpenAiRow)) {
                        final int index = cellGroup.rows.indexOf(transcribeProviderGeminiApiKeyRow) + 1;
                        cellGroup.rows.add(index, transcribeProviderOpenAiRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(transcribeProviderOpenAiRow)) {
                        final int index = cellGroup.rows.indexOf(transcribeProviderOpenAiRow);
                        cellGroup.rows.remove(transcribeProviderOpenAiRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
            } else if (key.equals("PremiumElements") || key.equals("DisableSwipeToNext")) {
                addRowsToMap(cellGroup);
            }
        };

        return superView;
    }

    @Override
    protected void onActionBarItemClick(int id) {
        if (id == 1) {
            CoreConfig.stickerSize.setConfigFloat(14.0f);
            menuItem.setVisibility(View.GONE);
            stickerSizeCell.invalidate();
        }
    }

    @Override
    protected void onCustomCellClick(View view, int position, float x, float y) {
        if (position == cellGroup.rows.indexOf(maxRecentStickerCountRow)) {
            final int[] counts = {20, 30, 40, 50, 80, 100, 120, 150, 180, 200};
            List<String> types = Arrays.stream(counts)
                    .filter(i -> i <= getMessagesController().maxRecentStickersCount)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.toList());
            PopupBuilder builder = new PopupBuilder(view);
            builder.setItems(types, (i, str) -> {
                CoreConfig.maxRecentStickerCount.setConfigInt(Integer.parseInt(str.toString()));
                listAdapter.notifyItemChanged(position);
                return Unit.INSTANCE;
            });
            builder.show();
        } else if (position == cellGroup.rows.indexOf(doubleTapActionRow) || position == cellGroup.rows.indexOf(doubleTapActionOutRow)) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(getString(R.string.Disable));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_NONE);
            arrayList.add(getString(R.string.SendReactions));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS);
            arrayList.add(getString(R.string.ShowReactions));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_SHOW_REACTIONS);
            arrayList.add(getString(R.string.TranslateMessage));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE);
            arrayList.add(getString(R.string.TranslateMessageLLM));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE_LLM);
            arrayList.add(getString(R.string.Reply));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_REPLY);
            arrayList.add(getString(R.string.AddToSavedMessages));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_SAVE);
            arrayList.add(getString(R.string.MZDoubleTapSaveToDevice));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_DOWNLOAD);
            arrayList.add(getString(R.string.Repeat));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT);
            arrayList.add(getString(R.string.RepeatAsCopy));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT_AS_COPY);
            if (position == cellGroup.rows.indexOf(doubleTapActionOutRow)) {
                arrayList.add(getString(R.string.Edit));
                types.add(DoubleTap.DOUBLE_TAP_ACTION_EDIT);
            }
            arrayList.add(getString(R.string.Delete));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_DELETE);
            PopupBuilder builder = new PopupBuilder(view);
            builder.setItems(arrayList, (i, str) -> {
                if (position == cellGroup.rows.indexOf(doubleTapActionRow)) {
                    ExtConfig.INSTANCE.getDoubleTapAction().setConfigInt(types.get(i));
                } else {
                    ExtConfig.INSTANCE.getDoubleTapActionOut().setConfigInt(types.get(i));
                }
                listAdapter.notifyItemChanged(position);
                return Unit.INSTANCE;
            });
            builder.show();
        } else if (position == cellGroup.rows.indexOf(emojiSetsRow)) {
            presentFragment(new CoreEmojiSettingsActivity());
        } else if (position == cellGroup.rows.indexOf(transcribeProviderCfCredentialsRow)) {
            TranscribeHelper.showCfCredentialsDialog(this);
        } else if (position == cellGroup.rows.indexOf(transcribeProviderGeminiApiKeyRow)) {
            TranscribeHelper.showGeminiApiKeyDialog(this);
        } else if (position == cellGroup.rows.indexOf(transcribeProviderOpenAiRow)) {
            TranscribeHelper.showOpenAiCredentialsDialog(this);
        }
    }

    @Override
    protected void onCheckBoxCellClick(View view, int position) {
        AbstractConfigCell a = cellGroup.rows.get(position);
        ((ConfigCellCheckBox) a).onClick((CheckBoxCell) view);
        int toggleRowIndex = cellGroup.rows.indexOf(premiumElementsToggleRow);
        if (position > toggleRowIndex && position <= toggleRowIndex + premiumElementsRows.size()) {
            listAdapter.notifyItemRangeChanged(toggleRowIndex, premiumElementsRows.size());
        }
        toggleRowIndex = cellGroup.rows.indexOf(disableSwipeToNextRow);
        if (position > toggleRowIndex && position <= toggleRowIndex + disableSwipeToNextRows.size()) {
            listAdapter.notifyItemChanged(toggleRowIndex);
        }
    }

    @Override
    public int getDrawable() {
        return R.drawable.menu_chats;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Chat);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = super.getThemeDescriptions();

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked));

        return themeDescriptions;
    }

    public static boolean[] getDeleteMenuChecks() {
        return new boolean[]{
                ExtConfig.INSTANCE.getDefaultDeleteMenuBanUsers().Bool(),
                ExtConfig.INSTANCE.getDefaultDeleteMenReportSpam().Bool(),
                ExtConfig.INSTANCE.getDefaultDeleteMenuDeleteAll().Bool(),
                ExtConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups().Bool(),
        };
    }

    @Override
    public void emojiPacksLoaded(String error) {
        if (listAdapter != null) {
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(emojiSetsRow));
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded && listAdapter != null) {
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(emojiSetsRow));
        }
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.onFragmentDestroy();
    }

    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final SeekBarView sizeBar;
        private final int startStickerSize = 2;
        private final int endStickerSize = 20;

        private final TextPaint textPaint;

        public StickerSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endStickerSize - startStickerSize + 1);
            sizeBar.setDelegate((stop, progress) -> {
                CoreConfig.stickerSize.setConfigFloat(startStickerSize + (endStickerSize - startStickerSize) * progress);
                StickerSizeCell.this.invalidate();
                menuItem.setVisibility(View.VISIBLE);
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));

            messagesCell = new StickerSizePreviewMessagesCell(context, CoreChatSettingsActivity.this);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + Math.round(CoreConfig.stickerSize.Float()), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((CoreConfig.stickerSize.Float() - startStickerSize) / (float) (endStickerSize - startStickerSize));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            messagesCell.invalidate();
            sizeBar.invalidate();
        }
    }

    //impl ListAdapter
    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TextSettingsCell textCell) {
                if (position == cellGroup.rows.indexOf(maxRecentStickerCountRow)) {
                    textCell.setTextAndValue(getString(R.string.maxRecentStickerCount), String.valueOf(CoreConfig.maxRecentStickerCount.Int()), true);
                } else if (position == cellGroup.rows.indexOf(doubleTapActionRow)) {
                    textCell.setTextAndValue(getString(R.string.DoubleTapIncoming), DoubleTap.doubleTapActionMap.get(ExtConfig.INSTANCE.getDoubleTapAction().Int()), true);
                } else if (position == cellGroup.rows.indexOf(doubleTapActionOutRow)) {
                    textCell.setTextAndValue(getString(R.string.DoubleTapOutgoing), DoubleTap.doubleTapActionMap.get(ExtConfig.INSTANCE.getDoubleTapActionOut().Int()), true);
                } else if (position == cellGroup.rows.indexOf(transcribeProviderCfCredentialsRow)) {
                    textCell.setTextAndValue(getString(R.string.CloudflareCredentials), "", true);
                } else if (position == cellGroup.rows.indexOf(transcribeProviderGeminiApiKeyRow)) {
                    textCell.setTextAndValue(getString(R.string.LlmProviderGeminiKey), "", true);
                } else if (position == cellGroup.rows.indexOf(transcribeProviderOpenAiRow)) {
                    textCell.setTextAndValue(getString(R.string.TranscribeProviderOpenAI), "", true);
                }
            } else if (holder.itemView instanceof EmojiSetCell v1) {
                v1.setData(EmojiHelper.getInstance().getCurrentEmojiPackInfo(), false, true);
            }
        }

        @Override
        protected View onCreateCustomViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case ConfigCellCustom.CUSTOM_ITEM_StickerSize:
                    view = stickerSizeCell = new StickerSizeCell(mContext);
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_EmojiSet:
                    view = new EmojiSetCell(mContext, false);
                    break;
                case CellGroup.ITEM_TYPE_CHECK2:
                    view = new TextCheckCell2(mContext);
                    break;
                case CellGroup.ITEM_TYPE_CHECK_BOX:
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, getResourceProvider());
                    checkBoxCell.getCheckBoxRound().setDrawBackgroundAsArc(14);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setEnabled(true);
                    view = checkBoxCell;
                    break;
            }
            return view;
        }
    }

    private void checkSkipOpenLinkConfirmRows() {
        boolean confirmAllLinks = ExtConfig.INSTANCE.getConfirmAllLinks().Bool();
        if (listAdapter == null) {
            if (confirmAllLinks) {
                cellGroup.rows.remove(skipOpenLinkConfirmRow);
            }
            return;
        }
        if (!confirmAllLinks) {
            final int index = cellGroup.rows.indexOf(headerConfirmation);
            if (!cellGroup.rows.contains(skipOpenLinkConfirmRow)) {
                cellGroup.rows.add(index + 1, skipOpenLinkConfirmRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(skipOpenLinkConfirmRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(skipOpenLinkConfirmRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkConfirmAVRows() {
        boolean useChatAttachMediaMenu = CoreConfig.useChatAttachMediaMenu.Bool();
        if (listAdapter == null) {
            if (useChatAttachMediaMenu) {
                cellGroup.rows.remove(confirmAVRow);
            }
            return;
        }
        if (!useChatAttachMediaMenu) {
            final int index = cellGroup.rows.indexOf(disableClickCommandToSendRow);
            if (!cellGroup.rows.contains(confirmAVRow)) {
                cellGroup.rows.add(index + 1, confirmAVRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(confirmAVRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(confirmAVRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }
}
