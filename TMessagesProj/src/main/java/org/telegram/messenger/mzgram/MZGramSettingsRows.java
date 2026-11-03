/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Interface > "Hide in Telegram's Settings": rows of
 * Telegram's Settings screen (SettingsActivity) the user does not want,
 * hidden one by one, also from the settings search (ProfileActivity's
 * SearchAdapter). Nothing else changes: the screens stay reachable by their
 * links. All shown by default.
 */

package org.telegram.messenger.mzgram;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MZGramSettingsRows {

    public static final class Row {
        public final String key;
        // The row's id in SettingsActivity
        public final int settingsId;
        // Its results in the settings search: guids from first to last
        public final int searchFirst;
        public final int searchLast;
        // One of the Help group
        public final boolean help;

        Row(String key, int settingsId, int searchFirst, int searchLast, boolean help) {
            this.key = key;
            this.settingsId = settingsId;
            this.searchFirst = searchFirst;
            this.searchLast = searchLast;
            this.help = help;
        }
    }

    // In the order of Telegram's Settings. Telegram Premium's search results
    // are 800 and up (its features); Ask a Question, FAQ and Privacy Policy
    // have one each; the others have none.
    private static final List<Row> ROWS = Collections.unmodifiableList(Arrays.asList(
            new Row("premium", 11, 800, 899, false),
            new Row("stars", 12, -1, -1, false),
            new Row("earnings", 13, -1, -1, false),
            new Row("business", 15, -1, -1, false),
            new Row("gift", 16, -1, -1, false),
            new Row("wallet", 25, -1, -1, false),
            new Row("ask", 17, 402, 402, true),
            new Row("faq", 18, 403, 403, true),
            new Row("features", 23, -1, -1, true),
            new Row("policy", 19, 404, 404, true)
    ));

    private MZGramSettingsRows() {
    }

    public static List<Row> rows() {
        return ROWS;
    }

    public static boolean hidden(String key) {
        return MZGramConfig.hiddenSettingsRows.contains(key);
    }

    public static void setHidden(String key, boolean hidden) {
        MZGramConfig.setSettingsRowHidden(key, hidden);
    }

    // Whether SettingsActivity leaves out its row with this id
    public static boolean hidesRow(int settingsId) {
        for (Row row : ROWS) {
            if (row.settingsId == settingsId) {
                return hidden(row.key);
            }
        }
        return false;
    }

    // Whether the settings search leaves out its result with this guid
    public static boolean hidesSearchResult(int guid) {
        for (Row row : ROWS) {
            if (row.searchFirst >= 0 && guid >= row.searchFirst && guid <= row.searchLast) {
                return hidden(row.key);
            }
        }
        return false;
    }

    // With every row of the Help group hidden, its header goes too
    public static boolean helpHidden() {
        for (Row row : ROWS) {
            if (row.help && !hidden(row.key)) {
                return false;
            }
        }
        return true;
    }
}
