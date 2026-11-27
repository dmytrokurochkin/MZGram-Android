package org.mzgram.plugins;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.DispatchQueue;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;

import org.mzgram.plugins.files.FilesControllerJava;
import org.mzgram.plugins.intents.IntentsDispatcher;
import org.mzgram.plugins.utils.ClassProxyFactory;
import org.mzgram.plugins.xposed.XposedHooks;

public final class PluginServices {

    private PluginServices() {
    }

    private static final String EMPTY_JSON_ARRAY = "[]";


    public static String hookMethod(String pluginId, Object member, PyObject handler,
                                    int priority, String filtersJson) {
        if (!PluginPermissions.check(pluginId, PluginPermissions.HOOKS, "hookMethod")) {
            return null;
        }
        return XposedHooks.hookMethod(pluginId, member, handler, priority, filtersJson);
    }

    public static String hookAllMethods(String pluginId, Object clazz, String methodName,
                                        PyObject handler, int priority, String filtersJson) {
        if (!PluginPermissions.check(pluginId, PluginPermissions.HOOKS, "hookAllMethods")) {
            return EMPTY_JSON_ARRAY;
        }
        return XposedHooks.hookAllMethods(pluginId, clazz, methodName, handler, priority, filtersJson);
    }

    public static String hookAllConstructors(String pluginId, Object clazz, PyObject handler,
                                             int priority, String filtersJson) {
        if (!PluginPermissions.check(pluginId, PluginPermissions.HOOKS, "hookAllConstructors")) {
            return EMPTY_JSON_ARRAY;
        }
        return XposedHooks.hookAllConstructors(pluginId, clazz, handler, priority, filtersJson);
    }

    public static void unhook(String unhookId) {
        XposedHooks.unhook(unhookId);
    }

    public static Object invokeOriginalMethod(Object member, Object thisObject, Object[] args) throws Exception {
        return XposedHooks.invokeOriginalMethod(member, thisObject, args);
    }

    public static void deoptimizeMethod(String pluginId, Object member) {
        if (!PluginPermissions.check(pluginId, PluginPermissions.HOOKS, "deoptimizeMethod")) {
            return;
        }
        XposedHooks.deoptimizeMethod(member);
    }

    public static Object allocateInstance(String pluginId, Object clazz) {
        if (!PluginPermissions.check(pluginId, PluginPermissions.HOOKS, "allocateInstance")) {
            return null;
        }
        return XposedHooks.allocateInstance(clazz);
    }


    public static String generateProxyClass(String pluginId, String specJson) {
        if (pluginId == null) {
            ClassProxyFactory.setLastError("no current plugin: called outside plugin context");
            return null;
        }
        String permission = ClassProxyFactory.needsHooks(specJson)
                ? PluginPermissions.HOOKS : PluginPermissions.UI;
        if (!PluginPermissions.check(pluginId, permission, "generateProxyClass")) {
            ClassProxyFactory.setLastError(
                    "permission '" + permission + "' not granted to " + pluginId);
            return null;
        }
        return ClassProxyFactory.generateProxyClass(pluginId, specJson);
    }

    public static String getProxyError() {
        return ClassProxyFactory.getLastError();
    }

    public static Object newProxyInstance(String pluginId, String classKey, String ctorSig,
                                          Object[] args, PyObject peer) {
        if (pluginId == null) {
            return null;
        }
        String permission = ClassProxyFactory.classNeedsHooks(classKey)
                ? PluginPermissions.HOOKS : PluginPermissions.UI;
        if (!PluginPermissions.check(pluginId, permission, "newProxyInstance")) {
            return null;
        }
        return ClassProxyFactory.newProxyInstance(pluginId, classKey, ctorSig, args, peer);
    }

    public static Object getProxyClass(String classKey) {
        return ClassProxyFactory.getProxyClass(classKey);
    }

