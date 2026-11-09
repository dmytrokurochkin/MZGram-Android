/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Pill: the size of Telegram's media cache on this device, counted the
 * way Telegram's Storage usage counts it (CacheControlActivity
 * calculateTotalSize, on its own background queue). Counted when the pill
 * comes on screen and every five minutes while it stays there. A tap
 * opens Storage usage, where the cache is cleared.
 *
 * Idea from exteraless CachePill (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.LaunchActivity;

@SuppressLint("ViewConstructor")
public class MZGramCachePill extends MZGramPill {

    private int generation;

    public MZGramCachePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setIcon(R.drawable.msg_clearcache);
        setText("...", false);
    }

    @Override
    public int pillId() {
        return MZGramPills.CACHE;
    }

    @Override
    public long refreshInterval() {
        return 5 * 60_000;
    }

    @Override
    public void update(boolean animated) {
        final int request = ++generation;
        CacheControlActivity.calculateTotalSize(size -> {
            if (request != generation || !isAttachedToWindow() || size == null) {
                return;
            }
            setText(AndroidUtilities.formatFileSize(size), true);
        });
    }

    @Override
    public void onTap() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            fragment.presentFragment(new CacheControlActivity());
        }
    }

    @Override
    protected void addOptions(ItemOptions options, BaseFragment fragment) {
        options.add(R.drawable.msg_clearcache, LocaleController.getString(R.string.StorageUsage), // Telegram's own text: "Storage Usage"
                () -> fragment.presentFragment(new CacheControlActivity()));
    }
}
