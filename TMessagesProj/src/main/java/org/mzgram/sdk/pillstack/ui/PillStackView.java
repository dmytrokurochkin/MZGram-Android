package org.mzgram.sdk.pillstack.ui;

import android.content.Context;
import org.mzgram.sdk.pillstack.ui.pills.BasePill;

public class PillStackView extends org.mzgram.pillstack.PillStackView {
    public PillStackView(Context context) {
        super(context);
    }

    public void addPill(BasePill pill) {
        super.addPill(pill);
    }
}
