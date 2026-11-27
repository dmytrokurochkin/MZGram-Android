package org.mzgram.plugins.xposed;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mvel2.MVEL;
import org.mvel2.optimizers.OptimizerFactory;
import org.telegram.messenger.FileLog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;

final class HookFilter {

    static {
        OptimizerFactory.setDefaultOptimizer(OptimizerFactory.SAFE_REFLECTIVE);
    }

    private static final class Unresolvable {
    }

    private static final ConcurrentHashMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Serializable> MVEL_CACHE = new ConcurrentHashMap<>();

    private static final Object MISSING = new Object();

    static final class Parsed {
        final List<HookFilter> before;
        final List<HookFilter> after;

        Parsed(List<HookFilter> before, List<HookFilter> after) {
            this.before = before;
            this.after = after;
        }
    }

    private String type = "";
    private Integer index;
    private Object value;
    private String className;
    private String expr;
    private Map<String, Object> objectVars;
    private List<HookFilter> orFilters;
    private boolean valid = true;

    private HookFilter() {
    }


    static Parsed parse(String filtersJson) {
        List<HookFilter> before = new ArrayList<>();
        List<HookFilter> after = new ArrayList<>();
        if (filtersJson != null && !filtersJson.isEmpty()) {
            try {
                JSONObject root = new JSONObject(filtersJson);
                parsePhase(root.optJSONArray("before"), before);
                parsePhase(root.optJSONArray("after"), after);
            } catch (Throwable t) {
                FileLog.e("HookFilter: invalid filtersJson: " + filtersJson, t);
                before.clear();
                after.clear();
            }
        }
        return new Parsed(before, after);
    }

