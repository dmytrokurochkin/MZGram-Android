/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Pill: ghost mode (MZGramConfig.ghostMode). Shows whether it is on; a tap
 * turns it on or off, the options lead to the ghost mode settings. The
 * switch can change elsewhere too (the quick settings tile, the stories
 * offer), so the pill reads it again every two seconds while on screen.
 *
 * Idea from exteraless GhostPill (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramGhostMode;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.mzgram.MZGramSettingsActivity;

@SuppressLint("ViewConstructor")
public class MZGramGhostPill extends MZGramPill {

    private boolean on;

    public MZGramGhostPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setIcon(R.drawable.ghost);
        on = MZGramGhostMode.isEnabled();
        setText(text(), false);
    }

    @Override
    public int pillId() {
        return MZGramPills.GHOST;
    }

    @Override
    public long refreshInterval() {
        return 2000;
    }

    private String text() {
        return LocaleController.getString(on ? R.string.MZGramPillGhostOn : R.string.MZGramPillGhostOff);
    }

    @Override
    protected int accentColor() {
        return on ? Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) : super.accentColor();
    }

    @Override
    public void update(boolean animated) {
        boolean now = MZGramGhostMode.isEnabled();
        if (now != on) {
            on = now;
            updateColors();
        }
        setText(text(), animated);
    }

    @Override
    public void onTap() {
        MZGramConfig.toggleGhostMode();
        update(true);
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            // createSimpleBulletin(int, ...) takes an animation from res/raw, so the icon goes as a drawable
            BulletinFactory.of(fragment).createSimpleBulletin(androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.ghost),
                    LocaleController.getString(on ? R.string.MZGramPillGhostTurnedOn : R.string.MZGramPillGhostTurnedOff)).show();
        }
    }

    @Override
    protected void addOptions(ItemOptions options, BaseFragment fragment) {
        options.add(R.drawable.ghost, LocaleController.getString(R.string.MZGramGhostMode),
                () -> fragment.presentFragment(new MZGramSettingsActivity(MZGramSettingsActivity.SECTION_GHOST_MODE)));
    }
}
