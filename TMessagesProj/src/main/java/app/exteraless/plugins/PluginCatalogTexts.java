package app.exteraless.plugins;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Names and descriptions of MZGram catalog plugins in the app language.
 * The texts come from the cached catalog (index.json of MZGram-Plugins), so a catalog plugin shows
 * the catalog's translated description everywhere in the app, not the text inside its file.
 */
public final class PluginCatalogTexts {

    public static final String CACHE_NAME = "mzgram_plugin_catalog.json";
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private static final class Texts {
        String name;
        JSONObject names;
        String description;
        JSONObject descriptions;
    }

    private static Map<String, Texts> byId;

    private PluginCatalogTexts() {
    }

    /** Reads the cached catalog in the background, so the first list does not wait for it. */
    public static void preload() {
        Utilities.globalQueue.postRunnable(PluginCatalogTexts::ensureLoaded);
    }

    /** Drops the loaded texts after the catalog cache was rewritten. */
    public static synchronized void invalidate() {
        byId = null;
    }

    /** The catalog name in the app language, or null for a plugin that is not in the catalog. */
    public static String name(String id) {
        Texts texts = get(id);
        if (texts == null) {
            return null;
        }
        String localized = forCurrentLanguage(texts.names);
        return !TextUtils.isEmpty(localized) ? localized : (TextUtils.isEmpty(texts.name) ? null : texts.name);
    }

    /** The catalog description in the app language, or null for a plugin that is not in the catalog. */
    public static String description(String id) {
        Texts texts = get(id);
        if (texts == null) {
            return null;
        }
        String localized = forCurrentLanguage(texts.descriptions);
        return !TextUtils.isEmpty(localized) ? localized : (TextUtils.isEmpty(texts.description) ? null : texts.description);
    }

    /** The value for the app language from a {"uk": ..., "pt-BR": ...} map, or null. */
    public static String forCurrentLanguage(JSONObject map) {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (map == null || locale == null) {
            return null;
        }
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        String plain = null;
        for (Iterator<String> it = map.keys(); it.hasNext(); ) {
            String key = it.next();
            if (key.equalsIgnoreCase(lang + "-" + country)) {
                return map.optString(key, null);
            }
            if (plain == null && key.equalsIgnoreCase(lang)) {
                plain = map.optString(key, null);
            }
        }
        return plain;
    }

    private static Texts get(String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        Map<String, Texts> map = ensureLoaded();
        return map.get(id);
    }

    private static synchronized Map<String, Texts> ensureLoaded() {
        if (byId != null) {
            return byId;
        }
        Map<String, Texts> map = new HashMap<>();
        File cache = new File(ApplicationLoader.getFilesDirFixed(), CACHE_NAME);
        if (cache.exists() && cache.length() <= MAX_BYTES) {
            try (InputStream in = new FileInputStream(cache)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) cache.length());
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                JSONArray plugins = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8)).getJSONArray("plugins");
                for (int i = 0; i < plugins.length(); i++) {
                    JSONObject p = plugins.optJSONObject(i);
                    if (p == null || TextUtils.isEmpty(p.optString("id", ""))) {
                        continue;
                    }
                    Texts texts = new Texts();
                    texts.name = p.optString("name", "");
                    texts.names = p.optJSONObject("name_i18n");
                    texts.description = p.optString("description", "");
                    texts.descriptions = p.optJSONObject("description_i18n");
                    map.put(p.optString("id"), texts);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        byId = map;
        return map;
    }
}
