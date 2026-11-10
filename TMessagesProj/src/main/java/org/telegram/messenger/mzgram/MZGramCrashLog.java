/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Local crash log. With the switch on, an uncaught exception is written to a
 * file in the app's private storage, and the next launch shows it once
 * (MZGramCrashReport) to read or copy. Nothing is sent anywhere. The handler
 * only adds the write before the handler that was there already, so the app
 * still crashes exactly as before; with the switch off it just passes the
 * exception on.
 *
 * Based on exteraless CrashLog (GPL-3.0): the report layout and the memory
 * state written for an OutOfMemoryError. Crashlytics keys, plugin counts and
 * the instance counts read through hidden APIs are left out.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MZGramCrashLog {

    private static final String FILE_NAME = "mzgram_last_crash.txt";
    private static final int MAX_LENGTH = 64 * 1024;

    private static boolean installed;
    private static volatile boolean recording;

    private MZGramCrashLog() {
    }

    /** At app start: the handler only when the switch is on (toggling it on installs it too). */
    public static void installIfEnabled() {
        if (MZGramConfig.crashReports) {
            install();
        }
    }

    /** Puts the write in front of the current default handler; once per process. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                if (MZGramConfig.crashReports) {
                    record(thread, error);
                }
            } catch (Throwable ignore) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else if (thread != null && thread.getThreadGroup() != null) {
                thread.getThreadGroup().uncaughtException(thread, error);
            }
        });
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    static void record(Thread thread, Throwable error) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null || error == null || recording) {
            return;
        }
        recording = true;
        try {
            final StringWriter stack = new StringWriter();
            final PrintWriter printer = new PrintWriter(stack);
            error.printStackTrace(printer);
            printer.flush();

            final StringBuilder report = new StringBuilder();
            report.append("MZGram ").append(BuildVars.BUILD_VERSION_STRING)
                    .append(" (").append(SharedConfig.buildVersion()).append(")\n");
            report.append("Android ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            report.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            report.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
            if (thread != null) {
                report.append("thread: ").append(thread.getName()).append('\n');
            }
            if (isOutOfMemory(error)) {
                appendMemoryState(report);
            }
            report.append('\n').append(stack);

            String text = report.toString();
            if (text.length() > MAX_LENGTH) {
                text = text.substring(0, MAX_LENGTH);
            }
            write(file(context), text);
        } catch (Throwable ignore) {
        } finally {
            recording = false;
        }
    }

    private static boolean isOutOfMemory(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof OutOfMemoryError) {
                return true;
            }
        }
        return false;
    }

    private static void appendMemoryState(StringBuilder report) {
        try {
            final Runtime runtime = Runtime.getRuntime();
            final long used = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
            final long max = runtime.maxMemory() >> 20;
            final long nativeHeap = Debug.getNativeHeapAllocatedSize() >> 20;
            report.append("heap: ").append(used).append(" / ").append(max)
                    .append(" MB, native: ").append(nativeHeap).append(" MB\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                final long uptime = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 60000L;
                report.append("uptime: ").append(uptime / 60).append(" h ").append(uptime % 60).append(" min\n");
            }
        } catch (Throwable ignore) {
        }
        try {
            int accounts = 0;
            int dialogs = 0;
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    accounts++;
                    dialogs += MessagesController.getInstance(a).dialogs_dict.size();
                }
            }
            report.append("accounts: ").append(accounts).append(", dialogs: ").append(dialogs).append('\n');
        } catch (Throwable ignore) {
        }
        try {
            final LaunchActivity activity = LaunchActivity.instance;
            final INavigationLayout layout = activity != null ? activity.getActionBarLayout() : null;
            if (layout != null) {
                report.append("screens: ").append(layout.getFragmentStack().size()).append('\n');
            }
        } catch (Throwable ignore) {
        }
        try {
            report.append("threads: ").append(Thread.activeCount()).append('\n');
        } catch (Throwable ignore) {
        }
    }

    /** The report of the last crash, deleted on the way out; null when there is none. */
    public static String take() {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        final File file = file(context);
        if (!file.exists() || file.length() == 0) {
            return null;
        }
        try {
            return read(file);
        } catch (Throwable ignore) {
            return null;
        } finally {
            clear();
        }
    }

    public static void clear() {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        try {
            final File file = file(context);
            if (file.exists()) {
                file.delete();
            }
        } catch (Throwable ignore) {
        }
    }

    private static String read(File file) throws Exception {
        final byte[] bytes = new byte[(int) Math.min(file.length(), MAX_LENGTH)];
        try (FileInputStream stream = new FileInputStream(file)) {
            int total = 0;
            int read;
            while (total < bytes.length && (read = stream.read(bytes, total, bytes.length - total)) > 0) {
                total += read;
            }
            return total > 0 ? new String(bytes, 0, total, StandardCharsets.UTF_8) : null;
        }
    }

    private static void write(File file, String text) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(text);
            writer.flush();
        }
    }
}
