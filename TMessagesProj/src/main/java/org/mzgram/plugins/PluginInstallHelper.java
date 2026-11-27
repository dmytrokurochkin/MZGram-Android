package org.mzgram.plugins;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.mzgram.plugins.ui.PluginInstallBottomSheet;
import org.mzgram.plugins.ui.PluginPermissionsActivity;
import java.util.Map;

public final class PluginInstallHelper {

    private static final String[] EXTENSIONS = {
            PluginsConstants.PLUGIN_EXT,       // .plugin
            PluginsConstants.PLUGIN_EXT_MZPACK,  // .mzpack
            PluginsConstants.PLUGIN_EXT_EAF,   // .eaf
    };

    private PluginInstallHelper() {
    }

    private static String extensionOf(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(name)) {
            name = uri.getLastPathSegment();
        }
        return pluginExtension(name);
    }

    private static String pluginExtension(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        name = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }

    public static boolean isPluginUri(Context context, Uri uri) {
        return context != null && uri != null && extensionOf(context, uri) != null;
    }

    public static boolean handleMessageTap(Activity activity, MessageObject message) {
        if (activity == null || message == null || pluginExtension(message.getDocumentName()) == null) {
            return false;
        }
        File file = FileLoader.getInstance(UserConfig.selectedAccount)
                .getPathToMessage(message.messageOwner);
        return explainOwnPluginsOnly(file);
    }

    public static boolean handleFileTap(Activity activity, String name, File file) {
        return activity != null && pluginExtension(name) != null && explainOwnPluginsOnly(file);
    }

    /**
     * MZGram installs only plugins the user brings as a file of their own:
     * a plugin file tapped in a chat is not installed, the tap says where
     * plugins are added instead.
     */
    private static boolean explainOwnPluginsOnly(File file) {
        if (file == null || !file.isFile() || file.length() == 0) {
            // Not downloaded yet: let the normal download run.
            return false;
        }
        AndroidUtilities.runOnUIThread(() -> {
            org.telegram.ui.ActionBar.BaseFragment last = org.telegram.ui.LaunchActivity.getLastFragment();
            org.telegram.ui.Components.BulletinFactory factory = last != null
                    ? org.telegram.ui.Components.BulletinFactory.of(last)
                    : org.telegram.ui.Components.BulletinFactory.global();
            if (factory == null) {
                return;
            }
            factory.createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.MZPluginsChatTapBlocked),
                    LocaleController.getString(R.string.MZGramPlugins),
                    () -> {
                        org.telegram.ui.ActionBar.BaseFragment f = org.telegram.ui.LaunchActivity.getLastFragment();
                        if (f != null) {
                            f.presentFragment(new org.mzgram.plugins.ui.PluginsActivity());
                        }
                    }).show();
        });
        return true;
    }

    /**
     * True when the call comes from plugin code (Python): a plugin may not
     * install other plugins, only the user does that from a file.
     */
    public static boolean calledFromPlugin() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String c = e.getClassName();
            if (c != null && c.startsWith("com.chaquo.python")) {
                return true;
            }
        }
        return false;
    }

    public static boolean handleViewIntent(Activity activity, Uri uri) {
        if (activity == null || uri == null) {
            return false;
        }
        String ext = extensionOf(activity, uri);
        if (ext == null) {
            return false;
        }
        File cached = copyToCache(activity, uri, ext);
        if (cached == null) {
            AndroidUtilities.runOnUIThread(() -> showError(activity,
                    LocaleController.getString(R.string.PluginsInstallReadError)));
            return true;
        }
        AndroidUtilities.runOnUIThread(() -> PluginsController.getInstance()
                .showInstallDialog(null, cached.getAbsolutePath(), false));
        return true;
    }

    private static File copyToCache(Activity activity, Uri uri, String ext) {
        File target = new File(activity.getCacheDir(), "plugin_incoming" + ext);
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            File direct = new File(uri.getPath());
            if (direct.canRead()) {
                try (InputStream in = new java.io.FileInputStream(direct);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                    return target;
                } catch (Throwable t) {
                    FileLog.e("PluginInstallHelper: cannot read " + direct, t);
                    return null;
                }
            }
        }
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return target;
        } catch (Throwable t) {
            FileLog.e("PluginInstallHelper: cannot read " + uri, t);
            return null;
        }
    }

    public static void confirmAndInstall(Activity activity, File file) {
        confirmAndInstall(activity, new org.mzgram.sdk.plugins.ui.components
                .InstallPluginBottomSheet.PluginInstallParams(file.getAbsolutePath(), false));
    }

    public static void confirmAndInstall(Activity activity,
                                         org.mzgram.sdk.plugins.ui.components
                                                 .InstallPluginBottomSheet.PluginInstallParams params) {
        final File file = params.toFile();
        if (file == null) {
            return;
        }
        PluginsController controller = PluginsController.getInstance();
        if (!controller.isEngineEnabled()) {
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.PluginsInstallTitle))
                    .setMessage(LocaleController.getString(R.string.PluginsEngineDisabledHint))
                    .setPositiveButton(LocaleController.getString(R.string.PluginsEngineEnableAndInstall),
                            (dialog, which) -> {
                                controller.setEngineEnabled(true);
                                confirmAndInstall(activity, params);
                            })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        }
        controller.readMetadataAsync(file, plugin -> {
            final Map<String, List<String>> capabilities = PluginCapabilityScan.scan(file);
            final Map<String, List<String>> offered = offeredPermissions(plugin, capabilities);
            AndroidUtilities.runOnUIThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                showConsentSheet(activity, file, params, plugin, offered, capabilities);
            });
        });
    }

    private static Map<String, List<String>> offeredPermissions(
            Plugin plugin, Map<String, List<String>> capabilities) {
        final boolean obfuscated = PluginCapabilityScan.isObfuscated(capabilities);
        if (!capabilities.isEmpty() && !obfuscated) {
            Map<String, List<String>> offered = new LinkedHashMap<>(capabilities);
            List<String> extra = new ArrayList<>();
            if (plugin != null && plugin.permissionsDeclared) {
                extra.addAll(PluginPermissions.getRequested(plugin));
            }
            List<String> stored = plugin != null ? PluginPermissions.getStored(plugin.id) : null;
            if (stored != null) {
                extra.addAll(stored);
            }
            for (String permission : PluginPermissions.sanitize(extra)) {
                if (!PluginPermissions.UI.equals(permission) && !offered.containsKey(permission)) {
                    offered.put(permission, PluginCapabilityScan.evidenceOf(capabilities, permission));
                }
            }
            return offered;
        }
        List<String> fallback = new ArrayList<>(!obfuscated && plugin != null && plugin.permissionsDeclared
                ? PluginPermissions.getRequested(plugin)
                : PluginPermissions.REQUESTABLE);
        List<String> stored = plugin != null ? PluginPermissions.getStored(plugin.id) : null;
        if (stored != null) {
            fallback.addAll(stored);
            fallback.remove(PluginPermissions.UI);
        }
        Map<String, List<String>> offered = new LinkedHashMap<>();
        for (String permission : PluginPermissions.sanitize(fallback)) {
            offered.put(permission, PluginCapabilityScan.evidenceOf(capabilities, permission));
        }
        if (obfuscated) {
            offered.put(PluginCapabilityScan.KEY_OBFUSCATION,
                    PluginCapabilityScan.obfuscationEvidence(capabilities));
        }
        return offered;
    }

    private static void showConsentSheet(Activity activity, File file,
                                         org.mzgram.sdk.plugins.ui.components
                                                 .InstallPluginBottomSheet.PluginInstallParams params,
                                         Plugin plugin,
                                         Map<String, List<String>> offered,
                                         Map<String, List<String>> capabilities) {
        new PluginInstallBottomSheet(activity, file, params, plugin, offered,
                (granted, enableAfterInstall) -> {
                    grantOnConsent(plugin, granted);
                    if (plugin != null && plugin.id != null) {
                        PluginCapabilityScan.store(plugin.id, capabilities);
                    }
                    install(activity, file, plugin != null ? plugin.id : null, enableAfterInstall);
                }).show();
    }

    private static void grantOnConsent(Plugin plugin, List<String> granted) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) {
            return;
        }
        PluginPermissions.setGranted(plugin.id, granted);
        final int level;
        if (granted.isEmpty()) {
            level = PluginTrustLevel.ISOLATED;
        } else if (granted.contains(PluginPermissions.HOOKS)
                || granted.contains(PluginPermissions.NATIVE)) {
            level = PluginTrustLevel.TRUSTED;
        } else {
            level = PluginTrustLevel.GATED;
        }
        PluginTrustLevel.setLevel(plugin.id, level);
    }

    private static void install(Activity activity, File file, String consentedId,
                                boolean enableAfterInstall) {
        AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage(LocaleController.getString(R.string.PluginsInstalling));
        progress.setCanCancel(false);
        progress.show();
        final boolean update = consentedId != null
                && PluginsController.getInstance().getPlugin(consentedId) != null;
        PluginsController.getInstance().installPlugin(file, enableAfterInstall, (ok, error, plugin) ->
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progress.dismiss();
                    } catch (Throwable ignored) {
                    }
                    if (activity.isFinishing()) {
                        return;
                    }
                    if (!ok) {
                        Plugin registered = plugin;
                        if (registered == null && consentedId != null) {
                            registered = PluginsController.getInstance().getPlugin(consentedId);
                        }
                        if (enableAfterInstall && isPermissionDenial(error)
                                && registered != null && registered.id != null) {
                            PluginsController.getInstance().setPluginEnabled(registered.id, true);
                            showError(activity, humanError(error, consentedId),
                                    plugin == null ? null : plugin.loadDebug);
                            return;
                        }
                        if (consentedId != null
                                && PluginsController.getInstance().getPlugin(consentedId) == null) {
                            PluginPermissions.clear(consentedId);
                        }
                        showError(activity, humanError(error, consentedId),
                                plugin == null ? null : plugin.loadDebug);
                        return;
                    }
                    if (enableAfterInstall && plugin != null && plugin.id != null) {
                        PluginsController.getInstance().setPluginEnabled(plugin.id, true);
                    }
                    showInstalled(plugin, update);
                }));
    }

    private static boolean isPermissionDenial(CharSequence error) {
        if (error == null) {
            return false;
        }
        String text = error.toString();
        return text.contains("PermissionError") || text.contains("missing the");
    }

    private static CharSequence humanError(CharSequence error, String pluginId) {
        if (error == null) {
            return LocaleController.getString(R.string.PluginsInstallError);
        }
        if (!isPermissionDenial(error)) {
            return error;
        }
        String text = error.toString();
        String permission = null;
        for (String candidate : PluginPermissions.ALL) {
            if (text.contains("'" + candidate + "'")) {
                permission = candidate;
                break;
            }
        }
        if (permission == null) {
            return LocaleController.getString(R.string.PluginsInstallDeniedGeneric);
        }
        return LocaleController.formatString(R.string.PluginsInstallDenied,
                PluginPermissionsActivity.titleOf(permission));
    }

    private static void showInstalled(Plugin plugin, boolean update) {
        org.telegram.ui.ActionBar.BaseFragment fragment =
                org.telegram.ui.LaunchActivity.getSafeLastFragment();
        CharSequence text = LocaleController.formatString(update ? R.string.PluginsUpdated : R.string.PluginsInstalled,
                plugin != null ? plugin.getDisplayName() : "");
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        org.telegram.ui.Components.BulletinFactory.of(fragment)
                .createSimpleBulletin(R.raw.contact_check, text)
                .show();
    }

    private static void showError(Activity activity, CharSequence message) {
        showError(activity, message, null);
    }

    private static void showError(Activity activity, CharSequence message, String debug) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.PluginsInstallError))
                .setMessage(message)
                .setPositiveButton(LocaleController.getString(R.string.OK), null);
        final String report = debug != null && !debug.isEmpty()
                ? debug : (message == null ? null : message.toString());
        if (report != null && !report.isEmpty()) {
            builder.setNeutralButton(LocaleController.getString(R.string.PluginsInstallCopyReport),
                    (dialog, which) -> {
                        AndroidUtilities.addToClipboard(report);
                        if (!activity.isFinishing()) {
                            Toast.makeText(activity, LocaleController.getString(R.string.TextCopied),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
        builder.show();
    }
}
