package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.SeekBarView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;

/**
 * MZGram: default delay and quick presets for the schedule-message dialog.
 * Works only while the "scheduleSlider" switch is on. The slider and the
 * minutes-to-step scale are ported from Nagram's ScheduleTimeHelper
 * (GPL-3.0, tw/nekomimi/nekogram/helpers/ScheduleTimeHelper.java); the quick
 * chips and the settings storage are MZGram's own.
 */
public final class MZGramSchedulePresets {

    private static final int STEP_COUNT = 30;
    private static final int LAST_MINUTE_STEP = 11;
    private static final int LAST_HOUR_STEP = 22;
    /** The "send when online" marker the schedule dialog receives instead of a date. */
    private static final long SEND_WHEN_ONLINE_DATE = 0x7FFFFFFEL;
    /** Hour of the "tomorrow morning" preset. */
    private static final int MORNING_HOUR = 9;

    private MZGramSchedulePresets() {
    }

    /** True when the dialog opens without a chosen date, so the default delay applies. */
    public static boolean shouldUseDefault(long currentDate) {
        return MZGramConfig.scheduleSlider && (currentDate <= 0 || currentDate == SEND_WHEN_ONLINE_DATE);
    }

    /**
     * Adds the delay slider and the quick chips under the pickers and sets the
     * pickers to "now + default delay". Does nothing when the switch is off or
     * the dialog edits an already chosen date (explicit choice always wins).
     *
     * @param currentDate        the date the dialog received (seconds), before any conversion
     * @param textColor          text colour of the dialog
     * @param backgroundColor    background colour of the dialog
     * @param onPickersChanged   re-checks the chosen time (enables or disables the button)
     */
    public static void attach(Context context, LinearLayout container, Theme.ResourcesProvider resourcesProvider,
                              long currentDate, int textColor, int backgroundColor,
                              NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker,
                              Runnable onPickersChanged) {
        if (!shouldUseDefault(currentDate)) {
            return;
        }
        final int startStep = stepForMinutes(MZGramConfig.scheduleDefaultMinutes);
        setPickers(targetFromNow(minutesForStep(startStep)), dayPicker, hourPicker, minutePicker);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        container.addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0, 8));

        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 24, 22, 0, 22, 0));

        final int accentColor = Theme.getColor(Theme.key_player_progress, resourcesProvider);

        final TextView title = new TextView(context);
        title.setText(getString(R.string.MZGramScheduleDelay));
        title.setTextColor(accentColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        final TextView value = new TextView(context);
        value.setText(format(minutesForStep(startStep)));
        value.setTextColor(accentColor);
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(value, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        final SeekBarView seekBar = new SeekBarView(context, resourcesProvider);
        seekBar.setReportChanges(true);
        seekBar.setSeparatorsCount(STEP_COUNT);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int step = Math.round(progress * (STEP_COUNT - 1));
                int minutes = minutesForStep(step);
                value.setText(format(minutes));
                if (MZGramConfig.scheduleDefaultMinutes != minutes) {
                    MZGramConfig.setScheduleDefaultMinutes(minutes);
                }
                setPickers(targetFromNow(minutes), dayPicker, hourPicker, minutePicker);
                onPickersChanged.run();
                if (stop) {
                    seekBar.setProgress(progressForStep(step), true);
                }
            }

            @Override
            public CharSequence getContentDescription() {
                return title.getText() + ", " + value.getText();
            }

            @Override
            public int getStepsCount() {
                return STEP_COUNT - 1;
            }
        });
        layout.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 13, 0, 13, 0));
        final float startProgress = progressForStep(startStep);
        AndroidUtilities.doOnLayout(seekBar, () -> seekBar.setProgress(startProgress));

        // Quick chips: in 15 minutes, in 1 hour, tomorrow morning. They only move the
        // pickers; the stored default delay is changed by the slider alone.
        final LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        layout.addView(chips, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 6, 16, 0));
        final int chipBackground = Theme.blendOver(backgroundColor, Theme.multAlpha(textColor, 0.075f));
        final int chipSelector = Theme.blendOver(chipBackground, Theme.multAlpha(textColor, 0.1f));
        addChip(context, chips, getString(R.string.MZGramScheduleIn15), textColor, chipBackground, chipSelector, () -> {
            setPickers(targetFromNow(15), dayPicker, hourPicker, minutePicker);
            onPickersChanged.run();
        });
        addChip(context, chips, getString(R.string.MZGramScheduleIn1Hour), textColor, chipBackground, chipSelector, () -> {
            setPickers(targetFromNow(60), dayPicker, hourPicker, minutePicker);
            onPickersChanged.run();
        });
        addChip(context, chips, getString(R.string.MZGramScheduleTomorrowMorning), textColor, chipBackground, chipSelector, () -> {
            setPickers(tomorrowMorning(), dayPicker, hourPicker, minutePicker);
            onPickersChanged.run();
        });
    }

    private static void addChip(Context context, LinearLayout row, String text, int textColor, int background, int selector, Runnable action) {
        final TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(textColor);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dp(8), 0, dp(8), 0);
        chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), background, selector));
        chip.setOnClickListener(v -> action.run());
        row.addView(chip, LayoutHelper.createLinear(0, 28, 1f, 0, 0, 6, 0));
    }

    /** Puts the three pickers on the given moment (milliseconds); ignores moments before today. */
    static void setPickers(long targetMillis, NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker) {
        final Calendar target = Calendar.getInstance();
        target.setTimeInMillis(targetMillis);
        final int days = (int) ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate(),
            Instant.ofEpochMilli(targetMillis).atZone(ZoneId.systemDefault()).toLocalDate());
        if (days < 0 || days > 365) {
            return;
        }
        minutePicker.setValue(target.get(Calendar.MINUTE));
        hourPicker.setValue(target.get(Calendar.HOUR_OF_DAY));
        dayPicker.setValue(days);
    }

    /** Now plus the given minutes, rounded up to a whole minute. */
    static long targetFromNow(int minutes) {
        long time = System.currentTimeMillis() + (long) minutes * 60 * 1000L;
        return ((time + 59999L) / 60000L) * 60000L;
    }

    /** 09:00 of the next calendar day. */
    static long tomorrowMorning() {
        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, MORNING_HOUR);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** Steps 0..11 are 5..60 minutes, 12..22 are 2..12 hours, 23..29 are 1..7 days. */
    static int minutesForStep(int step) {
        step = Utilities.clamp(step, STEP_COUNT - 1, 0);
        if (step <= LAST_MINUTE_STEP) {
            return (step + 1) * 5;
        } else if (step <= LAST_HOUR_STEP) {
            return (step - 10) * 60;
        }
        return (step - LAST_HOUR_STEP) * 24 * 60;
    }

    /** The step whose delay is closest to the given minutes. */
    static int stepForMinutes(int minutes) {
        int bestStep = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < STEP_COUNT; i++) {
            int diff = Math.abs(minutesForStep(i) - minutes);
            if (diff < bestDiff) {
                bestStep = i;
                bestDiff = diff;
            }
        }
        return bestStep;
    }

    private static float progressForStep(int step) {
        return step / (float) (STEP_COUNT - 1);
    }

    private static String format(int minutes) {
        if (minutes < 60 || minutes % 60 != 0) {
            return LocaleController.formatPluralString("Minutes", minutes);
        } else if (minutes < 24 * 60 || minutes % (24 * 60) != 0) {
            return LocaleController.formatPluralString("Hours", minutes / 60);
        }
        return LocaleController.formatPluralString("Days", minutes / (24 * 60));
    }
}
