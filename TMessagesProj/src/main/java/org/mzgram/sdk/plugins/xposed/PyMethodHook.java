package org.mzgram.sdk.plugins.xposed;

import com.chaquo.python.PyObject;

import java.util.Collections;

public class PyMethodHook extends org.mzgram.plugins.xposed.PyMethodHook {

    public PyMethodHook(String pluginId, PyObject handler, int priority) {
        super(pluginId, handler, priority, Collections.emptyList(), Collections.emptyList());
    }

    public PyMethodHook(String pluginId, PyObject handler) {
        this(pluginId, handler, 50);
    }
}
