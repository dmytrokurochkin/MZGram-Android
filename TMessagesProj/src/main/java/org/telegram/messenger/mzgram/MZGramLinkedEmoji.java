/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Ported from exteraless (app.exteraless.chats.LinkedCustomEmoji and
 * LinkTypefaceSpan), licensed under the GNU General Public License v3.0.
 * Only the display part is taken: nothing here changes what is sent.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.telegram.messenger.mzgram;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

import org.telegram.messenger.Emoji;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;

/**
 * MZGram: two display options for message text.
 * <p>
 * Linked custom emoji: some clients send a custom emoji as a text link
 * {@code tg://emoji?id=<document id>} over a single ordinary emoji. With the
 * option on, such a link is shown as the custom emoji itself. Only a copy of
 * the entities used for drawing is changed; the stored message, copying,
 * forwarding and editing keep the link. Nothing is sent, and sending custom
 * emoji still needs Telegram Premium as before.
 * <p>
 * Link typeface: bold or italic links and mentions are measured with their
 * own typeface, so their width matches what is drawn.
 */
public final class MZGramLinkedEmoji {

    public static final String PREFIX = "tg://emoji?id=";

    private MZGramLinkedEmoji() {
    }

    public static boolean isLink(TLRPC.MessageEntity entity) {
        return entity instanceof TLRPC.TL_messageEntityTextUrl && entity.url != null && entity.url.startsWith(PREFIX);
    }

    /** Document id of a link that covers exactly one emoji of the text, else 0. */
    private static long documentId(CharSequence text, TLRPC.MessageEntity entity) {
        if (text == null || !isLink(entity) || entity.offset < 0 || entity.length <= 0 || entity.offset + entity.length > text.length()) {
            return 0;
        }
        long documentId;
        try {
            documentId = Long.parseLong(entity.url.substring(PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
        if (documentId == 0) {
            return 0;
        }
        int[] emojiOnly = new int[1];
        ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(text.subSequence(entity.offset, entity.offset + entity.length).toString(), emojiOnly);
        return emojiOnly[0] > 0 && emojis.size() == 1 ? documentId : 0;
    }

    /** Whether the option is on and this entity is a link shown as a custom emoji. */
    public static boolean isLinkedEmoji(CharSequence text, TLRPC.MessageEntity entity) {
        return MZGramConfig.linkedCustomEmoji && documentId(text, entity) != 0;
    }

    /**
     * Entities for drawing the text: the same list when the option is off or
     * nothing matches, else a copy where each emoji link is a custom emoji.
     */
    public static ArrayList<TLRPC.MessageEntity> forDisplay(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (!MZGramConfig.linkedCustomEmoji || text == null || entities == null || entities.isEmpty()) {
            return entities;
        }
        ArrayList<TLRPC.MessageEntity> copy = null;
        for (int i = 0, N = entities.size(); i < N; i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            long documentId = documentId(text, entity);
            if (documentId == 0) {
                continue;
            }
            if (copy == null) {
                copy = new ArrayList<>(entities);
            }
            TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
            emoji.offset = entity.offset;
            emoji.length = entity.length;
            emoji.document_id = documentId;
            copy.set(i, emoji);
        }
        return copy == null ? entities : copy;
    }

    /** Adds the bold or italic typeface for measuring a link or mention run, when the option is on. */
    public static void applyLinkTypeface(Spannable spannable, TextStyleSpan.TextStyleRun run) {
        if (!MZGramConfig.linkTypeface || spannable == null || run == null
                || (run.flags & TextStyleSpan.FLAG_STYLE_MONO) != 0
                || (run.flags & (TextStyleSpan.FLAG_STYLE_BOLD | TextStyleSpan.FLAG_STYLE_ITALIC)) == 0) {
            return;
        }
        Typeface typeface = run.getTypeface();
        if (typeface != null && run.start >= 0 && run.end <= spannable.length() && run.start < run.end) {
            spannable.setSpan(new LinkTypefaceSpan(typeface), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static final class LinkTypefaceSpan extends MetricAffectingSpan {

        private final Typeface typeface;

        public LinkTypefaceSpan(@NonNull Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            paint.setTypeface(typeface);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint paint) {
            paint.setTypeface(typeface);
        }
    }
}
