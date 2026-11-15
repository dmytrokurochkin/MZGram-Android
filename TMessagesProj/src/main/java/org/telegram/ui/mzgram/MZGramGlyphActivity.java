// Ported from exteraless (GPL-3.0).
package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MZGramGlyphActivity extends UniversalFragment {

    private static final int BUTTON_ENABLE = 1;
    private static final int BUTTON_NEW_MESSAGE = 2;
    private static final int BUTTON_CALLS = 3;
    private static final int BUTTON_RECORDING = 4;
    private static final int BUTTON_SCREEN_OFF = 5;
    private static final int BUTTON_PREVIEW = 6;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramGlyphTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.MZGramGlyphTitle)));
        items.add(UItem.asCheck(BUTTON_ENABLE, getString(R.string.MZGramGlyphEnable)).setChecked(MZGramConfig.nothingGlyphEnabled));
        items.add(UItem.asCheck(BUTTON_NEW_MESSAGE, getString(R.string.MZGramGlyphNewMessage)).setChecked(MZGramConfig.glyphOnNewMessage));
        items.add(UItem.asCheck(BUTTON_CALLS, getString(R.string.MZGramGlyphCalls)).setChecked(MZGramConfig.glyphOnCall));
        items.add(UItem.asCheck(BUTTON_RECORDING, getString(R.string.MZGramGlyphRecording)).setChecked(MZGramConfig.glyphOnRecording));
        items.add(UItem.asCheck(BUTTON_SCREEN_OFF, getString(R.string.MZGramGlyphScreenOff)).setChecked(MZGramConfig.glyphScreenOffOnly));
        items.add(UItem.asButton(BUTTON_PREVIEW, R.drawable.mzgram_lamp_filled_24, getString(R.string.MZGramGlyphPreview)));
        items.add(UItem.asShadow(getString(R.string.MZGramGlyphInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        if (item.id == BUTTON_ENABLE) {
            MZGramConfig.toggleNothingGlyphEnabled();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.nothingGlyphEnabled);
            }
        } else if (item.id == BUTTON_NEW_MESSAGE) {
            MZGramConfig.toggleGlyphOnNewMessage();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.glyphOnNewMessage);
            }
        } else if (item.id == BUTTON_CALLS) {
            MZGramConfig.toggleGlyphOnCall();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.glyphOnCall);
            }
        } else if (item.id == BUTTON_RECORDING) {
            MZGramConfig.toggleGlyphOnRecording();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.glyphOnRecording);
            }
        } else if (item.id == BUTTON_SCREEN_OFF) {
            MZGramConfig.toggleGlyphScreenOffOnly();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MZGramConfig.glyphScreenOffOnly);
            }
        } else if (item.id == BUTTON_PREVIEW) {
            boolean isNothing = "Nothing".equalsIgnoreCase(Build.MANUFACTURER);
            if (!isNothing) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, getString(R.string.MZGramGlyphUnsupported))
                        .show();
            } else {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, getString(R.string.MZGramGlyphTitle))
                        .show();
            }
        }
    }
}
