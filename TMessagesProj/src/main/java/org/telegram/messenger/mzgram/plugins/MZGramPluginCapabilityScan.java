/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * What a plugin may do, read from its code without running it
 * (mzgram_plugins/capability_scan.py), for the prompt before a plugin is
 * added: the permissions its code points to, with the evidence by technical
 * names, the ones it does not declare (they will be refused), what MZGram
 * never allows a plugin, signs of code made hard to read and compiled
 * Android code carried in the file.
 *
 * Only a guess from the code: the line is held while the plugin runs, by
 * the sink gates and the permissions. Nothing is stored; the file is read
 * again each time the prompt opens.
 *
 * Based on exteraless plugins/PluginCapabilityScan.java (GPL-3.0): the call
 * of the scan and the reading of its JSON; written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MZGramPluginCapabilityScan {

    /** Not a permission: what MZGram never allows a plugin (processes, the plugin engine). */
    public static final String KEY_NEVER = "never";
    /** Not a permission: the code is made hard to read. */
    public static final String KEY_OBFUSCATION = "obfuscation";
    /** Not a permission: compiled Android code in the file ("size:N", "class:X", "more:N"). */
    public static final String KEY_DEX = "dex";

    /** Evidence shown for one key at most; the rest is counted. */
    public static final int MAX_EVIDENCE = 6;

    private static final String MODULE = "mzgram_plugins.capability_scan";
    private static final String FUNCTION = "scan_json";

    private MZGramPluginCapabilityScan() {
    }

    /** The scan of one file: the keys in the order of the permissions, then the other keys. */
    public static final class Result {
        /** Permission key or KEY_* to its evidence; no empty lists. */
        public final Map<String, List<String>> found;
        /** Why the code could not be read, or null. */
        public final String error;

        Result(Map<String, List<String>> found, String error) {
            this.found = found;
            this.error = error;
        }

        public boolean isOk() {
            return error == null;
        }

        /** The permission keys the code points to, in the order of MZGramPluginGrants.ALL, without "ui". */
        public List<String> permissions() {
            final ArrayList<String> out = new ArrayList<>();
            for (String key : MZGramPluginGrants.ALL) {
                if (!MZGramPluginGrants.UI.equals(key) && found.containsKey(key)) {
                    out.add(key);
                }
            }
            return out;
        }

        public List<String> evidence(String key) {
            final List<String> list = found.get(key);
            return list != null ? list : Collections.emptyList();
        }

        /** Bytes of compiled Android code in the file; 0 when there is none. */
        public long dexSize() {
            for (String item : evidence(KEY_DEX)) {
                if (item.startsWith("size:")) {
                    try {
                        return Long.parseLong(item.substring(5));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return 0;
        }
    }

    /**
     * Whether the plugin will be refused a permission its code points to:
     * it lists its permissions and this one is not among them. A plugin that
     * lists none is offered all of them, so nothing is undeclared.
     */
    public static boolean isUndeclared(MZGramPluginFormat.Meta meta, String key) {
        return meta != null && meta.permissionsDeclared && !meta.permissions.contains(key);
    }

    /** The evidence as one line: at most MAX_EVIDENCE names, then "+N". */
    public static String evidenceLine(List<String> evidence) {
        final StringBuilder line = new StringBuilder();
        final int shown = Math.min(evidence.size(), MAX_EVIDENCE);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(evidence.get(i));
        }
        if (evidence.size() > shown) {
            line.append(" +").append(evidence.size() - shown);
        }
        return line.toString();
    }

    /**
     * Scans the .plugin file without running it; onResult gets the result on
     * the UI thread. Needs the Python runtime (the build with plugins).
     */
    public static void scan(File file, Utilities.Callback<Result> onResult) {
        if (onResult == null) {
            return;
        }
        if (file == null || !file.isFile()) {
            onResult.run(new Result(Collections.emptyMap(), "no file"));
            return;
        }
        if (!MZGramPythonRuntime.isAvailable()) {
            onResult.run(new Result(Collections.emptyMap(), "no Python runtime in this build"));
            return;
        }
        MZGramPythonRuntime.call(MODULE, FUNCTION, file.getAbsolutePath(), json -> {
            if (json == null) {
                final String error = MZGramPythonRuntime.getLastError();
                onResult.run(new Result(Collections.emptyMap(), error != null ? error : "Python call failed"));
            } else {
                onResult.run(parse(json));
            }
        });
    }

    /**
     * The JSON of scan_json: {"ok": true, "found": {key: [evidence]}} or
     * {"ok": false, "error": "..."}. Unknown keys and empty lists are left out.
     */
    public static Result parse(String json) {
        try {
            final JSONObject object = new JSONObject(json);
            if (!object.optBoolean("ok", false)) {
                return new Result(Collections.emptyMap(), object.optString("error", "scan failed"));
            }
            final JSONObject found = object.optJSONObject("found");
            final LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
            if (found != null) {
                for (String key : MZGramPluginGrants.ALL) {
                    if (!MZGramPluginGrants.UI.equals(key)) {
                        put(out, key, found.optJSONArray(key));
                    }
                }
                put(out, KEY_NEVER, found.optJSONArray(KEY_NEVER));
                put(out, KEY_OBFUSCATION, found.optJSONArray(KEY_OBFUSCATION));
                put(out, KEY_DEX, found.optJSONArray(KEY_DEX));
            }
            return new Result(Collections.unmodifiableMap(out), null);
        } catch (Exception e) {
            return new Result(Collections.emptyMap(), "invalid scan: " + e.getMessage());
        }
    }

    private static void put(Map<String, List<String>> out, String key, JSONArray array) {
        if (array == null) {
            return;
        }
        final ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            final String item = array.optString(i, "").trim();
            if (!item.isEmpty() && !list.contains(item)) {
                list.add(item);
            }
        }
        if (!list.isEmpty()) {
            out.put(key, Collections.unmodifiableList(list));
        }
    }
}
