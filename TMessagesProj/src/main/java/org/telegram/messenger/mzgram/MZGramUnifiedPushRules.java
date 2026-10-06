/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Push notifications through UnifiedPush: which gateway the endpoint goes
 * through, what MZGram registers with Telegram, and whether UnifiedPush is
 * used at all. No Android state here, so all of it can be tested directly.
 */

package org.telegram.messenger.mzgram;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MZGramUnifiedPushRules {

    // Gateway used until the user sets their own.
    public static final String DEFAULT_GATEWAY = "https://p2p.belloworld.it/";
    // Public half of the key the default gateway signs Google FCM pushes with.
    public static final String DEFAULT_VAPID_KEY = "BOocuINYMsroo0cng_bA3B1AhDGnfxkGuYE_J_gH5G3w_Ek1t_kAOXA8CZS1WtenzRFaGMwnTKGQ7Hp4h3Dmw1g";
    // Google Play Services, and microG under the same package name.
    public static final String PLAY_SERVICES_PACKAGE = "com.google.android.gms";

    private MZGramUnifiedPushRules() {
    }

    // On unless the user turned it off: Telegram's own Google push cannot
    // work in MZGram builds (Google refuses the Firebase key for this
    // package), so a phone with Google services gets no notifications
    // without UnifiedPush either.
    public static boolean usesUnifiedPush(Boolean userChoice) {
        return userChoice == null || userChoice;
    }

    // The distributors the user can pick from: the installed ones, with the
    // built-in Google FCM one (this app's own package) only when Google Play
    // Services or microG is there to deliver for it.
    public static List<String> offeredDistributors(List<String> installed, String ownPackage, boolean playServices) {
        ArrayList<String> offered = new ArrayList<>();
        for (String distributor : installed) {
            if ((playServices || !distributor.equals(ownPackage)) && !offered.contains(distributor)) {
                offered.add(distributor);
            }
        }
        return offered;
    }

    // The distributor to use when the user has not picked one: the saved
    // one while it is still offered, else the first distributor app, else
    // the built-in Google FCM. Null when there is nothing to use.
    public static String pickDistributor(String saved, List<String> offered, String ownPackage) {
        if (saved != null && offered.contains(saved)) {
            return saved;
        }
        for (String distributor : offered) {
            if (!distributor.equals(ownPackage)) {
                return distributor;
            }
        }
        return offered.contains(ownPackage) ? ownPackage : null;
    }

    // Where the built-in Google FCM distributor's endpoints point: the
    // gateway's /fcm/ route, which signs each push with the gateway's key.
    // It needs the gateway even when the user switched the gateway off.
    public static String fcmEndpointPrefix(String customGateway) {
        return gateway(true, customGateway) + "fcm/";
    }

    // An uncompressed P-256 public key in base64url without padding, the
    // only shape a VAPID key has.
    public static boolean isValidVapidKey(String key) {
        if (key == null || !key.matches("^[A-Za-z0-9_-]{87}$")) {
            return false;
        }
        try {
            byte[] raw = Base64.decode(key, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            return raw.length == 65 && raw[0] == 4;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String vapidKey(String customKey) {
        return isValidVapidKey(customKey) ? customKey : DEFAULT_VAPID_KEY;
    }

    // The gateway address as it is stored and used: https only, a host, no
    // user name or password, no query or fragment (MZGram appends its own),
    // ending with "/". Null when the address cannot be used.
    public static String normalizeGateway(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                return null;
            }
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return null;
        }
        if (uri.getScheme() == null || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            return null;
        }
        String authority = uri.getRawAuthority();
        if (uri.getHost() == null || uri.getHost().isEmpty() || authority == null
                || uri.getRawUserInfo() != null || authority.contains("@")) {
            return null;
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return null;
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!path.endsWith("/")) {
            path += "/";
        }
        return "https://" + authority + path;
    }

    public static boolean isValidGateway(String url) {
        return normalizeGateway(url) != null;
    }

    // How long a silent distributor is waited for before it is asked again.
    public static final long ANSWER_WAIT_MIN_MS = 30_000;
    public static final long ANSWER_WAIT_MAX_MS = 15 * 60_000;

    public static long nextAnswerWait(long current) {
        return Math.min(Math.max(current, ANSWER_WAIT_MIN_MS) * 2, ANSWER_WAIT_MAX_MS);
    }

    // The gateway in use: the user's own if it is valid, otherwise the
    // default one. Null when the gateway is switched off and Telegram sends
    // straight to the distributor.
    public static String gateway(boolean gatewayEnabled, String customGateway) {
        if (!gatewayEnabled) {
            return null;
        }
        String custom = normalizeGateway(customGateway);
        return custom != null ? custom : DEFAULT_GATEWAY;
    }

    // Where Telegram sends encrypted notifications. The gateway moves the
    // encryption headers into the body, which distributors pass on as is.
    public static String webPushEndpoint(String distributorEndpoint, String gateway) {
        return gateway == null ? distributorEndpoint : gateway + "aesgcm?e=" + encode(distributorEndpoint);
    }

    // Where Telegram sends wake-ups without content (secret chats).
    public static String simplePushToken(String distributorEndpoint, String gateway) {
        return gateway == null ? distributorEndpoint : gateway + encode(distributorEndpoint);
    }

    // The built-in Google FCM distributor's endpoint already is the
    // gateway's /fcm/ route, which does the same folding; it is registered
    // as it is, for both kinds of push.
    public static String webPushEndpoint(String distributorEndpoint, String gateway, boolean builtInFcm) {
        return builtInFcm ? distributorEndpoint : webPushEndpoint(distributorEndpoint, gateway);
    }

    public static String simplePushToken(String distributorEndpoint, String gateway, boolean builtInFcm) {
        return builtInFcm ? distributorEndpoint : simplePushToken(distributorEndpoint, gateway);
    }

    // The registered token for the diagnostics screen, with what identifies
    // this device cut short: the endpoint keeps its gateway and route, the
    // keys only their first characters.
    public static String maskPushToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(token);
            JSONObject keys = json.optJSONObject("keys");
            StringBuilder masked = new StringBuilder("endpoint: ").append(maskUrl(json.optString("endpoint")));
            if (keys != null) {
                masked.append("\np256dh: ").append(maskTail(keys.optString("p256dh"), 8));
                masked.append("\nauth: ").append(maskTail(keys.optString("auth"), 0));
            }
            return masked.toString();
        } catch (JSONException e) {
            return maskUrl(token);
        }
    }

    public static String maskUrl(String url) {
        if (url == null) {
            return "";
        }
        int start = url.indexOf("://");
        int path = start < 0 ? -1 : url.indexOf('/', start + 3);
        if (path < 0) {
            return maskTail(url, 4);
        }
        int keep = path + 1;
        for (String route : new String[]{"fcm/", "aesgcm?e="}) {
            if (url.startsWith(route, keep)) {
                keep += route.length();
                break;
            }
        }
        String secret = url.substring(keep);
        if (secret.length() <= 8) {
            return url.substring(0, keep) + (secret.isEmpty() ? "" : "\u2026");
        }
        return url.substring(0, keep) + secret.substring(0, 4) + "\u2026" + secret.substring(secret.length() - 4);
    }

    private static String maskTail(String value, int shown) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, Math.min(shown, value.length())) + "\u2026";
    }

    // The token registered with Telegram as push type 10: the endpoint and
    // the keys Telegram encrypts with.
    public static String webPushToken(String endpoint, byte[] publicKey, byte[] authSecret) throws JSONException {
        JSONObject keys = new JSONObject();
        keys.put("p256dh", base64Url(publicKey));
        keys.put("auth", base64Url(authSecret));
        JSONObject token = new JSONObject();
        token.put("endpoint", endpoint);
        token.put("keys", keys);
        return token.toString();
    }

    // What the gateway delivers starts with the "aesgcm" line; anything else
    // (a wake-up, or a notification sent past the gateway) is not decryptable.
    public static boolean looksLikeWebPush(byte[] body) {
        return body != null && body.length > 6
                && "aesgcm".equals(new String(body, 0, 6, StandardCharsets.US_ASCII));
    }

    // The decrypted notification is {"p": "<the same data as through Google>"}.
    public static String payload(byte[] plaintext) throws JSONException {
        return new JSONObject(new String(plaintext, StandardCharsets.UTF_8)).getString("p");
    }

    public static String base64Url(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
