/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Pinned chats kept by MZGram itself, on this device only.
 *
 * Telegram allows a few pinned chats in the main list and the archive
 * (the server refuses more with PINNED_DIALOGS_TOO_MUCH; Premium raises the
 * limit, which MZGram does not touch). Once that limit is reached, MZGram
 * pins further chats locally, up to MAX_TOTAL pinned chats in all: they come
 * right after the server's pinned chats, in the order they were pinned, are
 * stored here (per account and list), survive a restart and never go to the
 * server, so other devices do not see them. The chat list marks them with a
 * pin in another color.
 *
 * Custom chat folders keep their own pinned chats on the server with a much
 * higher limit; local pins are only for the main list and the archive.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.HashMap;

public class MZGramLocalPins {

    // Server pins and local pins together, in one list
    public static final int MAX_TOTAL = 10;

    private static final String PREFERENCES_NAME = "mzgram_local_pins";
    private static final MZGramLocalPins[] instances = new MZGramLocalPins[UserConfig.MAX_ACCOUNT_COUNT];

    private final int account;
    private final HashMap<Integer, ArrayList<Long>> pins = new HashMap<>();

    public static MZGramLocalPins getInstance(int account) {
        MZGramLocalPins instance = instances[account];
        if (instance == null) {
            synchronized (MZGramLocalPins.class) {
                instance = instances[account];
                if (instance == null) {
                    instances[account] = instance = new MZGramLocalPins(account);
                }
            }
        }
        return instance;
    }

    private MZGramLocalPins(int account) {
        this.account = account;
    }

    private SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private String key(int folderId) {
        return "a" + account + "_f" + folderId;
    }

    private synchronized ArrayList<Long> list(int folderId) {
        ArrayList<Long> list = pins.get(folderId);
        if (list == null) {
            list = new ArrayList<>();
            String saved = preferences().getString(key(folderId), "");
            if (!TextUtils.isEmpty(saved)) {
                for (String part : saved.split(",")) {
                    try {
                        list.add(Long.parseLong(part));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            pins.put(folderId, list);
        }
        return list;
    }

    private void save(int folderId, ArrayList<Long> list) {
        preferences().edit().putString(key(folderId), TextUtils.join(",", list)).apply();
    }

    // The place of a locally pinned chat in its list (0 first), -1 when not pinned locally
    public synchronized int index(long dialogId, int folderId) {
        return list(folderId).indexOf(dialogId);
    }

    public boolean isPinned(long dialogId, int folderId) {
        return index(dialogId, folderId) >= 0;
    }

    public synchronized ArrayList<Long> get(int folderId) {
        return new ArrayList<>(list(folderId));
    }

    public synchronized void pin(long dialogId, int folderId) {
        ArrayList<Long> list = list(folderId);
        if (!list.contains(dialogId)) {
            list.add(dialogId);
            save(folderId, list);
        }
    }

    public synchronized void unpin(long dialogId, int folderId) {
        ArrayList<Long> list = list(folderId);
        if (list.remove(dialogId)) {
            save(folderId, list);
        }
    }

    // For the tests: forgets what is kept in memory, so the next read comes from the storage
    public synchronized void reload() {
        pins.clear();
    }

    public synchronized void clear() {
        pins.clear();
        SharedPreferences.Editor editor = preferences().edit();
        for (String key : preferences().getAll().keySet()) {
            if (key.startsWith("a" + account + "_")) {
                editor.remove(key);
            }
        }
        editor.commit();
    }

    // Whether pinning one more chat in a list goes locally: the server's limit
    // is reached, and server and local pins together stay within MAX_TOTAL.
    public static boolean pinsLocally(int serverPinned, int localPinned, int serverLimit) {
        return serverPinned >= serverLimit && serverPinned + localPinned < MAX_TOTAL;
    }
}
