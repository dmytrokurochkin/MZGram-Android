/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Icon packs (appearance.icon-packs, MZGramConfig.iconPacks, off by
 * default): other icons in place of Telegram's own drawables.
 *
 * A pack is a .icons file: a ZIP archive with metadata.json and the
 * pictures (PNG, WEBP, JPEG or SVG). metadata.json names the pack and maps
 * Telegram's drawable names to the files:
 *
 *   {"schemaVersion": 1, "packId": "...", "packName": "...", "author": "...",
 *    "version": "1.0", "isTintable": true, "icons": {"msg_settings": "a.svg"}}
 *
 * Several packs can be on at a time. The order of the list is the priority:
 * an icon comes from the first pack on the list that has it, then from the
 * built-in set (Solar or Remix, MZGramBuiltinIcons), then Telegram's own.
 *
 * Tinting (isTintable): Telegram colours most icons itself with a colour
 * filter of the theme. A tinted pack is drawn white, as Telegram's own
 * icons, so the theme's colour reaches it. A colourful pack is drawn as it
 * is and ignores colour filters, so the theme does not turn it into a flat
 * silhouette. "isTintable" in metadata.json decides; without it a pack of
 * SVG files only is tinted and a pack with pictures is tinted only when the
 * sampled pictures have no colour. The user can override it per pack.
 *
 * A pack is never read straight from the archive: install() takes only the
 * files metadata.json names, with limits on their number and size, and
 * stores them under its own file names, so no path from the archive is
 * ever used on disk. MZGram makes no network calls for packs: a pack comes
 * from a file the user picks or a file in a chat.
 *
 * Idea from exteraless IconPackManager, IconPackStorage and IconPack
 * (GPL-3.0, a port of exteraGram's icon packs); written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MZGramIconPacks {

    public static final String EXTENSION = ".icons";

    public static final int BUILTIN_NONE = 0;
    public static final int BUILTIN_SOLAR = 1;
    public static final int BUILTIN_REMIX = 2;

    public static final int TINT_AUTO = 0;
    public static final int TINT_ON = 1;
    public static final int TINT_OFF = 2;

    // Why a file is not a pack
    public static final int ERROR_NONE = 0;
    public static final int ERROR_READ = 1;
    public static final int ERROR_NOT_PACK = 2;
    public static final int ERROR_NO_ICONS = 3;
    public static final int ERROR_TOO_LARGE = 4;

    static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;
    static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;
    static final int MAX_METADATA_BYTES = 1024 * 1024;
    static final int MAX_ICONS = 5000;
    private static final int SAMPLE_PICTURES = 12;

    private static final String PREFS = "mzgram_iconpacks";
    private static final String KEY_ORDER = "order";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_BUILTIN = "builtin";
    private static final String KEY_TINT = "tint_";
    private static final String KEY_DETECTED = "detected_";
    private static final String DIR = "mzgram_iconpacks";
    private static final String PICKED_DIR = "mzgram_iconpacks_picked";
    private static final String METADATA = "metadata.json";

    private static final Pattern RESOURCE_NAME = Pattern.compile("[a-z0-9_]{1,200}");
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** A pack on disk, or a picked file before it is installed. */
    public static final class Pack {
        public final String key;
        public final String id;
        public final String name;
        public final String author;
        public final String version;
        /** isTintable from metadata.json, or null when it says nothing. */
        public final Boolean declaredTintable;
        /** Drawable name to the path of the picture (in the archive or on disk). */
        final Map<String, String> icons;
        final File dir;

        Pack(String key, String id, String name, String author, String version, Boolean declaredTintable, Map<String, String> icons, File dir) {
            this.key = key;
            this.id = id;
            this.name = name;
            this.author = author;
            this.version = version;
            this.declaredTintable = declaredTintable;
            this.icons = icons;
            this.dir = dir;
        }

        public int count() {
            return icons.size();
        }

        public boolean has(String drawableName) {
            return icons.containsKey(drawableName);
        }
    }

    /** What reading a picked file gave: a pack or an error. */
    public static final class Picked {
        public final File file;
        public final Pack pack;
        public final int error;

        Picked(File file, Pack pack, int error) {
            this.file = file;
            this.pack = pack;
            this.error = error;
        }
    }

    /** Where an icon's original drawable comes from, without the packs. */
    public interface Source {
        Drawable original(int resId, int density, Resources.Theme theme);

        Resources resources();
    }

    private static final Object lock = new Object();
    private static final ArrayList<Runnable> listeners = new ArrayList<>();

    // Loaded lazily on the first icon asked for; replaced as a whole on a change
    private static volatile State state;

    private static final class State {
        final ArrayList<Pack> installed = new ArrayList<>();
        final ArrayList<Pack> active = new ArrayList<>();
        final HashMap<String, Pack> owner = new HashMap<>();
        final HashMap<String, Boolean> tinted = new HashMap<>();
        SparseIntArray builtin;
    }

    private static final SparseArray<String> names = new SparseArray<>();
    private static final Set<Long> missing = Collections.synchronizedSet(new HashSet<>());
    private static final LruCache<Long, Bitmap> bitmaps = new LruCache<Long, Bitmap>(
            (int) Math.max(4096, Runtime.getRuntime().maxMemory() / 1024 / 16)) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private MZGramIconPacks() {
    }

    // ---- Settings ----

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when there is something to replace: a pack on or a built-in set. */
    public static boolean isActive() {
        if (!MZGramConfig.iconPacks || ApplicationLoader.applicationContext == null) {
            return false;
        }
        State s = state();
        return !s.active.isEmpty() || s.builtin != null;
    }

    public static int builtin() {
        int value = prefs().getInt(KEY_BUILTIN, BUILTIN_NONE);
        return value == BUILTIN_SOLAR || value == BUILTIN_REMIX ? value : BUILTIN_NONE;
    }

    public static void setBuiltin(int value) {
        prefs().edit().putInt(KEY_BUILTIN, value == BUILTIN_SOLAR || value == BUILTIN_REMIX ? value : BUILTIN_NONE).apply();
        changed();
    }

    public static String builtinTitle(int value) {
        if (value == BUILTIN_SOLAR) {
            return "Solar Icon Set";
        } else if (value == BUILTIN_REMIX) {
            return "Remix Icon";
        }
        return null;
    }

    public static String builtinAuthor(int value) {
        if (value == BUILTIN_SOLAR) {
            return "480 Design";
        } else if (value == BUILTIN_REMIX) {
            return "Remix Design";
        }
        return null;
    }

    /** Installed packs in the order of priority. */
    public static List<Pack> installed() {
        return new ArrayList<>(state().installed);
    }

    public static boolean isOn(String key) {
        return parseKeys(prefs().getString(KEY_ACTIVE, "")).contains(key);
    }

    public static void setOn(String key, boolean on) {
        ArrayList<String> active = parseKeys(prefs().getString(KEY_ACTIVE, ""));
        active.remove(key);
        if (on) {
            active.add(key);
        }
        prefs().edit().putString(KEY_ACTIVE, TextUtils.join(",", active)).apply();
        changed();
    }

    public static void setOrder(List<String> keys) {
        prefs().edit().putString(KEY_ORDER, TextUtils.join(",", keys)).apply();
        changed();
    }

    public static int tintMode(String key) {
        int value = prefs().getInt(KEY_TINT + key, TINT_AUTO);
        return value == TINT_ON || value == TINT_OFF ? value : TINT_AUTO;
    }

    /** Whether the pack is drawn white and coloured by the theme. */
    public static boolean isTinted(Pack pack) {
        int mode = tintMode(pack.key);
        if (mode != TINT_AUTO) {
            return mode == TINT_ON;
        }
        if (pack.declaredTintable != null) {
            return pack.declaredTintable;
        }
        return prefs().getBoolean(KEY_DETECTED + pack.key, true);
    }

    public static void setTinted(String key, boolean tinted) {
        prefs().edit().putInt(KEY_TINT + key, tinted ? TINT_ON : TINT_OFF).apply();
        changed();
    }

    public static void addListener(Runnable listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public static void removeListener(Runnable listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /** Rereads the packs and drops every picture drawn so far. */
    public static void changed() {
        synchronized (lock) {
            state = null;
            names.clear();
        }
        missing.clear();
        bitmaps.evictAll();
        ArrayList<Runnable> copy;
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }
        for (Runnable listener : copy) {
            AndroidUtilities.runOnUIThread(listener);
        }
    }

    // ---- Packs on disk ----

    private static File root() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), DIR);
    }

    private static State state() {
        State s = state;
        if (s != null) {
            return s;
        }
        synchronized (lock) {
            if (state == null) {
                state = load();
            }
            return state;
        }
    }

    private static State load() {
        State s = new State();
        try {
            HashMap<String, Pack> byKey = new HashMap<>();
            File[] dirs = root().listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    if (!dir.isDirectory() || !SAFE_KEY.matcher(dir.getName()).matches() || dir.getName().startsWith(".")) {
                        continue;
                    }
                    Pack pack = readInstalled(dir);
                    if (pack != null) {
                        byKey.put(pack.key, pack);
                    }
                }
            }
            SharedPreferences prefs = prefs();
            for (String key : parseKeys(prefs.getString(KEY_ORDER, ""))) {
                Pack pack = byKey.remove(key);
                if (pack != null) {
                    s.installed.add(pack);
                }
            }
            // Packs missing from the saved order go first: installed last, newest on top
            ArrayList<Pack> rest = new ArrayList<>(byKey.values());
            Collections.sort(rest, (a, b) -> Long.compare(b.dir.lastModified(), a.dir.lastModified()));
            s.installed.addAll(0, rest);
            if (MZGramConfig.iconPacks) {
                ArrayList<String> on = parseKeys(prefs.getString(KEY_ACTIVE, ""));
                for (Pack pack : s.installed) {
                    if (on.contains(pack.key)) {
                        s.active.add(pack);
                        s.tinted.put(pack.key, isTinted(pack));
                    }
                }
                // The first pack on the list wins
                for (int i = s.active.size() - 1; i >= 0; i--) {
                    for (String name : s.active.get(i).icons.keySet()) {
                        s.owner.put(name, s.active.get(i));
                    }
                }
                int builtin = builtin();
                if (builtin != BUILTIN_NONE) {
                    s.builtin = builtinMap(builtin);
                }
            }
        } catch (Throwable e) {
            FileLog.e("MZGramIconPacks.load", e);
        }
        return s;
    }

    private static SparseIntArray builtinMap(int builtin) {
        int[] pairs = builtin == BUILTIN_SOLAR ? MZGramBuiltinIcons.solar() : MZGramBuiltinIcons.remix();
        SparseIntArray map = new SparseIntArray(pairs.length / 2);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static Pack readInstalled(File dir) {
        File metadata = new File(dir, METADATA);
        if (!metadata.isFile() || metadata.length() > MAX_METADATA_BYTES) {
            return null;
        }
        try (InputStream in = new FileInputStream(metadata)) {
            byte[] bytes = readAll(in, MAX_METADATA_BYTES);
            if (bytes == null) {
                return null;
            }
            Pack pack = parseMetadata(new String(bytes, StandardCharsets.UTF_8), dir.getName(), dir);
            if (pack == null) {
                return null;
            }
            // Only the files install() wrote: plain names inside the folder
            Iterator<Map.Entry<String, String>> it = pack.icons.entrySet().iterator();
            while (it.hasNext()) {
                String file = it.next().getValue();
                if (file.indexOf('/') >= 0 || file.indexOf('\\') >= 0 || file.startsWith(".") || !new File(dir, file).isFile()) {
                    it.remove();
                }
            }
            return pack;
        } catch (Throwable e) {
            FileLog.e("MZGramIconPacks.readInstalled", e);
            return null;
        }
    }

    public static void delete(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            return;
        }
        deleteTree(new File(root(), key));
        ArrayList<String> active = parseKeys(prefs().getString(KEY_ACTIVE, ""));
        active.remove(key);
        ArrayList<String> order = parseKeys(prefs().getString(KEY_ORDER, ""));
        order.remove(key);
        prefs().edit()
                .putString(KEY_ACTIVE, TextUtils.join(",", active))
                .putString(KEY_ORDER, TextUtils.join(",", order))
                .remove(KEY_TINT + key)
                .remove(KEY_DETECTED + key)
                .apply();
        changed();
    }

    // ---- Reading and installing a picked file ----

    /** Copies the picked file and reads its metadata, off the UI thread; the result comes on it. */
    public static void readPicked(Uri uri, Utilities.Callback<Picked> onResult) {
        Utilities.globalQueue.postRunnable(() -> {
            Picked picked;
            File copy = copyPicked(uri);
            if (copy == null) {
                picked = new Picked(null, null, ERROR_READ);
            } else if (!copy.isFile()) {
                picked = new Picked(null, null, ERROR_TOO_LARGE);
            } else {
                picked = read(copy);
            }
            final Picked result = picked;
            AndroidUtilities.runOnUIThread(() -> onResult.run(result));
        });
    }

    /** Reads a file already on the device (a file in a chat), off the UI thread. */
    public static void readFile(File file, Utilities.Callback<Picked> onResult) {
        readPicked(Uri.fromFile(file), onResult);
    }

    public static void discard(Picked picked) {
        if (picked != null && picked.file != null) {
            picked.file.delete();
        }
    }

    private static File copyPicked(Uri uri) {
        Context context = ApplicationLoader.applicationContext;
        if (uri == null || context == null) {
            return null;
        }
        File folder = new File(context.getCacheDir(), PICKED_DIR);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return null;
        }
        File[] old = folder.listFiles();
        if (old != null) {
            long dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            for (File file : old) {
                if (file.lastModified() < dayAgo) {
                    file.delete();
                }
            }
        }
        File target = new File(folder, "picked_" + System.nanoTime() + EXTENSION);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            boolean fits;
            try (OutputStream out = new FileOutputStream(target)) {
                fits = copy(in, out, MAX_ARCHIVE_BYTES);
            }
            if (!fits) {
                target.delete();
                return new File(folder, "too_large");
            }
            return target;
        } catch (Throwable e) {
            FileLog.e("MZGramIconPacks.copyPicked", e);
            target.delete();
            return null;
        }
    }

    private static Picked read(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry(METADATA);
            if (entry == null || entry.isDirectory()) {
                file.delete();
                return new Picked(null, null, ERROR_NOT_PACK);
            }
            byte[] bytes;
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = readAll(in, MAX_METADATA_BYTES);
            }
            if (bytes == null) {
                file.delete();
                return new Picked(null, null, ERROR_TOO_LARGE);
            }
            Pack pack = parseMetadata(new String(bytes, StandardCharsets.UTF_8), null, null);
            if (pack == null) {
                file.delete();
                return new Picked(null, null, ERROR_NOT_PACK);
            }
            Iterator<Map.Entry<String, String>> it = pack.icons.entrySet().iterator();
            while (it.hasNext()) {
                String path = safeEntry(it.next().getValue());
                ZipEntry picture = path == null ? null : zip.getEntry(path);
                if (picture == null || picture.isDirectory() || pictureType(path) == null) {
                    it.remove();
                }
            }
            if (pack.icons.isEmpty()) {
                file.delete();
                return new Picked(null, null, ERROR_NO_ICONS);
            }
            return new Picked(file, pack, ERROR_NONE);
        } catch (Throwable e) {
            FileLog.e("MZGramIconPacks.read", e);
            file.delete();
            return new Picked(null, null, ERROR_NOT_PACK);
        }
    }

    /** Installs a read file, off the UI thread; true on success comes on the UI thread. */
    public static void install(Picked picked, Utilities.Callback<Boolean> onResult) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = picked != null && picked.pack != null && installNow(picked);
            discard(picked);
            if (ok) {
                changed();
            }
            AndroidUtilities.runOnUIThread(() -> onResult.run(ok));
        });
    }

    private static boolean installNow(Picked picked) {
        Pack pack = picked.pack;
        String key = keyFor(pack.id);
        File root = root();
        if (!root.isDirectory() && !root.mkdirs()) {
            return false;
        }
        File temp = new File(root, ".tmp_" + System.nanoTime());
        try (ZipFile zip = new ZipFile(picked.file)) {
            if (!temp.mkdirs()) {
                return false;
            }
            LinkedHashMap<String, String> written = new LinkedHashMap<>();
            HashMap<String, String> fileForEntry = new HashMap<>();
            long total = 0;
            int index = 0;
            for (Map.Entry<String, String> icon : pack.icons.entrySet()) {
                String path = safeEntry(icon.getValue());
                if (path == null) {
                    continue;
                }
                String already = fileForEntry.get(path);
                if (already != null) {
                    written.put(icon.getKey(), already);
                    continue;
                }
                ZipEntry entry = zip.getEntry(path);
                String type = pictureType(path);
                if (entry == null || entry.isDirectory() || type == null) {
                    continue;
                }
                String name = (index++) + "." + type;
                File out = new File(temp, name);
                long size;
                try (InputStream in = zip.getInputStream(entry); OutputStream os = new FileOutputStream(out)) {
                    size = copyCounted(in, os, MAX_FILE_BYTES);
                }
                if (size < 0) {
                    out.delete();
                    continue;
                }
                total += size;
                if (total > MAX_TOTAL_BYTES) {
                    deleteTree(temp);
                    return false;
                }
                fileForEntry.put(path, name);
                written.put(icon.getKey(), name);
            }
            if (written.isEmpty()) {
                deleteTree(temp);
                return false;
            }
            JSONObject json = new JSONObject();
            json.put("schemaVersion", 1);
            json.put("packId", pack.id);
            json.put("packName", pack.name);
            json.put("author", pack.author);
            json.put("version", pack.version);
            if (pack.declaredTintable != null) {
                json.put("isTintable", pack.declaredTintable.booleanValue());
            }
            JSONObject icons = new JSONObject();
            for (Map.Entry<String, String> icon : written.entrySet()) {
                icons.put(icon.getKey(), icon.getValue());
            }
            json.put("icons", icons);
            try (OutputStream os = new FileOutputStream(new File(temp, METADATA))) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            boolean detected = detectTintable(temp, new HashSet<>(written.values()));
            File target = new File(root, key);
            deleteTree(target);
            if (!temp.renameTo(target)) {
                deleteTree(temp);
                return false;
            }
            SharedPreferences prefs = prefs();
            ArrayList<String> order = parseKeys(prefs.getString(KEY_ORDER, ""));
            order.remove(key);
            order.add(0, key);
            ArrayList<String> active = parseKeys(prefs.getString(KEY_ACTIVE, ""));
            if (!active.contains(key)) {
                active.add(key);
            }
            prefs.edit()
                    .putString(KEY_ORDER, TextUtils.join(",", order))
                    .putString(KEY_ACTIVE, TextUtils.join(",", active))
                    .putBoolean(KEY_DETECTED + key, detected)
                    .apply();
            return true;
        } catch (Throwable e) {
            FileLog.e("MZGramIconPacks.install", e);
            deleteTree(temp);
            return false;
        }
    }

    private static boolean detectTintable(File dir, Set<String> files) {
        int sampled = 0;
        for (String name : files) {
            if (name.endsWith(".svg")) {
                continue;
            }
            if (sampled++ >= SAMPLE_PICTURES) {
                break;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            String path = new File(dir, name).getAbsolutePath();
            BitmapFactory.decodeFile(path, options);
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize(options.outWidth, options.outHeight, 64);
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap == null) {
                continue;
            }
            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            bitmap.recycle();
            if (isColorful(pixels)) {
                return false;
            }
        }
        return true;
    }

    // ---- Replacing an icon ----

    /**
     * The icon to show in place of resId, or null to keep Telegram's own.
     * Called by MZGramIconsResources on every drawable asked for.
     */
    public static Drawable drawableFor(Source source, int resId, int density, Resources.Theme theme) {
        if (!MZGramConfig.iconPacks || resId == 0 || (resId >>> 24) != 0x7f) {
            return null;
        }
        State s = state();
        if (!s.owner.isEmpty()) {
            Drawable fromPack = fromPack(s, source, resId, density, theme);
            if (fromPack != null) {
                return fromPack;
            }
        }
        if (s.builtin != null) {
            int mapped = s.builtin.get(resId, 0);
            if (mapped != 0 && mapped != resId) {
                try {
                    return source.original(mapped, density, theme);
                } catch (Throwable e) {
                    FileLog.e("MZGramIconPacks.builtin", e);
                }
            }
        }
        return null;
    }

    private static Drawable fromPack(State s, Source source, int resId, int density, Resources.Theme theme) {
        Resources res = source.resources();
        int dpi = density != 0 ? density : res.getDisplayMetrics().densityDpi;
        long cacheKey = ((long) resId << 32) | (dpi & 0xffffffffL);
        if (missing.contains(cacheKey)) {
            return null;
        }
        String name = nameOf(res, resId);
        Pack pack = name == null ? null : s.owner.get(name);
        if (pack == null) {
            missing.add(cacheKey);
            return null;
        }
        boolean tinted = !Boolean.FALSE.equals(s.tinted.get(pack.key));
        Bitmap bitmap = bitmaps.get(cacheKey);
        if (bitmap == null || bitmap.isRecycled()) {
            bitmap = null;
            try {
                int width = AndroidUtilities.dp(24);
                int height = width;
                Drawable original = source.original(resId, density, theme);
                if (original != null && original.getIntrinsicWidth() > 0 && original.getIntrinsicHeight() > 0) {
                    width = original.getIntrinsicWidth();
                    height = original.getIntrinsicHeight();
                }
                bitmap = render(new File(pack.dir, pack.icons.get(name)), width, height, tinted);
            } catch (Throwable e) {
                FileLog.e("MZGramIconPacks.render", e);
            }
            if (bitmap == null) {
                missing.add(cacheKey);
                return null;
            }
            bitmap.setDensity(dpi);
            bitmaps.put(cacheKey, bitmap);
        }
        return tinted ? new BitmapDrawable(res, bitmap) : new FixedBitmapDrawable(res, bitmap);
    }

    private static String nameOf(Resources res, int resId) {
        synchronized (lock) {
            String name = names.get(resId);
            if (name != null) {
                return name.isEmpty() ? null : name;
            }
        }
        String name;
        try {
            name = res.getResourceEntryName(resId);
        } catch (Throwable e) {
            name = null;
        }
        synchronized (lock) {
            names.put(resId, name == null ? "" : name);
        }
        return name;
    }

    /** A picture of a pack, drawn width x height; white with its own alpha when tinted. */
    static Bitmap render(File file, int width, int height, boolean tinted) throws IOException {
        if (file == null || !file.isFile() || width <= 0 || height <= 0) {
            return null;
        }
        Bitmap drawn;
        if (file.getName().endsWith(".svg")) {
            byte[] bytes;
            try (InputStream in = new FileInputStream(file)) {
                bytes = readAll(in, MAX_FILE_BYTES);
            }
            String svg = bytes == null ? null : normalizeSvg(new String(bytes, StandardCharsets.UTF_8));
            if (svg == null) {
                return null;
            }
            drawn = SvgHelper.getBitmap(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), width, height, tinted);
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize(options.outWidth, options.outHeight, Math.max(width, height));
            drawn = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
        if (drawn == null) {
            return null;
        }
        // The size of Telegram's own icon, the picture fitted in its middle
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (tinted) {
            paint.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
        }
        float scale = Math.min(width / (float) drawn.getWidth(), height / (float) drawn.getHeight());
        int w = Math.round(drawn.getWidth() * scale);
        int h = Math.round(drawn.getHeight() * scale);
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        canvas.drawBitmap(drawn, null, new Rect(left, top, left + w, top + h), paint);
        drawn.recycle();
        return out;
    }

    /** A colourful pack's icon: colour filters and tints of the theme leave it as it is. */
    static final class FixedBitmapDrawable extends BitmapDrawable {

        FixedBitmapDrawable(Resources res, Bitmap bitmap) {
            super(res, bitmap);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public void setColorFilter(int color, PorterDuff.Mode mode) {
        }

        @Override
        public void setTintList(ColorStateList tint) {
        }

        @Override
        public void setTintMode(PorterDuff.Mode tintMode) {
        }
    }

    // ---- Pure helpers (tested in MZGramIconPacksTest) ----

    /**
     * The pack described by metadata.json, or null when it is not one.
     * Drawable names that are not resource names are dropped.
     */
    public static Pack parseMetadata(String text, String key, File dir) {
        try {
            JSONObject json = new JSONObject(text);
            JSONObject icons = json.optJSONObject("icons");
            if (icons == null) {
                return null;
            }
            String id = json.optString("packId", "").trim();
            String name = json.optString("packName", "").trim();
            if (TextUtils.isEmpty(id)) {
                id = name;
            }
            if (TextUtils.isEmpty(id)) {
                return null;
            }
            if (TextUtils.isEmpty(name)) {
                name = id;
            }
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            Iterator<String> it = icons.keys();
            while (it.hasNext() && map.size() < MAX_ICONS) {
                String resource = it.next();
                String file = icons.optString(resource, "");
                if (RESOURCE_NAME.matcher(resource).matches() && !TextUtils.isEmpty(file)) {
                    map.put(resource, file);
                }
            }
            Boolean declared = json.has("isTintable") ? json.optBoolean("isTintable", true) : null;
            return new Pack(key != null ? key : keyFor(id), id, limit(name, 64), limit(json.optString("author", "").trim(), 64),
                    limit(json.optString("version", "1.0").trim(), 32), declared, map, dir);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String limit(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    /** The folder name for a pack id: the id itself when it is safe, otherwise a hash of it. */
    public static String keyFor(String id) {
        if (id != null && SAFE_KEY.matcher(id).matches() && !id.startsWith(".")) {
            return id;
        }
        return "pack_" + Utilities.MD5(id == null ? "" : id);
    }

    /** An archive path without "..", a leading slash or backslashes, or null. */
    public static String safeEntry(String path) {
        if (path == null) {
            return null;
        }
        String p = path.trim();
        if (p.isEmpty() || p.length() > 512 || p.startsWith("/") || p.indexOf('\\') >= 0 || p.indexOf(':') >= 0 || p.indexOf('\0') >= 0) {
            return null;
        }
        for (String part : p.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return null;
            }
        }
        return p;
    }

    /** "png", "webp", "jpg" or "svg" for a picture a pack may hold, otherwise null. */
    public static String pictureType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "png";
        } else if (lower.endsWith(".webp")) {
            return "webp";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "jpg";
        } else if (lower.endsWith(".svg")) {
            return "svg";
        }
        return null;
    }

    /**
     * True when a picture has colour: more than 8 in 100 of its visible
     * pixels are far from grey. Grey, black and white pictures are tinted.
     */
    public static boolean isColorful(int[] pixels) {
        int visible = 0;
        int colored = 0;
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 128) {
                continue;
            }
            visible++;
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
            if (chroma > 48) {
                colored++;
            }
        }
        return visible > 0 && colored * 100 > visible * 8;
    }

    private static final Pattern SVG_ROOT = Pattern.compile("<svg\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_END = Pattern.compile("</svg\\s*>", Pattern.CASE_INSENSITIVE);

    /**
     * An SVG that Telegram's SvgHelper draws right: SvgHelper takes the size
     * from width and height and ignores where the viewBox starts, so the root
     * keeps only the viewBox, moved to 0 0, and "currentColor" becomes white.
     * Null when there is no svg element.
     */
    public static String normalizeSvg(String svg) {
        if (svg == null) {
            return null;
        }
        Matcher root = SVG_ROOT.matcher(svg);
        if (!root.find()) {
            return null;
        }
        String attrs = root.group(1);
        boolean selfClosing = attrs.trim().endsWith("/");
        float minX = 0, minY = 0, w = 0, h = 0;
        String viewBox = attr(attrs, "viewBox");
        if (viewBox != null) {
            String[] parts = viewBox.trim().split("[\\s,]+");
            if (parts.length == 4) {
                try {
                    minX = Float.parseFloat(parts[0]);
                    minY = Float.parseFloat(parts[1]);
                    w = Float.parseFloat(parts[2]);
                    h = Float.parseFloat(parts[3]);
                } catch (NumberFormatException ignored) {
                    w = h = 0;
                }
            }
        }
        if (w <= 0 || h <= 0) {
            minX = minY = 0;
            w = number(attr(attrs, "width"));
            h = number(attr(attrs, "height"));
        }
        if (w <= 0 || h <= 0) {
            w = h = 24;
        }
        String fill = attr(attrs, "fill");
        StringBuilder out = new StringBuilder(svg.length() + 160);
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(plain(w)).append(' ').append(plain(h)).append("\">");
        if (selfClosing) {
            out.append("</svg>");
            return out.toString();
        }
        out.append("<g transform=\"translate(").append(plain(-minX)).append(',').append(plain(-minY)).append(")\"");
        if (fill != null && !fill.equalsIgnoreCase("currentColor")) {
            out.append(" fill=\"").append(fill).append('"');
        }
        out.append('>');
        String body = svg.substring(root.end());
        Matcher end = SVG_END.matcher(body);
        int stop = body.length();
        while (end.find()) {
            stop = end.start();
        }
        out.append(body, 0, stop);
        out.append("</g></svg>");
        return out.toString().replace("currentColor", "#ffffff");
    }

    private static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("(?:^|\\s)" + name + "\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL).matcher(attrs);
        return m.find() ? m.group(2) : null;
    }

    private static float number(String value) {
        if (value == null) {
            return 0;
        }
        Matcher m = Pattern.compile("^\\s*([0-9]*\\.?[0-9]+)\\s*(px)?\\s*$").matcher(value);
        if (!m.find()) {
            return 0;
        }
        try {
            return Float.parseFloat(m.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String plain(float value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    public static ArrayList<String> parseKeys(String text) {
        ArrayList<String> keys = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return keys;
        }
        for (String part : text.split(",")) {
            String key = part.trim();
            if (SAFE_KEY.matcher(key).matches() && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    // ---- Files ----

    private static int sampleSize(int width, int height, int target) {
        int sample = 1;
        if (width <= 0 || height <= 0 || target <= 0) {
            return sample;
        }
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
            sample *= 2;
        }
        return sample;
    }

    private static byte[] readAll(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return copy(in, out, max) ? out.toByteArray() : null;
    }

    private static boolean copy(InputStream in, OutputStream out, long max) throws IOException {
        return copyCounted(in, out, max) >= 0;
    }

    // The bytes copied, or -1 when there are more than max
    private static long copyCounted(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > max) {
                return -1;
            }
            out.write(buffer, 0, read);
        }
        return total;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }
}
