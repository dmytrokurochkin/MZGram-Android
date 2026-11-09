/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Interface > Icon packs (MZGramIconPacks): the
 * built-in icon set, the installed packs in the order of priority (hold
 * and drag to move one), and installing a .icons file. Tapping a pack
 * shows its options: on or off, tinting to the theme, deleting it.
 *
 * Idea from exteraless IconPacksActivity and InstallIconPackBottomSheet
 * (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramIconPacks;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MZGramIconPacksActivity extends UniversalFragment {

    private static final int REQUEST_CODE_PICK_PACK = 8847;

    private static final int ROW_INSTALL = 1;
    private static final int BUILTIN_ROW = 100;
    private static final int PACK_ROW = 1000;

    private final ArrayList<MZGramIconPacks.Pack> shown = new ArrayList<>();
    private int orderSectionId = -1;
    private AlertDialog progress;

    private final Runnable onChanged = () -> {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    };

    @Override
    public boolean onFragmentCreate() {
        MZGramIconPacks.addListener(onChanged);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        MZGramIconPacks.removeListener(onChanged);
        dismissProgress();
        super.onFragmentDestroy();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramIconPacks);
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        listView.listenReorder(this::whenReordered);
        listView.allowReorder(true);
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int builtin = MZGramIconPacks.builtin();
        items.add(UItem.asHeader(getString(R.string.MZGramIconPacksBuiltin)));
        items.add(UItem.asRadio(BUILTIN_ROW + MZGramIconPacks.BUILTIN_NONE, getString(R.string.MZGramIconPacksStock))
                .setChecked(builtin == MZGramIconPacks.BUILTIN_NONE));
        for (int set : new int[]{MZGramIconPacks.BUILTIN_SOLAR, MZGramIconPacks.BUILTIN_REMIX}) {
            items.add(UItem.asRadio(BUILTIN_ROW + set, MZGramIconPacks.builtinTitle(set), MZGramIconPacks.builtinAuthor(set))
                    .setChecked(builtin == set));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramIconPacksBuiltinInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramIconPacksInstalled)));
        shown.clear();
        shown.addAll(MZGramIconPacks.installed());
        if (!shown.isEmpty()) {
            orderSectionId = adapter.reorderSectionStart();
            for (int i = 0; i < shown.size(); i++) {
                MZGramIconPacks.Pack pack = shown.get(i);
                items.add(UItem.asButtonCheck(PACK_ROW + i, pack.name, subtitle(pack)).setChecked(MZGramIconPacks.isOn(pack.key)));
            }
            adapter.reorderSectionEnd();
        }
        items.add(UItem.asButton(ROW_INSTALL, R.drawable.msg_add, getString(R.string.MZGramIconPacksInstall)).accent());
        items.add(UItem.asShadow(getString(R.string.MZGramIconPacksInfo)));
    }

    private static String subtitle(MZGramIconPacks.Pack pack) {
        String count = formatString(R.string.MZGramIconPacksCount, pack.count());
        String look = getString(MZGramIconPacks.isTinted(pack) ? R.string.MZGramIconPacksTinted : R.string.MZGramIconPacksColorful);
        return TextUtils.isEmpty(pack.author) ? count + " · " + look : pack.author + " · " + count + " · " + look;
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        if (id != orderSectionId || items == null) {
            return;
        }
        ArrayList<String> order = new ArrayList<>();
        for (UItem item : items) {
            int index = item.id - PACK_ROW;
            if (index >= 0 && index < shown.size()) {
                order.add(shown.get(index).key);
            }
        }
        MZGramIconPacks.setOrder(order);
        rebuildScreens(this);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_INSTALL) {
            pickFile();
            return;
        }
        if (item.id >= BUILTIN_ROW && item.id <= BUILTIN_ROW + MZGramIconPacks.BUILTIN_REMIX) {
            MZGramIconPacks.setBuiltin(item.id - BUILTIN_ROW);
            rebuildScreens(this);
            return;
        }
        int index = item.id - PACK_ROW;
        if (index < 0 || index >= shown.size()) {
            return;
        }
        final MZGramIconPacks.Pack pack = shown.get(index);
        final boolean on = MZGramIconPacks.isOn(pack.key);
        final boolean tinted = MZGramIconPacks.isTinted(pack);
        ItemOptions.makeOptions(this, view)
                .addChecked(on, getString(R.string.MZGramIconPacksUse), () -> {
                    MZGramIconPacks.setOn(pack.key, !on);
                    rebuildScreens(this);
                })
                .addChecked(tinted, getString(R.string.MZGramIconPacksTint), () -> {
                    MZGramIconPacks.setTinted(pack.key, !tinted);
                    rebuildScreens(this);
                })
                .addGap()
                .add(R.drawable.msg_delete, getString(R.string.MZGramDelete), true, () -> confirmDelete(pack))
                .show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void confirmDelete(MZGramIconPacks.Pack pack) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.MZGramIconPacksDeleteTitle))
                .setMessage(formatString(R.string.MZGramIconPacksDeleteText, pack.name))
                .setPositiveButton(getString(R.string.MZGramDelete), (d, which) -> Utilities.globalQueue.postRunnable(() -> {
                    MZGramIconPacks.delete(pack.key);
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> rebuildScreens(this));
                }))
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        showDialog(dialog);
        View delete = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (delete instanceof android.widget.TextView) {
            ((android.widget.TextView) delete).setTextColor(Theme.getColor(Theme.key_text_RedBold, getResourceProvider()));
        }
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // .icons files have no media type of their own
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_PACK);
        } catch (Exception e) {
            FileLog.e("MZGramIconPacksActivity.pickFile", e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_PICK_PACK || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        showProgress();
        MZGramIconPacks.readPicked(data.getData(), picked -> {
            dismissProgress();
            confirmInstall(this, picked);
        });
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

    // ---- Shared with the chat ----

    /** Rebuilds the screens under fragment, so they take the icons of the packs now on. */
    public static void rebuildScreens(BaseFragment fragment) {
        if (fragment != null && fragment.getParentLayout() != null) {
            fragment.getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    /**
     * A tap on a .icons file in a chat, while icon packs are on: offers to
     * install it. False leaves the file to Telegram, as before.
     */
    public static boolean openFromChat(BaseFragment fragment, MessageObject message) {
        if (!MZGramConfig.iconPacks || fragment == null || message == null || message.messageOwner == null) {
            return false;
        }
        String name = message.getDocumentName();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(MZGramIconPacks.EXTENSION)) {
            return false;
        }
        File file = null;
        if (!TextUtils.isEmpty(message.messageOwner.attachPath)) {
            File sent = new File(message.messageOwner.attachPath);
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
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return false;
        }
        AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        MZGramIconPacks.readFile(file, picked -> {
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            confirmInstall(fragment, picked);
        });
        return true;
    }

    private static void confirmInstall(BaseFragment fragment, MZGramIconPacks.Picked picked) {
        if (fragment.isFinishing() || fragment.getParentActivity() == null) {
            MZGramIconPacks.discard(picked);
            return;
        }
        if (picked == null || picked.pack == null) {
            int error = picked == null ? MZGramIconPacks.ERROR_READ : picked.error;
            int text;
            if (error == MZGramIconPacks.ERROR_TOO_LARGE) {
                text = R.string.MZGramIconPacksTooLarge;
            } else if (error == MZGramIconPacks.ERROR_NO_ICONS) {
                text = R.string.MZGramIconPacksNoIcons;
            } else if (error == MZGramIconPacks.ERROR_NOT_PACK) {
                text = R.string.MZGramIconPacksNotPack;
            } else {
                text = R.string.MZGramIconPacksReadError;
            }
            BulletinFactory.of(fragment).createErrorBulletin(getString(text)).show();
            return;
        }
        MZGramIconPacks.Pack pack = picked.pack;
        StringBuilder text = new StringBuilder();
        if (!TextUtils.isEmpty(pack.author)) {
            text.append(formatString(R.string.MZGramIconPacksAuthor, pack.author)).append('\n');
        }
        text.append(formatString(R.string.MZGramIconPacksVersion, pack.version)).append('\n');
        text.append(formatString(R.string.MZGramIconPacksCount, pack.count()));
        if (pack.declaredTintable != null) {
            text.append('\n').append(getString(pack.declaredTintable ? R.string.MZGramIconPacksTinted : R.string.MZGramIconPacksColorful));
        }
        List<MZGramIconPacks.Pack> installed = MZGramIconPacks.installed();
        for (MZGramIconPacks.Pack other : installed) {
            if (other.key.equals(pack.key)) {
                text.append("\n\n").append(getString(R.string.MZGramIconPacksReplaces));
                break;
            }
        }
        final boolean[] installing = {false};
        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(pack.name)
                .setMessage(text.toString())
                .setPositiveButton(getString(R.string.MZGramIconPacksInstallButton), (d, which) -> {
                    installing[0] = true;
                    MZGramIconPacks.install(picked, ok -> {
                        if (fragment.isFinishing() || fragment.getParentActivity() == null) {
                            return;
                        }
                        if (ok) {
                            rebuildScreens(fragment);
                            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramIconPacksInstalledDone)).show();
                        } else {
                            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.MZGramIconPacksInstallFailed)).show();
                        }
                    });
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        if (fragment.showDialog(dialog, d -> {
            if (!installing[0]) {
                MZGramIconPacks.discard(picked);
            }
        }) == null) {
            MZGramIconPacks.discard(picked);
        }
    }
}
