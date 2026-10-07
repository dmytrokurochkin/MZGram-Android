/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > Chat settings (Appearance) > Color theme lists
 * MediaDataController.defaultEmojiThemes: the home theme (Classic, Day,
 * Night, Tinted) and the theme set the server marks as default in
 * account.getThemes (the emoji themes).
 *
 * When the server's answer has no default themes, or has not come yet, the
 * list holds the home theme and the chat themes of account.getChatThemes
 * (the emoji themes the chat theme picker shows) instead of the home theme
 * alone. A chat theme has a light and a dark setting, while the list needs
 * four variants (day, day, night, night), so each setting is used twice.
 *
 * Theme.loadRemoteThemes asks with the saved hash only once the list holds
 * more than the home theme (hasServerThemes), otherwise the answer is "not
 * modified" and the emoji themes never come.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.Components.ChatThemeBottomSheet;

import java.util.ArrayList;
import java.util.List;

public class MZGramDefaultThemes {

    private static final String HOME_EMOJI = "🏠";

    // The list with the home theme only, built without the network.
    public static ArrayList<ChatThemeBottomSheet.ChatThemeItem> homeOnly(int account) {
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = new ArrayList<>();
        items.add(new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createHomePreviewTheme(account)));
        return items;
    }

    // True when the list has more than the home theme.
    public static boolean hasServerThemes(MediaDataController controller) {
        for (ChatThemeBottomSheet.ChatThemeItem item : controller.defaultEmojiThemes) {
            if (item != null && item.chatTheme != null && !HOME_EMOJI.equals(item.chatTheme.getEmoticonOrSlug())) {
                return true;
            }
        }
        return false;
    }

    // A theme as the color theme list shows it, with four variants, or null
    // when it has no settings, or a setting without the wallpaper colors the
    // list's preview draws from.
    public static EmojiThemes preview(int account, TLRPC.TL_theme theme) {
        if (theme == null || theme.settings == null || theme.settings.isEmpty()) {
            return null;
        }
        for (TLRPC.ThemeSettings settings : theme.settings) {
            if (settings == null || settings.wallpaper == null || settings.wallpaper.settings == null) {
                return null;
            }
        }
        if (theme.settings.size() >= 4) {
            return EmojiThemes.createPreviewFullTheme(account, theme);
        }
        TLRPC.TL_theme four = new TLRPC.TL_theme();
        four.flags = theme.flags;
        four.creator = theme.creator;
        four.isDefault = theme.isDefault;
        four.for_chat = theme.for_chat;
        four.id = theme.id;
        four.access_hash = theme.access_hash;
        four.slug = theme.slug;
        four.title = theme.title;
        four.document = theme.document;
        four.emoticon = theme.emoticon;
        four.installs_count = theme.installs_count;
        TLRPC.ThemeSettings light = theme.settings.get(0);
        TLRPC.ThemeSettings dark = theme.settings.get(theme.settings.size() > 1 ? 1 : 0);
        four.settings.add(light);
        four.settings.add(light);
        four.settings.add(dark);
        four.settings.add(dark);
        return EmojiThemes.createPreviewFullTheme(account, four);
    }

    // MediaDataController, when the server gave no default themes: the home
    // theme at once, then the chat themes after it, unless the server's
    // themes came in the meantime. A list that already has the chat themes
    // stays as it is while they load again: dropping it to the home theme
    // for that moment left an Appearance screen opened then with one theme.
    public static void fill(MediaDataController controller, int account) {
        if (controller.defaultEmojiThemes.isEmpty()) {
            show(controller, account, homeOnly(account), false);
        }
        ChatThemeController.getInstance(account).requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> chatThemes) {
                ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = homeOnly(account);
                if (chatThemes != null) {
                    for (EmojiThemes chatTheme : chatThemes) {
                        if (chatTheme == null || chatTheme.isAnyStub() || chatTheme.isGiftTheme()) {
                            continue;
                        }
                        EmojiThemes theme = preview(account, chatTheme.getTlTheme(0));
                        if (theme != null) {
                            items.add(new ChatThemeBottomSheet.ChatThemeItem(theme));
                        }
                    }
                }
                FileLog.d("MZGram: color theme list from " + (items.size() - 1) + " chat themes");
                if (items.size() > 1) {
                    show(controller, account, items, true);
                }
            }

            @Override
            public void onError(TLRPC.TL_error error) {
                FileLog.d("MZGram: no chat themes for the color theme list: " + (error == null ? "no answer" : error.text));
            }
        }, false);
    }

    private static void show(MediaDataController controller, int account, ArrayList<ChatThemeBottomSheet.ChatThemeItem> items, boolean onlyOverHomeTheme) {
        ChatThemeController.chatThemeQueue.postRunnable(() -> {
            for (ChatThemeBottomSheet.ChatThemeItem item : items) {
                item.chatTheme.loadPreviewColors(account);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (onlyOverHomeTheme && hasServerThemes(controller)) {
                    return;
                }
                controller.defaultEmojiThemes.clear();
                controller.defaultEmojiThemes.addAll(items);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiPreviewThemesChanged);
            });
        });
    }
}
