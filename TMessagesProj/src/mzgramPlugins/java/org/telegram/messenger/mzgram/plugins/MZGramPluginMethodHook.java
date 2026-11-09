/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * One hook of one plugin on one method or constructor: calls the plugin's
 * Python before the method (before_hooked_method), after it
 * (after_hooked_method) or instead of it (replace_hooked_method).
 *
 * MZGram comes first: nothing a plugin does here may break the app.
 *   - An error in the plugin's code, or a value the method cannot take or
 *     return, undoes what the plugin did in that call: the arguments go back
 *     to what they were, and Aliuhook drops the result (the original method
 *     runs) or puts the earlier result back. The app sees the call as if
 *     there were no hook; the plugin gets one line in its log.
 *   - A number from Python becomes the exact type of the method
 *     (MZGramPluginHookGate.coerce), so an int method never gets a Long.
 *   - The filters (MZGramPluginHookFilter) are checked before Python is
 *     entered; a hook skipped by MZGramPluginHookGate.isSuspended() does
 *     nothing; a hook that runs into itself on the same thread lets the
 *     inner call go through.
 *   - A hook taken off (active = false) does nothing, even in a call that
 *     had already started.
 * A replacement of a constructor runs before it and the constructor still
 * runs: an object must be built.
 *
 * Compiled only in a build with -PmzgramPlugins=true (Chaquopy, Aliuhook).
 *
 * Based on exteraless plugins/xposed/PyMethodHook.java and
 * PyMethodReplacement.java (GPL-3.0): before/after/replace around the
 * original through Xposed callbacks, the result converted to the return
 * type; written anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;

final class MZGramPluginMethodHook extends XC_MethodHook {

    // Aliuhook keeps "the hook set a result" in a field of its own package
    private static final Field RETURN_EARLY = returnEarlyField();

    final String pluginId;
    final String target;
    private final String className;
    private final boolean constructor;
    private final Class<?> returnType;
    private final Class<?>[] parameterTypes;
    private final PyObject before;
    private final PyObject after;
    private final PyObject replace;
    private final MZGramPluginHookFilter.Parsed filters;
    final MZGramPluginHookGate.Counter counter;

    volatile boolean active = true;
    private final ThreadLocal<Boolean> inside = new ThreadLocal<>();
    private final AtomicBoolean told = new AtomicBoolean();

    MZGramPluginMethodHook(String pluginId, Member member, PyObject before, PyObject after, PyObject replace,
                           int priority, MZGramPluginHookFilter.Parsed filters, MZGramPluginHookGate.Counter counter) {
        super(priority);
        this.pluginId = pluginId;
        this.className = member.getDeclaringClass().getName();
        this.target = counter.target;
        this.constructor = member instanceof Constructor;
        this.returnType = member instanceof Method ? ((Method) member).getReturnType() : void.class;
        this.parameterTypes = member instanceof Method ? ((Method) member).getParameterTypes()
                : ((Constructor<?>) member).getParameterTypes();
        // A replacement takes the place of before and after
        this.replace = replace;
        this.before = replace == null ? before : null;
        this.after = replace == null ? after : null;
        this.filters = filters;
        this.counter = counter;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (!active || before == null && replace == null) {
            return;
        }
        if (MZGramPluginHookGate.isSuspended(className)) {
            counter.skip();
            return;
        }
        if (inside.get() != null || !MZGramPluginHookFilter.passes(filters.before, param.args, null, false)) {
            return;
        }
        final Object[] args = param.args;
        final Object[] saved = args != null ? args.clone() : null;
        if (replace != null) {
            final PyObject value = call(replace, param);
            if (constructor) {
                checkArgs(param, args, saved);
                return;
            }
            Object result;
            try {
                result = MZGramPluginHookGate.coerce(value != null ? value.toJava(Object.class) : null, returnType);
            } catch (Throwable t) {
                result = MZGramPluginHookGate.INVALID;
            }
            if (result == MZGramPluginHookGate.INVALID) {
                undo(param, args, saved, "replace_hooked_method returned a value the method cannot return");
            }
            param.setResult(result);
            return;
        }
        call(before, param);
        checkArgs(param, args, saved);
        if (!param.hasThrowable() && resultSet(param)) {
            final Object result = MZGramPluginHookGate.coerce(param.getResult(), returnType);
            if (result == MZGramPluginHookGate.INVALID) {
                undo(param, args, saved, "before_hooked_method set a result the method cannot return");
            }
            param.setResult(result);
        }
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        if (!active || after == null) {
            return;
        }
        if (MZGramPluginHookGate.isSuspended(className)) {
            if (before == null) {
                counter.skip();
            }
            return;
        }
        if (inside.get() != null || !MZGramPluginHookFilter.passes(filters.after, param.args, param.getResult(), true)) {
            return;
        }
        call(after, param);
        if (!param.hasThrowable() && !constructor) {
            final Object result = MZGramPluginHookGate.coerce(param.getResult(), returnType);
            if (result == MZGramPluginHookGate.INVALID) {
                // Aliuhook puts back the result from before this callback
                undo(param, null, null, "after_hooked_method set a result the method cannot return");
            }
            if (result != param.getResult()) {
                param.setResult(result);
            }
        }
    }

