/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > Chat settings (Appearance) > the color theme row
 * (MediaDataController.defaultEmojiThemes).
 *
 * Upstream fills the row only from the server: the home theme plus the
 * themes account.getThemes marks as default, and keeps the last answer. On a
 * phone that list could stay at the home theme alone: a fresh install whose
 * answer did not come, an update over a build that saved an answer without
 * usable themes (the saved list is then read back and never asked again), or
 * an answer without default themes for this app.
 *
 * MZGram builds the standard themes itself, from the color accents Telegram
 * ships for its built-in themes (Classic, Day, Night, Tinted), so the row
 * always has them: no network, no saved answer and no account needed. Each
 * local theme has the four variants the row needs, the same way the home
 * theme has (a built-in theme and one of its accents per variant), so picking
 * it works offline. Themes from the server are only added after them.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.theme.ThemeKey;
import org.telegram.ui.Components.ChatThemeBottomSheet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MZGramDefaultThemes {

    private static final String HOME_EMOJI = "🏠";

    // The label of each local theme in the row, in order. Not the emoji of
    // the server's chat themes, so a server theme is never mistaken for one.
    public static final String[] LOCAL_EMOJI = {
            "🌊", // water wave
            "🌿", // herb
            "🍑", // peach
            "🌸", // cherry blossom
            "🍇", // grapes
            "☁️", // cloud
            "🔥", // fire
            "🌙", // crescent moon
    };

    // The built-in themes of the four variants: day (Classic, Day), night (Night, Tinted)
    private static final String[] BASE_THEMES = {"Blue", "Day", "Night", "Dark Blue"};
    // The accent of each base theme the home theme already uses
    private static final int[] HOME_ACCENTS = {Theme.DEFALT_THEME_ACCENT_ID, 9, 0, 0};

    // Accents Telegram ships with a built-in theme: ids below 100; custom and
    // server accents get ids from 101 on.
    private static ArrayList<Integer> builtInAccents(Theme.ThemeInfo info, int homeAccent) {
        ArrayList<Integer> ids = new ArrayList<>();
        if (info == null || info.themeAccents == null) {
            return ids;
        }
        for (Theme.ThemeAccent accent : info.themeAccents) {
            if (accent != null && accent.info == null && accent.id < 100 && accent.id != homeAccent && accent.id != Theme.DEFALT_THEME_ACCENT_ID) {
                ids.add(accent.id);
            }
        }
        return ids;
    }

    // The standard themes, built here: LOCAL_EMOJI.length of them, or fewer
    // when a built-in theme has fewer accents.
    public static ArrayList<EmojiThemes> localThemes(int account) {
        ArrayList<ArrayList<Integer>> accents = new ArrayList<>();
        Theme.ThemeInfo[] infos = new Theme.ThemeInfo[BASE_THEMES.length];
        int count = LOCAL_EMOJI.length;
        for (int i = 0; i < BASE_THEMES.length; i++) {
            infos[i] = Theme.getTheme(BASE_THEMES[i]);
            ArrayList<Integer> ids = builtInAccents(infos[i], HOME_ACCENTS[i]);
            accents.add(ids);
            count = Math.min(count, ids.size());
        }
        ArrayList<EmojiThemes> result = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            EmojiThemes theme = new EmojiThemes(account);
            theme.emoji = LOCAL_EMOJI[k];
            theme.key = ThemeKey.ofEmoticon(theme.emoji);
            theme.chatTheme = TLRPC.ChatTheme.ofEmoticon(theme.emoji);
            theme.mzgramLocal = true;
            for (int i = 0; i < BASE_THEMES.length; i++) {
                EmojiThemes.ThemeItem item = new EmojiThemes.ThemeItem();
                item.themeInfo = infos[i];
                item.accentId = accents.get(i).get(k);
                theme.items.add(item);
            }
            result.add(theme);
        }
        return result;
    }

    public static boolean isLocal(EmojiThemes theme) {
        return theme != null && theme.mzgramLocal;
    }

    // The row: the home theme, the local standard themes, then the server's
    // themes that have the four variants (and are not repeated).
    public static ArrayList<ChatThemeBottomSheet.ChatThemeItem> list(int account, List<TLRPC.TL_theme> serverThemes) {
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = new ArrayList<>();
        items.add(new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createHomePreviewTheme(account)));
        HashSet<String> seen = new HashSet<>();
        seen.add(HOME_EMOJI);
        for (EmojiThemes theme : localThemes(account)) {
            seen.add(theme.emoji);
            items.add(new ChatThemeBottomSheet.ChatThemeItem(theme));
        }
        if (serverThemes != null) {
            for (TLRPC.TL_theme server : serverThemes) {
                EmojiThemes theme = preview(account, server);
                if (theme == null || theme.items.size() < 4) {
                    continue;
                }
                String emoticon = theme.getEmoticonOrSlug();
                if (emoticon != null && !seen.add(emoticon)) {
                    continue;
                }
                items.add(new ChatThemeBottomSheet.ChatThemeItem(theme));
            }
        }
        return items;
    }

    // The list with the home theme only, built without the network.
    public static ArrayList<ChatThemeBottomSheet.ChatThemeItem> homeOnly(int account) {
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = new ArrayList<>();
        items.add(new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createHomePreviewTheme(account)));
        return items;
    }

    // True when the list holds themes from the server's answer; until then
    // Theme.loadRemoteThemes asks for the whole list (hash 0), a saved hash
    // would get "not modified" back and the server's themes would never come.
    public static boolean hasServerThemes(MediaDataController controller) {
        for (ChatThemeBottomSheet.ChatThemeItem item : controller.defaultEmojiThemes) {
            if (item != null && item.chatTheme != null && !isLocal(item.chatTheme) && item.chatTheme.getTlTheme(0) != null) {
                return true;
            }
        }
        return false;
    }

    // A server theme as the row shows it, with four variants, or null when it
    // has no settings, or a setting without the wallpaper colors the row's
    // preview draws from.
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

    // MediaDataController: puts the row together and shows it. The local
    // themes are there at once; serverThemes may be null (nothing saved or
    // no answer yet).
    public static void publish(MediaDataController controller, int account, List<TLRPC.TL_theme> serverThemes) {
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = list(account, serverThemes);
        ChatThemeController.chatThemeQueue.postRunnable(() -> {
            for (ChatThemeBottomSheet.ChatThemeItem item : items) {
                item.chatTheme.loadPreviewColors(account);
            }
            AndroidUtilities.runOnUIThread(() -> {
                controller.defaultEmojiThemes.clear();
                controller.defaultEmojiThemes.addAll(items);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiPreviewThemesChanged);
            });
        });
    }
}
