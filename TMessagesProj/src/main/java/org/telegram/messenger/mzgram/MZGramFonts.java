/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * App font. Telegram draws the regular text with the font of the phone and
 * ships its own Roboto files for the medium (bold), italic, condensed and
 * extra bold text. Here the user can keep that (the default, untouched), use
 * the font of the phone for that text too, or a TTF or OTF font file of their
 * own. The font file also replaces the default font of the app process where
 * Android allows it, so the regular text follows. The mode is read once per
 * process: a change takes effect after a restart.
 *
 * The system font mapping follows Nekogram TypefaceHelper (GPL-3.0):
 * the medium weight check and the system families for the asset fonts.
 */

package org.telegram.messenger.mzgram;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class MZGramFonts {

    public static final int TELEGRAM = 0;
    public static final int SYSTEM = 1;
    public static final int FILE = 2;

    private static final String FILE_NAME = "mzgram_app_font";
    private static final long MAX_SIZE = 32L * 1024 * 1024;

    private static final String ASSET_ITALIC = "fonts/ritalic.ttf";
    private static final String ASSET_CONDENSED_BOLD = "fonts/rcondensedbold.ttf";

    private static final int KIND_MEDIUM = 0;
    private static final int KIND_MEDIUM_ITALIC = 1;
    private static final int KIND_ITALIC = 2;
    private static final int KIND_CONDENSED_BOLD = 3;
    private static final int KIND_EXTRA_BOLD = 4;

    // The default font before applyDefault() could replace it
    private static final Typeface ORIGINAL_DEFAULT = Typeface.DEFAULT;

    private static final Object lock = new Object();
    private static final HashMap<String, Typeface> cache = new HashMap<>();
    private static volatile int activeMode = -1;
    private static Typeface fileTypeface;
    private static Boolean mediumSupported;

    private MZGramFonts() {
    }

    /** True when the phone can load a font file (Typeface.Builder, Android 8). */
    public static boolean fileSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /** The chosen mode, as the settings show it. */
    public static int mode() {
        final int mode = MZGramConfig.appFont;
        return mode == SYSTEM || mode == FILE && fileSupported() ? mode : TELEGRAM;
    }

    private static File fontFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), FILE_NAME);
    }

    /** The mode of this process, read once. */
    private static int activeMode() {
        int mode = activeMode;
        if (mode < 0) {
            synchronized (lock) {
                if (activeMode < 0) {
                    activeMode = resolveMode();
                }
                mode = activeMode;
            }
        }
        return mode;
    }

    private static int resolveMode() {
        try {
            final int mode = mode();
            if (mode == SYSTEM) {
                return SYSTEM;
            }
            if (mode == FILE) {
                final File file = fontFile();
                if (file.isFile()) {
                    fileTypeface = new Typeface.Builder(file).build();
                    if (fileTypeface != null) {
                        return FILE;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e("MZGramFonts.resolveMode", e);
        }
        return TELEGRAM;
    }

    private static int kind(String assetPath) {
        switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM:
                return KIND_MEDIUM;
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC:
                return KIND_MEDIUM_ITALIC;
            case ASSET_ITALIC:
                return KIND_ITALIC;
            case ASSET_CONDENSED_BOLD:
                return KIND_CONDENSED_BOLD;
            case AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD:
                return KIND_EXTRA_BOLD;
            default:
                return -1;
        }
    }

    /**
     * The font for one of Telegram's Roboto text files, or null to keep
     * Telegram's own (the default mode, the mono, digits, icons and serif
     * files always stay).
     */
    public static Typeface typeface(String assetPath) {
        final int mode = activeMode();
        if (mode == TELEGRAM || assetPath == null) {
            return null;
        }
        final int kind = kind(assetPath);
        if (kind < 0) {
            return null;
        }
        synchronized (lock) {
            Typeface typeface = cache.get(assetPath);
            if (typeface == null) {
                try {
                    typeface = mode == FILE ? fromFile(kind) : fromSystem(kind);
                } catch (Throwable e) {
                    FileLog.e("MZGramFonts.typeface", e);
                }
                if (typeface != null) {
                    cache.put(assetPath, typeface);
                }
            }
            return typeface;
        }
    }

    private static Typeface fromFile(int kind) {
        final Typeface base = fileTypeface;
        if (base == null) {
            return null;
        }
        switch (kind) {
            case KIND_MEDIUM_ITALIC:
                return Typeface.create(base, Typeface.BOLD_ITALIC);
            case KIND_ITALIC:
                return Typeface.create(base, Typeface.ITALIC);
            case KIND_EXTRA_BOLD:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return Typeface.create(base, 800, false);
                }
                return Typeface.create(base, Typeface.BOLD);
            case KIND_MEDIUM:
            case KIND_CONDENSED_BOLD:
            default:
                return Typeface.create(base, Typeface.BOLD);
        }
    }

    private static Typeface fromSystem(int kind) {
        switch (kind) {
            case KIND_MEDIUM:
                return mediumSupported() ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.BOLD);
            case KIND_MEDIUM_ITALIC:
                return mediumSupported() ? Typeface.create("sans-serif-medium", Typeface.ITALIC) : Typeface.create("sans-serif", Typeface.BOLD_ITALIC);
            case KIND_ITALIC:
                return Typeface.create("sans-serif", Typeface.ITALIC);
            case KIND_CONDENSED_BOLD:
                return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case KIND_EXTRA_BOLD:
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 800, false);
                }
                return Typeface.create("sans-serif-black", Typeface.NORMAL);
        }
    }

    // Not every phone font has a medium weight: then bold, as Nekogram does
    private static boolean mediumSupported() {
        if (mediumSupported == null) {
            mediumSupported = rendersDifferently(Typeface.create("sans-serif-medium", Typeface.NORMAL), ORIGINAL_DEFAULT);
        }
        return mediumSupported;
    }

    private static boolean rendersDifferently(Typeface a, Typeface b) {
        final int size = 48;
        Bitmap first = null;
        Bitmap second = null;
        try {
            first = Bitmap.createBitmap(size * 3, size, Bitmap.Config.ARGB_8888);
            second = Bitmap.createBitmap(size * 3, size, Bitmap.Config.ARGB_8888);
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(size);
            paint.setTypeface(a);
            new Canvas(first).drawText("Rgж", 0, size * 0.8f, paint);
            paint.setTypeface(b);
            new Canvas(second).drawText("Rgж", 0, size * 0.8f, paint);
            return !first.sameAs(second);
        } catch (Throwable e) {
            FileLog.e("MZGramFonts.rendersDifferently", e);
            return false;
        } finally {
            if (first != null) {
                first.recycle();
            }
            if (second != null) {
                second.recycle();
            }
        }
    }

    /**
     * With a font file, also the default font of the app process, so the
     * regular text follows. Called once at the start of the process. Each
     * step is on its own: what Android does not allow is skipped and that
     * text keeps the font of the phone.
     */
    public static void applyDefault() {
        try {
            if (activeMode() != FILE) {
                return;
            }
            final Typeface regular = fileTypeface;
            if (regular == null) {
                return;
            }
            final Typeface bold = Typeface.create(regular, Typeface.BOLD);
            final Typeface italic = Typeface.create(regular, Typeface.ITALIC);
            final Typeface boldItalic = Typeface.create(regular, Typeface.BOLD_ITALIC);
            setStatic("DEFAULT", regular);
            setStatic("SANS_SERIF", regular);
            setStatic("DEFAULT_BOLD", bold);
            try {
                final Field field = Typeface.class.getDeclaredField("sDefaults");
                field.setAccessible(true);
                final Object defaults = field.get(null);
                if (defaults instanceof Typeface[] && ((Typeface[]) defaults).length >= 4) {
                    final Typeface[] array = (Typeface[]) defaults;
                    array[Typeface.NORMAL] = regular;
                    array[Typeface.BOLD] = bold;
                    array[Typeface.ITALIC] = italic;
                    array[Typeface.BOLD_ITALIC] = boldItalic;
                }
            } catch (Throwable e) {
                FileLog.d("MZGramFonts: sDefaults not replaced " + e);
            }
            try {
                final Field field = Typeface.class.getDeclaredField("sSystemFontMap");
                field.setAccessible(true);
                final Object map = field.get(null);
                if (map instanceof Map) {
                    @SuppressWarnings("unchecked") final Map<String, Typeface> fonts = (Map<String, Typeface>) map;
                    try {
                        fonts.put("sans-serif", regular);
                    } catch (UnsupportedOperationException e) {
                        final HashMap<String, Typeface> copy = new HashMap<>(fonts);
                        copy.put("sans-serif", regular);
                        field.set(null, copy);
                    }
                }
            } catch (Throwable e) {
                FileLog.d("MZGramFonts: sans-serif not replaced " + e);
            }
            try {
                final Method method = Typeface.class.getDeclaredMethod("setDefault", Typeface.class);
                method.setAccessible(true);
                method.invoke(null, regular);
            } catch (Throwable e) {
                FileLog.d("MZGramFonts: default not replaced " + e);
            }
        } catch (Throwable e) {
            FileLog.e("MZGramFonts.applyDefault", e);
        }
    }

    private static void setStatic(String name, Typeface typeface) {
        try {
            final Field field = Typeface.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, typeface);
        } catch (Throwable e) {
            FileLog.d("MZGramFonts: " + name + " not replaced " + e);
        }
    }

    /**
     * Copies a TTF or OTF file into the app and returns its name to show, or
     * null when it is not a font the phone can load. Not on the UI thread.
     */
    public static String importFont(Uri uri) {
        if (uri == null || !fileSupported()) {
            return null;
        }
        final ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        final File target = fontFile();
        final File temp = new File(target.getPath() + ".tmp");
        try {
            try (InputStream in = resolver.openInputStream(uri); OutputStream out = new FileOutputStream(temp)) {
                if (in == null) {
                    return null;
                }
                final byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_SIZE) {
                        throw new IOException("font file too big");
                    }
                    out.write(buffer, 0, read);
                }
            }
            if (!isFont(temp)) {
                return null;
            }
            if (!temp.renameTo(target)) {
                throw new IOException("cannot move the font file");
            }
            return displayName(resolver, uri);
        } catch (Throwable e) {
            FileLog.e("MZGramFonts.importFont", e);
            return null;
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    private static boolean isFont(File file) {
        final byte[] head = new byte[4];
        try (InputStream in = new java.io.FileInputStream(file)) {
            if (in.read(head) != 4) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        final boolean trueType = head[0] == 0 && head[1] == 1 && head[2] == 0 && head[3] == 0;
        final String tag = new String(head, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!trueType && !"OTTO".equals(tag) && !"true".equals(tag) && !"ttcf".equals(tag)) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Throws when the phone cannot load the font
                new android.graphics.fonts.Font.Builder(file).build();
                return true;
            }
            final Typeface typeface = new Typeface.Builder(file).build();
            return typeface != null && rendersDifferently(typeface, ORIGINAL_DEFAULT);
        } catch (Throwable e) {
            FileLog.e("MZGramFonts.isFont", e);
            return false;
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Throwable e) {
            FileLog.e("MZGramFonts.displayName", e);
        }
        if (TextUtils.isEmpty(name)) {
            name = uri.getLastPathSegment();
        }
        if (name == null) {
            return "";
        }
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Removes the copied font file (the app font is no longer a file). */
    public static void deleteFile() {
        try {
            final File file = fontFile();
            if (file.exists()) {
                file.delete();
            }
        } catch (Throwable e) {
            FileLog.e("MZGramFonts.deleteFile", e);
        }
    }
}
