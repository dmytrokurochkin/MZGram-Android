/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Plugins backup and restore: packages installed plugins, their non-sensitive
 * settings, and granted permissions into a ZIP archive for migration between
 * devices or creating a local backup.
 *
 * Security rules:
 * 1. Tokens, passwords, and API secrets are never included in backups.
 * 2. Restored plugins from the catalog strictly enforce author "MZGram"
 *    and strip any third-party icons.
 * 3. Corrupted plugin archives or malformed entries are handled gracefully
 *    and never crash or prevent restoring valid plugins or settings.
 */

package org.telegram.messenger.mzgram.plugins;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.mzgram.MZGramConfig;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class MZGramPluginsBackup {

    public static final String EXTENSION = ".plugins.zip";
    public static final String FORMAT = "mzgram-plugins";
    public static final int VERSION = 1;
    public static final String MANIFEST = "manifest.json";
    public static final long MAX_ENTRY_SIZE = 32L * 1024 * 1024;
    public static final long MAX_MANIFEST_SIZE = 4L * 1024 * 1024;
    private static final String SOURCE_BACKUP = "backup_import";
    // The first read starts Python, which can take a while
    private static final long METADATA_TIMEOUT_SECONDS = 60;

    private MZGramPluginsBackup() {
    }

    /**
     * Checks if a setting key may contain private tokens, keys, passwords or credentials.
     * Such keys are strictly excluded from backups.
     */
    public static boolean isSecretKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("token") || lower.contains("secret") || lower.contains("password")
                || lower.contains("api_key") || lower.contains("apikey") || lower.contains("auth")
                || lower.contains("credential") || lower.contains("private_key") || lower.contains("bearer");
    }

    /**
     * Sanitizes plugin settings JSON by filtering out any secret or credential keys.
     */
    public static String sanitizeSettings(String json) {
        if (json == null || json.isEmpty()) {
            return "{}";
        }
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject clean = new JSONObject();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (!isSecretKey(k)) {
                    clean.put(k, obj.opt(k));
                }
            }
            return clean.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final String version;
        public final String file;
        public final boolean enabled;
        public final boolean fromCatalog;
        public final String metaJson;
        public final List<String> grants;
        public final String settings;

        public Entry(String id, String name, String version, String file, boolean enabled, boolean fromCatalog, String metaJson, List<String> grants, String settings) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.file = file;
            this.enabled = enabled;
            this.fromCatalog = fromCatalog;
            this.metaJson = metaJson;
            this.grants = grants != null ? Collections.unmodifiableList(new ArrayList<>(grants)) : Collections.emptyList();
            this.settings = settings;
        }
    }

    public interface ImportCallback {
        void onDone(int imported, int total);
    }

    /**
     * Exports all installed plugins, their metadata, granted permissions, and non-sensitive
     * settings into a ZIP archive (.plugins.zip).
     */
    public static File export() {
        List<MZGramPlugins.Plugin> list = MZGramPlugins.list();
        if (list == null || list.isEmpty()) {
            return null;
        }
        File cacheDir = AndroidUtilities.getCacheDir();
        if (cacheDir == null) {
            return null;
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
        File out = new File(cacheDir, "mzgram-plugins-" + stamp + EXTENSION);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            JSONArray array = new JSONArray();
            for (MZGramPlugins.Plugin plugin : list) {
                if (plugin == null || plugin.meta == null || plugin.file == null || !plugin.file.isFile()) {
                    continue;
                }
                String entryName = "plugins/" + plugin.meta.id + ".plugin";
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream in = new FileInputStream(plugin.file)) {
                    copy(in, zip, MAX_ENTRY_SIZE);
                }
                zip.closeEntry();

                JSONObject item = new JSONObject();
                item.put("id", plugin.meta.id);
                item.put("name", plugin.meta.name);
                item.put("version", plugin.meta.version);
                item.put("file", entryName);
                item.put("enabled", plugin.enabled);
                item.put("meta_json", plugin.meta.json);
                boolean fromCatalog = "MZGram".equals(plugin.meta.author);
                item.put("from_catalog", fromCatalog);

                List<String> granted = MZGramPluginGrants.getStored(plugin.meta.id);
                if (granted != null && !granted.isEmpty()) {
                    JSONArray gArray = new JSONArray();
                    for (String g : granted) {
                        gArray.put(g);
                    }
                    item.put("grants", gArray);
                }

                String rawSettings = MZGramPluginSettingsStore.export(plugin.meta.id);
                item.put("settings", new JSONObject(sanitizeSettings(rawSettings)));
                array.put(item);
            }
            if (array.length() == 0) {
                out.delete();
                return null;
            }
            JSONObject manifest = new JSONObject();
            manifest.put("format", FORMAT);
            manifest.put("version", VERSION);
            manifest.put("plugins", array);

            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            FileLog.e("MZGramPluginsBackup.export failed", e);
            out.delete();
            return null;
        }
        return out;
    }

    /**
     * Reads a ZIP backup archive and extracts valid plugin entries.
     * Corrupted entries are skipped without aborting the archive read.
     */
    public static List<Entry> read(File archive) {
        if (archive == null || !archive.isFile()) {
            return null;
        }
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                copy(in, buffer, MAX_MANIFEST_SIZE);
            }
            JSONObject manifest = new JSONObject(buffer.toString("UTF-8"));
            if (!FORMAT.equals(manifest.optString("format"))) {
                return null;
            }
            JSONArray list = manifest.optJSONArray("plugins");
            if (list == null) {
                return null;
            }
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String id = item.optString("id");
                    String file = item.optString("file");
                    if (TextUtils.isEmpty(id) || TextUtils.isEmpty(file) || !MZGramPluginSettingsStore.isValidId(id)) {
                        continue;
                    }
                    if (zip.getEntry(file) == null) {
                        continue;
                    }
                    String name = item.optString("name", id);
                    String version = item.optString("version", "1.0");
                    boolean enabled = item.optBoolean("enabled", false);
                    boolean fromCatalog = item.optBoolean("from_catalog", false);
                    String metaJson = item.optString("meta_json", null);

                    List<String> grants = new ArrayList<>();
                    JSONArray gArray = item.optJSONArray("grants");
                    if (gArray != null) {
                        for (int g = 0; g < gArray.length(); g++) {
                            String gr = gArray.optString(g);
                            if (!TextUtils.isEmpty(gr)) {
                                grants.add(gr);
                            }
                        }
                    }

                    JSONObject settingsObj = item.optJSONObject("settings");
                    String cleanSettings = settingsObj != null ? sanitizeSettings(settingsObj.toString()) : "{}";

                    entries.add(new Entry(id, name, version, file, enabled, fromCatalog, metaJson, grants, cleanSettings));
                } catch (Exception e) {
                    // Corrupted individual entry does not abort reading other plugins
                    FileLog.e("MZGramPluginsBackup.read entry failed", e);
                }
            }
            return entries;
        } catch (Exception e) {
            FileLog.e("MZGramPluginsBackup.read failed", e);
            return null;
        }
    }

    /**
     * Imports all selected plugin entries from the archive.
     */
    public static void importAll(File archive, List<Entry> entries, ImportCallback callback) {
        if (entries == null || entries.isEmpty() || archive == null || !archive.isFile()) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onDone(0, entries != null ? entries.size() : 0));
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            File importDir = new File(AndroidUtilities.getCacheDir(), "mzgram_plugins_import");
            importDir.mkdirs();
            int imported = 0;
            int total = entries.size();
            try (ZipFile zip = new ZipFile(archive)) {
                for (Entry entry : entries) {
                    try {
                        ZipEntry zipEntry = zip.getEntry(entry.file);
                        if (zipEntry == null) {
                            continue;
                        }
                        File tempFile = new File(importDir, "import_" + entry.id + ".plugin");
                        try (InputStream in = zip.getInputStream(zipEntry); OutputStream out = new FileOutputStream(tempFile)) {
                            copy(in, out, MAX_ENTRY_SIZE);
                        }

                        JSONObject settings = null;
                        if (!TextUtils.isEmpty(entry.settings)) {
                            try {
                                settings = new JSONObject(entry.settings);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        if (restoreOne(tempFile, entry.id, entry.fromCatalog, settings)) {
                            imported++;
                        }
                    } catch (Exception e) {
                        // Corrupted individual plugin does not break import of remaining plugins
                        FileLog.e("MZGramPluginsBackup: error importing plugin " + entry.id, e);
                    }
                }
            } catch (Exception e) {
                FileLog.e("MZGramPluginsBackup.importAll failed", e);
            }
            final int finalImported = imported;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onDone(finalImported, total));
            }
        });
    }

    /**
     * Exports plugins as a JSON array for inclusion in MZGramSettingsBackup.
     */
    public static JSONArray exportPluginsJson() {
        List<MZGramPlugins.Plugin> list = MZGramPlugins.list();
        if (list == null || list.isEmpty()) {
            return null;
        }
        JSONArray array = new JSONArray();
        for (MZGramPlugins.Plugin plugin : list) {
            if (plugin == null || plugin.meta == null || plugin.file == null || !plugin.file.isFile()) {
                continue;
            }
            try {
                JSONObject item = new JSONObject();
                item.put("id", plugin.meta.id);
                item.put("name", plugin.meta.name);
                item.put("version", plugin.meta.version);
                item.put("enabled", plugin.enabled);
                item.put("meta_json", plugin.meta.json);
                boolean fromCatalog = "MZGram".equals(plugin.meta.author);
                item.put("from_catalog", fromCatalog);

                List<String> granted = MZGramPluginGrants.getStored(plugin.meta.id);
                if (granted != null && !granted.isEmpty()) {
                    JSONArray gArray = new JSONArray();
                    for (String g : granted) {
                        gArray.put(g);
                    }
                    item.put("grants", gArray);
                }

                String rawSettings = MZGramPluginSettingsStore.export(plugin.meta.id);
                item.put("settings", new JSONObject(sanitizeSettings(rawSettings)));

                byte[] bytes = readFileBytes(plugin.file);
                if (bytes != null) {
                    item.put("content", Base64.encodeToString(bytes, Base64.NO_WRAP));
                }
                array.put(item);
            } catch (Exception e) {
                FileLog.e("MZGramPluginsBackup.exportPluginsJson", e);
            }
        }
        return array;
    }

    /**
     * Counts the number of plugins defined in a JSON backup text.
     * Returns 0 if JSON is empty, malformed, or has no plugins.
     */
    public static int countPluginsInJson(String fullJson) {
        if (TextUtils.isEmpty(fullJson)) {
            return 0;
        }
        try {
            JSONObject root = new JSONObject(fullJson);
            JSONArray array = root.optJSONArray("plugins");
            return array != null ? array.length() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Restores plugins from a JSON backup text (e.g. from MZGramSettingsBackup).
     * Any error or corruption in the plugins section is caught and logged,
     * so it never disrupts the settings restore process.
     */
    public static int restorePluginsFromJson(String fullJson) {
        if (!MZGramConfig.pluginsBackup || TextUtils.isEmpty(fullJson)) {
            return 0;
        }
        int restored = 0;
        try {
            JSONObject root = new JSONObject(fullJson);
            JSONArray array = root.optJSONArray("plugins");
            if (array == null || array.length() == 0) {
                return 0;
            }
            File cacheDir = AndroidUtilities.getCacheDir();
            File tempDir = new File(cacheDir, "mzgram_settings_plugins_import");
            tempDir.mkdirs();

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String id = item.optString("id");
                    if (TextUtils.isEmpty(id) || !MZGramPluginSettingsStore.isValidId(id)) {
                        continue;
                    }
                    String base64 = item.optString("content");
                    if (TextUtils.isEmpty(base64)) {
                        continue;
                    }
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    if (bytes == null || bytes.length == 0 || bytes.length > MZGramPlugins.MAX_FILE_BYTES) {
                        continue;
                    }
                    File tempFile = new File(tempDir, "restore_" + id + ".plugin");
                    try (FileOutputStream out = new FileOutputStream(tempFile)) {
                        out.write(bytes);
                    }

                    if (restoreOne(tempFile, id, item.optBoolean("from_catalog", false), item.optJSONObject("settings"))) {
                        restored++;
                    }
                } catch (Exception e) {
                    // Corrupted individual plugin item does not fail the import
                    FileLog.e("MZGramPluginsBackup.restorePluginsFromJson: single plugin error", e);
                }
            }
        } catch (Exception e) {
            // Corrupted plugins section does not throw to caller
            FileLog.e("MZGramPluginsBackup.restorePluginsFromJson: corrupted plugins section", e);
        }
        return restored;
    }

    /**
     * Puts back one plugin file of a backup the way a picked file is read:
     * the metadata come from its code, not from the backup, and it comes back
     * switched off with no permission, whatever the backup says, so a changed
     * backup can neither start code nor give it permissions. An installed
     * plugin of the same id is switched off first. Runs on the global queue,
     * the queue of install(), so it installs right here instead of waiting.
     * True when the file is in place.
     */
    private static boolean restoreOne(File tempFile, String id, boolean fromCatalog, JSONObject settings) {
        MZGramPluginFormat.Meta meta = readMetadataNow(tempFile);
        if (meta == null || !id.equals(meta.id)) {
            FileLog.e("MZGramPluginsBackup: the code of " + id + " does not read as that plugin, skipped");
            MZGramPlugins.discard(tempFile);
            return false;
        }
        if (fromCatalog) {
            meta = meta.forCatalog();
        }
        MZGramPlugins.setEnabled(id, false, SOURCE_BACKUP);
        if (!MZGramPlugins.installNow(tempFile, meta, SOURCE_BACKUP, new ArrayList<>())) {
            return false;
        }
        // Settings that are not secrets
        if (settings != null) {
            Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!isSecretKey(key)) {
                    MZGramPluginSettingsStore.set(id, key, settings.optString(key));
                }
            }
        }
        return true;
    }

    /** The metadata of a plugin file as its code gives them; null on a read error or off the UI thread wait. */
    private static MZGramPluginFormat.Meta readMetadataNow(File file) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return null;  // the result comes on this thread: it could never be waited for
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final MZGramPluginFormat.Result[] result = new MZGramPluginFormat.Result[1];
        MZGramPluginFormat.readMetadata(file, r -> {
            result[0] = r;
            latch.countDown();
        });
        try {
            if (!latch.await(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            return null;
        }
        return result[0] != null && result[0].isOk() ? result[0].meta : null;
    }

    private static byte[] readFileBytes(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("entry too large");
            }
            out.write(buffer, 0, read);
        }
    }
}
