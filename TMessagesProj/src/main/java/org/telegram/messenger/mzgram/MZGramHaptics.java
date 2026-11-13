/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > General > In-app vibration. Off stops the taps and
 * buzzes of the app's own controls: Telegram asks a view for a tap
 * (performHapticFeedback), which a view with haptic feedback switched off
 * skips. While the switch is off, every view of each window MZGram follows
 * (the app, its sheets and dialogs, the photo and story viewers) gets its
 * haptic feedback switched off after each layout; the places where Telegram
 * asks for a tap regardless of the view's own setting ask through
 * viewSetting(), and the vibration effects of mini apps and errors stop too.
 * Calls still ring and vibrate, and notifications follow their own settings.
 * On by default: nothing changes until it is switched off.
 *
 * Idea from exteraless (GPL-3.0), "In-app vibration"; written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public final class MZGramHaptics {

    // The root views MZGram follows, weakly: a closed window goes away
    private static final Map<View, Boolean> roots = new WeakHashMap<>();

    private MZGramHaptics() {
    }

    public static boolean allowed() {
        return MZGramConfig.inAppVibration;
    }

    // For performHapticFeedback calls that skip the view's own setting: with
    // the switch off they keep to it, which the views of a followed window
    // have off
    public static int viewSetting() {
        return allowed() ? HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING : 0;
    }

    // Follow the window of this view: while the switch is off, its views get
    // haptic feedback off after every layout. Costs nothing while it is on.
    public static void follow(View view) {
        if (view == null) {
            return;
        }
        final View root = view.getRootView();
        synchronized (roots) {
            if (roots.containsKey(root)) {
                return;
            }
            roots.put(root, Boolean.TRUE);
        }
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!allowed()) {
                setEnabled(root, false);
            }
        });
        if (!allowed()) {
            setEnabled(root, false);
        }
    }

    // The switch changed: the followed windows take it at once
    public static void apply() {
        ArrayList<View> list;
        synchronized (roots) {
            list = new ArrayList<>(roots.keySet());
        }
        for (View root : list) {
            if (root != null) {
                setEnabled(root, allowed());
            }
        }
    }

    static void setEnabled(View view, boolean enabled) {
        if (view.isHapticFeedbackEnabled() != enabled) {
            view.setHapticFeedbackEnabled(enabled);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, n = group.getChildCount(); i < n; i++) {
                View child = group.getChildAt(i);
                if (child != null) {
                    setEnabled(child, enabled);
                }
            }
        }
    }
}
