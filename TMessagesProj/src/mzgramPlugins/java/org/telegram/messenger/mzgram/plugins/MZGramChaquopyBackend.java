/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The Chaquopy side of MZGramPythonRuntime. This source folder is compiled
 * only in a build with -PmzgramPlugins=true (TMessagesProj/build.gradle), the
 * only build that has the com.chaquo.python classes. MZGramPythonRuntime finds
 * this class by name, so the default build compiles and runs without it
 * (mzgram-plugins-rules.pro keeps the name through R8).
 */

package org.telegram.messenger.mzgram.plugins;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public final class MZGramChaquopyBackend implements MZGramPythonRuntime.Backend {

    public MZGramChaquopyBackend() {
    }

    @Override
    public void start(Context context) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
    }

    @Override
    public String describe() {
        // src/mzgramPlugins/python/mzgram_runtime.py
        return Python.getInstance().getModule("mzgram_runtime").callAttr("describe").toString();
    }

    @Override
    public String call(String module, String function, String argument) {
        // Python runs on this thread now: the sink gates look for a plugin
        // among its frames when no plugin is marked (MZGramPluginSinkGate)
        MZGramPluginSinkGate.enterPython();
        try {
            final PyObject result = Python.getInstance().getModule(module).callAttr(function, argument);
            return result != null ? result.toString() : null;
        } finally {
            MZGramPluginSinkGate.exitPython();
        }
    }
}
