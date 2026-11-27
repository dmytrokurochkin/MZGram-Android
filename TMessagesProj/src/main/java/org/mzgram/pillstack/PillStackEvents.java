package org.mzgram.pillstack;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PillStackEvents {

    public interface Listener {
        default void onPillStackLayoutChanged() {}

        default void onPillStackSettingsChanged(int[] pillIds) {}
    }

    private static final List<Listener> listeners = new ArrayList<>();
    private static final HashSet<Integer> pendingUpdates = new HashSet<>();

    public static void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private static int layoutVersion;

    public static int getLayoutVersion() {
        return layoutVersion;
    }

    public static void notifyLayoutChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            layoutVersion++;
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                    org.telegram.messenger.NotificationCenter.pillStackLayoutChanged);
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onPillStackLayoutChanged();
            }
        });
    }

    public static void notifySettingsChanged(int... pillIds) {
        if (pillIds == null || pillIds.length == 0) {
            for (int id : PillRegistry.getRegisteredIds()) {
                pendingUpdates.add(id);
            }
        } else {
            for (int id : pillIds) {
                pendingUpdates.add(id);
            }
        }
        final int[] ids = pillIds == null ? new int[0] : pillIds;
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onPillStackSettingsChanged(ids);
            }
        });
    }

    public static boolean checkAndClearPendingUpdate(int pillId) {
        return pendingUpdates.remove(pillId);
    }

    public static boolean shouldUpdatePill(int[] changedIds, int... pillIds) {
        if (changedIds == null || changedIds.length == 0 || pillIds.length == 0) {
            return true;
        }
        for (int changed : changedIds) {
            for (int pillId : pillIds) {
                if (changed == pillId) {
                    return true;
                }
            }
        }
        return false;
    }
}
