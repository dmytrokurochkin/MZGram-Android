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
 * list holds the home theme alone instead of nothing. That list is not the
 * server's list: Theme.loadRemoteThemes asks with the saved hash only once
 * the server's themes are in it (hasServerThemes), otherwise the answer is
 * "not modified" and the emoji themes never come.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.Components.ChatThemeBottomSheet;

import java.util.ArrayList;

public class MZGramDefaultThemes {

    private static final String HOME_EMOJI = "🏠";

    // The list with the home theme only, built without the network.
    public static ArrayList<ChatThemeBottomSheet.ChatThemeItem> homeOnly(int account) {
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = new ArrayList<>();
        items.add(new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createHomePreviewTheme(account)));
        return items;
    }

    // True when the list has a theme from the server, not only the home one.
    public static boolean hasServerThemes(MediaDataController controller) {
        for (ChatThemeBottomSheet.ChatThemeItem item : controller.defaultEmojiThemes) {
            if (item != null && item.chatTheme != null && !HOME_EMOJI.equals(item.chatTheme.getEmoticonOrSlug())) {
                return true;
            }
        }
        return false;
    }

    // MediaDataController, when the server gave no default themes: the home
    // theme, until the next theme list request brings the rest.
    public static void fill(MediaDataController controller, int account) {
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = homeOnly(account);
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
