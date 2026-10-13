/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The MZGram update check, in place of Telegram's own (help.getAppUpdate),
 * which offers the official app, signed with another key.
 *
 * At most once in CHECK_INTERVAL_MS the latest release of the MZGram-Android
 * repository is read from GitHub (no token, the ETag of the last answer sent
 * along, so an unchanged answer costs nothing). A stable release with a higher
 * version than the installed one gives a banner with a link to its page:
 * nothing is downloaded or installed by the app. A closed banner stays closed
 * for that version. Errors are silent. Settings > MZGram > Other turns it off.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MZGramUpdateChecker {

    public static final String REPOSITORY = "dmytrokurochkin/MZGram-Android";
    public static final String LATEST_RELEASE_URL = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    public static final String RELEASES_PAGE_PREFIX = "https://github.com/" + REPOSITORY + "/releases/";
    public static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    private static final String PREFERENCES_NAME = "mzgram_update";
    // The same tags as the release workflow: vX.Y.Z stable, vX.Y.Z-beta.N beta
    private static final Pattern TAG = Pattern.compile("^v(0|[1-9]\\d*)\\.(0|[1-9]\\d?)\\.(0|[1-9]\\d?)(-beta\\.([1-9]\\d?))?$");

    public static final class Release {
        public final String tag;
        public final String url;
        public final long code;

        Release(String tag, String url, long code) {
            this.tag = tag;
            this.url = url;
            this.code = code;
        }

        public String version() {
            return tag.substring(1);
        }
    }

    public static final class Response {
        public final int status;
        public final String body;
        public final String etag;

        public Response(int status, String body, String etag) {
            this.status = status;
            this.body = body;
            this.etag = etag;
        }
    }

    public interface Fetcher {
        Response fetch(String url, String etag) throws IOException;
    }

    public interface Listener {
        // A newer release to show; null when there is none (the manual check says so)
        void onResult(Release release);
    }

    // Swapped by the tests
    public static Fetcher fetcher = MZGramUpdateChecker::httpFetch;
    public static long installedCodeOverride = -1;
    public static long nowOverride = -1;

    private static volatile boolean checking;

    // The versionCode base (versionCode without the ABI digit) of a stable tag,
    // -1 for a beta or anything that is not a release tag.
    public static long stableCode(String tag) {
        if (tag == null) {
            return -1;
        }
        Matcher m = TAG.matcher(tag);
        if (!m.matches() || m.group(4) != null) {
            return -1;
        }
        long major = Long.parseLong(m.group(1));
        long minor = Long.parseLong(m.group(2));
        long patch = Long.parseLong(m.group(3));
        return major * 1000000L + minor * 10000L + patch * 100L + 99L;
    }

    // A release from the answer of /releases/latest, or null when it is not a
    // stable MZGram release (draft, prerelease, beta tag, no page link).
    public static Release parse(String json) {
        try {
            JSONObject object = new JSONObject(json);
            if (object.optBoolean("draft", false) || object.optBoolean("prerelease", false)) {
                return null;
            }
            String tag = object.optString("tag_name", "");
            long code = stableCode(tag);
            if (code < 0) {
                return null;
            }
            String url = object.optString("html_url", "");
            if (!url.startsWith(RELEASES_PAGE_PREFIX)) {
                url = RELEASES_PAGE_PREFIX + "tag/" + tag;
            }
            return new Release(tag, url, code);
        } catch (Exception e) {
            return null;
        }
    }

    public static long installedCode() {
        if (installedCodeOverride >= 0) {
            return installedCodeOverride;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode / 10;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private static long now() {
        return nowOverride >= 0 ? nowOverride : System.currentTimeMillis();
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static void dismiss(Release release) {
        preferences().edit().putString("dismissed_tag", release.tag).apply();
    }

    public static void reset() {
        preferences().edit().clear().commit();
    }

    private static Release saved(SharedPreferences prefs) {
        String tag = prefs.getString("latest_tag", null);
        String url = prefs.getString("latest_url", null);
        long code = stableCode(tag);
        return code < 0 || url == null ? null : new Release(tag, url, code);
    }

    // One check, on the calling thread (never the UI thread): the release to
    // show, or null. force: the user asked, the interval and the switch do not apply.
    public static Release checkNow(boolean force) {
        if (!force && !MZGramConfig.checkUpdates) {
            return null;
        }
        SharedPreferences prefs = preferences();
        long now = now();
        boolean due = force || Math.abs(now - prefs.getLong("last_check", 0)) >= CHECK_INTERVAL_MS;
        Release latest = saved(prefs);
        if (due) {
            prefs.edit().putLong("last_check", now).apply();
            try {
                Response response = fetcher.fetch(LATEST_RELEASE_URL, prefs.getString("etag", null));
                if (response.status == 200) {
                    latest = parse(response.body);
                    SharedPreferences.Editor editor = prefs.edit();
                    if (response.etag != null) {
                        editor.putString("etag", response.etag);
                    } else {
                        editor.remove("etag");
                    }
                    if (latest != null) {
                        editor.putString("latest_tag", latest.tag).putString("latest_url", latest.url);
                    } else {
                        editor.remove("latest_tag").remove("latest_url");
                    }
                    editor.apply();
                } else if (response.status == 404) {
                    // No release published yet
                    latest = null;
                    prefs.edit().remove("etag").remove("latest_tag").remove("latest_url").apply();
                }
                // 304: the saved release still holds; anything else: silent, the saved one stays
            } catch (Throwable e) {
                FileLog.d("MZGram update check failed: " + e.getMessage());
            }
        }
        if (latest == null || latest.code <= installedCode()) {
            return null;
        }
        if (!force && latest.tag.equals(prefs.getString("dismissed_tag", null))) {
            return null;
        }
        return latest;
    }

    // The check off the UI thread; the listener runs on the UI thread.
    public static void check(boolean force, Listener listener) {
        if (!force && !MZGramConfig.checkUpdates) {
            return;
        }
        if (checking) {
            return;
        }
        checking = true;
        Utilities.globalQueue.postRunnable(() -> {
            Release release = null;
            try {
                release = checkNow(force);
            } finally {
                checking = false;
            }
            final Release result = release;
            AndroidUtilities.runOnUIThread(() -> {
                if (listener != null && (result != null || force)) {
                    listener.onResult(result);
                }
            });
        });
    }

    private static Response httpFetch(String address, String etag) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "MZGram-Android");
            if (etag != null) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            int status = connection.getResponseCode();
            String body = "";
            if (status == 200) {
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    // A release answer is a few kilobytes; more is not a release
                    while ((read = in.read(buffer)) > 0 && out.size() < 1024 * 1024) {
                        out.write(buffer, 0, read);
                    }
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return new Response(status, body, connection.getHeaderField("ETag"));
        } finally {
            connection.disconnect();
        }
    }
}
