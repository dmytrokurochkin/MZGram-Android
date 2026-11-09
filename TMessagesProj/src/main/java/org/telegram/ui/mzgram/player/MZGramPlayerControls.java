/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The row of the player controls: shuffle, previous, play, next, repeat.
 *
 * Port of exteraless ControlsRow (GPL-3.0), from exteraGram.
 */

package org.telegram.ui.mzgram.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.SystemClock;
import android.view.ViewGroup;

public class MZGramPlayerControls extends ViewGroup implements MZGramMorphButton.PressListener {

    public final MZGramMorphButton shuffle;
    public final MZGramMorphButton prev;
    public final MZGramMorphButton play;
    public final MZGramMorphButton next;
    public final MZGramMorphButton repeat;
    private final MZGramMorphButton[] buttons;
    private MZGramMorphButton pressed;
    private boolean playing;
    private boolean shuffleOn;
    private boolean repeatOn;
    private float scale = 1f;
    private float userScale = 1f;
    private boolean modesVisible = true;
    private long lastFrame;
    private boolean ticking;
    private final Runnable tick = this::tick;

    public MZGramPlayerControls(Context context) {
        super(context);
        shuffle = new MZGramMorphButton(context, MZGramPlayerIcon.stroke(MZGramPlayerIcon.SHUFFLE, 22));
        prev = new MZGramMorphButton(context, MZGramPlayerIcon.fill(MZGramPlayerIcon.PREV, 28));
        play = new MZGramMorphButton(context, MZGramPlayerIcon.fill(MZGramPlayerIcon.PLAY, 36));
        play.setAltIcon(MZGramPlayerIcon.fill(MZGramPlayerIcon.PAUSE, 36));
        next = new MZGramMorphButton(context, MZGramPlayerIcon.fill(MZGramPlayerIcon.NEXT, 28));
        repeat = new MZGramMorphButton(context, MZGramPlayerIcon.stroke(MZGramPlayerIcon.REPEAT, 22));
        shuffle.setAltIcon(MZGramPlayerIcon.stroke(MZGramPlayerIcon.REVERSE, 22));
        repeat.setAltIcon(MZGramPlayerIcon.stroke(MZGramPlayerIcon.ONCE, 22));
        buttons = new MZGramMorphButton[]{shuffle, prev, play, next, repeat};
        float[] widths = {48, 64, 88, 64, 48};
        float[] heights = {48, 64, 88, 64, 48};
        for (int i = 0; i < buttons.length; i++) {
            MZGramMorphButton b = buttons[i];
            b.width.snap(dp(widths[i]));
            b.baseHeight = dp(heights[i]);
            b.setPressListener(this);
            addView(b);
        }
        shuffle.setRadius(dp(24), false);
        repeat.setRadius(dp(24), false);
        prev.setRadius(dp(32), false);
        next.setRadius(dp(32), false);
        play.setRadius(dp(44), false);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    public void setStyle(float userScale, boolean modesVisible) {
        if (this.userScale == userScale && this.modesVisible == modesVisible) {
            return;
        }
        this.userScale = userScale;
        this.modesVisible = modesVisible;
        shuffle.setVisibility(modesVisible ? VISIBLE : GONE);
        repeat.setVisibility(modesVisible ? VISIBLE : GONE);
        requestLayout();
    }

    public void setPlaying(boolean value, boolean animated) {
        playing = value;
        play.setAlt(value, animated);
        updateTargets(animated);
    }

    public void setModes(boolean shuffleActive, boolean reversed, boolean repeatActive, boolean repeatOne, boolean playOnce, boolean animated) {
        shuffleOn = shuffleActive || reversed;
        repeatOn = repeatActive || playOnce;
        shuffle.setAlt(reversed, animated);
        shuffle.setActive(shuffleOn, animated);
        repeat.setAlt(playOnce, animated);
        repeat.setActive(repeatOn, animated);
        repeat.setBadge(repeatOne ? "1" : null, animated);
        updateTargets(animated);
    }

    @Override
    public void onPressChanged(MZGramMorphButton button, boolean isPressed) {
        if (isPressed) {
            pressed = button;
        } else if (pressed == button) {
            pressed = null;
        }
        updateTargets(true);
    }

    private void updateTargets(boolean animated) {
        float prevW = pressed == prev ? 76 : pressed == play ? 58 : 64;
        float nextW = pressed == next ? 76 : pressed == play ? 58 : 64;
        float playW = pressed == play ? 100 : (pressed == prev || pressed == next) ? 80 : 88;
        setWidth(prev, dp(prevW), animated);
        setWidth(next, dp(nextW), animated);
        setWidth(play, dp(playW), animated);
        prev.setRadius(dp(pressed == prev ? 18 : 32), animated);
        next.setRadius(dp(pressed == next ? 18 : 32), animated);
        play.setRadius(dp(pressed == play ? 20 : playing ? 28 : 44), animated);
        shuffle.setRadius(dp(shuffleOn ? 14 : 24), animated);
        repeat.setRadius(dp(repeatOn ? 14 : 24), animated);
        if (animated) {
            startTick();
        } else if (getWidth() > 0) {
            measureButtons();
            layoutButtons(getWidth(), getHeight());
        }
    }

    private void setWidth(MZGramMorphButton b, float w, boolean animated) {
        if (animated) {
            b.width.target = w;
        } else {
            b.width.snap(w);
        }
    }

    private void startTick() {
        if (!ticking) {
            ticking = true;
            lastFrame = 0;
            postOnAnimation(tick);
        }
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        float dt = lastFrame == 0 ? 0.016f : (now - lastFrame) / 1000f;
        lastFrame = now;
        boolean animating = false;
        for (MZGramMorphButton b : buttons) {
            animating |= b.width.step(dt);
        }
        if (getWidth() > 0) {
            measureButtons();
            layoutButtons(getWidth(), getHeight());
        } else {
            requestLayout();
        }
        if (animating) {
            postOnAnimation(tick);
        } else {
            ticking = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
        ticking = false;
        for (MZGramMorphButton b : buttons) {
            b.width.snap(b.width.target);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float base = modesVisible ? 312 : 232;
        scale = Math.min(userScale, (width - dp(16)) / (float) dp(base));
        int height = Math.round(dp(88) * scale);
        measureButtons();
        setMeasuredDimension(width, height);
    }

    private void measureButtons() {
        for (MZGramMorphButton b : buttons) {
            b.scale = scale;
            int w = Math.max(1, Math.round(b.width.value * scale));
            int h = Math.max(1, Math.round(b.baseHeight * scale));
            b.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutButtons(r - l, b - t);
    }

    private void layoutButtons(int width, int height) {
        float total = 0;
        int count = 0;
        for (MZGramMorphButton button : buttons) {
            if (button.getVisibility() == GONE) {
                continue;
            }
            total += button.getMeasuredWidth();
            count++;
        }
        float gap;
        float x;
        if (modesVisible) {
            gap = (width - total) / Math.max(1f, count - 1f);
            x = 0;
        } else {
            gap = Math.min(dp(28) * scale, (width - total) / Math.max(1f, count - 1f));
            x = (width - total - gap * (count - 1)) / 2f;
        }
        for (MZGramMorphButton button : buttons) {
            if (button.getVisibility() == GONE) {
                continue;
            }
            int w = button.getMeasuredWidth();
            int h = button.getMeasuredHeight();
            int left = Math.round(x);
            int top = (height - h) / 2;
            button.layout(left, top, left + w, top + h);
            x += w + gap;
        }
    }
}
