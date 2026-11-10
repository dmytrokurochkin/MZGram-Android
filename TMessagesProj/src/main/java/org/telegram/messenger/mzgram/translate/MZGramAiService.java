/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Direct LLM client for user-configured API keys.
 * Supports OpenAI, Anthropic Claude, Google Gemini, DeepSeek and Custom endpoints.
 * All API keys are stored only on the user's device; no shared proxies.
 */

package org.telegram.messenger.mzgram.translate;

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
import java.nio.charset.StandardCharsets;

public final class MZGramAiService {

    public static final int AI_GEMINI = 0;
    public static final int AI_OPENAI = 1;
    public static final int AI_ANTHROPIC = 2;
    public static final int AI_DEEPSEEK = 3;
    public static final int AI_CUSTOM = 4;

    public static final int AI_PROVIDERS_COUNT = 5;

    private static final int TIMEOUT_MS = 25000;

    private MZGramAiService() {
    }

    public static String getAiProviderName(int provider) {
        switch (provider) {
            case AI_OPENAI:
                return "OpenAI";
            case AI_ANTHROPIC:
                return "Anthropic Claude";
            case AI_DEEPSEEK:
                return "DeepSeek";
            case AI_CUSTOM:
                return "Custom (OpenAI-compatible)";
            case AI_GEMINI:
            default:
                return "Google Gemini";
        }
    }

    public static String getDefaultModel(int provider) {
        switch (provider) {
            case AI_OPENAI:
                return "gpt-4o-mini";
            case AI_ANTHROPIC:
                return "claude-3-5-haiku-20241022";
            case AI_DEEPSEEK:
                return "deepseek-chat";
            case AI_CUSTOM:
                return "default";
            case AI_GEMINI:
            default:
                return "gemini-1.5-flash";
        }
    }

    public static void askAi(String prompt, String systemPrompt, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        final String apiKey = MZGramConfig.aiApiKey;
        if (TextUtils.isEmpty(apiKey) && MZGramConfig.aiProviderType != AI_CUSTOM) {
            deliverError(onError, LocaleController.getString(R.string.MZGramAiApiKeyRequired));
            return;
        }

        final int provider = MZGramConfig.aiProviderType;
        final String model = !TextUtils.isEmpty(MZGramConfig.aiCustomModel) ? MZGramConfig.aiCustomModel.trim() : getDefaultModel(provider);

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                if (provider == AI_GEMINI) {
                    conn = prepareGeminiRequest(prompt, systemPrompt, apiKey, model);
                } else if (provider == AI_ANTHROPIC) {
                    conn = prepareAnthropicRequest(prompt, systemPrompt, apiKey, model);
                } else if (provider == AI_DEEPSEEK) {
                    conn = prepareOpenAiCompatibleRequest("https://api.deepseek.com/v1/chat/completions", prompt, systemPrompt, apiKey, model);
                } else if (provider == AI_CUSTOM) {
                    String ep = !TextUtils.isEmpty(MZGramConfig.aiCustomEndpoint) ? MZGramConfig.aiCustomEndpoint.trim() : "http://localhost:11434/v1/chat/completions";
                    conn = prepareOpenAiCompatibleRequest(ep, prompt, systemPrompt, apiKey, model);
                } else {
                    conn = prepareOpenAiCompatibleRequest("https://api.openai.com/v1/chat/completions", prompt, systemPrompt, apiKey, model);
                }

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    deliverError(onError, "AI HTTP error " + code);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String reply;
                if (provider == AI_GEMINI) {
                    reply = parseGeminiResponse(response.toString());
                } else if (provider == AI_ANTHROPIC) {
                    reply = parseAnthropicResponse(response.toString());
                } else {
                    reply = parseOpenAiResponse(response.toString());
                }

                deliverResult(onResult, reply);
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

    public static void translate(String text, String fromLang, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        String system = "You are a professional translator. Translate the text into " + toLang + ". Output only the translation without any explanations or additional formatting.";
        askAi(text, system, onResult, onError);
    }

    private static HttpURLConnection prepareGeminiRequest(String prompt, String systemPrompt, String key, String model) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        JSONObject root = new JSONObject();
        if (!TextUtils.isEmpty(systemPrompt)) {
            JSONObject sysContent = new JSONObject();
            JSONArray sysParts = new JSONArray();
            sysParts.put(new JSONObject().put("text", systemPrompt));
            sysContent.put("parts", sysParts);
            root.put("system_instruction", sysContent);
        }

        JSONArray contents = new JSONArray();
        JSONObject userContent = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        userContent.put("parts", parts);
        contents.put(userContent);
        root.put("contents", contents);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    private static HttpURLConnection prepareAnthropicRequest(String prompt, String systemPrompt, String key, String model) throws Exception {
        URL url = new URL("https://api.anthropic.com/v1/messages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("x-api-key", key);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        JSONObject root = new JSONObject();
        root.put("model", model);
        root.put("max_tokens", 2048);
        if (!TextUtils.isEmpty(systemPrompt)) {
            root.put("system", systemPrompt);
        }

        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.put(msg);
        root.put("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    private static HttpURLConnection prepareOpenAiCompatibleRequest(String endpoint, String prompt, String systemPrompt, String key, String model) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        if (!TextUtils.isEmpty(key)) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        JSONObject root = new JSONObject();
        root.put("model", model);

        JSONArray messages = new JSONArray();
        if (!TextUtils.isEmpty(systemPrompt)) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.put(sys);
        }

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", prompt);
        messages.put(user);
        root.put("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    private static String parseGeminiResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject cand = candidates.getJSONObject(0);
            JSONObject content = cand.optJSONObject("content");
            if (content != null) {
                JSONArray parts = content.optJSONArray("parts");
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "").trim();
                }
            }
        }
        return "";
    }

    private static String parseAnthropicResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray content = root.optJSONArray("content");
        if (content != null && content.length() > 0) {
            return content.getJSONObject(0).optString("text", "").trim();
        }
        return "";
    }

    private static String parseOpenAiResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray choices = root.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.optJSONObject("message");
            if (message != null) {
                return message.optString("content", "").trim();
            }
        }
        return "";
    }

    private static void deliverResult(Utilities.Callback<String> onResult, String text) {
        if (onResult != null) {
            AndroidUtilities.runOnUIThread(() -> onResult.run(text));
        }
    }

    private static void deliverError(Utilities.Callback<String> onError, String error) {
        if (onError != null) {
            AndroidUtilities.runOnUIThread(() -> onError.run(error != null ? error : "AI error"));
        }
    }
}
