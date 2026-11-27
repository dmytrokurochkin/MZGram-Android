package org.mzgram.appearance;

import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mzgram.ux.android.animator.BoolAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.MainTabsLayout;

import org.mzgram.core.helpers.MainTabsHelper;

public final class MainTabsUiHelper {

    private MainTabsUiHelper() {
    }

    public static boolean isMaterial3NavigationBar() {
        return AppearanceConfig.newNavigationBarStyle() && !MainTabsLayout.isBottomNavigationFloating();
    }

    public static boolean isIosNavigationBar() {
        return AppearanceConfig.iosNavigationBarStyle() && !isMaterial3NavigationBar();
    }

    private static int getWrapperSideInset() {
        return isIosNavigationBar() ? AndroidUtilities.dp(8) : 0;
    }

    public static int getTabsViewHeightDp() {
        return isMaterial3NavigationBar() ? 64 : MainTabsHelper.getMainTabsHeightWithMargins();
    }

    public static int getTabsViewHeight(int bottomInset) {
        return isMaterial3NavigationBar()
                ? AndroidUtilities.dp(64) + bottomInset
                : AndroidUtilities.dp(MainTabsHelper.getMainTabsHeightWithMargins());
    }

    public static int getTabsInnerPaddingVertical() {
        if (isMaterial3NavigationBar()) {
            return 0;
        }
        return AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin() + (isIosNavigationBar() ? 6 : 4));
    }

    public static int getTabsInnerPaddingHorizontal() {
        return isMaterial3NavigationBar() ? 0 : AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin() + 4);
    }

    public static int getBackgroundInset() {
        return isMaterial3NavigationBar() ? 0 : AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin() - 0.334f);
    }

    public static float getBackgroundRadius() {
        return isMaterial3NavigationBar() ? 0 : AndroidUtilities.dp(MainTabsHelper.getMainTabsHeight() / 2f);
    }

    public static int getTabsViewWidth() {
        return isMaterial3NavigationBar() || isIosNavigationBar() ? LayoutHelper.MATCH_PARENT : MainTabsHelper.getTabsViewWidth();
    }

    public static int getTabsFabOffsetDp() {
        return isMaterial3NavigationBar() ? 64 : MainTabsHelper.getMainTabsHeight() + MainTabsHelper.getMainTabsMargin();
    }

    public static void applyTabsLayoutStyle(MainTabsLayout layout, int legacyMaxWidthPx) {
        final int paddingH = getTabsInnerPaddingHorizontal();
        final int paddingV = getTabsInnerPaddingVertical();
        layout.setPadding(paddingH, paddingV, paddingH, paddingV);
        layout.setMaxWidth(isMaterial3NavigationBar() || isIosNavigationBar() ? 0 : legacyMaxWidthPx);
    }

    public static void applyTabsBottomInset(MainTabsLayout layout, View wrapper, int bottomInset, int leftInset, int rightInset) {
        applyTabsBottomInset(layout, bottomInset);
        final int side = getWrapperSideInset();
        wrapper.setPadding(leftInset + side, 0, rightInset + side, isMaterial3NavigationBar() ? 0 : bottomInset);
    }

    public static void applyTabsBottomInset(MainTabsLayout layout, int bottomInset) {
        final int height = getTabsViewHeight(bottomInset);
        final ViewGroup.LayoutParams lp = layout.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            layout.setLayoutParams(lp);
        }
        final int paddingBottom = getTabsInnerPaddingVertical() + (isMaterial3NavigationBar() ? bottomInset : 0);
        if (layout.getPaddingBottom() != paddingBottom) {
            layout.setPadding(layout.getPaddingLeft(), layout.getPaddingTop(), layout.getPaddingRight(), paddingBottom);
        }
    }

    public static BlurredBackgroundProviderBuilder applyBackgroundStroke(BlurredBackgroundProviderBuilder builder) {
        if (isMaterial3NavigationBar()) {
            return builder
                    .setStrokeColorTop(0, 0)
                    .setStrokeColorBottom(0, 0)
                    .setStrokeWidth(0, 0);
        }
        return builder
                .setStrokeColorTop(0x11000000, 0x06FFFFFF)
                .setStrokeColorBottom(0x20000000, 0x11FFFFFF)
                .setStrokeWidth(AndroidUtilities.dpf2(0.4f), AndroidUtilities.dpf2(0.4f));
    }

    public static void setBlurBounds(RectF rectF, View view, int bottomInset) {
        final int bottom;
        final int top;
        if (isMaterial3NavigationBar()) {
            bottom = view.getMeasuredHeight();
            top = bottom - AndroidUtilities.dp(64) - bottomInset;
        } else {
            bottom = view.getMeasuredHeight() - bottomInset - AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin());
            top = bottom - AndroidUtilities.dp(MainTabsHelper.getMainTabsHeight());
        }
        rectF.set(0, top, view.getMeasuredWidth(), bottom);
    }

    public static void setMainTabSelectedIndicatorBounds(RectF rectF, float width, int height) {
        final float w = Math.min(AndroidUtilities.dp(56), Math.max(0, width - AndroidUtilities.dp(4) * 2));
        final float h = Math.min(AndroidUtilities.dp(32), height);
        final float x = (width - w) / 2f;
        final float y = MainTabsHelper.isMainTabsHideTitleStyle()
                ? (height - h) / 2f
                : AndroidUtilities.dp(6);
        rectF.set(x, y, w + x, h + y);
    }

    /**
     * MZGram: a Material 3 bottom bar sits a tone above the page (surface container): 6% of the page's text colour
     * over the page, lighter in a dark theme and a little greyer in a light one. The glass target of the theme was
     * darker than the chat list, so the bar looked like a hole under it.
     */
    public static int getMaterial3BarColor(Theme.ResourcesProvider resourcesProvider) {
        final int page = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider) | 0xFF000000;
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider) | 0xFF000000;
        return androidx.core.graphics.ColorUtils.blendARGB(page, text, 0.06f);
    }

    public static int getMainTabSelectedIndicatorColor(int color, float factor) {
        // MZGram: 30% of the accent over the bar gives the secondary container tone of Material 3
        // (about #4A4458 on a dark bar, #E8DEF8 on a light one); 12.5% was barely visible.
        return Theme.multAlpha(color, factor * 0.3f);
    }

    public static float getMaterial3MainTabIconTopDp() {
        return 10.0f;
    }

    public static float getMaterial3MainTabAvatarTopDp() {
        return getMaterial3MainTabIconTopDp() + 1.0f;
    }

    public static float getMainTabCounterCenterY(boolean material3, int height) {
        if (!material3) {
            return AndroidUtilities.dpf2(10.0f);
        }
        return MainTabsHelper.isMainTabsHideTitleStyle()
                ? height / 2f - AndroidUtilities.dpf2(6.0f)
                : AndroidUtilities.dp(getMaterial3MainTabIconTopDp() + 6.0f);
    }

    public static float getSelectedBackgroundScaleX(boolean material3, float factor) {
        return AndroidUtilities.lerp(material3 ? 0.4f : 0.6f, 1.0f, factor);
    }

    public static float getSelectedBackgroundScaleY(boolean material3, float factor) {
        return material3 ? 1.0f : getSelectedBackgroundScaleX(false, factor);
    }

    public static void applyMaterial3MainTabStyle(TextView textView, BoolAnimator animator) {
        animator.setDuration(500L);
        animator.setInterpolator(CubicBezierInterpolator.Emphasized);
        textView.setIncludeFontPadding(false);
        textView.setLetterSpacing(0.04166667f);
        textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16.0f, 49, 0.0f, 42.0f, 0.0f, 0.0f));
    }

    public static void setMaterial3MainTabSelected(BoolAnimator selected, BoolAnimator background, boolean value, boolean animated) {
        selected.setValue(value, animated);
        background.setDuration(value ? 100L : 200L);
        background.setInterpolator(value ? CubicBezierInterpolator.Emphasized : CubicBezierInterpolator.EmphasizedAccelerate);
        background.setValue(value, animated);
    }

    public static Drawable createMainTabsScrimBackground(Theme.ResourcesProvider resourcesProvider, boolean circle) {
        final int color = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final ShapeDrawable drawable = circle
                ? Theme.createCircleDrawable(AndroidUtilities.dp(40), color)
                : Theme.createRoundRectDrawable(AndroidUtilities.dp(28), color);
        drawable.getPaint().setShadowLayer(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(1), Theme.multAlpha(0xFF000000, 0.15f));
        if (!isMaterial3NavigationBar()) {
            return drawable;
        }
        return new InsetDrawable(drawable, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
    }
}
