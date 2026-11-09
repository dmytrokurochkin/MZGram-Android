/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Texts about one plugin shared by the plugin screens: the line under its
 * name and the names and icons of the permission keys of __permissions__
 * (the keys of mzgram_plugins/metadata.py).
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat;
import org.telegram.messenger.mzgram.plugins.MZGramPluginGrants;

import java.util.List;

final class MZGramPluginTexts {

    private static final String[] KEYS = {
            "ui", "messages.read", "messages.send", "network", "files", "intents", "settings", "hooks", "native",
    };
    private static final int[] NAMES = {
            R.string.MZGramPluginPermUi,
            R.string.MZGramPluginPermMessagesRead,
            R.string.MZGramPluginPermMessagesSend,
            R.string.MZGramPluginPermNetwork,
            R.string.MZGramPluginPermFiles,
            R.string.MZGramPluginPermIntents,
            R.string.MZGramPluginPermSettings,
            R.string.MZGramPluginPermHooks,
            R.string.MZGramPluginPermNative,
    };
    private static final int[] ICONS = {
            R.drawable.msg_palette,
            R.drawable.msg_message,
            R.drawable.msg_send,
            R.drawable.msg_language,
            R.drawable.msg_folders,
            R.drawable.msg_openin,
            R.drawable.msg_settings,
            R.drawable.msg_customize,
            R.drawable.msg_bots,
    };

    private MZGramPluginTexts() {
    }

    private static int indexOf(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** What the permission allows; the key itself for one MZGram doesn't know. */
    static String permissionName(String key) {
        final int index = indexOf(key);
        return index >= 0 ? getString(NAMES[index]) : key;
    }

    static int permissionIcon(String key) {
        final int index = indexOf(key);
        return index >= 0 ? ICONS[index] : R.drawable.msg2_permissions;
    }

    /** The line under the plugin's name: its version and author. */
    static String subtitle(MZGramPluginFormat.Meta meta) {
        String version = meta.version;
        if (meta.beta) {
            version = version + " (" + getString(R.string.MZGramPluginBeta) + ")";
        }
        return TextUtils.isEmpty(meta.author) ? version : version + " · " + meta.author;
    }

    /**
     * The permissions the user may grant, without "ui" (always granted):
     * the declared ones, or all of them when the plugin declares none.
     */
    static List<String> offeredPermissions(MZGramPluginFormat.Meta meta) {
        final List<String> out = MZGramPluginGrants.sanitize(meta.permissionsDeclared ? meta.permissions : MZGramPluginGrants.ALL);
        out.remove(MZGramPluginGrants.UI);
        return out;
    }

    /** The permissions as lines, for a dialog. */
    static String permissionsText(MZGramPluginFormat.Meta meta) {
        if (!meta.permissionsDeclared) {
            return getString(R.string.MZGramPluginPermissionsMissing);
        }
        if (meta.permissions.isEmpty()) {
            return getString(R.string.MZGramPluginNoPermissions);
        }
        final StringBuilder text = new StringBuilder(getString(R.string.MZGramPluginPermissions));
        for (String key : meta.permissions) {
            text.append("\n• ").append(permissionName(key));
        }
        return text.toString();
    }
}
