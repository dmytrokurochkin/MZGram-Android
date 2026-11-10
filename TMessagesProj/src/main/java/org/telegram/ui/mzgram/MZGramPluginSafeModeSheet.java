/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Bottom sheet shown when MZGram enters Safe Mode for plugins.
 * Offers to disable all plugins or exit Safe Mode.
 *
 * Based on exteraless SafeModeBottomSheet (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.plugins.MZGramPluginEngine;
import org.telegram.messenger.mzgram.plugins.MZGramPluginWatchdog;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class MZGramPluginSafeModeSheet extends BottomSheet {

    public MZGramPluginSafeModeSheet(Activity activity, Theme.ResourcesProvider resourcesProvider) {
        super(activity, false, resourcesProvider);
        fixNavigationBar();

        FrameLayout container = new FrameLayout(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        container.addView(content);

        TextView title = new TextView(activity);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(R.string.MZGramPluginSafeMode));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 24, 24, 24, 0));

        TextView info = new TextView(activity);
        info.setGravity(Gravity.CENTER);
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        info.setText(LocaleController.getString(R.string.MZGramPluginSafeModeSummary));
        content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 24, 12, 24, 0));

        ButtonWithCounterView disableAllButton = new ButtonWithCounterView(activity, true, resourcesProvider);
        disableAllButton.setText(LocaleController.getString(R.string.MZGramPluginSafeModeDisableAll), false);
        disableAllButton.setOnClickListener(v -> {
            dismiss();
            for (MZGramPlugins.Plugin plugin : MZGramPlugins.list()) {
                if (plugin.enabled) {
                    MZGramPlugins.setEnabled(plugin.meta.id, false);
                }
            }
            MZGramPluginWatchdog.setSafeMode(false);
        });
        content.addView(disableAllButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48,
                16, 20, 16, 8));

        ButtonWithCounterView exitButton = new ButtonWithCounterView(activity, false, resourcesProvider);
        exitButton.setText(LocaleController.getString(R.string.MZGramPluginSafeModeExit), false);
        exitButton.setOnClickListener(v -> {
            dismiss();
            MZGramPluginWatchdog.setSafeMode(false);
            if (MZGramConfig.pluginsRun) {
                MZGramPluginEngine.onResume();
            }
        });
        content.addView(exitButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48,
                16, 0, 16, 16));

        setCustomView(container);
    }
}
