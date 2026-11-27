package org.mzgram.icons.picker;

import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.LaunchActivity;

import org.mzgram.icons.IconPacksConfig;

public class IconPickerController {

    @Nullable
    private static IconPickerView pickerView;
    private static boolean initializing;

    private IconPickerController() {
    }

    public static boolean isActive() {
        return pickerView != null;
    }

    public static void setActive(LaunchActivity activity, boolean active) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(() -> setActive(activity, active));
            return;
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (active == (pickerView != null)) {
            return;
        }
        if (active) {
            show(activity);
        } else {
            hide(activity);
        }
    }

    private static void show(LaunchActivity activity) {
        if (!IconPacksConfig.isEditing() || pickerView != null || initializing) {
            return;
        }
        initializing = true;
        try {
            FrameLayout container = activity.getMainContainerFrameLayout();
            if (container == null) {
                return;
            }
            IconPickerView view = new IconPickerView(activity);
            pickerView = view;
            container.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            view.showFab();
        } catch (Throwable t) {
            FileLog.e("mzGram: failed to show icon picker", t);
            pickerView = null;
        } finally {
            initializing = false;
        }
    }

    private static void hide(LaunchActivity activity) {
        IconPickerView view = pickerView;
        if (view == null) {
            return;
        }
        pickerView = null;
        IconObserver.clear();
        view.dismiss(() -> removeFromParent(view));
    }

    private static void removeFromParent(IconPickerView view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    public static void invalidateIcons() {
        IconPickerView view = pickerView;
        if (view != null) {
            AndroidUtilities.runOnUIThread(view::updateItems);
        }
    }

    public static boolean onBackPressed(boolean invoked) {
        IconPickerView view = pickerView;
        return view != null && view.onBackPressed(invoked);
    }

    public static void onDestroy() {
        IconPickerView view = pickerView;
        pickerView = null;
        if (view != null) {
            view.saveConfig();
            removeFromParent(view);
        }
    }

    public static void finishEditing() {
        IconPacksConfig.setEditingPackId(null);
        setActive(LaunchActivity.instance, false);
    }
}
