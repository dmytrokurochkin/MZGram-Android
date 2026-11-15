package app.exteraless.drawer;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

import app.exteraless.appearance.AppearanceConfig;

/**
 * MZGram: the Material 3 navigation drawer. The sheet is a surface container a step above the page with
 * 16 dp end corners, the items are 56 dp pills inset 12 dp with medium 14 sp labels, the counters are plain
 * trailing labels, and the account switcher is a card a tone above the sheet with the current account on
 * the secondary container. It follows the Material navigation setting, as the bottom bar does.
 */
final class DrawerMaterial {

    static final float ITEM_HEIGHT_DP = 56f;
    static final float ITEM_INSET_DP = 12f;
    static final float SHEET_CORNER_DP = 16f;
    static final float DIVIDER_INSET_DP = 28f;

    private DrawerMaterial() {
    }

    static boolean enabled() {
        return AppearanceConfig.newNavigationBarStyle();
    }

    private static int page() {
        return Theme.getColor(Theme.key_windowBackgroundWhite) | 0xFF000000;
    }

    private static int onSurface() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText) | 0xFF000000;
    }

    private static int primary() {
        return Theme.getColor(Theme.key_featuredStickers_addButton) | 0xFF000000;
    }

    /** Surface container low: the sheet of the drawer. */
    static int sheetColor() {
        return ColorUtils.blendARGB(page(), onSurface(), Theme.isCurrentThemeDark() ? 0.045f : 0.03f);
    }

    /** Surface container high: the card of the accounts. */
    static int cardColor() {
        return ColorUtils.blendARGB(page(), onSurface(), Theme.isCurrentThemeDark() ? 0.1f : 0.07f);
    }

    /** Secondary container: the current account. */
    static int selectedColor() {
        return ColorUtils.blendARGB(cardColor(), primary(), Theme.isCurrentThemeDark() ? 0.24f : 0.18f);
    }

    /** On surface variant: icons and counters. */
    static int onSurfaceVariant() {
        return ColorUtils.blendARGB(onSurface(), page(), 0.3f);
    }
}
