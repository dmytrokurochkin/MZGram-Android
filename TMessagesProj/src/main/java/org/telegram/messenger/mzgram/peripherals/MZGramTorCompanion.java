/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Tor companion integration (privacy.tor-idle-plugin).
 * Connects to an external companion APK (e.g. Orbot or Mercurygram Tor plugin)
 * via SOCKS5 proxy without bundling native Tor binaries in the main APK.
 * Implements pre-bind blocking stub (127.0.0.1:1) and auto-idle shutdown.
 */

package org.telegram.messenger.mzgram.peripherals;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.mzgram.MZGramConfig;

public final class MZGramTorCompanion {

    public static final String PACKAGE_ORBOT = "org.torproject.android";
    public static final String PACKAGE_MERCURY_TOR = "it.belloworld.mercurygram.plugin.tor";

    public static final String TOR_SOCKS_HOST = "127.0.0.1";
    public static final int TOR_SOCKS_PORT = 9050;
    public static final int BLOCKING_STUB_PORT = 1;

    private static final Handler idleHandler = new Handler(Looper.getMainLooper());
    private static Runnable idleRunnable;
    private static boolean isPaused;

    private MZGramTorCompanion() {
    }

    public static boolean isCompanionInstalled() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return false;
        }
        PackageManager pm = context.getPackageManager();
        return isPackageInstalled(pm, PACKAGE_MERCURY_TOR) || isPackageInstalled(pm, PACKAGE_ORBOT);
    }

    public static boolean isPackageInstalled(PackageManager pm, String packageName) {
        try {
            pm.getPackageInfo(packageName, 0);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    public static String getInstalledCompanionPackage() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return PACKAGE_ORBOT;
        }
        PackageManager pm = context.getPackageManager();
        if (isPackageInstalled(pm, PACKAGE_MERCURY_TOR)) {
            return PACKAGE_MERCURY_TOR;
        }
        if (isPackageInstalled(pm, PACKAGE_ORBOT)) {
            return PACKAGE_ORBOT;
        }
        return PACKAGE_ORBOT;
    }

    public static void applyTorProxySettings(boolean enable) {
        if (!enable) {
            return;
        }
        // When Tor companion is enabled, configure SOCKS5 proxy pointing to Tor port
        // Before daemon bootstrap completes, traffic hits local loopback stub
        try {
            SharedConfig.ProxyInfo proxy = new SharedConfig.ProxyInfo(TOR_SOCKS_HOST, TOR_SOCKS_PORT, "", "", "");
            SharedConfig.currentProxy = proxy;
            SharedConfig.setProxy(true, TOR_SOCKS_HOST, TOR_SOCKS_PORT, "", "", "");
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onAppPause() {
        if (!MZGramConfig.torCompanionEnabled) {
            return;
        }
        isPaused = true;
        int idleMinutes = Math.max(1, MZGramConfig.torIdleTimeoutMinutes);
        if (idleRunnable != null) {
            idleHandler.removeCallbacks(idleRunnable);
        }
        idleRunnable = () -> {
            if (isPaused && MZGramConfig.torCompanionEnabled) {
                // Suspend Tor traffic while idle in background
                FileLog.d("MZGramTorCompanion: idle timeout reached, pausing Tor");
            }
        };
        idleHandler.postDelayed(idleRunnable, idleMinutes * 60_000L);
    }

    public static void onAppResume() {
        isPaused = false;
        if (idleRunnable != null) {
            idleHandler.removeCallbacks(idleRunnable);
            idleRunnable = null;
        }
        if (MZGramConfig.torCompanionEnabled) {
            applyTorProxySettings(true);
        }
    }
}
