package org.mzgram.appearance;

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

/**
 * The color theme row of Appearance (MediaDataController.defaultEmojiThemes).
 *
 * Telegram fills the row only from the server: the home theme plus the themes
 * account.getThemes marks as default, and keeps the last answer. The row could stay
 * at the home theme alone: a fresh install whose answer did not come, an update over
 * a build that saved an answer without usable themes, or an answer without default
 * themes for this app.
 *
 * MZGram builds the standard themes on the device, from the color accents shipped
 * with the built-in themes (Classic, Day, Night, Tinted), so the row always has them:
 * no network, no saved answer and no account needed. Each one has the four variants
 * the row needs, as the home theme has, so picking it works offline. Themes from the
 * server are added after them.
 */
public final class StandardThemes {

    private static final String HOME_EMOJI = "🏠";

    /** The label of each standard theme in the row, in order; not one of the server's chat theme emoji. */
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

    /** The built-in themes of the four variants: day (Classic, Day), night (Night, Tinted). */
    private static final String[] BASE_THEMES = {"Blue", "Day", "Night", "Dark Blue"};
    /** The accent of each base theme the home theme already uses. */
    private static final int[] HOME_ACCENTS = {Theme.DEFALT_THEME_ACCENT_ID, 9, 0, 0};

    private StandardThemes() {
    }

    /** Accents shipped with a built-in theme have ids below 100; custom and server accents start at 101. */
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

    /** The standard themes: LOCAL_EMOJI.length of them, or fewer when a built-in theme has fewer accents. */
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
            theme.standardLocal = true;
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
        return theme != null && theme.standardLocal;
    }

    /** The row: the home theme, the standard themes, then the server's themes with four variants, none repeated. */
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

    /**
     * True when the row holds themes from the server's answer. Until then Theme.loadRemoteThemes
     * asks for the whole list (hash 0): a saved hash would get "not modified" back and the
     * server's themes would never come.
     */
    public static boolean hasServerThemes(MediaDataController controller) {
        for (ChatThemeBottomSheet.ChatThemeItem item : controller.defaultEmojiThemes) {
            if (item != null && item.chatTheme != null && !isLocal(item.chatTheme) && item.chatTheme.getTlTheme(0) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * A server theme as the row shows it, with four variants, or null when it has no settings
     * or a setting without the wallpaper colors the row's preview draws from.
     */
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

    /** Puts the row together and shows it. The standard themes are there at once; serverThemes may be null. */
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
