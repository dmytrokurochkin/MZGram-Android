/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The permission model of plugins: what a plugin may do, asked where it
 * does it. The keys and what was granted live in MZGramPluginGrants; this
 * class answers the gates.
 *
 * - has(pluginId, key): quiet, for screens and for code that only chooses.
 * - check(pluginId, key, what): the check at a call site. A refusal never
 *   breaks the plugin (the caller gives back a safe value), it is written to
 *   the activity log (repeats counted) and FileLog (once per plugin, key and
 *   action), and the user is told once per plugin and key in a notice with
 *   a button that grants it (MZGramPluginDenialNotice).
 * - The plugin whose code runs on this thread: enter(pluginId) and
 *   exit(previous) are set where control passes to plugin code (loading,
 *   app events, menu and settings callbacks from mzgram_plugins, hooks from
 *   MZGramPluginMethodHook). checkCurrent(key, what) is the check for code
 *   of MZGram that a plugin can reach through Java: without a plugin on the
 *   thread it allows, MZGram's own code is never asked. isOnThread(pluginId) tells
 *   whether the code of that plugin is anywhere on the thread (nested
 *   hooks), for the refusal of one plugin acting for another. Threads a
 *   plugin starts in Python keep its mark (mzgram_plugins/sink_gate.py);
 *   for Java callbacks into Python the sink gates find the plugin by its
 *   Python frames (MZGramPluginSinkGate).
 * - "hooks" and "native" are dangerous: with them a plugin can get around
 *   every other check, so the screens ask once more before granting them.
 * - Plugin code may not change permissions: the writes of
 *   MZGramPluginGrants refuse while a plugin's code runs on the thread.
 *
 * The honest limit: plugins run in the app's process and reach Java through
 * Chaquopy, so code made to get around the checks can; the model makes what
 * a plugin does visible and stops accidental harm.
 *
 * Based on exteraless plugins/PluginPermissions.java, PluginRuntime.java and
 * PluginDenialNotice.java (GPL-3.0): the check at the call site with one
 * log line per refusal, the dangerous keys, the mark of the running plugin
 * on the thread and the refusal of grant changes from plugin code; written
 * anew for MZGram without the trust levels and without the unsafe mode.
 */

