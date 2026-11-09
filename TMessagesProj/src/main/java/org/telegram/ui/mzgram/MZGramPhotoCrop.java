/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Crop at full resolution. Telegram cuts the crop out of a bitmap already
 * scaled down to the send size (or to the size of the viewer), so a crop of
 * a quarter of the frame is sent at a quarter of the resolution. With the
 * switch on, the crop is cut out of the source file decoded with the largest
 * sample size that still leaves the cropped region at the send size. Off by
 * default; any doubt (no crop, drawing, video, live photo, too little
 * memory, a decode error) returns null and Telegram's path runs as before.
 *
 * Ported from Mercurygram MgPhotoCrop (GPL-2.0): the sample size and memory
 * budget calculation. The orientation is the one the caller already uses for
 * Telegram's own crop, so the region is the same as without the switch.
 */

package org.telegram.ui.mzgram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.PhotoViewer;

import java.io.File;

public final class MZGramPhotoCrop {

    private MZGramPhotoCrop() {
    }

    /**
     * The cropped photo at up to twice the send size on its long side, or
     * null to keep Telegram's crop. orientation is {rotation, invert} of the
     * source as the caller crops it.
     */
    public static Bitmap render(MediaController.MediaEditState entry, int targetSize, int[] orientation) {
        if (!MZGramConfig.losslessPhotoCrop || entry == null) {
            return null;
        }
        final MediaController.CropState cropState = entry.cropState;
        if (cropState == null || entry.isVideo || entry.isLivePhoto() || entry.paintPath != null || targetSize <= 0) {
            return null;
        }
        final String path = sourcePath(entry);
        if (path == null) {
            return null;
        }
        Bitmap source = null;
        try {
            final BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            final int width = bounds.outWidth;
            final int height = bounds.outHeight;
            if (width <= 0 || height <= 0 || Math.max(width, height) <= targetSize) {
                // Small enough already: Telegram's path loses nothing
                return null;
            }
            final int[] transform = orientation != null && orientation.length >= 2 ? orientation : new int[]{orientation != null && orientation.length > 0 ? orientation[0] : 0, 0};
            final int sample = computeSampleSize(width, height, cropState, transform[0], targetSize, memoryBudget());
            if (sample <= 0) {
                return null;
            }
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            source = BitmapFactory.decodeFile(path, options);
            if (source == null) {
                return null;
            }
            return PhotoViewer.createCroppedBitmap(source, cropState, transform, true);
        } catch (Throwable e) {
            // OutOfMemoryError included: Telegram's crop then runs as before
            FileLog.e("MZGramPhotoCrop.render", e);
            return null;
        } finally {
            if (source != null) {
                source.recycle();
            }
        }
    }

    /** The edited source: the filtered image when there is one, else the original. */
    private static String sourcePath(MediaController.MediaEditState entry) {
        final String path = entry.filterPath != null && !entry.filterPath.isEmpty() ? entry.filterPath : entry.getPath();
        return path == null || path.isEmpty() || !new File(path).exists() ? null : path;
    }

    /** What is free right now, with the headroom StoryEntry.getScaledBitmap keeps. */
    private static long memoryBudget() {
        final Runtime runtime = Runtime.getRuntime();
        final long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        return (long) (available / 1.1f);
    }

    /**
     * Largest power-of-two sample size that keeps the cropped output at or
     * above targetSize on its long side, never coarser than Telegram's own
     * decode, raised within that bound when the decode would not fit in
     * maxBytes. 0 when the crop is degenerate or even that does not fit.
     */
    private static int computeSampleSize(int width, int height, MediaController.CropState cropState, int sourceRotation, int targetSize, long maxBytes) {
        final int rotation = ((cropState.transformRotation + sourceRotation) % 360 + 360) % 360;
        final boolean swapped = rotation == 90 || rotation == 270;
        final int rotatedWidth = swapped ? height : width;
        final int rotatedHeight = swapped ? width : height;
        final float cropWidth = rotatedWidth * cropState.cropPw;
        final float cropHeight = rotatedHeight * cropState.cropPh;
        // A zoomed-in crop magnifies its region, so fewer source pixels feed the output
        final float cropLongSide = Math.max(cropWidth, cropHeight) / Math.max(1f, cropState.cropScale);
        if (cropLongSide <= 0f) {
            return 0;
        }
        int sample = 1;
        while (cropLongSide / (sample * 2) >= targetSize) {
            sample *= 2;
        }
        // Telegram's decode keeps the short side at the target: never go coarser than that
        int coarsest = 1;
        while (Math.min(width, height) / (coarsest * 2) >= targetSize) {
            coarsest *= 2;
        }
        if (sample > coarsest) {
            sample = coarsest;
        }
        // The source and the cropped copy live at the same time
        while (sample < coarsest && decodedBytes(width, height, cropWidth, cropHeight, sample) > maxBytes) {
            sample *= 2;
        }
        return decodedBytes(width, height, cropWidth, cropHeight, sample) > maxBytes ? 0 : sample;
    }

    private static long decodedBytes(int width, int height, float cropWidth, float cropHeight, int sample) {
        final long source = (long) (width / sample) * (height / sample);
        final long crop = (long) (cropWidth / sample) * (long) (cropHeight / sample);
        return (source + crop) * 4L;
    }
}
