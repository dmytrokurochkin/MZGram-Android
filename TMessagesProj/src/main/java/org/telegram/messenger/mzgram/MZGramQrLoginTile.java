/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Quick settings tile for logging in on another device by QR code: a tap opens
 * Telegram's own "Scan QR code" screen (tg://scanqr, the same one as in
 * Devices). The tile shows only after it is added to the quick settings by
 * hand. The app passcode, when set, is asked as usual.
 *
 * Idea from exteraless quick toggles and QR entry (GPL-3.0); written anew for
 * MZGram as a quick settings tile.
 */

package org.telegram.messenger.mzgram;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

@TargetApi(Build.VERSION_CODES.N)
public class MZGramQrLoginTile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        try {
            tile.setIcon(Icon.createWithResource(this, R.drawable.msg_qr_scan));
            tile.setLabel(LocaleController.getString(R.string.MZGramQrLoginTile));
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://scanqr"), this, LaunchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
