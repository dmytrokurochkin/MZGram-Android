package org.mzgram.icons.picker;

import android.app.Activity;
import android.os.Looper;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

import org.mzgram.icons.IconPacksConfig;

public class IconObserver {

    private static final WeakHashMap<BaseFragment, Set<Integer>> iconSources = new WeakHashMap<>();

    private IconObserver() {
    }

    private static volatile boolean suspended;

    public static void setSuspended(boolean value) {
        suspended = value;
    }

    public static void log(int resId) {
        if (suspended || resId == 0 || !IconPacksConfig.isEditing()) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        Activity activity = fragment.getParentActivity();
        if (activity instanceof LaunchActivity) {
            IconPickerController.setActive((LaunchActivity) activity, true);
        }
        synchronized (iconSources) {
            Set<Integer> set = iconSources.get(fragment);
            if (set == null) {
                set = new LinkedHashSet<>();
                iconSources.put(fragment, set);
            }
            set.add(resId);
        }
    }

    public static Set<Integer> getUsedIcons() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return Collections.emptySet();
        }
        synchronized (iconSources) {
            Set<Integer> set = iconSources.get(fragment);
            return set == null ? Collections.emptySet() : new LinkedHashSet<>(set);
        }
    }

    public static Set<Integer> getAllIcons() {
        synchronized (iconSources) {
            Set<Integer> result = new LinkedHashSet<>();
            for (Set<Integer> set : iconSources.values()) {
                result.addAll(set);
            }
            return result;
        }
    }

    public static void removeSource(BaseFragment owner) {
        synchronized (iconSources) {
            iconSources.remove(owner);
        }
    }

    public static void clear() {
        synchronized (iconSources) {
            iconSources.clear();
        }
    }

    static Set<Integer> snapshot(Set<Integer> source) {
        return new HashSet<>(source);
    }
}
