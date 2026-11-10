/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The sink gates on the Java side: what the Python gate
 * (mzgram_plugins/sink_gate.py) can't see. Chaquopy gives plugins live Java
 * objects, so a plugin can open a connection with java.net.URL, start a
 * process with Runtime.exec or take a class by name with Class.forName, and
 * Python never hears of it. Here the sinks themselves are hooked (Aliuhook,
 * the same engine as the hooks of plugins). The rules are in
 * MZGramPluginSinkPolicy.
 *
 * A hook checks only when plugin code runs on the thread: the mark of
 * MZGramPluginPermissions, or, for a Java callback into Python (a Runnable
 * or a listener made by a plugin, called by the app), the plugin found by
 * its Python frames. For the app's own code a hook is two ThreadLocal reads.
 *
 * Most sinks are checked only when plugin code calls them itself (the
 * caller below the sink is Python): when a plugin opens a screen of
 * Telegram and Telegram loads, sends requests or reads its settings, that
 * is the app working, and refusing it would break the screen. The network
 * sockets and URL connections are checked on any call inside plugin code.
 *
 *   never      Runtime.exec, ProcessBuilder.start, the settings files of
 *              the plugin engine and of other plugins;
 *   native     System.load/loadLibrary (not for platform classes and
 *              static initializers, which only run inside plugin code);
 *   network    URL.openConnection, Socket.connect, DatagramSocket,
 *              DownloadManager.enqueue, MediaPlayer.setDataSource with a
 *              remote address, WebView loads, ImageLoader.loadHttpFile;
 *   classes    Class.forName (MZGramPluginSinkPolicy.classRule);
 *   hooks      dex class loaders, system overlay windows;
 *   settings   SharedPreferences of the app by name;
 *   messages   SendMessagesHelper (send), ConnectionsManager.sendRequest
 *              (by the request: MZGramPluginSinkPolicy.requestKey).
 *
 * A refusal throws what the method may throw (ConnectException,
 * ClassNotFoundException, SecurityException); sending and requests are
 * skipped quietly with an empty result, as if they had failed.
 *
 * A plugin with "hooks" can take these hooks off; there is no gate against
 * full control. Without Aliuhook (it failed to start twice, see
 * MZGramPluginHookGate) only the Python gate stands.
 *
 * Compiled only in a build with -PmzgramPlugins=true;
 * mzgram-plugins-rules.pro keeps its name for R8.
 *
 * Based on exteraless plugins/PluginSinkGate.java (GPL-3.0): the sinks, the
 * native load exceptions, the stack owner for Python callbacks and the
 * empty results; written anew for MZGram with the check of the caller.
 */

package org.telegram.messenger.mzgram.plugins;

import android.net.Uri;
import android.view.WindowManager;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

public final class MZGramPluginSinkGate {

    private static final String PYTHON_MODULE = "mzgram_plugins.sink_gate";
    private static final String OWN_PREFIX = "org.telegram.messenger.mzgram.plugins.MZGramPluginSinkGate";

    private static final String[] SEND_SINKS = {
            "sendMessage", "prepareSendingText", "prepareSendingMedia", "prepareSendingPhoto",
            "prepareSendingVideo", "prepareSendingDocument", "prepareSendingDocuments",
            "prepareSendingAudioDocuments", "sendCurrentLocation", "prepareSendingPoll",
            "prepareSendingArticle", "prepareSendingBotContextResult", "sendSticker", "sendGame",
            "sendScreenshotMessage", "retrySendMessage", "editMessage", "editRichMessage",
    };

    private static final String[] CODE_LOADERS = {
            "dalvik.system.DexClassLoader", "dalvik.system.PathClassLoader",
            "dalvik.system.InMemoryDexClassLoader", "dalvik.system.DelegateLastClassLoader",
    };

