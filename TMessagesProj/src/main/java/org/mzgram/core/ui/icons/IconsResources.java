package org.mzgram.core.ui.icons;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.annotation.Nullable;

import org.mzgram.icons.IconPackManager;

import org.mzgram.ext.ExtConfig;

@SuppressLint("UseCompatLoadingForDrawables")
public class IconsResources extends Resources {

    public static final int ICON_REPLACE_SOLAR = 1;
    private int _iconsType = -1;
    private final LruCache<Long, Drawable.ConstantState> constantStates = new LruCache<>(300);

    public IconsResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        return getDrawableForDensity(id, 0, null);
    }

    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        return getDrawableForDensity(id, 0, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        Drawable fromPack = IconPackManager.getInstance().getDrawable(this, id, density, theme);
        if (fromPack != null) {
            return fromPack;
        }
        return getOriginalDrawableForDensity(id, density, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return getDrawableForDensity(id, density, null);
    }

    @Nullable
    public Drawable getOriginalDrawableForDensity(int id, int density, @Nullable Theme theme) {
        int converted = getConversion(id);
        long key = ((long) converted << 32) | (density & 0xffffffffL);
        Drawable.ConstantState state = constantStates.get(key);
        if (state != null) {
            return state.newDrawable(this).mutate();
        }
        Drawable drawable = super.getDrawableForDensity(converted, density, theme);
        Drawable.ConstantState loaded = drawable != null ? drawable.getConstantState() : null;
        if (loaded == null || loaded.canApplyTheme()) {
            return drawable;
        }
        constantStates.put(key, loaded);
        return loaded.newDrawable(this).mutate();
    }

    private int getConversion(int icon) {
        return getConversion(icon, -1);
    }

    private int getConversion(int icon, int forcedIconsType) {
        if (_iconsType == -1) {
            _iconsType = ExtConfig.INSTANCE.getIconReplacements().Int();
        }

        int consideredIconsType = forcedIconsType == -1 ? _iconsType : forcedIconsType;

        if (consideredIconsType == ICON_REPLACE_SOLAR) {
            return SolarIcons.Companion.getConversion(icon);
        }

        return icon;
    }
}
