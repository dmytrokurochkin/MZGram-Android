package org.mzgram.sdk.pillstack.core;

import java.util.ArrayList;

public final class PillStackConfig {
    private PillStackConfig() {
    }

    public static ArrayList<Integer> getActivePills() {
        org.mzgram.pillstack.PillStackConfig.loadConfig(false);
        return org.mzgram.pillstack.PillStackConfig.getActivePills();
    }

    public static ArrayList<Integer> getHiddenPills() {
        org.mzgram.pillstack.PillStackConfig.loadConfig(false);
        return org.mzgram.pillstack.PillStackConfig.getHiddenPills();
    }

    public static int getLastActivePillId() {
        org.mzgram.pillstack.PillStackConfig.loadConfig(false);
        return org.mzgram.pillstack.PillStackConfig.lastActivePillId.Int();
    }
}
