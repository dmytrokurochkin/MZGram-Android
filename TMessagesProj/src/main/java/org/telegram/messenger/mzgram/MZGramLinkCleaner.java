/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Strips tracking parameters from a web link before it opens. Works only on
 * the link itself, on the device: no request goes anywhere to unshorten or
 * check it. Nekogram's link analyzer runs on its own server, so this is
 * MZGram's own code rather than a port.
 */

package org.telegram.messenger.mzgram;

import android.net.Uri;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MZGramLinkCleaner {

    // Parameters removed on every site.
    private static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
            "fbclid", "gclid", "gclsrc", "dclid", "wbraid", "gbraid", "msclkid",
            "yclid", "ysclid", "mc_cid", "mc_eid", "igshid", "igsh", "twclid",
            "ttclid", "li_fat_id", "_hsenc", "_hsmi", "mkt_tok", "vero_id",
            "oly_anon_id", "oly_enc_id", "rb_clickid", "s_cid", "ref_src"
    ));

    // "si" is a share id on these hosts only; elsewhere it can be a real parameter.
    private static final Set<String> SI_HOSTS = new HashSet<>(Arrays.asList(
            "youtube.com", "youtu.be", "music.youtube.com", "open.spotify.com"
    ));

    public static boolean isTrackingParam(String host, String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        final String key = name.toLowerCase(Locale.US);
        if (key.startsWith("utm_") || TRACKING_PARAMS.contains(key)) {
            return true;
        }
        return "si".equals(key) && host != null && SI_HOSTS.contains(stripWww(host));
    }

    // Returns the link without tracking parameters, or the same Uri when there
    // is nothing to remove. Leaves Telegram links (t.me, telegram.me, tg://) and
    // anything that is not http or https untouched.
    public static Uri clean(Uri uri) {
        if (uri == null || uri.isOpaque()) {
            return uri;
        }
        final String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return uri;
        }
        final String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.US);
        if (host == null || isTelegramHost(host)) {
            return uri;
        }
        final String query = uri.getEncodedQuery();
        if (TextUtils.isEmpty(query)) {
            return uri;
        }
        final StringBuilder kept = new StringBuilder();
        boolean removed = false;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            final int eq = pair.indexOf('=');
            final String name = Uri.decode(eq >= 0 ? pair.substring(0, eq) : pair);
            if (isTrackingParam(host, name)) {
                removed = true;
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(pair);
        }
        if (!removed) {
            return uri;
        }
        return uri.buildUpon().encodedQuery(kept.length() == 0 ? null : kept.toString()).build();
    }

    private static boolean isTelegramHost(String host) {
        final String h = stripWww(host);
        return h.equals("t.me") || h.endsWith(".t.me") || h.equals("telegram.me") || h.equals("telegram.dog")
                || h.equals("telegra.ph") || h.equals("te.legra.ph") || h.equals("graph.org");
    }

    private static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
