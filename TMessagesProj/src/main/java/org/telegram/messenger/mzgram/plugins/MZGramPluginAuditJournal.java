/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The activity log of plugins: what was done with each plugin and what each
 * plugin did, kept only on the phone and never sent anywhere.
 *
 * Two parts. What the user (or the developer server) did with a plugin
 * (added, updated, deleted, turned on or off, permissions given or taken
 * back) is rare and worth keeping: the last MANAGE_LIMIT such entries are
 * kept in files/mzgram_plugin_journal.json and stay after a restart. What a
 * plugin does while it runs (hooks blocked by MZGramPluginHookGate, hook
 * errors, later the gates of the engine) can be frequent: the last
 * RUNTIME_LIMIT entries are kept in memory only, so nothing is written to
 * disk on a hot path. Entries hold plugin ids, versions, permission keys and
 * the names of hooked methods, never message text or other user data; a
 * detail is cut at MAX_DETAIL characters.
 *
 * The same entry repeated in a row is counted, not added again.
 *
 * Based on exteraless PluginAuditJournal (GPL-3.0): a ring buffer of plugin
 * events with repeat counts and a per-plugin view; written anew for MZGram,
 * with the kept part for the user's own actions.
 */

package org.telegram.messenger.mzgram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class MZGramPluginAuditJournal {

    public static final int LEVEL_INFO = 0;
    public static final int LEVEL_DENIED = 1;
    public static final int LEVEL_ERROR = 2;

    /** What the user or the developer server did with a plugin; kept on disk. */
    public static final String CATEGORY_MANAGE = "manage";
    /** Permissions given or taken back; kept on disk. */
    public static final String CATEGORY_PERMISSIONS = "permissions";
    /** Hooks of a running plugin; memory only. */
    public static final String CATEGORY_HOOKS = "hooks";
    /** A plugin started, stopped, failed or wrote to its log (MZGramPluginEngine); memory only. */
    public static final String CATEGORY_RUNTIME = "runtime";

    public static final String EVENT_ADDED = "added";
    public static final String EVENT_UPDATED = "updated";
    public static final String EVENT_DELETED = "deleted";
    public static final String EVENT_ENABLED = "enabled";
    public static final String EVENT_DISABLED = "disabled";
    public static final String EVENT_GRANTED = "granted";
    public static final String EVENT_REVOKED = "revoked";
    /** The signed permissions record was changed outside MZGram and dropped. */
    public static final String EVENT_GRANTS_DROPPED = "grants_dropped";
    public static final String EVENT_HOOK_DENIED = "hook_denied";
    public static final String EVENT_HOOK_PROTECTED = "hook_protected";
    public static final String EVENT_HOOK_NO_PERMISSION = "hook_no_permission";
    public static final String EVENT_HOOK_FAILED = "hook_failed";
    /**
     * detail: the permission key a running plugin lacked; previous: what it
     * tried (MZGramPluginPermissions.check).
     */
    public static final String EVENT_PERMISSION_DENIED = "permission_denied";
    /**
     * detail: what a running plugin tried that is never available to
     * plugins (another process, a class of the plugin engine, the settings
     * of another plugin); MZGramPluginSinkPolicy.blocked.
     */
    public static final String EVENT_SINK_BLOCKED = "sink_blocked";
    /** detail: the host a running plugin connected to (only the host). */
    public static final String EVENT_NETWORK = "network";
    /** detail: the class and method a running plugin hooked (MZGramPluginHooks). */
    public static final String EVENT_HOOKED = "hooked";
    public static final String EVENT_STARTED = "started";
    /** detail: why the plugin did not start. */
    public static final String EVENT_START_FAILED = "start_failed";
    public static final String EVENT_STOPPED = "stopped";
    /** detail: the callback and its error. */
    public static final String EVENT_ERROR = "error";
    /** detail: a line of BasePlugin.log. */
    public static final String EVENT_LOG = "log";

    /** The source of an action done through MZGramPluginDevServer. */
    public static final String SOURCE_DEV_SERVER = "dev_server";

    public static final int MANAGE_LIMIT = 100;
    public static final int RUNTIME_LIMIT = 200;
    private static final int MAX_DETAIL = 200;
    private static final String FILE = "mzgram_plugin_journal.json";
    private static final long SAVE_DELAY_MS = 1000;

    private static final Object lock = new Object();
    private static final ArrayDeque<Entry> manage = new ArrayDeque<>();
    private static final ArrayDeque<Entry> runtime = new ArrayDeque<>();
    private static boolean loaded;
    private static final Runnable saveRunnable = MZGramPluginAuditJournal::save;

    private MZGramPluginAuditJournal() {
    }

    public static final class Entry {
        /** The time of the last repeat, in milliseconds. */
        public long ts;
        public final String pluginId;
        public final String event;
        public final String category;
        /** A version, a permission key or a hooked method; may be empty. */
        public final String detail;
        /** The version before an update, else null. */
        public final String previous;
        /** SOURCE_DEV_SERVER or null. */
        public final String source;
        public final int level;
        /** How many times in a row the same entry happened. */
        public int count;

        Entry(long ts, String pluginId, String event, String category, String detail, String previous, String source, int level, int count) {
            this.ts = ts;
            this.pluginId = pluginId;
            this.event = event;
            this.category = category;
            this.detail = detail;
            this.previous = previous;
            this.source = source;
            this.level = level;
            this.count = count;
        }

        Entry copy() {
            return new Entry(ts, pluginId, event, category, detail, previous, source, level, count);
        }

        boolean sameAs(Entry other) {
            return pluginId.equals(other.pluginId) && event.equals(other.event) && category.equals(other.category)
                    && detail.equals(other.detail) && level == other.level
                    && equal(previous, other.previous) && equal(source, other.source);
        }
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean persistent(String category) {
        return CATEGORY_MANAGE.equals(category) || CATEGORY_PERMISSIONS.equals(category);
    }

    public static void record(String pluginId, String event, String category, String detail, int level) {
        record(pluginId, event, category, detail, null, null, level);
    }

    /** Adds an entry; does nothing for an invalid plugin id. Any thread. */
    public static void record(String pluginId, String event, String category, String detail, String previous, String source, int level) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId) || event == null || category == null) {
            return;
        }
        final Entry entry = new Entry(System.currentTimeMillis(), pluginId, event, category,
                cut(detail), previous == null ? null : cut(previous), source, level, 1);
        final boolean keep = persistent(category);
        synchronized (lock) {
            if (keep) {
                ensureLoaded();
            }
            final ArrayDeque<Entry> target = keep ? manage : runtime;
            final Entry last = target.peekLast();
            if (last != null && last.sameAs(entry)) {
                last.count++;
                last.ts = entry.ts;
            } else {
                while (target.size() >= (keep ? MANAGE_LIMIT : RUNTIME_LIMIT)) {
                    target.pollFirst();
                }
                target.addLast(entry);
            }
        }
        if (keep) {
            scheduleSave();
        }
    }

    private static String cut(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_DETAIL ? text.substring(0, MAX_DETAIL) : text;
    }

    /** Copies of the entries of one plugin (all plugins for null), oldest first. */
    public static List<Entry> entries(String pluginId) {
        final ArrayList<Entry> out = new ArrayList<>();
        synchronized (lock) {
            ensureLoaded();
            for (Entry entry : manage) {
                if (pluginId == null || pluginId.equals(entry.pluginId)) {
                    out.add(entry.copy());
                }
            }
            for (Entry entry : runtime) {
                if (pluginId == null || pluginId.equals(entry.pluginId)) {
                    out.add(entry.copy());
                }
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.ts, b.ts));
        return out;
    }

    /**
     * After the plugin was deleted: drops what it did while running. What was
     * done with it, the deletion too, stays, so it can still be looked up.
     */
    public static void forget(String pluginId) {
        if (pluginId == null) {
            return;
        }
        synchronized (lock) {
            removeOf(runtime, pluginId);
        }
    }

    /** Clears the log of one plugin, or the whole log for null. */
    public static void clear(String pluginId) {
        synchronized (lock) {
            ensureLoaded();
            if (pluginId == null) {
                manage.clear();
                runtime.clear();
            } else {
                removeOf(manage, pluginId);
                removeOf(runtime, pluginId);
            }
        }
        scheduleSave();
    }

    private static void removeOf(ArrayDeque<Entry> entries, String pluginId) {
        // No removeIf: API 23
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            if (pluginId.equals(it.next().pluginId)) {
                it.remove();
            }
        }
    }

    // ---------- the kept part ----------

    private static File file() {
        return new File(ApplicationLoader.getFilesDirFixed(), FILE);
    }

    /** Reads the kept part once; called with the lock held. */
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        final File file = file();
        if (!file.isFile() || file.length() > 1024 * 1024) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            final byte[] bytes = new byte[(int) file.length()];
            int total = 0;
            int read;
            while (total < bytes.length && (read = in.read(bytes, total, bytes.length - total)) > 0) {
                total += read;
            }
            final JSONArray array = new JSONArray(new String(bytes, 0, total, StandardCharsets.UTF_8));
            final ArrayList<Entry> fromFile = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                final JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final String pluginId = item.optString("plugin", null);
                final String event = item.optString("event", null);
                final String category = item.optString("category", null);
                if (!MZGramPluginSettingsStore.isValidId(pluginId) || event == null || !persistent(category)) {
                    continue;
                }
                fromFile.add(new Entry(item.optLong("ts", 0), pluginId, event, category,
                        cut(item.optString("detail", "")),
                        item.has("previous") ? cut(item.optString("previous", "")) : null,
                        item.has("source") ? item.optString("source", null) : null,
                        item.optInt("level", LEVEL_INFO), Math.max(1, item.optInt("count", 1))));
            }
            // Entries added before the file was read come after the read ones
            final ArrayList<Entry> added = new ArrayList<>(manage);
            manage.clear();
            for (Entry entry : fromFile) {
                manage.addLast(entry);
            }
            for (Entry entry : added) {
                manage.addLast(entry);
            }
            while (manage.size() > MANAGE_LIMIT) {
                manage.pollFirst();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void scheduleSave() {
        Utilities.globalQueue.cancelRunnable(saveRunnable);
        Utilities.globalQueue.postRunnable(saveRunnable, SAVE_DELAY_MS);
    }

    private static void save() {
        final JSONArray array = new JSONArray();
        synchronized (lock) {
            if (!loaded) {
                return;
            }
            try {
                for (Entry entry : manage) {
                    final JSONObject item = new JSONObject();
                    item.put("ts", entry.ts);
                    item.put("plugin", entry.pluginId);
                    item.put("event", entry.event);
                    item.put("category", entry.category);
                    item.put("detail", entry.detail);
                    if (entry.previous != null) {
                        item.put("previous", entry.previous);
                    }
                    if (entry.source != null) {
                        item.put("source", entry.source);
                    }
                    item.put("level", entry.level);
                    item.put("count", entry.count);
                    array.put(item);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                return;
            }
        }
        final File file = file();
        if (array.length() == 0) {
            if (file.exists() && !file.delete()) {
                FileLog.e("MZGramPluginAuditJournal: cannot delete " + file);
            }
            return;
        }
        final File part = new File(file.getParentFile(), FILE + ".part");
        try (OutputStream out = new FileOutputStream(part)) {
            out.write(array.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable e) {
            FileLog.e(e);
            part.delete();
            return;
        }
        if (!part.renameTo(file) && !(file.delete() && part.renameTo(file))) {
            FileLog.e("MZGramPluginAuditJournal: cannot write " + file);
            part.delete();
        }
    }
}
