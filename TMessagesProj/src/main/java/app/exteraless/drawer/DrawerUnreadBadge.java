package app.exteraless.drawer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Счётчик непрочитанных на пункте бокового меню.
 * exteraGram: {@code com/exteragram/messenger/drawer/DrawerUnreadBadge.java} (79 строк).
 *
 * Рисуется поверх пункта, а подпись пункта получает увеличенный правый отступ,
 * чтобы длинное имя не залезло под бейдж.
 */
final class DrawerUnreadBadge {

    private final RectF rect = new RectF();

    private int badgeWidth;
    private int counter;
    private String text;
    private int textWidth;
    private boolean visible;
    private int defaultTextPaddingEnd = Integer.MIN_VALUE;
    /** MZGram: the Material 3 badge label, the count as trailing text of the item. */
    private boolean material;
    private TextPaint materialPaint;

    void bind(int counter, TextView textView) {
        this.counter = counter;
        update(textView);
    }

    /** Высота 23dp, радиус 11.5dp, отступ сверху 12.5dp. */
    void draw(View view, Canvas canvas) {
        if (!visible) {
            return;
        }
        if (material) {
            materialPaint.setColor(DrawerMaterial.onSurfaceVariant());
            final Paint.FontMetrics fm = materialPaint.getFontMetrics();
            final float baseline = view.getMeasuredHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(text, view.getMeasuredWidth() - AndroidUtilities.dp(24.0f) - textWidth, baseline, materialPaint);
            return;
        }
        final float top = AndroidUtilities.dp(12.5f);
        final float left = view.getMeasuredWidth() - AndroidUtilities.dp(16.5f) - badgeWidth;
        rect.set(left, top, left + badgeWidth, top + AndroidUtilities.dp(23.0f));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(11.5f), AndroidUtilities.dp(11.5f), Theme.dialogs_countGrayPaint);
        canvas.drawText(text, rect.left + ((rect.width() - textWidth) / 2.0f),
                top + AndroidUtilities.dp(16.0f), Theme.dialogs_countTextPaint);
    }

    void update(TextView textView) {
        rememberDefaultTextPadding(textView);
        visible = false;
        text = null;
        textWidth = 0;
        badgeWidth = 0;
        if (counter <= 0) {
            restoreTextPadding(textView);
            return;
        }
        visible = true;
        material = DrawerMaterial.enabled();
        if (material) {
            if (materialPaint == null) {
                materialPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                materialPaint.setTypeface(AndroidUtilities.bold());
            }
            materialPaint.setTextSize(AndroidUtilities.dp(14.0f));
            text = counter > 999 ? "999+" : Integer.toString(counter);
            textWidth = (int) Math.ceil(materialPaint.measureText(text));
            badgeWidth = textWidth;
            applyTextPadding(textView, defaultTextPaddingEnd + textWidth + AndroidUtilities.dp(20.0f));
            return;
        }
        text = Integer.toString(counter);
        textWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
        badgeWidth = Math.max(AndroidUtilities.dp(10.0f), textWidth) + AndroidUtilities.dp(14.0f);
        applyTextPadding(textView, defaultTextPaddingEnd + badgeWidth + AndroidUtilities.dp(12.0f));
    }

    private void rememberDefaultTextPadding(TextView textView) {
        if (defaultTextPaddingEnd != Integer.MIN_VALUE) {
            return;
        }
        defaultTextPaddingEnd = textView.getPaddingEnd();
    }

    private void restoreTextPadding(TextView textView) {
        applyTextPadding(textView, defaultTextPaddingEnd == Integer.MIN_VALUE ? 0 : defaultTextPaddingEnd);
    }

    private void applyTextPadding(TextView textView, int paddingEnd) {
        if (textView.getPaddingEnd() == paddingEnd) {
            return;
        }
        textView.setPaddingRelative(textView.getPaddingStart(), textView.getPaddingTop(),
                paddingEnd, textView.getPaddingBottom());
    }
}
