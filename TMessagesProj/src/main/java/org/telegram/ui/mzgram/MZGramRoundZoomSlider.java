/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The zoom slider under the round video camera when it runs on CameraX
 * (MZGramCameraX). It takes the real zoom range of the camera, so ".5" is
 * the real ultra-wide lens and not a digital zoom, and its stops come from
 * the phone's lenses (MZGramLensStops) where the phone tells them; the
 * front camera gets "1" and "2" from its full sensor.
 *
 * The session is ready only after the camera opens, so the binding is
 * tried up to 25 times every 100 ms; until the range is known the slider
 * is hidden. Settings > MZGram > Chats > "Zoom slider"
 * (MZGramConfig.cameraXZoomSlider) hides it; pinch zoom stays.
 *
 * Port of exteraless InstantCameraZoomSlider (GPL-3.0), from exteraGram.
 * Changed for MZGram: CameraX only (Telegram's Camera1 and Camera2 keep
 * their own zoom), the listener gets the zoom ratio.
 */

package org.telegram.ui.mzgram;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.camera.core.ZoomState;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.mzgram.MZGramCameraX;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramLensStops;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class MZGramRoundZoomSlider extends MZGramZoomSliderView {

    public interface OnCameraZoomChangeListener {
        /** @param zoom the zoom ratio; fromUser when the slider set it */
        void onCameraZoomChanged(float zoom, boolean fromUser);
    }

    public static final AnimationProperties.FloatProperty<MZGramRoundZoomSlider> OPEN_ALPHA =
        new AnimationProperties.FloatProperty<MZGramRoundZoomSlider>("openAlpha") {
            @Override
            public void setValue(MZGramRoundZoomSlider slider, float value) {
                slider.setOpenAlpha(value);
            }

            @Override
            public Float get(MZGramRoundZoomSlider slider) {
                return slider.getOpenAlpha();
            }
        };

    private static final int MAX_BIND_RETRIES = 25;
    private static final long BIND_RETRY_DELAY = 100L;
    private static final long APPEAR_DURATION = 180L;
    private static final long ZOOM_UPDATE_INTERVAL = 1000L / MZGramCameraX.FRAME_RATE;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable bindRunnable = this::tryBind;
    private final Observer<ZoomState> zoomObserver = this::onZoomStateChanged;

    private MZGramCameraX session;
    private LiveData<ZoomState> zoomStateData;

    private float defaultZoom = 1f;
    private float wideZoom = 1f;

    private OnCameraZoomChangeListener cameraZoomChangeListener;
    private BlurredBackgroundDrawable blurBackground;
    private float blurCornerRadius = -1f;

    private ValueAnimator appearAnimator;
    private float appearProgress;
    private float openAlpha;
    private float baseTranslationY;
    private int textureViewSize;

    private boolean switchingCamera;
    private boolean animateNextConfiguration;
    private int bindRetries;

    private final Runnable zoomFlushRunnable = this::flushPendingZoom;
    private float pendingZoom = Float.NaN;
    private float lastAppliedZoom = Float.NaN;
    private long lastZoomAppliedAt;
    private boolean zoomFlushScheduled;

    public MZGramRoundZoomSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setVisibility(GONE);
        applyAppearProgress();
        setOnZoomChangeListener(this::applyZoom);
        applyTelegramColors();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private void applyTelegramColors() {
        final int background = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        final int onAccent = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
        final int text = Theme.getColor(Theme.key_chat_messagePanelText, resourcesProvider);
        setColors(background, text, accent, accent, onAccent);
        setToggleTextColor(text);
    }

    // ---------- stops ----------

    private static void addDistinctStop(ArrayList<Float> stops, float value) {
        for (int i = 0; i < stops.size(); i++) {
            final float existing = stops.get(i);
            if (Math.abs(existing - value) <= 1.0E-4f) {
                return;
            }
            if (existing > value) {
                stops.add(i, value);
                return;
            }
        }
        stops.add(value);
    }

    static float[] boundStops(float[] values, float min, float max, boolean withEdges) {
        final ArrayList<Float> stops = new ArrayList<>(values.length + 2);
        if (withEdges) {
            addDistinctStop(stops, min);
        }
        for (float value : values) {
            if (value <= 0f || !isFinite(value)) {
                continue;
            }
            if (value < min - 1.0E-4f) {
                if (!withEdges) {
                    addDistinctStop(stops, min);
                }
            } else if (value <= max + 1.0E-4f) {
                addDistinctStop(stops, clamp(value, min, max));
            }
        }
        if (withEdges) {
            addDistinctStop(stops, max);
        }
        final float[] result = new float[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            result[i] = stops.get(i);
        }
        return result;
    }

    /** {@code unit} is the real zoom labelled "1x". */
    static float[] buildRulerStops(float min, float max, float unit) {
        final float[] stops = boundStops(new float[]{min, unit, 2f * unit, 5f * unit, 10f * unit, 30f * unit}, min, max, false);
        // The edge gets its own label only when it is clearly beyond the last
        // tick, so "10" and "11" do not stand side by side.
        if (stops.length == 0 || stops[stops.length - 1] * 1.15f < max) {
            return boundStops(stops, min, max, true);
        }
        return stops;
    }

    static float[] buildToggleStops(boolean frontFace, float min, float max, float unit) {
        return boundStops(frontFace
            ? new float[]{min, unit, 2f * unit}
            : new float[]{min, unit, 2f * unit, 5f * unit}, min, max, false);
    }

    // ---------- zoom ----------

    /**
     * Queues the zoom instead of sending it at once: the slider moves on each
     * touch event, the camera takes a change at most once a frame, and
     * without the queue the calls pile up and the zoom lags the finger.
     */
    private void applyZoom(float value) {
        pendingZoom = value;
        scheduleZoomFlush();
    }

    private void scheduleZoomFlush() {
        if (zoomFlushScheduled) {
            return;
        }
        zoomFlushScheduled = true;
        postOnAnimation(zoomFlushRunnable);
    }

    private void discardPendingZoom() {
        removeCallbacks(zoomFlushRunnable);
        zoomFlushScheduled = false;
        pendingZoom = Float.NaN;
    }

    private void resetZoomThrottle() {
        discardPendingZoom();
        lastAppliedZoom = Float.NaN;
        lastZoomAppliedAt = 0L;
    }

    private void flushPendingZoom() {
        zoomFlushScheduled = false;
        final float value = pendingZoom;
        if (Float.isNaN(value) || session == null) {
            return;
        }
        final long now = SystemClock.uptimeMillis();
        if (!Float.isNaN(lastAppliedZoom)) {
            if (value == lastAppliedZoom) {
                return;
            }
            if (now - lastZoomAppliedAt < ZOOM_UPDATE_INTERVAL) {
                scheduleZoomFlush();
                return;
            }
        }
        lastAppliedZoom = value;
        lastZoomAppliedAt = now;
        session.setZoomRatio(value);
        if (cameraZoomChangeListener != null) {
            cameraZoomChangeListener.onCameraZoomChanged(value, true);
        }
    }

    private void onZoomStateChanged(ZoomState state) {
        if (session == null || state == null || cameraZoomChangeListener == null) {
            return;
        }
        cameraZoomChangeListener.onCameraZoomChanged(state.getZoomRatio(), false);
    }

    private void attachZoomObserver() {
        if (session == null) {
            return;
        }
        final LiveData<ZoomState> state = session.zoomStateData();
        if (state == null || zoomStateData == state) {
            return;
        }
        detachZoomObserver();
        zoomStateData = state;
        state.observeForever(zoomObserver);
    }

    private void detachZoomObserver() {
        if (zoomStateData != null) {
            zoomStateData.removeObserver(zoomObserver);
            zoomStateData = null;
        }
    }

    /** The zoom to return to: one, or the ultra-wide lens on the back camera by the setting. */
    public float getResetZoom() {
        final boolean front = session != null && session.isActiveCameraFrontface();
        return !MZGramConfig.cameraXWideLens || front ? defaultZoom : wideZoom;
    }

    /** Moves the slider to the camera's zoom without sending it back. */
    public void syncZoom(float ratio) {
        if (session != null && isFinite(ratio)) {
            setZoom(ratio);
        }
    }

    // ---------- binding to the session ----------

    public void bindSession(MZGramCameraX newSession) {
        final boolean morph = newSession != null && getVisibility() == VISIBLE && isLaidOut()
            && (switchingCamera || session == newSession);
        switchingCamera = false;
        if (morph) {
            prepareZoomConfigurationTransition();
        }
        resetBinding(!morph);
        if (newSession == null) {
            return;
        }
        session = newSession;
        animateNextConfiguration = morph;
        if (morph) {
            setEnabled(false);
            setExpanded(false, true);
        }
        tryBind();
    }

    public void unbindSession() {
        switchingCamera = false;
        resetBinding(true);
    }

    public void beginCameraSwitch() {
        if (session != null && getVisibility() == VISIBLE && isLaidOut()) {
            switchingCamera = true;
            prepareZoomConfigurationTransition();
            setEnabled(false);
            setExpanded(false, true);
        }
    }

    private void resetBinding(boolean hide) {
        setExternalZoomGestureActive(false);
        resetZoomThrottle();
        detachZoomObserver();
        setZoom(getZoom());
        session = null;
        defaultZoom = 1f;
        wideZoom = 1f;
        bindRetries = 0;
        removeCallbacks(bindRunnable);
        animateNextConfiguration = false;
        if (hide) {
            cancelZoomConfigurationTransition();
            setEnabled(true);
            setExpanded(false, false);
            hideImmediately();
        }
    }

    private void retryBinding() {
        if (session != null && bindRetries++ < MAX_BIND_RETRIES) {
            postDelayed(bindRunnable, BIND_RETRY_DELAY);
            return;
        }
        animateNextConfiguration = false;
        cancelZoomConfigurationTransition();
        setEnabled(true);
        hideImmediately();
    }

    private void tryBind() {
        if (session == null) {
            return;
        }
        if (!session.isReady()) {
            retryBinding();
            return;
        }
        final ZoomState state = session.zoomState();
        if (state == null) {
            retryBinding();
            return;
        }
        final float minRatio = state.getMinZoomRatio();
        final float maxRatio = state.getMaxZoomRatio();
        final boolean frontFace = session.isActiveCameraFrontface();

        if (!isFinite(minRatio) || !isFinite(maxRatio) || minRatio <= 0f || maxRatio <= minRatio) {
            animateNextConfiguration = false;
            cancelZoomConfigurationTransition();
            setEnabled(true);
            hideImmediately();
            return;
        }

        // The front camera: its minimum is the full sensor, not another lens,
        // and its usual view. It is labelled "1x", and "2x" counts from it.
        final float displayOneZoom = frontFace ? minRatio : 1f;
        defaultZoom = clamp(displayOneZoom, minRatio, maxRatio);
        wideZoom = minRatio;
        setDisplayNormalizationFactor(displayOneZoom);

        final float[] opticalToggles = frontFace ? null : MZGramLensStops.buildToggleStops(minRatio, maxRatio,
            MZGramLensStops.opticalZoomRatios(getContext(), session.getActiveCameraId(), minRatio));
        final float[] toggles = opticalToggles != null ? opticalToggles : buildToggleStops(frontFace, minRatio, maxRatio, displayOneZoom);
        final float[] ruler = opticalToggles != null ? MZGramLensStops.buildRulerStops(minRatio, maxRatio, opticalToggles) : buildRulerStops(minRatio, maxRatio, displayOneZoom);
        final float current = clamp(getResetZoom(), minRatio, maxRatio);

        final boolean morph = animateNextConfiguration;
        animateNextConfiguration = false;
        setZoomConfiguration(minRatio, maxRatio, toggles, ruler, current, morph);
        if (!morph) {
            setExpanded(false, false);
        }
        attachZoomObserver();
        session.setZoomRatio(current);
        if (cameraZoomChangeListener != null) {
            cameraZoomChangeListener.onCameraZoomChanged(current, true);
        }
        showAnimated();
    }

    // ---------- gestures from outside ----------

    public void beginPinchZoomGesture() {
        if (session != null) {
            discardPendingZoom();
            setZoom(getZoom());
        }
        setExternalZoomGestureActive(true);
    }

    public void endPinchZoomGesture() {
        setExternalZoomGestureActive(false);
    }

    // ---------- showing and the place on screen ----------

    private void applyAppearProgress() {
        setAlpha(openAlpha * appearProgress);
        final float scale = appearProgress * 0.1f + 0.9f;
        setScaleX(scale);
        setScaleY(scale);
    }

    private void setAppearProgress(float value) {
        if (appearProgress != value) {
            appearProgress = value;
            applyAppearProgress();
        }
    }

    private void cancelAppearAnimation() {
        final ValueAnimator animator = appearAnimator;
        if (animator != null) {
            appearAnimator = null;
            animator.cancel();
        }
    }

    private void hideImmediately() {
        cancelAppearAnimation();
        setAppearProgress(0f);
        setVisibility(GONE);
    }

    private void showAnimated() {
        setEnabled(true);
        if (!MZGramConfig.cameraXZoomSlider) {
            hideImmediately();
            return;
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        if (appearAnimator != null || appearProgress >= 1f) {
            return;
        }
        final ValueAnimator animator = ValueAnimator.ofFloat(appearProgress, 1f);
        appearAnimator = animator;
        animator.addUpdateListener(a -> setAppearProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (appearAnimator == animation) {
                    appearAnimator = null;
                    setAppearProgress(1f);
                }
            }
        });
        animator.setDuration(APPEAR_DURATION);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    private void applyPosition() {
        setTranslationY(baseTranslationY + textureViewSize / 2f + AndroidUtilities.dp(80f) - getMeasuredHeight() / 2f);
    }

    /** The vertical shift of the round video, the slider stays under it. */
    public void setBaseTranslationY(float value) {
        if (baseTranslationY != value) {
            baseTranslationY = value;
            applyPosition();
        }
    }

    public void setTextureViewSize(int size) {
        if (textureViewSize != size) {
            textureViewSize = size;
            applyPosition();
        }
    }

    public float getOpenAlpha() {
        return openAlpha;
    }

    public void setOpenAlpha(float value) {
        if (openAlpha != value) {
            openAlpha = value;
            setAlpha(value * appearProgress);
        }
    }

    public void setOnCameraZoomChangeListener(OnCameraZoomChangeListener listener) {
        cameraZoomChangeListener = listener;
    }

    public void setBlurBackground(BlurredBackgroundDrawable drawable) {
        if (blurBackground != drawable) {
            blurBackground = drawable;
            blurCornerRadius = -1f;
            invalidate();
        }
    }

    @Override
    public boolean drawPillBackground(Canvas canvas, RectF bounds, float radius) {
        if (blurBackground == null) {
            return false;
        }
        if (blurCornerRadius != radius) {
            blurCornerRadius = radius;
            blurBackground.setRadius(radius);
        }
        blurBackground.setBounds(Math.round(bounds.left), Math.round(bounds.top),
            Math.round(bounds.right), Math.round(bounds.bottom));
        blurBackground.draw(canvas);
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (session != null && (getVisibility() != VISIBLE || !isEnabled() || animateNextConfiguration)) {
            bindRetries = 0;
            tryBind();
        } else if (session != null) {
            attachZoomObserver();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(bindRunnable);
        discardPendingZoom();
        detachZoomObserver();
        if (appearAnimator != null) {
            cancelAppearAnimation();
            setAppearProgress(getVisibility() == VISIBLE ? 1f : 0f);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        applyPosition();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || getAlpha() < 0.5f) {
            return false;
        }
        return super.onTouchEvent(event);
    }
}