package org.telegram.messenger.mzgram.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.ui.mzgram.MZGramPluginDenialNotice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MZGramPluginPermissions {

    // Distinct (plugin, key, action) refusals written to FileLog, at most
    private static final int MAX_LOGGED = 500;

    private static final ThreadLocal<String> current = new ThreadLocal<>();
    // Every plugin entered on the thread, innermost last (null for an invalid id)
    private static final ThreadLocal<ArrayList<String>> entered = new ThreadLocal<>();
    // pluginId -> the effective keys, dropped on every change of a record
    private static final ConcurrentHashMap<String, Set<String>> effective = new ConcurrentHashMap<>();
    private static volatile int generation;
    private static final Set<String> logged =Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MZGramPluginPermissions() {
    }

    /** With it a plugin can get around every other check. */
    public static boolean isDangerous(String permission) {
        return MZGramPluginGrants.HOOKS.equals(permission) || MZGramPluginGrants.NATIVE.equals(permission);
    }

    /** A short English text of the key, for logs. */
    public static String describe(String permission) {
        if (permission == null) {
            return "unknown";
        }
        switch (permission) {
            case MZGramPluginGrants.UI:
                return "its own windows and notices";
            case MZGramPluginGrants.MESSAGES_READ:
                return "read messages and updates";
            case MZGramPluginGrants.MESSAGES_SEND:
                return "send, edit and delete messages";
            case MZGramPluginGrants.NETWORK:
                return "network";
            case MZGramPluginGrants.FILES:
                return "files outside its own folder";
            case MZGramPluginGrants.INTENTS:
                return "links and other apps";
            case MZGramPluginGrants.SETTINGS:
                return "settings of the app";
            case MZGramPluginGrants.HOOKS:
                return "hooks (full control)";
            case MZGramPluginGrants.NATIVE:
                return "native code (full control)";
            default:
                return permission;
        }
    }

    // ---------- the checks ----------

    /**
     * Whether the plugin has the key; nothing is logged. Any thread. The
     * record goes when a plugin is deleted, so a plugin that is not added
     * has only "ui".
     */
    public static boolean has(String pluginId, String permission) {
        if (!MZGramPluginGrants.isKnown(permission) || !MZGramPluginSettingsStore.isValidId(pluginId)) {
            return false;
        }
        return effectiveOf(pluginId).contains(permission);
    }

    private static Set<String> effectiveOf(String pluginId) {
        Set<String> keys = effective.get(pluginId);
        if (keys == null) {
            final int before = generation;
            keys = Collections.unmodifiableSet(new HashSet<>(MZGramPluginGrants.getEffective(pluginId)));
            // A record changed while it was read: answer with it, keep nothing
            if (before == generation) {
                effective.put(pluginId, keys);
            }
        }
        return keys;
    }

    /**
     * The check where a plugin does something: true when it has the key.
     * A refusal is logged and told to the user; the caller then does
     * nothing and gives back a safe value. what: the action, for the log
     * (a method name or a host, never user data).
     */
    public static boolean check(String pluginId, String permission, String what) {
        if (has(pluginId, permission)) {
            return true;
        }
        denied(pluginId, permission, what);
        return false;
    }

    /** The check for the plugin whose code runs on this thread; true without one. */
    public static boolean checkCurrent(String permission, String what) {
        final String pluginId = current.get();
        return pluginId == null || check(pluginId, permission, what);
    }

    /**
     * A refusal found by a gate of its own (such as MZGramPluginHookGate,
     * which writes its own log entry): only the notice to the user.
     */
    public static void noteDenied(String pluginId, String permission) {
        if (MZGramPlugins.get(pluginId) != null && MZGramPluginGrants.isKnown(permission)
                && !MZGramPluginGrants.UI.equals(permission)) {
            MZGramPluginDenialNotice.note(pluginId, permission);
        }
    }

    private static void denied(String pluginId, String permission, String what) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId)) {
            return;
        }
        final String action = what == null ? "" : what;
        final String mark = pluginId + '\u0000' + permission + '\u0000' + action;
        if (logged.size() < MAX_LOGGED && logged.add(mark)) {
            FileLog.w("MZGramPluginPermissions: denied " + (action.isEmpty() ? "" : action + " ") + "to " + pluginId
                    + ", missing " + permission + " (" + describe(permission) + ")");
        }
        MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_PERMISSION_DENIED,
                MZGramPluginAuditJournal.CATEGORY_RUNTIME, permission == null ? "" : permission, action, null,
                MZGramPluginAuditJournal.LEVEL_DENIED);
        noteDenied(pluginId, permission);
    }

    // ---------- the plugin on this thread ----------

    /**
     * Plugin code starts on this thread; give the result back to exit().
     * Inside the code of another plugin (a hook of one plugin fired by the
     * code of another) the one with fewer permissions stays marked, so
     * nesting never adds permissions.
     */
    public static String enter(String pluginId) {
        final String previous = current.get();
        ArrayList<String> stack = entered.get();
        if (stack == null) {
            stack = new ArrayList<>();
            entered.set(stack);
        }
        final boolean valid = MZGramPluginSettingsStore.isValidId(pluginId);
        stack.add(valid ? pluginId : null);
        if (valid) {
            final boolean keep = previous != null && !previous.equals(pluginId)
                    && effectiveOf(previous).size() <= effectiveOf(pluginId).size();
            if (!keep) {
                current.set(pluginId);
            }
        }
        return previous;
    }

    /** Plugin code ended; previous is what enter() gave (null clears the mark). */
    public static void exit(String previous) {
        if (previous == null) {
            current.remove();
            entered.remove();
        } else {
            current.set(previous);
            final ArrayList<String> stack = entered.get();
            if (stack != null && !stack.isEmpty()) {
                stack.remove(stack.size() - 1);
            }
        }
    }

    /** The plugin whose code runs on this thread, or null. */
    public static String current() {
        return current.get();
    }

    /** Whether code of this plugin runs anywhere on this thread, nested or not. */
    public static boolean isOnThread(String pluginId) {
        final ArrayList<String> stack = entered.get();
        return pluginId != null && stack != null && stack.contains(pluginId);
    }

    /** Whether plugin code runs on this thread (then grants may not change). */
    public static boolean insidePlugin() {
        return current.get() != null;
    }

    // ---------- changes ----------

    /** A record changed or was dropped: the next checks read it again. */
    static synchronized void invalidate(String pluginId) {
        generation++;
        if (pluginId == null) {
            effective.clear();
        } else {
            effective.remove(pluginId);
        }
    }

    /**
     * The user changed what the plugin has: the refusals may be told again,
     * and a running plugin starts again so that it has exactly that.
     */
    static void changed(String pluginId, boolean restart) {
        invalidate(pluginId);
        final String prefix = pluginId + '\u0000';
        // Without removeIf: Collection.removeIf needs Android 7
        for (Iterator<String> it = logged.iterator(); it.hasNext(); ) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
        MZGramPluginDenialNotice.reset(pluginId);
        if (restart) {
            MZGramPluginEngine.restartIfRunning(pluginId);
        }
    }

    /** The plugin was deleted. */
    static void forget(String pluginId) {
        changed(pluginId, false);
    }
}
