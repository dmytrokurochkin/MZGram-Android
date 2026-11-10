/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The rules of the sink gates of plugins: what a plugin may reach where
 * the harm is done (the network, other processes, native code, Java classes,
 * the files and settings of the app, requests to Telegram), and the record
 * of it. The gates themselves are mzgram_plugins/sink_gate.py (Python: an
 * audit hook, the jclass and import checks, threads that keep their plugin)
 * and MZGramPluginSinkGate (Java hooks on the sinks, only in a build with
 * plugins); both ask this class.
 *
 * - The network needs "network". Without Java (tests on a computer) it
 *   stays closed, as before. An allowed connection is written to the
 *   activity log with its host only, never the path or the data.
 * - Other processes and the classes of the plugin engine itself are never
 *   available to plugins (RULE_NEVER): with them a plugin would change its
 *   permissions or get around the gates.
 * - Loading code (dex), raw memory and the key store need "hooks" (full
 *   control), the network classes of Java need "network".
 * - Requests to Telegram made by plugin code itself: reading needs
 *   "messages.read", changing "messages.send", the account, sign-in and
 *   payments "hooks" (requestKey).
 * - A plugin may not act for another plugin (its settings, menus, hooks,
 *   log): refuseForeign.
 * - Its own folder (dataDirOf) is free; the private storage of the app
 *   needs "hooks", other files "files".
 *
 * The honest limit: plugins run in the app's process; code written to get
 * around the gates can, and with "hooks" or "native" a plugin is not held
 * at all. The gates stop what ordinary plugin code would do by itself and
 * make what it does visible.
 *
 * Based on exteraless plugins/PluginSinkGate.java and
 * extera_utils/audit_gate.py (GPL-3.0): the lists of sinks and classes, the
 * permission of a request by its name and the remote address check;
 * written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MZGramPluginSinkPolicy {

    public static final int RULE_FREE = 0;
    public static final int RULE_NETWORK = 1;
    public static final int RULE_HOOKS = 2;
    public static final int RULE_NEVER = 3;

    /** The folder of the plugins' own data, inside the files of the app. */
    public static final String DATA_DIR = "mzgram_plugin_data";

    // A name ending with "." is a package (and what is under it), one ending
    // with "*" a prefix; any other is one class with its inner classes.
    private static final String[] NEVER_CLASSES = {
            "java.lang.ProcessBuilder",
            "java.lang.ProcessImpl",
            "java.lang.UNIXProcess",
            "java.lang.ProcessManager",
            "org.telegram.messenger.mzgram.plugins.",
            "org.telegram.ui.mzgram.MZGramPlugin*",
    };

    private static final String[] HOOKS_CLASSES = {
            "dalvik.system.DexClassLoader",
            "dalvik.system.PathClassLoader",
            "dalvik.system.InMemoryDexClassLoader",
            "dalvik.system.BaseDexClassLoader",
            "dalvik.system.DelegateLastClassLoader",
            "dalvik.system.DexFile",
            "dalvik.system.VMRuntime",
            "dalvik.system.VMDebug",
            "de.robv.android.xposed.",
            "com.aliucord.",
            "org.lsposed.",
            "sun.misc.Unsafe",
            "jdk.internal.misc.Unsafe",
            "java.security.KeyStore",
            "android.security.keystore.",
    };

    private static final String[] NETWORK_CLASSES = {
            "java.net.URL",
            "java.net.URLConnection",
            "java.net.HttpURLConnection",
            "java.net.JarURLConnection",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.DatagramSocket",
            "java.net.MulticastSocket",
            "java.net.InetAddress",
            "java.net.Inet4Address",
            "java.net.Inet6Address",
            "java.nio.channels.SocketChannel",
            "java.nio.channels.ServerSocketChannel",
            "java.nio.channels.DatagramChannel",
            "javax.net.",
            "android.net.http.",
            "android.net.Network",
            "android.app.DownloadManager",
            "com.android.okhttp.",
            "okhttp3.",
            "okio.",
            "org.apache.http.",
    };

    private static final String[] ACCOUNT_NAMESPACES = {"TL_auth_", "TL_account_"};
    private static final String[] PAYMENT_NAMESPACES = {"TL_payments_", "TL_stars_"};
    private static final Set<String> SENSITIVE_ACCOUNT_READS = new HashSet<>(Arrays.asList(
            "getAuthorizations", "getPassword", "getTmpPassword", "getAuthorizationForm",
            "getWebAuthorizations", "getAllSecureValues", "getSecureValue", "getPasswordSettings"));
    private static final Set<String> ROUTINE_ACCOUNT_WRITES = new HashSet<>(Arrays.asList(
            "updateStatus", "updateNotifySettings", "updateDeviceLocked"));
    private static final String[] READ_VERBS = {"get", "search", "check", "resolve", "read", "fetch"};

    // Distinct (plugin, action) blocks written to FileLog, at most
    private static final int MAX_LOGGED = 500;
    private static final Set<String> logged = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MZGramPluginSinkPolicy() {
    }

    // ---------- classes ----------

    /** "[Ljava.net.URL;" and "java/net/URL" are java.net.URL. */
    static String className(String name) {
        if (name == null) {
            return "";
        }
        String out = name.trim().replace('/', '.');
        while (out.startsWith("[")) {
            out = out.substring(1);
        }
        if (out.startsWith("L") && out.endsWith(";")) {
            out = out.substring(1, out.length() - 1);
        }
        return out;
    }

    static boolean matches(String name, String pattern) {
        if (pattern.endsWith(".")) {
            return name.startsWith(pattern);
        }
        if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return name.equals(pattern) || name.startsWith(pattern + "$");
    }

    private static boolean matchesAny(String name, String[] patterns) {
        for (String pattern : patterns) {
            if (matches(name, pattern)) {
                return true;
            }
        }
        return false;
    }

    /** RULE_FREE, RULE_NETWORK, RULE_HOOKS or RULE_NEVER for a class name. */
    public static int classRule(String name) {
        final String clean = className(name);
        if (clean.isEmpty()) {
            return RULE_FREE;
        }
        if (matchesAny(clean, NEVER_CLASSES)) {
            return RULE_NEVER;
        }
        if (matchesAny(clean, HOOKS_CLASSES)) {
            return RULE_HOOKS;
        }
        if (matchesAny(clean, NETWORK_CLASSES)) {
            return RULE_NETWORK;
        }
        return RULE_FREE;
    }

    /**
     * Whether the plugin may have the class (jclass, an import from Java,
     * Class.forName); a refusal is logged. Without a plugin, true.
     */
    public static boolean allowClass(String pluginId, String name) {
        if (pluginId == null) {
            return true;
        }
        final String clean = className(name);
        switch (classRule(clean)) {
            case RULE_NEVER:
                blocked(pluginId, "class " + clean);
                return false;
            case RULE_HOOKS:
                return MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.HOOKS, "class " + clean);
            case RULE_NETWORK:
                return MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.NETWORK, "class " + clean);
            default:
                return true;
        }
    }

    // ---------- the network ----------

    /** An address that leads out of the device, not to local markup. */
    public static boolean isRemoteUrl(String url) {
        if (url == null) {
            return false;
        }
        final String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("ws://") || lower.startsWith("wss://")
                || lower.startsWith("ftp://") || lower.startsWith("rtsp://")
                || lower.startsWith("rtmp://");
    }

    /** "host" or "host:port" of an address, without the path and the data; "" when there is none. */
    public static String hostOf(String url) {
        if (url == null) {
            return "";
        }
        String rest = url.trim();
        final int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int end = rest.length();
        for (char stop : new char[]{'/', '?', '#'}) {
            final int at = rest.indexOf(stop);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        rest = rest.substring(0, end);
        final int user = rest.lastIndexOf('@');
        if (user >= 0) {
            rest = rest.substring(user + 1);
        }
        return rest.toLowerCase(Locale.ROOT);
    }

    /**
     * The check of a connection: true when the plugin has "network". what:
     * the action ("connect"); target: the host, or host:port. An allowed
     * connection is written to the activity log (host only).
     */
    public static boolean allowNetwork(String pluginId, String what, String target) {
        if (pluginId == null) {
            return true;
        }
        final String host = target == null ? "" : target;
        final String action = what == null ? "" : what;
        if (!MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.NETWORK, host.isEmpty() ? action : action + " " + host)) {
            return false;
        }
        MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_NETWORK,
                MZGramPluginAuditJournal.CATEGORY_RUNTIME, host.isEmpty() ? action : host, MZGramPluginAuditJournal.LEVEL_INFO);
        return true;
    }

    // ---------- requests to Telegram ----------

    /** TL_messages_getHistory for org.telegram.tgnet.TLRPC$TL_messages_getHistory. */
    public static String requestName(Object request) {
        if (request == null) {
            return "";
        }
        String name = request.getClass().getName();
        name = name.substring(name.lastIndexOf('.') + 1);
        final int inner = name.lastIndexOf('$');
        return inner >= 0 ? name.substring(inner + 1) : name;
    }

    /** The permission a request needs, by its name. */
    public static String requestKey(String name) {
        if (name == null) {
            return MZGramPluginGrants.MESSAGES_SEND;
        }
        for (String namespace : ACCOUNT_NAMESPACES) {
            if (name.startsWith(namespace)) {
                if ("TL_auth_".equals(namespace)) {
                    return MZGramPluginGrants.HOOKS;
                }
                final String method = name.substring(namespace.length());
                if (ROUTINE_ACCOUNT_WRITES.contains(method)) {
                    return MZGramPluginGrants.MESSAGES_SEND;
                }
                if (method.startsWith("get") && !SENSITIVE_ACCOUNT_READS.contains(method)) {
                    return MZGramPluginGrants.MESSAGES_READ;
                }
                return MZGramPluginGrants.HOOKS;
            }
        }
        for (String namespace : PAYMENT_NAMESPACES) {
            if (name.startsWith(namespace)) {
                return isReadVerb(name.substring(namespace.length())) ? MZGramPluginGrants.MESSAGES_READ : MZGramPluginGrants.HOOKS;
            }
        }
        if (name.endsWith("_acceptUrlAuth") || name.endsWith("_requestUrlAuth") || name.endsWith("_editCreator")) {
            return MZGramPluginGrants.HOOKS;
        }
        final int separator = name.indexOf('_', name.startsWith("TL_") ? 3 : 0);
        final String verb = separator < 0 ? name : name.substring(separator + 1);
        return isReadVerb(verb) ? MZGramPluginGrants.MESSAGES_READ : MZGramPluginGrants.MESSAGES_SEND;
    }

    private static boolean isReadVerb(String method) {
        for (String verb : READ_VERBS) {
            if (method.startsWith(verb)) {
                return true;
            }
        }
        return false;
    }

    // ---------- one plugin for another ----------

    /**
     * True (and logged) when plugin code on this thread acts for another
     * plugin: its settings, menu items, hooks or log. Outside plugin code
     * false: MZGram's own code acts for every plugin.
     */
    public static boolean refuseForeign(String pluginId, String what) {
        final String current = MZGramPluginPermissions.current();
        if (current == null || pluginId != null && MZGramPluginPermissions.isOnThread(pluginId)) {
            return false;
        }
        blocked(current, what + " (" + pluginId + ")");
        return true;
    }

    // ---------- the record ----------

    /** Something never available to plugins was refused. */
    public static void blocked(String pluginId, String what) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId)) {
            return;
        }
        final String action = what == null ? "" : what;
        if (logged.size() < MAX_LOGGED && logged.add(pluginId + '\u0000' + action)) {
            FileLog.w("MZGramPluginSinkPolicy: blocked " + action + " for " + pluginId + ", never available to plugins");
        }
        MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_SINK_BLOCKED,
                MZGramPluginAuditJournal.CATEGORY_RUNTIME, action, MZGramPluginAuditJournal.LEVEL_DENIED);
    }

    // ---------- files ----------

    /** The plugin's own folder (not created here), or null for an id that is not valid. */
    public static File dataDirOf(String pluginId) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId)) {
            return null;
        }
        return new File(new File(ApplicationLoader.getFilesDirFixed(), DATA_DIR), pluginId);
    }

    /**
     * The folders the Python gate tells apart, as JSON: "private" (the
     * storage of the app), "plugins" (the plugin files), "plugin_data" (the
     * root of the plugins' own folders).
     */
    public static String paths() {
        try {
            final Context context = ApplicationLoader.applicationContext;
            final File files = ApplicationLoader.getFilesDirFixed();
            final JSONArray own = new JSONArray();
            final JSONObject out = new JSONObject();
            if (context != null) {
                add(own, context.getApplicationInfo().dataDir);
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        add(own, context.createDeviceProtectedStorageContext().getDataDir());
                    } catch (Throwable ignore) {
                    }
                }
                try {
                    add(own, parentOf(context.getExternalFilesDir(null)));
                    add(own, parentOf(context.getExternalCacheDir()));
                    add(own, context.getObbDir());
                } catch (Throwable ignore) {
                }
            }
            add(own, files);
            out.put("private", own);
            out.put("plugins", new File(files, "mzgram_plugins").getAbsolutePath());
            out.put("plugin_data", new File(files, DATA_DIR).getAbsolutePath());
            return out.toString();
        } catch (Throwable t) {
            FileLog.e("MZGramPluginSinkPolicy: paths", t);
            return "{}";
        }
    }

    private static File parentOf(File file) {
        return file != null ? file.getParentFile() : null;
    }

    private static void add(JSONArray list, Object path) {
        if (path instanceof File) {
            list.put(((File) path).getAbsolutePath());
        } else if (path instanceof String && !((String) path).isEmpty()) {
            list.put(path);
        }
    }
}
