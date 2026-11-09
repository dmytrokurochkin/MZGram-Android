package org.telegram.ui.mzgram;

import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramAccountPrefs;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

/**
 * MZGram: the folder that opens when the app starts, chosen per account.
 * Pressing Back on another folder first returns to it. Off by default
 * (folder id 0). Only applies on the first chat list of a process (cold start).
 */
public class MZGramLaunchFolder {

    private static final String FEATURE = "launch_folder";
    private static boolean appliedThisProcess;

    public static int getFolderId(int account) {
        return MZGramAccountPrefs.of(FEATURE, account).getInt("folderId", 0);
    }

    public static void setFolderId(int account, int folderId) {
        MZGramAccountPrefs.of(FEATURE, account).putInt("folderId", folderId);
    }

    /** The index of the chosen folder in the account's folder list, or -1 when off or deleted. */
    private static int folderIndex(int account) {
        int id = getFolderId(account);
        if (id == 0) {
            return -1;
        }
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(account).getDialogFilters();
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    /** Called once the folder tabs are filled: opens the chosen folder on a cold start. */
    public static void applyOnLaunch(DialogsActivity dialogs, int account) {
        if (appliedThisProcess) {
            return;
        }
        int index = folderIndex(account);
        if (index < 0) {
            return;
        }
        appliedThisProcess = true;
        dialogs.scrollToFolder(getFolderId(account));
    }

    /** True when Back on another folder should return to the chosen folder (does it when invoked). */
    public static boolean backToLaunchFolder(DialogsActivity dialogs, int account, FilterTabsView tabs, boolean invoked) {
        int index = folderIndex(account);
        if (index < 0 || tabs == null || tabs.getCurrentTabId() == index) {
            return false;
        }
        if (invoked) {
            dialogs.scrollToFolder(getFolderId(account));
        }
        return true;
    }

    /** True while the chosen folder is the current one, so Back must leave the app instead of going to "All chats". */
    public static boolean isOnLaunchFolder(int account, FilterTabsView tabs) {
        int index = folderIndex(account);
        return index >= 0 && tabs != null && tabs.getCurrentTabId() == index;
    }

    /** Title of the chosen folder for the settings row. */
    public static String valueFor(int account) {
        int id = getFolderId(account);
        if (id == 0) {
            return LocaleController.getString(R.string.MZGramLaunchFolderOff);
        }
        int index = folderIndex(account);
        if (index < 0) {
            return LocaleController.getString(R.string.MZGramLaunchFolderOff);
        }
        MessagesController.DialogFilter filter = MessagesController.getInstance(account).getDialogFilters().get(index);
        // Telegram's own text: the folder's name, as Telegram's folder tabs show it
        return filter.isDefault() ? LocaleController.getString(R.string.FilterAllChats) : filter.name;
    }

    /** Dialog with the account's folders; the first row switches the feature off. */
    public static void choose(BaseFragment fragment, int account, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(account).getDialogFilters();
        ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<Integer> ids = new ArrayList<>();
        names.add(LocaleController.getString(R.string.MZGramLaunchFolderOff));
        ids.add(0);
        for (MessagesController.DialogFilter filter : filters) {
            // Telegram's own text: the folder's name, as Telegram's folder tabs show it
            names.add(filter.isDefault() ? LocaleController.getString(R.string.FilterAllChats) : filter.name);
            ids.add(filter.id);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MZGramLaunchFolder));
        builder.setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
            setFolderId(account, ids.get(which));
            if (onChanged != null) {
                onChanged.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null);
        fragment.showDialog(builder.create());
    }
}
