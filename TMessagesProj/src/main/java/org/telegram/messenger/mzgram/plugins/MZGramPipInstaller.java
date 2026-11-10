/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Mini-pip installer on device: queries PyPI JSON API and downloads/extracts
 * pure-Python wheels (*-none-any.whl) on user request and explicit consent.
 * Native code / compiled binaries are rejected with a clear message.
 */

package org.telegram.messenger.mzgram.plugins;

import android.app.Activity;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.List;

public final class MZGramPipInstaller {

    private static final String MODULE = "mzgram_plugins.pip_controller";

    public static final class PackageInfo {
        public final String name;
        public final String version;
        public final String filename;
        public final long size;
        public final boolean installed;

        PackageInfo(JSONObject json) {
            this.name = json.optString("name", "");
            this.version = json.optString("version", "");
            this.filename = json.optString("filename", "");
            this.size = json.optLong("size", 0L);
            this.installed = json.optBoolean("installed", false);
        }
    }

    public static final class QuerySummary {
        public final boolean ok;
        public final String error;
        public final long totalSize;
        public final List<PackageInfo> packages = new ArrayList<>();

        QuerySummary(boolean ok, String error, long totalSize, JSONArray arr) {
            this.ok = ok;
            this.error = error;
            this.totalSize = totalSize;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        packages.add(new PackageInfo(obj));
                    }
                }
            }
        }
    }

    public static final class InstallResult {
        public final boolean ok;
        public final String error;
        public final List<String> installed = new ArrayList<>();

        InstallResult(boolean ok, String error, JSONArray arr) {
            this.ok = ok;
            this.error = error;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String item = arr.optString(i, null);
                    if (item != null) {
                        installed.add(item);
                    }
                }
            }
        }
    }

    private MZGramPipInstaller() {
    }

    public static boolean isPipEnabled() {
        return MZGramConfig.pipEnabled && MZGramPythonRuntime.isAvailable();
    }

    public static void queryRequirements(List<String> requirements, Utilities.Callback<QuerySummary> onResult) {
        if (!isPipEnabled()) {
            if (onResult != null) {
                AndroidUtilities.runOnUIThread(() -> onResult.run(new QuerySummary(false, "pip disabled", 0, null)));
            }
            return;
        }
        try {
            JSONObject req = new JSONObject();
            JSONArray arr = new JSONArray();
            if (requirements != null) {
                for (String r : requirements) {
                    if (!TextUtils.isEmpty(r)) {
                        arr.put(r);
                    }
                }
            }
            req.put("requirements", arr);
            MZGramPythonRuntime.call(MODULE, "query_requirements_json", req.toString(), raw -> {
                if (raw == null) {
                    if (onResult != null) {
                        onResult.run(new QuerySummary(false, "Python runtime unavailable", 0, null));
                    }
                    return;
                }
                try {
                    JSONObject res = new JSONObject(raw);
                    boolean ok = res.optBoolean("ok", false);
                    String error = res.optString("error", "");
                    long totalSize = res.optLong("total_size", 0);
                    JSONArray pkgs = res.optJSONArray("packages");
                    if (onResult != null) {
                        onResult.run(new QuerySummary(ok, error, totalSize, pkgs));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    if (onResult != null) {
                        onResult.run(new QuerySummary(false, e.getMessage(), 0, null));
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
            if (onResult != null) {
                onResult.run(new QuerySummary(false, e.getMessage(), 0, null));
            }
        }
    }

    public static void installRequirements(String pluginId, List<String> requirements, Utilities.Callback<InstallResult> onResult) {
        if (!isPipEnabled()) {
            if (onResult != null) {
                AndroidUtilities.runOnUIThread(() -> onResult.run(new InstallResult(false, "pip disabled", null)));
            }
            return;
        }
        try {
            JSONObject req = new JSONObject();
            req.put("plugin_id", pluginId);
            JSONArray arr = new JSONArray();
            if (requirements != null) {
                for (String r : requirements) {
                    if (!TextUtils.isEmpty(r)) {
                        arr.put(r);
                    }
                }
            }
            req.put("requirements", arr);
            MZGramPythonRuntime.call(MODULE, "install_requirements_json", req.toString(), raw -> {
                if (raw == null) {
                    if (onResult != null) {
                        onResult.run(new InstallResult(false, "Python runtime unavailable", null));
                    }
                    return;
                }
                try {
                    JSONObject res = new JSONObject(raw);
                    boolean ok = res.optBoolean("ok", false);
                    String error = res.optString("error", "");
                    JSONArray installed = res.optJSONArray("installed");
                    if (onResult != null) {
                        onResult.run(new InstallResult(ok, error, installed));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    if (onResult != null) {
                        onResult.run(new InstallResult(false, e.getMessage(), null));
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
            if (onResult != null) {
                onResult.run(new InstallResult(false, e.getMessage(), null));
            }
        }
    }

    public static void removeRequirements(String pluginId) {
        if (!MZGramPythonRuntime.isAvailable()) {
            return;
        }
        try {
            JSONObject req = new JSONObject();
            req.put("plugin_id", pluginId);
            MZGramPythonRuntime.call(MODULE, "remove_requirements_json", req.toString(), null);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void showInstallDialog(BaseFragment fragment, String pluginId, List<String> requirements, Runnable onComplete) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final Activity activity = fragment.getParentActivity();
        if (!MZGramConfig.pipEnabled) {
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.MZGramPipEnabled))
                    .setMessage(LocaleController.getString(R.string.MZGramPipEnabledInfo))
                    .setPositiveButton(LocaleController.getString(R.string.MZGramOk), null)
                    .show();
            return;
        }

        final AlertDialog[] progress = new AlertDialog[1];
        progress[0] = new AlertDialog(activity, 3);
        progress[0].setCanCanceled(false);
        progress[0].show();

        queryRequirements(requirements, summary -> {
            if (progress[0] != null) {
                progress[0].dismiss();
                progress[0] = null;
            }
            if (fragment.getParentActivity() == null) {
                return;
            }

            if (!summary.ok) {
                new AlertDialog.Builder(activity)
                        .setTitle(LocaleController.getString(R.string.MZGramPipInstallTitle))
                        .setMessage(LocaleController.formatString(R.string.MZGramPipInstallFailed, summary.error))
                        .setPositiveButton(LocaleController.getString(R.string.MZGramOk), null)
                        .show();
                return;
            }

            boolean hasUninstalled = false;
            StringBuilder sb = new StringBuilder();
            for (PackageInfo pkg : summary.packages) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("• ").append(pkg.name).append(" ").append(pkg.version);
                if (pkg.installed) {
                    sb.append(" (installed)");
                } else {
                    hasUninstalled = true;
                    if (pkg.size > 0) {
                        sb.append(" (~").append(AndroidUtilities.formatFileSize(pkg.size)).append(")");
                    }
                }
            }

            if (!hasUninstalled) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MZGramPipInstallSuccess)).show();
                return;
            }

            sb.append("\n\n");
            sb.append(LocaleController.getString(R.string.MZGramPipInstallWarning));

            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.MZGramPipInstallTitle))
                    .setMessage(sb.toString())
                    .setPositiveButton(LocaleController.getString(R.string.MZGramPipInstallPackages), (dialog, which) -> {
                        final AlertDialog[] installProgress = new AlertDialog[1];
                        installProgress[0] = new AlertDialog(activity, 3);
                        installProgress[0].setCanCanceled(false);
                        installProgress[0].show();

                        installRequirements(pluginId, requirements, result -> {
                            if (installProgress[0] != null) {
                                installProgress[0].dismiss();
                                installProgress[0] = null;
                            }
                            if (fragment.getParentActivity() == null) {
                                return;
                            }
                            if (result.ok) {
                                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MZGramPipInstallSuccess)).show();
                                if (onComplete != null) {
                                    onComplete.run();
                                }
                            } else {
                                new AlertDialog.Builder(activity)
                                        .setTitle(LocaleController.getString(R.string.MZGramPipInstallTitle))
                                        .setMessage(LocaleController.formatString(R.string.MZGramPipInstallFailed, result.error))
                                        .setPositiveButton(LocaleController.getString(R.string.MZGramOk), null)
                                        .show();
                            }
                        });
                    })
                    .setNegativeButton(LocaleController.getString(R.string.MZGramCancel), null)
                    .show();
        });
    }
}
