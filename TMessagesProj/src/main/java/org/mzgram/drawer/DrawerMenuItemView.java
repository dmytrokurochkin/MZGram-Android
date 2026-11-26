package org.mzgram.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.mzgram.feed.FeedController;
import org.mzgram.utils.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Один пункт бокового меню: иконка 24dp слева, жирная подпись 15sp, бейдж непрочитанных справа.
 */
public class DrawerMenuItemView extends FrameLayout {

    private static final int COLOR_KEY_SELECTOR = Theme.key_listSelector;
    private static final int COLOR_KEY_ICON = Theme.key_windowBackgroundWhiteGrayIcon;
    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;

    private final ImageView iconView;
    private final TextView textView;
    private final DrawerUnreadBadge unreadBadge;

    private int layoutButtonId = Integer.MIN_VALUE;
    /** MZGram: the Material 3 drawer item, see {@link DrawerMaterial}. */
    private final boolean material;

    public DrawerMenuItemView(Context context) {
        super(context);
        setWillNotDraw(false);
        material = DrawerMaterial.enabled();
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(material ? DrawerMaterial.ITEM_HEIGHT_DP : 48.0f));
        if (material) {
            // MZGram: Material 3 drawer items are pills inset from the sheet's edges
            lp.leftMargin = lp.rightMargin = AndroidUtilities.dp(DrawerMaterial.ITEM_INSET_DP);
        }
        setLayoutParams(lp);
        setBackground(createSelectorDrawable());
        // MZGram зовёт UIUtil.applyScaleStateListAnimator(this, 16f, false, false, 2, 0.04f, 1.5f);
        // у нас перенесена короткая форма — масштаб 0.04 и tension 1.5 те же.
        UIUtil.applyScaleStateListAnimator(this, 0.04f, 1.5f);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setColorFilter(createIconColorFilter());
        addView(iconView, LayoutHelper.createFrame(24, 24.0f, Gravity.LEFT | Gravity.CENTER_VERTICAL, material ? 16.0f : 20.0f, 0.0f, 0.0f, 0.0f));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, material ? 14.0f : 15.0f);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL, material ? 52.0f : 68.0f, 0.0f, 16.0f, 0.0f));

        unreadBadge = new DrawerUnreadBadge();
    }

    public void setMenuItem(int layoutButtonId, int currentAccount, int iconRes, CharSequence text) {
        this.layoutButtonId = layoutButtonId;
        iconView.setImageResource(iconRes);
        textView.setText(text);
        updateUnreadCounter(currentAccount);
    }

    public void updateColors() {
        setBackground(createSelectorDrawable());
        iconView.setColorFilter(createIconColorFilter());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        invalidate();
    }

    public void updateUnreadCounter(int currentAccount) {
        unreadBadge.bind(resolveUnreadCounter(currentAccount), textView);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        unreadBadge.draw(this, canvas);
    }

    private int resolveUnreadCounter(int currentAccount) {
        if (layoutButtonId == MainMenuItem.ARCHIVE.getId()) {
            return MessagesStorage.getInstance(currentAccount).getArchiveUnreadCount();
        }
        if (layoutButtonId == MainMenuItem.FEED.getId()) {
            return FeedController.getInstance(currentAccount).getUnreadCount();
        }
        return 0;
    }

    private PorterDuffColorFilter createIconColorFilter() {
        return new PorterDuffColorFilter(material ? DrawerMaterial.onSurfaceVariant() : Theme.getColor(COLOR_KEY_ICON), PorterDuff.Mode.SRC_IN);
    }

    private Drawable createSelectorDrawable() {
        final int radius = material ? (int) (DrawerMaterial.ITEM_HEIGHT_DP / 2) : 12;
        return Theme.createRadSelectorDrawable(Theme.getColor(COLOR_KEY_SELECTOR), radius, radius);
    }
}
