package org.mzgram.arc.utils;

import android.os.Process;

import org.telegram.messenger.DispatchQueue;

public final class ArcQueues {
    public static final DispatchQueue lastSeenQueue = new DispatchQueue("lastSeenQueue", true, Process.THREAD_PRIORITY_BACKGROUND);

    private ArcQueues() {
    }
}
