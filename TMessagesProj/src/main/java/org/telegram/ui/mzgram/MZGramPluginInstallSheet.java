/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The prompt before a plugin file is added or updated: the plugin's name,
 * version, author and description, a check box for each permission it may
 * get and a button that shows its code. The file is kept only after "Add"
 * or "Update", and the plugin gets exactly the checked permissions
 * (MZGramPluginGrants); boxes start unchecked for a new plugin and as
 * granted before for an update. A plugin that doesn't list its permissions
 * is offered all of them, all unchecked.
 *
 * Opened from the plugin list (a picked file) and from a chat: a tap on a
 * downloaded .plugin file in a build with plugins asks to add it instead of
 * opening it in another app.
 *
 * Based on exteraless plugins/ui/PluginInstallBottomSheet.java and
 * PluginInstallHelper.java (GPL-3.0): the consent sheet with unchecked
 * boxes and the tap on a plugin file in a chat; written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat;
import org.telegram.messenger.mzgram.plugins.MZGramPluginGrants;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MZGramPluginInstallSheet extends BottomSheet {

    private final BaseFragment fragment;
    private final File picked;
    private final MZGramPluginFormat.Meta meta;
    private final boolean update;
    private final List<String> offered;
    private final boolean[] checked;
    private final Runnable onChanged;
    // The file goes on to install() or to the code screen; otherwise it is discarded on close
    private boolean keepFile;

    /**
     * Asks before adding the picked file (from MZGramPlugins.readPicked).
     * onChanged runs on the UI thread after the plugin was added; may be null.
     */
    public static void show(BaseFragment fragment, File picked, MZGramPluginFormat.Meta meta, Runnable onChanged) {
        show(fragment, picked, meta, null, onChanged);
    }

    private static void show(BaseFragment fragment, File picked, MZGramPluginFormat.Meta meta, boolean[] state, Runnable onChanged) {
        final Activity activity = fragment != null ? fragment.getParentActivity() : null;
        if (activity == null || fragment.isFinishing() || picked == null || meta == null) {
            MZGramPlugins.discard(picked);
            return;
        }
        final MZGramPluginInstallSheet sheet = new MZGramPluginInstallSheet(activity, fragment, picked, meta, state, onChanged);
        if (fragment.showDialog(sheet) == null) {
            MZGramPlugins.discard(picked);
        }
    }

    /**
     * A tap on a file in a chat: true when it is a downloaded .plugin file
     * in a build with plugins and the prompt took over; false to open the
     * file as before.
     */
    public static boolean openFromChat(BaseFragment fragment, MessageObject message) {
        if (fragment == null || message == null || !MZGramPythonRuntime.isAvailable()) {
            return false;
        }
        final String name = message.getDocumentName();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(MZGramPluginFormat.EXTENSION)) {
            return false;
        }
        File file = null;
        if (!TextUtils.isEmpty(message.messageOwner.attachPath)) {
            final File sent = new File(message.messageOwner.attachPath);
            if (sent.isFile()) {
                file = sent;
            }
        }
        if (file == null) {
            file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        }
        if (file == null || !file.isFile() || file.length() == 0) {
            // Not downloaded yet: the usual download goes on
            return false;
        }
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return false;
        }
        final AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        // The first read starts Python, which takes a few seconds
        MZGramPlugins.readPicked(Uri.fromFile(file), (copy, result) -> {
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            if (fragment.isFinishing() || fragment.getParentActivity() == null) {
                MZGramPlugins.discard(copy);
                return;
            }
            if (copy == null || result == null || !result.isOk()) {
                showReadError(fragment, result != null ? result.error : null);
            } else {
                show(fragment, copy, result.meta, null);
            }
        });
        return true;
    }

    /** The error of a file that could not be read, as a bulletin. */
    static void showReadError(BaseFragment fragment, String error) {
        if (MZGramPlugins.ERROR_TOO_LARGE.equals(error)) {
            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.MZGramPluginsTooLarge)).show();
        } else if (TextUtils.isEmpty(error) || MZGramPlugins.ERROR_READ.equals(error)) {
            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.MZGramPluginsAddFailed)).show();
        } else {
            // The reason from the metadata reader, such as a missing __id__
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, getString(R.string.MZGramPluginsAddFailed), error).show();
        }
    }

    private MZGramPluginInstallSheet(Context context, BaseFragment fragment, File picked, MZGramPluginFormat.Meta meta,
                                     boolean[] state, Runnable onChanged) {
        super(context, false, fragment.getResourceProvider());
        this.fragment = fragment;
        this.picked = picked;
        this.meta = meta;
        this.onChanged = onChanged;
        final MZGramPlugins.Plugin installed = MZGramPlugins.get(meta.id);
        this.update = installed != null;
        this.offered = MZGramPluginTexts.offeredPermissions(meta);
        this.checked = new boolean[offered.size()];
        if (state != null && state.length == checked.length) {
            System.arraycopy(state, 0, checked, 0, checked.length);
        } else if (update) {
            // As granted now, less what the new version no longer declares
            final List<String> stored = MZGramPluginGrants.getStored(meta.id);
            for (int i = 0; i < checked.length; i++) {
                checked[i] = stored != null && stored.contains(offered.get(i));
            }
        }
        fixNavigationBar();
        final Theme.ResourcesProvider resources = fragment.getResourceProvider();

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final TextView titleView = new TextView(context);
        titleView.setText(getString(update ? R.string.MZGramPluginsUpdateTitle : R.string.MZGramPluginsAddTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 16, 22, 8));

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // Plain text throughout: the name and the description come from the file
        final TextView nameView = text(context, meta.name, 16, Theme.key_dialogTextBlack, resources);
        nameView.setTypeface(AndroidUtilities.bold());
        content.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 0, 22, 2));
        content.addView(text(context, MZGramPluginTexts.subtitle(meta), 14, Theme.key_dialogTextGray2, resources),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 0, 22, 8));
        if (!TextUtils.isEmpty(meta.description)) {
            content.addView(text(context, meta.description, 14, Theme.key_dialogTextBlack, resources),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 0, 22, 8));
        }
        content.addView(text(context, update
                        ? LocaleController.formatString(R.string.MZGramPluginsUpdateInfo, installed.meta.version)
                        : getString(R.string.MZGramPluginsAddInfo), 14, Theme.key_dialogTextGray2, resources),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 0, 22, 8));

        final TextView permissionsHeader = text(context, getString(R.string.MZGramPluginPermissions), 15, Theme.key_dialogTextBlue2, resources);
        permissionsHeader.setTypeface(AndroidUtilities.bold());
        content.addView(permissionsHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 8, 22, 4));
        if (!meta.permissionsDeclared) {
            content.addView(text(context, getString(R.string.MZGramPluginPermissionsMissing), 14, Theme.key_dialogTextGray2, resources),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 0, 22, 4));
        }
        if (offered.isEmpty()) {
            content.addView(text(context, getString(R.string.MZGramPluginNoPermissions), 14, Theme.key_dialogTextGray2, resources),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 0, 22, 4));
        } else {
            for (int i = 0; i < offered.size(); i++) {
                final int index = i;
                final CheckBoxCell cell = new CheckBoxCell(context, 1, resources);
                cell.setBackground(Theme.getSelectorDrawable(false));
                cell.setText(MZGramPluginTexts.permissionName(offered.get(i)), "", checked[i], false);
                cell.setOnClickListener(v -> {
                    checked[index] = !checked[index];
                    cell.setChecked(checked[index], true);
                });
                content.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            }
            content.addView(text(context, getString(R.string.MZGramPluginInstallPermissionsInfo), 14, Theme.key_dialogTextGray2, resources),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 4, 22, 4));
        }

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        layout.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        final TextView codeButton = new TextView(context);
        codeButton.setText(getString(R.string.MZGramPluginSource));
        codeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        codeButton.setGravity(Gravity.CENTER);
        codeButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resources));
        codeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), Theme.RIPPLE_MASK_ALL));
        codeButton.setOnClickListener(v -> showCode());
        layout.addView(codeButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 16, 8, 16, 0));

        final ButtonWithCounterView addButton = new ButtonWithCounterView(context, resources);
        addButton.setText(getString(update ? R.string.MZGramPluginsUpdate : R.string.MZGramPluginsAdd), false);
        addButton.setOnClickListener(v -> confirm());
        layout.addView(addButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 16));

        setCustomView(layout);
        setOnHideListener(d -> {
            if (!keepFile) {
                MZGramPlugins.discard(picked);
            }
        });
    }

    private static TextView text(Context context, CharSequence value, int size, int colorKey, Theme.ResourcesProvider resources) {
        final TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTextColor(Theme.getColor(colorKey, resources));
        return view;
    }

    // The code screen opens over the chat or the list; the prompt comes back when it is closed
    private void showCode() {
        keepFile = true;
        final boolean[] state = checked.clone();
        dismiss();
        fragment.presentFragment(new MZGramPluginSourceActivity(meta.id, picked,
                () -> show(fragment, picked, meta, state, onChanged)));
    }

    private void confirm() {
        if (keepFile) {
            return;
        }
        keepFile = true;
        final ArrayList<String> granted = new ArrayList<>();
        for (int i = 0; i < offered.size(); i++) {
            if (checked[i]) {
                granted.add(offered.get(i));
            }
        }
        dismiss();
        MZGramPlugins.install(picked, meta, ok -> {
            if (ok) {
                // After install(): a new plugin starts with no record, an update keeps only declared keys
                MZGramPluginGrants.setGranted(meta.id, granted);
            }
            if (fragment.isFinishing() || fragment.getParentActivity() == null) {
                return;
            }
            if (ok) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                        getString(update ? R.string.MZGramPluginsUpdated : R.string.MZGramPluginsAdded)).show();
            } else {
                BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.MZGramPluginsSaveFailed)).show();
            }
            if (onChanged != null) {
                onChanged.run();
            }
        });
    }
}
