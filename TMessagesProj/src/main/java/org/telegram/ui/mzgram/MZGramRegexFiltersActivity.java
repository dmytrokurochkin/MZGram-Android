/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Message filter rules (MZGramRegexFilters). Without a chat: the switches
 * of the filters, the common rules and the chats that have rules of their
 * own; with a chat: the rules of that chat only. A tap on a rule opens the
 * editor (MZGramRegexFilterEditor), a long tap turns it off or on. The main
 * screen also lists the chats left out of all filters (filters.minor, dialog
 * exclusions; also from the profile menu of a chat), a chat's screen has the
 * switch for it.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramRegexFilters;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collections;

public class MZGramRegexFiltersActivity extends UniversalFragment {

    private static final int ROW_IN_PRIVATE = 1;
    private static final int ROW_NO_DOWNLOAD = 2;
    private static final int ROW_ADD_RULE = 3;
    private static final int ROW_ADD_CHAT = 4;
    private static final int ROW_CHAT_EXCLUDED = 5;
    private static final int ROW_ADD_EXCLUDED = 6;
    private static final int RULE_ROW = 1000;
    private static final int CHAT_ROW = 2000;
    private static final int EXCLUDED_ROW = 100000;
    private static final int SHOWN_PATTERN = 80;

    private final long dialogId;
    private final ArrayList<MZGramRegexFilters.Rule> rules = new ArrayList<>();
    private final ArrayList<Long> chats = new ArrayList<>();
    private final ArrayList<Long> excluded = new ArrayList<>();

    /** The screen of the switches, the common rules and the chats with rules. */
    public MZGramRegexFiltersActivity() {
        this(MZGramRegexFilters.COMMON);
    }

    /** The rules of one chat, or the main screen for MZGramRegexFilters.COMMON. */
    public MZGramRegexFiltersActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    private String nameOf(long peerId) {
        String name = DialogObject.getName(currentAccount, peerId);
        return TextUtils.isEmpty(name) ? String.valueOf(peerId) : name;
    }

    @Override
    protected CharSequence getTitle() {
        return dialogId == MZGramRegexFilters.COMMON ? getString(R.string.MZGramRegexFilters) : nameOf(dialogId);
    }

    private static String shown(String pattern) {
        String oneLine = pattern.replace('\n', ' ');
        return oneLine.length() > SHOWN_PATTERN ? oneLine.substring(0, SHOWN_PATTERN) + "..." : oneLine;
    }

