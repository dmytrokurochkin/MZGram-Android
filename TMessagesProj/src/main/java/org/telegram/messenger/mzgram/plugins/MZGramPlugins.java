/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The plugins added to MZGram: each .plugin file is kept in the app's own
 * folder as files/mzgram_plugins/<id>.plugin, and its metadata (read by
 * MZGramPluginFormat without running the file) in the SharedPreferences
 * "mzgram_plugins", so the list can be shown without starting Python.
 * Whether a plugin is turned on is kept here too (off when added).
 *
 * Nothing here runs a plugin: running them is the job of the plugin engine
 * (MZGramPluginEngine), told here of every change of a plugin.
 * A file name only ever comes from a valid plugin id
 * (MZGramPluginSettingsStore.isValidId, the rule of __id__).
 *
 * Based on exteraless PluginsController (GPL-3.0): one folder of plugin
 * files and a kept on/off state per plugin; written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MZGramPlugins {

    /** The same limit as MAX_SOURCE_BYTES in mzgram_plugins/metadata.py. */
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    /** Error of readPicked() for a file over MAX_FILE_BYTES. */
    public static final String ERROR_TOO_LARGE = "too_large";
    /** Error of readPicked() when the picked file could not be read. */
    public static final String ERROR_READ = "read";

    private static final String DIR = "mzgram_plugins";
    private static final String PICKED_DIR = "mzgram_plugin_picked";
    private static final String SHARE_DIR = "mzgram_plugins";
    private static final String PREFS = "mzgram_plugins";
    private static final String KEY_META = "meta_";
    private static final String KEY_ENABLED = "enabled_";
    private static final String KEY_ADDED = "added_";

    private MZGramPlugins() {
    }

    /** One added plugin. */
    public static final class Plugin {
        public final MZGramPluginFormat.Meta meta;
        public final File file;
        public final boolean enabled;
        /** When it was first added, in milliseconds. */
        public final long addedAt;

        Plugin(MZGramPluginFormat.Meta meta, File file, boolean enabled, long addedAt) {
            this.meta = meta;
            this.file = file;
            this.enabled = enabled;
            this.addedAt = addedAt;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File dir() {
        final File dir = new File(ApplicationLoader.getFilesDirFixed(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            FileLog.e("MZGramPlugins: cannot create " + dir);
        }
        return dir;
    }

    /** The kept file of the plugin, or null for an id that is not valid. */
    public static File fileOf(String pluginId) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId)) {
            return null;
        }
        return new File(dir(), pluginId + MZGramPluginFormat.EXTENSION);
    }

    /** The added plugins whose file is still there, by name. */
    public static List<Plugin> list() {
        final ArrayList<Plugin> plugins = new ArrayList<>();
        if (ApplicationLoader.applicationContext == null) {
            return plugins;
        }
        final SharedPreferences prefs = prefs();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(KEY_META) || !(entry.getValue() instanceof String)) {
                continue;
            }
            final Plugin plugin = make(prefs, entry.getKey().substring(KEY_META.length()), (String) entry.getValue());
            if (plugin != null) {
                plugins.add(plugin);
            }
        }
        Collections.sort(plugins, (a, b) -> {
            final int byName = String.CASE_INSENSITIVE_ORDER.compare(a.meta.name, b.meta.name);
            return byName != 0 ? byName : a.meta.id.compareTo(b.meta.id);
        });
        return plugins;
    }

    /** The added plugin with this id, or null. */
    public static Plugin get(String pluginId) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId) || ApplicationLoader.applicationContext == null) {
            return null;
        }
        final SharedPreferences prefs = prefs();
        return make(prefs, pluginId, prefs.getString(KEY_META + pluginId, null));
    }

    private static Plugin make(SharedPreferences prefs, String pluginId, String json) {
        final MZGramPluginFormat.Meta meta = MZGramPluginFormat.metaFromJson(json);
        final File file = fileOf(pluginId);
        if (meta == null || !pluginId.equals(meta.id) || file == null || !file.isFile()) {
            return null;
        }
        return new Plugin(meta, file, prefs.getBoolean(KEY_ENABLED + pluginId, false), prefs.getLong(KEY_ADDED + pluginId, 0));
    }

    public static int count() {
        return list().size();
    }

    public static void setEnabled(String pluginId, boolean enabled) {
        setEnabled(pluginId, enabled, null);
    }

    /** source: MZGramPluginAuditJournal.SOURCE_DEV_SERVER or null, for the activity log. */
    public static void setEnabled(String pluginId, boolean enabled, String source) {
        final Plugin plugin = get(pluginId);
        if (plugin == null) {
            return;
        }
        prefs().edit().putBoolean(KEY_ENABLED + pluginId, enabled).apply();
        if (plugin.enabled != enabled) {
            MZGramPluginAuditJournal.record(pluginId, enabled ? MZGramPluginAuditJournal.EVENT_ENABLED : MZGramPluginAuditJournal.EVENT_DISABLED,
                    MZGramPluginAuditJournal.CATEGORY_MANAGE, "", null, source, MZGramPluginAuditJournal.LEVEL_INFO);
            MZGramPluginEngine.apply(pluginId);
        }
    }

    /**
     * Copies a file the user picked into a temporary .plugin file and reads
     * its metadata without running it. onResult gets, on the UI thread, the
     * temporary file (null on a read error) and the result; the error of a
     * failed result may be ERROR_TOO_LARGE or ERROR_READ. The temporary file
     * goes to install() or discard().
     */
    public static void readPicked(Uri uri, Utilities.Callback2<File, MZGramPluginFormat.Result> onResult) {
        if (onResult == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            final File picked = copyPicked(uri);
            if (picked == null || !picked.isFile()) {
                final String error = picked == null ? ERROR_READ : ERROR_TOO_LARGE;
                discard(picked);
                AndroidUtilities.runOnUIThread(() -> onResult.run(null, new MZGramPluginFormat.Result(null, error)));
                return;
            }
            AndroidUtilities.runOnUIThread(() -> MZGramPluginFormat.readMetadata(picked, result -> {
                if (result == null || !result.isOk()) {
                    discard(picked);
                    onResult.run(null, result != null ? result : new MZGramPluginFormat.Result(null, ERROR_READ));
                } else {
                    onResult.run(picked, result);
                }
            }));
        });
    }

    // The picked file in the cache folder; a missing file (not null) when it
    // is too large, null when it could not be read
    private static File copyPicked(Uri uri) {
        final Context context = ApplicationLoader.applicationContext;
        if (uri == null || context == null) {
            return null;
        }
        final File folder = new File(context.getCacheDir(), PICKED_DIR);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return null;
        }
        // Copies left by an earlier pick that never reached install or discard
        final File[] old = folder.listFiles();
        if (old != null) {
            final long dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            for (File file : old) {
                if (file.lastModified() < dayAgo) {
                    file.delete();
                }
            }
        }
        final File target = new File(folder, "picked_" + System.nanoTime() + MZGramPluginFormat.EXTENSION);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            try (OutputStream out = new FileOutputStream(target)) {
                if (!copy(in, out, MAX_FILE_BYTES)) {
                    out.close();
                    target.delete();
                    return target;
                }
            }
            return target;
        } catch (Exception e) {
            FileLog.e(e);
            target.delete();
            return null;
        }
    }

    /**
     * Writes a .plugin file sent by the developer server into the folder of
     * picked files, so it goes to readMetadata() and then install() or
     * discard() like a picked one; null when it is too large or can't be
     * written. Not on the UI thread.
     */
    public static File writeIncoming(byte[] data) {
        final Context context = ApplicationLoader.applicationContext;
        if (data == null || data.length > MAX_FILE_BYTES || context == null) {
            return null;
        }
        final File folder = new File(context.getCacheDir(), PICKED_DIR);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return null;
        }
        final File target = new File(folder, "incoming_" + System.nanoTime() + MZGramPluginFormat.EXTENSION);
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(data);
            return target;
        } catch (Exception e) {
            FileLog.e(e);
            target.delete();
            return null;
        }
    }

    // False when the stream is longer than limit
    private static boolean copy(InputStream in, OutputStream out, long limit) throws IOException {
        final byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > limit) {
                return false;
            }
            out.write(buffer, 0, read);
        }
        return true;
    }

    /** Deletes a temporary file of readPicked() that is not installed. */
    public static void discard(File picked) {
        if (picked != null && picked.getParentFile() != null && PICKED_DIR.equals(picked.getParentFile().getName())) {
            picked.delete();
        }
    }

    /**
     * Keeps the picked file as the plugin meta.id, replacing an earlier file
     * of the same id (its settings and on/off state stay). onDone gets true
     * on the UI thread when the file is in place.
     */
    public static void install(File picked, MZGramPluginFormat.Meta meta, Utilities.Callback<Boolean> onDone) {
        install(picked, meta, null, onDone);
    }

    /** source: MZGramPluginAuditJournal.SOURCE_DEV_SERVER or null, for the activity log. */
    public static void install(File picked, MZGramPluginFormat.Meta meta, String source, Utilities.Callback<Boolean> onDone) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            final Plugin before = meta != null ? get(meta.id) : null;
            final File target = meta != null ? fileOf(meta.id) : null;
            if (picked != null && picked.isFile() && target != null) {
                final File part = new File(target.getParentFile(), target.getName() + ".part");
                try (InputStream in = new FileInputStream(picked); OutputStream out = new FileOutputStream(part)) {
                    ok = copy(in, out, MAX_FILE_BYTES);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (ok && !part.renameTo(target)) {
                    ok = target.delete() && part.renameTo(target);
                }
                if (ok) {
                    final SharedPreferences prefs = prefs();
                    final SharedPreferences.Editor editor = prefs.edit().putString(KEY_META + meta.id, meta.json);
                    final boolean update = prefs.contains(KEY_ADDED + meta.id);
                    if (!update) {
                        editor.putLong(KEY_ADDED + meta.id, System.currentTimeMillis());
                    }
                    editor.apply();
                    if (update) {
                        MZGramPluginAuditJournal.record(meta.id, MZGramPluginAuditJournal.EVENT_UPDATED, MZGramPluginAuditJournal.CATEGORY_MANAGE,
                                meta.version, before != null ? before.meta.version : "", source, MZGramPluginAuditJournal.LEVEL_INFO);
                        // Granted keys the new version no longer declares are taken back
                        MZGramPluginGrants.retainDeclared(meta.id, meta.permissionsDeclared ? meta.permissions : null);
                    } else {
                        MZGramPluginAuditJournal.record(meta.id, MZGramPluginAuditJournal.EVENT_ADDED, MZGramPluginAuditJournal.CATEGORY_MANAGE,
                                meta.version, null, source, MZGramPluginAuditJournal.LEVEL_INFO);
                        // A new plugin with an old id never gets what was granted before
                        MZGramPluginGrants.clear(meta.id);
                    }
                } else {
                    part.delete();
                }
            }
            discard(picked);
            final boolean done = ok;
            if (done) {
                // A plugin that is on starts again from its new file
                MZGramPluginEngine.apply(meta.id);
            }
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(() -> onDone.run(done));
            }
        });
    }

    /** Removes the plugin: its file, its state and its settings values. */
    public static void delete(String pluginId) {
        delete(pluginId, null);
    }

    /** source: MZGramPluginAuditJournal.SOURCE_DEV_SERVER or null, for the activity log. */
    public static void delete(String pluginId, String source) {
        final File file = fileOf(pluginId);
        if (file == null) {
            return;
        }
        final boolean existed = get(pluginId) != null;
        MZGramPluginEngine.forget(pluginId);
        if (file.exists() && !file.delete()) {
            FileLog.e("MZGramPlugins: cannot delete " + file);
        }
        prefs().edit().remove(KEY_META + pluginId).remove(KEY_ENABLED + pluginId).remove(KEY_ADDED + pluginId).apply();
        MZGramPluginSettingsStore.clear(pluginId);
        MZGramPluginGrants.clear(pluginId);
        MZGramPluginHookGate.forget(pluginId);
        MZGramPluginAuditJournal.forget(pluginId);
        if (existed) {
            MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_DELETED, MZGramPluginAuditJournal.CATEGORY_MANAGE, "", null, source, MZGramPluginAuditJournal.LEVEL_INFO);
        }
    }

    /**
     * The source of the plugin as text, or null when it can't be read or is
     * not text. Reads the file, so not on the UI thread.
     */
    public static String readSource(String pluginId) {
        return readSource(fileOf(pluginId));
    }

    /** The same for any .plugin file, such as one picked and not added yet. */
    public static String readSource(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_FILE_BYTES) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            final byte[] bytes = new byte[(int) file.length()];
            int total = 0;
            int read;
            while (total < bytes.length && (read = in.read(bytes, total, bytes.length - total)) > 0) {
                total += read;
            }
            for (int i = 0; i < total; i++) {
                if (bytes[i] == 0) {
                    return null;
                }
            }
            return new String(bytes, 0, total, StandardCharsets.UTF_8);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * A copy of the plugin file in the shared cache folder (files/cache), the
     * only place the app's FileProvider gives out; null on failure. Not on
     * the UI thread.
     */
    public static File shareCopy(String pluginId) {
        final File file = fileOf(pluginId);
        if (file == null || !file.isFile()) {
            return null;
        }
        final File folder = new File(new File(ApplicationLoader.getFilesDirFixed(), "cache"), SHARE_DIR);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return null;
        }
        final File copy = new File(folder, file.getName());
        try (InputStream in = new FileInputStream(file); OutputStream out = new FileOutputStream(copy)) {
            return copy(in, out, MAX_FILE_BYTES) ? copy : null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}
