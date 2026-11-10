/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Local badges system (system.badges-system).
 * Allows assigning custom badges (supporter, verified, developer, donor)
 * to users locally. Stored in device preferences without external tracking.
 */

package org.telegram.messenger.mzgram.peripherals;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramConfig;

import java.util.HashMap;
import java.util.Map;

public final class MZGramBadges {

    private static final String PREFS_NAME = "mzgram_badges";
    private static final String KEY_PREFIX_TEXT = "badge_text_";
    private static final String KEY_PREFIX_COLOR = "badge_color_";

    public static final String BADGE_SUPPORTER = "Supporter";
    public static final String BADGE_VERIFIED = "Verified";
    public static final String BADGE_DEVELOPER = "Developer";
    public static final String BADGE_BETA = "Beta";
    public static final String BADGE_DONOR = "Donor";

    public static final class Badge {
        public final String text;
        public final int color;

        public Badge(String text, int color) {
            this.text = text != null ? text : "";
            this.color = color;
        }
    }

    private static final Map<Long, Badge> memoryCache = new HashMap<>();

    private MZGramBadges() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static Badge getBadge(long userId) {
        if (!MZGramConfig.customBadgesEnabled || userId == 0) {
            return null;
        }
        synchronized (memoryCache) {
            if (memoryCache.containsKey(userId)) {
                return memoryCache.get(userId);
            }
        }
        SharedPreferences p = prefs();
        String text = p.getString(KEY_PREFIX_TEXT + userId, null);
        if (TextUtils.isEmpty(text)) {
            // Default self badge if self and enabled
            long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (userId == selfId) {
                Badge selfBadge = new Badge(BADGE_SUPPORTER, 0xFF4CAF50);
                synchronized (memoryCache) {
                    memoryCache.put(userId, selfBadge);
                }
                return selfBadge;
            }
            return null;
        }
        int color = p.getInt(KEY_PREFIX_COLOR + userId, 0xFF2196F3);
        Badge badge = new Badge(text, color);
        synchronized (memoryCache) {
            memoryCache.put(userId, badge);
        }
        return badge;
    }

    public static void setBadge(long userId, String text, int color) {
        if (userId == 0) {
            return;
        }
        SharedPreferences.Editor edit = prefs().edit();
        if (TextUtils.isEmpty(text)) {
            edit.remove(KEY_PREFIX_TEXT + userId);
            edit.remove(KEY_PREFIX_COLOR + userId);
            synchronized (memoryCache) {
                memoryCache.remove(userId);
            }
        } else {
            edit.putString(KEY_PREFIX_TEXT + userId, text.trim());
            edit.putInt(KEY_PREFIX_COLOR + userId, color);
            synchronized (memoryCache) {
                memoryCache.put(userId, new Badge(text.trim(), color));
            }
        }
        edit.apply();
    }

    public static void clearAll() {
        prefs().edit().clear().apply();
        synchronized (memoryCache) {
            memoryCache.clear();
        }
    }
}
