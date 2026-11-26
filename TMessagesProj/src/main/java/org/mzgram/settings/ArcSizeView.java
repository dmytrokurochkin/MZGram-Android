/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Message archive: its size as Telegram's Storage Usage shows it. The total in large
 * type, a bar split by part in the colours of Telegram's storage chart, and a legend
 * with a dot and the size of each part; "Counting…" until the count ends.
 */
public class ArcSizeView extends LinearLayout {

    private static final int[] COLORS = {
            Theme.key_statisticChartLine_blue,
            Theme.key_statisticChartLine_green,
    };
    private static final int[] NAMES = {
            R.string.MZArcSizeDatabase,
            R.string.MZArcSizeAttachments,
    };

    private final TextView totalView;
    private final TextView[] nameViews = new TextView[NAMES.length];
    private final TextView[] sizeViews = new TextView[NAMES.length];
    private final GradientDrawable[] dots = new GradientDrawable[NAMES.length];
    private final View bar;
    private long[] parts = new long[NAMES.length];
    private boolean counting = true;

    public ArcSizeView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(21), dp(4), dp(21), dp(10));

        totalView = new TextView(context);
        totalView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        totalView.setTypeface(AndroidUtilities.bold());
        addView(totalView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        bar = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path clip = new Path();
            private final RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                rect.set(0, 0, getWidth(), getHeight());
                clip.reset();
                clip.addRoundRect(rect, getHeight() / 2f, getHeight() / 2f, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                canvas.drawRect(rect, paint);
                long total = 0;
                for (long part : parts) {
                    total += part;
                }
                if (!counting && total > 0) {
                    float x = 0;
                    for (int i = 0; i < parts.length; i++) {
                        float width = getWidth() * (parts[i] / (float) total);
                        if (parts[i] > 0) {
                            width = Math.max(width, dp(3));
                        }
                        paint.setColor(Theme.getColor(COLORS[i]));
                        canvas.drawRect(x, 0, Math.min(x + width, getWidth()), getHeight(), paint);
                        x += width;
                    }
                }
                canvas.restore();
            }
        };
        addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8, 0, 10, 0, 8));

        for (int i = 0; i < NAMES.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(context);
            dots[i] = new GradientDrawable();
            dots[i].setShape(GradientDrawable.OVAL);
            dot.setBackground(dots[i]);
            row.addView(dot, LayoutHelper.createLinear(10, 10, 0, 0, 12, 0));
            nameViews[i] = new TextView(context);
            nameViews[i].setText(getString(NAMES[i]));
            nameViews[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            nameViews[i].setSingleLine(true);
            row.addView(nameViews[i], LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            sizeViews[i] = new TextView(context);
            sizeViews[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            row.addView(sizeViews[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));
        }
        updateColors();
        setCounting();
    }

    public void updateColors() {
        totalView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        for (int i = 0; i < NAMES.length; i++) {
            dots[i].setColor(Theme.getColor(COLORS[i]));
            nameViews[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            sizeViews[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        }
        bar.invalidate();
    }

    public void setCounting() {
        counting = true;
        totalView.setText(getString(R.string.MZArcSizeCounting));
        for (TextView size : sizeViews) {
            size.setText("");
        }
        bar.invalidate();
    }

    /** The database with the messages, and the Saved Attachments folder. */
    public void setSizes(long database, long attachments) {
        counting = false;
        parts = new long[]{Math.max(0, database), Math.max(0, attachments)};
        totalView.setText(AndroidUtilities.formatFileSize(parts[0] + parts[1]));
        for (int i = 0; i < sizeViews.length; i++) {
            sizeViews[i].setText(AndroidUtilities.formatFileSize(parts[i]));
        }
        bar.invalidate();
    }
}
