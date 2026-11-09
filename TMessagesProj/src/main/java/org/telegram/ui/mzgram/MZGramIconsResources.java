/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The point where icon packs replace Telegram's icons (appearance.icon-packs):
 * LaunchActivity.getResources() hands out this wrapper while a pack or a
 * built-in set is on, and every drawable asked for through it goes past
 * MZGramIconPacks first. While nothing is on, LaunchActivity's own
 * Resources are handed out as before, untouched.
 *
 * Only drawables loaded through the activity are replaced. Icons that
 * Telegram draws once at start through the application (some chat icons in
 * Theme) change after the app is started again.
 *
 * Idea from Nagram X IconsResources and exteraless IconPackManager
 * (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramIconPacks;

@SuppressLint("UseCompatLoadingForDrawables")
public final class MZGramIconsResources extends Resources implements MZGramIconPacks.Source {

    private static Resources lastBase;
    private static MZGramIconsResources lastWrapper;

    @SuppressWarnings("deprecation")
    private MZGramIconsResources(Resources base) {
        super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }

    /** base itself while no pack is on, otherwise a wrapper that follows base's configuration. */
    public static Resources wrap(Resources base) {
        if (base == null || !MZGramConfig.iconPacks) {
            return base;
        }
        try {
            if (!MZGramIconPacks.isActive()) {
                return base;
            }
            synchronized (MZGramIconsResources.class) {
                MZGramIconsResources wrapper = lastWrapper;
                if (wrapper == null || lastBase != base || !sameAs(wrapper, base)) {
                    // A new wrapper on a configuration change (rotation, size, night mode):
                    // the activity handles these itself and its own Resources change in place
                    wrapper = new MZGramIconsResources(base);
                    lastBase = base;
                    lastWrapper = wrapper;
                }
                return wrapper;
            }
        } catch (Throwable e) {
            FileLog.e("MZGramIconsResources.wrap", e);
            return base;
        }
    }

    private static boolean sameAs(Resources wrapper, Resources base) {
        if (wrapper.getAssets() != base.getAssets() || !wrapper.getConfiguration().equals(base.getConfiguration())) {
            return false;
        }
        DisplayMetrics a = wrapper.getDisplayMetrics();
        DisplayMetrics b = base.getDisplayMetrics();
        return a.widthPixels == b.widthPixels && a.heightPixels == b.heightPixels && a.densityDpi == b.densityDpi
                && a.density == b.density && a.scaledDensity == b.scaledDensity;
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        return getDrawableForDensity(id, 0, null);
    }

    @Override
    public Drawable getDrawable(int id, Theme theme) throws NotFoundException {
        return getDrawableForDensity(id, 0, theme);
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return getDrawableForDensity(id, density, null);
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density, Theme theme) {
        Drawable replaced = null;
        try {
            replaced = MZGramIconPacks.drawableFor(this, id, density, theme);
        } catch (Throwable e) {
            FileLog.e("MZGramIconsResources.getDrawable", e);
        }
        return replaced != null ? replaced : super.getDrawableForDensity(id, density, theme);
    }

    // Resources.getDrawableForDensity(int, int, Theme) does the loading itself;
    // the other getDrawable methods of Resources call back into this class.
    @Override
    public Drawable original(int resId, int density, Theme theme) {
        return super.getDrawableForDensity(resId, density, theme);
    }

    @Override
    public Resources resources() {
        return this;
    }
}