    private static String stateOf(MZGramRegexFilters.Rule rule) {
        if (!rule.isValid()) {
            return getString(R.string.MZGramRegexFilterBroken);
        }
        if (!rule.enabled) {
            return getString(R.string.MZGramRegexFilterOff);
        }
        return rule.reversed ? getString(R.string.MZGramRegexFilterReversedShort) : "";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rules.clear();
        rules.addAll(MZGramRegexFilters.rules(dialogId));
        boolean common = dialogId == MZGramRegexFilters.COMMON;
        if (common) {
            items.add(UItem.asCheck(ROW_IN_PRIVATE, getString(R.string.MZGramRegexFiltersInPrivate)).setChecked(MZGramConfig.regexFiltersInPrivate));
            items.add(UItem.asCheck(ROW_NO_DOWNLOAD, getString(R.string.MZGramRegexFiltersNoDownload)).setChecked(MZGramConfig.regexFiltersNoDownload));
            items.add(UItem.asShadow(getString(R.string.MZGramRegexFiltersSwitchesInfo)));
            items.add(UItem.asHeader(getString(R.string.MZGramRegexFiltersCommon)));
        } else {
            items.add(UItem.asCheck(ROW_CHAT_EXCLUDED, getString(R.string.MZGramRegexFiltersExclude)).setChecked(MZGramRegexFilters.isExcluded(dialogId)));
            items.add(UItem.asShadow(getString(R.string.MZGramRegexFiltersExcludeInfo)));
            items.add(UItem.asHeader(getString(R.string.MZGramRegexFiltersChatRules)));
        }
        items.add(UItem.asButton(ROW_ADD_RULE, R.drawable.msg_add, getString(R.string.MZGramRegexFilterAdd)).accent());
        for (int i = 0, N = rules.size(); i < N; i++) {
            MZGramRegexFilters.Rule rule = rules.get(i);
            UItem row = UItem.asButton(RULE_ROW + i, shown(rule.pattern), stateOf(rule));
            items.add(rule.isValid() ? row : row.red());
        }
        items.add(UItem.asShadow(getString(common ? R.string.MZGramRegexFiltersCommonInfo : R.string.MZGramRegexFiltersChatInfo)));
        if (!common) {
            return;
        }
        chats.clear();
        chats.addAll(MZGramRegexFilters.chatsWithRules());
        Collections.sort(chats);
        items.add(UItem.asHeader(getString(R.string.MZGramRegexFiltersChats)));
        items.add(UItem.asButton(ROW_ADD_CHAT, R.drawable.msg_add, getString(R.string.MZGramRegexFiltersAddChat)).accent());
        for (int i = 0, N = chats.size(); i < N; i++) {
            long chat = chats.get(i);
            items.add(UItem.asButton(CHAT_ROW + i, nameOf(chat), String.valueOf(MZGramRegexFilters.count(chat))));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramRegexFiltersChatsInfo)));
        excluded.clear();
        excluded.addAll(MZGramRegexFilters.excluded());
        Collections.sort(excluded);
        items.add(UItem.asHeader(getString(R.string.MZGramRegexFiltersExcluded)));
        items.add(UItem.asButton(ROW_ADD_EXCLUDED, R.drawable.msg_add, getString(R.string.MZGramRegexFiltersAddChat)).accent());
        for (int i = 0, N = excluded.size(); i < N; i++) {
            items.add(UItem.asButton(EXCLUDED_ROW + i, nameOf(excluded.get(i)), ""));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramRegexFiltersExcludedInfo)));
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    /** Picks a chat: for its own rules, or (exclude) to leave it out of the filters. */
    private void pickChat(boolean exclude) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("allowGlobalSearch", false);
        args.putBoolean("checkCanWrite", false);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) {
                return false;
            }
            long chosen = dids.get(0).dialogId;
            if (chosen == 0 || chosen == getUserConfig().getClientUserId()) {
                return false;
            }
            if (exclude) {
                MZGramRegexFilters.setExcluded(chosen, true);
                fragment.finishFragment();
                update();
            } else {
                presentFragment(new MZGramRegexFiltersActivity(chosen), true);
            }
            return true;
        });
        presentFragment(picker);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_IN_PRIVATE) {
            MZGramConfig.toggleRegexFiltersInPrivate();
            ((TextCheckCell) view).setChecked(MZGramConfig.regexFiltersInPrivate);
        } else if (item.id == ROW_NO_DOWNLOAD) {
            MZGramConfig.toggleRegexFiltersNoDownload();
            ((TextCheckCell) view).setChecked(MZGramConfig.regexFiltersNoDownload);
        } else if (item.id == ROW_ADD_RULE) {
            MZGramRegexFilterEditor.show(this, dialogId, -1, null, 0, this::update);
        } else if (item.id == ROW_CHAT_EXCLUDED) {
            MZGramRegexFilters.setExcluded(dialogId, !MZGramRegexFilters.isExcluded(dialogId));
            ((TextCheckCell) view).setChecked(MZGramRegexFilters.isExcluded(dialogId));
        } else if (item.id == ROW_ADD_CHAT) {
            pickChat(false);
        } else if (item.id == ROW_ADD_EXCLUDED) {
            pickChat(true);
        } else if (item.id >= EXCLUDED_ROW && item.id < EXCLUDED_ROW + excluded.size()) {
            presentFragment(new MZGramRegexFiltersActivity(excluded.get(item.id - EXCLUDED_ROW)));
        } else if (item.id >= RULE_ROW && item.id < RULE_ROW + rules.size()) {
            MZGramRegexFilterEditor.show(this, dialogId, item.id - RULE_ROW, null, 0, this::update);
        } else if (item.id >= CHAT_ROW && item.id < CHAT_ROW + chats.size()) {
            presentFragment(new MZGramRegexFiltersActivity(chats.get(item.id - CHAT_ROW)));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= RULE_ROW && item.id < RULE_ROW + rules.size()) {
            int index = item.id - RULE_ROW;
            MZGramRegexFilters.Rule rule = rules.get(index);
            MZGramRegexFilters.replaceRule(dialogId, index, rule.withEnabled(!rule.enabled));
            update();
            return true;
        }
        if (item.id >= EXCLUDED_ROW && item.id < EXCLUDED_ROW + excluded.size()) {
            MZGramRegexFilters.setExcluded(excluded.get(item.id - EXCLUDED_ROW), false);
            update();
            return true;
        }
        if (item.id >= CHAT_ROW && item.id < CHAT_ROW + chats.size() && getParentActivity() != null) {
            long chat = chats.get(item.id - CHAT_ROW);
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle(nameOf(chat));
            builder.setMessage(getString(R.string.MZGramRegexFiltersClearChatConfirm));
            builder.setPositiveButton(getString(R.string.MZGramRegexFiltersClearChat), (dialog, which) -> {
                MZGramRegexFilters.setRules(chat, null);
                update();
            });
            builder.setNegativeButton(getString(R.string.MZGramCancel), null);
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button instanceof android.widget.TextView) {
                ((android.widget.TextView) button).setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_text_RedBold));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }
}
