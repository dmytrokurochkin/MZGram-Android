package org.mzgram.plugins;

public final class PluginRuntime {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private static final ThreadLocal<int[]> PYTHON_DEPTH = new ThreadLocal<>();

    private PluginRuntime() {
    }

    public static String enter(String pluginId) {
        String previous = CURRENT.get();
        CURRENT.set(pluginId);
        return previous;
    }

    public static void exit(String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static String current() {
        return CURRENT.get();
    }

    public static void enterPython() {
        int[] depth = PYTHON_DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            PYTHON_DEPTH.set(depth);
        }
        depth[0]++;
    }

    public static void exitPython() {
        int[] depth = PYTHON_DEPTH.get();
        if (depth == null) {
            return;
        }
        if (--depth[0] <= 0) {
            PYTHON_DEPTH.remove();
        }
    }

    public static boolean isPythonActive() {
        int[] depth = PYTHON_DEPTH.get();
        return depth != null && depth[0] > 0;
    }
}
