/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram: every MZGram feature has its switch here.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MZGramSettingsActivity extends UniversalFragment {

    // Row id of the entry in Settings; clear of the ids SettingsActivity uses.
    public static final int SETTINGS_ROW_ID = 1000;

    private static final int BUTTON_TEST_TOGGLE = 1;
    private static final int BUTTON_DISABLE_NUMBER_ROUNDING = 2;
    private static final int BUTTON_FORMAT_TIME_WITH_SECONDS = 3;
    private static final int BUTTON_ASK_BEFORE_CALL = 4;
    private static final int BUTTON_HIDE_STORIES = 5;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGram);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.MZGramGeneral)));
        items.add(UItem.asCheck(BUTTON_TEST_TOGGLE, getString(R.string.MZGramTestToggle)).setChecked(MZGramConfig.testToggle));
        items.add(UItem.asShadow(getString(R.string.MZGramTestToggleInfo)));
        items.add(UItem.asCheck(BUTTON_ASK_BEFORE_CALL, getString(R.string.MZGramAskBeforeCall)).setChecked(MZGramConfig.askBeforeCall));
        items.add(UItem.asShadow(getString(R.string.MZGramAskBeforeCallInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramAppearance)));
        items.add(UItem.asCheck(BUTTON_DISABLE_NUMBER_ROUNDING, getString(R.string.MZGramDisableNumberRounding)).setChecked(MZGramConfig.disableNumberRounding));
        items.add(UItem.asShadow(getString(R.string.MZGramDisableNumberRoundingInfo)));
        items.add(UItem.asCheck(BUTTON_FORMAT_TIME_WITH_SECONDS, getString(R.string.MZGramFormatTimeWithSeconds)).setChecked(MZGramConfig.formatTimeWithSeconds));
        items.add(UItem.asShadow(getString(R.string.MZGramFormatTimeWithSecondsInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_STORIES, getString(R.string.MZGramHideStories)).setChecked(MZGramConfig.hideStories));
        items.add(UItem.asShadow(getString(R.string.MZGramHideStoriesInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_TEST_TOGGLE) {
            MZGramConfig.toggleTestToggle();
            ((TextCheckCell) view).setChecked(MZGramConfig.testToggle);
        } else if (item.id == BUTTON_DISABLE_NUMBER_ROUNDING) {
            MZGramConfig.toggleDisableNumberRounding();
            ((TextCheckCell) view).setChecked(MZGramConfig.disableNumberRounding);
        } else if (item.id == BUTTON_FORMAT_TIME_WITH_SECONDS) {
            MZGramConfig.toggleFormatTimeWithSeconds();
            ((TextCheckCell) view).setChecked(MZGramConfig.formatTimeWithSeconds);
        } else if (item.id == BUTTON_ASK_BEFORE_CALL) {
            MZGramConfig.toggleAskBeforeCall();
            ((TextCheckCell) view).setChecked(MZGramConfig.askBeforeCall);
        } else if (item.id == BUTTON_HIDE_STORIES) {
            MZGramConfig.toggleHideStories();
            ((TextCheckCell) view).setChecked(MZGramConfig.hideStories);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
