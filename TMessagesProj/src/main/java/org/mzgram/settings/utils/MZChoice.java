package org.mzgram.settings.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;

import org.telegram.ui.Components.AlertsCreator;

/**
 * MZGram: a Material single-choice dialog for settings pickers.
 *
 * Every option has a radio button, and the current value is checked, so the person
 * sees what is set before choosing. A tap picks the option and closes the dialog.
 */
public final class MZChoice {

    private MZChoice() {
    }

    public static Dialog create(Activity activity, CharSequence title, CharSequence[] items, int selected,
                                DialogInterface.OnClickListener listener) {
        String[] options = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            options[i] = String.valueOf(items[i]);
        }
        return AlertsCreator.createSingleChoiceDialog(activity, options, title == null ? null : title.toString(),
                selected, listener);
    }
}
