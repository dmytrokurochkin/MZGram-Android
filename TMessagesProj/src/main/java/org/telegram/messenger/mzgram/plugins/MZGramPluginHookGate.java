/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Decides whether a plugin may hook a method of the app, and counts the
 * hooks that ran. The hook engine asks check() before it installs a hook
 * and isSuspended() before each call of an installed one; nothing is
 * hooked here.
 *
 * A plugin may hook only with the "hooks" permission granted by the user
 * (MZGramPluginGrants), and never what protects the user or the plugins
 * themselves, whatever was granted: the passcode screens and checks, the
 * MTProto keys, two-step verification, secret chat keys, the push keys,
 * the update installer, the plugin gates and stores, reflection, crypto,
 * the Keystore, class loaders and the hook framework.
 *
 * While "Hide Stories" is on, hooks on stories classes are skipped: the
 * stories row is not on screen, so a plugin that expects it would only
 * fail (fix 1.2 of the knowledge base).
 *
 * Based on exteraless plugins/xposed/HookGate.java and HookStats.java
 * (GPL-3.0): the gate in front of every hook and the per hook counters;
 * the rules and the code are written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import android.os.Looper;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.mzgram.MZGramConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class MZGramPluginHookGate {

    public static final int ALLOWED = 0;
    /** The plugin id is not valid or the target is empty. */
    public static final int DENIED_INVALID = 1;
    /** The user has not granted "hooks" to this plugin. */
    public static final int DENIED_NO_PERMISSION = 2;
    /** The target protects the user or the plugins; never hooked. */
    public static final int DENIED_PROTECTED = 3;

    // Whole packages that are never hooked
    private static final String[] PROTECTED_PACKAGES = {
            "org.telegram.messenger.mzgram.plugins.",
            "de.robv.android.xposed.",
            "com.chaquo.python.",
            "java.lang.reflect.",
            "java.lang.invoke.",
            "java.security.",
            "javax.crypto.",
            "javax.net.ssl.",
            "android.security.",
            "dalvik.system.",
    };

    // Whole classes that are never hooked (with their inner classes)
    private static final String[] PROTECTED_CLASSES = {
            "org.telegram.ui.Components.PasscodeView",
            "org.telegram.ui.Components.PasscodeViewDialog",
            "org.telegram.ui.PasscodeActivity",
            "org.telegram.messenger.mzgram.MZGramPasscodeAccounts",
            "org.telegram.ui.mzgram.MZGramPasscodeAccountsActivity",
            "org.telegram.messenger.SRPHelper",
            "org.telegram.messenger.SecretChatHelper",
            "org.telegram.messenger.mzgram.MZGramWebPushCrypto",
            "org.telegram.messenger.mzgram.MZGramUpdateInstaller",
            "org.telegram.messenger.mzgram.MZGramUpdateChecker",
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.System",
    };

    // Methods never hooked in an otherwise open class: class, then name
    // patterns ("name" exact, "prefix*", "*part*" ignoring case)
    private static final String[][] PROTECTED_METHODS = {
            {"org.telegram.tgnet.ConnectionsManager", "native_*", "*authkey*"},
            {"org.telegram.messenger.SharedConfig", "*passcode*", "*fingerprint*"},
            {"org.telegram.messenger.Utilities", "aes*", "pbkdf2*"},
    };

    private static final String STORIES_PACKAGE = "org.telegram.ui.Stories.";

    // Targets already reported as denied, so the log is written once each
    private static final HashSet<String> reported = new HashSet<>();
    private static final int MAX_REPORTED = 500;

    private static volatile boolean statsEnabled;
    private static final ArrayList<Counter> counters = new ArrayList<>();

    private MZGramPluginHookGate() {
    }

    // ---------- the gate ----------

    /** Whether the plugin may hook this method or constructor: ALLOWED or a DENIED_ reason. */
    public static int check(String pluginId, Member member) {
        if (member == null) {
            return deny(pluginId, null, DENIED_INVALID);
        }
        return check(pluginId, member.getDeclaringClass().getName(), memberName(member));
    }

    /** The same by name; memberName "<init>" is a constructor. */
    public static int check(String pluginId, String className, String memberName) {
        final String target = className + "." + memberName;
        if (!MZGramPluginSettingsStore.isValidId(pluginId) || isEmpty(className) || isEmpty(memberName)) {
            return deny(pluginId, target, DENIED_INVALID);
        }
        if (isProtected(className, memberName)) {
            return deny(pluginId, target, DENIED_PROTECTED);
        }
        if (!MZGramPluginGrants.isGranted(pluginId, MZGramPluginGrants.HOOKS)) {
            return deny(pluginId, target, DENIED_NO_PERMISSION);
        }
        return ALLOWED;
    }

    /** Whether the target is never hooked, whatever the plugin was granted. */
    public static boolean isProtected(String className, String memberName) {
        if (className == null) {
            return true;
        }
        for (String prefix : PROTECTED_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        for (String name : PROTECTED_CLASSES) {
            if (isClassOrInner(className, name)) {
                return true;
            }
        }
        if (memberName == null) {
            return false;
        }
        for (String[] rule : PROTECTED_METHODS) {
            if (!isClassOrInner(className, rule[0])) {
                continue;
            }
            for (int i = 1; i < rule.length; i++) {
                if (matches(rule[i], memberName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether an installed hook on this class should be skipped now (the
     * original method runs as if there were no hook). Cheap: asked on every
     * call of a hooked method.
     */
    public static boolean isSuspended(String className) {
        return MZGramConfig.hideStories && isStoriesClass(className);
    }

    static boolean isStoriesClass(String className) {
        if (className == null) {
            return false;
        }
        if (className.startsWith(STORIES_PACKAGE)) {
            return true;
        }
        final String simple = className.substring(className.lastIndexOf('.') + 1);
        return simple.contains("Stories");
    }

    private static int deny(String pluginId, String target, int reason) {
        final String key = pluginId + " " + target + " " + reason;
        boolean first;
        synchronized (reported) {
            first = reported.size() < MAX_REPORTED && reported.add(key);
        }
        if (first) {
            FileLog.w("MZGramPluginHookGate: " + pluginId + " may not hook " + target + " (" + reasonName(reason) + ")");
        }
        // Every refusal, repeats counted (the journal ignores an invalid id)
        MZGramPluginAuditJournal.record(pluginId, reason == DENIED_PROTECTED ? MZGramPluginAuditJournal.EVENT_HOOK_PROTECTED
                        : reason == DENIED_NO_PERMISSION ? MZGramPluginAuditJournal.EVENT_HOOK_NO_PERMISSION
                        : MZGramPluginAuditJournal.EVENT_HOOK_DENIED,
                MZGramPluginAuditJournal.CATEGORY_HOOKS, target, MZGramPluginAuditJournal.LEVEL_DENIED);
        return reason;
    }

    public static String reasonName(int reason) {
        switch (reason) {
            case ALLOWED:
                return "allowed";
            case DENIED_INVALID:
                return "invalid";
            case DENIED_NO_PERMISSION:
                return "no hooks permission";
            case DENIED_PROTECTED:
                return "protected";
            default:
                return "unknown";
        }
    }

    private static String memberName(Member member) {
        return member instanceof Constructor ? "<init>" : member.getName();
    }

    private static boolean isClassOrInner(String className, String name) {
        return className.equals(name) || className.startsWith(name + "$");
    }

    private static boolean matches(String pattern, String name) {
        if (pattern.length() > 1 && pattern.startsWith("*") && pattern.endsWith("*")) {
            final String part = pattern.substring(1, pattern.length() - 1).toLowerCase(Locale.US);
            return name.toLowerCase(Locale.US).contains(part);
        }
        if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return name.equals(pattern);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    // ---------- counters ----------

    /** One installed hook of one plugin; the engine records each call. */
    public static final class Counter {

        public final String pluginId;
        public final String target;
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong totalNs = new AtomicLong();
        private final AtomicLong maxNs = new AtomicLong();
        private final AtomicLong mainCalls = new AtomicLong();
        private final AtomicLong mainNs = new AtomicLong();
        private final AtomicLong skipped = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();

        Counter(String pluginId, String target) {
            this.pluginId = pluginId;
            this.target = target;
        }

        /** A call of the plugin's hook body that took elapsedNs. */
        public void record(long elapsedNs) {
            if (!statsEnabled) {
                return;
            }
            calls.incrementAndGet();
            totalNs.addAndGet(elapsedNs);
            updateMax(maxNs, elapsedNs);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                mainCalls.incrementAndGet();
                mainNs.addAndGet(elapsedNs);
            }
        }

        /** A call skipped by isSuspended(). */
        public void skip() {
            if (statsEnabled) {
                skipped.incrementAndGet();
            }
        }

        /** A call whose hook body threw (always counted, the watchdog reads it). */
        public void fail() {
            failures.incrementAndGet();
            MZGramPluginAuditJournal.record(pluginId, MZGramPluginAuditJournal.EVENT_HOOK_FAILED,
                    MZGramPluginAuditJournal.CATEGORY_HOOKS, target, MZGramPluginAuditJournal.LEVEL_ERROR);
        }

        public long getFailures() {
            return failures.get();
        }

        void reset() {
            calls.set(0);
            totalNs.set(0);
            maxNs.set(0);
            mainCalls.set(0);
            mainNs.set(0);
            skipped.set(0);
        }

        private static void updateMax(AtomicLong max, long value) {
            long current;
            while (value > (current = max.get()) && !max.compareAndSet(current, value)) {
            }
        }
    }

    /** A counter for a hook the engine installs after check() said ALLOWED. */
    public static Counter counter(String pluginId, Member member) {
        final String target = member == null ? "?" : shortClass(member.getDeclaringClass().getName()) + "." + memberName(member);
        final Counter counter = new Counter(pluginId != null ? pluginId : "?", target);
        synchronized (counters) {
            counters.add(counter);
        }
        return counter;
    }

    /** Drops the counters of a plugin that was turned off or deleted. */
    public static void forget(String pluginId) {
        if (pluginId == null) {
            return;
        }
        synchronized (counters) {
            for (int i = counters.size() - 1; i >= 0; i--) {
                if (counters.get(i).pluginId.equals(pluginId)) {
                    counters.remove(i);
                }
            }
        }
    }

    public static boolean isStatsEnabled() {
        return statsEnabled;
    }

    /** Starts counting from zero; off by default, since timing every call costs a little. */
    public static void startStats() {
        synchronized (counters) {
            for (Counter counter : counters) {
                counter.reset();
            }
        }
        statsEnabled = true;
    }

    public static void stopStats() {
        statsEnabled = false;
    }

    /** A plain text table for the crash log and the profiler reports. */
    public static void appendReport(StringBuilder out) {
        out.append("\n== plugin hooks ==\n");
        final ArrayList<Counter> active = new ArrayList<>();
        synchronized (counters) {
            for (Counter counter : counters) {
                if (counter.calls.get() > 0 || counter.skipped.get() > 0 || counter.failures.get() > 0) {
                    active.add(counter);
                }
            }
        }
        if (!statsEnabled) {
            out.append("counting is off\n");
        }
        if (active.isEmpty()) {
            out.append("no plugin hook calls\n");
            return;
        }
        Collections.sort(active, (a, b) -> {
            final int byMain = Long.compare(b.mainNs.get(), a.mainNs.get());
            return byMain != 0 ? byMain : Long.compare(b.totalNs.get(), a.totalNs.get());
        });
        out.append("     calls  total ms    avg us   max ms  main calls   main ms   skipped  failures  hook\n");
        for (Counter c : active) {
            final long calls = c.calls.get();
            final long total = c.totalNs.get();
            out.append(String.format(Locale.US, "%10d %9.1f %9.1f %8.2f %11d %9.1f %9d %9d  %s %s\n",
                    calls, total / 1e6, calls > 0 ? total / 1e3 / calls : 0.0, c.maxNs.get() / 1e6,
                    c.mainCalls.get(), c.mainNs.get() / 1e6, c.skipped.get(), c.failures.get(),
                    c.pluginId, c.target));
        }
    }

    private static String shortClass(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
