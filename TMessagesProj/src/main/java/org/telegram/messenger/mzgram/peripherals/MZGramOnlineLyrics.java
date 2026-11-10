/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Online synced lyrics fetcher (media.synced-lyrics).
 * Connects to open LRCLIB endpoint (https://lrclib.net/api/get)
 * to retrieve timestamped LRC lyrics for the playing audio track.
 * Gated by explicit user consent setting (disabled by default).
 */

package org.telegram.messenger.mzgram.peripherals;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.mzgram.player.MZGramLyrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class MZGramOnlineLyrics {

    private static final String LRCLIB_ENDPOINT = "https://lrclib.net/api/get";
    private static final String CACHE_DIR = "lyrics_cache";
    private static final int TIMEOUT_MS = 10000;

    private MZGramOnlineLyrics() {
    }

    public static File getCacheDir() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), CACHE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getCacheFile(String artist, String title) {
        String key = (artist != null ? artist : "") + "_" + (title != null ? title : "");
        String filename = Utilities.MD5(key) + ".lrc";
        return new File(getCacheDir(), filename);
    }

    public static void fetchLyrics(String artist, String title, String album, int durationSec, Utilities.Callback<MZGramLyrics> onResult) {
        if (!MZGramConfig.syncedLyricsOnline || TextUtils.isEmpty(title)) {
            deliverResult(onResult, null);
            return;
        }

        final File cacheFile = getCacheFile(artist, title);
        if (cacheFile.isFile() && cacheFile.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                MZGramLyrics lyrics = MZGramLyrics.parse(sb.toString(), MZGramLyrics.SOURCE_ONLINE, "LRCLIB (Cached)");
                if (lyrics != null) {
                    deliverResult(onResult, lyrics);
                    return;
                }
            } catch (Exception ignore) {
            }
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                StringBuilder urlBuilder = new StringBuilder(LRCLIB_ENDPOINT);
                urlBuilder.append("?track_name=").append(URLEncoder.encode(title, "UTF-8"));
                if (!TextUtils.isEmpty(artist)) {
                    urlBuilder.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"));
                }
                if (!TextUtils.isEmpty(album)) {
                    urlBuilder.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"));
                }
                if (durationSec > 0) {
                    urlBuilder.append("&duration=").append(durationSec);
                }

                URL url = new URL(urlBuilder.toString());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "MZGram-Android/1.0");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    deliverResult(onResult, null);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    resp.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(resp.toString());
                String syncedText = json.optString("syncedLyrics", "");
                String plainText = json.optString("plainLyrics", "");

                String textToParse = !TextUtils.isEmpty(syncedText) ? syncedText : plainText;
                if (!TextUtils.isEmpty(textToParse)) {
                    // Save to cache
                    try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        fos.write(textToParse.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception ignore) {
                    }

                    MZGramLyrics lyrics = MZGramLyrics.parse(textToParse, MZGramLyrics.SOURCE_ONLINE, "LRCLIB");
                    deliverResult(onResult, lyrics);
                } else {
                    deliverResult(onResult, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
                deliverResult(onResult, null);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void deliverResult(Utilities.Callback<MZGramLyrics> onResult, MZGramLyrics lyrics) {
        if (onResult != null) {
            AndroidUtilities.runOnUIThread(() -> onResult.run(lyrics));
        }
    }
}
