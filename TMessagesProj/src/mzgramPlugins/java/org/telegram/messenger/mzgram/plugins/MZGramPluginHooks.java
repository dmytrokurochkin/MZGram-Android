/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The hook engine: lets a running plugin hook methods and constructors of
 * the app (BasePlugin.hook_method, hook_all_methods, hook_all_constructors
 * in base_plugin.py, through mzgram_plugins/hooks.py by name with jclass).
 * The hooks are Xposed callbacks on top of Aliuhook (LSPlant).
 *
 * A hook goes on only when
 *   - plugins may run (MZGramPluginEngine.isOn()) and this plugin is
 *     starting or running;
 *   - MZGramPluginHookGate.check() allows the method: the user granted
 *     "hooks", and the method protects neither the user nor the plugins;
 *   - the native part started. Its first start is marked on disk
 *     (MZGramPluginHookGate.beginEngineStart): if it kills the process
 *     twice in a row, the hooks stay off until the user tries again or
 *     MZGram is updated, and plugins run without them.
 * Every hook is written to the plugin's journal, and all hooks of a plugin
 * come off when it stops (mzgram_plugins/loader.py, unhookAll). What a hook
 * does on each call is in MZGramPluginMethodHook; an error there never
 * reaches the app.
 *
 * XposedBridge.hookMethod itself is hooked here, so that a plugin calling
 * it by name, past this class and its gate, is refused.
 *
 * Every method returns, never throws; the hook methods return JSON:
 *   {"ids": [...], "denied": ["Class.method (reason)", ...], "error": "..." or null,
 *    "warning": "..." or null}
 *
 * Compiled only in a build with -PmzgramPlugins=true (Chaquopy, Aliuhook);
 * mzgram-plugins-rules.pro keeps its name for R8.
 *
 * Based on exteraless plugins/xposed/XposedHooks.java and HookGate.java
 * (GPL-3.0): the facade over XposedBridge, the registry of unhooks by
 * plugin, the lazy start of Aliuhook with a crash mark and the guard on
 * XposedBridge.hookMethod; written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import com.chaquo.python.PyObject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class MZGramPluginHooks {

    /** The most hooks one plugin may have at once. */
    public static final int MAX_HOOKS = 500;

    private static final class Entry {
        final String pluginId;
        final XC_MethodHook.Unhook unhook;
        final MZGramPluginMethodHook hook;

        Entry(String pluginId, XC_MethodHook.Unhook unhook, MZGramPluginMethodHook hook) {
            this.pluginId = pluginId;
            this.unhook = unhook;
            this.hook = hook;
        }
    }

    private static final ConcurrentHashMap<String, Entry> hooks = new ConcurrentHashMap<>();
    private static final AtomicLong nextId = new AtomicLong();

    // Set while this class itself calls XposedBridge.hookMethod
    private static final ThreadLocal<Boolean> installing = new ThreadLocal<>();

    private static final Object startLock = new Object();
    private static volatile boolean tried;
    private static volatile boolean ready;
    private static volatile boolean broken;

    private MZGramPluginHooks() {
    }

    // ---------- the native part ----------

    /** True once the native part started; starts it on the first call. */
    public static boolean isReady() {
        return start();
    }

    private static boolean start() {
        if (tried) {
            return ready;
        }
        synchronized (startLock) {
            if (tried) {
                return ready;
            }
            tried = true;
            if (!MZGramPluginHookGate.beginEngineStart()) {
                broken = true;
                return false;
            }
            boolean ok = false;
            try {
                // The first touch of XposedBridge loads libaliuhook. The ART
                // profile saver would compile hooked methods again later and
                // drop their hooks, so it is off for this process.
                final boolean profileSaverOff = XposedBridge.disableProfileSaver();
                FileLog.d("MZGramPluginHooks: Aliuhook started, profile saver off: " + profileSaverOff);
                ok = true;
                guardBridge();
            } catch (Throwable t) {
                FileLog.e("MZGramPluginHooks: Aliuhook did not start, plugins run without hooks", t);
            }
            MZGramPluginHookGate.endEngineStart(ok);
            ready = ok;
            return ok;
        }
    }

    /**
     * A hook of MZGramPluginSinkGate, past the guard on
     * XposedBridge.hookMethod; null when it can't go on.
     */
    static XC_MethodHook.Unhook hookForGate(Member member, XC_MethodHook hook) {
        installing.set(Boolean.TRUE);
        try {
            return XposedBridge.hookMethod(member, hook);
        } catch (Throwable t) {
            FileLog.e("MZGramPluginHooks: the gate cannot hook " + member, t);
            return null;
        } finally {
            installing.remove();
        }
    }

    private static void guardBridge() {
        try {
            final Method hookMethod = XposedBridge.class.getDeclaredMethod("hookMethod", Member.class, XC_MethodHook.class);
            installing.set(Boolean.TRUE);
            try {
                XposedBridge.hookMethod(hookMethod, new BridgeGuard());
            } finally {
                installing.remove();
            }
        } catch (Throwable t) {
            FileLog.e("MZGramPluginHooks: XposedBridge.hookMethod stays open to plugins", t);
        }
    }

    // Refuses XposedBridge.hookMethod unless this class calls it
    private static final class BridgeGuard extends XC_MethodHook {

        BridgeGuard() {
            super(PRIORITY_HIGHEST);
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (installing.get() == null) {
                param.setThrowable(new SecurityException("plugins hook through BasePlugin.hook_method in MZGram"));
            }
        }
    }

    // ---------- hooking ----------

    /** The class of the app with this name, or null (Python's class loader may not see it). */
    public static Class<?> findClass(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Class.forName(name, false, MZGramPluginHooks.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Hooks one method or constructor. */
    public static String hookMember(String pluginId, Member member, PyObject before, PyObject after, PyObject replace,
                                    int priority, String filtersJson) {
        final Reply reply = new Reply();
        if (member == null) {
            return reply.error("not a method or constructor").json();
        }
        final ArrayList<Member> members = new ArrayList<>();
        members.add(member);
        return hook(pluginId, members, before, after, replace, priority, filtersJson, reply);
    }

    /** Hooks every method of the class with this name (declared in the class itself). */
    public static String hookMethods(String pluginId, Class<?> clazz, String name, PyObject before, PyObject after,
                                     PyObject replace, int priority, String filtersJson) {
        final Reply reply = new Reply();
        if (clazz == null || name == null || name.isEmpty()) {
            return reply.error("no class or method name").json();
        }
        final ArrayList<Member> members = new ArrayList<>();
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    members.add(method);
                }
            }
        } catch (Throwable t) {
            return reply.error("cannot read the methods of " + clazz.getName() + ": " + MZGramPluginMethodHook.firstLine(t)).json();
        }
        if (members.isEmpty()) {
            return reply.error("no method " + name + " in " + clazz.getName()).json();
        }
        return hook(pluginId, members, before, after, replace, priority, filtersJson, reply);
    }

    /** Hooks every constructor of the class. */
    public static String hookConstructors(String pluginId, Class<?> clazz, PyObject before, PyObject after,
                                          PyObject replace, int priority, String filtersJson) {
        final Reply reply = new Reply();
        if (clazz == null) {
            return reply.error("no class").json();
        }
        final ArrayList<Member> members = new ArrayList<>();
        try {
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                members.add(constructor);
            }
        } catch (Throwable t) {
            return reply.error("cannot read the constructors of " + clazz.getName() + ": " + MZGramPluginMethodHook.firstLine(t)).json();
        }
        if (members.isEmpty()) {
            return reply.error("no constructor in " + clazz.getName()).json();
        }
        return hook(pluginId, members, before, after, replace, priority, filtersJson, reply);
    }

    private static String hook(String pluginId, ArrayList<Member> members, PyObject before, PyObject after,
                               PyObject replace, int priority, String filtersJson, Reply reply) {
        try {
            if (!MZGramPluginSettingsStore.isValidId(pluginId)) {
                return reply.error("bad plugin id").json();
            }
            if (MZGramPluginSinkPolicy.refuseForeign(pluginId, "hooks")) {
                return reply.error("a plugin hooks only for itself").json();
            }
            final int state = MZGramPluginEngine.getState(pluginId);
            if (!MZGramPluginEngine.isOn() || state != MZGramPluginEngine.STATE_STARTING && state != MZGramPluginEngine.STATE_RUNNING) {
                return reply.error("the plugin is not running").json();
            }
            if (before == null && after == null && replace == null) {
                return reply.error("the handler has no before_hooked_method, after_hooked_method or replace_hooked_method").json();
            }
            if (!start()) {
                return reply.error(broken
                        ? "hooks are off: MZGram closed twice while starting them; Plugins > Try hooks again"
                        : "hooks are not available on this device").json();
            }
            final MZGramPluginHookFilter.Parsed filters = MZGramPluginHookFilter.parse(filtersJson);
            if (filters.hasUnsupported()) {
                reply.warning = "a filter MZGram does not support (an expression or an unknown type) never passes";
            }
            for (Member member : members) {
                install(pluginId, member, before, after, replace, priority, filters, reply);
            }
        } catch (Throwable t) {
            FileLog.e("MZGramPluginHooks: hooking failed for " + pluginId, t);
            reply.error(MZGramPluginMethodHook.firstLine(t));
        }
        return reply.json();
    }

    private static void install(String pluginId, Member member, PyObject before, PyObject after, PyObject replace,
                                int priority, MZGramPluginHookFilter.Parsed filters, Reply reply) {
        final String name = member.getDeclaringClass().getName() + "." + (member instanceof Constructor ? "<init>" : member.getName());
        if (!(member instanceof Method) && !(member instanceof Constructor)) {
            reply.error("not a method or constructor: " + name);
            return;
        }
        if (Modifier.isAbstract(member.getModifiers())) {
            reply.error("an abstract method has no code to hook: " + name);
            return;
        }
        final int verdict = MZGramPluginHookGate.check(pluginId, member);
        if (verdict != MZGramPluginHookGate.ALLOWED) {
            reply.denied.put(name + " (" + MZGramPluginHookGate.reasonName(verdict) + ")");
            return;
        }
        if (count(pluginId) >= MAX_HOOKS) {
            reply.error("too many hooks (" + MAX_HOOKS + ")");
            return;
        }
        final MZGramPluginHookGate.Counter counter = MZGramPluginHookGate.counter(pluginId, member);
        final MZGramPluginMethodHook hook = new MZGramPluginMethodHook(pluginId, member, before, after, replace, priority, filters, counter);
        final XC_MethodHook.Unhook unhook;
        installing.set(Boolean.TRUE);
        try {
            unhook = XposedBridge.hookMethod(member, hook);
        } catch (Throwable t) {
            MZGramPluginHookGate.release(counter);
            reply.error("cannot hook " + name + ": " + MZGramPluginMethodHook.firstLine(t));
            return;
        } finally {
            installing.remove();
        }
        if (unhook == null) {
            MZGramPluginHookGate.release(counter);
            reply.error("cannot hook " + name);
            return;
        }
        final String id = "h" + nextId.incrementAndGet();
        hooks.put(id, new Entry(pluginId, unhook, hook));
        reply.ids.put(id);
        MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_HOOKED,
                MZGramPluginAuditJournal.CATEGORY_HOOKS, counter.target, MZGramPluginAuditJournal.LEVEL_INFO);
    }

    // ---------- unhooking ----------

    /** Takes off one hook of the plugin; false when it has no such hook. */
    public static boolean unhook(String pluginId, String id) {
        if (pluginId == null || id == null) {
            return false;
        }
        if (MZGramPluginSinkPolicy.refuseForeign(pluginId, "hooks")) {
            return false;
        }
        final Entry entry = hooks.get(id);
        if (entry == null || !entry.pluginId.equals(pluginId) || !hooks.remove(id, entry)) {
            return false;
        }
        close(entry);
        return true;
    }

    /** Takes off every hook of the plugin (it stops); how many there were. */
    public static int unhookAll(String pluginId) {
        if (pluginId == null || MZGramPluginSinkPolicy.refuseForeign(pluginId, "hooks")) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, Entry> item : hooks.entrySet()) {
            final Entry entry = item.getValue();
            if (entry.pluginId.equals(pluginId) && hooks.remove(item.getKey(), entry)) {
                close(entry);
                count++;
            }
        }
        MZGramPluginHookGate.forget(pluginId);
        return count;
    }

    /** How many hooks the plugin has on. */
    public static int count(String pluginId) {
        int count = 0;
        for (Entry entry : hooks.values()) {
            if (entry.pluginId.equals(pluginId)) {
                count++;
            }
        }
        return count;
    }

    private static void close(Entry entry) {
        entry.hook.active = false;
        try {
            entry.unhook.unhook();
        } catch (Throwable t) {
            FileLog.e("MZGramPluginHooks: unhook failed for " + entry.pluginId, t);
        }
        MZGramPluginHookGate.release(entry.hook.counter);
    }

    // ---------- the reply to Python ----------

    private static final class Reply {
        final JSONArray ids = new JSONArray();
        final JSONArray denied = new JSONArray();
        String error;
        String warning;

        Reply error(String text) {
            if (error == null) {
                error = text;
            }
            return this;
        }

        String json() {
            try {
                final JSONObject out = new JSONObject();
                out.put("ids", ids);
                out.put("denied", denied);
                out.put("error", error != null ? error : JSONObject.NULL);
                out.put("warning", warning != null ? warning : JSONObject.NULL);
                return out.toString();
            } catch (Exception e) {
                return "{\"ids\":[],\"denied\":[],\"error\":\"reply\",\"warning\":null}";
            }
        }
    }
}
