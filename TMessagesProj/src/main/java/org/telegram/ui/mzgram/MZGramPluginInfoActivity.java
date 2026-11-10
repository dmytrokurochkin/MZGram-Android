/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The screen of one added plugin: its on/off switch, what its metadata say
 * (description, version, author, id, the versions it needs), the
 * permissions it declares, the Python packages it asks for, and buttons to
 * read its code, share its file and delete it. Everything comes from the
 * metadata kept by MZGramPlugins; nothing of the plugin runs here. Under the
 * switch: whether the plugin runs (MZGramPluginEngine) or why it did not
 * start, its settings page while it runs, and a restart; a running plugin
 * with hooks also says how many methods it hooks (MZGramPluginHookGate).
 * Turning on "hooks" or "native" asks once more (full control); a running
 * plugin starts again when its permissions change.
 *
 * Based on exteraless plugins/ui/PluginsActivity.java and PluginCell.java
 * (GPL-3.0): what a plugin shows and the actions on it; written anew for
 * MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramProfilerReports;
import org.telegram.messenger.mzgram.plugins.MZGramPluginDevServer;
import org.telegram.messenger.mzgram.plugins.MZGramPluginEngine;
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat;
import org.telegram.messenger.mzgram.plugins.MZGramPluginGrants;
import org.telegram.messenger.mzgram.plugins.MZGramPluginHookGate;
import org.telegram.messenger.mzgram.plugins.MZGramPluginPermissions;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MZGramPluginInfoActivity extends UniversalFragment {

    private static final int BUTTON_ENABLED = 1;
    private static final int BUTTON_VERSION = 2;
    private static final int BUTTON_AUTHOR = 3;
    private static final int BUTTON_ID = 4;
    private static final int BUTTON_APP_VERSION = 5;
    private static final int BUTTON_SDK_VERSION = 6;
    private static final int BUTTON_SOURCE = 7;
    private static final int BUTTON_SHARE = 8;
    private static final int BUTTON_DELETE = 9;
    private static final int BUTTON_JOURNAL = 10;
    private static final int BUTTON_SETTINGS = 11;
    private static final int BUTTON_RESTART = 12;
    private static final int BUTTON_PIP_INSTALL = 13;
    // Rows of the permissions and the packages, by their place
    private static final int PERMISSION_ROW = 100;
    private static final int REQUIREMENT_ROW = 300;

    private final String pluginId;
    private MZGramPlugins.Plugin plugin;
    // The permissions shown as switches, in row order
    private List<String> offered = new ArrayList<>();

    public MZGramPluginInfoActivity(String pluginId) {
        this.pluginId = pluginId;
    }

    @Override
    public boolean onFragmentCreate() {
        plugin = MZGramPlugins.get(pluginId);
        return plugin != null && super.onFragmentCreate();
    }

    // The developer server may update, turn on or off, or delete the plugin
    private final Runnable devServerListener = this::refresh;

    @Override
    public void onResume() {
        super.onResume();
        MZGramPluginDevServer.addListener(devServerListener);
        MZGramPluginEngine.addListener(devServerListener);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        MZGramPluginDevServer.removeListener(devServerListener);
        MZGramPluginEngine.removeListener(devServerListener);
    }

    private void refresh() {
        final MZGramPlugins.Plugin current = MZGramPlugins.get(pluginId);
        if (current == null) {
            // Deleted meanwhile
            finishFragment();
            return;
        }
        plugin = current;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return plugin != null ? plugin.meta.name : getString(R.string.MZGramPlugins);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (plugin == null) {
            return;
        }
        final MZGramPluginFormat.Meta meta = plugin.meta;
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_ENABLED, getString(R.string.MZGramPluginEnabled)).setChecked(plugin.enabled));
        if (MZGramPluginEngine.hasSettings(pluginId)) {
            items.add(UItem.asButton(BUTTON_SETTINGS, R.drawable.msg_settings, getString(R.string.MZGramPluginSettings)));
        }
        if (plugin.enabled && MZGramPluginEngine.isOn()) {
            items.add(UItem.asButton(BUTTON_RESTART, R.drawable.msg_retry, getString(R.string.MZGramPluginRestart)));
        }
        items.add(UItem.asShadow(stateText()));

        items.add(UItem.asHeader(getString(R.string.MZGramPluginAbout)));
        items.add(UItem.asButton(BUTTON_VERSION, getString(R.string.MZGramPluginVersion), meta.beta
                ? meta.version + " (" + getString(R.string.MZGramPluginBeta) + ")" : meta.version));
        if (!TextUtils.isEmpty(meta.author)) {
            items.add(UItem.asButton(BUTTON_AUTHOR, getString(R.string.MZGramPluginAuthor), meta.author));
        }
        items.add(UItem.asButton(BUTTON_ID, getString(R.string.MZGramPeerIdLabel), meta.id));
        if (meta.appVersion != null) {
            items.add(UItem.asButton(BUTTON_APP_VERSION, getString(R.string.MZGramPluginAppVersion), meta.appVersion));
        }
        if (meta.sdkVersion != null) {
            items.add(UItem.asButton(BUTTON_SDK_VERSION, getString(R.string.MZGramPluginSdkVersion), meta.sdkVersion));
        }
        items.add(UItem.asShadow(TextUtils.isEmpty(meta.description) ? null : meta.description));

        items.add(UItem.asHeader(getString(R.string.MZGramPluginPermissions)));
        offered = MZGramPluginTexts.offeredPermissions(meta);
        if (!offered.isEmpty()) {
            final List<String> granted = MZGramPluginGrants.getEffective(pluginId);
            for (int i = 0; i < offered.size(); i++) {
                final String key = offered.get(i);
                items.add(UItem.asCheck(PERMISSION_ROW + i, MZGramPluginTexts.permissionName(key)).setChecked(granted.contains(key)));
            }
            final String info = getString(R.string.MZGramPluginPermissionsInfo) + " " + getString(R.string.MZGramPluginPermissionsRestartInfo);
            items.add(UItem.asShadow(meta.permissionsDeclared ? info : getString(R.string.MZGramPluginPermissionsMissing) + " " + info));
        } else {
            items.add(UItem.asShadow(getString(R.string.MZGramPluginNoPermissions)));
        }

        if (!meta.requirements.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.MZGramPluginRequirements)));
            for (int i = 0; i < meta.requirements.size(); i++) {
                items.add(UItem.asButton(REQUIREMENT_ROW + i, meta.requirements.get(i)));
            }
            if (MZGramConfig.pipEnabled && org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime.isAvailable()) {
                items.add(UItem.asButton(BUTTON_PIP_INSTALL, R.drawable.msg_download, getString(R.string.MZGramPipInstallPackages)).accent());
                items.add(UItem.asShadow(getString(R.string.MZGramPipInstallPackagesInfo)));
            } else {
                items.add(UItem.asShadow(getString(R.string.MZGramPluginRequirementsInfo)));
            }
        }

        items.add(UItem.asButton(BUTTON_SOURCE, R.drawable.msg_view_file, getString(R.string.MZGramPluginSource)));
        items.add(UItem.asButton(BUTTON_SHARE, R.drawable.msg_share, getString(R.string.MZGramPluginShare)));
        items.add(UItem.asButton(BUTTON_JOURNAL, R.drawable.msg_log, getString(R.string.MZGramPluginJournal)));
        items.add(UItem.asButton(BUTTON_DELETE, R.drawable.msg_delete, getString(R.string.MZGramPluginDelete)).red());
        items.add(UItem.asShadow(null));
    }

    // Whether the plugin runs, or why not
    private String stateText() {
        if (!MZGramPluginEngine.isOn()) {
            return getString(R.string.MZGramPluginsRunOffInfo);
        }
        if (!plugin.enabled) {
            return getString(R.string.MZGramPluginStoppedInfo);
        }
        switch (MZGramPluginEngine.getState(pluginId)) {
            case MZGramPluginEngine.STATE_RUNNING: {
                final int hooks = MZGramPluginHookGate.count(pluginId);
                final String running = getString(R.string.MZGramPluginRunningInfo);
                return hooks > 0 ? running + " " + LocaleController.formatString(R.string.MZGramPluginHooksActive, hooks) : running;
            }
            case MZGramPluginEngine.STATE_FAILED: {
                final String error = MZGramPluginEngine.getError(pluginId);
                return LocaleController.formatString(R.string.MZGramPluginFailedInfo, TextUtils.isEmpty(error) ? "?" : error);
            }
            default:
                return getString(R.string.MZGramPluginStartingInfo);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (plugin == null) {
            return;
        }
        if (item.id == BUTTON_ENABLED) {
            MZGramPlugins.setEnabled(pluginId, !item.checked);
            refresh();
        } else if (item.id == BUTTON_SETTINGS) {
            presentFragment(new MZGramPluginSettingsActivity(pluginId, plugin.meta.name));
        } else if (item.id == BUTTON_RESTART) {
            MZGramPluginEngine.restart(pluginId);
            refresh();
        } else if (item.id == BUTTON_ID) {
            AndroidUtilities.addToClipboard(pluginId);
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramTextCopied)).show();
        } else if (item.id >= PERMISSION_ROW && item.id < PERMISSION_ROW + offered.size()) {
            final String key = offered.get(item.id - PERMISSION_ROW);
            if (!item.checked && MZGramPluginPermissions.isDangerous(key) && getParentActivity() != null) {
                // Full control: asked once more
                MZGramPluginDenialNotice.confirmDangerous(getParentActivity(), getResourceProvider(), plugin.meta.name, key,
                        () -> setPermission(key, true));
            } else {
                setPermission(key, !item.checked);
            }
        } else if (item.id == BUTTON_SOURCE) {
            presentFragment(new MZGramPluginSourceActivity(pluginId));
        } else if (item.id == BUTTON_SHARE) {
            share();
        } else if (item.id == BUTTON_JOURNAL) {
            presentFragment(new MZGramPluginJournalActivity(pluginId));
        } else if (item.id == BUTTON_PIP_INSTALL) {
            org.telegram.messenger.mzgram.plugins.MZGramPipInstaller.showInstallDialog(this, pluginId, plugin.meta.requirements, this::refresh);
        } else if (item.id == BUTTON_DELETE) {
            confirmDelete();
        }
    }

    // A running plugin starts again with what it has now (MZGramPluginGrants)
    private void setPermission(String key, boolean on) {
        final boolean ok = on ? MZGramPluginGrants.grant(pluginId, key) : MZGramPluginGrants.revoke(pluginId, key);
        if (!ok) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginsSaveFailed)).show();
        }
        refresh();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void share() {
        Utilities.globalQueue.postRunnable(() -> {
            final File copy = MZGramPlugins.shareCopy(pluginId);
            AndroidUtilities.runOnUIThread(() -> {
                if (isFinishing() || getParentActivity() == null) {
                    return;
                }
                if (!MZGramProfilerReports.share(getParentActivity(), copy)) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginFileFailed)).show();
                }
            });
        });
    }

    private void confirmDelete() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.MZGramPluginDelete));
        // Plain text: the name comes from the plugin file
        builder.setMessage(LocaleController.formatString(R.string.MZGramPluginDeleteConfirm, plugin.meta.name));
        builder.setPositiveButton(getString(R.string.MZGramDelete), (dialog, which) -> {
            MZGramPlugins.delete(pluginId);
            finishFragment();
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        builder.makeRed(AlertDialog.BUTTON_POSITIVE);
        showDialog(builder.create());
    }
}
