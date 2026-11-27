package org.mzgram.updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.mzgram.core.helpers.remote.UpdateHelper;
import org.mzgram.ext.ExtConfig;

public final class GitHubUpdater {

    private static final String REPO = "dmytrokurochkin/MZGram-Android";
    public static final String RELEASES_URL = "https://github.com/" + REPO + "/releases";
    private static final String API = "https://api.github.com/repos/" + REPO;
    private static final long AUTO_INTERVAL = TimeUnit.HOURS.toMillis(6);
    private static final String PREFS = "mzgram_updater";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_SKIPPED = "skipped_tag";
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    // An MZGram release tag: v1.0.11 or v1.0.11-beta.2
    private static final Pattern TAG = Pattern.compile("^v(0|[1-9]\\d*)\\.(0|[1-9]\\d?)\\.(0|[1-9]\\d?)(-beta\\.([1-9]\\d?))?$");
    private static final String SUMS_NAME = "SHA256SUMS";
    private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";

    private static volatile OkHttpClient client;
    private static volatile boolean checking;
    private static volatile Call download;

    private GitHubUpdater() {
    }

    public static final class Release {
        public String tag;
        public String name;
        public String body;
        public String apkUrl;
        public String apkName;
        public long apkSize;
        // GitHub's "sha256:<hex>" of the APK, and the release's SHA256SUMS file
        public String apkDigest;
        public String sumsUrl;
        public long published;

        public String displayVersion() {
            String value = TextUtils.isEmpty(tag) ? name : tag;
            return value != null && value.startsWith("v") ? value.substring(1) : value;
        }
    }

    public interface CheckCallback {
        void onChecked(Release release, Boolean newer, boolean failed);
    }

    public interface DownloadListener {
        void onProgress(long done, long total);

        // Downloaded; the SHA-256 is being checked
        default void onVerifying() {
        }

        void onFinished(boolean success, boolean canceled);
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (GitHubUpdater.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void check(boolean force) {
        int channel = ExtConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (!force) {
            if (channel == UpdateHelper.UPDATE_OFF) {
                return;
            }
            if (Math.abs(System.currentTimeMillis() - lastCheck()) < AUTO_INTERVAL) {
                return;
            }
        }
        if (checking) {
            return;
        }
        checking = true;
        fetch((found, isNewer, failed) -> {
            checking = false;
            boolean offer = found != null && (Boolean.TRUE.equals(isNewer) || force && isNewer == null);
            if (offer && (force || !TextUtils.equals(found.tag, prefs().getString(KEY_SKIPPED, null)))) {
                show(found);
            } else if (force) {
                bulletin(failed
                        ? LocaleController.getString(R.string.MZUpdateCheckFailed)
                        : LocaleController.getString(R.string.MZUpdateLatest), failed);
            }
        });
    }

    public static void fetch(CheckCallback callback) {
        final boolean prerelease = ExtConfig.INSTANCE.getAutoUpdateChannel().Int() == UpdateHelper.UPDATE_CHANNEL_BETA;
        new Thread(() -> {
            Release release = null;
            Boolean newer = null;
            boolean failed = false;
            try {
                release = latest(prerelease);
                if (release != null) {
                    newer = isNewer(release);
                }
                prefs().edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                failed = true;
                FileLog.e("GitHubUpdater: check failed", e);
            }
            final Release found = release;
            final Boolean isNewer = newer;
            final boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> callback.onChecked(found, isNewer, error));
        }, "gh-updater").start();
    }

    public static long lastCheck() {
        return prefs().getLong(KEY_LAST_CHECK, 0);
    }

    public static boolean isDownloading() {
        return download != null;
    }

    public static void cancelDownload() {
        Call call = download;
        if (call != null) {
            call.cancel();
        }
    }

