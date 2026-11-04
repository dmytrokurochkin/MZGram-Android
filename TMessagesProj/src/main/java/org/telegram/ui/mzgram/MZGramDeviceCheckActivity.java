/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Notifications and Sounds > UnifiedPush > Device check: whether
 * notifications are allowed, whether calls pop up on screen, battery
 * limits and, on MIUI, its own permissions. Each row only shows the state
 * and opens the system screen where the user can change it; MZGram itself
 * changes nothing. Share push log gives the log without the push token.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramDeviceCheck;
import org.telegram.messenger.mzgram.MZGramDeviceCheck.State;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MZGramDeviceCheckActivity extends UniversalFragment {

    public static final int BUTTON_NOTIFICATIONS = 1;
    public static final int BUTTON_CALLS = 2;
    public static final int BUTTON_BATTERY = 3;
    public static final int BUTTON_AUTOSTART = 4;
    public static final int BUTTON_POPUPS = 5;
    public static final int BUTTON_LOCK_SCREEN = 6;
    public static final int BUTTON_SHARE = 7;
    public static final int BUTTON_RELIABLE = 8;

    // The last intent a row tried to open, for tests
    public static Intent openedForTest;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramDeviceCheck);
    }

    public static String stateText(State state) {
        return getString(state == State.YES ? R.string.MZGramDeviceCheckYes
                : state == State.NO ? R.string.MZGramDeviceCheckNo : R.string.MZGramDeviceCheckUnknown);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.MZGramDeviceCheckInfo)));
        items.add(UItem.asButton(BUTTON_NOTIFICATIONS, getString(R.string.MZGramDeviceCheckNotifications), stateText(MZGramDeviceCheck.notifications())));
        items.add(UItem.asButton(BUTTON_CALLS, getString(R.string.MZGramDeviceCheckCalls), stateText(MZGramDeviceCheck.calls())));
        items.add(UItem.asButton(BUTTON_BATTERY, getString(R.string.MZGramDeviceCheckBattery), stateText(MZGramDeviceCheck.battery())));
        items.add(UItem.asButton(BUTTON_RELIABLE, getString(R.string.MZGramReliable), stateText(MZGramConfig.reliableDelivery ? State.YES : State.NO)));
        // The phone limits the app in the background: Reliable delivery is
        // offered; its switch stays the user's
        items.add(UItem.asShadow(suggestsReliable() ? getString(R.string.MZGramReliableSuggest) : null));
        if (MZGramDeviceCheck.isMiui()) {
            String version = MZGramDeviceCheck.miuiVersion();
            String miui = getString(R.string.MZGramDeviceCheckMiui);
            items.add(UItem.asHeader(version != null ? miui + " " + version : miui));
            items.add(UItem.asButton(BUTTON_AUTOSTART, getString(R.string.MZGramDeviceCheckAutostart), stateText(MZGramDeviceCheck.miuiPermission(MZGramDeviceCheck.OP_AUTO_START))));
            items.add(UItem.asButton(BUTTON_POPUPS, getString(R.string.MZGramDeviceCheckPopups), stateText(MZGramDeviceCheck.miuiPermission(MZGramDeviceCheck.OP_BACKGROUND_START_ACTIVITY))));
            items.add(UItem.asButton(BUTTON_LOCK_SCREEN, getString(R.string.MZGramDeviceCheckLockScreen), stateText(MZGramDeviceCheck.miuiPermission(MZGramDeviceCheck.OP_SHOW_WHEN_LOCKED))));
            items.add(UItem.asShadow(getString(R.string.MZGramDeviceCheckMiuiInfo)));
        }
        items.add(UItem.asButton(BUTTON_SHARE, getString(R.string.MZGramDeviceCheckShare)));
        items.add(UItem.asShadow(getString(R.string.MZGramDeviceCheckShareInfo)));
    }

    public static boolean suggestsReliable() {
        return !MZGramConfig.reliableDelivery && MZGramDeviceCheck.battery() == State.NO;
    }

    // The system screen a row opens; null for a row that opens none.
    public static Intent intentFor(int id) {
        Context context = ApplicationLoader.applicationContext;
        String packageName = context.getPackageName();
        switch (id) {
            case BUTTON_NOTIFICATIONS:
                return MZGramDeviceCheck.notificationsIntent(packageName);
            case BUTTON_CALLS:
                return MZGramDeviceCheck.channelIntent(packageName, MZGramDeviceCheck.callsChannelId());
            case BUTTON_BATTERY:
                return MZGramDeviceCheck.batteryIntent();
            case BUTTON_AUTOSTART:
            case BUTTON_POPUPS:
            case BUTTON_LOCK_SCREEN:
                return MZGramDeviceCheck.miuiPermissionsIntent(packageName, android.os.Process.myUid());
            case BUTTON_SHARE: {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "MZGram push log");
                send.putExtra(Intent.EXTRA_TEXT, MZGramDeviceCheck.log());
                return Intent.createChooser(send, getString(R.string.MZGramDeviceCheckShare));
            }
            default:
                return null;
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_RELIABLE) {
            presentFragment(new org.telegram.ui.NotificationsSettingsActivity());
            return;
        }
        Intent intent = intentFor(item.id);
        if (intent == null) {
            return;
        }
        openedForTest = intent;
        if (open(intent)) {
            return;
        }
        // MIUI's permissions screen is missing on some versions, and the
        // calls channel is made only at the first call: the app's own
        // settings are the next best place.
        if (item.id != BUTTON_SHARE && open(MZGramDeviceCheck.appDetailsIntent(ApplicationLoader.applicationContext.getPackageName()))) {
            return;
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.MZGramDeviceCheckCannotOpen)).show();
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
