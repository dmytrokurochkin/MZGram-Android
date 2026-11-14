/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Chats > Date on forwarded messages: the "Forwarded
 * from" line also says when the original message was sent. From
 * exteraless (GPL-3.0).
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;

import java.util.Date;

public class MZGramForwardDate {

    public static String withDate(MessageObject messageObject, String line) {
        if (!MZGramConfig.forwardedDate || line == null || messageObject == null || messageObject.messageOwner == null
                || messageObject.messageOwner.fwd_from == null || messageObject.messageOwner.fwd_from.date == 0) {
            return line;
        }
        return line + " · " + format(messageObject.messageOwner.fwd_from.date);
    }

    // "12 Jan 14:03", or only the time for today (formatDate already gives the time then)
    static String format(long date) {
        // Telegram's own text: the date sits in the message bubble, in Telegram's language
        String day = LocaleController.formatDate(date);
        String time = LocaleController.getInstance().getFormatterDay().format(new Date(date * 1000L));
        return day.equals(time) ? day : day + ' ' + time;
    }
}
