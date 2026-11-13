/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Telegram's own AI features, each of which can be hidden in Settings >
 * MZGram > Chats > Telegram AI features: the AI editor button (message
 * field, captions, the rich editor), the message summary button and the
 * AI summary block at the top of an Instant View page. All shown by default,
 * as in Telegram. Idea from exteraless (GPL-3.0).
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ArticleViewer;

public final class MZGramTelegramAi {

    // The caption Telegram gives the summary block of an Instant View page
    private static final String IV_SUMMARY_CAPTION = "Cocoon AI Summary";

    private MZGramTelegramAi() {
    }

    public static boolean editorHidden() {
        return !MZGramConfig.telegramAiEditor;
    }

    public static boolean summaryHidden() {
        return !MZGramConfig.telegramAiSummary;
    }

    /** Whether this block of an Instant View page is Telegram's AI summary and is to be left out. */
    public static boolean hideIvBlock(TL_iv.PageBlock block) {
        if (MZGramConfig.telegramAiIvSummary) {
            return false;
        }
        TL_iv.RichText caption;
        if (block instanceof TL_iv.pageBlockBlockquote) {
            caption = ((TL_iv.pageBlockBlockquote) block).caption;
        } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
            caption = ((TL_iv.pageBlockBlockquoteBlocks) block).caption;
        } else {
            return false;
        }
        CharSequence plain = ArticleViewer.getPlainText(caption);
        return plain != null && TextUtils.equals(IV_SUMMARY_CAPTION, plain.toString());
    }
}
