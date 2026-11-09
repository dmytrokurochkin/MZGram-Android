/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The activity log of one plugin, or of all plugins: what was done with
 * them and what they did (MZGramPluginAuditJournal), newest first. A row
 * opens to the full entry on a tap and is copied on a long tap; a switch
 * shows only blocked actions and errors; the log can be searched, shared as
 * a text file and cleared.
 *
 * Based on exteraless plugins/ui/PluginActivityLogActivity (GPL-3.0): one
 * line per action in plain words, rows that open to the full detail, search,
 * export and clear; written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramLanguage;
import org.telegram.messenger.mzgram.MZGramProfilerReports;
import org.telegram.messenger.mzgram.plugins.MZGramPluginAuditJournal;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MZGramPluginJournalActivity extends UniversalFragment {

    private static final int MENU_SEARCH = 1;
    private static final int BUTTON_PROBLEMS = 1;
    private static final int BUTTON_SHARE = 2;
    private static final int BUTTON_CLEAR = 3;
    // Rows of the entries: ENTRY_ROW + their place in the list from the journal
    private static final int ENTRY_ROW = 1000;
    private static final int MAX_ROWS = 300;

    /** null: all plugins. */
    private final String pluginId;
    private List<MZGramPluginAuditJournal.Entry> entries = new ArrayList<>();
    private final Set<Integer> expanded = new HashSet<>();
    private boolean problemsOnly;
    private String query;

    public MZGramPluginJournalActivity(String pluginId) {
        this.pluginId = pluginId;
    }

    @Override
    public boolean onFragmentCreate() {
        entries = MZGramPluginAuditJournal.entries(pluginId);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        if (pluginId != null) {
            actionBar.setSubtitle(pluginName(pluginId));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        final ActionBarMenu menu = actionBar.createMenu();
        final ActionBarMenuItem search = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        query = null;
                        update();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        final String text = editText.getText().toString().trim();
                        query = text.isEmpty() ? null : text.toLowerCase(Locale.ROOT);
                        update();
                    }
                });
        search.setSearchFieldHint(getString(R.string.MZGramSearch));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        entries = MZGramPluginAuditJournal.entries(pluginId);
        expanded.clear();
        update();
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramPluginJournal);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_PROBLEMS, getString(R.string.MZGramPluginJournalProblems)).setChecked(problemsOnly));
        items.add(UItem.asShadow(null));

        int shown = 0;
        for (int i = entries.size() - 1; i >= 0 && shown < MAX_ROWS; i--) {
            final MZGramPluginAuditJournal.Entry entry = entries.get(i);
            if (problemsOnly && entry.level == MZGramPluginAuditJournal.LEVEL_INFO) {
                continue;
            }
            final CharSequence title = rowTitle(entry);
            if (query != null && !matches(entry, title)) {
                continue;
            }
            if (shown == 0) {
                items.add(UItem.asHeader(getString(R.string.MZGramPluginJournal)));
            }
            final UItem row = UItem.asButton(ENTRY_ROW + i, title, rowValue(entry));
            if (entry.level != MZGramPluginAuditJournal.LEVEL_INFO) {
                row.red();
            }
            items.add(row);
            if (expanded.contains(i)) {
                items.add(UItem.asShadow(fullText(entry)));
            }
            shown++;
        }
        if (shown == 0) {
            items.add(UItem.asShadow(getString(query != null ? R.string.MZGramPluginJournalNotFound : R.string.MZGramPluginJournalEmpty)));
        } else {
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asButton(BUTTON_SHARE, R.drawable.msg_share, getString(R.string.MZGramPluginJournalShare)));
        items.add(UItem.asButton(BUTTON_CLEAR, R.drawable.msg_clear, getString(R.string.MZGramPluginJournalClear)).red());
        items.add(UItem.asShadow(getString(R.string.MZGramPluginJournalInfo)));
    }

    private boolean matches(MZGramPluginAuditJournal.Entry entry, CharSequence title) {
        return contains(title, query) || contains(entry.detail, query) || contains(entry.pluginId, query)
                || contains(entry.event, query);
    }

    private static boolean contains(CharSequence text, String query) {
        return text != null && text.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_PROBLEMS) {
            problemsOnly = !problemsOnly;
            update();
        } else if (item.id == BUTTON_SHARE) {
            share();
        } else if (item.id == BUTTON_CLEAR) {
            confirmClear();
        } else if (item.id >= ENTRY_ROW && item.id < ENTRY_ROW + entries.size()) {
            final int index = item.id - ENTRY_ROW;
            if (!expanded.remove(index)) {
                expanded.add(index);
            }
            update();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < ENTRY_ROW || item.id >= ENTRY_ROW + entries.size()) {
            return false;
        }
        if (AndroidUtilities.addToClipboard(fullText(entries.get(item.id - ENTRY_ROW)))) {
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramTextCopied)).show();
        }
        return true;
    }

    // ---------- texts ----------

    private static String pluginName(String pluginId) {
        final MZGramPlugins.Plugin plugin = MZGramPlugins.get(pluginId);
        return plugin != null ? plugin.meta.name : pluginId;
    }

    private CharSequence rowTitle(MZGramPluginAuditJournal.Entry entry) {
        final String text = describe(entry);
        return pluginId == null ? pluginName(entry.pluginId) + ": " + text : text;
    }

    private static String describe(MZGramPluginAuditJournal.Entry entry) {
        final String detail = entry.detail == null ? "" : entry.detail;
        String text;
        switch (entry.event) {
            case MZGramPluginAuditJournal.EVENT_ADDED:
                text = detail.isEmpty() ? getString(R.string.MZGramPluginJournalAddedNoVersion)
                        : LocaleController.formatString(R.string.MZGramPluginJournalAdded, detail);
                break;
            case MZGramPluginAuditJournal.EVENT_UPDATED:
                text = LocaleController.formatString(R.string.MZGramPluginJournalUpdated,
                        TextUtils.isEmpty(entry.previous) ? "?" : entry.previous, detail.isEmpty() ? "?" : detail);
                break;
            case MZGramPluginAuditJournal.EVENT_DELETED:
                text = getString(R.string.MZGramPluginJournalDeleted);
                break;
            case MZGramPluginAuditJournal.EVENT_ENABLED:
                text = getString(R.string.MZGramPluginJournalEnabled);
                break;
            case MZGramPluginAuditJournal.EVENT_DISABLED:
                text = getString(R.string.MZGramPluginJournalDisabled);
                break;
            case MZGramPluginAuditJournal.EVENT_GRANTED:
                text = LocaleController.formatString(R.string.MZGramPluginJournalGranted, MZGramPluginTexts.permissionName(detail));
                break;
            case MZGramPluginAuditJournal.EVENT_REVOKED:
                text = LocaleController.formatString(R.string.MZGramPluginJournalRevoked, MZGramPluginTexts.permissionName(detail));
                break;
            case MZGramPluginAuditJournal.EVENT_GRANTS_DROPPED:
                text = getString(R.string.MZGramPluginJournalGrantsDropped);
                break;
            case MZGramPluginAuditJournal.EVENT_HOOK_PROTECTED:
                text = LocaleController.formatString(R.string.MZGramPluginJournalHookProtected, detail);
                break;
            case MZGramPluginAuditJournal.EVENT_HOOK_NO_PERMISSION:
                text = LocaleController.formatString(R.string.MZGramPluginJournalHookNoPermission, detail);
                break;
            case MZGramPluginAuditJournal.EVENT_HOOK_DENIED:
                text = LocaleController.formatString(R.string.MZGramPluginJournalHookDenied, detail);
                break;
            case MZGramPluginAuditJournal.EVENT_HOOK_FAILED:
                text = LocaleController.formatString(R.string.MZGramPluginJournalHookFailed, detail);
                break;
            case MZGramPluginAuditJournal.EVENT_STARTED:
                text = getString(R.string.MZGramPluginJournalStarted);
                break;
            case MZGramPluginAuditJournal.EVENT_START_FAILED:
                text = LocaleController.formatString(R.string.MZGramPluginJournalStartFailed, detail);
                break;
            case MZGramPluginAuditJournal.EVENT_STOPPED:
                text = getString(R.string.MZGramPluginJournalStopped);
                break;
            case MZGramPluginAuditJournal.EVENT_ERROR:
                text = LocaleController.formatString(R.string.MZGramPluginJournalError, detail);
                break;
            case MZGramPluginAuditJournal.EVENT_LOG:
                text = LocaleController.formatString(R.string.MZGramPluginJournalLog, detail);
                break;
            default:
                // Events added later (the gates of the plugin engine)
                text = detail.isEmpty() ? entry.event : entry.event + " · " + detail;
                break;
        }
        if (MZGramPluginAuditJournal.SOURCE_DEV_SERVER.equals(entry.source)) {
            text = LocaleController.formatString(R.string.MZGramPluginJournalDevServer, text);
        }
        return text;
    }

    private static CharSequence rowValue(MZGramPluginAuditJournal.Entry entry) {
        final String time = MZGramLanguage.formatDayMonthTime(entry.ts);
        if (entry.count > 1) {
            return LocaleController.formatString(R.string.MZGramPluginJournalRepeat, entry.count) + ", " + time;
        }
        return time;
    }

    private static String exactTime(long ts) {
        return MZGramLanguage.formatDateTime(ts, true);
    }

    /** The whole entry: what it says, the plugin id, the raw event and detail, the time. */
    private static String fullText(MZGramPluginAuditJournal.Entry entry) {
        final StringBuilder sb = new StringBuilder();
        sb.append(describe(entry)).append('\n');
        sb.append(entry.pluginId).append(", ").append(entry.event);
        if (!TextUtils.isEmpty(entry.detail)) {
            sb.append('\n').append(entry.detail);
        }
        sb.append('\n').append(exactTime(entry.ts));
        if (entry.count > 1) {
            sb.append(", ").append(LocaleController.formatString(R.string.MZGramPluginJournalRepeat, entry.count));
        }
        return sb.toString();
    }

    // ---------- actions ----------

    private void share() {
        final List<MZGramPluginAuditJournal.Entry> all = MZGramPluginAuditJournal.entries(pluginId);
        if (all.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginJournalEmpty)).show();
            return;
        }
        final StringBuilder text = new StringBuilder();
        text.append(getString(R.string.MZGramPluginJournal));
        if (pluginId != null) {
            text.append(": ").append(pluginName(pluginId)).append(" (").append(pluginId).append(')');
        }
        text.append("\n\n");
        for (MZGramPluginAuditJournal.Entry entry : all) {
            if (pluginId == null) {
                text.append(pluginName(entry.pluginId)).append(": ");
            }
            text.append(fullText(entry)).append("\n\n");
        }
        final String content = text.toString();
        Utilities.globalQueue.postRunnable(() -> {
            final File file = write(content);
            AndroidUtilities.runOnUIThread(() -> {
                if (isFinishing() || getParentActivity() == null) {
                    return;
                }
                if (!MZGramProfilerReports.share(getParentActivity(), file)) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginFileFailed)).show();
                }
            });
        });
    }

    /** The log as a text file in the logs folder, which the app's FileProvider gives out. */
    private static File write(String content) {
        final File dir = AndroidUtilities.getLogsDir();
        if (dir == null) {
            return null;
        }
        final String stamp = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss", Locale.US).format(new Date());
        final File file = new File(dir, "mzgram_plugin_log-" + stamp + ".txt");
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void confirmClear() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.MZGramPluginJournalClear));
        builder.setMessage(getString(R.string.MZGramPluginJournalClearConfirm));
        builder.setPositiveButton(getString(R.string.MZGramPluginJournalClearButton), (dialog, which) -> {
            MZGramPluginAuditJournal.clear(pluginId);
            reload();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramPluginJournalCleared)).show();
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        builder.makeRed(AlertDialog.BUTTON_POSITIVE);
        showDialog(builder.create());
    }
}
