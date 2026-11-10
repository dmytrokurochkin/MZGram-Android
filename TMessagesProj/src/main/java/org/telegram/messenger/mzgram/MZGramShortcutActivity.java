/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Launcher static shortcut handler for MZGram actions (Ghost Mode toggle, QR login).
 */

package org.telegram.messenger.mzgram;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

public class MZGramShortcutActivity extends Activity {

    public static final String ACTION_TOGGLE_GHOST_MODE = "org.telegram.messenger.mzgram.ACTION_TOGGLE_GHOST_MODE";
    public static final String ACTION_SCAN_QR = "org.telegram.messenger.mzgram.ACTION_SCAN_QR";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final String action = intent == null ? null : intent.getAction();
        try {
            ApplicationLoader.postInitApplication();
            if (ACTION_TOGGLE_GHOST_MODE.equals(action)) {
                MZGramConfig.toggleGhostMode();
                boolean on = MZGramGhostMode.isEnabled();
                String text = LocaleController.getString(R.string.MZGramGhostMode) + ": " +
                        LocaleController.getString(on ? R.string.MZGramTileOn : R.string.MZGramTileOff);
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            } else if (ACTION_SCAN_QR.equals(action)) {
                Intent qrIntent = new Intent(this, LaunchActivity.class);
                qrIntent.setAction(Intent.ACTION_VIEW);
                qrIntent.setData(Uri.parse("tg://scanqr"));
                qrIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(qrIntent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        finish();
    }
}
