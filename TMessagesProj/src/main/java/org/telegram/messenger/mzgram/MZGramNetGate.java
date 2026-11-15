/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * MZGramNetGate: Gatekeeper for outbound third-party connections (§3.5 Rule 2).
 */

package org.telegram.messenger.mzgram;

import android.net.Uri;
import android.util.Log;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.mzgram.translate.MZGramTranslator;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class MZGramNetGate {

    public static final String TAG = "MZGramNet";

    public static final String FEATURE_TRANSLATION_GOOGLE = "translation_google";
    public static final String FEATURE_TRANSLATION_DEEPL = "translation_deepl";
    public static final String FEATURE_TRANSLATION_MOZHI = "translation_mozhi";
    public static final String FEATURE_TRANSLATION_AI = "translation_ai";
    public static final String FEATURE_TRANSLATION_YANDEX = "translation_yandex";
    public static final String FEATURE_DOH = "dns_over_https";
    public static final String FEATURE_MAP_PREVIEW = "map_preview";
    public static final String FEATURE_MAP_OSM = "map_osm";
    public static final String FEATURE_UNIFIED_PUSH = "unified_push";
    public static final String FEATURE_UPDATE_CHECK = "update_check";
    public static final String FEATURE_CURRENCY = "currency";
    public static final String FEATURE_LRCLIB = "lrclib";
    public static final String FEATURE_VOSK = "vosk_model";

    public static boolean isFeatureEnabled(String feature) {
        if (feature == null) {
            return false;
        }
        switch (feature) {
            case FEATURE_TRANSLATION_GOOGLE:
                return MZGramConfig.chatTranslationBar
                        && (MZGramConfig.translationProvider == MZGramTranslator.PROVIDER_GOOGLE
                        || MZGramConfig.translationAutoFallback);
            case FEATURE_TRANSLATION_DEEPL:
                return MZGramConfig.chatTranslationBar
                        && MZGramConfig.translationProvider == MZGramTranslator.PROVIDER_DEEPL;
            case FEATURE_TRANSLATION_MOZHI:
                return MZGramConfig.chatTranslationBar
                        && MZGramConfig.translationProvider == MZGramTranslator.PROVIDER_MOZHI;
            case FEATURE_TRANSLATION_AI:
                return MZGramConfig.chatTranslationBar
                        && MZGramConfig.translationProvider == MZGramTranslator.PROVIDER_OPENAI;
            case FEATURE_TRANSLATION_YANDEX:
                return MZGramConfig.chatTranslationBar
                        && MZGramConfig.translationProvider == MZGramTranslator.PROVIDER_TELEGRAM; // Fallback or Yandex if configured
            case FEATURE_DOH:
                return MZGramConfig.dohProvider != 0;
            case FEATURE_MAP_PREVIEW:
                return MZGramConfig.mapPreviewProvider != 0 && MZGramConfig.mapPreviewProvider != 3
                        || MZGramConfig.openFreeMapPreview;
            case FEATURE_MAP_OSM:
                return MZGramConfig.mapProvider == 1;
            case FEATURE_UNIFIED_PUSH:
                return true;
            case FEATURE_UPDATE_CHECK:
                return MZGramConfig.checkUpdates;
            case FEATURE_CURRENCY:
            case FEATURE_LRCLIB:
            case FEATURE_VOSK:
                return true;
            default:
                return true;
        }
    }

    public static String extractHost(String hostOrUrl) {
        if (hostOrUrl == null || hostOrUrl.isEmpty()) {
            return "";
        }
        try {
            if (hostOrUrl.contains("://")) {
                Uri uri = Uri.parse(hostOrUrl);
                String host = uri.getHost();
                return host != null ? host : hostOrUrl;
            }
        } catch (Exception e) {
            // Ignore parse failures
        }
        int slashIndex = hostOrUrl.indexOf('/');
        if (slashIndex > 0) {
            return hostOrUrl.substring(0, slashIndex);
        }
        int colonIndex = hostOrUrl.indexOf(':');
        if (colonIndex > 0) {
            return hostOrUrl.substring(0, colonIndex);
        }
        return hostOrUrl;
    }

    public static boolean checkAndLog(String feature, String hostOrUrl) {
        String host = extractHost(hostOrUrl);
        boolean enabled = isFeatureEnabled(feature);
        if (!enabled) {
            Log.w(TAG, "BLOCKED: " + feature + " -> " + host);
            FileLog.w("MZGramNet: BLOCKED: " + feature + " -> " + host);
            return false;
        }
        Log.i(TAG, feature + " -> " + host);
        FileLog.d("MZGramNet: " + feature + " -> " + host);
        return true;
    }

    public static HttpURLConnection openConnection(String feature, URL url) throws IOException {
        String host = url != null ? url.getHost() : "";
        if (!checkAndLog(feature, host)) {
            throw new IOException("MZGramNetGate: connection to " + host + " blocked because feature '" + feature + "' is disabled");
        }
        return (HttpURLConnection) url.openConnection();
    }
}
