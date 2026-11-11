/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.telegram.ui.mzgram;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProfileActivity;

/**
 * MZGram: opens the profile of a user, group or channel by its number, for
 * a number taken from a bot or a database. Telegram gives a profile only for
 * accounts the app already knows, so a number it has never seen is reported
 * as not found; nothing is asked from the network.
 */
public class MZGramOpenById {

    public static void show(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setSingleLine(true);
        input.setHint(LocaleController.getString(R.string.MZGramOpenByIdHint));
        FrameLayout frame = new FrameLayout(context);
        int pad = AndroidUtilities.dp(24);
        frame.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        frame.addView(input);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MZGramOpenById));
        builder.setView(frame);
        builder.setPositiveButton(LocaleController.getString(R.string.MZGramOpen), (dialog, which) -> {
            open(fragment, input.getText().toString().trim());
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null);
        fragment.showDialog(builder.create());
    }

    private static void open(BaseFragment fragment, String text) {
        long id;
        try {
            id = Long.parseLong(text);
        } catch (NumberFormatException e) {
            Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.MZGramOpenByIdNotFound), Toast.LENGTH_SHORT).show();
            return;
        }
        // A channel or supergroup number from the Bot API starts with -100.
        if (id < -1000000000000L) {
            id = -(id + 1000000000000L);
        } else if (id < 0) {
            id = -id;
        }
        MessagesController controller = MessagesController.getInstance(fragment.getCurrentAccount());
        android.os.Bundle args = new android.os.Bundle();
        TLRPC.User user = controller.getUser(id);
        if (user != null) {
            args.putLong("user_id", id);
        } else if (controller.getChat(id) != null) {
            args.putLong("chat_id", id);
        } else {
            Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.MZGramOpenByIdNotFound), Toast.LENGTH_SHORT).show();
            return;
        }
        fragment.presentFragment(new ProfileActivity(args));
    }
}
