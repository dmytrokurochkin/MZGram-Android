/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * "Delete my messages" of the chat menu (MZGramChatMenu), ported from
 * exteraless (GPL-3.0): in a group, every message the user sent there is
 * deleted for everyone, or only those sent before a time. The messages are
 * found with messages.search by sender, page by page, and deleted when the
 * bulletin with Undo goes away. An admin who may delete messages can instead
 * have the server delete them all at once (channels.deleteParticipantHistory).
 */

package org.telegram.ui.mzgram;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramLanguage;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Calendar;

public final class MZGramDeleteOwnMessages {

    private static final int PAGE = 100;

    private MZGramDeleteOwnMessages() {
    }

    public static void ask(ChatActivity fragment, TLRPC.Chat chat, long mergeDialogId) {
        ask(fragment, chat, mergeDialogId, 0);
    }

    // before: only messages sent before this time (seconds); 0 for all
    private static void ask(ChatActivity fragment, TLRPC.Chat chat, long mergeDialogId, int before) {
        Context context = fragment.getParentActivity();
        if (context == null || chat == null) {
            return;
        }
        Theme.ResourcesProvider resources = fragment.getResourceProvider();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resources);
        // As an admin who may delete messages: the server deletes them all at once
        CheckBoxCell asAdmin = before == 0 && ChatObject.isChannel(chat) && ChatObject.canUserDoAction(chat, ChatObject.ACTION_DELETE_MESSAGES)
                ? new CheckBoxCell(context, 1, resources) : null;

