/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The round video camera on Jetpack CameraX, a third engine beside
 * Telegram's Camera1 (CameraSession) and Camera2 (Camera2Session). It is
 * used only when Settings > MZGram > Chats > "CameraX for round videos"
 * (MZGramConfig.cameraX) is on; otherwise InstantCameraView works as in
 * Telegram.
 *
 * What it gives over Camera2:
 *  - both cameras bound at once (ConcurrentCamera), so switching the camera
 *    does not rebuild the session, where the phone allows it;
 *  - the start on the ultra-wide lens (MZGramConfig.cameraXWideLens);
 *  - preview stabilization (MZGramConfig.cameraStabilization, the same
 *    switch as for Camera2);
 *  - the real zoom range of the logical camera, with the lens stops read
 *    by MZGramLensStops, for the zoom slider (MZGramRoundZoomSlider).
 *
 * Conflict rule 2.1 ("Disable instant camera" against the round video
 * pipeline): CameraX is never warmed up in the background. The provider is
 * asked for only when the round video view opens its camera, and it is
 * unbound when the view closes, so MZGramConfig.disableInstantCamera and
 * the recording by holding the button work as before.
 *
 * All methods but the ones marked are called on the UI thread: CameraX
 * binds to a lifecycle, and LifecycleRegistry wants the main thread.
 *
 * Port of exteraless CameraXSession (GPL-3.0), itself a port of
 * exteraGram's CameraXSession. Changed for MZGram: 30 frames per second
 * only (no 60 fps mode), no switch for the front camera's mirror, the
 * round video size from MessagesController, sorting without API 24 calls.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Looper;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ConcurrentCamera;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.Preview;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Stories.recorder.DualCameraView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MZGramCameraX {

    /** Whether the round video camera runs on CameraX. */
    public static boolean isOn() {
        return MZGramConfig.cameraX;
    }

    /**
     * The lifecycle CameraX binds to: the round video view has no activity of
     * its own. One per session: closing the session destroys it, and the
     * next round video opens a new session.
     */
    private static class CameraLifecycle implements LifecycleOwner {

        private final LifecycleRegistry lifecycleRegistry;

        public CameraLifecycle() {
            lifecycleRegistry = new LifecycleRegistry(this);
            lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return lifecycleRegistry;
        }

        public void start() {
            try {
                if (lifecycleRegistry.getCurrentState() != Lifecycle.State.DESTROYED) {
                    lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public void stop() {
            try {
                lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    /** The size CameraX gives to the surface, that is, the preview size. */
    public interface PreviewSizeListener {
        void onPreviewSize(int width, int height);
    }

    public static final int FRAME_RATE = 30;
    private static final Range<Integer> FPS_30_RANGE = new Range<>(FRAME_RATE, FRAME_RATE);
    /** Without the sensor's characteristics it is taken for a usual 4:3 one. */
    private static final Size FALLBACK_SENSOR_ASPECT = new Size(4, 3);

    private static final Map<CameraSelector, Boolean> STABILIZATION_SUPPORT_CACHE = new ConcurrentHashMap<>();
    private static volatile Boolean seamlessSwitchingAvailableCache;

    private final CameraLifecycle lifecycle;
    private final Preview.SurfaceProvider surfaceProviderPrimary;
    private final int roundSize;
    private Preview.SurfaceProvider surfaceProviderSecondary;

    private ProcessCameraProvider provider;
    private Camera camera;
    private Camera cameraFront;
    private Camera cameraBack;
    private CameraControl cameraControl;
    private CameraControl cameraControlFront;
    private CameraControl cameraControlBack;
    private CameraSelector cameraSelector;
    private Preview previewUseCase;
    private Preview previewUseCaseBack;

    private boolean isFrontface;
    private boolean isInitiated;
    private boolean isDualMode;
    private boolean isBinding;
    private boolean isTorchOn;

    /**
     * Made on the UI thread (LifecycleRegistry).
     *
     * @param roundSize the side of the round video in pixels
     *                  (MessagesController.roundVideoSize), the smallest
     *                  preview side wanted
     */
    public MZGramCameraX(Preview.SurfaceProvider surfaceProvider, int roundSize) {
        this.lifecycle = new CameraLifecycle();
        this.surfaceProviderPrimary = surfaceProvider;
        this.roundSize = roundSize > 0 ? roundSize : 384;
    }

    // ---- Opening and closing ----

    public void initCamera(Context context, boolean frontface, boolean dual, Runnable onReady) {
        isFrontface = frontface;
        final ListenableFuture<ProcessCameraProvider> future;
        try {
            future = ProcessCameraProvider.getInstance(context);
        } catch (Throwable t) {
            FileLog.e(t);
            isInitiated = false;
            return;
        }
        future.addListener(() -> {
            try {
                provider = future.get();
                if (lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                    return;
                }
                isDualMode = dual && supportsConcurrentFrontBackPair();
                rebindCamera();
                lifecycle.start();
                if (onReady != null) {
                    onReady.run();
                }
            } catch (Exception e) {
                FileLog.e(e);
                isInitiated = false;
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /** May come from the camera thread: moved to the UI thread, where the lifecycle lives. */
    public void closeCamera() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(this::closeCamera);
            return;
        }
        try {
            if (provider != null) {
                provider.unbindAll();
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            lifecycle.stop();
            isInitiated = false;
            clearCameraReferences();
        }
    }

    private void rebindCamera() {
        if (provider == null
                || lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED
                || isBinding) {
            return;
        }
        isBinding = true;
        clearCameraReferences();
        try {
            provider.unbindAll();
            if (isDualMode) {
                bindDualUseCases();
            } else {
                bindSingleUseCases();
            }
            isInitiated = camera != null;
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
            try {
                provider.unbindAll();
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            clearCameraReferences();
        } finally {
            isBinding = false;
        }
    }

    private void clearCameraReferences() {
        cameraBack = null;
        cameraFront = null;
        camera = null;
        cameraControlBack = null;
        cameraControlFront = null;
        cameraControl = null;
        previewUseCaseBack = null;
        previewUseCase = null;
    }

    // ---- Binding ----

    /**
     * One lens: first at 30 frames per second, then without a frame rate,
     * then without stabilization; the preview is built again on each step,
     * because the frame rate and the stabilization are set when it is built.
     */
    private void bindSingleUseCases() {
        try {
            cameraSelector = isFrontface ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
            if (!provider.hasCamera(cameraSelector)) {
                isInitiated = false;
                return;
            }
            final boolean stabilization = MZGramConfig.cameraStabilization
                    && isPreviewStabilizationSupported(cameraSelector);

            previewUseCase = buildPreview(cameraSelector, stabilization, null);
            previewUseCase.setSurfaceProvider(surfaceProviderPrimary);
            camera = tryBindSingleAtFrameRate(FPS_30_RANGE);

            if (camera == null) {
                try {
                    camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (camera == null && stabilization) {
                previewUseCase = buildPreview(cameraSelector, false, null);
                previewUseCase.setSurfaceProvider(surfaceProviderPrimary);
                camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase);
            }
            if (camera == null) {
                isInitiated = false;
                return;
            }
            cameraControl = camera.getCameraControl();
            observeCameraState(camera);
            applyInitialZoom(camera, cameraControl);
            updateTorchState();
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
        }
    }

    /**
     * Both lenses at once, for the switch without a pause: 30 frames per
     * second, then as it comes, and only then back to one lens.
     */
    private void bindDualUseCases() {
        ConcurrentCamera concurrent = tryBindConcurrentCameras(FPS_30_RANGE);
        if (concurrent == null) {
            concurrent = tryBindConcurrentCameras(null);
        }
        if (concurrent == null) {
            isDualMode = false;
            bindSingleUseCases();
            return;
        }
        for (Camera bound : concurrent.getCameras()) {
            if (bound.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                cameraFront = bound;
                cameraControlFront = bound.getCameraControl();
                applyInitialZoom(cameraFront, cameraControlFront);
            } else {
                cameraBack = bound;
                cameraControlBack = bound.getCameraControl();
                applyInitialZoom(cameraBack, cameraControlBack);
            }
            observeCameraState(bound);
        }
        updateActiveControl(isFrontface);
        updateTorchState();
    }

    private ConcurrentCamera tryBindConcurrentCameras(Range<Integer> range) {
        try {
            previewUseCase = buildPreview(CameraSelector.DEFAULT_FRONT_CAMERA, false, range);
            previewUseCase.setSurfaceProvider(surfaceProviderPrimary);

            previewUseCaseBack = buildPreview(CameraSelector.DEFAULT_BACK_CAMERA, false, range);
            if (surfaceProviderSecondary != null) {
                previewUseCaseBack.setSurfaceProvider(surfaceProviderSecondary);
            }

            List<ConcurrentCamera.SingleCameraConfig> configs = new ArrayList<>(2);
            configs.add(new ConcurrentCamera.SingleCameraConfig(CameraSelector.DEFAULT_FRONT_CAMERA,
                    new UseCaseGroup.Builder().addUseCase(previewUseCase).build(), lifecycle));
            configs.add(new ConcurrentCamera.SingleCameraConfig(CameraSelector.DEFAULT_BACK_CAMERA,
                    new UseCaseGroup.Builder().addUseCase(previewUseCaseBack).build(), lifecycle));
            return provider.bindToLifecycle(configs);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                provider.unbindAll();
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            return null;
        }
    }

    private Camera tryBindSingleAtFrameRate(Range<Integer> range) {
        try {
            CameraInfo info = provider.getCameraInfo(cameraSelector);
            SessionConfig.Builder builder = new SessionConfig.Builder(previewUseCase);
            if (!info.getSupportedFrameRateRanges(builder.build()).contains(range)) {
                return null;
            }
            builder.setFrameRateRange(range);
            SessionConfig sessionConfig = builder.build();
            if (!info.isSessionConfigSupported(sessionConfig)) {
                return null;
            }
            return provider.bindToLifecycle(lifecycle, cameraSelector, sessionConfig);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** The preview for a round video: 4:3, its own order of sizes, stabilization if asked. */
    private Preview buildPreview(CameraSelector selector, boolean stabilization, Range<Integer> range) {
        final Size sensorAspect = getSensorAspect(selector);
        final int target = roundSize;

        Preview.Builder builder = new Preview.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionFilter((sizes, rotation) -> sortRoundPreviewSizes(sizes, sensorAspect, target))
                        .build());
        if (range != null) {
            builder.setTargetFrameRate(range);
        }
        // With two lenses bound there is no preview stabilization at all.
        builder.setPreviewStabilizationEnabled(!isDualMode && stabilization);
        return builder.build();
    }

    // ---- The preview size ----

    /**
     * The order of preference for a round video: first the sizes not smaller
     * than the round video, among them the ones that cut less of the
     * sensor's view, then the closest to twice the round video, then the
     * bigger one.
     */
    public static List<Size> sortRoundPreviewSizes(List<Size> sizes, Size sensorAspect, int target) {
        List<Size> sorted = new ArrayList<>(sizes);
        final int comfortable = target * 2;
        final int sensorShort = Math.min(sensorAspect.getWidth(), sensorAspect.getHeight());
        final int sensorLong = Math.max(sensorAspect.getWidth(), sensorAspect.getHeight());
        Collections.sort(sorted, (a, b) -> compareRoundPreviewSizes(a, b, target, comfortable, sensorShort, sensorLong));
        return sorted;
    }

    private static int compareRoundPreviewSizes(Size a, Size b, int target, int comfortable,
                                                int sensorShort, int sensorLong) {
        final int aShort = Math.min(a.getWidth(), a.getHeight());
        final int aLong = Math.max(a.getWidth(), a.getHeight());
        final int bShort = Math.min(b.getWidth(), b.getHeight());
        final int bLong = Math.max(b.getWidth(), b.getHeight());

        final boolean aEnough = aShort >= target;
        if (aEnough != (bShort >= target)) {
            return aEnough ? -1 : 1;
        }
        if (!aEnough) {
            int cmp = Integer.compare(bShort, aShort);
            if (cmp != 0) {
                return cmp;
            }
        }
        // The field of view first: 16:9 on a 4:3 sensor is a crop, and the
        // round video taken from it loses a part of its width.
        int cmp = Long.compare(
                fieldOfViewPenalty(aShort, aLong, sensorShort, sensorLong) * bShort,
                fieldOfViewPenalty(bShort, bLong, sensorShort, sensorLong) * aShort);
        if (cmp != 0) {
            return cmp;
        }
        final boolean aComfortable = aShort >= comfortable;
        if (aComfortable != (bShort >= comfortable)) {
            return aComfortable ? -1 : 1;
        }
        if (!aComfortable) {
            cmp = Integer.compare(bShort, aShort);
            if (cmp != 0) {
                return cmp;
            }
        }
        cmp = Integer.compare(Math.abs(aShort - comfortable), Math.abs(bShort - comfortable));
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare((long) b.getWidth() * b.getHeight(), (long) a.getWidth() * a.getHeight());
    }

    /** How much narrower than the sensor the frame is: the wider, the smaller. */
    private static long fieldOfViewPenalty(int shortSide, int longSide, int sensorShort, int sensorLong) {
        long frame = (long) shortSide * sensorLong;
        return Math.max(0L, (((long) longSide * sensorShort) - frame) * 100 - frame);
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private Size getSensorAspect(CameraSelector selector) {
        try {
            if (provider != null && selector != null && provider.hasCamera(selector)) {
                Rect array = Camera2CameraInfo.from(provider.getCameraInfo(selector))
                        .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (array != null && array.width() > 0 && array.height() > 0) {
                    return new Size(array.width(), array.height());
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return FALLBACK_SENSOR_ASPECT;
    }

    private boolean isPreviewStabilizationSupported(CameraSelector selector) {
        if (selector == null) {
            return false;
        }
        try {
            if (provider == null || !provider.hasCamera(selector)) {
                return false;
            }
            Boolean cached = STABILIZATION_SUPPORT_CACHE.get(selector);
            if (cached != null) {
                return cached;
            }
            boolean supported = Preview.getPreviewCapabilities(provider.getCameraInfo(selector))
                    .isStabilizationSupported();
            STABILIZATION_SUPPORT_CACHE.put(selector, supported);
            return supported;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    // ---- Both cameras at once ----

    /** Whether the switch without a pause is possible: the round dual mode and a lens pair. */
    public static boolean isRoundDualAvailable(Context context) {
        return DualCameraView.roundDualAvailableStatic(context) && isSeamlessSwitchingAvailable(context);
    }

    private static boolean isSeamlessSwitchingAvailable(Context context) {
        if (seamlessSwitchingAvailableCache != null) {
            return seamlessSwitchingAvailableCache;
        }
        seamlessSwitchingAvailableCache = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE
                && SharedConfig.allowPreparingHevcPlayers()
                && hasConcurrentFrontBackPair(context);
        return seamlessSwitchingAvailableCache;
    }

    /**
     * The system promises the front and the back camera at once. Asked of
     * camera2 directly: ProcessCameraProvider knows it only after it is
     * ready, and it is needed before the session is made.
     */
    private static boolean hasConcurrentFrontBackPair(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null
                || !packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) {
            return false;
        }
        CameraManager cameraManager = context.getSystemService(CameraManager.class);
        if (cameraManager == null) {
            return false;
        }
        try {
            for (Set<String> ids : cameraManager.getConcurrentCameraIds()) {
                boolean front = false;
                boolean back = false;
                for (String id : ids) {
                    Integer facing = cameraManager.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.LENS_FACING);
                    if (facing == null) {
                        continue;
                    }
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        front = true;
                    } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        back = true;
                    }
                }
                if (front && back) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private boolean supportsConcurrentFrontBackPair() {
        if (provider == null) {
            return false;
        }
        try {
            for (List<CameraInfo> combination : provider.getAvailableConcurrentCameraInfos()) {
                boolean front = false;
                boolean back = false;
                for (CameraInfo info : combination) {
                    if (info.getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                        front = true;
                    } else if (info.getLensFacing() == CameraSelector.LENS_FACING_BACK) {
                        back = true;
                    }
                }
                if (front && back) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    // ---- Zoom, focus, torch ----

    /**
     * The zoom at the start. The ultra-wide lens is a zoom below one, and it
     * is known only after the camera opens: until then ZoomState is empty, so
     * it is waited for once.
     */
    private void applyInitialZoom(final Camera camera, final CameraControl control) {
        if (control == null || camera == null) {
            return;
        }
        control.setZoomRatio(1.0f);
        if (!wantsWideAngleStart(camera)) {
            return;
        }
        final LiveData<ZoomState> zoomState = camera.getCameraInfo().getZoomState();
        if (zoomState.getValue() != null) {
            applyWideAngle(control, zoomState.getValue());
            return;
        }
        zoomState.observe(lifecycle, new Observer<ZoomState>() {
            @Override
            public void onChanged(ZoomState state) {
                if (state == null) {
                    return;
                }
                zoomState.removeObserver(this);
                if (wantsWideAngleStart(camera)) {
                    applyWideAngle(control, state);
                }
            }
        });
    }

    private void applyWideAngle(CameraControl control, ZoomState state) {
        if (state == null || state.getMinZoomRatio() >= 1.0f) {
            return;
        }
        control.setZoomRatio(state.getMinZoomRatio());
    }

    /**
     * The back camera by the setting. The front one always: its zoom below
     * one is not another lens but the full sensor instead of a crop, and it
     * is its usual view.
     */
    private boolean wantsWideAngleStart(Camera camera) {
        if (camera == null) {
            return false;
        }
        return camera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_FRONT
                || MZGramConfig.cameraXWideLens;
    }

    public void focusToPoint(float x, float y, float viewWidth, float viewHeight) {
        Display display;
        if (cameraControl == null || camera == null || viewWidth <= 0 || viewHeight <= 0
                || (display = getDefaultDisplay()) == null) {
            return;
        }
        try {
            cameraControl.startFocusAndMetering(new FocusMeteringAction.Builder(
                    new DisplayOrientedMeteringPointFactory(display, camera.getCameraInfo(), viewWidth, viewHeight)
                            .createPoint(x, y),
                    FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setTorchEnabled(boolean enabled) {
        isTorchOn = enabled;
        updateTorchState();
    }

    /** Only the back camera has a torch, so the front one is always kept dark. */
    private void updateTorchState() {
        try {
            if (!isDualMode) {
                if (camera == null || cameraControl == null || !camera.getCameraInfo().hasFlashUnit()) {
                    return;
                }
                boolean back = camera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_BACK;
                cameraControl.enableTorch(isTorchOn && back);
                return;
            }
            if (cameraControlFront != null) {
                cameraControlFront.enableTorch(false);
            }
            if (cameraControlBack == null || cameraBack == null
                    || !cameraBack.getCameraInfo().hasFlashUnit()) {
                return;
            }
            cameraControlBack.enableTorch(isTorchOn && !isFrontface);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setZoomRatio(float ratio) {
        if (cameraControl == null) {
            return;
        }
        try {
            cameraControl.setZoomRatio(ratio);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public float getZoomRatio() {
        ZoomState state = zoomState();
        return state == null ? 1f : state.getZoomRatio();
    }

    public float getMinZoomRatio() {
        ZoomState state = zoomState();
        return state == null ? 1f : state.getMinZoomRatio();
    }

    public float getMaxZoomRatio() {
        ZoomState state = zoomState();
        return state == null ? 1f : state.getMaxZoomRatio();
    }

    /**
     * The zoom the camera returns to: the front camera its full sensor (its
     * minimum), the back one 1x, or its ultra-wide lens by the setting.
     */
    public float resetZoom() {
        ZoomState state = zoomState();
        if (state == null) {
            return 1f;
        }
        final float min = state.getMinZoomRatio();
        final float max = state.getMaxZoomRatio();
        if (isActiveCameraFrontface() || MZGramConfig.cameraXWideLens) {
            return min;
        }
        return Math.max(min, Math.min(max, 1f));
    }

    /** The zoom state of the active camera, or null until it opens. */
    public ZoomState zoomState() {
        return camera == null ? null : camera.getCameraInfo().getZoomState().getValue();
    }

    /** The live zoom state of the active camera, or null without one. */
    public LiveData<ZoomState> zoomStateData() {
        return camera == null ? null : camera.getCameraInfo().getZoomState();
    }

    // ---- Switching the camera ----

    public void switchCamera() {
        isFrontface = !isFrontface;
        if (isDualMode && cameraFront != null && cameraBack != null) {
            // Both lenses are bound: only the one that records changes.
            updateActiveControl(isFrontface);
            updateTorchState();
        } else {
            rebindCamera();
        }
    }

    private void updateActiveControl(boolean frontface) {
        camera = frontface ? cameraFront : cameraBack;
        cameraControl = frontface ? cameraControlFront : cameraControlBack;
    }

    public void setSecondSurfaceProvider(Preview.SurfaceProvider surfaceProvider) {
        surfaceProviderSecondary = surfaceProvider;
        if (isInitiated && isDualMode && previewUseCaseBack != null) {
            previewUseCaseBack.setSurfaceProvider(surfaceProvider);
        }
    }

    // ---- State ----

    public boolean isInitiated() {
        return isInitiated;
    }

    public boolean isReady() {
        return isInitiated && !isBinding && camera != null;
    }

    public boolean isDualMode() {
        return isDualMode;
    }

    public boolean isFrontface() {
        return isFrontface;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public String getActiveCameraId() {
        if (camera == null) {
            return null;
        }
        try {
            return Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public boolean isActiveCameraFrontface() {
        if (camera == null) {
            return isFrontface;
        }
        switch (camera.getCameraInfo().getLensFacing()) {
            case CameraSelector.LENS_FACING_FRONT:
                return true;
            case CameraSelector.LENS_FACING_BACK:
                return false;
            default:
                return isFrontface;
        }
    }

    /** CameraX has no world angle: the turn is taken from the display. Any thread. */
    public static int getDisplayOrientation() {
        Display display = getDefaultDisplay();
        int rotation = display != null ? display.getRotation() : Surface.ROTATION_0;
        switch (rotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private void observeCameraState(Camera camera) {
        try {
            camera.getCameraInfo().getCameraState().observe(lifecycle, state -> {
                CameraState.StateError error = state.getError();
                if (error == null) {
                    return;
                }
                FileLog.e("MZGramCameraX camera state error: code=" + error.getCode() + " type=" + state.getType());
                if (error.getCause() != null) {
                    FileLog.e(error.getCause());
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static Display getDefaultDisplay() {
        DisplayManager manager = (DisplayManager) ApplicationLoader.applicationContext
                .getSystemService(Context.DISPLAY_SERVICE);
        return manager != null ? manager.getDisplay(Display.DEFAULT_DISPLAY) : null;
    }

    // ---- The surface ----

    /**
     * The bridge between CameraX and the texture of the GL thread: CameraX
     * asks for a surface, it gets one on the round video's SurfaceTexture,
     * and the size is told upwards, first the requested one, then the one
     * after the crop.
     */
    public static Preview.SurfaceProvider createSurfaceProvider(final Context context,
                                                                final SurfaceTexture surfaceTexture,
                                                                final PreviewSizeListener listener) {
        return request -> {
            try {
                final Size resolution = request.getResolution();
                listener.onPreviewSize(resolution.getWidth(), resolution.getHeight());
                request.setTransformationInfoListener(ContextCompat.getMainExecutor(context), info -> {
                    Rect crop = info.getCropRect();
                    listener.onPreviewSize(
                            crop.width() > 0 ? crop.width() : resolution.getWidth(),
                            crop.height() > 0 ? crop.height() : resolution.getHeight());
                });
                surfaceTexture.setDefaultBufferSize(resolution.getWidth(), resolution.getHeight());
                final Surface surface = new Surface(surfaceTexture);
                request.provideSurface(surface, ContextCompat.getMainExecutor(context), result -> {
                    request.clearTransformationInfoListener();
                    surface.release();
                });
            } catch (Exception e) {
                FileLog.e(e);
                request.willNotProvideSurface();
            }
        };
    }
}
