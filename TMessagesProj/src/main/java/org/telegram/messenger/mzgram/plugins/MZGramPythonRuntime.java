/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Python runtime for plugins (Chaquopy, CPython 3.11). It exists only in a
 * build made with -PmzgramPlugins=true: that build adds the Chaquopy Gradle
 * plugin and the backend in src/mzgramPlugins/java. The default build has no
 * Python at all; there isAvailable() is false and every call is a no-op stub,
 * so nothing changes for it.
 *
 * The interpreter never starts by itself: only start() starts it, on its own
 * thread, and all Python work runs on that thread. No packages are fetched on
 * the device: the bundled ones come with the build.
 *
 * The idea of running the plugin engine on Chaquopy is from exteraless
 * PythonPluginsEngine (GPL-3.0); the code here is MZGram's own.
 */

package org.telegram.messenger.mzgram.plugins;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public final class MZGramPythonRuntime {

    /** Implemented in src/mzgramPlugins/java (MZGramChaquopyBackend), only in a build with plugins. */
    public interface Backend {
        /** Starts the interpreter if it is not running yet. */
        void start(Context context) throws Throwable;

        /** "Python 3.11.x" and the versions of the bundled packages, one line each. */
        String describe() throws Throwable;

        /** module.function(argument) of the bundled Python code, its result as a string (null for None). */
        String call(String module, String function, String argument) throws Throwable;
    }

    private static final String BACKEND_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramChaquopyBackend";

    private static boolean resolved;
    private static Backend backend;
    private static DispatchQueue queue;

    private static volatile boolean started;
    private static volatile String description;
    private static volatile String lastError;

    private MZGramPythonRuntime() {
    }

    private static synchronized Backend backend() {
        if (!resolved) {
            resolved = true;
            try {
                backend = (Backend) Class.forName(BACKEND_CLASS).getDeclaredConstructor().newInstance();
            } catch (Throwable ignore) {
                // The default build: no Chaquopy, no backend
                backend = null;
            }
        }
        return backend;
    }

    private static synchronized DispatchQueue queue() {
        if (queue == null) {
            queue = new DispatchQueue("mzgramPython");
        }
        return queue;
    }

    /** True only in a build with the Python runtime (-PmzgramPlugins=true). */
    public static boolean isAvailable() {
        return backend() != null;
    }

    public static boolean isStarted() {
        return started;
    }

    /** What describe() returned after the start; null before it. */
    public static String getDescription() {
        return description;
    }

    /** Why the last start failed; null when it did not. */
    public static String getLastError() {
        return lastError;
    }

    /**
     * Starts the interpreter on the Python thread (the first start unpacks the
     * standard library and can take a few seconds). onDone gets true on the UI
     * thread once Python is running, false when it cannot run in this build or
     * failed to start.
     */
    public static void start(Utilities.Callback<Boolean> onDone) {
        final Backend runtime = backend();
        if (runtime == null) {
            lastError = "no Python runtime in this build";
            finish(onDone, false);
            return;
        }
        if (started) {
            finish(onDone, true);
            return;
        }
        queue().postRunnable(() -> {
            boolean ok = started;
            if (!ok) {
                try {
                    final long time = System.currentTimeMillis();
                    runtime.start(ApplicationLoader.applicationContext);
                    description = runtime.describe();
                    started = true;
                    lastError = null;
                    ok = true;
                    FileLog.d("MZGramPythonRuntime: started in " + (System.currentTimeMillis() - time) + " ms");
                } catch (Throwable e) {
                    lastError = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                    FileLog.e(e);
                }
            }
            finish(onDone, ok);
        });
    }

    /**
     * Calls module.function(argument) of the bundled Python code (src/mzgramPlugins/python)
     * on the Python thread, starting the interpreter first when it is not running.
     * onResult gets the string result on the UI thread, or null when Python is not
     * in this build, failed to start or the call raised (getLastError() says why).
     */
    public static void call(String module, String function, String argument, Utilities.Callback<String> onResult) {
        final Backend runtime = backend();
        if (runtime == null) {
            lastError = "no Python runtime in this build";
            deliver(onResult, null);
            return;
        }
        start(ok -> {
            if (!ok) {
                deliver(onResult, null);
                return;
            }
            queue().postRunnable(() -> {
                String result = null;
                try {
                    result = runtime.call(module, function, argument);
                } catch (Throwable e) {
                    lastError = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                    FileLog.e(e);
                }
                deliver(onResult, result);
            });
        });
    }

    private static void deliver(Utilities.Callback<String> onResult, String result) {
        if (onResult != null) {
            AndroidUtilities.runOnUIThread(() -> onResult.run(result));
        }
    }

    /**
     * Runs work on the Python thread, after the interpreter has started; false
     * (and nothing runs) when it has not. For the plugin engine.
     */
    public static boolean post(Runnable work) {
        if (!started || work == null) {
            return false;
        }
        return queue().postRunnable(work);
    }

    private static void finish(Utilities.Callback<Boolean> onDone, boolean ok) {
        if (onDone != null) {
            AndroidUtilities.runOnUIThread(() -> onDone.run(ok));
        }
    }
}
