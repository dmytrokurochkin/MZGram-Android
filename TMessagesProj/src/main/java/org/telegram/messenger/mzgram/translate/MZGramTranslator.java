/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Multi-provider translation layer for MZGram.
 * Supports Telegram Cloud, Google Translate, DeepL (with user key),
 * OpenAI / DeepSeek (with user key), AIDL offline translation,
 * and Mozhi decentralized privacy proxy.
 */

package org.telegram.messenger.mzgram.translate;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class MZGramTranslator {

    public static final int PROVIDER_TELEGRAM = 0;
    public static final int PROVIDER_GOOGLE = 1;
    public static final int PROVIDER_DEEPL = 2;
    public static final int PROVIDER_OPENAI = 3;
    public static final int PROVIDER_OFFLINE_AIDL = 4;
    public static final int PROVIDER_MOZHI = 5;

    public static final int PROVIDERS_COUNT = 6;

    private static final int TIMEOUT_MS = 15000;

    private MZGramTranslator() {
    }

    public static String getProviderName(int provider) {
        switch (provider) {
            case PROVIDER_GOOGLE:
                return LocaleController.getString(R.string.MZGramTranslatorGoogle);
            case PROVIDER_DEEPL:
                return LocaleController.getString(R.string.MZGramTranslatorDeepL);
            case PROVIDER_OPENAI:
                return LocaleController.getString(R.string.MZGramTranslatorAi);
            case PROVIDER_OFFLINE_AIDL:
                return LocaleController.getString(R.string.MZGramTranslatorAidl);
            case PROVIDER_MOZHI:
                return LocaleController.getString(R.string.MZGramTranslatorMozhi);
            case PROVIDER_TELEGRAM:
            default:
                return LocaleController.getString(R.string.MZGramTranslatorTelegram);
        }
    }

    public static void translate(String text, String fromLang, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        if (TextUtils.isEmpty(text)) {
            deliverResult(onResult, "");
            return;
        }

        final int provider = MZGramConfig.translationProvider;
        final String target = !TextUtils.isEmpty(toLang) ? toLang : (TextUtils.isEmpty(MZGramConfig.translationTargetLang) ? LocaleController.getInstance().getCurrentLocale().getLanguage() : MZGramConfig.translationTargetLang);
        final String source = !TextUtils.isEmpty(fromLang) ? fromLang : "auto";

        switch (provider) {
            case PROVIDER_GOOGLE:
                translateGoogle(text, source, target, onResult, onError);
                break;
            case PROVIDER_DEEPL:
                translateDeepL(text, source, target, onResult, onError);
                break;
            case PROVIDER_OPENAI:
                translateAi(text, source, target, onResult, onError);
                break;
            case PROVIDER_OFFLINE_AIDL:
                translateAidl(text, target, onResult, onError);
                break;
            case PROVIDER_MOZHI:
                translateMozhi(text, source, target, onResult, onError);
                break;
            case PROVIDER_TELEGRAM:
            default:
                // Fallback to Google if Telegram cloud is called through custom dispatcher
                translateGoogle(text, source, target, onResult, onError);
                break;
        }
    }

    private static void translateGoogle(String text, String fromLang, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                String sl = TextUtils.isEmpty(fromLang) || "auto".equalsIgnoreCase(fromLang) ? "auto" : fromLang;
                String tl = TextUtils.isEmpty(toLang) ? "en" : toLang;
                String query = URLEncoder.encode(text, "UTF-8");
                String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + sl + "&tl=" + tl + "&dt=t&q=" + query;

                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    deliverError(onError, "Google HTTP " + code);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONArray root = new JSONArray(response.toString());
                JSONArray sentences = root.optJSONArray(0);
                if (sentences == null) {
                    deliverError(onError, "Empty Google response");
                    return;
                }

                StringBuilder translated = new StringBuilder();
                for (int i = 0; i < sentences.length(); i++) {
                    JSONArray part = sentences.optJSONArray(i);
                    if (part != null) {
                        String fragment = part.optString(0, "");
                        translated.append(fragment);
                    }
                }

                deliverResult(onResult, translated.toString());
            } catch (Exception e) {
                FileLog.e(e);
                deliverError(onError, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void translateDeepL(String text, String fromLang, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        final String apiKey = MZGramConfig.deeplApiKey;
        if (TextUtils.isEmpty(apiKey)) {
            deliverError(onError, LocaleController.getString(R.string.MZGramTranslatorDeepLKeyRequired));
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                boolean isPro = !apiKey.endsWith(":fx");
                String endpoint = isPro ? "https://api.deepl.com/v2/translate" : "https://api-free.deepl.com/v2/translate";

                JSONObject body = new JSONObject();
                JSONArray textArr = new JSONArray();
                textArr.put(text);
                body.put("text", textArr);
                body.put("target_lang", toLang.toUpperCase(java.util.Locale.ROOT));
                if (!TextUtils.isEmpty(fromLang) && !"auto".equalsIgnoreCase(fromLang)) {
                    body.put("source_lang", fromLang.toUpperCase(java.util.Locale.ROOT));
                }

                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("Authorization", "DeepL-Auth-Key " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                byte[] postData = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                }

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    deliverError(onError, "DeepL HTTP " + code);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject res = new JSONObject(response.toString());
                JSONArray translations = res.optJSONArray("translations");
                if (translations != null && translations.length() > 0) {
                    String translated = translations.getJSONObject(0).optString("text", "");
                    deliverResult(onResult, translated);
                } else {
                    deliverError(onError, "Empty DeepL response");
                }
            } catch (Exception e) {
                FileLog.e(e);
                deliverError(onError, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void translateAi(String text, String fromLang, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        MZGramAiService.translate(text, fromLang, toLang, onResult, onError);
    }

    private static void translateAidl(String text, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        MZGramAidlTranslate.translate(text, toLang, result -> {
            if (result != null) {
                deliverResult(onResult, result);
            } else {
                if (MZGramConfig.translationAutoFallback) {
                    translateGoogle(text, "auto", toLang, onResult, onError);
                } else {
                    deliverError(onError, "AIDL service unavailable");
                }
            }
        }, error -> {
            if (MZGramConfig.translationAutoFallback) {
                translateGoogle(text, "auto", toLang, onResult, onError);
            } else {
                deliverError(onError, error);
            }
        });
    }

    private static void translateMozhi(String text, String fromLang, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                String instance = !TextUtils.isEmpty(MZGramConfig.mozhiCustomInstance) ? MZGramConfig.mozhiCustomInstance.trim() : "https://mozhi.themarkup.org";
                if (instance.endsWith("/")) {
                    instance = instance.substring(0, instance.length() - 1);
                }
                String endpoint = instance + "/api/v1/translate";

                String sl = TextUtils.isEmpty(fromLang) ? "auto" : fromLang;
                String tl = TextUtils.isEmpty(toLang) ? "en" : toLang;

                String params = "engine=google&from=" + URLEncoder.encode(sl, "UTF-8") + "&to=" + URLEncoder.encode(tl, "UTF-8") + "&text=" + URLEncoder.encode(text, "UTF-8");

                URL url = new URL(endpoint + "?" + params);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    if (MZGramConfig.translationAutoFallback) {
                        translateGoogle(text, fromLang, toLang, onResult, onError);
                    } else {
                        deliverError(onError, "Mozhi HTTP " + code);
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject res = new JSONObject(response.toString());
                String translated = res.optString("translated-text", "");
                if (!TextUtils.isEmpty(translated)) {
                    deliverResult(onResult, translated);
                } else {
                    deliverError(onError, "Empty Mozhi translation");
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (MZGramConfig.translationAutoFallback) {
                    translateGoogle(text, fromLang, toLang, onResult, onError);
                } else {
                    deliverError(onError, e.getMessage());
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void deliverResult(Utilities.Callback<String> onResult, String text) {
        if (onResult != null) {
            AndroidUtilities.runOnUIThread(() -> onResult.run(text));
        }
    }

    private static void deliverError(Utilities.Callback<String> onError, String error) {
        if (onError != null) {
            AndroidUtilities.runOnUIThread(() -> onError.run(error != null ? error : "error"));
        }
    }
}
