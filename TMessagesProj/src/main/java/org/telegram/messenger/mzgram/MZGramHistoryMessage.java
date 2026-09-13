/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * One row of the local message history archive: either a deleted message or
 * one edited revision of a message. Field set mirrors AyuGram4A's
 * database/entities/AyuMessageBase.java, trimmed to what MZGram stores
 * (reactions and forward/reply metadata are not kept).
 *
 * Ported from AyuGram4A 7013145676d36d82ee13c02a89f72097b7490dcd
 * (database/entities/AyuMessageBase.java, DeletedMessage.java, EditedMessage.java).
 */

package org.telegram.messenger.mzgram;

public class MZGramHistoryMessage {

    public static final int KIND_DELETED = 0;
    public static final int KIND_EDITED = 1;

    public static final int MEDIA_NONE = 0;
    public static final int MEDIA_PHOTO = 1;
    public static final int MEDIA_STICKER = 2;
    public static final int MEDIA_FILE = 3;

    public long rowId; // primary key in mzgram_history.db
    public int kind; // KIND_DELETED or KIND_EDITED

    public long accountUserId; // which logged-in account this belongs to
    public long dialogId;
    public long topicId;
    public int messageId;
    public long groupedId; // album id, 0 if none
    public long fromId;

    public int date; // original message date
    public int editDate; // message.edit_date at capture time
    public int entityCreateDate; // when MZGram captured this row

    public String text; // plain text, entities stripped
    public byte[] entities; // TL-serialized ArrayList<MessageEntity>, may be null

    public String mediaPath; // copy kept in the MZGram media archive, or null
    public int mediaType; // MEDIA_* constant
    public String mimeType;
}
