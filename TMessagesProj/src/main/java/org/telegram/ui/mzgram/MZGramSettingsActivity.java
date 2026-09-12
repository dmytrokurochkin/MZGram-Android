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

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGram);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.MZGramGeneral)));
        items.add(UItem.asCheck(BUTTON_TEST_TOGGLE, getString(R.string.MZGramTestToggle)).setChecked(MZGramConfig.testToggle));
        items.add(UItem.asShadow(getString(R.string.MZGramTestToggleInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_TEST_TOGGLE) {
            MZGramConfig.toggleTestToggle();
            ((TextCheckCell) view).setChecked(MZGramConfig.testToggle);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
