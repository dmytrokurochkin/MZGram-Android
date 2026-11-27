/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.notifications;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Intent;
import android.view.View;

import org.mzgram.notifications.DeviceCheck.State;
import org.telegram.messenger.R;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * MZGram settings > General > Notifications > Device check: what the phone allows the app,
 * each row with yes, no or unknown. A tap opens the system screen where the user changes
 * it; the last row shares the push report.
 */
public class DeviceCheckActivity extends UniversalFragment {

    private static final int BUTTON_NOTIFICATIONS = 1;
    private static final int BUTTON_CALLS = 2;
    private static final int BUTTON_BATTERY = 3;
    private static final int BUTTON_AUTOSTART = 4;
    private static final int BUTTON_POPUPS = 5;
    private static final int BUTTON_LOCK_SCREEN = 6;
    private static final int BUTTON_SHARE = 7;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.DeviceCheck);
    }

    private static String stateText(State state) {
        return getString(state == State.YES ? R.string.DeviceCheckYes
                : state == State.NO ? R.string.DeviceCheckNo : R.string.DeviceCheckUnknown);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.DeviceCheckInfo)));
        items.add(UItem.asButton(BUTTON_NOTIFICATIONS, getString(R.string.DeviceCheckNotifications), stateText(DeviceCheck.notifications())));
        items.add(UItem.asButton(BUTTON_CALLS, getString(R.string.DeviceCheckCalls), stateText(DeviceCheck.calls())));
        items.add(UItem.asButton(BUTTON_BATTERY, getString(R.string.DeviceCheckBattery), stateText(DeviceCheck.battery())));
        items.add(UItem.asShadow(null));
        if (DeviceCheck.isMiui()) {
            final String version = DeviceCheck.miuiVersion();
            items.add(UItem.asHeader(version != null ? "MIUI " + version : "MIUI"));
            items.add(UItem.asButton(BUTTON_AUTOSTART, getString(R.string.DeviceCheckAutostart), stateText(DeviceCheck.miuiPermission(XiaomiUtilities.OP_AUTO_START))));
            items.add(UItem.asButton(BUTTON_POPUPS, getString(R.string.DeviceCheckPopups), stateText(DeviceCheck.miuiPermission(XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY))));
            items.add(UItem.asButton(BUTTON_LOCK_SCREEN, getString(R.string.DeviceCheckLockScreen), stateText(DeviceCheck.miuiPermission(XiaomiUtilities.OP_SHOW_WHEN_LOCKED))));
            items.add(UItem.asShadow(getString(R.string.DeviceCheckMiuiInfo)));
        }
        items.add(UItem.asButton(BUTTON_SHARE, getString(R.string.DeviceCheckShare)));
        items.add(UItem.asShadow(getString(R.string.DeviceCheckShareInfo)));
    }

    private static Intent intentFor(int id) {
        switch (id) {
            case BUTTON_NOTIFICATIONS:
                return DeviceCheck.notificationsIntent();
            case BUTTON_CALLS:
                return DeviceCheck.callsIntent();
            case BUTTON_BATTERY:
                return DeviceCheck.batteryIntent();
            case BUTTON_AUTOSTART:
            case BUTTON_POPUPS:
            case BUTTON_LOCK_SCREEN:
                return DeviceCheck.miuiPermissionsIntent();
            case BUTTON_SHARE: {
                final Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "MZGram push report");
                send.putExtra(Intent.EXTRA_TEXT, PushDiagnostics.report());
                return Intent.createChooser(send, getString(R.string.DeviceCheckShare));
            }
            default:
                return null;
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final Intent intent = intentFor(item.id);
        if (intent == null) {
            return;
        }
        if (open(intent)) {
            return;
        }
        // MIUI's permissions screen is missing on some versions, and the calls channel is
        // made only at the first call: the app's own system page is the next best place.
        if (item.id != BUTTON_SHARE && open(DeviceCheck.appDetailsIntent())) {
            return;
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.DeviceCheckCannotOpen)).show();
    }

    private boolean open(Intent intent) {
        if (getParentActivity() == null) {
            return false;
        }
        try {
            getParentActivity().startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    // The states change while the user is in the system screens.
    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
