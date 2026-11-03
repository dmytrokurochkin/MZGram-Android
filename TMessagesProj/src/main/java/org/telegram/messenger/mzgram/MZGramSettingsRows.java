/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Interface > "Hide in Telegram's Settings". The tests
 * come first: the rows are not written yet.
 */

package org.telegram.messenger.mzgram;

import java.util.Collections;
import java.util.List;

public final class MZGramSettingsRows {

    public static final class Row {
        public final String key;
        public final int settingsId;

        Row(String key, int settingsId) {
            this.key = key;
            this.settingsId = settingsId;
        }
    }

    private MZGramSettingsRows() {
    }

    public static List<Row> rows() {
        return Collections.emptyList();
    }

    public static boolean hidden(String key) {
        return false;
    }

    public static void setHidden(String key, boolean hidden) {
    }

    public static boolean hidesRow(int settingsId) {
        return false;
    }

    public static boolean hidesSearchResult(int guid) {
        return false;
    }

    public static boolean helpHidden() {
        return false;
    }
}
