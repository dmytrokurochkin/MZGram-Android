/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Notifications and Sounds > UnifiedPush: whether notifications come
 * through a UnifiedPush distributor, which one, and the gateway.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramUnifiedPush;
import org.telegram.messenger.mzgram.MZGramUnifiedPushRules;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

public class MZGramUnifiedPushActivity extends UniversalFragment {

    private static final int BUTTON_ENABLED = 1;
    private static final int BUTTON_DISTRIBUTOR = 2;
    private static final int BUTTON_GATEWAY_ENABLED = 3;
    private static final int BUTTON_GATEWAY = 4;
    private static final int BUTTON_GATEWAY_RESET = 5;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramUnifiedPush);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean active = MZGramUnifiedPush.isActive();
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_ENABLED, getString(R.string.MZGramUseUnifiedPush)).setChecked(active));
        items.add(UItem.asShadow(getString(R.string.MZGramUnifiedPushInfo)));
        if (active) {
            String distributor = MZGramUnifiedPush.appName(MZGramUnifiedPush.distributor());
            items.add(UItem.asButton(BUTTON_DISTRIBUTOR, getString(R.string.MZGramUnifiedPushDistributor), distributor != null ? distributor : getString(R.string.MZGramUnifiedPushNotChosen)));
            items.add(UItem.asShadow(statusText()));
        }
        items.add(UItem.asCheck(BUTTON_GATEWAY_ENABLED, getString(R.string.MZGramUnifiedPushGatewayEnabled)).setChecked(MZGramConfig.unifiedPushGatewayEnabled));
        items.add(UItem.asButton(BUTTON_GATEWAY, getString(R.string.MZGramUnifiedPushGateway), MZGramUnifiedPushRules.gateway(true, MZGramConfig.unifiedPushGateway)));
        items.add(UItem.asButton(BUTTON_GATEWAY_RESET, getString(R.string.MZGramUnifiedPushGatewayReset)));
        items.add(UItem.asShadow(getString(R.string.MZGramUnifiedPushGatewayInfo)));
    }

    private String statusText() {
        switch (MZGramUnifiedPush.status()) {
            case NO_DISTRIBUTOR: return getString(R.string.MZGramUnifiedPushStatusNone);
            case CHOOSE_DISTRIBUTOR: return getString(R.string.MZGramUnifiedPushStatusChoose);
            case WAITING: return getString(R.string.MZGramUnifiedPushStatusWaiting);
            case REGISTERED: return getString(R.string.MZGramUnifiedPushStatusRegistered);
            default: return null;
        }
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    // The status changes when the distributor answers, a moment later.
    private void updateSoon() {
        update();
        AndroidUtilities.runOnUIThread(this::update, 3000);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_ENABLED) {
            MZGramConfig.setUseUnifiedPush(!MZGramUnifiedPush.isActive());
            MZGramUnifiedPush.applyChoice();
            ((TextCheckCell) view).setChecked(MZGramUnifiedPush.isActive());
            if (MZGramUnifiedPush.isActive() && MZGramUnifiedPush.distributor() == null && !MZGramUnifiedPush.distributors().isEmpty()) {
                pickDistributor();
            }
            updateSoon();
        } else if (item.id == BUTTON_DISTRIBUTOR) {
            if (MZGramUnifiedPush.distributors().isEmpty()) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MZGramUnifiedPushStatusNone)).show();
            } else {
                pickDistributor();
            }
        } else if (item.id == BUTTON_GATEWAY_ENABLED) {
            MZGramConfig.toggleUnifiedPushGatewayEnabled();
            ((TextCheckCell) view).setChecked(MZGramConfig.unifiedPushGatewayEnabled);
            Utilities.globalQueue.postRunnable(MZGramUnifiedPush::registerTokens);
        } else if (item.id == BUTTON_GATEWAY) {
            editGateway();
        } else if (item.id == BUTTON_GATEWAY_RESET) {
            resetGateway();
        }
    }

    private void pickDistributor() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        MZGramUnifiedPush.pickDistributor(activity, this::showDistributorList, this::updateSoon);
    }

    // When the system has no default distributor to offer.
    private void showDistributorList() {
        List<String> distributors = MZGramUnifiedPush.distributors();
        if (getContext() == null || distributors.isEmpty()) {
            updateSoon();
            return;
        }
        CharSequence[] names = new CharSequence[distributors.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = MZGramUnifiedPush.appName(distributors.get(i));
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.MZGramUnifiedPushDistributor))
                .setItems(names, (dialog, which) -> {
                    MZGramUnifiedPush.useDistributor(distributors.get(which));
                    updateSoon();
                })
                .setOnDismissListener(dialog -> update())
                .show();
    }

    private void editGateway() {
        if (getContext() == null) {
            return;
        }
        EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
        editText.setText(MZGramUnifiedPushRules.gateway(true, MZGramConfig.unifiedPushGateway));
        editText.setSingleLine(true);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setLineColors(
                Theme.getColor(Theme.key_dialogInputField, getResourceProvider()),
                Theme.getColor(Theme.key_dialogInputFieldActivated, getResourceProvider()),
                Theme.getColor(Theme.key_text_RedBold, getResourceProvider()));
        editText.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        FrameLayout frame = new FrameLayout(getContext());
        frame.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.MZGramUnifiedPushGateway))
                .setView(frame)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    String text = editText.getText() == null ? "" : editText.getText().toString();
                    if (MZGramUnifiedPushRules.DEFAULT_GATEWAY.equals(MZGramUnifiedPushRules.normalizeGateway(text))) {
                        text = "";
                    }
                    if (!MZGramConfig.setUnifiedPushGateway(text)) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.MZGramUnifiedPushGatewayInvalid)).show();
                        return;
                    }
                    Utilities.globalQueue.postRunnable(MZGramUnifiedPush::registerTokens);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramUnifiedPushGatewaySaved)).show();
                    update();
                })
                .setNeutralButton(getString(R.string.MZGramUnifiedPushGatewayReset), (dialog, which) -> resetGateway())
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void resetGateway() {
        MZGramConfig.setUnifiedPushGateway("");
        Utilities.globalQueue.postRunnable(MZGramUnifiedPush::registerTokens);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramUnifiedPushGatewayDefault)).show();
        update();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }
}
