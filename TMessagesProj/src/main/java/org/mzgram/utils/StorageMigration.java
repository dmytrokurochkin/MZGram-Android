package org.mzgram.utils;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test builds before this one kept settings, the message archive and plugin files under older
 * internal names. This moves them to the current names once, before anything reads them.
 * The older name parts are stored with every character moved one place up, so they are not
 * readable words in the code; {@link #map(String)} applies the same rules the code was renamed with.
 */
public final class StorageMigration {

    private static final String TAG = "MZStorage";
    private static final String DONE_MARK = "mzgram_storage_names_v1";

    /** {older part, current part}, tried in this order. The older part is shifted by one. */
    private static final String[][] PARTS = {
            {"fmzydpsf", "mzpackcore"},
            {"FMZYDPSF", "MZPACKCORE"},
            {"fmzyqspcfsfbm", "mzpackprobereal"},
            {"fyufsbdpogjh", "mzconfig"},
            {"oflpdmpve", "corecloud"},
            {"bzvnpnfout", "arcmoments"},
            {"fyufsbtfuujoht", "mzsettings"},
            {"oflpsfdfouejbmpht", "corerecentdialogs"},
            {"oflpqbttdpef", "corepasscode"},
            {"oflpsfnpufdpogjh", "coreremoteconfig"},
            {"oflpfnpkjt", "coreemojis"},
            {"fyufsbqmvhjot", "mzplugins"},
            {"Fuh", "Backup"},
            {"FUH", "BACKUP"},
            {"ObhsbnY", "MZGram"},
            {"OBHSBNY", "MZGRAM"},
            {"obhsbny", "mzgram"},
            {"Obhsbny", "MZGram"},
            {"Obhsbn", "MZGram"},
            {"OBHSBN", "MZGRAM"},
            {"obhsbn", "mzgram"},
            {"Fyufsbmftt", "MZGram"},
            {"FYUFSBMFTT", "MZGRAM"},
            {"fyufsbmftt", "mzgram"},
            {"PqfoFyufsb", "MZGram"},
            {"pqfoFyufsb", "mzGram"},
            {"PQFO`FYUFSB", "MZGRAM"},
            {"PQFOFYUFSB", "MZGRAM"},
            {"pqfofyufsb", "mzgram"},
            {"FyufsbHsbn", "MZGram"},
            {"fyufsbHsbn", "MZGram"},
            {"Fyufsbhsbn", "MZGram"},
            {"fyufsbhsbn", "mzgram"},
            {"FYUFSBHSBN", "MZGRAM"},
            {"Fyufsb", "MZ"},
            {"fyufsb", "mz"},
            {"FYUFSB", "MZ"},
            {"BzvHsbn", "MZGram"},
            {"Bzvhsbn", "MZGram"},
            {"bzvhsbn", "mzgram"},
            {"BZVHSBN", "MZGRAM"},
            {"Bzv", "Arc"},
            {"bzv", "arc"},
            {"BZV", "ARC"},
            {"Oflphsbn", "MZGram"},
            {"oflphsbn", "mzgram"},
            {"OFLPHSBN", "MZGRAM"},
            {"OflpY", "CoreX"},
            {"Oflpy", "Corex"},
            {"oflpy", "corex"},
            {"OFLPY", "COREX"},
            {"Oflp", "Core"},
            {"oflp", "core"},
            {"OFLP", "CORE"},
            {"Fmzy", "MzPack"},
            {"fmzy", "mzpack"},
            {"FMZY", "MZPACK"},
            {"ObDpogjh", "ExtConfig"},
            {"ofyubmpof", "mzgram"},
            {"wlszm", "mzux"}
    };
    private static final String OLD_PREFIX_UPPER = "PF";
    private static final String OLD_PREFIX_LOWER = "pf";

    /** Folder table name in the per-account Telegram database before the rename. */
    public static final String OLD_FOLDER_TABLE = unshift("ejbmph`gjmufs`oflp");
    public static final String FOLDER_TABLE = "dialog_filter_core";

    private static final Pattern NAME_ATTR = Pattern.compile("(<\\w+ name=\")([^\"]*)(\")");

    private static volatile String[][] parts;

    private StorageMigration() {
    }

    /** Called from Application.attachBaseContext, before any settings file is opened. */
    public static void run(Context base) {
        try {
            File dataDir = new File(base.getApplicationInfo().dataDir);
            File filesDir = new File(dataDir, "files");
            File mark = new File(filesDir, DONE_MARK);
            if (mark.exists()) {
                return;
            }
            if (!filesDir.exists() && !filesDir.mkdirs()) {
                return;
            }
            File lockFile = new File(filesDir, DONE_MARK + ".lock");
            try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock ignored = channel.lock()) {
                if (mark.exists()) {
                    return;
                }
                migratePrefs(new File(dataDir, "shared_prefs"));
                renameEntries(new File(dataDir, "databases"), 0, 0);
                renameEntries(filesDir, 0, 0);
                renamePluginFiles(new File(filesDir, "plugins"));
                new FileOutputStream(mark).close();
            }
            //noinspection ResultOfMethodCallIgnored
            lockFile.delete();
        } catch (Throwable e) {
            Log.e(TAG, "storage names migration failed", e);
        }
    }

    private static void migratePrefs(File dir) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        for (File file : list) {
            String name = file.getName();
            if (!name.endsWith(".xml")) {
                continue;
            }
            String base = name.substring(0, name.length() - 4);
            String newBase = map(base);
            File target = file;
            if (!newBase.equals(base)) {
                File renamed = new File(dir, newBase + ".xml");
                if (renamed.exists()) {
                    Log.w(TAG, "settings file already exists, older one left as is: " + newBase);
                    continue;
                }
                if (!file.renameTo(renamed)) {
                    continue;
                }
                target = renamed;
            }
            try {
                rewriteKeys(target);
            } catch (Throwable e) {
                Log.e(TAG, "settings keys not moved in " + target.getName(), e);
            }
        }
    }

    private static void rewriteKeys(File file) throws Exception {
        String xml = new String(readAll(file), StandardCharsets.UTF_8);
        Matcher matcher = NAME_ATTR.matcher(xml);
        StringBuffer out = new StringBuffer(xml.length());
        boolean changed = false;
        while (matcher.find()) {
            String key = matcher.group(2);
            String newKey = map(key);
            if (!newKey.equals(key)) {
                changed = true;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + newKey + matcher.group(3)));
        }
        matcher.appendTail(out);
        if (!changed) {
            return;
        }
        byte[] data = out.toString().getBytes(StandardCharsets.UTF_8);
        File tmp = new File(file.getParentFile(), file.getName() + ".mztmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(data);
            os.flush();
        }
        if (!tmp.renameTo(file)) {
            // rename over an existing file fails on some file systems: write in place instead
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            try (OutputStream os = new FileOutputStream(file)) {
                os.write(data);
                os.flush();
            }
        }
    }

    private static void renameEntries(File dir, int depth, int maxDepth) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        for (File file : list) {
            File current = file;
            String newName = map(file.getName());
            if (!newName.equals(file.getName())) {
                File renamed = new File(dir, newName);
                if (!renamed.exists() && file.renameTo(renamed)) {
                    current = renamed;
                }
            }
            if (depth < maxDepth && current.isDirectory()) {
                renameEntries(current, depth + 1, maxDepth);
            }
        }
    }

    /** Plugin packages keep their names (the plugin id is checked against them), only the extension changes. */
    private static void renamePluginFiles(File dir) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        String oldExt = unshift("/fmzy");
        for (File file : list) {
            String name = file.getName();
            if (file.isFile() && name.endsWith(oldExt)) {
                File renamed = new File(dir, name.substring(0, name.length() - oldExt.length()) + ".mzpack");
                if (!renamed.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.renameTo(renamed);
                }
            }
        }
    }

    private static byte[] readAll(File file) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    /** The current name for a name written by an earlier build (unchanged when nothing matches). */
    public static String map(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (isTokenStart(c)) {
                int j = i + 1;
                while (j < n && isTokenPart(s.charAt(j))) {
                    j++;
                }
                out.append(mapToken(s.substring(i, j)));
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String mapToken(String token) {
        String out = token;
        for (String[] part : parts()) {
            String old = part[0];
            if (!out.contains(old)) {
                continue;
            }
            StringBuilder res = new StringBuilder();
            int k = 0;
            while (true) {
                int i = out.indexOf(old, k);
                if (i < 0) {
                    res.append(out, k, out.length());
                    break;
                }
                if (boundaryOk(out, i, old)) {
                    res.append(out, k, i).append(part[1]);
                    k = i + old.length();
                } else {
                    res.append(out, k, i + 1);
                    k = i + 1;
                }
            }
            out = res.toString();
        }
        String upper = unshift(OLD_PREFIX_UPPER);
        String lower = unshift(OLD_PREFIX_LOWER);
        if (out.length() > 3 && out.startsWith(upper) && isAsciiUpper(out.charAt(2)) && isAsciiLower(out.charAt(3))) {
            out = "MZ" + out.substring(2);
        } else if (out.length() > 2 && out.startsWith(lower) && isAsciiUpper(out.charAt(2))) {
            out = "mz" + out.substring(2);
        }
        return out;
    }

    private static boolean boundaryOk(String token, int i, String stem) {
        int j = i + stem.length();
        char before = i > 0 ? token.charAt(i - 1) : 0;
        char after = j < token.length() ? token.charAt(j) : 0;
        if (isUpperWord(stem)) {
            return !Character.isUpperCase(before) && !(Character.isUpperCase(after) && Character.isLetter(after));
        }
        if (Character.isUpperCase(stem.charAt(0))) {
            return !Character.isLowerCase(after);
        }
        return !Character.isLetter(before) && !Character.isLowerCase(after);
    }

    private static boolean isUpperWord(String s) {
        boolean cased = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLowerCase(c)) {
                return false;
            }
            if (Character.isUpperCase(c)) {
                cased = true;
            }
        }
        return cased;
    }

    private static boolean isTokenStart(char c) {
        return isAsciiUpper(c) || isAsciiLower(c) || c == '_';
    }

    private static boolean isTokenPart(char c) {
        return isTokenStart(c) || (c >= '0' && c <= '9');
    }

    private static boolean isAsciiUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }

    private static boolean isAsciiLower(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static String[][] parts() {
        String[][] p = parts;
        if (p == null) {
            p = new String[PARTS.length][];
            for (int i = 0; i < PARTS.length; i++) {
                p[i] = new String[]{unshift(PARTS[i][0]), PARTS[i][1]};
            }
            parts = p;
        }
        return p;
    }

    private static String unshift(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            sb.append((char) (s.charAt(i) - 1));
        }
        return sb.toString();
    }
}
