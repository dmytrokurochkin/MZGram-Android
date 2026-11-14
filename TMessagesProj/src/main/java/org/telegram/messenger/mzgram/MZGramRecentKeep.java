/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Unlimited saved GIFs and Unlimited recent stickers: Telegram's server
 * keeps only its own number of them, and a sync replaces this device's list
 * with the server's. With the switch on, what only this device has stays,
 * after the server's list, in the order it had. From exteraless (GPL-3.0).
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

public class MZGramRecentKeep {

    public static boolean keeps(boolean gif, int type) {
        return gif ? MZGramConfig.unlimitedSavedGifs : type == MediaDataController.TYPE_IMAGE && MZGramConfig.unlimitedRecentStickers;
    }

    // The server's documents first, then the ones only this device has, up to limit
    public static ArrayList<TLRPC.Document> merge(ArrayList<TLRPC.Document> server, ArrayList<TLRPC.Document> local, int limit) {
        if (local == null || local.isEmpty()) {
            return server;
        }
        try {
            // The list belongs to the UI thread and the server answers on another: work on a copy
            ArrayList<TLRPC.Document> kept = new ArrayList<>(local);
            ArrayList<TLRPC.Document> result = new ArrayList<>(server);
            HashSet<Long> ids = new HashSet<>();
            for (int a = 0; a < server.size(); a++) {
                TLRPC.Document document = server.get(a);
                if (document != null) {
                    ids.add(document.id);
                }
            }
            for (int a = 0; a < kept.size() && result.size() < limit; a++) {
                TLRPC.Document document = kept.get(a);
                if (document != null && ids.add(document.id)) {
                    result.add(document);
                }
            }
            return result;
        } catch (Exception e) {
            FileLog.e(e);
            return server;
        }
    }
}
