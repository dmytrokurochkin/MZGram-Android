/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Plugins catalog loader and downloader.
 * Fetches index.json from user-configured repository URL,
 * parses plugin metadata, downloads .plugin files with mandatory
 * SHA-256 checksum verification before passing them to MZGramPluginInstallSheet.
 */

package org.telegram.messenger.mzgram.plugins;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MZGramPluginsCatalog {

    private static final int TIMEOUT_MS = 15000;
    private static final String PICKED_DIR = "mzgram_plugin_picked";

    public static final String AUTHOR_MZGRAM = "MZGram";

    public static final class CatalogPlugin {
        public static final String AUTHOR_MZGRAM = MZGramPluginsCatalog.AUTHOR_MZGRAM;

        public final String id;
        public final String name;
        public final String version;
        public final String description;
        public final String author;
        public final String file;
        public final String url;
        public final String sha256;
        public final long size;
        public final String minAppVersion;
        public final List<String> requirements;
        public final List<String> permissions;

        public CatalogPlugin(JSONObject json) {
            this.id = json.optString("id", "");
            this.name = json.optString("name", id);
            this.version = json.optString("version", "1.0");
            this.description = json.optString("description", "");
            // In MZGram plugins catalog, author is always MZGram; any external author/avatar is ignored
            this.author = AUTHOR_MZGRAM;
            this.file = json.optString("file", "");
            this.url = json.optString("url", "");
            this.sha256 = json.optString("sha256", "");
            this.size = json.optLong("size", 0L);
            this.minAppVersion = json.optString("min_app_version", "");

            List<String> reqs = new ArrayList<>();
            JSONArray reqArr = json.optJSONArray("requirements");
            if (reqArr != null) {
                for (int i = 0; i < reqArr.length(); i++) {
                    String r = reqArr.optString(i, null);
                    if (!TextUtils.isEmpty(r)) {
                        reqs.add(r);
                    }
                }
            }
            this.requirements = Collections.unmodifiableList(reqs);

            List<String> perms = new ArrayList<>();
            JSONArray permArr = json.optJSONArray("permissions");
            if (permArr != null) {
                for (int i = 0; i < permArr.length(); i++) {
                    String p = permArr.optString(i, null);
                    if (!TextUtils.isEmpty(p)) {
                        perms.add(p);
                    }
                }
            }
            this.permissions = Collections.unmodifiableList(perms);
        }
    }

    public static final class CatalogData {
        public final String name;
        public final String description;
        public final List<CatalogPlugin> plugins;

        public CatalogData(String name, String description, List<CatalogPlugin> plugins) {
            this.name = name;
            this.description = description;
            this.plugins = plugins != null ? plugins : Collections.emptyList();
        }
    }

    private MZGramPluginsCatalog() {
    }

    public static void fetchCatalog(String catalogUrl, Utilities.Callback<CatalogData> onResult, Utilities.Callback<String> onError) {
        if (TextUtils.isEmpty(catalogUrl)) {
            deliverError(onError, "empty url");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                String targetUrl = catalogUrl.trim();
                if (!targetUrl.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    if (!targetUrl.endsWith("/")) {
                        targetUrl += "/";
                    }
                    targetUrl += "index.json";
                }

                URL url = new URL(targetUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "MZGram-Android/1.0");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    deliverError(onError, "HTTP " + code);
                    return;
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        bos.write(buf, 0, len);
                    }
                }

                String jsonText = bos.toString("UTF-8");
                JSONObject root = new JSONObject(jsonText);
                String catName = root.optString("name", "MZGram Plugins");
                String catDesc = root.optString("description", "");
                List<CatalogPlugin> pluginList = new ArrayList<>();

                JSONArray arr = root.optJSONArray("plugins");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) {
                            pluginList.add(new CatalogPlugin(obj));
                        }
                    }
                }

                CatalogData data = new CatalogData(catName, catDesc, pluginList);
                AndroidUtilities.runOnUIThread(() -> {
                    if (onResult != null) {
                        onResult.run(data);
                    }
                });
            } catch (Exception e) {
                FileLog.e("MZGramPluginsCatalog.fetchCatalog", e);
                deliverError(onError, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public static void downloadPlugin(CatalogPlugin item, String catalogBaseUrl, Utilities.Callback<File> onDone, Utilities.Callback<String> onError) {
        if (item == null) {
            deliverError(onError, "null item");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            File tempFile = null;
            try {
                String downloadUrl = item.url;
                if (TextUtils.isEmpty(downloadUrl)) {
                    String base = catalogBaseUrl != null ? catalogBaseUrl.trim() : "";
                    if (base.toLowerCase(Locale.ROOT).endsWith("index.json")) {
                        base = base.substring(0, base.length() - "index.json".length());
                    }
                    if (!base.endsWith("/")) {
                        base += "/";
                    }
                    String fileRel = item.file != null ? item.file.trim() : "";
                    if (fileRel.startsWith("/")) {
                        fileRel = fileRel.substring(1);
                    }
                    downloadUrl = base + fileRel;
                }
                if (TextUtils.isEmpty(downloadUrl) || downloadUrl.endsWith("/")) {
                    deliverError(onError, "empty_file_path");
                    return;
                }

                File folder = new File(ApplicationLoader.getFilesDirFixed(), PICKED_DIR);
                if (!folder.isDirectory() && !folder.mkdirs()) {
                    deliverError(onError, "cannot create temp folder");
                    return;
                }

                tempFile = new File(folder, "picked_" + System.nanoTime() + MZGramPluginFormat.EXTENSION);

                URL url = new URL(downloadUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "MZGram-Android/1.0");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    deliverError(onError, "HTTP " + code);
                    return;
                }

                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                        md.update(buf, 0, len);
                    }
                }

                byte[] digest = md.digest();
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    String h = Integer.toHexString(0xff & b);
                    if (h.length() == 1) {
                        hex.append('0');
                    }
                    hex.append(h);
                }
                String computedSha = hex.toString();

                String expected = item.sha256 != null ? item.sha256.trim().toLowerCase(Locale.ROOT) : "";
                if (TextUtils.isEmpty(expected) || !expected.equals(computedSha.toLowerCase(Locale.ROOT))) {
                    tempFile.delete();
                    deliverError(onError, "sha256_mismatch");
                    return;
                }

                final File resultFile = tempFile;
                AndroidUtilities.runOnUIThread(() -> {
                    if (onDone != null) {
                        onDone.run(resultFile);
                    }
                });
            } catch (Exception e) {
                FileLog.e("MZGramPluginsCatalog.downloadPlugin", e);
                if (tempFile != null) {
                    tempFile.delete();
                }
                deliverError(onError, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void deliverError(Utilities.Callback<String> onError, String message) {
        AndroidUtilities.runOnUIThread(() -> {
            if (onError != null) {
                onError.run(message != null ? message : "unknown error");
            }
        });
    }
}
