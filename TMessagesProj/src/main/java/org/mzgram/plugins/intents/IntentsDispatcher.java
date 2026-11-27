package org.mzgram.plugins.intents;

import android.content.Intent;
import android.net.Uri;

import com.chaquo.python.PyObject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.mzgram.plugins.PluginsController;
import org.mzgram.plugins.PluginsWatchdog;

public final class IntentsDispatcher {

    private static final class Handler {
        final String id;
        final String pluginId;
        final boolean before;
        final int priority;
        final PyObject callback;
        final List<String> schemes;
        final List<String> hosts;
        final String pathTemplate;
        final List<String> queryArgs;
        final List<String> categories;
        final List<Integer> flags;

        Handler(String id, String pluginId, boolean before, int priority, PyObject callback,
                List<String> schemes, List<String> hosts, String pathTemplate,
                List<String> queryArgs, List<String> categories, List<Integer> flags) {
            this.id = id;
            this.pluginId = pluginId;
            this.before = before;
            this.priority = priority;
            this.callback = callback;
            this.schemes = schemes;
            this.hosts = hosts;
            this.pathTemplate = pathTemplate;
            this.queryArgs = queryArgs;
            this.categories = categories;
            this.flags = flags;
        }
    }

    private static final List<Handler> handlers = new CopyOnWriteArrayList<>();

    private IntentsDispatcher() {
    }


