/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Notifications and Sounds > UnifiedPush: whether notifications come
 * through UnifiedPush, which distributor, the diagnostics, the gateway and
 * the key of the built-in Google FCM distributor.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramPushDiagnostics;
import org.telegram.messenger.mzgram.MZGramPushTest;
import org.telegram.messenger.mzgram.MZGramUnifiedPush;
import org.telegram.messenger.mzgram.MZGramUnifiedPushRules;
import org.telegram.messenger.mzgram.MZGramWebPushCrypto;
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
    private static final int BUTTON_DIAGNOSTICS = 6;
    private static final int BUTTON_VAPID = 7;

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
        String current = null;
        if (active) {
            List<String> distributors = MZGramUnifiedPush.distributors();
            String acked = MZGramUnifiedPush.ackedDistributor();
            current = acked != null ? acked : MZGramUnifiedPush.distributor();
            items.add(UItem.asHeader(getString(R.string.MZGramUnifiedPushDistributors)));
            for (String distributor : distributors) {
                UItem row = UItem.asRadio(BUTTON_DISTRIBUTOR, MZGramUnifiedPush.label(distributor)).setChecked(distributor.equals(current));
                row.object = distributor;
                items.add(row);
            }
            items.add(UItem.asShadow(statusText()));
        }
        long lastPush = MZGramPushDiagnostics.lastReceived();
        items.add(UItem.asButton(BUTTON_DIAGNOSTICS, getString(R.string.MZGramPushDiagnostics),
                lastPush == 0 ? getString(R.string.MZGramPushDiagnosticsNever) : LocaleController.formatDateTime(lastPush / 1000, true)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_GATEWAY_ENABLED, getString(R.string.MZGramUnifiedPushGatewayEnabled)).setChecked(MZGramConfig.unifiedPushGatewayEnabled));
        items.add(UItem.asButton(BUTTON_GATEWAY, getString(R.string.MZGramUnifiedPushGateway), MZGramUnifiedPushRules.gateway(true, MZGramConfig.unifiedPushGateway)));
        items.add(UItem.asButton(BUTTON_GATEWAY_RESET, getString(R.string.MZGramUnifiedPushGatewayReset)));
        items.add(UItem.asShadow(getString(R.string.MZGramUnifiedPushGatewayPrefix) + " " + getString(R.string.MZGramUnifiedPushGatewayInfo)));
        // Only the built-in distributor signs with this key.
        if (active && MZGramUnifiedPush.isBuiltIn(current)) {
            items.add(UItem.asHeader(getString(R.string.MZGramEmbeddedFcm)));
            items.add(UItem.asButton(BUTTON_VAPID, getString(R.string.MZGramEmbeddedFcmVapid),
                    getString(MZGramConfig.unifiedPushVapidKey.isEmpty() ? R.string.MZGramEmbeddedFcmVapidDefault : R.string.MZGramEmbeddedFcmVapidCustom)));
            items.add(UItem.asShadow(formatString(R.string.MZGramEmbeddedFcmVapidInfo, MZGramUnifiedPushRules.fcmEndpointPrefix(MZGramConfig.unifiedPushGateway))));
        }
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
            updateSoon();
        } else if (item.id == BUTTON_DISTRIBUTOR) {
            selectDistributor((String) item.object);
        } else if (item.id == BUTTON_DIAGNOSTICS) {
            showDiagnostics();
        } else if (item.id == BUTTON_GATEWAY_ENABLED) {
            MZGramConfig.toggleUnifiedPushGatewayEnabled();
            ((TextCheckCell) view).setChecked(MZGramConfig.unifiedPushGatewayEnabled);
            MZGramUnifiedPush.onGatewayChanged();
        } else if (item.id == BUTTON_GATEWAY) {
            editGateway();
        } else if (item.id == BUTTON_GATEWAY_RESET) {
            resetGateway();
        } else if (item.id == BUTTON_VAPID) {
            editVapidKey();
        }
    }

    // Picking the built-in Google FCM tells first what Google gets to see.
    private void selectDistributor(String distributor) {
        if (distributor == null || distributor.equals(MZGramUnifiedPush.ackedDistributor())) {
            return;
        }
        if (MZGramUnifiedPush.isBuiltIn(distributor)) {
            if (getContext() == null) {
                return;
            }
            new AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramEmbeddedFcm))
                    .setMessage(getString(R.string.MZGramEmbeddedFcmWarning))
                    .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                        MZGramUnifiedPush.useDistributor(distributor);
                        updateSoon();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else {
            MZGramUnifiedPush.useDistributor(distributor);
            updateSoon();
        }
    }

    public static String diagnosticsText() {
        StringBuilder text = new StringBuilder();
        long received = MZGramPushDiagnostics.received();
        if (received == 0) {
            text.append(getString(R.string.MZGramPushDiagnosticsNone));
        } else {
            long ago = (System.currentTimeMillis() - MZGramPushDiagnostics.lastReceived()) / 1000;
            text.append(formatString(R.string.MZGramPushDiagnosticsLast, String.valueOf(ago)));
            text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsCounts, String.valueOf(received),
                    String.valueOf(MZGramPushDiagnostics.decrypted()), String.valueOf(MZGramPushDiagnostics.wakeUps()),
                    String.valueOf(MZGramPushDiagnostics.decryptFailed())));
        }
        boolean keys;
        try {
            keys = MZGramWebPushCrypto.keys() != null;
        } catch (Exception e) {
            keys = false;
        }
        text.append("\n\n").append(formatString(R.string.MZGramPushDiagnosticsKeys, getString(keys ? R.string.MZGramPushDiagnosticsPresent : R.string.MZGramPushDiagnosticsMissing)));
        String playServices = MZGramPushDiagnostics.playServices();
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsPlayServices, playServices != null ? playServices : getString(R.string.MZGramPushDiagnosticsNotInstalled)));
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsFcm, fcmResultText()));
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsGms, String.valueOf(MZGramPushDiagnostics.gmsReceived())));
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsTelegram, telegramResultText()));
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsShown, String.valueOf(MZGramPushDiagnostics.shown())));
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsAllowed, getString(MZGramPushDiagnostics.notificationsAllowed() ? R.string.MZGramPushDiagnosticsAllowedYes : R.string.MZGramPushDiagnosticsAllowedNo)));
        String token = SharedConfig.pushType == org.telegram.messenger.PushListenerController.PUSH_TYPE_WEB ? SharedConfig.pushString : null;
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsEndpoint,
                token == null || token.isEmpty() ? getString(R.string.MZGramPushDiagnosticsNoneSet) : "\n" + MZGramUnifiedPushRules.maskPushToken(token)));
        String failure = MZGramPushDiagnostics.lastFailure();
        if (failure != null) {
            text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsFailure, failure));
        }
        String saved = MZGramUnifiedPush.distributor();
        String acked = MZGramUnifiedPush.ackedDistributor();
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsSaved, saved != null ? MZGramUnifiedPush.label(saved) : getString(R.string.MZGramPushDiagnosticsNoneSet)));
        text.append('\n').append(formatString(R.string.MZGramPushDiagnosticsAcked, acked != null ? MZGramUnifiedPush.label(acked) : getString(R.string.MZGramPushDiagnosticsNoneSet)));
        List<String> events = MZGramPushDiagnostics.events();
        if (!events.isEmpty()) {
            text.append("\n\n").append(getString(R.string.MZGramPushDiagnosticsEvents));
            for (int i = events.size() - 1; i >= 0; i--) {
                text.append('\n').append(events.get(i));
            }
        }
        return text.toString();
    }

    // What Telegram last answered to the WebPush registration, or how long
    // it has been silent.
    private static String telegramResultText() {
        long requested = MZGramPushDiagnostics.telegramRequestTime();
        long answered = MZGramPushDiagnostics.telegramResultTime();
        if (requested == 0 && answered == 0) {
            return getString(R.string.MZGramPushDiagnosticsFcmNotTried);
        }
        if (requested > answered && (System.currentTimeMillis() - requested) / 1000 >= 30) {
            return formatString(R.string.MZGramPushDiagnosticsTelegramNoAnswer, String.valueOf((System.currentTimeMillis() - requested) / 1000));
        }
        String result = MZGramPushDiagnostics.telegramResult();
        return result != null ? result + " (" + LocaleController.formatDateTime(answered / 1000, true) + ")" : getString(R.string.MZGramPushDiagnosticsFcmNotTried);
    }

    // What Google Play Services (or microG) last said to the built-in
    // distributor, or how long it has been silent.
    private static String fcmResultText() {
        long requested = MZGramPushDiagnostics.fcmRequestTime();
        long answered = MZGramPushDiagnostics.fcmResultTime();
        if (requested == 0 && answered == 0) {
            return getString(R.string.MZGramPushDiagnosticsFcmNotTried);
        }
        if (requested > answered) {
            long silent = (System.currentTimeMillis() - requested) / 1000;
            if (silent >= 30) {
                return formatString(R.string.MZGramPushDiagnosticsFcmNoAnswer, String.valueOf(silent));
            }
        }
        String result = MZGramPushDiagnostics.fcmResult();
        return result != null ? result + " (" + LocaleController.formatDateTime(answered / 1000, true) + ")" : getString(R.string.MZGramPushDiagnosticsFcmNotTried);
    }

    private void showDiagnostics() {
        if (getContext() == null) {
            return;
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.MZGramPushDiagnostics))
                .setMessage(diagnosticsText())
                .setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
                    MZGramPushDiagnostics.reset();
                    update();
                })
                .setNegativeButton(getString(R.string.MZGramPushTest), (dialog, which) -> sendTestPush())
                .setPositiveButton(getString(R.string.OK), null)
                .show();
    }

    private void sendTestPush() {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MZGramPushTestSending)).show();
        MZGramPushTest.send((outcome, detail) -> {
            if (getContext() == null || getParentActivity() == null) {
                return;
            }
            new AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.MZGramPushTest))
                    .setMessage(testPushText(outcome, detail))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
            update();
        });
    }

    public static String testPushText(MZGramPushTest.Outcome outcome, String detail) {
        switch (outcome) {
            case ARRIVED:
                return formatString(R.string.MZGramPushTestArrived, detail);
            case LOST:
                return formatString(R.string.MZGramPushTestLost, detail, String.valueOf(MZGramPushTest.WAIT_SECONDS));
            case REFUSED:
                return "403".equals(detail) ? getString(R.string.MZGramPushTestForbidden) : formatString(R.string.MZGramPushTestRefused, detail);
            case FAILED:
                return formatString(R.string.MZGramPushTestFailed, detail);
            default:
                return getString(R.string.MZGramPushTestNoEndpoint);
        }
    }

    private EditTextBoldCursor dialogField(String text) {
        EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
        editText.setText(text);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setLineColors(
                Theme.getColor(Theme.key_dialogInputField, getResourceProvider()),
                Theme.getColor(Theme.key_dialogInputFieldActivated, getResourceProvider()),
                Theme.getColor(Theme.key_text_RedBold, getResourceProvider()));
        editText.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        return editText;
    }

    private FrameLayout frame(View field) {
        FrameLayout frame = new FrameLayout(getContext());
        frame.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));
        return frame;
    }

    private void editGateway() {
        if (getContext() == null) {
            return;
        }
        EditTextBoldCursor editText = dialogField(MZGramUnifiedPushRules.gateway(true, MZGramConfig.unifiedPushGateway));
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.MZGramUnifiedPushGateway))
                .setView(frame(editText))
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    String text = editText.getText() == null ? "" : editText.getText().toString();
                    if (MZGramUnifiedPushRules.DEFAULT_GATEWAY.equals(MZGramUnifiedPushRules.normalizeGateway(text))) {
                        text = "";
                    }
                    String before = MZGramConfig.unifiedPushGateway;
                    if (!MZGramConfig.setUnifiedPushGateway(text)) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.MZGramUnifiedPushGatewayInvalid)).show();
                        return;
                    }
                    if (!before.equals(MZGramConfig.unifiedPushGateway)) {
                        MZGramUnifiedPush.onGatewayChanged();
                    }
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramUnifiedPushGatewaySaved)).show();
                    update();
                })
                .setNeutralButton(getString(R.string.MZGramUnifiedPushGatewayReset), (dialog, which) -> resetGateway())
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void resetGateway() {
        boolean changed = !MZGramConfig.unifiedPushGateway.isEmpty();
        MZGramConfig.setUnifiedPushGateway("");
        if (changed) {
            MZGramUnifiedPush.onGatewayChanged();
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.MZGramUnifiedPushGatewayDefault)).show();
        update();
    }

    private void editVapidKey() {
        if (getContext() == null) {
            return;
        }
        EditTextBoldCursor editText = dialogField(MZGramUnifiedPushRules.vapidKey(MZGramConfig.unifiedPushVapidKey));
        AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.MZGramEmbeddedFcmVapid))
                .setView(frame(editText))
                .setPositiveButton(getString(R.string.OK), (d, which) -> {
                    String key = editText.getText() == null ? "" : editText.getText().toString();
                    String before = MZGramConfig.unifiedPushVapidKey;
                    if (!MZGramConfig.setUnifiedPushVapidKey(key)) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.MZGramEmbeddedFcmVapidInvalid)).show();
                        return;
                    }
                    if (!before.equals(MZGramConfig.unifiedPushVapidKey)) {
                        MZGramUnifiedPush.onGatewayChanged();
                    }
                    update();
                })
                .setNeutralButton(getString(R.string.Reset), null)
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        dialog.show();
        // Reset fills the field and keeps the dialog open, so Cancel still
        // backs out of it.
        View reset = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (reset != null) {
            reset.setOnClickListener(v -> editText.setText(MZGramUnifiedPushRules.DEFAULT_VAPID_KEY));
        }
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
