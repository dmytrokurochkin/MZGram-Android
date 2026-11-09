/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Tells the user that a plugin was refused something. A refusal does not
 * break the plugin: its action just does nothing, which looks like a bug.
 * This notice is the answer to "why does it not work": the plugin's name,
 * what it lacked and a button that grants it (or opens the plugin's screen
 * when the plugin does not ask for that permission, so the screen would
 * have no switch to take it back).
 *
 * Shown once per plugin and permission while the app runs (hooks fire
 * often, a repeated notice becomes noise), never over the passcode screen;
 * a refusal with no screen to show it on is told at the next refusal.
 * Granting "hooks" or "native" asks once more, as on the plugin's screen
 * (confirmDangerous). A running plugin starts again with the new grant
 * (MZGramPluginGrants).
 *
 * Based on exteraless plugins/PluginDenialNotice.java (GPL-3.0): one notice
 * per plugin and permission with a grant button and the confirmation of a
 * full-control grant; written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.mzgram.plugins.MZGramPluginGrants;
import org.telegram.messenger.mzgram.plugins.MZGramPluginPermissions;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MZGramPluginDenialNotice {

    // "pluginId\0permission" already told while the app runs
    private static final Set<String> shown = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MZGramPluginDenialNotice() {
    }

    /** The plugin was refused the permission. Any thread. */
    public static void note(String pluginId, String permission) {
        if (TextUtils.isEmpty(pluginId) || TextUtils.isEmpty(permission) || MZGramPluginGrants.UI.equals(permission)) {
            return;
        }
        final String mark = pluginId + '\u0000' + permission;
        if (!shown.add(mark)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (!show(pluginId, permission)) {
                // Nowhere to show it now: the next refusal tries again
                shown.remove(mark);
            }
        });
    }

    /** Forgets what was told (the permissions changed or the plugin went). */
    public static void reset(String pluginId) {
        if (TextUtils.isEmpty(pluginId)) {
            return;
        }
        final String prefix = pluginId + '\u0000';
        for (Iterator<String> it = shown.iterator(); it.hasNext(); ) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }

    private static boolean show(String pluginId, String permission) {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null || SharedConfig.isWaitingForPasscodeEnter) {
            return false;
        }
        final MZGramPlugins.Plugin plugin = MZGramPlugins.get(pluginId);
        if (plugin == null) {
            // Deleted meanwhile: nothing to tell
            return true;
        }
        final String name = plugin.meta.name;
        final String what = MZGramPluginTexts.permissionName(permission);
        // Only what the plugin asks for can be granted here: its screen has a switch to take it back
        final boolean grantable = MZGramPluginTexts.offeredPermissions(plugin.meta).contains(permission);
        final CharSequence text = LocaleController.formatString(grantable
                ? R.string.MZGramPluginDeniedNotice : R.string.MZGramPluginDeniedUndeclared, name, what);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, text,
                getString(grantable ? R.string.MZGramPluginDeniedAllow : R.string.MZGramPluginDeniedOpen), () -> {
                    if (!grantable) {
                        fragment.presentFragment(new MZGramPluginInfoActivity(pluginId));
                    } else if (MZGramPluginPermissions.isDangerous(permission)) {
                        confirmDangerous(fragment.getParentActivity(), fragment.getResourceProvider(), name, permission,
                                () -> grant(fragment, pluginId, name, permission));
                    } else {
                        grant(fragment, pluginId, name, permission);
                    }
                }).show();
        return true;
    }

    private static void grant(BaseFragment fragment, String pluginId, String name, String permission) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        if (MZGramPluginGrants.grant(pluginId, permission)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                    LocaleController.formatString(R.string.MZGramPluginDeniedGranted, name,
                            MZGramPluginTexts.permissionName(permission))).show();
        } else {
            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.MZGramPluginsSaveFailed)).show();
        }
    }

    /**
     * Asks before granting a permission that gives full control ("hooks",
     * "native"); onYes runs on the UI thread when the user agrees. The name
     * comes from the plugin file and is shown as plain text.
     */
    public static void confirmDangerous(Context context, Theme.ResourcesProvider resources, String pluginName,
                                        String permission, Runnable onYes) {
        if (context == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context, resources);
        builder.setTitle(MZGramPluginTexts.permissionName(permission));
        builder.setMessage(LocaleController.formatString(R.string.MZGramPluginDangerousConfirm, pluginName));
        builder.setPositiveButton(getString(R.string.MZGramPluginDeniedAllow), (dialog, which) -> onYes.run());
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        builder.makeRed(AlertDialog.BUTTON_POSITIVE);
        builder.show();
    }
}
