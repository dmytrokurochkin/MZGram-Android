package org.mzgram.core;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;
import org.mzgram.core.helpers.AppRestartHelper;
import org.mzgram.core.ui.BottomBuilder;
import org.mzgram.core.utils.AndroidUtil;

public class CoreXConfig {

    public static final int TITLE_TYPE_TEXT = 0;
    public static final int TITLE_TYPE_ICON = 1;
    public static final int TITLE_TYPE_MIX = 2;

    public static final int API_TYPE_DEFAULT = 0;
    public static final int API_TYPE_CUSTOM = 3;

    public static SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("corex_config", Context.MODE_PRIVATE);

    public static int customApi = preferences.getInt("custom_api", 0);
    public static int customAppId = preferences.getInt("custom_app_id", 0);
    public static String customAppHash = preferences.getString("custom_app_hash", "");

    public static int currentAppId() {
        return customApi == API_TYPE_CUSTOM ? customAppId : BuildConfig.APP_ID;
    }

    public static String currentAppHash() {
        return customApi == API_TYPE_CUSTOM ? customAppHash : BuildConfig.APP_HASH;
    }

    public static void saveCustomApi() {
        preferences.edit()
                .putInt("custom_api", customApi)
                .putInt("custom_app_id", customAppId)
                .putString("custom_app_hash", customAppHash)
                .apply();
    }

    public static String formatLang(String name) {
        if (name == null || name.isEmpty()) {
            return getString(R.string.Default);
        } else {
            Locale locale = Locale.forLanguageTag(name.replace('_', '-'));
            return locale.getDisplayName(LocaleController.getInstance().currentLocale);
        }
    }

    public static int getNotificationColor() {
        int color = 0;
        Configuration configuration = ApplicationLoader.applicationContext.getResources().getConfiguration();
        boolean isDark = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (isDark) {
            color = 0xffffffff;
        } else {
            if (Theme.getActiveTheme().hasAccentColors()) {
                color = Theme.getActiveTheme().getAccentColor(Theme.getActiveTheme().currentAccentId);
            }
            if (Theme.getActiveTheme().isDark() || color == 0) {
                color = Theme.getColor(Theme.key_actionBarDefault);
            }
            // too bright
            if (AndroidUtilities.computePerceivedBrightness(color) >= 0.721f) {
                color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader) | 0xff000000;
            }
        }
        return color;
    }

    public static void showCustomApiBottomSheet(BaseFragment fragment) {
        final Context context = fragment.getContext();
        if (context == null) return;

        AtomicInteger useApiType = new AtomicInteger(-1);
        BottomBuilder builder = new BottomBuilder(context);
        EditText[] inputs = new EditText[2];

        builder.addTitle(getString(R.string.CustomApi), true, getString(R.string.UseCustomApiNotice));

        builder.addRadioItem(getString(R.string.CustomApiNo), CoreXConfig.customApi != API_TYPE_CUSTOM, (cell) -> {
            useApiType.set(API_TYPE_DEFAULT);
            builder.doRadioCheck(cell);
            for (EditText input : inputs) {
                input.setVisibility(View.GONE);
            }
            return Unit.INSTANCE;
        });

        builder.addRadioItem(getString(R.string.CustomApiInput), CoreXConfig.customApi == API_TYPE_CUSTOM, (cell) -> {
            useApiType.set(API_TYPE_CUSTOM);
            builder.doRadioCheck(cell);
            for (EditText input : inputs) {
                input.setVisibility(View.VISIBLE);
            }
            return Unit.INSTANCE;
        });

        inputs[0] = builder.addEditText("App ID");
        inputs[0].setInputType(InputType.TYPE_CLASS_NUMBER);
        if (CoreXConfig.customAppId != 0) {
            inputs[0].setText(String.valueOf(CoreXConfig.customAppId));
        }

        inputs[1] = builder.addEditText("App Hash");
        if (!TextUtils.isEmpty(CoreXConfig.customAppHash)) {
            inputs[1].setText(CoreXConfig.customAppHash);
        }

        if (CoreXConfig.customApi != API_TYPE_CUSTOM) {
            for (EditText input : inputs) {
                input.setVisibility(View.GONE);
            }
        }

        builder.addCancelButton();
        builder.addButton(getString(R.string.Set), true, (it) -> {
            int targetType = useApiType.get();
            if (targetType == -1) {
                targetType = CoreXConfig.customApi == API_TYPE_CUSTOM ? API_TYPE_CUSTOM : API_TYPE_DEFAULT;
            }

            if (targetType == API_TYPE_CUSTOM) {
                String appIdStr = inputs[0].getText().toString().trim();
                String appHashStr = inputs[1].getText().toString().trim();

                boolean isAppIdEmpty = appIdStr.isEmpty();
                boolean isAppHashEmpty = appHashStr.isEmpty();

                if (isAppIdEmpty && isAppHashEmpty) {
                    AndroidUtil.setPushService(true);
                    PushListenerController.reconcilePushRegistration();
                    resetCustomApi();
                    saveCustomApiAndRestart(context);
                    return Unit.INSTANCE;
                }

                if (isAppIdEmpty) {
                    AndroidUtil.showInputError(inputs[0]);
                    return Unit.INSTANCE;
                }
                try {
                    if (Integer.parseInt(appIdStr) == 0) {
                        AndroidUtil.showInputError(inputs[0]);
                        return Unit.INSTANCE;
                    }
                } catch (NumberFormatException e) {
                    AndroidUtil.showInputError(inputs[0]);
                    FileLog.e(e);
                    return Unit.INSTANCE;
                }

                if (isAppHashEmpty || appHashStr.length() != 32) {
                    AndroidUtil.showInputError(inputs[1]);
                    return Unit.INSTANCE;
                }

                CoreXConfig.customApi = API_TYPE_CUSTOM;
                CoreXConfig.customAppId = Integer.parseInt(appIdStr);
                CoreXConfig.customAppHash = appHashStr;

                AndroidUtil.setPushService(false);
            } else {
                AndroidUtil.setPushService(true);
                resetCustomApi();
            }

            PushListenerController.reconcilePushRegistration();
            saveCustomApiAndRestart(context);
            return Unit.INSTANCE;
        });
        builder.show();
    }

    private static void resetCustomApi() {
        CoreXConfig.customApi = API_TYPE_DEFAULT;
        CoreXConfig.customAppId = 0;
        CoreXConfig.customAppHash = "";
    }

    private static void saveCustomApiAndRestart(Context context) {
        CoreXConfig.saveCustomApi();
        AlertDialog restart = new AlertDialog(context, 0);
        restart.setTitle(getString(R.string.MZGram));
        restart.setMessage(getString(R.string.RestartAppToTakeEffect));
        restart.setPositiveButton(getString(R.string.OK), (__, ___) ->
                AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
        restart.show();
    }

}
