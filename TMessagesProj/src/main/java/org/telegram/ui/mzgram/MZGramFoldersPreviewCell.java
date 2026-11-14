// Ported from exteraless (GPL-3.0).

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;

/**
 * Live preview of chat folders using the genuine FilterTabsView component.
 */
@SuppressLint("ViewConstructor")
public class MZGramFoldersPreviewCell extends FrameLayout {

    private static final int TAB_ALL = 0;
    private static final int TAB_GROUPS = 1;
    private static final int TAB_BOTS = 2;
    private static final int TAB_CHANNELS = 3;

    private final Theme.ResourcesProvider resourcesProvider;
    private final FilterTabsView tabsView;
    private final BlurredBackgroundSourceColor backgroundSource;

    private boolean showAllTab = true;

    public MZGramFoldersPreviewCell(Context context) {
        this(context, null);
    }

    public MZGramFoldersPreviewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        setClipChildren(false);

        tabsView = new FilterTabsView(context, resourcesProvider);
        tabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
            }

            @Override
            public void onPageScrolled(float progress) {
            }

            @Override
            public void onSamePageSelected() {
            }

            @Override
            public int getTabCounter(int tabId) {
                return tabId == (showAllTab ? TAB_ALL : TAB_GROUPS) ? 3 : 0;
            }

            @Override
            public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                return false;
            }

            @Override
            public boolean isTabMenuVisible() {
                return false;
            }

            @Override
            public void onDeletePressed(int id) {
            }

            @Override
            public void onPageReorder(int fromId, int toId) {
            }

            @Override
            public boolean canPerformActions() {
                return false;
            }
        });

        backgroundSource = new BlurredBackgroundSourceColor();
        backgroundSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        BlurredBackgroundDrawable background = new BlurredBackgroundDrawableViewFactory(backgroundSource)
                .create(tabsView, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
        background.setRadius(dp(18));
        background.setPadding(dp(6.666f));
        tabsView.setPadding(0, dp(7), 0, dp(7));
        tabsView.setBlurredBackground(background);

        addView(tabsView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER, 21, 0, 21, 0));
        rebuildTabs(false);
    }

    public void setShowAllTab(boolean show, boolean animated) {
        if (showAllTab != show) {
            showAllTab = show;
            rebuildTabs(animated);
        }
    }

    private void rebuildTabs(boolean animated) {
        tabsView.removeTabs();
        tabsView.resetTabId();

        // Telegram's own text: default folder names in Telegram
        if (showAllTab) {
            tabsView.addTab(TAB_ALL, TAB_ALL, LocaleController.getString(R.string.FilterAllChats), false, true, false);
        }
        // Telegram's own text: default folder names in Telegram
        tabsView.addTab(TAB_GROUPS, TAB_GROUPS, LocaleController.getString(R.string.FilterGroups), false, false, false);
        // Telegram's own text: default folder names in Telegram
        tabsView.addTab(TAB_BOTS, TAB_BOTS, LocaleController.getString(R.string.FilterBots), false, false, false);
        // Telegram's own text: default folder names in Telegram
        tabsView.addTab(TAB_CHANNELS, TAB_CHANNELS, LocaleController.getString(R.string.FilterChannels), false, false, false);
        tabsView.finishAddingTabs(animated);
        tabsView.requestLayout();
    }

    public void updateAllChatsTabName(boolean animate) {
        rebuildTabs(animate);
    }

    public void updateTabTitle(boolean animate) {
        rebuildTabs(animate);
    }

    public void updateTabIcons(boolean animate) {
        rebuildTabs(animate);
    }

    public void updateTabCounter(boolean animate) {
        tabsView.checkTabsCounter();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (backgroundSource != null) {
            backgroundSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        }
        if (tabsView != null) {
            tabsView.updateColors();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(LocaleController.isRTL ? 0 : dp(21), getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? dp(21) : 0), getMeasuredHeight() - 1,
                Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(86), MeasureSpec.EXACTLY));
    }
}
