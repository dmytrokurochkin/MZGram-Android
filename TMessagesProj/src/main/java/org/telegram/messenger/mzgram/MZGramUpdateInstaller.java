/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Installs a newer MZGram release from the update banner (MZGramUpdateBanner):
 *
 * 1. The release is read again by its tag from the MZGram-Android repository
 *    on GitHub; it must be a published stable release newer than this build.
 * 2. Its APK is picked by name (MZGram-Android-X.Y.Z-<abi>.apk for this
 *    device's ABI, else the universal MZGram-Android-X.Y.Z.apk) together with
 *    its SHA256SUMS. Every address must be https on github.com or its download
 *    hosts, and a file must come from this one repository's releases.
 * 3. The APK is downloaded into the app's cache, resuming a broken download.
 * 4. Its SHA-256 must match SHA256SUMS and, when GitHub gives one, the digest
 *    of the asset; otherwise the file is deleted and nothing is installed.
 *    Its package must be this app's own (org.mzgram.messenger), or it is
 *    deleted too.
 * 5. PackageInstaller installs it. Android asks the user to confirm (on
 *    Android 12 and newer it may skip that for an app updating itself) and
 *    checks the signature, which is the MZGram key of the installed app.
 *
 * Started only by Update on the banner, also with the switch of the
 * automatic check (MZGramConfig.checkUpdates) off.
 * Nothing here talks to Telegram, and the app's identity towards Telegram's
 * servers does not change.
 */

package org.telegram.messenger.mzgram;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MZGramUpdateInstaller {

    public static final String RELEASE_BY_TAG_URL = "https://api.github.com/repos/" + MZGramUpdateChecker.REPOSITORY + "/releases/tags/";
    public static final String DOWNLOAD_PREFIX = "https://github.com/" + MZGramUpdateChecker.REPOSITORY + "/releases/download/";
    // github.com and the hosts its release downloads redirect to
    public static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "github.com",
            "api.github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com"
    ));
    public static final String SUMS_NAME = "SHA256SUMS";
    private static final long MAX_APK_SIZE = 512L * 1024 * 1024;
    private static final String INSTALL_ACTION = "org.telegram.messenger.mzgram.UPDATE_INSTALL_STATUS";

    public static final class Asset {
        public final String name;
        public final String url;
        public final String digest;
        public final long size;

        public Asset(String name, String url, String digest, long size) {
            this.name = name;
            this.url = url;
            this.digest = digest;
            this.size = size;
        }
    }

    public enum Failure {
        NETWORK,
        CHECKSUM,
        NOT_FOUND,
        INSTALL
    }

    public interface Listener {
        void onProgress(int percent);

        void onVerifying();

        void onInstalling();

        void onFailed(Failure failure, String detail);

        // The release has no APK this app can take: the banner opens its page
        void onFallback(String url);
    }

    public interface Progress {
        void onBytes(long done, long total);
    }

    public interface Downloader {
        // Downloads url into target, appending from offset (a resumed download)
        void download(String url, File target, long offset, long maxSize, Progress progress) throws IOException;
    }

    public interface Installer {
        void install(Context context, File apk, Listener listener) throws Exception;
    }

    public interface PackageReader {
        // The package of an APK file; null when it is not one
        String packageOf(Context context, File apk);
    }

    // Swapped by the tests: they never touch the network or install anything
    public static Downloader downloader = MZGramUpdateInstaller::httpDownload;
    public static Installer installer = MZGramUpdateInstaller::packageInstall;
    public static PackageReader packageReader = MZGramUpdateInstaller::apkPackage;
    public static String[] abisOverride;

    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    // https on github.com or its download hosts, nothing else
    public static boolean isAllowedUrl(String address) {
        try {
            URL url = new URL(address);
            return "https".equals(url.getProtocol())
                    && url.getHost() != null
                    && ALLOWED_HOSTS.contains(url.getHost().toLowerCase(Locale.US))
                    && url.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    // A file of a release of this repository, nothing else
    public static boolean isReleaseFile(String address, String tag) {
        return address != null
                && address.startsWith(DOWNLOAD_PREFIX + tag + "/")
                && isAllowedUrl(address);
    }

    public static List<Asset> parseAssets(JSONObject release) {
        List<Asset> result = new ArrayList<>();
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return result;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String digest = asset.isNull("digest") ? null : asset.optString("digest", null);
            result.add(new Asset(asset.optString("name", ""), asset.optString("browser_download_url", ""), digest, asset.optLong("size", -1)));
        }
        return result;
    }

    private static String[] abis() {
        return abisOverride != null ? abisOverride : Build.SUPPORTED_ABIS;
    }

    // The APK for this device: MZGram-Android-X.Y.Z-<abi>.apk for one of its
    // ABIs (in the device's order), else the universal MZGram-Android-X.Y.Z.apk.
    public static Asset chooseApk(List<Asset> assets, String version) {
        Map<String, Asset> byName = new HashMap<>();
        for (Asset asset : assets) {
            byName.put(asset.name, asset);
        }
        for (String abi : abis()) {
            Asset asset = byName.get("MZGram-Android-" + version + "-" + abi + ".apk");
            if (asset != null) {
                return asset;
            }
        }
        return byName.get("MZGram-Android-" + version + ".apk");
    }

    public static Asset find(List<Asset> assets, String name) {
        for (Asset asset : assets) {
            if (asset.name.equals(name)) {
                return asset;
            }
        }
        return null;
    }

    // "<sha256>  <name>" lines, as sha256sum writes them
    public static Map<String, String> parseSums(String text) {
        Map<String, String> result = new HashMap<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\n")) {
            line = line.trim();
            int space = line.indexOf(' ');
            if (space != 64) {
                continue;
            }
            String hash = line.substring(0, 64).toLowerCase(Locale.US);
            String name = line.substring(space).trim();
            if (name.startsWith("*")) {
                name = name.substring(1);
            }
            if (hash.matches("[0-9a-f]{64}") && !name.isEmpty()) {
                result.put(name, hash);
            }
        }
        return result;
    }

    public static String sha256(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return Utilities.bytesToHex(digest.digest()).toLowerCase(Locale.US);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    // The file matches SHA256SUMS and, when GitHub gives one, the asset digest
    public static boolean matches(File file, String expected, String digest) throws IOException {
        if (expected == null) {
            return false;
        }
        String actual = sha256(file);
        if (!actual.equals(expected.toLowerCase(Locale.US))) {
            return false;
        }
        if (digest != null && digest.startsWith("sha256:")) {
            return actual.equals(digest.substring(7).toLowerCase(Locale.US));
        }
        return true;
    }

    public static File directory() {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "mzgram_update");
        dir.mkdirs();
        return dir;
    }

    // Whether Android lets this app install packages; false: the setting must be opened
    public static boolean canInstall() {
        if (Build.VERSION.SDK_INT < 26) {
            return true;
        }
        return ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent permissionIntent() {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
    }

    private static void ui(Runnable runnable) {
        AndroidUtilities.runOnUIThread(runnable);
    }

    // Downloads, checks and installs the release; the listener runs on the UI
    // thread. false: nothing started (one is running). Only the user starts
    // it, with Update on the bar, so the switch of the automatic check does
    // not stop it: "Check for updates" shows the bar with the switch off too,
    // and its Update did nothing then.
    public static boolean start(MZGramUpdateChecker.Release release, Listener listener) {
        if (release == null || running) {
            return false;
        }
        running = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                removeOld();
                run(release, listener);
            } finally {
                running = false;
            }
        });
        return true;
    }

    private static void run(MZGramUpdateChecker.Release release, Listener listener) {
        List<Asset> assets;
        try {
            MZGramUpdateChecker.Response response = MZGramUpdateChecker.fetcher.fetch(RELEASE_BY_TAG_URL + release.tag, null);
            if (response.status != 200) {
                throw new IOException("release answer " + response.status);
            }
            JSONObject object = new JSONObject(response.body);
            MZGramUpdateChecker.Release again = MZGramUpdateChecker.parse(response.body);
            if (again == null || !again.tag.equals(release.tag) || again.code <= MZGramUpdateChecker.installedCode()) {
                ui(() -> listener.onFailed(Failure.NOT_FOUND, release.tag));
                return;
            }
            assets = parseAssets(object);
        } catch (Exception e) {
            FileLog.d("MZGram update: " + e.getMessage());
            ui(() -> listener.onFailed(Failure.NETWORK, e.getMessage()));
            return;
        }
        Asset apk = chooseApk(assets, release.version());
        Asset sums = find(assets, SUMS_NAME);
        if (apk == null || sums == null || !isReleaseFile(apk.url, release.tag) || !isReleaseFile(sums.url, release.tag)) {
            ui(() -> listener.onFallback(release.url));
            return;
        }
        File dir = directory();
        File sumsFile = new File(dir, release.tag + "-" + SUMS_NAME);
        File part = new File(dir, apk.name + ".part");
        File ready = new File(dir, apk.name);
        try {
            sumsFile.delete();
            downloader.download(sums.url, sumsFile, 0, 1024 * 1024, (done, total) -> {});
            String expected = parseSums(readText(sumsFile)).get(apk.name);
            sumsFile.delete();
            if (expected == null) {
                ui(() -> listener.onFallback(release.url));
                return;
            }
            if (!ready.exists()) {
                long offset = part.exists() ? part.length() : 0;
                downloader.download(apk.url, part, offset, MAX_APK_SIZE, (done, total) -> {
                    int percent = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
                    ui(() -> listener.onProgress(percent));
                });
                ui(listener::onVerifying);
                if (!matches(part, expected, apk.digest)) {
                    part.delete();
                    ui(() -> listener.onFailed(Failure.CHECKSUM, apk.name));
                    return;
                }
                if (!part.renameTo(ready)) {
                    throw new IOException("cannot keep the download");
                }
            } else {
                ui(listener::onVerifying);
                if (!matches(ready, expected, apk.digest)) {
                    ready.delete();
                    ui(() -> listener.onFailed(Failure.CHECKSUM, apk.name));
                    return;
                }
            }
        } catch (Exception e) {
            FileLog.d("MZGram update download: " + e.getMessage());
            // A broken download stays as .part and continues next time
            ui(() -> listener.onFailed(Failure.NETWORK, e.getMessage()));
            return;
        }
        // Only an APK of this app's own package is this update: one of
        // another package (Telegram's, or MZGram's old org.telegram.messenger.web)
        // would be a separate app
        Context context = ApplicationLoader.applicationContext;
        String apkPackage = packageReader.packageOf(context, ready);
        if (!context.getPackageName().equals(apkPackage)) {
            FileLog.d("MZGram update: the APK is of " + apkPackage + ", not " + context.getPackageName());
            ready.delete();
            ui(() -> listener.onFailed(Failure.INSTALL, "package " + apkPackage));
            return;
        }
        ui(listener::onInstalling);
        try {
            installer.install(context, ready, listener);
        } catch (Exception e) {
            FileLog.e(e);
            ready.delete();
            ui(() -> listener.onFailed(Failure.INSTALL, e.getMessage()));
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 1024 * 1024)];
            int total = 0;
            int read;
            while (total < data.length && (read = in.read(data, total, data.length - total)) > 0) {
                total += read;
            }
            return new String(data, 0, total, StandardCharsets.UTF_8);
        }
    }

    private static final java.util.regex.Pattern FILE_VERSION = java.util.regex.Pattern.compile("^MZGram-Android-(\\d+\\.\\d+\\.\\d+)[-.].*");

    // Removes what is not a download of a version newer than the installed
    // one: an installed update leaves its APK behind until then.
    public static void removeOld() {
        File[] files = directory().listFiles();
        if (files == null) {
            return;
        }
        long installed = MZGramUpdateChecker.installedCode();
        for (File file : files) {
            java.util.regex.Matcher m = FILE_VERSION.matcher(file.getName());
            if (!m.matches() || MZGramUpdateChecker.stableCode("v" + m.group(1)) <= installed) {
                file.delete();
            }
        }
    }

    // Removes every download
    public static void cleanUp() {
        File[] files = directory().listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    // HTTP with every redirect checked against the allowed hosts; a resumed
    // download asks for the rest with Range.
    private static void httpDownload(String address, File target, long offset, long maxSize, Progress progress) throws IOException {
        String current = address;
        for (int redirect = 0; redirect < 5; redirect++) {
            if (!isAllowedUrl(current)) {
                throw new IOException("not an allowed address");
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "MZGram-Android");
            connection.setRequestProperty("Accept", "application/octet-stream");
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
            }
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("redirect without an address");
                    }
                    current = new URL(new URL(current), location).toString();
                    continue;
                }
                boolean append = offset > 0 && status == 206;
                if (status != 200 && !append) {
                    throw new IOException("download answer " + status);
                }
                long length = contentLength(connection);
                long total = length >= 0 ? (append ? offset + length : length) : -1;
                if (total > maxSize) {
                    throw new IOException("file too large");
                }
                long done = append ? offset : 0;
                try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(target, append)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        done += read;
                        if (done > maxSize) {
                            throw new IOException("file too large");
                        }
                        progress.onBytes(done, total);
                    }
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    private static long contentLength(HttpURLConnection connection) {
        try {
            String value = connection.getHeaderField("Content-Length");
            return value == null ? -1 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String apkPackage(Context context, File apk) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return info == null ? null : info.packageName;
        } catch (Exception e) {
            return null;
        }
    }

    private static void packageInstall(Context context, File apk, Listener listener) throws Exception {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            // An app updating itself may be installed without a prompt where Android allows it
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int sessionId = packageInstaller.createSession(params);
        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("mzgram.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            }
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (confirm != null) {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(confirm);
                        }
                        return;
                    }
                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception ignore) {
                    }
                    if (status != PackageInstaller.STATUS_SUCCESS) {
                        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                        apk.delete();
                        listener.onFailed(Failure.INSTALL, message != null ? message : String.valueOf(status));
                    }
                    // On success Android replaces and restarts the app
                }
            };
            IntentFilter filter = new IntentFilter(INSTALL_ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            Intent intent = new Intent(INSTALL_ACTION).setPackage(context.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, intent, flags);
            session.commit(pending.getIntentSender());
        }
    }
}
