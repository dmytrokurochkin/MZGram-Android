/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.settings;

import org.mzgram.core.config.ConfigItem;
import org.mzgram.ext.ExtConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ProfileActivity;

/**
 * Chats settings > Hiding > "Hide in Telegram's Settings": a switch for each row of
 * Telegram's Settings that has nothing to do with the app itself (Money, Premium, Stars,
 * Gram Earnings, Business, Send a Gift, and the Help rows). A hidden row is gone from
 * Settings and from the settings search; nothing else changes.
 */
public final class HiddenSettingsRows {

    // Item ids of the rows in SettingsActivity.fillItems.
    public static final int WALLET = 25;
    public static final int PREMIUM = 11;
    public static final int STARS = 12;
    public static final int EARNINGS = 13;
    public static final int BUSINESS = 15;
    public static final int GIFT = 16;
    public static final int ASK = 17;
    public static final int FAQ = 18;
    public static final int FEATURES = 23;
    public static final int POLICY = 19;

    public static final int[] IDS = {WALLET, PREMIUM, STARS, EARNINGS, BUSINESS, GIFT, ASK, FAQ, FEATURES, POLICY};
    public static final int[] TITLES = {
            R.string.WalletAttachMoney, R.string.TelegramPremium, R.string.TelegramStars, R.string.GramEarnings,
            R.string.TelegramBusiness, R.string.SendAGift, R.string.AskAQuestion, R.string.TelegramFAQ,
            R.string.TelegramFeatures, R.string.PrivacyPolicy,
    };

    private static boolean migrated;

    private HiddenSettingsRows() {
    }

    /** The switches, in the order of IDS. */
    public static ConfigItem[] items() {
        migrate();
        final ExtConfig c = ExtConfig.INSTANCE;
        return new ConfigItem[]{
                c.getHideRowWallet(), c.getHideRowPremium(), c.getHideRowStars(), c.getHideRowEarnings(),
                c.getHideRowBusiness(), c.getHideRowGift(), c.getHideRowAsk(), c.getHideRowFaq(),
                c.getHideRowFeatures(), c.getHideRowPolicy(),
        };
    }

    public static boolean hidden(int id) {
        final ConfigItem[] items = items();
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i] == id) {
                return items[i].Bool();
            }
        }
        return false;
    }

    public static boolean allHidden(int... ids) {
        for (int id : ids) {
            if (!hidden(id)) {
                return false;
            }
        }
        return true;
    }

    /** The settings search: a hidden row's entries are dropped (left null, as unavailable ones are). */
    public static ProfileActivity.SearchAdapter.SearchResult[] filterSearch(ProfileActivity.SearchAdapter.SearchResult[] results) {
        if (results == null) {
            return null;
        }
        for (int i = 0; i < results.length; i++) {
            final ProfileActivity.SearchAdapter.SearchResult r = results[i];
            if (r != null && hiddenInSearch(r.guid)) {
                results[i] = null;
            }
        }
        return results;
    }

    private static boolean hiddenInSearch(int guid) {
        if (guid >= 800 && guid < 900) {
            return hidden(PREMIUM); // Telegram Premium and its features
        }
        switch (guid) {
            case 402:
                return hidden(ASK);
            case 403:
                return hidden(FAQ);
            case 404:
                return hidden(POLICY);
        }
        return false;
    }

    // The two old switches hid whole sections: carry them over to the rows once.
    private static void migrate() {
        if (migrated) {
            return;
        }
        migrated = true;
        final ExtConfig c = ExtConfig.INSTANCE;
        if (c.getHidePremiumSection().Bool()) {
            for (ConfigItem item : new ConfigItem[]{c.getHideRowPremium(), c.getHideRowStars(), c.getHideRowEarnings(),
                    c.getHideRowBusiness(), c.getHideRowGift()}) {
                item.setConfigBool(true);
            }
            c.getHidePremiumSection().setConfigBool(false);
        }
        if (c.getHideHelpSection().Bool()) {
            for (ConfigItem item : new ConfigItem[]{c.getHideRowAsk(), c.getHideRowFaq(), c.getHideRowFeatures(), c.getHideRowPolicy()}) {
                item.setConfigBool(true);
            }
            c.getHideHelpSection().setConfigBool(false);
        }
    }
}