    // The plugin's Python; an error is told and undone
    private PyObject call(PyObject callable, MethodHookParam param) {
        inside.set(Boolean.TRUE);
        // The plugin's code runs on this thread (for the permission checks)
        final String previous = MZGramPluginPermissions.enter(pluginId);
        final long start = System.nanoTime();
        try {
            return callable.call(param);
        } catch (Throwable t) {
            failed(firstLine(t));
            throw new Undone(pluginId);
        } finally {
            MZGramPluginPermissions.exit(previous);
            inside.remove();
            counter.record(System.nanoTime() - start);
        }
    }

    // The arguments must still fit the method
    private void checkArgs(MethodHookParam param, Object[] args, Object[] saved) {
        if (param.args == args && MZGramPluginHookGate.coerceAll(args, parameterTypes)) {
            return;
        }
        undo(param, args, saved, "the hook set an argument the method cannot take");
    }

    private void undo(MethodHookParam param, Object[] args, Object[] saved, String why) {
        if (args != null && saved != null) {
            System.arraycopy(saved, 0, args, 0, Math.min(saved.length, args.length));
            param.args = args;
        }
        failed(why);
        throw new Undone(pluginId);
    }

    private void failed(String why) {
        counter.fail();
        if (told.compareAndSet(false, true)) {
            // Once per hook: a hook on a busy method would fill the log
            MZGramPluginEngine.log(pluginId, "hook " + target + " failed, the app went on without it: " + why);
        }
    }

    private static boolean resultSet(MethodHookParam param) {
        if (RETURN_EARLY != null) {
            try {
                return RETURN_EARLY.getBoolean(param);
            } catch (Throwable ignore) {
            }
        }
        return param.getResult() != null;
    }

    private static Field returnEarlyField() {
        try {
            final Field field = MethodHookParam.class.getDeclaredField("returnEarly");
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            FileLog.e("MZGramPluginMethodHook: no returnEarly in this Aliuhook, a result of None is not checked");
            return null;
        }
    }

    static String firstLine(Throwable t) {
        String text = t.getMessage();
        if (text == null || text.trim().isEmpty()) {
            text = t.getClass().getSimpleName();
        }
        text = text.trim();
        // A Python error comes with its traceback after the first line
        final int end = text.indexOf('\n');
        if (end > 0) {
            text = text.substring(0, end);
        }
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    /** Thrown so that Aliuhook undoes the call; carries no stack (it is no bug of the app). */
    static final class Undone extends RuntimeException {

        Undone(String pluginId) {
            super("MZGram: a hook of the plugin " + pluginId + " was undone");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
