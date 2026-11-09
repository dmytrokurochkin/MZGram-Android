/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The style editor of the music player: the cover (shape, size, corners),
 * colors and background, the title, the order and look of the blocks.
 * It sits under the small live copy of the player in MZGramPlayerSheet.
 *
 * Port of exteraless PlayerStyleEditor (GPL-3.0), from exteraGram.
 * Changed for MZGram: the values go to MZGramPlayerStyle; no "Mini
 * player" tab (there is no floating mini player).
 */

package org.telegram.ui.mzgram.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class MZGramPlayerStyleEditor extends LinearLayout {

    public static final int TARGET_NONE = -1;
    public static final int TARGET_COVER = 0;
    public static final int TARGET_TITLE = 1;
    public static final int TARGET_PROGRESS = 2;
    public static final int TARGET_CONTROLS = 3;
    public static final int TARGET_ACTIONS = 4;

    public interface Listener {
        void onStyleChanged();
    }

    private interface Part {
        void bind(MZGramPlayerColors colors);

        void refresh();
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface BoolGetter {
        boolean get();
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private final Listener listener;
    private final ArrayList<Part> parts = new ArrayList<>();
    private MZGramPlayerColors colors;
    private final View sizeRow;
    private final View radiusRow;
    private final View spinRow;
    private final View swipeRow;
    private final View positionGroup;
    private final View backgroundGroup;
    private final ElementCard titleCard;
    private final ElementCard[] blockCard = new ElementCard[3];
    private final LinearLayout blockCards;
    private final View extendRow;
    private final ChipGroup coverChips;
    private final LinearLayout[] sections = new LinearLayout[2];
    private LinearLayout section;
    private static int selectedSection;

    public MZGramPlayerStyleEditor(Context context, MZGramPlayerColors colors, Listener listener) {
        super(context);
        this.colors = colors;
        this.listener = listener;
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(16));

        Segmented tabs = new Segmented(context, new String[]{
                getString(R.string.MZGramPlayerStyleCover), getString(R.string.MZGramPlayerStyleElements)
        }, () -> selectedSection, this::selectSection);
        addView(tabs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 16, 8, 16, 4));
        parts.add(tabs);
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new LinearLayout(context);
            sections[i].setOrientation(VERTICAL);
            addView(sections[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        section = sections[0];
        addSegmented(new String[]{
                getString(R.string.MZGramPlayerCoverCard), getString(R.string.MZGramPlayerCoverWide),
                getString(R.string.MZGramPlayerCoverFull), getString(R.string.MZGramPlayerCoverDisc)
        }, MZGramPlayerStyle::coverStyle, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_COVER_STYLE, value));
        sizeRow = addSlider(R.string.MZGramPlayerCoverSize, MZGramPlayerStyle.COVER_SIZE_MIN, MZGramPlayerStyle.COVER_SIZE_MAX, 5, "%",
                () -> Math.round(MZGramPlayerStyle.coverSize() * 100), value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_COVER_SIZE, value));
        radiusRow = addSlider(R.string.MZGramPlayerCoverRadius, 0, MZGramPlayerStyle.COVER_RADIUS_MAX, 2, "",
                MZGramPlayerStyle::coverRadiusDp, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_COVER_RADIUS, value));
        coverChips = addChips();
        spinRow = addChip(coverChips, R.string.MZGramPlayerDiscSpin,
                MZGramPlayerStyle::discSpin, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_DISC_SPIN, value));
        swipeRow = addChip(coverChips, R.string.MZGramPlayerCoverSwipe,
                MZGramPlayerStyle::coverSwipe, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_COVER_SWIPE, value));
        extendRow = addChip(coverChips, R.string.MZGramPlayerCoverExtend,
                MZGramPlayerStyle::coverExtend, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_COVER_EXTEND, value));
        addHeader(R.string.MZGramPlayerStyleColors);
        addSegmented(new String[]{getString(R.string.MZGramPlayerColorsCover), getString(R.string.MZGramPlayerColorsTheme)},
                () -> MZGramPlayerStyle.themeColors() ? 1 : 0, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_COLORS, value));
        backgroundGroup = addSegmented(new String[]{
                getString(R.string.MZGramPlayerBackgroundSolid), getString(R.string.MZGramPlayerBackgroundGradient),
                getString(R.string.MZGramPlayerBackgroundBlur)
        }, MZGramPlayerStyle::background, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_BACKGROUND, value));

        section = sections[1];
        addInfo(R.string.MZGramPlayerEditorTapHint);
        titleCard = new ElementCard(context, TARGET_TITLE, getString(R.string.MZGramPlayerStyleTitleSection), null, null, false);
        section.addView(titleCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 0));
        section = titleCard.body;
        addSegmented(new String[]{getString(R.string.MZGramPlayerAlignStart), getString(R.string.MZGramPlayerAlignCenter)},
                () -> MZGramPlayerStyle.titleCentered() ? 1 : 0, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_TITLE_ALIGN, value));
        addSegmented(new String[]{
                getString(R.string.MZGramPlayerSizeSmall), getString(R.string.MZGramPlayerSizeMedium), getString(R.string.MZGramPlayerSizeLarge)
        }, MZGramPlayerStyle::titleSize, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_TITLE_SIZE, value));
        positionGroup = addSegmented(new String[]{getString(R.string.MZGramPlayerTitleBelow), getString(R.string.MZGramPlayerTitleAboveCover)},
                () -> MZGramPlayerStyle.titleAboveSaved() ? 1 : 0, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_TITLE_ABOVE, value == 1));
        ChipGroup titleChips = addChips();
        addChip(titleChips, R.string.MZGramPlayerShowLike,
                MZGramPlayerStyle::showLike, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_SHOW_LIKE, value));
        addChip(titleChips, R.string.MZGramPlayerShowSource,
                MZGramPlayerStyle::showSource, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_SHOW_SOURCE, value));

        blockCards = new LinearLayout(context);
        blockCards.setOrientation(VERTICAL);
        sections[1].addView(blockCards, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 0));

        blockCard[MZGramPlayerStyle.BLOCK_PROGRESS] = new ElementCard(context, TARGET_PROGRESS,
                getString(R.string.MZGramPlayerBlockProgress), null, null, true);
        section = blockCard[MZGramPlayerStyle.BLOCK_PROGRESS].body;
        ChipGroup progressChips = addChips();
        addChip(progressChips, R.string.MZGramPlayerShowWave,
                MZGramPlayerStyle::wavy, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_WAVY, value));

        blockCard[MZGramPlayerStyle.BLOCK_CONTROLS] = new ElementCard(context, TARGET_CONTROLS,
                getString(R.string.MZGramPlayerBlockControls), null, null, true);
        section = blockCard[MZGramPlayerStyle.BLOCK_CONTROLS].body;
        addSegmented(new String[]{
                getString(R.string.MZGramPlayerControlsCompact), getString(R.string.MZGramPlayerControlsNormal),
                getString(R.string.MZGramPlayerControlsLarge)
        }, MZGramPlayerStyle::controlsSize, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_CONTROLS_SIZE, value));
        ChipGroup controlChips = addChips();
        addChip(controlChips, R.string.MZGramPlayerShowModes,
                MZGramPlayerStyle::showModes, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_SHOW_MODES, value));

        blockCard[MZGramPlayerStyle.BLOCK_ACTIONS] = new ElementCard(context, TARGET_ACTIONS,
                getString(R.string.MZGramPlayerBlockActions),
                MZGramPlayerStyle::showActions, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_SHOW_ACTIONS, value), true);
        section = blockCard[MZGramPlayerStyle.BLOCK_ACTIONS].body;
        addSegmented(new String[]{getString(R.string.MZGramPlayerActionsButtons), getString(R.string.MZGramPlayerActionsIsland)},
                () -> MZGramPlayerStyle.actionsIsland() ? 1 : 0, value -> MZGramPlayerStyle.set(MZGramPlayerStyle.KEY_ACTIONS_STYLE, value));
        layoutBlockCards();

        section = sections[1];
        addInfo(R.string.MZGramPlayerLayoutInfo);

        selectSection(selectedSection);
        refreshVisibility();
        setColors(colors);
    }

    // Opens the settings of the part of the player the user tapped
    public void focus(int target) {
        if (target == TARGET_COVER) {
            selectSection(0);
            return;
        }
        ElementCard card = target == TARGET_TITLE ? titleCard : target == TARGET_PROGRESS ? blockCard[MZGramPlayerStyle.BLOCK_PROGRESS]
                : target == TARGET_CONTROLS ? blockCard[MZGramPlayerStyle.BLOCK_CONTROLS]
                : target == TARGET_ACTIONS ? blockCard[MZGramPlayerStyle.BLOCK_ACTIONS] : null;
        if (card == null) {
            return;
        }
        if (selectedSection != 1) {
            selectSection(1);
        }
        expand(card, true);
        postDelayed(() -> scrollToCard(card), 260);
    }

    private void scrollToCard(View card) {
        if (!(getParent() instanceof ScrollView)) {
            return;
        }
        int y = 0;
        for (View view = card; view != null && view != this; view = view.getParent() instanceof View ? (View) view.getParent() : null) {
            y += view.getTop();
        }
        ((ScrollView) getParent()).smoothScrollTo(0, Math.max(0, y - dp(8)));
    }

    private void expand(ElementCard card, boolean animated) {
        if (animated && isLaidOut()) {
            TransitionManager.beginDelayedTransition(this, new AutoTransition().setDuration(220));
        }
        for (ElementCard item : new ElementCard[]{titleCard, blockCard[0], blockCard[1], blockCard[2]}) {
            if (item != null) {
                item.setExpanded(item == card);
                item.bindShape();
            }
        }
    }

    private static int blockOf(int target) {
        return target == TARGET_PROGRESS ? MZGramPlayerStyle.BLOCK_PROGRESS
                : target == TARGET_CONTROLS ? MZGramPlayerStyle.BLOCK_CONTROLS : MZGramPlayerStyle.BLOCK_ACTIONS;
    }

    private void moveBlock(int block, int delta) {
        int[] order = moved(MZGramPlayerStyle.order(), block, delta);
        if (order == null) {
            return;
        }
        MZGramPlayerStyle.setOrder(order);
        if (isLaidOut()) {
            TransitionManager.beginDelayedTransition(blockCards, new ChangeBounds().setDuration(220));
        }
        layoutBlockCards();
        changed();
    }

    // Public for the tests: the order with the block moved one step up
    // (delta -1) or down (delta 1), or null when it cannot move.
    public static int[] moved(int[] order, int block, int delta) {
        int index = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == block) {
                index = i;
            }
        }
        int target = index + delta;
        if (index < 0 || target < 0 || target >= order.length || delta == 0) {
            return null;
        }
        int[] result = order.clone();
        result[index] = order[target];
        result[target] = order[index];
        return result;
    }

    private void layoutBlockCards() {
        if (blockCards == null) {
            return;
        }
        int[] order = MZGramPlayerStyle.order();
        blockCards.removeAllViews();
        for (int i = 0; i < order.length; i++) {
            ElementCard card = blockCard[order[i]];
            blockCards.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, i == 0 ? 0 : 2, 0, 0));
            card.setPosition(i, order.length);
        }
    }

    private void selectSection(int index) {
        selectedSection = Math.max(0, Math.min(sections.length - 1, index));
        for (int i = 0; i < sections.length; i++) {
            sections[i].setVisibility(i == selectedSection ? VISIBLE : GONE);
        }
        if (getParent() instanceof ScrollView) {
            ((ScrollView) getParent()).scrollTo(0, 0);
        }
    }

    public void setColors(MZGramPlayerColors colors) {
        this.colors = colors;
        for (Part part : parts) {
            part.bind(colors);
        }
    }

    public void refresh() {
        for (Part part : parts) {
            part.refresh();
        }
        layoutBlockCards();
        refreshVisibility();
    }

    private void changed() {
        refreshVisibility();
        if (listener != null) {
            listener.onStyleChanged();
        }
    }

    private void refreshVisibility() {
        int style = MZGramPlayerStyle.coverStyle();
        boolean framed = style == MZGramPlayerStyle.COVER_CARD || style == MZGramPlayerStyle.COVER_DISC;
        setVisible(sizeRow, framed);
        setVisible(radiusRow, style == MZGramPlayerStyle.COVER_CARD);
        setVisible(spinRow, style == MZGramPlayerStyle.COVER_DISC);
        setVisible(swipeRow, framed);
        setVisible(extendRow, style == MZGramPlayerStyle.COVER_WIDE);
        setVisible(coverChips, style != MZGramPlayerStyle.COVER_FULL);
        setVisible(positionGroup, framed);
        setVisible(backgroundGroup, style != MZGramPlayerStyle.COVER_FULL);
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private void addHeader(int textRes) {
        TextView header = new TextView(getContext());
        header.setText(getString(textRes));
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        header.setTypeface(AndroidUtilities.bold());
        section.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 20, 24, 8));
        parts.add(new Part() {
            @Override
            public void bind(MZGramPlayerColors c) {
                header.setTextColor(c.primary);
            }

            @Override
            public void refresh() {
            }
        });
    }

    private void addInfo(int textRes) {
        TextView info = new TextView(getContext());
        info.setText(getString(textRes));
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        section.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 6, 24, 4));
        parts.add(new Part() {
            @Override
            public void bind(MZGramPlayerColors c) {
                info.setTextColor(c.onSurfaceVariant);
            }

            @Override
            public void refresh() {
            }
        });
    }

    private View addSegmented(String[] options, IntGetter getter, IntSetter setter) {
        Segmented segmented = new Segmented(getContext(), options, getter, value -> {
            setter.set(value);
            changed();
        });
        segmented.inset = section.getParent() instanceof ElementCard;
        section.addView(segmented, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 16, 4, 16, 4));
        parts.add(segmented);
        return segmented;
    }

    private View addSlider(int labelRes, int min, int max, int step, String suffix, IntGetter getter, IntSetter setter) {
        SliderRow row = new SliderRow(getContext(), getString(labelRes), min, max, step, suffix, getter, value -> {
            setter.set(value);
            changed();
        });
        section.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 0));
        parts.add(row);
        return row;
    }

    private ChipGroup addChips() {
        ChipGroup group = new ChipGroup(getContext());
        section.addView(group, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 10, 16, 6));
        return group;
    }

    private View addChip(ChipGroup group, int labelRes, BoolGetter getter, BoolSetter setter) {
        Chip chip = new Chip(getContext(), getString(labelRes), getter, value -> {
            setter.set(value);
            changed();
        });
        group.addView(chip);
        parts.add(chip);
        return chip;
    }

    private static class Segmented extends LinearLayout implements Part {

        private final TextView[] items;
        private final GradientDrawable[] backgrounds;
        private final IntGetter getter;
        private MZGramPlayerColors colors;
        boolean inset;

        Segmented(Context context, String[] options, IntGetter getter, IntSetter onSelect) {
            super(context);
            this.getter = getter;
            setOrientation(HORIZONTAL);
            items = new TextView[options.length];
            backgrounds = new GradientDrawable[options.length];
            for (int i = 0; i < options.length; i++) {
                final int index = i;
                TextView item = new TextView(context);
                item.setText(options[i]);
                item.setGravity(Gravity.CENTER);
                item.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                item.setTypeface(AndroidUtilities.bold());
                item.setSingleLine(true);
                item.setEllipsize(TextUtils.TruncateAt.END);
                item.setPadding(dp(6), 0, dp(6), 0);
                GradientDrawable bg = new GradientDrawable();
                float outer = dp(22);
                float inner = dp(6);
                float l = i == 0 ? outer : inner;
                float r = i == options.length - 1 ? outer : inner;
                bg.setCornerRadii(new float[]{l, l, r, r, r, r, l, l});
                backgrounds[i] = bg;
                item.setBackground(bg);
                item.setOnClickListener(v -> {
                    if (getter.get() != index) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        onSelect.set(index);
                        refresh();
                    }
                });
                items[i] = item;
                addView(item, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, i == 0 ? 0 : 2, 0, 0, 0));
            }
        }

        @Override
        public void bind(MZGramPlayerColors c) {
            colors = c;
            refresh();
        }

        @Override
        public void refresh() {
            if (colors == null) {
                return;
            }
            int selected = getter.get();
            for (int i = 0; i < items.length; i++) {
                boolean on = i == selected;
                backgrounds[i].setColor(on ? colors.secondaryContainer : inset ? colors.surfaceContainer : colors.surfaceHigh);
                items[i].setTextColor(on ? colors.onSecondaryContainer : colors.onSurfaceVariant);
                items[i].setSelected(on);
            }
        }
    }

    private static class SliderRow extends LinearLayout implements Part {

        private final TextView label;
        private final TextView valueView;
        private final MZGramWavySeekBar bar;
        private final int min;
        private final int max;
        private final int step;
        private final String suffix;
        private final IntGetter getter;

        SliderRow(Context context, String text, int min, int max, int step, String suffix, IntGetter getter, IntSetter onChange) {
            super(context);
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix;
            this.getter = getter;
            setOrientation(VERTICAL);
            LinearLayout top = new LinearLayout(context);
            top.setOrientation(HORIZONTAL);
            label = new TextView(context);
            label.setText(text);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            top.addView(label, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            valueView.setTypeface(AndroidUtilities.bold());
            valueView.setFontFeatureSettings("tnum");
            top.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
            bar = new MZGramWavySeekBar(context);
            bar.setMaxAmplitude(0);
            bar.setContentDescription(text);
            bar.setDelegate(new MZGramWavySeekBar.Delegate() {
                private int last = Integer.MIN_VALUE;

                @Override
                public void onSeekStart() {
                    last = getter.get();
                }

                @Override
                public void onSeekMove(float progress) {
                    apply(progress, false);
                }

                @Override
                public void onSeekEnd(float progress, boolean commit) {
                    apply(progress, true);
                }

                private void apply(float progress, boolean end) {
                    int value = snap(progress, min, max, step);
                    valueView.setText(format(value));
                    if (value != last) {
                        last = value;
                        bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        onChange.set(value);
                    }
                    if (end) {
                        bar.setProgress(fraction(value));
                    }
                }
            });
            addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));
            refresh();
        }

        private float fraction(int value) {
            return (value - min) / (float) Math.max(1, max - min);
        }

        private String format(int value) {
            return suffix.isEmpty() ? String.valueOf(value) : value + suffix;
        }

        @Override
        public void bind(MZGramPlayerColors c) {
            label.setTextColor(c.onSurface);
            valueView.setTextColor(c.primary);
            bar.setColors(c.primary, c.secondaryContainer);
        }

        @Override
        public void refresh() {
            int value = getter.get();
            valueView.setText(format(value));
            bar.setProgress(fraction(value));
        }
    }

    // Public for the tests: the value of a slider at the given part of its
    // length, rounded to the step and kept inside the range.
    public static int snap(float progress, int min, int max, int step) {
        float raw = min + (max - min) * progress;
        int value = Math.round(raw / step) * step;
        return Math.max(min, Math.min(max, value));
    }

    private static class ChipGroup extends ViewGroup {

        ChipGroup(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int gap = dp(8);
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
                int childWidth = child.getMeasuredWidth();
                if (x > 0 && x + childWidth > width) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                x += childWidth + gap;
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + rowHeight);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int gap = dp(8);
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                if (x > 0 && x + childWidth > width) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                int left = LocaleController.isRTL ? width - x - childWidth : x;
                child.layout(left, y, left + childWidth, y + childHeight);
                x += childWidth + gap;
                rowHeight = Math.max(rowHeight, childHeight);
            }
        }
    }

    private static class Chip extends TextView implements Part {

        private final BoolGetter getter;
        private final GradientDrawable background = new GradientDrawable();
        private final MZGramPlayerIcon check = MZGramPlayerIcon.stroke(MZGramPlayerIcon.CHECK, 18);
        private MZGramPlayerColors colors;

        Chip(Context context, String text, BoolGetter getter, BoolSetter onChange) {
            super(context);
            this.getter = getter;
            setText(text);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            setTypeface(AndroidUtilities.bold());
            setGravity(Gravity.CENTER_VERTICAL);
            setSingleLine(true);
            setEllipsize(TextUtils.TruncateAt.END);
            setCompoundDrawablePadding(dp(8));
            background.setCornerRadius(dp(8));
            setOnClickListener(v -> {
                boolean value = !getter.get();
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                onChange.set(value);
                refresh();
            });
        }

        @Override
        public void bind(MZGramPlayerColors c) {
            colors = c;
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(dp(8));
            mask.setColor(0xffffffff);
            setBackground(new RippleDrawable(ColorStateList.valueOf(c.ripple()), background, mask));
            refresh();
        }

        @Override
        public void refresh() {
            if (colors == null) {
                return;
            }
            boolean on = getter.get();
            setSelected(on);
            background.setColor(on ? colors.secondaryContainer : 0);
            background.setStroke(on ? 0 : dp(1), colors.outlineVariant);
            int textColor = on ? colors.onSecondaryContainer : colors.onSurfaceVariant;
            setTextColor(textColor);
            if (on) {
                check.setColor(textColor);
                setCompoundDrawablesRelativeWithIntrinsicBounds(check, null, null, null);
                setPaddingRelative(dp(8), 0, dp(16), 0);
            } else {
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
                setPaddingRelative(dp(16), 0, dp(16), 0);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.CheckBox");
            info.setCheckable(true);
            info.setChecked(getter.get());
        }
    }

    private class ElementCard extends LinearLayout implements Part {

        private final BoolGetter shownGetter;
        private final FrameLayout header;
        private final TextView title;
        private final MZGramMorphButton eye;
        private final MZGramMorphButton up;
        private final MZGramMorphButton down;
        private final MZGramPlayerIcon chevronDown = MZGramPlayerIcon.stroke(MZGramPlayerIcon.CHEVRON_DOWN, 18);
        private final MZGramPlayerIcon chevronUp = MZGramPlayerIcon.stroke(MZGramPlayerIcon.CHEVRON_UP, 18);
        private final GradientDrawable background = new GradientDrawable();
        final LinearLayout body;
        private MZGramPlayerColors colors;
        private boolean expanded;
        private int position;
        private int count = 1;

        // shownGetter and shownSetter: the "eye" button that hides the block, or null
        ElementCard(Context context, int target, String text, BoolGetter shownGetter, BoolSetter shownSetter, boolean movable) {
            super(context);
            this.shownGetter = shownGetter;
            setOrientation(VERTICAL);
            setBackground(background);
            header = new FrameLayout(context);
            header.setOnClickListener(v -> expand(expanded ? null : this, true));
            title = new TextView(context);
            title.setText(text);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setCompoundDrawablePadding(dp(6));
            int buttons = (movable ? 2 : 0) + (shownGetter != null ? 1 : 0);
            header.addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 0, 12 + 44 * buttons, 0));
            int right = 8;
            if (movable) {
                down = new MZGramMorphButton(context, MZGramPlayerIcon.stroke(MZGramPlayerIcon.ARROW_DOWN, 20));
                down.setRadius(dp(20), false);
                down.setContentDescription(getString(R.string.MZGramPlayerMoveDown));
                down.setOnClickListener(v -> moveBlock(blockOf(target), 1));
                header.addView(down, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, right, 0));
                right += 44;
                up = new MZGramMorphButton(context, MZGramPlayerIcon.stroke(MZGramPlayerIcon.ARROW_UP, 20));
                up.setRadius(dp(20), false);
                up.setContentDescription(getString(R.string.MZGramPlayerMoveUp));
                up.setOnClickListener(v -> moveBlock(blockOf(target), -1));
                header.addView(up, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, right, 0));
                right += 44;
            } else {
                up = null;
                down = null;
            }
            if (shownGetter != null) {
                eye = new MZGramMorphButton(context, MZGramPlayerIcon.stroke(MZGramPlayerIcon.EYE, 20));
                eye.setAltIcon(MZGramPlayerIcon.stroke(MZGramPlayerIcon.EYE_OFF, 20));
                eye.setRadius(dp(20), false);
                eye.setOnClickListener(v -> {
                    shownSetter.set(!shownGetter.get());
                    refresh();
                    changed();
                });
                header.addView(eye, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, right, 0));
            } else {
                eye = null;
            }
            addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
            body = new LinearLayout(context);
            body.setOrientation(VERTICAL);
            body.setPadding(0, 0, 0, dp(12));
            body.setVisibility(GONE);
            addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            parts.add(this);
            refresh();
        }

        void setExpanded(boolean value) {
            expanded = value;
            body.setVisibility(value ? VISIBLE : GONE);
            title.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, value ? chevronUp : chevronDown, null);
        }

        void setPosition(int position, int count) {
            this.position = position;
            this.count = count;
            if (up != null) {
                up.setEnabled(position > 0);
                up.setAlpha(position > 0 ? 1f : 0.38f);
                down.setEnabled(position < count - 1);
                down.setAlpha(position < count - 1 ? 1f : 0.38f);
            }
            bindShape();
        }

        void bindShape() {
            int topDp = position == 0 ? 20 : 6;
            int bottomDp = position == count - 1 ? 20 : 6;
            float top = dp(topDp);
            float bottom = dp(bottomDp);
            background.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
            if (colors != null) {
                header.setBackground(Theme.createRadSelectorDrawable(colors.ripple(), topDp, expanded ? 0 : bottomDp));
            }
        }

        @Override
        public void bind(MZGramPlayerColors c) {
            colors = c;
            background.setColor(c.surfaceHigh);
            title.setTextColor(c.onSurface);
            chevronDown.setColor(c.onSurfaceVariant);
            chevronUp.setColor(c.onSurfaceVariant);
            for (MZGramMorphButton button : new MZGramMorphButton[]{eye, up, down}) {
                if (button != null) {
                    button.setColors(c.surfaceContainer, c.onSurfaceVariant, c.surfaceContainer, c.onSurfaceVariant);
                }
            }
            bindShape();
            refresh();
        }

        @Override
        public void refresh() {
            boolean shown = shownGetter == null || shownGetter.get();
            title.setAlpha(shown ? 1f : 0.5f);
            if (eye != null) {
                eye.setAlt(!shown, isLaidOut());
                eye.setContentDescription(shown
                        ? getString(R.string.Hide) // Telegram's own text: the button hides the block
                        : getString(R.string.Show)); // Telegram's own text: the button shows the block
            }
            setExpanded(expanded);
            bindShape();
        }
    }
}
