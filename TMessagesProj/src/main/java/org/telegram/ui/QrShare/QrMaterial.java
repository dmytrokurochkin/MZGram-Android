package org.telegram.ui.QrShare;

import androidx.core.graphics.ColorUtils;

import org.mzgram.appearance.AppearanceConfig;

/**
 * MZGram: the QR screen under Material surfaces. The screen's glass (blur, white film, sheen and the Dark out rim) gives
 * way to flat tonal containers in the backdrop's own hue, the way Material You tints controls from what lies under
 * them. Buttons, chips, the menu and the toast are a filled tonal step (tone about 30 on a dark backdrop, about 90 on
 * a light one); the sheet a deeper container, so its white content stays readable on every backdrop.
 */
final class QrMaterial {

    /** Over the camera (the scanner): a neutral dark container, a little see-through, no rim. */
    static final int CAMERA = 0xE6232326;

    private QrMaterial() {
    }

    static boolean on() {
        return AppearanceConfig.materialSurfaces();
    }

    /** A filled tonal button or chip on a backdrop whose middle colour is {@code mid}. */
    static int button(int mid, boolean light) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(mid | 0xFF000000, lab);
        if (light) {
            return ColorUtils.LABToColor(clamp(lab[0] + 10, 88, 95), lab[1] * 0.35, lab[2] * 0.35);
        }
        return ColorUtils.LABToColor(clamp(lab[0] + 16, 28, 38), lab[1] * 0.55, lab[2] * 0.55);
    }

    /**
     * The sheet's container: a step under the buttons, still apart from the backdrop under it, white text on it on
     * every backdrop.
     */
    static int container(int mid) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(mid | 0xFF000000, lab);
        return ColorUtils.LABToColor(clamp(lab[0] + 10, 20, 30), lab[1] * 0.5, lab[2] * 0.5);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