    // Set while a gate checks, so that the check itself (settings, classes) is not checked
    private static final ThreadLocal<Boolean> inside = new ThreadLocal<>();
    // How deep Python code runs on this thread (Java callbacks into Python, calls of the engine)
    private static final ThreadLocal<int[]> python = new ThreadLocal<>();

    private static volatile boolean tried;
    private static volatile boolean installed;
    private static volatile int hooked;

    private MZGramPluginSinkGate() {
    }

    // ---------- installing ----------

    /** Puts the gates on once; false when Aliuhook is not there. Any thread. */
    public static synchronized boolean install() {
        if (tried) {
            return installed;
        }
        tried = true;
        if (!MZGramPluginHooks.isReady()) {
            FileLog.w("MZGramPluginSinkGate: Aliuhook is off, only the Python gate stands");
            return false;
        }
        int count = 0;
        count += hookPython();
        count += hookProcesses();
        count += hookNativeLoad();
        count += hookNetwork();
        count += hookClasses();
        count += hookCodeLoaders();
        count += hookPreferences();
        count += hookOverlays();
        count += hookMessenger();
        hooked = count;
        installed = true;
        FileLog.d("MZGramPluginSinkGate: " + count + " gates on");
        return true;
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static int hookCount() {
        return hooked;
    }

    /** Python code starts on this thread (MZGramChaquopyBackend.call). */
    static void enterPython() {
        int[] depth = python.get();
        if (depth == null) {
            depth = new int[1];
            python.set(depth);
        }
        depth[0]++;
    }

    static void exitPython() {
        final int[] depth = python.get();
        if (depth != null && depth[0] > 0) {
            depth[0]--;
        }
    }

    // ---------- the plugin on this thread ----------

    /**
     * The plugin to check for, or null (the app's own code, or already
     * inside a check). Non-null leaves the check open: the caller closes it
     * with inside.remove().
     */
    private static String owner(boolean direct, String sinkClass, String[] skip) {
        if (inside.get() != null) {
            return null;
        }
        String pluginId = MZGramPluginPermissions.current();
        if (pluginId == null) {
            final int[] depth = python.get();
            if (depth == null || depth[0] <= 0) {
                return null;
            }
        }
        inside.set(Boolean.TRUE);
        boolean open = false;
        try {
            if (pluginId == null) {
                pluginId = frameOwner();
                if (pluginId == null) {
                    return null;
                }
            }
            if (direct && !calledFromPython(sinkClass, skip)) {
                return null;
            }
            open = true;
            return pluginId;
        } finally {
            if (!open) {
                inside.remove();
            }
        }
    }

    // The plugin whose Python code is on this thread's stack
    private static String frameOwner() {
        try {
            final PyObject owner = Python.getInstance().getModule(PYTHON_MODULE).callAttr("frame_owner");
            return owner != null ? owner.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Whether the sink was called by Python itself and not by code of the
     * app: the first frame below the sink that is not the sink, the hook
     * engine or reflection is Chaquopy, or there is none (a call from native
     * code, which is Python here).
     */
    static boolean calledFromPython(String sinkClass, String[] skip) {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            final String name = element.getClassName();
            if (name == null || name.equals(sinkClass) || skippedFrame(name, skip)) {
                continue;
            }
            return name.startsWith("com.chaquo.python.");
        }
        return true;
    }

    private static boolean skippedFrame(String name, String[] skip) {
        if (name.startsWith("dalvik.system.VMStack") || name.equals("java.lang.Thread")
                || name.startsWith(OWN_PREFIX) || name.startsWith("de.robv.android.xposed.")
                || name.startsWith("LSPHooker") || name.startsWith("org.lsposed.") || name.startsWith("com.aliucord.")
                || name.startsWith("java.lang.reflect.")) {
            return true;
        }
        if (skip != null) {
            for (String prefix : skip) {
                if (name.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------- one gate ----------

    private abstract static class Gate extends XC_MethodHook {
        final boolean direct;
        final String[] skip;

        Gate(boolean direct, String... skip) {
            super(PRIORITY_HIGHEST);
            this.direct = direct;
            this.skip = skip;
        }

        boolean applies(MethodHookParam param) {
            return true;
        }

        abstract void check(String pluginId, MethodHookParam param);

        @Override
        protected final void beforeHookedMethod(MethodHookParam param) {
            if (!applies(param)) {
                return;
            }
            final String pluginId = owner(direct, param.method.getDeclaringClass().getName(), skip);
            if (pluginId == null) {
                return;
            }
            try {
                check(pluginId, param);
            } catch (Throwable t) {
                FileLog.e("MZGramPluginSinkGate: a gate failed on " + param.method, t);
            } finally {
                inside.remove();
            }
        }
    }

    // ---------- Python callbacks ----------

    private static int hookPython() {
        final Class<?> handler = classForName("com.chaquo.python.PyInvocationHandler");
        if (handler == null) {
            return 0;
        }
        return hookAll(handler, "invoke", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                enterPython();
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                exitPython();
            }
        });
    }

    // ---------- never ----------

    private static int hookProcesses() {
        final Gate gate = new Gate(true, "java.lang.Runtime", "java.lang.ProcessBuilder") {
            @Override
            void check(String pluginId, MethodHookParam param) {
                MZGramPluginSinkPolicy.blocked(pluginId, "run a process");
                param.setThrowable(new SecurityException("MZGram: plugins may not start processes"));
            }
        };
        return hookAll(Runtime.class, "exec", gate) + hookAll(ProcessBuilder.class, "start", gate);
    }

    // ---------- native ----------

    private static int hookNativeLoad() {
        final Gate gate = new Gate(true, "java.lang.System", "java.lang.Runtime") {
            @Override
            void check(String pluginId, MethodHookParam param) {
                if (platformLoad(param)) {
                    return;
                }
                final String library = firstString(param.args);
                if (!MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.NATIVE, "load " + (library == null ? "a library" : new File(library).getName()))) {
                    param.setThrowable(new SecurityException("MZGram: loading native code needs the permission 'native'"));
                }
            }
        };
        return hookAll(Runtime.class, "load0", gate) + hookAll(Runtime.class, "loadLibrary0", gate);
    }

    /**
     * A library loaded for the platform, not by the plugin: for a class of
     * the boot class loader, or from a static initializer (a refusal there
     * would break the class for the whole app).
     */
    private static boolean platformLoad(XC_MethodHook.MethodHookParam param) {
        final Object[] args = param.args;
        final Class<?>[] types = param.method instanceof Method ? ((Method) param.method).getParameterTypes() : null;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Class) {
                    if (((Class<?>) args[i]).getClassLoader() == null) {
                        return true;
                    }
                    break;
                }
                if (args[i] instanceof ClassLoader) {
                    break;
                }
                if (args[i] == null && types != null && i < types.length && ClassLoader.class.isAssignableFrom(types[i])) {
                    return true;
                }
            }
        }
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if ("<clinit>".equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    // ---------- the network ----------

    private static int hookNetwork() {
        int count = 0;
        count += hookAll(URL.class, "openConnection", new Gate(false) {
            @Override
            boolean applies(MethodHookParam param) {
                if (!(param.thisObject instanceof URL)) {
                    return false;
                }
                final String protocol = ((URL) param.thisObject).getProtocol();
                return protocol != null && MZGramPluginSinkPolicy.isRemoteUrl(protocol + "://");
            }

            @Override
            void check(String pluginId, MethodHookParam param) {
                final URL url = (URL) param.thisObject;
                final String host = url.getPort() > 0 ? url.getHost() + ":" + url.getPort() : url.getHost();
                if (!MZGramPluginSinkPolicy.allowNetwork(pluginId, "connect", host)) {
                    param.setThrowable(networkRefused());
                }
            }
        });
        try {
            count += hook(Socket.class.getDeclaredMethod("connect", SocketAddress.class, int.class), new Gate(false) {
                @Override
                void check(String pluginId, MethodHookParam param) {
                    if (!MZGramPluginSinkPolicy.allowNetwork(pluginId, "connect", address(param.args))) {
                        param.setThrowable(networkRefused());
                    }
                }
            });
        } catch (Throwable t) {
            FileLog.e("MZGramPluginSinkGate: Socket.connect", t);
        }
        final Gate datagram = new Gate(false) {
            @Override
            void check(String pluginId, MethodHookParam param) {
                if (!MZGramPluginSinkPolicy.allowNetwork(pluginId, "send", address(param.args))) {
                    param.setThrowable("send".equals(param.method.getName()) ? networkRefused()
                            : new SecurityException("MZGram: the network needs the permission 'network'"));
                }
            }
        };
        count += hookAll(DatagramSocket.class, "connect", datagram);
        count += hookAll(DatagramSocket.class, "send", datagram);
        final Class<?> downloads = classForName("android.app.DownloadManager");
        if (downloads != null) {
            count += hookAll(downloads, "enqueue", new Gate(false) {
                @Override
                void check(String pluginId, MethodHookParam param) {
                    // The address stays inside the request; DownloadManager only downloads
                    if (!MZGramPluginSinkPolicy.allowNetwork(pluginId, "download", "")) {
                        param.setThrowable(new SecurityException("MZGram: the network needs the permission 'network'"));
                    }
                }
            });
        }
        final Class<?> player = classForName("android.media.MediaPlayer");
        if (player != null) {
            count += hookAll(player, "setDataSource", new Gate(false) {
                @Override
                boolean applies(MethodHookParam param) {
                    return remoteArgument(param.args) != null;
                }

                @Override
                void check(String pluginId, MethodHookParam param) {
                    if (!MZGramPluginSinkPolicy.allowNetwork(pluginId, "stream", MZGramPluginSinkPolicy.hostOf(remoteArgument(param.args)))) {
                        param.setThrowable(networkRefused());
                    }
                }
            });
        }
        final Class<?> webView = classForName("android.webkit.WebView");
        if (webView != null) {
            final Gate load = new Gate(true, "android.webkit.WebView") {
                @Override
                boolean applies(MethodHookParam param) {
                    return remoteArgument(param.args) != null;
                }

                @Override
                void check(String pluginId, MethodHookParam param) {
                    if (!MZGramPluginSinkPolicy.allowNetwork(pluginId, "load a page", MZGramPluginSinkPolicy.hostOf(remoteArgument(param.args)))) {
                        param.setResult(null);
                    }
                }
            };
            count += hookAll(webView, "loadUrl", load) + hookAll(webView, "postUrl", load)
                    + hookAll(webView, "loadDataWithBaseURL", load);
        }
        final Class<?> images = classForName("org.telegram.messenger.ImageLoader");
        if (images != null) {
            count += hookAll(images, "loadHttpFile", new Gate(true) {
                @Override
                boolean applies(MethodHookParam param) {
                    return remoteArgument(param.args) != null;
                }

                @Override
                void check(String pluginId, MethodHookParam param) {
                    if (!MZGramPluginSinkPolicy.allowNetwork(pluginId, "download", MZGramPluginSinkPolicy.hostOf(remoteArgument(param.args)))) {
                        param.setResult(null);
                    }
                }
            });
        }
        return count;
    }

    private static Throwable networkRefused() {
        return new ConnectException("MZGram: the network needs the permission 'network'");
    }

    // host:port of the first address among the arguments
    private static String address(Object[] args) {
        if (args == null) {
            return "";
        }
        InetAddress address = null;
        int port = -1;
        for (Object arg : args) {
            if (arg instanceof InetSocketAddress) {
                final InetSocketAddress socket = (InetSocketAddress) arg;
                return socket.getHostString() + ":" + socket.getPort();
            }
            if (arg instanceof DatagramPacket) {
                final DatagramPacket packet = (DatagramPacket) arg;
                address = packet.getAddress();
                port = packet.getPort();
            } else if (arg instanceof InetAddress) {
                address = (InetAddress) arg;
            } else if (arg instanceof Integer && port < 0) {
                port = (Integer) arg;
            }
        }
        if (address == null) {
            return "";
        }
        return address.getHostAddress() + (port > 0 ? ":" + port : "");
    }

    // The first argument that is a remote address (a string or a Uri), or null
    private static String remoteArgument(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String || arg instanceof Uri) {
                final String text = arg.toString();
                if (MZGramPluginSinkPolicy.isRemoteUrl(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    // ---------- classes ----------

    private static int hookClasses() {
        try {
            final Method forName = Class.class.getDeclaredMethod("forName", String.class, boolean.class, ClassLoader.class);
            return hook(forName, new Gate(true) {
                @Override
                boolean applies(MethodHookParam param) {
                    return param.args != null && param.args.length > 0 && param.args[0] instanceof String;
                }

                @Override
                void check(String pluginId, MethodHookParam param) {
                    final String name = (String) param.args[0];
                    if (!MZGramPluginSinkPolicy.allowClass(pluginId, name)) {
                        param.setThrowable(new ClassNotFoundException("MZGram: " + name + " is not available to this plugin"));
                    }
                }
            });
        } catch (Throwable t) {
            FileLog.e("MZGramPluginSinkGate: Class.forName", t);
            return 0;
        }
    }

    private static int hookCodeLoaders() {
        int count = 0;
        final Gate gate = new Gate(true, "dalvik.system.") {
            @Override
            void check(String pluginId, MethodHookParam param) {
                if (!MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.HOOKS, "load code " + param.method.getDeclaringClass().getSimpleName())) {
                    param.setThrowable(new SecurityException("MZGram: loading code needs the permission 'hooks'"));
                }
            }
        };
        for (String name : CODE_LOADERS) {
            final Class<?> loader = classForName(name);
            if (loader == null) {
                continue;
            }
            try {
                for (Constructor<?> constructor : loader.getDeclaredConstructors()) {
                    count += hook(constructor, gate);
                }
            } catch (Throwable t) {
                FileLog.e("MZGramPluginSinkGate: constructors of " + name, t);
            }
        }
        final Class<?> dexFile = classForName("dalvik.system.DexFile");
        if (dexFile != null) {
            count += hookAll(dexFile, "loadDex", gate);
        }
        return count;
    }

    // ---------- settings ----------

    private static int hookPreferences() {
        final Class<?> context = classForName("android.app.ContextImpl");
        if (context == null) {
            return 0;
        }
        final Gate gate = new Gate(true, "android.app.ContextImpl", "android.content.ContextWrapper") {
            @Override
            boolean applies(MethodHookParam param) {
                return param.args != null && param.args.length > 0
                        && (param.args[0] instanceof String || param.args[0] instanceof File);
            }

            @Override
            void check(String pluginId, MethodHookParam param) {
                String name = param.args[0] instanceof File ? ((File) param.args[0]).getName() : (String) param.args[0];
                if (name.endsWith(".xml")) {
                    name = name.substring(0, name.length() - 4);
                }
                if (name.startsWith("mzgram_plugin")) {
                    // The engine's own settings and those of other plugins
                    if (!name.equals(MZGramPluginSettingsStore.PREFS_PREFIX + pluginId)) {
                        MZGramPluginSinkPolicy.blocked(pluginId, "settings " + name);
                        param.setThrowable(new SecurityException("MZGram: these settings are not available to plugins"));
                    }
                    return;
                }
                if (!MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.SETTINGS, "settings " + name)) {
                    param.setThrowable(new SecurityException("MZGram: the settings of the app need the permission 'settings'"));
                }
            }
        };
        return hookAll(context, "getSharedPreferences", gate) + hookAll(context, "deleteSharedPreferences", gate)
                + hookAll(context, "getSharedPreferencesPath", gate);
    }

    // ---------- system windows ----------

    private static int hookOverlays() {
        final Class<?> windows = classForName("android.view.WindowManagerImpl");
        if (windows == null) {
            return 0;
        }
        return hookAll(windows, "addView", new Gate(true, "android.view.WindowManager") {
            @Override
            boolean applies(MethodHookParam param) {
                if (param.args == null || param.args.length < 2 || !(param.args[1] instanceof WindowManager.LayoutParams)) {
                    return false;
                }
                final int type = ((WindowManager.LayoutParams) param.args[1]).type;
                return type >= WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW && type <= WindowManager.LayoutParams.LAST_SYSTEM_WINDOW;
            }

            @Override
            void check(String pluginId, MethodHookParam param) {
                if (!MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.HOOKS, "show a window over other apps")) {
                    param.setThrowable(new SecurityException("MZGram: a window over other apps needs the permission 'hooks'"));
                }
            }
        });
    }

    // ---------- Telegram ----------

    private static int hookMessenger() {
        int count = 0;
        final Class<?> send = classForName("org.telegram.messenger.SendMessagesHelper");
        if (send != null) {
            final Gate gate = new Gate(true) {
                @Override
                void check(String pluginId, MethodHookParam param) {
                    if (!MZGramPluginPermissions.check(pluginId, MZGramPluginGrants.MESSAGES_SEND, param.method.getName())) {
                        param.setResult(emptyResult(param.method));
                    }
                }
            };
            for (String name : SEND_SINKS) {
                count += hookAll(send, name, gate);
            }
        }
        final Class<?> connections = classForName("org.telegram.tgnet.ConnectionsManager");
        if (connections != null) {
            final List<Member> targets = new ArrayList<>();
            Method widest = null;
            try {
                for (Method method : connections.getDeclaredMethods()) {
                    if ("sendRequestSync".equals(method.getName())) {
                        targets.add(method);
                    } else if ("sendRequest".equals(method.getName())
                            && (widest == null || method.getParameterTypes().length > widest.getParameterTypes().length)) {
                        widest = method;
                    }
                }
            } catch (Throwable t) {
                FileLog.e("MZGramPluginSinkGate: ConnectionsManager", t);
            }
            if (widest != null) {
                targets.add(widest);
            }
            final Gate gate = new Gate(true) {
                @Override
                void check(String pluginId, MethodHookParam param) {
                    final String request = MZGramPluginSinkPolicy.requestName(param.args != null && param.args.length > 0 ? param.args[0] : null);
                    if (!MZGramPluginPermissions.check(pluginId, MZGramPluginSinkPolicy.requestKey(request), request)) {
                        param.setResult(emptyResult(param.method));
                    }
                }
            };
            for (Member target : targets) {
                count += hook(target, gate);
            }
        }
        return count;
    }

    // ---------- helpers ----------

    // The empty value of the return type: null would break unboxing of a primitive
    static Object emptyResult(Member member) {
        final Class<?> type = member instanceof Method ? ((Method) member).getReturnType() : null;
        if (type == null || !type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0d;
    }

    private static String firstString(Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof String) {
                    return (String) arg;
                }
            }
        }
        return null;
    }

    private static Class<?> classForName(String name) {
        try {
            return Class.forName(name, false, MZGramPluginSinkGate.class.getClassLoader());
        } catch (Throwable ignored) {
            // Not in this build or this Android version: nothing to gate
            return null;
        }
    }

    private static int hookAll(Class<?> owner, String name, XC_MethodHook hook) {
        int count = 0;
        try {
            for (Method method : owner.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    count += hook(method, hook);
                }
            }
        } catch (Throwable t) {
            FileLog.e("MZGramPluginSinkGate: methods of " + owner.getName(), t);
        }
        return count;
    }

    private static int hook(Member member, XC_MethodHook hook) {
        return MZGramPluginHooks.hookForGate(member, hook) != null ? 1 : 0;
    }
}
