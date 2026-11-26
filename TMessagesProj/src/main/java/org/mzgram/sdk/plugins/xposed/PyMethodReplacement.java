package org.mzgram.sdk.plugins.xposed;

import com.chaquo.python.PyObject;

import java.util.Collections;

/** Шим {@code org.mzgram.sdk.plugins.xposed.PyMethodReplacement}. */
public class PyMethodReplacement extends org.mzgram.plugins.xposed.PyMethodReplacement {

    public PyMethodReplacement(String pluginId, PyObject handler, int priority) {
        super(pluginId, handler, priority, Collections.emptyList());
    }

    public PyMethodReplacement(String pluginId, PyObject handler) {
        this(pluginId, handler, 50);
    }
}
