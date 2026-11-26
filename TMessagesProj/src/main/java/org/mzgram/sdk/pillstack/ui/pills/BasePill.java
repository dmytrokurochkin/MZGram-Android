package org.mzgram.sdk.pillstack.ui.pills;

import android.content.Context;
import org.telegram.ui.ActionBar.Theme;

public abstract class BasePill extends org.mzgram.pillstack.pills.BasePill {
    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }
}
