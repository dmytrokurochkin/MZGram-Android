/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Quick settings tile for ghost mode: one tap in the notification shade turns
 * ghost mode on or off. The tile shows only after it is added to the quick
 * settings by hand, so nothing changes otherwise. On a locked device the
 * device is unlocked first.
 *
 * Idea from exteraless QuickToggleActivity (GPL-3.0); written anew for MZGram
 * as a quick settings tile.
 */

package org.telegram.messenger.mzgram;

import android.annotation.TargetApi;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

@TargetApi(Build.VERSION_CODES.N)
public class MZGramGhostTile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        update();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::toggle);
        } else {
            toggle();
        }
    }

    private void toggle() {
        try {
            MZGramConfig.toggleGhostMode();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        update();
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        try {
            boolean on = MZGramGhostMode.isEnabled();
            tile.setIcon(Icon.createWithResource(this, R.drawable.ghost));
            tile.setLabel(LocaleController.getString(R.string.MZGramGhostMode));
            tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(LocaleController.getString(on ? R.string.MZGramTileOn : R.string.MZGramTileOff));
            }
            tile.updateTile();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
