/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Report files of the profilers (MZGramJankProfiler, MZGramEnergyProfiler):
 * the latest one by prefix in the logs folder, and the system share sheet for
 * one when the user asks for it. Nothing is sent by the app itself.
 */

package org.telegram.messenger.mzgram;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;

public final class MZGramProfilerReports {

    private MZGramProfilerReports() {
    }

    static File last(String prefix) {
        final File dir = AndroidUtilities.getLogsDir();
        if (dir == null) {
            return null;
        }
        final File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".txt"));
        File last = null;
        if (files != null) {
            for (File file : files) {
                if (last == null || file.lastModified() > last.lastModified()) {
                    last = file;
                }
            }
        }
        return last;
    }

    /** Opens the share sheet for the report; false when there is nothing to share. */
    public static boolean share(Activity activity, File file) {
        if (activity == null || activity.isFinishing() || file == null || !file.exists()) {
            return false;
        }
        try {
            final Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, file.getName()));
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }
}
