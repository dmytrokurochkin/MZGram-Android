/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * MZGram → Other → Plugins (only in a build with -PmzgramPlugins=true):
 * the added plugins, each with its version, author and an on/off switch;
 * a tap on the row opens the plugin's own screen. "Add plugin file" picks a
 * .plugin file, reads its metadata without running it and asks before the
 * file is kept (MZGramPluginInstallSheet), with a check box for each
 * permission.
 *
 * "Run plugins" (off by default, asks first) lets the plugins that are on
 * run (MZGramPluginEngine); each row then says whether its plugin runs,
 * starts or failed.
 *
 * "For developers" turns on the developer server (MZGramPluginDevServer):
 * off by default and on only while the app runs, with its address and
 * token shown here.
 *
 * Based on exteraless plugins/ui/PluginsActivity.java (GPL-3.0): the list
 * of plugins with switches and adding from a file; written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.plugins.MZGramPluginDevServer;
import org.telegram.messenger.mzgram.plugins.MZGramPluginEngine;
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MZGramPluginsActivity extends UniversalFragment {

    private static final int BUTTON_ADD = 1;
    private static final int BUTTON_DEV_SERVER = 2;
    private static final int DEV_ADDRESS = 3;
    private static final int DEV_TOKEN = 4;
    private static final int BUTTON_JOURNAL = 5;
    private static final int BUTTON_RUN = 6;
    // Rows of the plugins: PLUGIN_ROW + their place in the list
    private static final int PLUGIN_ROW = 1000;
    private static final int REQUEST_CODE_PICK_PLUGIN = 8846;

    private List<MZGramPlugins.Plugin> plugins = new ArrayList<>();
    private final SparseArray<String> idsByRow = new SparseArray<>();
    private AlertDialog progress;
    // The developer server started, stopped or changed the plugins
    private final Runnable devServerListener = this::reload;

    @Override
    public boolean onFragmentCreate() {
        plugins = MZGramPlugins.list();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        MZGramPluginDevServer.addListener(devServerListener);
        MZGramPluginEngine.addListener(devServerListener);
        // A plugin may have been turned on, updated or deleted on its own screen
        reload();
    }

    @Override
    public void onPause() {
        super.onPause();
        MZGramPluginDevServer.removeListener(devServerListener);
        MZGramPluginEngine.removeListener(devServerListener);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        dismissProgress();
    }

    private void reload() {
        plugins = MZGramPlugins.list();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramPlugins);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        idsByRow.clear();
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_RUN, getString(R.string.MZGramPluginsRun)).setChecked(MZGramConfig.pluginsRun));
        items.add(UItem.asShadow(getString(R.string.MZGramPluginsRunInfo)));
        if (!plugins.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.MZGramPlugins)));
            for (int i = 0; i < plugins.size(); i++) {
                final MZGramPlugins.Plugin plugin = plugins.get(i);
                final int row = PLUGIN_ROW + i;
                idsByRow.put(row, plugin.meta.id);
                items.add(UItem.asButtonCheck(row, plugin.meta.name, rowSubtitle(plugin)).setChecked(plugin.enabled));
            }
        }
        items.add(UItem.asButton(BUTTON_ADD, R.drawable.msg_add, getString(R.string.MZGramPluginsAddFile)).accent());
        items.add(UItem.asButton(BUTTON_JOURNAL, R.drawable.msg_log, getString(R.string.MZGramPluginJournal)));
        items.add(UItem.asShadow(plugins.isEmpty() ? getString(R.string.MZGramPluginsEmpty) : null));

        items.add(UItem.asHeader(getString(R.string.MZGramPluginsDevHeader)));
        final String token = MZGramPluginDevServer.getToken();
        items.add(UItem.asCheck(BUTTON_DEV_SERVER, getString(R.string.MZGramPluginsDevServer)).setChecked(token != null));
        if (token != null) {
            items.add(UItem.asButton(DEV_ADDRESS, getString(R.string.MZGramPluginsDevAddress), MZGramPluginDevServer.HOST + ":" + MZGramPluginDevServer.PORT));
            items.add(UItem.asButton(DEV_TOKEN, R.drawable.msg_copy, token));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramPluginsDevInfo)));
    }

    // Version and author, and how the plugin runs when plugins may run
    private static String rowSubtitle(MZGramPlugins.Plugin plugin) {
        final String subtitle = MZGramPluginTexts.subtitle(plugin.meta);
        if (!plugin.enabled || !MZGramPluginEngine.isOn()) {
            return subtitle;
        }
        final int state;
        switch (MZGramPluginEngine.getState(plugin.meta.id)) {
            case MZGramPluginEngine.STATE_RUNNING:
                state = R.string.MZGramPluginStateRunning;
                break;
            case MZGramPluginEngine.STATE_FAILED:
                state = R.string.MZGramPluginStateFailed;
                break;
            default:
                state = R.string.MZGramPluginStateStarting;
                break;
        }
        return subtitle + " · " + getString(state);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_RUN) {
            toggleRun();
            return;
        }
        if (item.id == BUTTON_ADD) {
            pickFile();
            return;
        }
        if (item.id == BUTTON_JOURNAL) {
            presentFragment(new MZGramPluginJournalActivity(null));
            return;
        }
        if (item.id == BUTTON_DEV_SERVER) {
            toggleDevServer();
            return;
        }
        if (item.id == DEV_TOKEN) {
            final String token = MZGramPluginDevServer.getToken();
            if (token != null && AndroidUtilities.addToClipboard(token)) {
                BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramTextCopied)).show();
            }
            return;
        }
        final String pluginId = idsByRow.get(item.id);
        if (pluginId == null) {
            return;
        }
        // The switch sits at the end of the row, as in Telegram's notification rows
        final boolean onSwitch = LocaleController.isRTL ? x <= dp(76) : x >= view.getMeasuredWidth() - dp(76);
        if (onSwitch) {
            MZGramPlugins.setEnabled(pluginId, !item.checked);
            reload();
        } else {
            presentFragment(new MZGramPluginInfoActivity(pluginId));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void toggleRun() {
        if (MZGramConfig.pluginsRun) {
            MZGramConfig.togglePluginsRun();
            MZGramPluginEngine.onRunChanged();
            reload();
            return;
        }
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.MZGramPluginsRun));
        builder.setMessage(getString(R.string.MZGramPluginsRunConfirm));
        builder.setPositiveButton(getString(R.string.MZGramPluginsRunTurnOn), (dialog, which) -> {
            if (!MZGramConfig.pluginsRun) {
                MZGramConfig.togglePluginsRun();
                MZGramPluginEngine.onRunChanged();
            }
            reload();
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        showDialog(builder.create());
    }

    private void toggleDevServer() {
        if (MZGramPluginDevServer.isRunning()) {
            MZGramPluginDevServer.stop();
            reload();
            return;
        }
        MZGramPluginDevServer.start(ok -> {
            if (isFinishing() || getParentActivity() == null) {
                return;
            }
            if (!ok) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginsDevFailed)).show();
            }
            reload();
        });
    }

    private void pickFile() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // .plugin files have no media type of their own
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_PLUGIN);
        } catch (Exception e) {
            FileLog.e("MZGramPluginsActivity.pickFile", e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_PICK_PLUGIN || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        showProgress();
        // The first read starts Python, which takes a few seconds
        MZGramPlugins.readPicked(data.getData(), (picked, result) -> {
            dismissProgress();
            if (isFinishing() || getParentActivity() == null) {
                MZGramPlugins.discard(picked);
                return;
            }
            if (picked == null || !result.isOk()) {
                showReadError(result.error);
            } else {
                confirmInstall(picked, result.meta);
            }
        });
    }

    private void showReadError(String error) {
        MZGramPluginInstallSheet.showReadError(this, error);
    }

    private void confirmInstall(File picked, MZGramPluginFormat.Meta meta) {
        MZGramPluginInstallSheet.show(this, picked, meta, this::reload);
    }

    private void showProgress() {
        dismissProgress();
        if (getParentActivity() == null) {
            return;
        }
        progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
    }

    private void dismissProgress() {
        if (progress != null) {
            try {
                progress.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            progress = null;
        }
    }
}
