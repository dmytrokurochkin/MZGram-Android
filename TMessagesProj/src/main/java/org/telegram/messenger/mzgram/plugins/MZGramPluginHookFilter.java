/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * When a hook of a plugin runs: the filters a plugin gives with
 * hook_method(..., filters=[...]) (base_plugin.HookFilter), read once when
 * the hook is installed and checked on every call before Python is entered,
 * so a hook that does not apply costs no Python call.
 *
 *   {"before": [<filter>, ...], "after": [<filter>, ...]}
 *
 * The filters of a list must all pass; {"type": "or", "filters": [...]}
 * passes when any of its own does. A result_* filter passes only after the
 * method ran. An argument outside the arguments fails any argument_*
 * filter. A filter that cannot be read never passes, and neither does a
 * "condition" (an expression): MZGram does not evaluate expressions, so a
 * hook with one does not run rather than run where it should not; the
 * plugin is told so in its log (hasUnsupported()).
 *
 * Pure Java with org.json; exists in every build and is used by the hook
 * engine of a build with plugins (src/mzgramPlugins/java).
 *
 * Based on exteraless plugins/xposed/HookFilter.java (GPL-3.0): the JSON
 * form, the filter types and how they pass; without the MVEL expressions.
 */

package org.telegram.messenger.mzgram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class MZGramPluginHookFilter {

    public static final String TYPE_OR = "or";
    public static final String TYPE_CONDITION = "condition";

    /** The filters of both phases of one hook. */
    public static final class Parsed {

        public final List<MZGramPluginHookFilter> before;
        public final List<MZGramPluginHookFilter> after;
        private final boolean unsupported;

        Parsed(List<MZGramPluginHookFilter> before, List<MZGramPluginHookFilter> after, boolean unsupported) {
            this.before = Collections.unmodifiableList(before);
            this.after = Collections.unmodifiableList(after);
            this.unsupported = unsupported;
        }

        /** A filter that never passes was given (an expression or an unknown type). */
        public boolean hasUnsupported() {
            return unsupported;
        }
    }

    // Marks a class that could not be found (the map keeps no null)
    private static final class Missing {
    }

    private static final ConcurrentHashMap<String, Class<?>> classes = new ConcurrentHashMap<>();
    private static final int MAX_CLASSES = 512;
    private static final int MAX_FILTERS = 64;
    private static final int MAX_DEPTH = 8;

    // An argument that is not there, unlike an argument that is null
    private static final Object ABSENT = new Object();

    private final String type;
    private Integer index;
    private Object value;
    private String className;
    private List<MZGramPluginHookFilter> any;
    private boolean valid = true;

    private MZGramPluginHookFilter(String type) {
        this.type = type;
    }

    // ---------- reading (once, when the hook is installed) ----------

    /** Never throws: JSON that cannot be read gives no filters. */
    public static Parsed parse(String json) {
        final ArrayList<MZGramPluginHookFilter> before = new ArrayList<>();
        final ArrayList<MZGramPluginHookFilter> after = new ArrayList<>();
        final boolean[] unsupported = {false};
        if (json != null && !json.isEmpty()) {
            try {
                final JSONObject root = new JSONObject(json);
                readList(root.optJSONArray("before"), before, unsupported, 0);
                readList(root.optJSONArray("after"), after, unsupported, 0);
            } catch (Exception e) {
                FileLog.e("MZGramPluginHookFilter: filters that cannot be read: " + e.getMessage());
                before.clear();
                after.clear();
            }
        }
        return new Parsed(before, after, unsupported[0]);
    }

    private static void readList(JSONArray array, List<MZGramPluginHookFilter> out, boolean[] unsupported, int depth) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length() && out.size() < MAX_FILTERS; i++) {
            final JSONObject json = array.optJSONObject(i);
            if (json != null) {
                out.add(read(json, unsupported, depth));
            }
        }
    }

    private static MZGramPluginHookFilter read(JSONObject json, boolean[] unsupported, int depth) {
        final MZGramPluginHookFilter filter = new MZGramPluginHookFilter(json.optString("type", ""));
        if (json.has("index")) {
            filter.index = json.optInt("index", -1);
        }
        if (json.has("value")) {
            filter.value = json.isNull("value") ? null : json.opt("value");
        }
        if (json.has("class") && !json.isNull("class")) {
            filter.className = json.optString("class", null);
        }
        if (TYPE_OR.equals(filter.type)) {
            filter.any = new ArrayList<>();
            if (depth < MAX_DEPTH) {
                readList(json.optJSONArray("filters"), filter.any, unsupported, depth + 1);
            } else {
                filter.valid = false;
            }
        } else if (!isKnown(filter.type)) {
            // An expression ("condition") or a type MZGram does not know
            filter.valid = false;
            unsupported[0] = true;
        }
        return filter;
    }

    private static boolean isKnown(String type) {
        switch (type) {
            case "argument_is_null":
            case "argument_not_null":
            case "argument_is_true":
            case "argument_is_false":
            case "argument_equal":
            case "argument_not_equal":
            case "argument_is_instance_of":
            case "result_is_null":
            case "result_not_null":
            case "result_is_true":
            case "result_is_false":
            case "result_equal":
            case "result_not_equal":
            case "result_is_instance_of":
                return true;
            default:
                return false;
        }
    }

    // ---------- checking (on every call of the hooked method) ----------

    /** All filters pass (an empty list passes). A filter that throws does not pass. */
    public static boolean passes(List<MZGramPluginHookFilter> filters, Object[] args, Object result, boolean afterCall) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (int i = 0; i < filters.size(); i++) {
            boolean pass;
            try {
                pass = filters.get(i).check(args, result, afterCall);
            } catch (Throwable t) {
                pass = false;
            }
            if (!pass) {
                return false;
            }
        }
        return true;
    }

    private boolean check(Object[] args, Object result, boolean afterCall) {
        if (!valid) {
            return false;
        }
        switch (type) {
            case TYPE_OR:
                for (int i = 0; any != null && i < any.size(); i++) {
                    if (any.get(i).check(args, result, afterCall)) {
                        return true;
                    }
                }
                return false;
            case "argument_is_null":
                return argument(args) == null;
            case "argument_not_null": {
                final Object a = argument(args);
                return a != ABSENT && a != null;
            }
            case "argument_is_true":
                return Boolean.TRUE.equals(argument(args));
            case "argument_is_false":
                return Boolean.FALSE.equals(argument(args));
            case "argument_equal": {
                final Object a = argument(args);
                return a != ABSENT && same(a, value);
            }
            case "argument_not_equal": {
                final Object a = argument(args);
                return a != ABSENT && !same(a, value);
            }
            case "argument_is_instance_of": {
                final Object a = argument(args);
                return a != ABSENT && a != null && isInstance(a);
            }
            case "result_is_null":
                return afterCall && result == null;
            case "result_not_null":
                return afterCall && result != null;
            case "result_is_true":
                return afterCall && Boolean.TRUE.equals(result);
            case "result_is_false":
                return afterCall && Boolean.FALSE.equals(result);
            case "result_equal":
                return afterCall && same(result, value);
            case "result_not_equal":
                return afterCall && !same(result, value);
            case "result_is_instance_of":
                return afterCall && result != null && isInstance(result);
            default:
                return false;
        }
    }

    private Object argument(Object[] args) {
        if (index == null || args == null || index < 0 || index >= args.length) {
            return ABSENT;
        }
        return args[index];
    }

    private boolean isInstance(Object object) {
        final Class<?> type = find(className);
        return type != null && type.isInstance(object);
    }

    /**
     * A value from JSON against one from Java: numbers by value whatever
     * their box, a text against a number or a boolean by its text.
     */
    static boolean same(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        if (a instanceof Number && b instanceof Number) {
            final boolean floating = a instanceof Double || a instanceof Float || b instanceof Double || b instanceof Float;
            return floating
                    ? Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0
                    : ((Number) a).longValue() == ((Number) b).longValue();
        }
        if (a instanceof Character || b instanceof Character) {
            return String.valueOf(a).equals(String.valueOf(b));
        }
        if ((a instanceof CharSequence && (b instanceof Number || b instanceof Boolean))
                || (b instanceof CharSequence && (a instanceof Number || a instanceof Boolean))) {
            return String.valueOf(a).equals(String.valueOf(b));
        }
        if (a instanceof CharSequence && b instanceof CharSequence) {
            return a.toString().equals(b.toString());
        }
        return false;
    }

    private static Class<?> find(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        final Class<?> known = classes.get(name);
        if (known != null) {
            return known == Missing.class ? null : known;
        }
        Class<?> found = null;
        try {
            found = Class.forName(name, false, MZGramPluginHookFilter.class.getClassLoader());
        } catch (Throwable ignore) {
        }
        if (classes.size() < MAX_CLASSES) {
            classes.put(name, found != null ? found : Missing.class);
        }
        return found;
    }
}
