/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Ported from Nekogram (tw.nekomimi.nekogram.MessageDetailsActivity, commit
 * d769499), rebuilt on upstream's UniversalFragment. Shows the raw details of
 * a message: ids, sender, dates, forward source, file and media info, DC and
 * sticker pack owners. Unlike Nekogram it does not look people up through
 * third-party bots, has no DC ping popup and no JSON viewer: everything shown
 * is read locally.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FlagSecureReason;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mzgram.MZGramMessageHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MZGramMessageDetailsActivity extends UniversalFragment {

    private static final int ROW_ID = 1;
    private static final int ROW_MESSAGE = 2;
    private static final int ROW_CAPTION = 3;
    private static final int ROW_CHAT = 4;
    private static final int ROW_FROM = 5;
    private static final int ROW_BOT = 6;
    private static final int ROW_DATE = 7;
    private static final int ROW_EDITED = 8;
    private static final int ROW_FORWARD = 9;
    private static final int ROW_RESTRICTION = 10;
    private static final int ROW_VIEWS = 11;
    private static final int ROW_FILE_NAME = 12;
    private static final int ROW_FILE_PATH = 13;
    private static final int ROW_FILE_SIZE = 14;
    private static final int ROW_MIME = 15;
    private static final int ROW_MEDIA = 16;
    private static final int ROW_DC = 17;
    private static final int ROW_STICKER_OWNER = 18;
    private static final int ROW_EMOJI_OWNERS = 19;
    private static final int ROW_LANGUAGE = 20;
    private static final int ROW_LINK_OR_EMOJI_ONLY = 21;

    private final MessageObject messageObject;
    private final boolean noforwards;

    private TLRPC.Chat toChat;
    private TLRPC.User fromUser;
    private TLRPC.Chat fromChat;
    private TLRPC.Peer forwardFromPeer;
    private String filePath;
    private String fileName;
    private int width;
    private int height;
    private String videoCodec;
    private int dc;
    private long stickerSetOwner;
    private final ArrayList<Long> emojiSetOwners = new ArrayList<>();
    private CharSequence language;
    private FlagSecureReason flagSecure;

    public MZGramMessageDetailsActivity(MessageObject messageObject) {
        this.messageObject = messageObject;
        final TLRPC.Message owner = messageObject.messageOwner;

        if (owner.peer_id != null && (owner.peer_id.channel_id != 0 || owner.peer_id.chat_id != 0)) {
            toChat = getMessagesController().getChat(owner.peer_id.channel_id != 0 ? owner.peer_id.channel_id : owner.peer_id.chat_id);
        }
        if (owner.fwd_from != null && owner.fwd_from.from_id != null) {
            forwardFromPeer = owner.fwd_from.from_id;
        }
        if (owner.from_id != null) {
            if (owner.from_id.channel_id != 0 || owner.from_id.chat_id != 0) {
                fromChat = getMessagesController().getChat(owner.from_id.channel_id != 0 ? owner.from_id.channel_id : owner.from_id.chat_id);
            } else if (owner.from_id.user_id != 0) {
                fromUser = getMessagesController().getUser(owner.from_id.user_id);
            }
        }

        final TLRPC.MessageMedia media = MessageObject.getMedia(owner);
        if (media != null) {
            filePath = MZGramMessageHelper.getPathToMessage(messageObject);
            final TLRPC.Photo photo = media.webpage != null ? media.webpage.photo : media.photo;
            if (photo != null) {
                dc = photo.dc_id;
                final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
                if (photoSize != null) {
                    width = photoSize.w;
                    height = photoSize.h;
                }
            }
            final TLRPC.Document document = media.webpage != null ? media.webpage.document : media.document;
            if (document != null) {
                dc = document.dc_id;
                for (TLRPC.DocumentAttribute attribute : document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                        fileName = attribute.file_name;
                    }
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker && attribute.stickerset != null) {
                        stickerSetOwner = getOwnerFromStickerSetId(attribute.stickerset.id);
                    }
                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                        width = attribute.w;
                        height = attribute.h;
                        videoCodec = attribute.video_codec;
                    }
                }
            }
        }

        if (owner.entities != null) {
            for (TLRPC.MessageEntity entity : owner.entities) {
                if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                    final TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, ((TLRPC.TL_messageEntityCustomEmoji) entity).document_id);
                    final TLRPC.InputStickerSet stickerSet = MessageObject.getInputStickerSet(document);
                    if (stickerSet == null) {
                        continue;
                    }
                    final long setOwner = getOwnerFromStickerSetId(stickerSet.id);
                    if (setOwner != 0 && !emojiSetOwners.contains(setOwner)) {
                        emojiSetOwners.add(setOwner);
                    }
                }
            }
        }

        noforwards = isPeerNoForwards() || owner.noforwards || messageObject.type == MessageObject.TYPE_PAID_MEDIA;
    }

    private boolean isPeerNoForwards() {
        return toChat != null
                ? getMessagesController().isChatNoForwards(toChat)
                : fromUser != null && getMessagesController().isUserNoForwards(fromUser.id);
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        flagSecure = new FlagSecureReason(getParentActivity().getWindow(), () -> noforwards);
        final String plainText = getMessagePlainText(messageObject);
        if (!TextUtils.isEmpty(plainText)) {
            language = "…";
            LanguageDetector.detectLanguage(plainText, lang -> {
                language = lang;
                updateList();
            }, e -> {
                language = e != null ? e.getLocalizedMessage() : null;
                updateList();
            });
        }
        return view;
    }

    private void updateList() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramMessageDetails);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final TLRPC.Message owner = messageObject.messageOwner;
        if (!messageObject.isSponsored()) {
            items.add(DetailFactory.of(ROW_ID, "ID", String.valueOf(owner.id)));
        }
        if (!TextUtils.isEmpty(messageObject.messageText)) {
            items.add(DetailFactory.of(ROW_MESSAGE, "Message", messageObject.messageText.toString()));
        }
        if (!TextUtils.isEmpty(messageObject.caption)) {
            items.add(DetailFactory.of(ROW_CAPTION, "Caption", messageObject.caption.toString()));
        }
        if (toChat != null) {
            items.add(DetailFactory.of(ROW_CHAT, toChat.broadcast ? "Channel" : "Group", describe(toChat)));
        }
        if (fromUser != null || fromChat != null || !TextUtils.isEmpty(owner.post_author)) {
            final CharSequence from = fromUser != null ? describe(fromUser) : fromChat != null ? describe(fromChat) : owner.post_author;
            items.add(DetailFactory.of(ROW_FROM, "From", from));
        }
        if (fromUser != null && fromUser.bot) {
            items.add(DetailFactory.of(ROW_BOT, "Bot", "Yes"));
        }
        if (owner.date != 0) {
            items.add(DetailFactory.of(ROW_DATE, messageObject.scheduled ? "Scheduled date" : "Date", formatTime(owner.date)));
        }
        if (owner.edit_date != 0) {
            items.add(DetailFactory.of(ROW_EDITED, "Edited", formatTime(owner.edit_date)));
        }
        if (messageObject.isForwarded() && owner.fwd_from != null) {
            final StringBuilder builder = new StringBuilder();
            if (forwardFromPeer != null) {
                if (forwardFromPeer.channel_id != 0 || forwardFromPeer.chat_id != 0) {
                    builder.append(describe(getMessagesController().getChat(forwardFromPeer.channel_id != 0 ? forwardFromPeer.channel_id : forwardFromPeer.chat_id)));
                } else if (forwardFromPeer.user_id != 0) {
                    builder.append(describe(getMessagesController().getUser(forwardFromPeer.user_id)));
                }
            } else if (!TextUtils.isEmpty(owner.fwd_from.from_name)) {
                builder.append(owner.fwd_from.from_name);
            }
            builder.append("\n").append(formatTime(owner.fwd_from.date));
            items.add(DetailFactory.of(ROW_FORWARD, "Forward from", builder));
        }
        if (owner.restriction_reason != null && !owner.restriction_reason.isEmpty()) {
            final StringBuilder value = new StringBuilder();
            for (int i = 0; i < owner.restriction_reason.size(); i++) {
                final TLRPC.RestrictionReason reason = owner.restriction_reason.get(i);
                if (i > 0) {
                    value.append("\n");
                }
                value.append(reason.reason).append("-").append(reason.platform).append(": ").append(reason.text);
            }
            items.add(DetailFactory.of(ROW_RESTRICTION, "Restriction reason", value));
        }
        if (owner.views > 0 || owner.forwards > 0) {
            items.add(DetailFactory.of(ROW_VIEWS, "Views and forwards", String.format(Locale.US, "%d views, %d forwards", owner.views, owner.forwards)));
        }
        if (!TextUtils.isEmpty(fileName)) {
            items.add(DetailFactory.of(ROW_FILE_NAME, "File name", fileName));
        }
        if (!TextUtils.isEmpty(filePath)) {
            items.add(DetailFactory.of(ROW_FILE_PATH, "File path", filePath));
        }
        if (messageObject.getSize() > 0) {
            items.add(DetailFactory.of(ROW_FILE_SIZE, "File size", AndroidUtilities.formatFileSize(messageObject.getSize())));
        }
        if (!TextUtils.isEmpty(messageObject.getMimeType())) {
            items.add(DetailFactory.of(ROW_MIME, "MimeType", messageObject.getMimeType()));
        }
        if (width > 0 && height > 0) {
            items.add(DetailFactory.of(ROW_MEDIA, "Media", String.format(Locale.US, "%dx%d", width, height) + (TextUtils.isEmpty(videoCodec) ? "" : ", " + videoCodec)));
        }
        if (dc != 0) {
            items.add(DetailFactory.of(ROW_DC, "DC", formatDCString(dc)));
        }
        if (stickerSetOwner > 0) {
            final TLRPC.User user = getMessagesController().getUser(stickerSetOwner);
            items.add(DetailFactory.of(ROW_STICKER_OWNER, "Sticker Pack creator", user != null ? describe(user) : String.valueOf(stickerSetOwner)));
        }
        if (!emojiSetOwners.isEmpty()) {
            items.add(DetailFactory.of(ROW_EMOJI_OWNERS, "Emoji Pack creators", TextUtils.join(", ", emojiSetOwners)));
        }
        if (!TextUtils.isEmpty(language)) {
            items.add(DetailFactory.of(ROW_LANGUAGE, "Language", language));
        }
        if (!TextUtils.isEmpty(owner.message) && isLinkOrEmojiOnlyMessage(messageObject)) {
            items.add(DetailFactory.of(ROW_LINK_OR_EMOJI_ONLY, "Link or emoji only", "Yes"));
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_FILE_PATH) {
            if (noforwards) {
                showNoForwards();
                return;
            }
            try {
                final Uri uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", new File(filePath));
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setDataAndType(uri, messageObject.getMimeType());
                startActivityForResult(Intent.createChooser(intent, getString(R.string.ShareFile)), 500);
            } catch (Exception ignored) {
            }
        } else if (item.id == ROW_CHAT && toChat != null) {
            openProfile("chat_id", toChat.id);
        } else if (item.id == ROW_FROM) {
            if (fromChat != null) {
                openProfile("chat_id", fromChat.id);
            } else if (fromUser != null) {
                openProfile("user_id", fromUser.id);
            }
        } else if (item.id == ROW_FORWARD && forwardFromPeer != null) {
            if (forwardFromPeer.channel_id != 0 || forwardFromPeer.chat_id != 0) {
                openProfile("chat_id", forwardFromPeer.channel_id != 0 ? forwardFromPeer.channel_id : forwardFromPeer.chat_id);
            } else if (forwardFromPeer.user_id != 0) {
                openProfile("user_id", forwardFromPeer.user_id);
            }
        } else if (item.id == ROW_STICKER_OWNER && getMessagesController().getUser(stickerSetOwner) != null) {
            openProfile("user_id", stickerSetOwner);
        }
    }

    // Long press copies the value, except protected text and file paths.
    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (!(view instanceof TextDetailSettingsCell)) {
            return false;
        }
        if (noforwards && (item.id == ROW_MESSAGE || item.id == ROW_CAPTION || item.id == ROW_FILE_PATH)) {
            showNoForwards();
            return true;
        }
        AndroidUtilities.addToClipboard(((TextDetailSettingsCell) view).getValueTextView().getText());
        BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
        return true;
    }

    private void openProfile(String key, long id) {
        final Bundle args = new Bundle();
        args.putLong(key, id);
        presentFragment(new ProfileActivity(args));
    }

    private void showNoForwards() {
        final int text;
        if (toChat == null) {
            text = R.string.ForwardsRestrictedInfoUser;
        } else {
            text = toChat.broadcast ? R.string.ForwardsRestrictedInfoChannel : R.string.ForwardsRestrictedInfoGroup;
        }
        BulletinFactory.of(this).createErrorBulletin(getString(text)).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (flagSecure != null) {
            flagSecure.attach();
        }
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        if (flagSecure != null) {
            flagSecure.detach();
        }
    }

    private String formatTime(int timestamp) {
        if (timestamp == 0x7ffffffe) {
            return "When online";
        }
        final Date date = new Date(timestamp * 1000L);
        return timestamp + "\n" + LocaleController.formatString(R.string.formatDateAtTime,
                LocaleController.getInstance().getFormatterYear().format(date),
                LocaleController.getInstance().getFormatterDayWithSeconds().format(date));
    }

    private static CharSequence describe(TLObject object) {
        final StringBuilder builder = new StringBuilder();
        if (object instanceof TLRPC.User) {
            final TLRPC.User user = (TLRPC.User) object;
            builder.append(ContactsController.formatName(user.first_name, user.last_name)).append("\n");
            final String username = UserObject.getPublicUsername(user);
            if (!TextUtils.isEmpty(username)) {
                builder.append("@").append(username).append("\n");
            }
            builder.append(user.id);
        } else if (object instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) object;
            builder.append(chat.title).append("\n");
            final String username = ChatObject.getPublicUsername(chat);
            if (!TextUtils.isEmpty(username)) {
                builder.append("@").append(username).append("\n");
            }
            builder.append(chat.id);
        }
        return builder;
    }

    private static String getMessagePlainText(MessageObject messageObject) {
        if (messageObject.isPoll() && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
            final TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
            final StringBuilder pollText = new StringBuilder(poll.question.text).append("\n");
            for (TLRPC.PollAnswer answer : poll.answers) {
                pollText.append("\n🔘 ").append(answer.text.text);
            }
            return pollText.toString();
        } else if (messageObject.isVoiceTranscriptionOpen()) {
            return messageObject.messageOwner.voiceTranscription;
        }
        return messageObject.messageOwner.message;
    }

    private static boolean isLinkOrEmojiOnlyMessage(MessageObject messageObject) {
        if (messageObject.getEmojiOnlyCount() > 0) {
            return true;
        }
        final String message = messageObject.messageOwner.message;
        final ArrayList<TLRPC.MessageEntity> entities = messageObject.messageOwner.entities;
        if (message == null || entities == null) {
            return false;
        }
        for (TLRPC.MessageEntity entity : entities) {
            if (entity instanceof TLRPC.TL_messageEntityBotCommand ||
                    entity instanceof TLRPC.TL_messageEntityEmail ||
                    entity instanceof TLRPC.TL_messageEntityUrl ||
                    entity instanceof TLRPC.TL_messageEntityMention ||
                    entity instanceof TLRPC.TL_messageEntityCashtag ||
                    entity instanceof TLRPC.TL_messageEntityHashtag ||
                    entity instanceof TLRPC.TL_messageEntityBankCard ||
                    entity instanceof TLRPC.TL_messageEntityPhone) {
                if (entity.offset == 0 && entity.length == message.length()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatDCString(int dc) {
        final String name;
        final String location;
        switch (dc) {
            case 1: name = "Pluto"; location = "Miami"; break;
            case 2: name = "Venus"; location = "Amsterdam"; break;
            case 3: name = "Aurora"; location = "Miami"; break;
            case 4: name = "Vesta"; location = "Amsterdam"; break;
            case 5: name = "Flora"; location = "Singapore"; break;
            default: name = "Unknown"; location = "Unknown"; break;
        }
        return String.format(Locale.US, "DC%d %s, %s", dc, name, location);
    }

    // The user id packed into a sticker set id.
    private static long getOwnerFromStickerSetId(long id) {
        long ownerId = id >> 32;
        final long extByte = (id >> 24) & 0xff;
        final long sepByte = (id >> 16) & 0xff;
        if (sepByte == 0x3f) {
            ownerId |= 0x80000000L;
        }
        if (extByte != 0) {
            ownerId += 0x100000000L;
        }
        return ownerId;
    }

    // Two-line cell: a label above its value.
    public static class DetailFactory extends UItem.UItemFactory<TextDetailSettingsCell> {
        static {
            setup(new DetailFactory());
        }

        @Override
        public TextDetailSettingsCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            // Upstream's cell takes no resources provider, unlike Nekogram's.
            return new TextDetailSettingsCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final TextDetailSettingsCell cell = (TextDetailSettingsCell) view;
            cell.setMultilineDetail(true);
            cell.setTextAndValue(item.text, item.subtext, divider);
        }

        public static UItem of(int id, CharSequence title, CharSequence value) {
            final UItem item = UItem.ofFactory(DetailFactory.class);
            item.id = id;
            item.text = title;
            item.subtext = value;
            return item;
        }
    }
}
