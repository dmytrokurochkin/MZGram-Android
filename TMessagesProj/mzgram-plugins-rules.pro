# MZGram: used only in a build with -PmzgramPlugins=true (consumerProguardFiles
# in TMessagesProj/build.gradle). MZGramPythonRuntime finds the Chaquopy
# backend by its name, so R8 must keep the class and its constructor.
-keep class org.telegram.messenger.mzgram.plugins.MZGramChaquopyBackend {
    public <init>();
    public *;
}

# mzgram_plugins/settings.py reads and writes plugin settings through this
# class by its name (jclass), so R8 must keep its static methods.
-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginSettingsStore {
    public static *;
}

# mzgram_plugins/menus.py hands menu items of plugins to this class by its
# name (jclass), so R8 must keep its static methods.
-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginMenus {
    public static *;
}

# mzgram_plugins/loader.py hands the log lines of plugins to this class by
# its name (jclass), so R8 must keep its static methods.
-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginEngine {
    public static *;
}

# mzgram_plugins/hooks.py hooks methods through this class by its name
# (jclass), so R8 must keep its static methods.
-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginHooks {
    public static *;
}

# mzgram_plugins/permissions.py marks the running plugin and checks its
# permissions through this class by its name (jclass), so R8 must keep its
# static methods.
-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginPermissions {
    public static *;
}

# mzgram_plugins/sink_gate.py asks the rules of the sink gates and puts the
# Java gates on through these classes by their names (jclass), so R8 must keep
# their static methods. The gates skip their own frames on the stack by the
# class name, so the names of the inner classes stay too.
-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginSinkPolicy {
    public static *;
}
-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginSinkGate {
    public static *;
}
-keepnames class org.telegram.messenger.mzgram.plugins.MZGramPluginSinkGate$*

-keep class org.telegram.messenger.mzgram.plugins.MZGramPluginWatchdog {
    public static *;
}

# Aliuhook: its native part calls back into these classes by name, plugins
# read MethodHookParam (thisObject, args, method, getResult, setResult) from
# Python, MZGramPluginMethodHook reads its returnEarly field, and the guard on
# XposedBridge.hookMethod needs the method kept as it is (not inlined).
-keep class de.robv.android.xposed.** { *; }
