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
import java.util.Locale;

public final class MZGramUnifiedPushRules {

    // Gateway used until the user sets their own.
    public static final String DEFAULT_GATEWAY = "https://p2p.belloworld.it/";

    private MZGramUnifiedPushRules() {
    }

    // UnifiedPush runs by itself only where Google services are missing;
    // the user's own choice, once made, wins either way.
    public static boolean usesUnifiedPush(Boolean userChoice, boolean hasGoogleServices) {
        return userChoice != null ? userChoice : !hasGoogleServices;
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
