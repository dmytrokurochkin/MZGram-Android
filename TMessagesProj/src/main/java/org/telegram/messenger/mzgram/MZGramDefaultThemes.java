/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > Chat settings (Appearance) > Color theme lists
 * MediaDataController.defaultEmojiThemes: the home theme (Classic, Day,
 * Night, Tinted) and the theme set the server marks as default in
 * account.getThemes. When that answer has no default themes, or has not
 * come yet, the list was left empty and the screen showed no themes at all.
 *
 * Here the list always starts with the home theme, built from the app's own
 * themes, and the rest is filled from the chat themes (account.getChatThemes,
 * the same set the chat theme picker shows).
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
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

    // MediaDataController, when the server gave no default themes: shows the
    // home theme right away, then adds the chat themes once they are loaded.
    public static void fill(MediaDataController controller, int account) {
        apply(controller, account, homeOnly(account));
        ChatThemeController.getInstance(account).requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> result) {
                ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = homeOnly(account);
                if (result != null) {
                    for (EmojiThemes theme : result) {
                        if (theme != null && theme.items.size() >= 4 && !HOME_EMOJI.equals(theme.getEmoticonOrSlug())) {
                            items.add(new ChatThemeBottomSheet.ChatThemeItem(theme));
                        }
                    }
                }
                apply(controller, account, items);
            }

            @Override
            public void onError(TLRPC.TL_error error) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("MZGram: chat themes not loaded, " + (error != null ? error.text : "no answer"));
                }
            }
        }, false);
    }

    private static void apply(MediaDataController controller, int account, ArrayList<ChatThemeBottomSheet.ChatThemeItem> items) {
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
