# MZGram: used only in a build with -PmzgramPlugins=true (consumerProguardFiles
# in TMessagesProj/build.gradle). MZGramPythonRuntime finds the Chaquopy
# backend by its name, so R8 must keep the class and its constructor.
-keep class org.telegram.messenger.mzgram.plugins.MZGramChaquopyBackend {
    public <init>();
    public *;
}
