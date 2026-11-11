/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.telegram.messenger.mzgram;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

/**
 * MZGram: asks the camera for video stabilization while a round video message
 * is being recorded. Optical stabilization is preferred, electronic is the
 * fallback; if the camera supports neither, nothing changes.
 */
public class MZGramCameraStabilization {

    /** Adds stabilization to a recording capture request when the switch is on. */
    public static void apply(CaptureRequest.Builder builder, CameraCharacteristics characteristics) {
        if (!MZGramConfig.cameraStabilization || builder == null || characteristics == null) {
            return;
        }
        try {
            int[] optical = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
            if (contains(optical, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
                return;
            }
            int[] electronic = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
            if (contains(electronic, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
                builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
            }
        } catch (RuntimeException ignore) {
        }
    }

    private static boolean contains(int[] values, int wanted) {
        if (values == null) {
            return false;
        }
        for (int value : values) {
            if (value == wanted) {
                return true;
            }
        }
        return false;
    }
}
