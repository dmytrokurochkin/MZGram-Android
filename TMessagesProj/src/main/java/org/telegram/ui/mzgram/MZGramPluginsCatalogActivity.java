/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Plugins catalog screen: displays online plugins available in the
 * user-connected repository index.json. Allows downloading plugins
 * with mandatory SHA-256 verification and previewing permissions (№51)
 * plus capability scan (№67) before installation.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.messenger.mzgram.plugins.MZGramPluginsCatalog;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MZGramPluginsCatalogActivity extends UniversalFragment {

    private static final int BUTTON_CONNECT = 1;
    private static final int BUTTON_URL = 2;
    private static final int BUTTON_REFRESH = 3;
    private static final int BUTTON_DISCONNECT = 4;
    private static final int PLUGIN_ROW_BASE = 1000;

    private boolean loading;
    private String loadError;
    private MZGramPluginsCatalog.CatalogData catalogData;
    private final List<MZGramPluginsCatalog.CatalogPlugin> plugins = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        if (!TextUtils.isEmpty(MZGramConfig.pluginsCatalogUrl)) {
            loadCatalog();
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void loadCatalog() {
        if (TextUtils.isEmpty(MZGramConfig.pluginsCatalogUrl)) {
            return;
        }
        loading = true;
        loadError = null;
        reload();

        MZGramPluginsCatalog.fetchCatalog(MZGramConfig.pluginsCatalogUrl, data -> {
            loading = false;
            catalogData = data;
            plugins.clear();
            if (data != null && data.plugins != null) {
                plugins.addAll(data.plugins);
            }
            reload();
        }, error -> {
            loading = false;
            loadError = error;
            reload();
        });
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramPluginsCatalog);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(null));

        if (TextUtils.isEmpty(MZGramConfig.pluginsCatalogUrl)) {
            items.add(UItem.asHeader(getString(R.string.MZGramPluginsCatalog)));
            items.add(UItem.asButton(BUTTON_CONNECT, R.drawable.msg_channel, getString(R.string.MZGramPluginsCatalogConnect)).accent());
            items.add(UItem.asShadow(getString(R.string.MZGramPluginsCatalogNotConnectedInfo)));
            return;
        }

        final String title = (catalogData != null && !TextUtils.isEmpty(catalogData.name)) ? catalogData.name : getString(R.string.MZGramPluginsCatalog);
        items.add(UItem.asHeader(title));
        items.add(UItem.asButton(BUTTON_URL, getString(R.string.MZGramPluginsCatalogUrl), MZGramConfig.pluginsCatalogUrl));
        items.add(UItem.asButton(BUTTON_REFRESH, R.drawable.msg_retry, getString(R.string.MZGramPluginsCatalogRefresh)));
        items.add(UItem.asButton(BUTTON_DISCONNECT, R.drawable.msg_delete, getString(R.string.MZGramPluginsCatalogDisconnect)).red());

        if (loading) {
            items.add(UItem.asShadow(getString(R.string.MZGramPluginsCatalogLoading)));
            return;
        }

        if (loadError != null) {
            items.add(UItem.asShadow(formatString(R.string.MZGramPluginsCatalogLoadFailed, loadError)));
            return;
        }

        if (plugins.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.MZGramPluginsCatalogEmpty)));
            return;
        }

        items.add(UItem.asHeader(getString(R.string.MZGramPlugins)));
        for (int i = 0; i < plugins.size(); i++) {
            final MZGramPluginsCatalog.CatalogPlugin p = plugins.get(i);
            final boolean installed = MZGramPlugins.get(p.id) != null;
            final StringBuilder sub = new StringBuilder();
            sub.append("v").append(p.version);
            if (!TextUtils.isEmpty(p.author)) {
                sub.append(" · ").append(p.author);
            }
            if (!TextUtils.isEmpty(p.description)) {
                sub.append("\n").append(p.description);
            }
            final String badge = installed ? getString(R.string.MZGramPluginsCatalogInstalled) : getString(R.string.MZGramPluginsCatalogInstall);
            UItem row = UItem.asButton(PLUGIN_ROW_BASE + i, p.name, badge);
            row.subtext = sub.toString();
            if (!installed) {
                row.accent();
            }
            items.add(row);
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_CONNECT || item.id == BUTTON_URL) {
            promptCatalogUrl();
            return;
        }
        if (item.id == BUTTON_REFRESH) {
            loadCatalog();
            return;
        }
        if (item.id == BUTTON_DISCONNECT) {
            MZGramConfig.setPluginsCatalogUrl("");
            catalogData = null;
            plugins.clear();
            reload();
            return;
        }

        if (item.id >= PLUGIN_ROW_BASE && item.id < PLUGIN_ROW_BASE + plugins.size()) {
            final MZGramPluginsCatalog.CatalogPlugin plugin = plugins.get(item.id - PLUGIN_ROW_BASE);
            if (MZGramPlugins.get(plugin.id) != null) {
                presentFragment(new MZGramPluginInfoActivity(plugin.id));
            } else {
                installPlugin(plugin);
            }
        }
    }

    private void promptCatalogUrl() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }

        final FrameLayout container = new FrameLayout(activity);
        container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));

        final EditTextBoldCursor editText = new EditTextBoldCursor(activity);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setText(MZGramConfig.pluginsCatalogUrl);
        editText.setSelection(editText.getText().length());
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramPluginsCatalogUrl))
                .setMessage(getString(R.string.MZGramPluginsCatalogUrlPrompt))
                .setView(container)
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    final String url = editText.getText().toString().trim();
                    MZGramConfig.setPluginsCatalogUrl(url);
                    if (!TextUtils.isEmpty(url)) {
                        loadCatalog();
                    } else {
                        catalogData = null;
                        plugins.clear();
                        reload();
                    }
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void installPlugin(MZGramPluginsCatalog.CatalogPlugin plugin) {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }

        final AlertDialog[] progress = new AlertDialog[1];
        progress[0] = new AlertDialog(activity, 3);
        progress[0].setCanceledOnTouchOutside(false);
        progress[0].setMessage(getString(R.string.MZGramPluginsCatalogDownloading));
        progress[0].show();

        MZGramPluginsCatalog.downloadPlugin(plugin, MZGramConfig.pluginsCatalogUrl, file -> {
            if (progress[0] != null) {
                progress[0].dismiss();
                progress[0] = null;
            }
            if (getParentActivity() == null || file == null) {
                return;
            }

            MZGramPluginFormat.readMetadata(file, result -> {
                if (result.isOk()) {
                    MZGramPluginInstallSheet.show(this, file, result.meta, () -> {
                        reload();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramPluginsCatalogInstalled)).show();
                    });
                } else {
                    MZGramPlugins.discard(file);
                    new AlertDialog.Builder(activity)
                            .setTitle(getString(R.string.MZGramPluginsAddFailed))
                            .setMessage(result.error)
                            .setPositiveButton(getString(R.string.MZGramOk), null)
                            .show();
                }
            });
        }, error -> {
            if (progress[0] != null) {
                progress[0].dismiss();
                progress[0] = null;
            }
            if (getParentActivity() == null) {
                return;
            }

            if ("sha256_mismatch".equals(error)) {
                new AlertDialog.Builder(activity)
                        .setTitle(getString(R.string.MZGramPluginsAddFailed))
                        .setMessage(getString(R.string.MZGramPluginsCatalogSha256Mismatch))
                        .setPositiveButton(getString(R.string.MZGramOk), null)
                        .show();
            } else {
                new AlertDialog.Builder(activity)
                        .setTitle(getString(R.string.MZGramPluginsAddFailed))
                        .setMessage(formatString(R.string.MZGramPluginsCatalogLoadFailed, error))
                        .setPositiveButton(getString(R.string.MZGramOk), null)
                        .show();
            }
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