    public static String registerHandler(String pluginId, boolean before, String filtersJson,
                                         int priority, PyObject callback) {
        if (pluginId == null || callback == null) {
            return null;
        }
        List<String> schemes = new ArrayList<>();
        List<String> hosts = new ArrayList<>();
        List<String> queryArgs = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        String pathTemplate = null;
        int effectivePriority = priority;
        if (filtersJson != null && !filtersJson.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(filtersJson);
                putStrings(obj.optJSONArray("schemes"), schemes, true);
                putStrings(obj.optJSONArray("hosts"), hosts, true);
                String template = org.mzgram.plugins.JsonUtils.optStringOrNull(obj, "path_template");
                if (template != null && !template.isEmpty()) {
                    pathTemplate = template;
                }
                putStrings(obj.optJSONArray("query_args"), queryArgs, false);
                putStrings(obj.optJSONArray("categories"), categories, false);
                JSONArray flagsArray = obj.optJSONArray("flags");
                if (flagsArray != null) {
                    for (int i = 0; i < flagsArray.length(); i++) {
                        flags.add(flagsArray.optInt(i));
                    }
                }
                if (obj.has("priority")) {
                    effectivePriority = obj.optInt("priority", priority);
                }
            } catch (Throwable t) {
                FileLog.e("IntentsDispatcher: bad filters json from " + pluginId, t);
                return null;
            }
        }
        Handler handler = new Handler(UUID.randomUUID().toString(), pluginId, before,
                effectivePriority, callback, schemes, hosts, pathTemplate, queryArgs, categories, flags);
        int index = 0;
        while (index < handlers.size() && handlers.get(index).priority >= effectivePriority) {
            index++;
        }
        handlers.add(index, handler);
        return handler.id;
    }

    public static void unregisterHandler(String pluginId, String handlerId) {
        if (pluginId == null || handlerId == null) {
            return;
        }
        handlers.removeIf(h -> h.id.equals(handlerId) && h.pluginId.equals(pluginId));
    }

    public static void unregisterAllForPlugin(String pluginId) {
        if (pluginId == null) {
            return;
        }
        handlers.removeIf(h -> h.pluginId.equals(pluginId));
    }


    public static boolean hasHandlers() {
        return !handlers.isEmpty();
    }

    public static boolean hasBeforeHandlers() {
        for (Handler h : handlers) {
            if (h.before) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAfterHandlers() {
        for (Handler h : handlers) {
            if (!h.before) {
                return true;
            }
        }
        return false;
    }


    public static boolean dispatchBefore(Map<String, Object> context) {
        if (handlers.isEmpty() || !PluginsController.getInstance().isEngineEnabled()) {
            return false;
        }
        for (Handler handler : handlers) {
            if (!handler.before || !matches(handler, context) || org.telegram.ui.Wallet.WalletPluginFreeze.isFrozen()) {
                continue;
            }
            PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
            watchdog.notePluginEnter(handler.pluginId);
            try {
                PyObject result = handler.callback.callAttr("__call__", context);
                if (result != null && result.toBoolean()) {
                    return true;
                }
            } catch (Throwable t) {
                FileLog.e("IntentsDispatcher: before-handler failed (" + handler.pluginId + ")", t);
                watchdog.handlePluginError(handler.pluginId, t);
            } finally {
                watchdog.notePluginExit(handler.pluginId);
            }
        }
        return false;
    }

    public static void dispatchAfter(Map<String, Object> context) {
        if (handlers.isEmpty() || !PluginsController.getInstance().isEngineEnabled()) {
            return;
        }
        for (Handler handler : handlers) {
            if (handler.before || !matches(handler, context) || org.telegram.ui.Wallet.WalletPluginFreeze.isFrozen()) {
                continue;
            }
            PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
            watchdog.notePluginEnter(handler.pluginId);
            try {
                handler.callback.callAttr("__call__", context);
            } catch (Throwable t) {
                FileLog.e("IntentsDispatcher: after-handler failed (" + handler.pluginId + ")", t);
                watchdog.handlePluginError(handler.pluginId, t);
            } finally {
                watchdog.notePluginExit(handler.pluginId);
            }
        }
    }


    public static Map<String, Object> buildIntentContext(Intent intent, int account, boolean fromIntent) {
        Map<String, Object> context = new HashMap<>();
        context.put("from_intent", fromIntent);
        context.put("account", account);
        if (intent == null) {
            return context;
        }
        context.put("intent", intent);
        context.put("action", intent.getAction());
        context.put("flags", intent.getFlags());
        try {
            String type = intent.getType();
            if (type != null) {
                context.put("type", type);
            }
        } catch (Throwable t) {
            FileLog.e("IntentsDispatcher: getType failed", t);
        }
        List<String> categories = new ArrayList<>();
        try {
            Set<String> intentCategories = intent.getCategories();
            if (intentCategories != null) {
                categories.addAll(intentCategories);
            }
        } catch (Throwable t) {
            FileLog.e("IntentsDispatcher: getCategories failed", t);
        }
        context.put("categories", categories);
        Uri data = intent.getData();
        context.put("data_string", data != null ? data.toString() : null);
        fillUriParts(context, data);
        return context;
    }

    public static Map<String, Object> buildUriContext(Uri uri, int account, boolean fromIntent) {
        Map<String, Object> context = new HashMap<>();
        context.put("from_intent", fromIntent);
        context.put("account", account);
        context.put("action", Intent.ACTION_VIEW);
        context.put("flags", 0);
        context.put("categories", new ArrayList<String>());
        context.put("data_string", uri != null ? uri.toString() : null);
        fillUriParts(context, uri);
        return context;
    }

    private static void fillUriParts(Map<String, Object> context, Uri uri) {
        if (uri == null) {
            return;
        }
        uri = normalizeOpaque(uri);
        try {
            context.put("scheme", uri.getScheme());
            context.put("host", uri.getHost());
            context.put("path", uri.getPath());
        } catch (Throwable t) {
            FileLog.e("IntentsDispatcher: uri parts failed", t);
        }
        Map<String, String> query = new HashMap<>();
        try {
            Set<String> names = uri.getQueryParameterNames();
            for (String name : names) {
                query.put(name, uri.getQueryParameter(name));
            }
        } catch (Throwable ignored) {
        }
        context.put("query", query);
    }

    private static Uri normalizeOpaque(Uri uri) {
        try {
            if (uri.isOpaque() && uri.getScheme() != null) {
                String fixed = uri.toString().replaceFirst(
                        "^" + Pattern.quote(uri.getScheme()) + ":(?!//)", uri.getScheme() + "://");
                return Uri.parse(fixed);
            }
        } catch (Throwable t) {
            FileLog.e("IntentsDispatcher: uri normalize failed", t);
        }
        return uri;
    }


    private static boolean matches(Handler handler, Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        String scheme = asString(context.get("scheme"));
        String host = asString(context.get("host"));
        String path = asString(context.get("path"));

        if (!handler.schemes.isEmpty()
                && (scheme == null || !handler.schemes.contains(scheme.toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (!handler.hosts.isEmpty()
                && (host == null || !handler.hosts.contains(host.toLowerCase(Locale.ROOT)))) {
            return false;
        }

        String pathTemplate = handler.pathTemplate;
        String hostTemplate = null;
        if (pathTemplate != null) {
            int slash = pathTemplate.indexOf('/');
            if (slash > 0) {
                hostTemplate = pathTemplate.substring(0, slash);
                pathTemplate = pathTemplate.substring(slash);
            }
        }
        Map<String, String> extracted = new HashMap<>();
        if (hostTemplate != null && !matchTemplate(hostTemplate, host, "\\.", extracted)) {
            return false;
        }
        if (pathTemplate != null && !matchTemplate(pathTemplate, path, "/", extracted)) {
            return false;
        }

        if (!handler.queryArgs.isEmpty()) {
            Object queryObj = context.get("query");
            if (!(queryObj instanceof Map)) {
                return false;
            }
            Map<?, ?> query = (Map<?, ?>) queryObj;
            for (String name : handler.queryArgs) {
                if (!query.containsKey(name)) {
                    return false;
                }
            }
        }
        if (!handler.categories.isEmpty()) {
            Object categoriesObj = context.get("categories");
            if (!(categoriesObj instanceof List)) {
                return false;
            }
            List<?> contextCategories = (List<?>) categoriesObj;
            for (String category : handler.categories) {
                if (!contextCategories.contains(category)) {
                    return false;
                }
            }
        }
        if (!handler.flags.isEmpty()) {
            Object flagsObj = context.get("flags");
            int contextFlags = flagsObj instanceof Number ? ((Number) flagsObj).intValue() : 0;
            for (int flag : handler.flags) {
                if ((contextFlags & flag) != flag) {
                    return false;
                }
            }
        }

        if (!extracted.isEmpty()) {
            context.putAll(extracted);
            Object pathVars = context.get("path_vars");
            Map<String, Object> varsMap = pathVars instanceof Map
                    ? (Map<String, Object>) pathVars : new HashMap<>();
            varsMap.putAll(extracted);
            context.put("path_vars", varsMap);
        }
        return true;
    }

    private static boolean matchTemplate(String template, String value, String separatorRegex,
                                         Map<String, String> extracted) {
        if (value == null) {
            return false;
        }
        String[] templateSegments = splitSegments(template, separatorRegex);
        String[] valueSegments = splitSegments(value, separatorRegex);
        if (templateSegments.length != valueSegments.length) {
            return false;
        }
        for (int i = 0; i < templateSegments.length; i++) {
            String t = templateSegments[i];
            if (t.length() > 2 && t.startsWith("{") && t.endsWith("}")) {
                if (valueSegments[i].isEmpty()) {
                    return false;
                }
                extracted.put(t.substring(1, t.length() - 1), Uri.decode(valueSegments[i]));
            } else if (!t.equalsIgnoreCase(valueSegments[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] splitSegments(String s, String separatorRegex) {
        String stripped = s;
        if ("/".equals(separatorRegex)) {
            while (stripped.startsWith("/")) {
                stripped = stripped.substring(1);
            }
            while (stripped.endsWith("/")) {
                stripped = stripped.substring(0, stripped.length() - 1);
            }
        }
        if (stripped.isEmpty()) {
            return new String[0];
        }
        return stripped.split(separatorRegex);
    }

    private static String asString(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static void putStrings(JSONArray array, List<String> out, boolean lowerCase) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            String s = org.mzgram.plugins.JsonUtils.optStringOrNull(array, i);
            if (s != null && !s.isEmpty()) {
                out.add(lowerCase ? s.toLowerCase(Locale.ROOT) : s);
            }
        }
    }
}
