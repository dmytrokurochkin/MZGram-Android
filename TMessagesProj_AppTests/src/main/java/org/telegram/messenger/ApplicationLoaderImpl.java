package org.telegram.messenger;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected boolean isAndroidTestEnv() {
        return true;
    }

    // As the app gives it; without it FileProvider authorities come out
    // as "null.provider" in tests.
    @Override
    protected String onGetApplicationId() {
        return getPackageName();
    }
}