    private static void bulletin(String text, boolean error) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        if (error) {
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        } else {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, text).show();
        }
    }

    private static JSONObject getJson(String url) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MZGram")
                .build();
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            String text = body.string();
            return text.trim().startsWith("[") ? new JSONObject().put("items", new JSONArray(text)) : new JSONObject(text);
        }
    }

    private static Release latest(boolean prerelease) throws Exception {
        JSONObject wrapper = getJson(API + "/releases?per_page=10");
        JSONArray releases = wrapper == null ? null : wrapper.optJSONArray("items");
        if (releases == null) {
            return null;
        }
        for (int i = 0; i < releases.length(); i++) {
            JSONObject item = releases.optJSONObject(i);
            if (item == null || item.optBoolean("draft") || item.optBoolean("prerelease") && !prerelease) {
                continue;
            }
            JSONObject asset = pickAsset(item.optJSONArray("assets"));
            if (asset == null) {
                continue;
            }
            Release release = new Release();
            release.tag = item.optString("tag_name");
            release.name = item.optString("name", release.tag);
            release.body = item.optString("body", "");
            release.apkUrl = asset.optString("browser_download_url");
            release.apkName = asset.optString("name");
            release.apkSize = asset.optLong("size");
            release.apkDigest = asset.isNull("digest") ? null : asset.optString("digest", null);
            JSONObject sums = findAsset(item.optJSONArray("assets"), SUMS_NAME);
            release.sumsUrl = sums == null ? null : sums.optString("browser_download_url", null);
            release.published = parseDate(item.optString("published_at"));
            return release;
        }
        return null;
    }

    private static JSONObject pickAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        JSONObject universal = null;
        JSONObject any = null;
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            String name = asset == null ? "" : asset.optString("name").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".apk")) {
                continue;
            }
            if (!abi.isEmpty() && name.contains(abi.toLowerCase(Locale.ROOT))) {
                return asset;
            }
            if (name.contains("universal") && universal == null) {
                universal = asset;
            }
            if (any == null && !name.contains("armeabi") && !name.contains("x86")) {
                any = asset;
            }
        }
        return universal != null ? universal : any;
    }

    private static int[] version(String... sources) {
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            Matcher matcher = VERSION.matcher(source);
            if (matcher.find()) {
                return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))};
            }
        }
        return null;
    }

    private static JSONObject findAsset(JSONArray assets, String name) {
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && name.equals(asset.optString("name"))) {
                return asset;
            }
        }
        return null;
    }

    // The versionCode base of a release tag, the scheme of the MZGram builds
    // (TMessagesProj/build.gradle): MAJOR * 1000000 + MINOR * 10000 + PATCH * 100,
    // plus 99 for a stable release or N for beta.N; -1 when the tag is not an MZGram one.
    static long releaseCode(String tag) {
        Matcher m = tag == null ? null : TAG.matcher(tag);
        if (m == null || !m.matches()) {
            return -1;
        }
        long code = Long.parseLong(m.group(1)) * 1000000L + Long.parseLong(m.group(2)) * 10000L + Long.parseLong(m.group(3)) * 100L;
        return code + (m.group(5) != null ? Long.parseLong(m.group(5)) : 99L);
    }

    // The same number for this build: versionCode without its last (ABI) digit
    static long installedCode() {
        try {
            Context context = ApplicationLoader.applicationContext;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode / 10;
        } catch (Exception e) {
            return -1;
        }
    }

    // Telegram's version (BuildVars.BUILD_VERSION_STRING) says nothing about MZGram's,
    // so only the version codes are compared; null sends isNewer to the commits.
    private static Integer compareVersion(Release release) {
        long remote = releaseCode(release.tag);
        long local = installedCode();
        if (remote < 0 || local < 0) {
            return null;
        }
        return Long.compare(remote, local);
    }

    private static Boolean isNewer(Release release) throws Exception {
        Integer versionOrder = compareVersion(release);
        if (versionOrder != null) {
            return versionOrder > 0;
        }
        String commit = BuildConfig.BUILD_COMMIT_ID;
        if (!TextUtils.isEmpty(commit)) {
            JSONObject compare = getJson(API + "/compare/" + commit + "..." + Uri.encode(release.tag));
            if (compare != null) {
                return compare.optInt("ahead_by", 0) > 0;
            }
        }
        if (BuildConfig.BUILD_TIMESTAMP > 0) {
            JSONObject item = getJson(API + "/releases/tags/" + Uri.encode(release.tag));
            long published = item == null ? 0 : parseDate(item.optString("published_at"));
            return published > BuildConfig.BUILD_TIMESTAMP * 1000L;
        }
        return versionOrder != null ? Boolean.FALSE : null;
    }

    private static long parseDate(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.parse(value).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static void show(Release release) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        String size = release.apkSize > 0 ? AndroidUtilities.formatFileSize(release.apkSize) : "";
        int[] remote = version(release.tag, release.name, release.apkName);
        StringBuilder subtitle = new StringBuilder(TextUtils.isEmpty(release.name) ? release.tag : release.name);
        if (remote != null) {
            subtitle.append(" · ").append(remote[0]).append('.').append(remote[1]).append('.').append(remote[2]);
        }
        if (!size.isEmpty()) {
            subtitle.append(" · ").append(size);
        }
        String notes = TextUtils.isEmpty(release.body) ? release.tag : release.body;
        String updateText = size.isEmpty() ? LocaleController.getString(R.string.MZUpdateInstall)
                : LocaleController.formatString(R.string.MZUpdateInstallSize, size);
        new UpdateSheet(activity, fragment.getResourceProvider(), LocaleController.getString(R.string.MZUpdateTitle),
                subtitle.toString(), notes, updateText, new UpdateSheet.Delegate() {
            @Override
            public void onUpdate(UpdateSheet sheet) {
                sheet.setDownloading(true);
                download(activity, release, new DownloadListener() {
                    @Override
                    public void onProgress(long done, long total) {
                        sheet.setProgress(done, total);
                    }

                    @Override
                    public void onVerifying() {
                        sheet.setVerifying(LocaleController.formatString(R.string.MZUpdateVerifying, release.displayVersion()));
                    }

                    @Override
                    public void onFinished(boolean success, boolean canceled) {
                        if (success) {
                            sheet.finishDownload();
                        } else if (!canceled) {
                            sheet.setDownloading(false);
                        }
                    }
                });
            }

            @Override
            public void onSkip() {
                prefs().edit().putString(KEY_SKIPPED, release.tag).apply();
            }

            @Override
            public void onCancelDownload() {
                cancelDownload();
            }
        }).show();
    }

    public static void download(Activity activity, Release release, DownloadListener listener) {
        if (download != null) {
            return;
        }
        File dir = new File(activity.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            bulletin(LocaleController.getString(R.string.MZUpdateDownloadFailed), true);
            listener.onFinished(false, false);
            return;
        }
        File[] old = dir.listFiles();
        if (old != null) {
            for (File file : old) {
                file.delete();
            }
        }
        File target = new File(dir, release.apkName.replaceAll("[^A-Za-z0-9._-]", "_"));
        Call call = client().newCall(new Request.Builder().url(release.apkUrl)
                .header("User-Agent", "MZGram").build());
        download = call;
        new Thread(() -> {
            boolean ok = false;
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                long total = body.contentLength() > 0 ? body.contentLength() : release.apkSize;
                try (InputStream input = body.byteStream(); OutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        output.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final long doneBytes = done;
                                AndroidUtilities.runOnUIThread(() -> listener.onProgress(doneBytes, total));
                            }
                        }
                    }
                }
                ok = true;
            } catch (Exception e) {
                if (!call.isCanceled()) {
                    FileLog.e("GitHubUpdater: download failed", e);
                }
            }
            boolean verified = false;
            if (ok && !call.isCanceled()) {
                AndroidUtilities.runOnUIThread(listener::onVerifying);
                verified = verify(activity, release, target);
            }
            final boolean success = ok && verified;
            final boolean badFile = ok && !verified && !call.isCanceled();
            AndroidUtilities.runOnUIThread(() -> {
                download = null;
                if (success) {
                    listener.onFinished(true, false);
                    install(activity, target);
                } else {
                    target.delete();
                    listener.onFinished(false, call.isCanceled());
                    if (badFile) {
                        bulletin(LocaleController.formatString(R.string.MZUpdateChecksumFailed, release.displayVersion()), true);
                    } else if (!call.isCanceled()) {
                        bulletin(LocaleController.getString(R.string.MZUpdateDownloadFailed), true);
                    }
                }
            });
        }, "gh-updater-download").start();
    }

    // Before anything is installed: the file must come from this repository's releases,
    // match the release's SHA256SUMS and GitHub's digest of the asset (each one the
    // release gives, at least one of them), and be this app's own package. Android then
    // checks that it is signed with the installed app's key.
    private static boolean verify(Context context, Release release, File file) {
        try {
            if (release.apkUrl == null || !release.apkUrl.startsWith(DOWNLOAD_PREFIX)) {
                FileLog.e("GitHubUpdater: the APK is not from " + REPO);
                return false;
            }
            String expected = null;
            if (release.sumsUrl != null && release.sumsUrl.startsWith(DOWNLOAD_PREFIX)) {
                expected = parseSums(getText(release.sumsUrl)).get(release.apkName);
            }
            String digest = release.apkDigest != null && release.apkDigest.startsWith("sha256:")
                    ? release.apkDigest.substring(7).toLowerCase(Locale.US) : null;
            if (expected == null && digest == null) {
                FileLog.e("GitHubUpdater: the release gives no SHA-256 for " + release.apkName);
                return false;
            }
            String actual = sha256(file);
            if (expected != null && !expected.equals(actual) || digest != null && !digest.equals(actual)) {
                FileLog.e("GitHubUpdater: SHA-256 mismatch for " + release.apkName);
                return false;
            }
            PackageInfo info = context.getPackageManager().getPackageArchiveInfo(file.getPath(), 0);
            if (info == null || !context.getPackageName().equals(info.packageName)) {
                FileLog.e("GitHubUpdater: the APK is not " + context.getPackageName());
                return false;
            }
            return true;
        } catch (Exception e) {
            FileLog.e("GitHubUpdater: check failed", e);
            return false;
        }
    }

    private static String getText(String url) throws Exception {
        Request request = new Request.Builder().url(url).header("User-Agent", "MZGram").build();
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            return body.string();
        }
    }

    // "<sha256>  <name>" lines, as sha256sum writes them ("*<name>" in binary mode)
    static Map<String, String> parseSums(String text) {
        Map<String, String> sums = new HashMap<>();
        for (String line : text.split("\n")) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2 && parts[0].matches("[0-9a-fA-F]{64}")) {
                String name = parts[1].startsWith("*") ? parts[1].substring(1) : parts[1];
                sums.put(name.trim(), parts[0].toLowerCase(Locale.US));
            }
        }
        return sums;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", b));
        }
        return hex.toString();
    }

    private static void install(Activity activity, File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file),
                    "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
            bulletin(LocaleController.getString(R.string.MZUpdateDownloadFailed), true);
        }
    }
}
