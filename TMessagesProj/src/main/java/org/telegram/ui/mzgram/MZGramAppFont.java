/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The choice of the app font in MZGram → Interface (MZGramFonts): as in
 * Telegram (the default), the font of the phone, or a TTF or OTF file the
 * user picks. Takes effect after a restart.
 */

package org.telegram.ui.mzgram;

import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramFonts;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

public final class MZGramAppFont {

    private MZGramAppFont() {
    }

    /** The value shown in the settings row. */
    public static String title() {
        final int mode = MZGramFonts.mode();
        if (mode == MZGramFonts.SYSTEM) {
            return LocaleController.getString(R.string.MZGramAppFontSystem);
        }
        if (mode == MZGramFonts.FILE) {
            final String name = MZGramConfig.appFontName;
            return TextUtils.isEmpty(name) ? LocaleController.getString(R.string.MZGramAppFontFileShort) : name;
        }
        return LocaleController.getString(R.string.MZGramAppFontTelegram);
    }

    /**
     * Telegram or the system font are applied at once (for the next start);
     * a file runs pickFile and is applied in fileImported.
     */
    public static void choose(BaseFragment fragment, Runnable onChanged, Runnable pickFile) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final boolean file = MZGramFonts.fileSupported();
        final CharSequence[] names = new CharSequence[file ? 3 : 2];
        names[0] = LocaleController.getString(R.string.MZGramAppFontTelegram);
        names[1] = LocaleController.getString(R.string.MZGramAppFontSystem);
        if (file) {
            names[2] = LocaleController.getString(R.string.MZGramAppFontFile);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MZGramAppFont));
        builder.setItems(names, (dialog, which) -> {
            if (which == MZGramFonts.FILE) {
                if (pickFile != null) {
                    pickFile.run();
                }
                return;
            }
            final int before = MZGramFonts.mode();
            MZGramConfig.setAppFont(which, null);
            MZGramFonts.deleteFile();
            if (onChanged != null) {
                onChanged.run();
            }
            if (before != which) {
                showRestart(fragment);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    /** After MZGramFonts.importFont on the UI thread: name is null when the file is no font. */
    public static void fileImported(BaseFragment fragment, String name, Runnable onChanged) {
        if (name == null) {
            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MZGramAppFontInvalid)).show();
            return;
        }
        MZGramConfig.setAppFont(MZGramFonts.FILE, name);
        if (onChanged != null) {
            onChanged.run();
        }
        showRestart(fragment);
    }

    private static void showRestart(BaseFragment fragment) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MZGramAppFontRestart)).show();
    }
}