    public static Object invokeSuper(String classKey, Object proxy, String methodSig, Object[] args) {
        if (!PluginPermissions.check(pluginIdOfClassKey(classKey), PluginPermissions.HOOKS,
                "invokeSuper")) {
            return null;
        }
        return ClassProxyFactory.invokeSuper(classKey, proxy, methodSig, args);
    }

    private static String pluginIdOfClassKey(String classKey) {
        if (classKey == null) {
            return null;
        }
        int sep = classKey.indexOf(':');
        return sep > 0 ? classKey.substring(0, sep) : null;
    }


    public static String registerFileHandler(String pluginId, String ext, String whitelistJson,
                                             String blacklistJson, boolean hasIcon) {
        return registerFileHandler(pluginId, ext, whitelistJson, blacklistJson, hasIcon, null);
    }

    public static String registerFileHandler(String pluginId, String ext, String whitelistJson,
                                             String blacklistJson, boolean hasIcon, PyObject onClick) {
        if (!PluginPermissions.check(pluginId, PluginPermissions.FILES,
                "registerFileHandler(." + ext + ")")) {
            return null;
        }
        return FilesControllerJava.register(pluginId, ext, whitelistJson, blacklistJson, hasIcon, onClick);
    }

    public static void unregisterFileHandler(String pluginId, String ext, String secret) {
        FilesControllerJava.unregister(pluginId, ext, secret);
    }

    public static boolean fileIconsSupported() {
        return FilesControllerJava.isIconsSupported();
    }


    public static String registerIntentHandler(String pluginId, boolean before, String filtersJson,
                                               int priority, PyObject callback) {
        if (!PluginPermissions.check(pluginId, PluginPermissions.INTENTS, "registerIntentHandler")) {
            return null;
        }
        return IntentsDispatcher.registerHandler(pluginId, before, filtersJson, priority, callback);
    }

    public static void unregisterIntentHandler(String pluginId, String handlerId) {
        IntentsDispatcher.unregisterHandler(pluginId, handlerId);
    }


    public static Runnable runnable(PyObject callback) {
        if (callback == null) {
            return () -> { };
        }
        return () -> runWhenUnfrozen(() -> {
            try {
                callback.call();
            } catch (Throwable t) {
                FileLog.e("plugin callback failed", t);
            }
        });
    }

    private static final long FROZEN_RETRY_MS = 300L;

    static void runWhenUnfrozen(Runnable task) {
        if (org.telegram.ui.Wallet.WalletPluginFreeze.isFrozen()) {
            UI_HANDLER.postDelayed(() -> runWhenUnfrozen(task), FROZEN_RETRY_MS);
            return;
        }
        task.run();
    }

    public static void postRunnable(DispatchQueue queue, PyObject callback, long delay) {
        if (queue == null || callback == null) {
            return;
        }
        final Runnable task = runnable(callback);
        if (delay > 0) {
            queue.postRunnable(task, delay);
        } else {
            queue.postRunnable(task);
        }
    }

    public static int sendRequest(int account, TLObject request, PyObject callback) {
        final RequestDelegate delegate = (response, error) -> {
            if (callback == null) {
                return;
            }
            runWhenUnfrozen(() -> {
                try {
                    callback.call(response, error);
                } catch (Throwable t) {
                    FileLog.e("plugin request callback failed", t);
                }
            });
        };
        return ConnectionsManager.getInstance(account).sendRequest(request, delegate);
    }

    public static void runOnUiThread(PyObject callback, long delay) {
        if (callback == null) {
            return;
        }
        final Runnable runnable = () -> runWhenUnfrozen(() -> {
            try {
                callback.call();
            } catch (Throwable t) {
                FileLog.e("plugin ui callback failed", t);
            }
        });
        final Message message = Message.obtain(UI_HANDLER, runnable);
        message.setAsynchronous(true);
        UI_HANDLER.sendMessageDelayed(message, Math.max(0L, delay));
    }

    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
}
