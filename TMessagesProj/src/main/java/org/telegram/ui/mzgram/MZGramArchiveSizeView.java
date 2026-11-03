package org.telegram.ui.mzgram;

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
import org.telegram.messenger.mzgram.MZGramArchiveSize;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Settings > MZGram > Archive: the archive's size as Telegram's Storage
 * Usage shows it. The total in large type, a bar split by kind in the
 * colours of Telegram's storage chart, and a legend with a dot and the size
 * of each kind; "Counting…" until the count ends.
 */
public class MZGramArchiveSizeView extends LinearLayout {

    public static final int HEIGHT_DP = 222;
    public static final int[] COLORS = {
            Theme.key_statisticChartLine_blue,
            Theme.key_statisticChartLine_green,
            Theme.key_statisticChartLine_orange,
            Theme.key_statisticChartLine_purple,
    };
    private static final int[] NAMES = {
            R.string.MZGramArchiveSizeDatabase,
            R.string.MZGramArchiveSizeMedia,
            R.string.MZGramArchiveSizeAttachments,
            R.string.MZGramArchiveSizeOther,
    };

    private final TextView totalView;
    private final TextView[] sizeViews = new TextView[NAMES.length];
    private final View bar;
    private long[] parts = new long[NAMES.length];
    private boolean counting;

    public MZGramArchiveSizeView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(21), dp(14), dp(21), dp(10));
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        TextView title = new TextView(context);
        title.setText(getString(R.string.MZGramArchiveSize));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        totalView = new TextView(context);
        totalView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        totalView.setTypeface(AndroidUtilities.bold());
        totalView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(totalView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

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
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Theme.getColor(COLORS[i]));
            dot.setBackground(circle);
            row.addView(dot, LayoutHelper.createLinear(10, 10, 0, 0, 12, 0));
            TextView name = new TextView(context);
            name.setText(getString(NAMES[i]));
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            name.setSingleLine(true);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            TextView size = new TextView(context);
            size.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            size.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            row.addView(size, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            sizeViews[i] = size;
            addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));
        }
        setCounting();
    }

    public void setCounting() {
        counting = true;
        totalView.setText(getString(R.string.MZGramArchiveSizeCounting));
        for (TextView size : sizeViews) {
            size.setText("");
        }
        bar.invalidate();
    }

    public void setSizes(MZGramArchiveSize.Sizes sizes) {
        counting = false;
        parts = sizes.parts();
        totalView.setText(AndroidUtilities.formatFileSize(sizes.total()));
        for (int i = 0; i < sizeViews.length; i++) {
            sizeViews[i].setText(AndroidUtilities.formatFileSize(parts[i]));
        }
        bar.invalidate();
    }

    // The tests
    public boolean countingForTest() {
        return counting;
    }

    public String totalForTest() {
        return totalView.getText().toString();
    }

    public String sizeForTest(int index) {
        return sizeViews[index].getText().toString();
    }
}
