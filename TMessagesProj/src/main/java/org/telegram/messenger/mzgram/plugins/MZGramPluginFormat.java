/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The .plugin format: one Python file whose metadata are top-level literal
 * constants (__id__, __name__, __version__, __permissions__ ...) plus one
 * BasePlugin subclass. The metadata are read in Python through the AST,
 * without running the file (src/mzgramPlugins/python/mzgram_plugins/metadata.py),
 * and come back here as JSON. Archives (.elyx, .eaf) are not supported yet.
 *
 * Only a build with -PmzgramPlugins=true can read plugins; in the default
 * build readMetadata() answers with an error and nothing else happens.
 *
 * Based on exteraless metadata_parser and PluginsConstants (GPL-3.0).
 */

package org.telegram.messenger.mzgram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MZGramPluginFormat {

    public static final String EXTENSION = ".plugin";

    private static final String MODULE = "mzgram_plugins.metadata";
    private static final String FUNCTION = "read_metadata_json";

    private MZGramPluginFormat() {
    }

    /** The metadata of one plugin, as the file declares them. */
    public static final class Meta {
        public final String id;
        public final String name;
        public final String description;
        public final String author;
        public final String version;
        /** Sticker set name of the icon, or null. */
        public final String icon;
        /** A version requirement like ">=11.9.0", or null. */
        public final String appVersion;
        public final String sdkVersion;
        public final boolean beta;
        /** PEP 508 strings; not installed on the device (no pip). */
        public final List<String> requirements;
        public final List<String> permissions;
        /** False when the file has no __permissions__ at all (not the same as an empty list). */
        public final boolean permissionsDeclared;

        Meta(JSONObject json) {
            id = json.optString("id", "");
            name = json.optString("name", "");
            description = json.optString("description", "");
            author = json.optString("author", "");
            version = json.optString("version", "1.0");
            icon = optNullable(json, "icon");
            appVersion = optNullable(json, "app_version");
            sdkVersion = optNullable(json, "sdk_version");
            beta = json.optBoolean("beta", false);
            requirements = strings(json.optJSONArray("requirements"));
            permissions = strings(json.optJSONArray("permissions"));
            permissionsDeclared = json.optBoolean("permissions_declared", false);
        }
    }

    /** Either the metadata or the reason they could not be read. */
    public static final class Result {
        public final Meta meta;
        public final String error;

        Result(Meta meta, String error) {
            this.meta = meta;
            this.error = error;
        }

        public boolean isOk() {
            return meta != null;
        }
    }

    public static boolean isPluginFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    /**
     * Reads the metadata of a .plugin file without running it; onResult gets
     * the result on the UI thread. Starts the Python runtime when needed.
     */
    public static void readMetadata(File file, Utilities.Callback<Result> onResult) {
        if (onResult == null) {
            return;
        }
        if (file == null || !isPluginFile(file.getName())) {
            onResult.run(new Result(null, "not a " + EXTENSION + " file"));
            return;
        }
        if (!MZGramPythonRuntime.isAvailable()) {
            onResult.run(new Result(null, "no Python runtime in this build"));
            return;
        }
        MZGramPythonRuntime.call(MODULE, FUNCTION, file.getAbsolutePath(), json -> {
            if (json == null) {
                final String error = MZGramPythonRuntime.getLastError();
                onResult.run(new Result(null, error != null ? error : "Python call failed"));
            } else {
                onResult.run(parse(json));
            }
        });
    }

    /** The JSON of read_metadata_json: {"ok": true, "meta": {...}} or {"ok": false, "error": "..."}. */
    public static Result parse(String json) {
        try {
            final JSONObject object = new JSONObject(json);
            if (object.optBoolean("ok", false)) {
                final JSONObject meta = object.optJSONObject("meta");
                if (meta != null && meta.optString("id", "").length() > 0) {
                    return new Result(new Meta(meta), null);
                }
                return new Result(null, "no metadata");
            }
            return new Result(null, object.optString("error", "invalid metadata"));
        } catch (Exception e) {
            return new Result(null, "invalid metadata: " + e.getMessage());
        }
    }

    private static String optNullable(JSONObject json, String key) {
        if (json.isNull(key)) {
            return null;
        }
        final String value = json.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static List<String> strings(JSONArray array) {
        if (array == null || array.length() == 0) {
            return Collections.emptyList();
        }
        final ArrayList<String> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            final String value = array.optString(i, null);
            if (value != null) {
                list.add(value);
            }
        }
        return Collections.unmodifiableList(list);
    }
}
