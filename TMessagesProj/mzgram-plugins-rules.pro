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
