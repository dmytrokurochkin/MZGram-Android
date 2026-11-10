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
 * starts or failed. When the hook engine killed MZGram twice while it
 * started (MZGramPluginHookGate), the hooks are off and a button under the
 * switch tries them again with the next start.
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
import android.net.Uri;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.plugins.MZGramPluginDevServer;
import org.telegram.messenger.mzgram.plugins.MZGramPluginEngine;
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat;
import org.telegram.messenger.mzgram.plugins.MZGramPluginHookGate;
import org.telegram.messenger.mzgram.plugins.MZGramPluginWatchdog;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.messenger.mzgram.plugins.MZGramPluginsBackup;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MZGramPluginsActivity extends UniversalFragment {

    private static final int BUTTON_ADD = 1;
    private static final int BUTTON_DEV_SERVER = 2;
    private static final int DEV_ADDRESS = 3;
    private static final int DEV_TOKEN = 4;
    private static final int BUTTON_JOURNAL = 5;
    private static final int BUTTON_RUN = 6;
    private static final int BUTTON_HOOKS_RETRY = 7;
    private static final int BUTTON_SAFE_MODE_EXIT = 8;
    private static final int BUTTON_PIP = 9;
    private static final int BUTTON_CATALOG = 10;
    private static final int MENU_EXPORT_BACKUP = 11;
    private static final int MENU_IMPORT_BACKUP = 12;
    // Rows of the plugins: PLUGIN_ROW + their place in the list
    private static final int PLUGIN_ROW = 1000;
    private static final int REQUEST_CODE_PICK_PLUGIN = 8846;
    private static final int REQUEST_CODE_PICK_BACKUP = 8852;

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
    public View createView(android.content.Context context) {
        View view = super.createView(context);
        if (actionBar != null) {
            org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
            org.telegram.ui.ActionBar.ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
            item.addSubItem(MENU_EXPORT_BACKUP, R.drawable.msg_share, getString(R.string.MZGramPluginsExportBackup));
            item.addSubItem(MENU_IMPORT_BACKUP, R.drawable.msg_download, getString(R.string.MZGramPluginsImportBackup));
            actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == MENU_EXPORT_BACKUP) {
                        exportPluginsBackup();
                    } else if (id == MENU_IMPORT_BACKUP) {
                        pickPluginsBackup();
                    }
                }
            });
        }
        return view;
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
        if (MZGramPluginWatchdog.isSafeMode()) {
            items.add(UItem.asButton(BUTTON_SAFE_MODE_EXIT, R.drawable.msg_retry, getString(R.string.MZGramPluginSafeModeExit)));
            items.add(UItem.asShadow(getString(R.string.MZGramPluginSafeModeActive)));
        }
        if (MZGramConfig.pluginsRun && MZGramPluginHookGate.isEngineBroken()) {
            items.add(UItem.asButton(BUTTON_HOOKS_RETRY, R.drawable.msg_retry, getString(R.string.MZGramPluginHooksRetry)));
            items.add(UItem.asShadow(getString(R.string.MZGramPluginHooksBroken)));
        }
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
        items.add(UItem.asButton(BUTTON_CATALOG, R.drawable.msg_channel, getString(R.string.MZGramPluginsCatalog), TextUtils.isEmpty(MZGramConfig.pluginsCatalogUrl) ? getString(R.string.MZGramPluginsCatalogNotConnected) : MZGramConfig.pluginsCatalogUrl));
        items.add(UItem.asButton(BUTTON_JOURNAL, R.drawable.msg_log, getString(R.string.MZGramPluginJournal)));
        items.add(UItem.asShadow(plugins.isEmpty() ? getString(R.string.MZGramPluginsEmpty) : null));

        items.add(UItem.asHeader(getString(R.string.MZGramPluginsDevHeader)));
        items.add(UItem.asCheck(BUTTON_PIP, getString(R.string.MZGramPipEnabled)).setChecked(MZGramConfig.pipEnabled));
        items.add(UItem.asShadow(getString(R.string.MZGramPipEnabledInfo)));
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
        if (item.id == BUTTON_SAFE_MODE_EXIT) {
            MZGramPluginWatchdog.setSafeMode(false);
            if (MZGramConfig.pluginsRun) {
                MZGramPluginEngine.onResume();
            }
            reload();
            return;
        }
        if (item.id == BUTTON_HOOKS_RETRY) {
            MZGramPluginHookGate.retryEngine();
            reload();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MZGramPluginHooksRetryDone)).show();
            return;
        }
        if (item.id == BUTTON_ADD) {
            pickFile();
            return;
        }
        if (item.id == BUTTON_CATALOG) {
            presentFragment(new MZGramPluginsCatalogActivity());
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
        if (item.id == BUTTON_PIP) {
            togglePip();
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

    private void togglePip() {
        if (!MZGramConfig.pipEnabled) {
            if (getParentActivity() == null) {
                return;
            }
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.MZGramPipEnabled))
                    .setMessage(getString(R.string.MZGramPipEnabledPrompt))
                    .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                        MZGramConfig.togglePipEnabled();
                        reload();
                    })
                    .setNegativeButton(getString(R.string.MZGramCancel), null)
                    .show();
        } else {
            MZGramConfig.togglePipEnabled();
            reload();
        }
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

    private void exportPluginsBackup() {
        if (plugins.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginsBackupEmpty)).show();
            return;
        }
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            File file = MZGramPluginsBackup.export();
            AndroidUtilities.runOnUIThread(() -> {
                if (file == null) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginsBackupEmpty)).show();
                    return;
                }
                Activity activity = getParentActivity();
                if (activity != null) {
                    Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        activity.startActivity(Intent.createChooser(intent, getString(R.string.MZGramPluginsExportBackup)));
                    } catch (Exception e) {
                        FileLog.e("MZGramPluginsActivity.exportBackup", e);
                    }
                }
            });
        });
    }

    private void pickPluginsBackup() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_BACKUP);
        } catch (Exception e) {
            FileLog.e("MZGramPluginsActivity.pickBackup", e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_BACKUP && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Activity activity = getParentActivity();
            if (activity == null) {
                return;
            }
            File cacheDir = AndroidUtilities.getCacheDir();
            File tempArchive = new File(cacheDir, "plugins_import_" + System.currentTimeMillis() + ".zip");
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                try (InputStream in = activity.getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(tempArchive)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                    }
                } catch (Exception e) {
                    FileLog.e("MZGramPluginsActivity: cannot copy backup", e);
                    tempArchive.delete();
                }
                List<MZGramPluginsBackup.Entry> entries = MZGramPluginsBackup.read(tempArchive);
                AndroidUtilities.runOnUIThread(() -> {
                    if (entries == null || entries.isEmpty()) {
                        tempArchive.delete();
                        BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginsBackupInvalid)).show();
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle(getString(R.string.MZGramPluginsImportBackup))
                            .setMessage(LocaleController.formatString(R.string.MZGramPluginsBackupConfirm, entries.size()))
                            .setPositiveButton(getString(R.string.MZGramPluginsBackupImport), (dialog, which) -> {
                                MZGramPluginsBackup.importAll(tempArchive, entries, (imported, total) -> {
                                    tempArchive.delete();
                                    reload();
                                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                                            LocaleController.formatString(R.string.MZGramPluginsBackupDone, imported, total)).show();
                                });
                            })
                            .setNegativeButton(getString(R.string.MZGramCancel), (dialog, which) -> tempArchive.delete())
                            .show();
                });
            });
            return;
        }
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
