package org.mzgram.plugins.xposed;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

public class PyMethodReplacement extends XC_MethodHook {

    private final String pluginId;
    private final PyObject replaceCall;
    private final HookStats stats;
    private final List<HookFilter> beforeFilters;

    public PyMethodReplacement(String pluginId, PyObject handler) {
        this(pluginId, handler, PRIORITY_DEFAULT);
    }

    public PyMethodReplacement(String pluginId, PyObject handler, int priority) {
        this(pluginId, handler, priority, Collections.emptyList());
    }

    public PyMethodReplacement(String pluginId, PyObject handler, int priority,
                        List<HookFilter> beforeFilters) {
        super(priority);
        this.pluginId = pluginId;
        final int statId = HookStats.nextId();
        this.replaceCall = handler != null
                ? XposedHooks.bindHook(handler, "replace_hooked_method", statId) : null;
        this.stats = replaceCall != null ? new HookStats(statId, pluginId, "replace") : null;
        this.beforeFilters = beforeFilters;
    }

    void addTarget(java.lang.reflect.Member member) {
        if (stats != null) {
            stats.addTarget(member);
        }
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (replaceCall == null || !HookFilter.evaluateAll(beforeFilters, param, false)) {
            return;
        }
        XposedHooks.PyResult result =
                XposedHooks.callPython(pluginId, replaceCall, param, stats);
        if (!result.ok) {
            return;
        }
        try {
            applyResult(param, result.value);
        } catch (Throwable t) {
            XposedHooks.reportError(pluginId, t);
        }
    }

    private void applyResult(MethodHookParam param, PyObject pyResult) {
        if (!(param.method instanceof Method)) {
            return;
        }
        Class<?> returnType = ((Method) param.method).getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            param.setResult(null);
            return;
        }
        if (pyResult == null) {
            setNullResult(param, returnType);
            return;
        }
        if (returnType.isPrimitive()) {
            if (pyResult.toJava(Object.class) == null) {
                setNullResult(param, returnType);
                return;
            }
            param.setResult(pyResult.toJava(returnType));
            return;
        }
        param.setResult(pyResult.toJava(returnType));
    }

    private void setNullResult(MethodHookParam param, Class<?> returnType) {
        if (returnType.isPrimitive()) {
            FileLog.e("XposedHooks: replacement for " + param.method
                    + " returned None for primitive " + returnType + ", running original");
            return;
        }
        param.setResult(null);
    }
}
