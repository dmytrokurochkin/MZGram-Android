package org.telegram.ui.Components.blur3;

import org.mzgram.appearance.AppearanceConfig;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public enum GlassOutlineStyle {
    GLARE,
    SOLID,
    HIDDEN;

    private static final GlassOutlineStyle[] VALUES = values();
    private static final Set<Listener> listeners = Collections.newSetFromMap(new WeakHashMap<>());

    // made with <3 from uzbekgram
    public interface Listener {
        void onGlassOutlineStyleChanged();
    }

    public static void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public static void dispatchChange() {
        final Listener[] snapshot;
        synchronized (listeners) {
            snapshot = listeners.toArray(new Listener[0]);
        }
        for (Listener listener : snapshot) {
            listener.onGlassOutlineStyleChanged();
        }
    }

    public static GlassOutlineStyle current() {
        final int index = AppearanceConfig.glassOutlineStyle();
        if (index < 0 || index >= VALUES.length) {
            return GLARE;
        }
        return VALUES[index];
    }
}
