package org.mzgram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static String optStringOrNull(JSONObject json, String key) {
        if (json == null || key == null || json.isNull(key)) {
            return null;
        }
        String value = json.optString(key, null);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    public static String optStringOrNull(JSONArray array, int index) {
        if (array == null || array.isNull(index)) {
            return null;
        }
        String value = array.optString(index, null);
        return value == null || value.isEmpty() ? null : value;
    }
}
