// Ported from exteraless (GPL-3.0).

package org.telegram.ui.mzgram;

import org.telegram.ui.ActionBar.Theme;

/**
 * Common color palette for settings preview cells.
 */
public final class MZGramPreviewColors {

    private MZGramPreviewColors() {
    }

    /** Mock card background. */
    public static int getBackgroundColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                Theme.isCurrentThemeDark() ? 0.05f : 0.035f);
    }

    /** Mock card outline. */
    public static int getOutlineColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                (Theme.isCurrentThemeDark() ? 0.05f : 0.035f) + 0.085f);
    }

    /**
     * Mock text/placeholder lines inside preview cards.
     *
     * @param primary true for primary title, false for secondary subtitle
     */
    public static int getMockColor(boolean primary) {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2),
                primary ? 0.4f : 0.2f);
    }
}
