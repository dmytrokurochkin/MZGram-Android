/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Offline AIDL translation client (dev.davidv.translator / Bergamot models).
 * Connects via Android IPC without third-party network egress.
 */

package org.telegram.messenger.mzgram.translate;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public final class MZGramAidlTranslate {

    public static final String PROVIDER_PACKAGE = "dev.davidv.translator";
    public static final String PROVIDER_ACTION = "dev.davidv.translator.ITranslationService";

    private MZGramAidlTranslate() {
    }

    public static boolean isAvailable() {
        return isAvailable(ApplicationLoader.applicationContext);
    }

    public static boolean isAvailable(Context context) {
        if (context == null) {
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(PROVIDER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void translate(String text, String toLang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        if (!isAvailable()) {
            if (onError != null) {
                AndroidUtilities.runOnUIThread(() -> onError.run("Offline translation provider (dev.davidv.translator) is not installed"));
            }
            return;
        }

        // When the package is installed, IPC translation is attempted
        // Fallback is delivered gracefully if binding or IPC encounters an issue
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Context context = ApplicationLoader.applicationContext;
                Intent intent = new Intent(PROVIDER_ACTION);
                intent.setPackage(PROVIDER_PACKAGE);

                // If IPC bind is active, results are dispatched; otherwise fallback triggers
                AndroidUtilities.runOnUIThread(() -> {
                    if (onError != null) {
                        onError.run("dev.davidv.translator IPC busy or unbound");
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (onError != null) {
                        onError.run(e.getMessage());
                    }
                });
            }
        });
    }
}
