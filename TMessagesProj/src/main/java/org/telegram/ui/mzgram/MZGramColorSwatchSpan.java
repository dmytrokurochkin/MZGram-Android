/*
 * Copyright (C) 2019-2022 qwq233 <qwq233@qwq2333.top>
 * https://github.com/qwq233/Nullgram
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this software.
 *  If not, see
 * <https://www.gnu.org/licenses/>
 */

package org.telegram.ui.mzgram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.Components.TextStyleSpan;

/**
 * MZGram: a small square of the colour after a hashtag that is a hex colour
 * (#RRGGBB or #AARRGGBB). Works only while the "hexColorSwatch" switch is on.
 * Ported from Nagram's SyntaxHighlight and ColorHighlightSpan, which come from
 * Nullgram (tw/nekomimi/nekogram/syntaxhighlight/, GPL-2.0-or-later, header above).
 */
public class MZGramColorSwatchSpan extends ReplacementSpan {

    private static final Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int radius = AndroidUtilities.dp(4);
    private static final int spacing = AndroidUtilities.dp(2);

    private final int size = AndroidUtilities.dp(SharedConfig.fontSize - 1);
    private final int color;
    private final TextStyleSpan.TextStyleRun style;

    public MZGramColorSwatchSpan(int color, TextStyleSpan.TextStyleRun run) {
        this.color = color;
        this.style = run;
    }

    /**
     * Adds the square after the hashtag text[start, end) when it is a hex colour.
     * Does nothing while the switch is off.
     */
    public static void apply(Spannable spannable, int start, int end, @Nullable TextStyleSpan.TextStyleRun run) {
        if (!MZGramConfig.hexColorSwatch || spannable == null) {
            return;
        }
        int length = end - start;
        if ((length != 7 && length != 9) || start < 0 || end > spannable.length() || spannable.charAt(start) != '#') {
            return;
        }
        try {
            int color = Color.parseColor(spannable.subSequence(start, end).toString());
            spannable.setSpan(new MZGramColorSwatchSpan(color, run), end - 1, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (IllegalArgumentException ignore) {
            // Not a colour, for example #flowers.
        }
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        return Math.round(paint.measureText(text, start, end) + spacing + size);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        TextPaint textPaint = (TextPaint) paint;
        int linkColor = textPaint.linkColor;
        int textColor = textPaint.getColor();
        if (style != null) {
            style.applyStyle(textPaint);
        }
        textPaint.setUnderlineText(linkColor == textColor);
        textPaint.setColor(linkColor);
        canvas.drawText(text, start, end, x, y, textPaint);

        colorPaint.setColor(color);
        float paddingTop = top + (bottom - top - size) / 2.0f;
        float paddingLeft = x + textPaint.measureText(text, start, end) + spacing;
        canvas.drawRoundRect(paddingLeft, paddingTop, paddingLeft + size, paddingTop + size, radius, radius, colorPaint);
    }
}
