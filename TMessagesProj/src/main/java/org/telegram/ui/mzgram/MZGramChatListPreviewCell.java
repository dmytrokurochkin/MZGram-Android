// Ported from exteraless (GPL-3.0).

package org.telegram.ui.mzgram;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Preview of the chat list header with action bar, emoji status, and centered title toggle.
 */
@SuppressLint("ViewConstructor")
public class MZGramChatListPreviewCell extends FrameLayout {

    private final ActionBar actionBar;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    private Drawable premiumStar;

    private boolean needDivider = true;

    public MZGramChatListPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(null, AndroidUtilities.dp(26));
        statusDrawable.center = true;

        actionBar = new ActionBar(context);
        actionBar.setOccupyStatusBar(false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        actionBar.setBackground(new MZGramPreviewBackgroundDrawable());
        actionBar.setSupportsHolidayImage(true);
        addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 21, 21, 21, 21));

        updateStatus(false);
        refreshTitlePosition(false);
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public void setNeedDivider(boolean needDivider) {
        if (this.needDivider != needDivider) {
            this.needDivider = needDivider;
            invalidate();
        }
    }

    public void updateTitle(boolean animated) {
        updateStatus(animated);
    }

    public void updateStatus(boolean animated) {
        final int account = UserConfig.selectedAccount;
        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();

        Drawable rightDrawable = null;
        if (user != null) {
            Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
            if (emojiStatusId != null) {
                statusDrawable.set(emojiStatusId, animated);
                statusDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));
                rightDrawable = statusDrawable;
            } else if (MessagesController.getInstance(account).isPremiumUser(user)) {
                if (premiumStar == null) {
                    Drawable star = getContext().getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
                    premiumStar = new AnimatedEmojiDrawable.WrapSizeDrawable(star, AndroidUtilities.dp(18), AndroidUtilities.dp(18)) {
                        @Override
                        public void draw(@NonNull Canvas canvas) {
                            canvas.save();
                            canvas.translate(AndroidUtilities.dp(-2), AndroidUtilities.dp(1));
                            super.draw(canvas);
                            canvas.restore();
                        }
                    };
                }
                premiumStar.setColorFilter(new PorterDuffColorFilter(
                        Theme.getColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
                statusDrawable.set(premiumStar, animated);
                rightDrawable = statusDrawable;
            }
        }

        CharSequence title = MZGramTitleText.text(account);
        if (title == null) {
            // Telegram's own text: default title in chat list
            title = LocaleController.getString(R.string.FilterChats);
        }
        if (animated) {
            actionBar.setTitleAnimated(title, true, 250);
        } else {
            actionBar.setTitle(title, rightDrawable);
        }
    }

    public void updateCentered(boolean animated) {
        refreshTitlePosition(animated);
    }

    private void refreshTitlePosition(boolean animated) {
        final int gravity = MZGramConfig.centerTitle
                ? Gravity.CENTER : (Gravity.LEFT | Gravity.CENTER_VERTICAL);

        final ArrayList<SimpleTextView> views = new ArrayList<>();
        final SimpleTextView title = actionBar.getTitleTextView();
        final SimpleTextView title2 = actionBar.getTitleTextView2();

        if (!animated) {
            if (title != null) title.setGravity(gravity);
            if (title2 != null) title2.setGravity(gravity);
            actionBar.requestLayout();
            return;
        }

        if (title != null && title.getVisibility() == VISIBLE) views.add(title);
        if (title2 != null && title2.getVisibility() == VISIBLE) views.add(title2);

        final ArrayList<Float> fromX = new ArrayList<>();
        final ArrayList<Float> fromY = new ArrayList<>();
        for (int i = 0; i < views.size(); i++) {
            SimpleTextView view = views.get(i);
            view.animate().cancel();
            fromX.add(view.getTextStartX() + view.getTranslationX());
            fromY.add(view.getTextStartY() + view.getTranslationY());
        }

        if (title != null) title.setGravity(gravity);
        if (title2 != null) title2.setGravity(gravity);
        actionBar.requestLayout();

        if (views.isEmpty()) {
            return;
        }
        final ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                for (int i = 0; i < views.size(); i++) {
                    SimpleTextView view = views.get(i);
                    AnimatorSet set = new AnimatorSet();
                    set.playTogether(
                            ObjectAnimator.ofFloat(view, (Property<View, Float>) View.TRANSLATION_X, fromX.get(i) - view.getTextStartX(), 0f),
                            ObjectAnimator.ofFloat(view, (Property<View, Float>) View.TRANSLATION_Y, fromY.get(i) - view.getTextStartY(), 0f)
                    );
                    set.setDuration(300);
                    set.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    set.start();
                }
                return true;
            }
        };
        getViewTreeObserver().addOnPreDrawListener(listener);
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                getViewTreeObserver().removeOnPreDrawListener(listener);
                removeOnAttachStateChangeListener(this);
            }
        });
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (actionBar != null) {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
            statusDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));
            actionBar.invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        statusDrawable.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        statusDrawable.detach();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!needDivider) {
            return;
        }
        canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1,
                Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), getMeasuredHeight());
    }
}
