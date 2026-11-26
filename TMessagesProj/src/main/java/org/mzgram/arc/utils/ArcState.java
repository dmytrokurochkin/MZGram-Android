/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package org.mzgram.arc.utils;

import android.util.LongSparseArray;

import java.util.ArrayList;

import org.mzgram.core.CoreConfig;

public class ArcState {
    private static final ArcStateVariable allowReadPacket = new ArcStateVariable();
    private static final ArcStateVariable hideSelection = new ArcStateVariable();
    private static final LongSparseArray<ArrayList<Integer>> deletePermitted = new LongSparseArray<>();
    private static final LongSparseArray<ArrayList<Integer>> deleteKept = new LongSparseArray<>();

    public static void setAllowReadPacket(boolean val, int resetAfter) {
        allowReadPacket.val = val;
        allowReadPacket.resetAfter = resetAfter;
    }

    public static boolean getAllowReadPacket() {
        return CoreConfig.sendReadMessagePackets.Bool() || allowReadPacket.process();
    }

    public static void setHideSelection(boolean val, int resetAfter) {
        hideSelection.val = val;
        hideSelection.resetAfter = resetAfter;
    }

    public static boolean getHideSelection() {
        return hideSelection.process();
    }

    public static void permitDeleteMessage(long dialogId, int messageId) {
        var list = deletePermitted.get(dialogId);
        if (list == null) {
            list = new ArrayList<>();
            deletePermitted.put(dialogId, list);
        }

        list.add(messageId);
    }

    public static boolean isDeletePermitted(long dialogId, int messageId) {
        var list = deletePermitted.get(dialogId);
        if (list == null) {
            return false;
        }

        return list.contains(messageId);
    }

    public static void keepDeletedMessages(long dialogId, ArrayList<Integer> messageIds) {
        var list = deleteKept.get(dialogId);
        if (list == null) {
            list = new ArrayList<>();
            deleteKept.put(dialogId, list);
        }

        list.addAll(messageIds);
    }

    public static boolean isDeleteKept(long dialogId, int messageId) {
        var list = deleteKept.get(dialogId);
        if (list == null) {
            return false;
        }

        return list.contains(messageId);
    }

    public static void releaseKeptMessages(long dialogId) {
        deleteKept.remove(dialogId);
    }

    public static void messageDeleted(long dialogId, int messageId) {
        var list = deletePermitted.get(dialogId);
        if (list == null) {
            return;
        }

        list.remove((Object) messageId);
    }
}
