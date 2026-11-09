/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The pill bar of the chat list's search bar: one pill on screen, a
 * vertical swipe on it brings the next one (up) or the one before (down),
 * round the list. A tap runs the pill's action, a long press opens its
 * options.
 *
 * Gestures (conflict rule 1.3, "Open the archive by pulling down"): the bar
 * takes a move only when it is steeper than 25 degrees from the horizontal
 * and only then keeps its parents from taking the touch. A flatter move is
 * left to the parents (the swipe between folders), and a pull down that
 * starts on the chat list never reaches the bar, so the pull to the
 * archive (MZGramConfig.openArchiveOnPull) works as before.
 *
 * Idea from exteraless PillStackView (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram.pills;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

public class MZGramPillStackView extends FrameLayout {

    /** A move flatter than this (degrees from the horizontal) is left to the parents. */
    public static final double MIN_SWIPE_ANGLE = 25;

    private final ArrayList<MZGramPill> pills = new ArrayList<>();
    private final float touchSlop;

    private int current;
    private ValueAnimator animator;
    private float progress;
    private boolean up;
    private boolean swiping;
    private boolean leftToParents;
    private boolean maybeTap;
    private boolean held;
    private float startX;
    private float startY;
    private boolean onScreen = true;

    private final Runnable hold = () -> {
        if (maybeTap && !swiping && !pills.isEmpty()) {
            held = pills.get(current).onHold();
            if (held) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }
    };

    public MZGramPillStackView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
    }

    /** Whether a move of dx, dy is steep enough for the bar (rule 1.3). */
    public static boolean isSwipe(float dx, float dy) {
        return Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx))) > MIN_SWIPE_ANGLE;
    }

    public int count() {
        return pills.size();
    }

    public void addPill(MZGramPill pill) {
        pills.add(pill);
        addView(pill, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        boolean shown = pills.size() - 1 == current;
        pill.setVisibility(shown ? VISIBLE : GONE);
        pill.setSelectedPill(shown && onScreen);
    }

    public void clear() {
        stopAnimation();
        for (MZGramPill pill : pills) {
            pill.setSelectedPill(false);
        }
        pills.clear();
        removeAllViews();
        current = 0;
    }

    public void setCurrent(int index) {
        if (index < 0 || index >= pills.size() || index == current) {
            return;
        }
        MZGramPill previous = pills.get(current);
        previous.setVisibility(GONE);
        previous.setSelectedPill(false);
        current = index;
        MZGramPill next = pills.get(index);
        reset(next);
        next.setVisibility(VISIBLE);
        next.setSelectedPill(onScreen);
    }

    public void updateColors() {
        for (MZGramPill pill : pills) {
            pill.updateColors();
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (onScreen == isVisible) {
            return;
        }
        onScreen = isVisible;
        if (!pills.isEmpty()) {
            pills.get(current).setSelectedPill(isVisible);
        }
    }

    private int neighbour(boolean up) {
        int size = pills.size();
        return ((up ? current + 1 : current - 1) + size) % size;
    }

    private static void reset(View view) {
        view.setTranslationY(0);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    // ---- Gestures ----

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) {
            return false;
        }
        MZGramPill pill = pills.get(current);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopAnimation();
                pill = pills.get(current);
                startX = event.getRawX();
                startY = event.getRawY();
                swiping = false;
                leftToParents = false;
                maybeTap = true;
                held = false;
                pill.layout.setPressed(true);
                removeCallbacks(hold);
                postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (leftToParents) {
                    return false;
                }
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;
                if (!swiping && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    maybeTap = false;
                    removeCallbacks(hold);
                    pill.layout.setPressed(false);
                    if (pills.size() > 1 && isSwipe(dx, dy)) {
                        swiping = true;
                        startY = event.getRawY();
                        dy = 0;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else {
                        leftToParents = true;
                        return false;
                    }
                }
                if (swiping) {
                    follow(dy);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);
                pill.layout.setPressed(false);
                if (swiping) {
                    finish(event.getRawY() - startY);
                } else if (maybeTap && !held) {
                    pill.onTap();
                }
                maybeTap = false;
                swiping = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                pill.layout.setPressed(false);
                if (swiping) {
                    animateTo(0f);
                }
                maybeTap = false;
                swiping = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void follow(float dy) {
        int height = getHeight();
        if (height <= 0) {
            return;
        }
        up = dy < 0;
        progress = Math.min(1f, Math.abs(dy) / height);
        apply(progress);
    }

    private void finish(float dy) {
        int height = getHeight();
        if (height > 0 && Math.abs(dy) > height * 0.25f) {
            animateTo(1f);
        } else {
            animateTo(0f);
        }
    }

    private void apply(float value) {
        MZGramPill pill = pills.get(current);
        int nextIndex = neighbour(up);
        MZGramPill next = pills.get(nextIndex);
        for (int i = 0; i < pills.size(); i++) {
            if (i != current && i != nextIndex && pills.get(i).getVisibility() != GONE) {
                pills.get(i).setVisibility(GONE);
            }
        }
        if (next.getVisibility() != VISIBLE) {
            next.setVisibility(VISIBLE);
            next.updateColors();
            next.update(false);
        }
        float offset = getHeight() * value;
        pill.setTranslationY(up ? -offset : offset);
        pill.setAlpha(1f - value);
        pill.setScaleX(1f - 0.2f * value);
        pill.setScaleY(1f - 0.2f * value);
        float from = up ? getHeight() : -getHeight();
        next.setTranslationY(from * (1f - value));
        next.setAlpha(value);
        next.setScaleX(0.8f + 0.2f * value);
        next.setScaleY(0.8f + 0.2f * value);
    }

    private void animateTo(float target) {
        stopAnimation();
        final boolean switching = target >= 1f;
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(switching ? 250 : 200);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            apply(progress);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animator = null;
                if (!cancelled) {
                    settle(switching);
                }
            }
        });
        animator.start();
    }

    // Ends a swipe at once: on the next pill when switching, else back on the current one
    private void settle(boolean switching) {
        if (pills.isEmpty()) {
            return;
        }
        if (switching) {
            MZGramPill previous = pills.get(current);
            previous.setSelectedPill(false);
            current = neighbour(up);
            MZGramPills.setLastActive(pills.get(current).pillId());
        }
        for (int i = 0; i < pills.size(); i++) {
            MZGramPill pill = pills.get(i);
            reset(pill);
            pill.setVisibility(i == current ? VISIBLE : GONE);
        }
        pills.get(current).setSelectedPill(onScreen);
        progress = 0f;
    }

    private void stopAnimation() {
        ValueAnimator running = animator;
        if (running != null) {
            animator = null;
            // Ending runs onAnimationEnd: the swipe settles where it was going
            running.end();
        }
    }
}