        FrameLayout frame = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (asAdmin != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + asAdmin.getMeasuredHeight() + AndroidUtilities.dp(7));
                }
            }
        };
        builder.setView(frame);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));
        avatarDrawable.setInfo(chat);
        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        imageView.setForUserOrChat(chat, avatarDrawable);
        frame.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView title = new TextView(context);
        title.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resources));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        title.setText(LocaleController.getString(R.string.MZGramDeleteOwn));
        frame.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));

        TextView text = new TextView(context);
        text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        text.setText(before > 0
                ? LocaleController.formatString(R.string.MZGramDeleteOwnBeforeText, MZGramLanguage.formatDateTime(before * 1000L, false))
                : LocaleController.getString(R.string.MZGramDeleteOwnText));
        frame.addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

        if (asAdmin != null) {
            asAdmin.setBackground(Theme.getSelectorDrawable(false));
            asAdmin.setText(LocaleController.getString(R.string.MZGramDeleteOwnAsAdmin), "", false, false);
            asAdmin.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            frame.addView(asAdmin, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            asAdmin.setOnClickListener(v -> asAdmin.setChecked(!asAdmin.isChecked(), true));
        }

        builder.setNeutralButton(LocaleController.getString(R.string.MZGramDeleteOwnOlder),
                (dialog, which) -> askBefore(fragment, time -> ask(fragment, chat, mergeDialogId, time)));
        builder.setPositiveButton(LocaleController.getString(R.string.MZGramDelete), (dialog, which) -> {
            int account = fragment.getCurrentAccount();
            if (asAdmin != null && asAdmin.isChecked()) {
                showUndo(fragment, 0, () -> MessagesController.getInstance(account).deleteUserChannelHistory(chat, UserConfig.getInstance(account).getCurrentUser(), null, 0));
            } else {
                find(fragment, chat, mergeDialogId, before);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null);
        AlertDialog alert = builder.create();
        fragment.showDialog(alert);
        TextView button = (TextView) alert.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resources));
        }
    }

    // A day, a week, a month ago, or a chosen date and time
    private static void askBefore(ChatActivity fragment, Utilities.Callback<Integer> callback) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        int now = ConnectionsManager.getInstance(fragment.getCurrentAccount()).getCurrentTime();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.MZGramDeleteOwnOlder));
        builder.setItems(new CharSequence[]{
                LocaleController.getString(R.string.MZGramDeleteOwnOlderDay),
                LocaleController.getString(R.string.MZGramDeleteOwnOlderWeek),
                LocaleController.getString(R.string.MZGramDeleteOwnOlderMonth),
                LocaleController.getString(R.string.MZGramDeleteOwnOlderDate)
        }, (dialog, which) -> {
            if (which == 0) {
                callback.run(now - 60 * 60 * 24);
            } else if (which == 1) {
                callback.run(now - 60 * 60 * 24 * 7);
            } else if (which == 2) {
                callback.run(now - 60 * 60 * 24 * 30);
            } else {
                Calendar calendar = Calendar.getInstance();
                DatePickerDialog dateDialog = new DatePickerDialog(context, (view, year, month, day) -> {
                    TimePickerDialog timeDialog = new TimePickerDialog(context, (view1, hour, minute) -> {
                        calendar.set(year, month, day, hour, minute, 0);
                        callback.run((int) (calendar.getTimeInMillis() / 1000));
                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
                    fragment.showDialog(timeDialog);
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
                dateDialog.getDatePicker().setMinDate(1375315200000L); // Telegram's first year
                dateDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
                fragment.showDialog(dateDialog);
            }
        });
        fragment.showDialog(builder.create());
    }

    // Finds the user's messages (and those of the old group a supergroup came
    // from), then offers Undo before deleting them
    private static void find(ChatActivity fragment, TLRPC.Chat chat, long mergeDialogId, int before) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        int account = fragment.getCurrentAccount();
        AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        boolean[] cancelled = new boolean[1];
        progress.setCanCancel(true);
        progress.setOnCancelListener(d -> cancelled[0] = true);
        fragment.showDialog(progress);
        int limit = before > 0 ? before : Integer.MAX_VALUE;
        long dialogId = -chat.id;
        ArrayList<Integer> ids = new ArrayList<>();
        search(account, dialogId, limit, 0, ids, cancelled, () -> {
            ArrayList<Integer> merged = new ArrayList<>();
            Runnable done = () -> {
                progress.dismiss();
                if (cancelled[0]) {
                    return;
                }
                int count = ids.size() + merged.size();
                if (count == 0) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.MZGramDeleteOwnNone)).show();
                    return;
                }
                showUndo(fragment, count, () -> {
                    delete(account, dialogId, ids);
                    delete(account, mergeDialogId, merged);
                });
            };
            if (mergeDialogId != 0 && !cancelled[0]) {
                search(account, mergeDialogId, limit, 0, merged, cancelled, done);
            } else {
                done.run();
            }
        });
    }

    // One page of messages.search by the user; the next page starts below the
    // lowest id; done runs on the UI thread at the end
    private static void search(int account, long dialogId, int before, int offsetId, ArrayList<Integer> ids, boolean[] cancelled, Runnable done) {
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = controller.getInputPeer(dialogId);
        req.from_id = controller.getInputPeer(UserConfig.getInstance(account).getClientUserId());
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.limit = PAGE;
        req.offset_id = offsetId;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (cancelled[0]) {
                AndroidUtilities.runOnUIThread(done);
                return;
            }
            if (!(response instanceof TLRPC.messages_Messages) || response instanceof TLRPC.TL_messages_messagesNotModified) {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> BulletinFactory.global().createErrorBulletin(error.text).show());
                }
                AndroidUtilities.runOnUIThread(done);
                return;
            }
            ArrayList<TLRPC.Message> messages = ((TLRPC.messages_Messages) response).messages;
            int next = nextOffset(messages, offsetId);
            synchronized (ids) {
                ids.addAll(ownBefore(messages, before));
            }
            if (messages.isEmpty() || next == offsetId && offsetId != 0) {
                AndroidUtilities.runOnUIThread(done);
            } else {
                search(account, dialogId, before, next, ids, cancelled, done);
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    /** The lowest id of a page, where the next page starts; offsetId when there is none lower. */
    public static int nextOffset(ArrayList<TLRPC.Message> messages, int offsetId) {
        int next = offsetId == 0 ? Integer.MAX_VALUE : offsetId;
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message != null) {
                next = Math.min(next, message.id);
            }
        }
        return next == Integer.MAX_VALUE ? offsetId : next;
    }

    /** The user's own messages of a page sent before a time: not channel posts, not service messages. */
    public static ArrayList<Integer> ownBefore(ArrayList<TLRPC.Message> messages, int before) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message != null && message.out && !message.post && message.date < before && !(message instanceof TLRPC.TL_messageService)) {
                ids.add(message.id);
            }
        }
        return ids;
    }

    private static void delete(int account, long dialogId, ArrayList<Integer> ids) {
        if (dialogId == 0 || ids.isEmpty()) {
            return;
        }
        for (int a = 0; a < ids.size(); a += PAGE) {
            ArrayList<Integer> part = new ArrayList<>(ids.subList(a, Math.min(ids.size(), a + PAGE)));
            MessagesController.getInstance(account).deleteMessages(part, null, null, dialogId, 0, true, 0);
        }
    }

    // Nothing is deleted before the bulletin goes away; Undo keeps everything
    private static void showUndo(ChatActivity fragment, int count, Runnable delete) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            delete.run();
            return;
        }
        Theme.ResourcesProvider resources = fragment.getResourceProvider();
        Bulletin.ButtonLayout layout;
        if (count > 0) {
            Bulletin.TwoLineLottieLayout twoLines = new Bulletin.TwoLineLottieLayout(context, resources);
            twoLines.setAnimation(R.raw.ic_delete, "Envelope", "Cover", "Bucket");
            twoLines.titleTextView.setText(LocaleController.getString(R.string.MZGramDeleteOwnDone));
            twoLines.subtitleTextView.setText(LocaleController.formatString(R.string.MZGramMessagesCount, count));
            twoLines.setTimer();
            layout = twoLines;
        } else {
            Bulletin.LottieLayout oneLine = new Bulletin.LottieLayout(context, resources);
            oneLine.setAnimation(R.raw.ic_delete, "Envelope", "Cover", "Bucket");
            oneLine.textView.setText(LocaleController.getString(R.string.MZGramDeleteOwnDone));
            oneLine.setTimer();
            layout = oneLine;
        }
        layout.setButton(new Bulletin.UndoButton(context, true, resources).setDelayedAction(delete));
        Bulletin.make(fragment, layout, Bulletin.DURATION_PROLONG).show();
    }
}