    private static void parsePhase(JSONArray array, List<HookFilter> out) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json != null) {
                out.add(parseOne(json));
            }
        }
    }

    private static HookFilter parseOne(JSONObject json) {
        HookFilter f = new HookFilter();
        f.type = json.optString("type", "");
        if (json.has("index")) {
            f.index = json.optInt("index");
        }
        if (json.has("value")) {
            f.value = jsonToJava(json.opt("value"));
        }
        if (json.has("class")) {
            f.className = org.mzgram.plugins.JsonUtils.optStringOrNull(json, "class");
        }
        if (json.has("expression")) {
            f.expr = org.mzgram.plugins.JsonUtils.optStringOrNull(json, "expression");
        } else if (json.has("expr")) {
            f.expr = org.mzgram.plugins.JsonUtils.optStringOrNull(json, "expr");
        }
        JSONObject object = json.optJSONObject("object");
        if (object != null) {
            f.objectVars = toMap(object);
        }
        if ("or".equals(f.type)) {
            f.orFilters = new ArrayList<>();
            JSONArray subs = json.optJSONArray("filters");
            if (subs != null) {
                for (int i = 0; i < subs.length(); i++) {
                    JSONObject sub = subs.optJSONObject(i);
                    if (sub != null) {
                        f.orFilters.add(parseOne(sub));
                    }
                }
            }
        } else if ("condition".equals(f.type)) {
            if (f.expr == null || f.expr.isEmpty()) {
                FileLog.e("HookFilter: condition without expr, disabled");
                f.valid = false;
            } else {
                try {
                    compiled(f.expr);
                } catch (Throwable t) {
                    FileLog.e("HookFilter: bad MVEL expression: " + f.expr, t);
                    f.valid = false;
                }
            }
        } else if (!isKnownType(f.type)) {
            FileLog.e("HookFilter: unknown filter type '" + f.type + "', disabled");
            f.valid = false;
        }
        return f;
    }

    private static boolean isKnownType(String type) {
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


    static boolean evaluateAll(List<HookFilter> filters, XC_MethodHook.MethodHookParam param,
                               boolean afterPhase) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (HookFilter filter : filters) {
            boolean pass;
            try {
                pass = filter.evaluate(param, afterPhase);
            } catch (Throwable t) {
                FileLog.e("HookFilter: evaluation failed, type=" + filter.type, t);
                pass = false;
            }
            if (!pass) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluate(XC_MethodHook.MethodHookParam param, boolean afterPhase) {
        if (!valid) {
            return false;
        }
        switch (type) {
            case "or":
                if (orFilters != null) {
                    for (HookFilter sub : orFilters) {
                        if (sub.evaluate(param, afterPhase)) {
                            return true;
                        }
                    }
                }
                return false;
            case "condition":
                return evalCondition(param, afterPhase);
            case "argument_is_null":
                return arg(param) == null;
            case "argument_not_null": {
                Object a = arg(param);
                return a != MISSING && a != null;
            }
            case "argument_is_true": {
                Object a = arg(param);
                return a instanceof Boolean && (Boolean) a;
            }
            case "argument_is_false": {
                Object a = arg(param);
                return a instanceof Boolean && !(Boolean) a;
            }
            case "argument_equal": {
                Object a = arg(param);
                return a != MISSING && valuesEqual(a, value);
            }
            case "argument_not_equal": {
                Object a = arg(param);
                return a != MISSING && !valuesEqual(a, value);
            }
            case "argument_is_instance_of": {
                Object a = arg(param);
                return a != MISSING && a != null && isInstanceOf(a);
            }
            case "result_is_null":
            case "result_not_null":
            case "result_is_true":
            case "result_is_false":
            case "result_equal":
            case "result_not_equal":
            case "result_is_instance_of":
                return afterPhase && evalResult(param);
            default:
                return false;
        }
    }

    private boolean evalResult(XC_MethodHook.MethodHookParam param) {
        Object result = param.getResult();
        switch (type) {
            case "result_is_null":
                return result == null;
            case "result_not_null":
                return result != null;
            case "result_is_true":
                return result instanceof Boolean && (Boolean) result;
            case "result_is_false":
                return result instanceof Boolean && !(Boolean) result;
            case "result_equal":
                return valuesEqual(result, value);
            case "result_not_equal":
                return !valuesEqual(result, value);
            case "result_is_instance_of":
                return result != null && isInstanceOf(result);
            default:
                return false;
        }
    }

    private static final ThreadLocal<HashMap<String, Object>> CONDITION_VARS = new ThreadLocal<>();

    private boolean evalCondition(XC_MethodHook.MethodHookParam param, boolean afterPhase) {
        HashMap<String, Object> vars = CONDITION_VARS.get();
        boolean pooled = vars != null;
        if (pooled) {
            CONDITION_VARS.set(null);
            vars.clear();
        } else {
            vars = new HashMap<>();
        }
        try {
            if (objectVars != null) {
                vars.putAll(objectVars);
            }
            vars.put("param", param);
            vars.put("thisObject", param.thisObject);
            vars.put("args", param.args);
            vars.put("result", afterPhase ? param.getResult() : null);
            return truthy(MVEL.executeExpression(compiled(expr), param.thisObject, vars));
        } finally {
            vars.clear();
            CONDITION_VARS.set(vars);
        }
    }

    private Object arg(XC_MethodHook.MethodHookParam param) {
        if (index == null) {
            return MISSING;
        }
        Object[] args = param.args;
        if (args == null || index < 0 || index >= args.length) {
            return MISSING;
        }
        return args[index];
    }

    private boolean isInstanceOf(Object obj) {
        Class<?> cls = resolveClass(className);
        return cls != null && cls.isInstance(obj);
    }


    private static Serializable compiled(String expression) {
        Serializable s = MVEL_CACHE.get(expression);
        if (s == null) {
            s = MVEL.compileExpression(expression);
            MVEL_CACHE.put(expression, s);
        }
        return s;
    }

    private static boolean truthy(Object r) {
        if (r == null) {
            return false;
        }
        if (r instanceof Boolean) {
            return (Boolean) r;
        }
        if (r instanceof Number) {
            return ((Number) r).doubleValue() != 0;
        }
        String s = String.valueOf(r).trim();
        return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
    }

    private static boolean valuesEqual(Object a, Object b) {
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
            boolean floating = a instanceof Double || a instanceof Float
                    || b instanceof Double || b instanceof Float;
            return floating
                    ? Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0
                    : ((Number) a).longValue() == ((Number) b).longValue();
        }
        if ((a instanceof String && (b instanceof Number || b instanceof Boolean))
                || (b instanceof String && (a instanceof Number || a instanceof Boolean))) {
            return String.valueOf(a).equals(String.valueOf(b));
        }
        return false;
    }

    private static Class<?> resolveClass(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Class<?> cached = CLASS_CACHE.get(name);
        if (cached != null) {
            return cached == Unresolvable.class ? null : cached;
        }
        Class<?> loaded = tryLoadClass(name);
        CLASS_CACHE.put(name, loaded != null ? loaded : Unresolvable.class);
        if (loaded == null) {
            FileLog.e("HookFilter: cannot resolve class " + name);
        }
        return loaded;
    }

    private static Class<?> tryLoadClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                return cl.loadClass(name);
            }
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader cl = HookFilter.class.getClassLoader();
            if (cl != null) {
                return cl.loadClass(name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---------- JSON -> Java ----------

    private static Object jsonToJava(Object o) {
        if (o == null || JSONObject.NULL.equals(o)) {
            return null;
        }
        if (o instanceof JSONObject) {
            return toMap((JSONObject) o);
        }
        if (o instanceof JSONArray) {
            return toList((JSONArray) o);
        }
        return o; // String / Integer / Long / Double / Boolean
    }

    private static Map<String, Object> toMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        for (Iterator<String> it = json.keys(); it.hasNext(); ) {
            String key = it.next();
            map.put(key, jsonToJava(json.opt(key)));
        }
        return map;
    }

    private static List<Object> toList(JSONArray array) {
        List<Object> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            list.add(jsonToJava(array.opt(i)));
        }
        return list;
    }
}
