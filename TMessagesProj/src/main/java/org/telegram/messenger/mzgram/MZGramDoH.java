/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * DNS-over-HTTPS (DoH) client supporting Cloudflare, Google, and AdGuard.
 * Resolves hostnames via encrypted HTTPS queries to the user-selected provider,
 * protecting DNS traffic from ISP snooping and spoofing.
 */

package org.telegram.messenger.mzgram;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MZGramDoH {

    public static final int PROVIDER_OFF = 0;
    public static final int PROVIDER_CLOUDFLARE = 1;
    public static final int PROVIDER_GOOGLE = 2;
    public static final int PROVIDER_ADGUARD = 3;

    public static final int[] PROVIDERS = {
        PROVIDER_OFF, PROVIDER_CLOUDFLARE, PROVIDER_GOOGLE, PROVIDER_ADGUARD
    };

    private static final Map<String, CacheEntry> cache = new HashMap<>();

    private static final class CacheEntry {
        final List<InetAddress> addresses;
        final long expiresAt;

        CacheEntry(List<InetAddress> addresses, long ttlMs) {
            this.addresses = addresses;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    private MZGramDoH() {
    }

    public static String getProviderTitle(int provider) {
        switch (provider) {
            case PROVIDER_CLOUDFLARE:
                return "Cloudflare (1.1.1.1)";
            case PROVIDER_GOOGLE:
                return "Google (8.8.8.8)";
            case PROVIDER_ADGUARD:
                return "AdGuard";
            case PROVIDER_OFF:
            default:
                return LocaleController.getString(R.string.MZGramDoHOff);
        }
    }

    public static String getProviderUrl(int provider, String host) {
        switch (provider) {
            case PROVIDER_CLOUDFLARE:
                return "https://cloudflare-dns.com/dns-query?name=" + host + "&type=A";
            case PROVIDER_GOOGLE:
                return "https://dns.google/resolve?name=" + host + "&type=A";
            case PROVIDER_ADGUARD:
                return "https://dns.adguard-dns.com/resolve?name=" + host + "&type=A";
            default:
                return null;
        }
    }

    public static List<InetAddress> resolve(String host) {
        int provider = MZGramConfig.dohProvider;
        if (provider == PROVIDER_OFF || host == null || host.isEmpty()) {
            return null;
        }
        synchronized (cache) {
            CacheEntry entry = cache.get(host);
            if (entry != null && entry.isValid()) {
                return entry.addresses;
            }
        }
        String queryUrl = getProviderUrl(provider, host);
        if (queryUrl == null) {
            return null;
        }
        try {
            URL url = new URL(queryUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Accept", "application/dns-json");
            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                in.close();
                String responseStr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(responseStr);
                if (json.has("Answer")) {
                    JSONArray answers = json.getJSONArray("Answer");
                    List<InetAddress> result = new ArrayList<>();
                    for (int i = 0; i < answers.length(); i++) {
                        JSONObject ans = answers.getJSONObject(i);
                        if (ans.optInt("type") == 1 && ans.has("data")) {
                            String ip = ans.getString("data");
                            try {
                                result.add(InetAddress.getByName(ip));
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                    if (!result.isEmpty()) {
                        synchronized (cache) {
                            cache.put(host, new CacheEntry(result, 60_000L));
                        }
                        return result;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    public static void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
