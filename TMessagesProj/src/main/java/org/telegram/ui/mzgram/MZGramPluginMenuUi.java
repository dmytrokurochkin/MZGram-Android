/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Shows the menu items of plugins (MZGramPluginMenus) in Telegram's menus:
 * the menu of a message, the three dots of a chat and of a profile, and the
 * menu of the chat list. Up to two items of plugins are shown as they are;
 * more are folded into one "Plugins" item that opens their list (rule 7.1 of
 * the feature conflicts: the message menu must not grow past the screen).
 *
 * A tap hands the plugin the context of the menu as JSON: the account and
 * the ids of the chat, the user or the message (and the message's text),
 * never Java objects. Nothing shows while no plugin has added items, so
 * Telegram's menus stay as they are.
 *
 * Based on exteraless plugins/menus/MenuInjector.java (GPL-3.0): the places,
 * the context of a tap and the folding into a "Plugins" item; written anew
 * for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.plugins.MZGramPluginMenus;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;

import java.util.ArrayList;
import java.util.List;

public final class MZGramPluginMenuUi {

    // Ids of the options and menu items, far from Telegram's and MZGram's own:
    // BASE is the folded "Plugins" item, BASE + 1 + i the i-th shown item.
    private static final int MESSAGE_BASE = 1_900_000;
    private static final int CHAT_BASE = 1_900_100;
    private static final int PROFILE_BASE = 1_900_200;
    private static final int RANGE = 1 + MZGramPluginMenus.MAX_SHOWN;

    private static final int ICON = R.drawable.msg_bots;

    // The items each menu was built with, so a tap finds the same item
    private static List<MZGramPluginMenus.Item> messageItems = new ArrayList<>();
    private static List<MZGramPluginMenus.Item> chatItems = new ArrayList<>();
    private static List<MZGramPluginMenus.Item> profileItems = new ArrayList<>();

    private MZGramPluginMenuUi() {
    }

    private static boolean folded(List<MZGramPluginMenus.Item> items) {
        return items.size() > MZGramPluginMenus.MAX_SHOWN;
    }

    private static int iconOf(MZGramPluginMenus.Item item) {
        final Context context = ApplicationLoader.applicationContext;
        if (item.icon == null || context == null) {
            return ICON;
        }
        final int id = context.getResources().getIdentifier(item.icon, "drawable", context.getPackageName());
        return id != 0 ? id : ICON;
    }

    // ---------- the menu of a message ----------

    /** Adds the items of plugins to the lists ChatActivity builds the message menu from. */
    public static void fillMessageMenu(ArrayList<CharSequence> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        messageItems = MZGramPluginMenus.items(MZGramPluginMenus.MESSAGE_CONTEXT_MENU);
        if (messageItems.isEmpty()) {
            return;
        }
        if (folded(messageItems)) {
            items.add(getString(R.string.MZGramPlugins));
            options.add(MESSAGE_BASE);
            icons.add(ICON);
            return;
        }
        for (int i = 0; i < messageItems.size(); i++) {
            items.add(messageItems.get(i).text);
            options.add(MESSAGE_BASE + 1 + i);
            icons.add(iconOf(messageItems.get(i)));
        }
    }

    /** A selected option of the message menu; does nothing for options that are not of plugins. */
    public static void onMessageOption(BaseFragment fragment, int option, MessageObject message, int account) {
        if (option < MESSAGE_BASE || option >= MESSAGE_BASE + RANGE || message == null) {
            return;
        }
        final JSONObject context = new JSONObject();
        try {
            context.put("account", account);
            context.put("dialog_id", message.getDialogId());
            context.put("message_id", message.getId());
            context.put("from_id", message.getFromChatId());
            context.put("out", message.isOut());
            context.put("date", message.messageOwner != null ? message.messageOwner.date : 0);
            context.put("text", message.messageOwner != null && message.messageOwner.message != null ? message.messageOwner.message : "");
        } catch (Exception e) {
            FileLog.e(e);
        }
        open(fragment, messageItems, option - MESSAGE_BASE, context);
    }

    // ---------- the three dots of a chat and of a profile ----------

    /** Adds the items of plugins to the three dots of a chat. */
    public static void addChatItems(ActionBarMenuItem menu) {
        chatItems = MZGramPluginMenus.items(MZGramPluginMenus.CHAT_ACTION_MENU);
        addSubItems(menu, chatItems, CHAT_BASE, true);
    }

    /** Adds the items of plugins to the three dots of a profile. */
    public static void addProfileItems(ActionBarMenuItem menu) {
        profileItems = MZGramPluginMenus.items(MZGramPluginMenus.PROFILE_ACTION_MENU);
        addSubItems(menu, profileItems, PROFILE_BASE, false);
    }

    private static void addSubItems(ActionBarMenuItem menu, List<MZGramPluginMenus.Item> list, int base, boolean lazily) {
        if (menu == null || list.isEmpty()) {
            return;
        }
        if (folded(list)) {
            addSubItem(menu, base, ICON, getString(R.string.MZGramPlugins), lazily);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            addSubItem(menu, base + 1 + i, iconOf(list.get(i)), list.get(i).text, lazily);
        }
    }

    private static void addSubItem(ActionBarMenuItem menu, int id, int icon, CharSequence text, boolean lazily) {
        if (lazily) {
            menu.lazilyAddSubItem(id, icon, text);
        } else {
            menu.addSubItem(id, icon, text);
        }
    }

    /** A tap on an item of the three dots of a chat; false when it is not of plugins. */
    public static boolean onChatItem(BaseFragment fragment, int id, long dialogId, int account) {
        if (id < CHAT_BASE || id >= CHAT_BASE + RANGE) {
            return false;
        }
        final JSONObject context = new JSONObject();
        try {
            context.put("account", account);
            context.put("dialog_id", dialogId);
        } catch (Exception e) {
            FileLog.e(e);
        }
        open(fragment, chatItems, id - CHAT_BASE, context);
        return true;
    }

    /** A tap on an item of the three dots of a profile; false when it is not of plugins. */
    public static boolean onProfileItem(BaseFragment fragment, int id, long userId, long chatId, int account) {
        if (id < PROFILE_BASE || id >= PROFILE_BASE + RANGE) {
            return false;
        }
        final JSONObject context = new JSONObject();
        try {
            context.put("account", account);
            context.put("user_id", userId);
            context.put("chat_id", chatId);
            context.put("dialog_id", userId != 0 ? userId : -chatId);
        } catch (Exception e) {
            FileLog.e(e);
        }
        open(fragment, profileItems, id - PROFILE_BASE, context);
        return true;
    }

    // ---------- the menu of the chat list ----------

    /** Adds the items of plugins to the menu of the chat list (MAIN_MENU and DRAWER_MENU). */
    public static void addTo(ItemOptions options, BaseFragment fragment, int account) {
        final List<MZGramPluginMenus.Item> list = MZGramPluginMenus.items(MZGramPluginMenus.MAIN_MENU);
        if (options == null || list.isEmpty()) {
            return;
        }
        final JSONObject context = new JSONObject();
        try {
            context.put("account", account);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (folded(list)) {
            options.add(ICON, getString(R.string.MZGramPlugins), () -> open(fragment, list, 0, context));
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final int place = 1 + i;
            options.add(iconOf(list.get(i)), list.get(i).text, () -> open(fragment, list, place, context));
        }
    }

    // ---------- taps ----------

    // place 0 is the folded "Plugins" item, place 1 + i the i-th item
    private static void open(BaseFragment fragment, List<MZGramPluginMenus.Item> list, int place, JSONObject context) {
        if (place == 0) {
            showList(fragment, list, context);
        } else if (place - 1 < list.size()) {
            click(fragment, list.get(place - 1), context);
        }
    }

    private static void showList(BaseFragment fragment, List<MZGramPluginMenus.Item> list, JSONObject context) {
        if (fragment == null || fragment.getParentActivity() == null || list.isEmpty()) {
            return;
        }
        final CharSequence[] texts = new CharSequence[list.size()];
        final int[] icons = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            texts[i] = list.get(i).text;
            icons[i] = iconOf(list.get(i));
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(getString(R.string.MZGramPlugins));
        builder.setItems(texts, icons, (dialog, which) -> {
            if (which >= 0 && which < list.size()) {
                click(fragment, list.get(which), context);
            }
        });
        fragment.showDialog(builder.create());
    }

    private static void click(BaseFragment fragment, MZGramPluginMenus.Item item, JSONObject context) {
        MZGramPluginMenus.click(item, context, error -> {
            if (error == null || fragment == null || fragment.getParentActivity() == null) {
                return;
            }
            FileLog.e("MZGramPluginMenuUi: " + item.pluginId + "/" + item.itemId + ": " + error);
            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.MZGramPluginSettingsActionFailed)).show();
        });
    }
}
