package org.mzgram.utils;

import android.graphics.Point;

import org.telegram.messenger.AndroidUtilities;

public final class AppUtils {

    private AppUtils() {
    }

    public static int getSwipeVelocity() {
        final Point size = AndroidUtilities.displaySize;
        return size.x > size.y ? 1250 : 850;
    }

    public static int getRoundVideoResolution(int serverValue) {
        return Math.max(ROUND_VIDEO_RESOLUTION, serverValue);
    }

    public static final int ROUND_VIDEO_RESOLUTION = 512;

    public static String ensureUrlHasHttps(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.contains("://")) {
            return url;
        }
        final java.util.regex.Pattern webUrl = org.telegram.messenger.LinkifyPort.WEB_URL;
        return webUrl != null && webUrl.matcher(url).matches() ? "https://".concat(url) : url;
    }
}
