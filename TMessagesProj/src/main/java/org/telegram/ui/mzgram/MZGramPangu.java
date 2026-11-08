package org.telegram.ui.mzgram;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import org.telegram.messenger.mzgram.MZGramConfig;

/**
 * MZGram: while typing, puts a space between CJK characters and Latin letters
 * or digits (Pangu spacing: "中文abc" becomes "中文 abc"). Idea from the Pangu
 * text-spacing rule; this is MZGram's own small implementation that only looks
 * at the character just typed and its neighbour.
 */
public class MZGramPangu {

    /** Adds the spacing rule to a message field; it does nothing while the switch is off. */
    public static void attach(EditText field) {
        if (field == null) {
            return;
        }
        field.addTextChangedListener(new TextWatcher() {
            private int insertAt = -1;
            private boolean busy;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                insertAt = -1;
                if (!MZGramConfig.panguSpacing || busy || before != 0 || count != 1) {
                    return;
                }
                char typed = s.charAt(start);
                if (start > 0 && needsSpace(s.charAt(start - 1), typed)) {
                    insertAt = start;
                } else if (start + 1 < s.length() && needsSpace(typed, s.charAt(start + 1))) {
                    insertAt = start + 1;
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (insertAt < 0 || busy) {
                    return;
                }
                int at = insertAt;
                insertAt = -1;
                if (at > editable.length()) {
                    return;
                }
                busy = true;
                try {
                    editable.insert(at, " ");
                } finally {
                    busy = false;
                }
            }
        });
    }

    /** True when a space belongs between the two neighbouring characters. */
    public static boolean needsSpace(char left, char right) {
        return (isCjk(left) && isLatinOrDigit(right)) || (isLatinOrDigit(left) && isCjk(right));
    }

    private static boolean isLatinOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // CJK unified ideographs
            || (c >= 0x3400 && c <= 0x4DBF)      // extension A
            || (c >= 0xF900 && c <= 0xFAFF)      // compatibility ideographs
            || (c >= 0x3040 && c <= 0x30FF)      // hiragana and katakana
            || (c >= 0xAC00 && c <= 0xD7AF);     // hangul syllables
    }
}
