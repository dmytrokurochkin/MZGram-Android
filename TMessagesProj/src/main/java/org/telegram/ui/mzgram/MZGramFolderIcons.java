/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Look of the folder tabs on the chat list: names only (as in Telegram, the
 * default), icons with names, or icons only. The icon is the emoticon of the
 * folder from the server (MZGramFolderEmoticons) or, without one, a guess
 * from the folder type. It is put in the tab title as an image span tinted
 * with the text color, so the tab animations, counters and the active color
 * of Telegram stay as they are.
 *
 * Folder icons and the type guess from Nekogram FolderIconHelper (GPL-3.0);
 * the setting from exteraless TabIconsMode / FilterTabsPreviewCell (GPL-3.0).
 * Written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramFolderEmoticons;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.FilterTabsView;

import java.util.HashMap;

public final class MZGramFolderIcons {

    public static final int TITLES = 0;
    public static final int ICONS_AND_TITLES = 1;
    public static final int ICONS = 2;

    public static final int[] MODE_TITLES = {
        R.string.MZGramFolderTabsTitles,
        R.string.MZGramFolderTabsIconsAndTitles,
        R.string.MZGramFolderTabsIcons
    };

    private static final String ALL = "💬";
    private static final String CUSTOM = "📁";

    // The emoticons Telegram offers for folders, as in Nekogram FolderIconHelper
    private static final HashMap<String, Integer> icons = new HashMap<>();

    static {
        icons.put("🐱", R.drawable.mzgram_folder_cat);
        icons.put("📕", R.drawable.mzgram_folder_book);
        icons.put("💰", R.drawable.mzgram_folder_money);
        icons.put("🎮", R.drawable.mzgram_folder_game);
        icons.put("💡", R.drawable.mzgram_folder_light);
        icons.put("👌", R.drawable.mzgram_folder_like);
        icons.put("🎵", R.drawable.mzgram_folder_note);
        icons.put("🎨", R.drawable.mzgram_folder_palette);
        icons.put("✈", R.drawable.mzgram_folder_travel);
        icons.put("⚽", R.drawable.mzgram_folder_sport);
        icons.put("⭐", R.drawable.mzgram_folder_favorite);
        icons.put("🎓", R.drawable.mzgram_folder_study);
        icons.put("🛫", R.drawable.mzgram_folder_airplane);
        icons.put("👤", R.drawable.mzgram_folder_private);
        icons.put("👥", R.drawable.mzgram_folder_group);
        icons.put(ALL, R.drawable.mzgram_folder_all);
        icons.put("✅", R.drawable.mzgram_folder_unread);
        icons.put("🤖", R.drawable.mzgram_folder_bots);
        icons.put("👑", R.drawable.mzgram_folder_crown);
        icons.put("🌹", R.drawable.mzgram_folder_flower);
        icons.put("🏠", R.drawable.mzgram_folder_home);
        icons.put("❤", R.drawable.mzgram_folder_love);
        icons.put("🎭", R.drawable.mzgram_folder_mask);
        icons.put("🍸", R.drawable.mzgram_folder_party);
        icons.put("📈", R.drawable.mzgram_folder_trade);
        icons.put("💼", R.drawable.mzgram_folder_work);
        icons.put("🔔", R.drawable.mzgram_folder_unmuted);
        icons.put("📢", R.drawable.mzgram_folder_channels);
        icons.put(CUSTOM, R.drawable.mzgram_folder_custom);
        icons.put("📋", R.drawable.mzgram_folder_setup);
    }

    private MZGramFolderIcons() {
    }

    public static int mode() {
        final int mode = MZGramConfig.folderTabIcons;
        return mode == ICONS_AND_TITLES || mode == ICONS ? mode : TITLES;
    }

    static int iconRes(String emoticon) {
        if (emoticon != null) {
            // Desktop and iOS may send the emoji with the variation selector
            final Integer res = icons.get(emoticon.replace("️", ""));
            if (res != null) {
                return res;
            }
        }
        return R.drawable.mzgram_folder_custom;
    }

    /** The icon of a folder without an emoticon, from its chat types. */
    static String guess(MessagesController.DialogFilter filter) {
        if (filter.isDefault()) {
            return ALL;
        }
        final int all = MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
        final int types = filter.flags & all;
        if (types == all) {
            if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                return "✅";
            }
            if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                return "🔔";
            }
            return CUSTOM;
        }
        final int people = MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
        if (types != 0 && (types & ~people) == 0) {
            return "👤";
        }
        if (types == MessagesController.DIALOG_FILTER_FLAG_GROUPS) {
            return "👥";
        }
        if (types == MessagesController.DIALOG_FILTER_FLAG_CHANNELS) {
            return "📢";
        }
        if (types == MessagesController.DIALOG_FILTER_FLAG_BOTS) {
            return "🤖";
        }
        return CUSTOM;
    }

    private static ColoredImageSpan span(int res, boolean only) {
        final ColoredImageSpan span = new ColoredImageSpan(res);
        span.setSize(dp(only ? 22 : 19));
        return span;
    }

    /** The title with the icon: the emoticon stays in the text for screen readers. */
    private static CharSequence decorate(CharSequence title, String plainName, String emoticon) {
        final int mode = mode();
        if (mode == TITLES) {
            return title;
        }
        final int res = iconRes(emoticon);
        if (mode == ICONS) {
            // The whole name under one span: the tab shows the icon, TalkBack reads the name
            final String name = TextUtils.isEmpty(plainName) ? emoticon : plainName;
            final SpannableStringBuilder text = new SpannableStringBuilder(name);
            text.setSpan(span(res, true), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }
        final SpannableStringBuilder text = new SpannableStringBuilder(emoticon);
        text.setSpan(span(res, false), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(' ');
        text.append(title);
        return text;
    }

    /**
     * Title of a folder tab with its icon, or null for names only (then
     * Telegram's own call adds the tab).
     */
    public static CharSequence tabTitle(FilterTabsView tabs, int account, MessagesController.DialogFilter filter) {
        if (mode() == TITLES || tabs == null || filter == null) {
            return null;
        }
        MZGramFolderEmoticons.ensureLoaded(account);
        if (filter.isDefault()) {
            final String name = LocaleController.getString(R.string.FilterAllChats);
            return defaultTabTitle(tabs.text(name, null), name);
        }
        String emoticon = MZGramFolderEmoticons.get(account, filter.id);
        if (TextUtils.isEmpty(emoticon)) {
            emoticon = guess(filter);
        }
        return decorate(tabs.text(filter.name, filter.entities), filter.name, emoticon);
    }

    private static final HashMap<String, CharSequence> defaultTitles = new HashMap<>();

    /**
     * Title of the "All chats" tab, set again by FilterTabsView on every
     * layout: the same text object is returned for the same name, so the tab
     * does not see a changed title and does not animate.
     */
    public static CharSequence defaultTabTitle(CharSequence title, String plainName) {
        final int mode = mode();
        if (mode == TITLES || plainName == null) {
            return title;
        }
        final String key = mode + ":" + plainName;
        CharSequence cached = defaultTitles.get(key);
        if (cached == null) {
            cached = decorate(title, plainName, ALL);
            defaultTitles.put(key, cached);
        }
        return cached;
    }

    public static void choose(BaseFragment fragment, Runnable onChanged) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final CharSequence[] names = new CharSequence[MODE_TITLES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = LocaleController.getString(MODE_TITLES[i]);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MZGramFolderTabs));
        builder.setItems(names, (dialog, which) -> {
            MZGramConfig.setFolderTabIcons(which);
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogFiltersUpdated);
                }
            }
            if (onChanged != null) {
                onChanged.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }
}
