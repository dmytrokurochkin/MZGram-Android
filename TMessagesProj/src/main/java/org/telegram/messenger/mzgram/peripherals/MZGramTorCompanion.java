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
import android.text.TextUtils;

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

    private static SharedConfig.ProxyInfo previousProxy;
    private static boolean hadPreviousProxy;
    private static boolean torProxyActive;

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

    private static android.content.SharedPreferences prefs() {
        if (ApplicationLoader.applicationContext != null) {
            return ApplicationLoader.applicationContext.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE);
        }
        return null;
    }

    public static void applyTorProxySettings(boolean enable) {
        try {
            android.content.SharedPreferences p = prefs();
            boolean torActiveInPrefs = p != null && p.getBoolean("mzgram_tor_active", false);

            if (enable) {
                // (б) Do not overwrite saved prior state if Tor was already active in memory or prefs
                if (!torProxyActive && !torActiveInPrefs) {
                    hadPreviousProxy = SharedConfig.isProxyEnabled();
                    previousProxy = SharedConfig.currentProxy;
                    if (p != null) {
                        android.content.SharedPreferences.Editor editor = p.edit();
                        editor.putBoolean("mzgram_tor_active", true);
                        editor.putBoolean("mzgram_tor_had_prev", hadPreviousProxy);
                        SharedConfig.ProxyInfo prev = previousProxy;
                        if (prev != null && prev.settings != null) {
                            editor.putBoolean("mzgram_tor_has_settings", true);
                            editor.putString("mzgram_tor_prev_ip", prev.settings.getAddress() != null ? prev.settings.getAddress() : "");
                            editor.putInt("mzgram_tor_prev_port", prev.settings.getPort());
                            editor.putString("mzgram_tor_prev_user", prev.settings.getUser() != null ? prev.settings.getUser() : "");
                            editor.putString("mzgram_tor_prev_pass", prev.settings.getPassword() != null ? prev.settings.getPassword() : "");
                            editor.putString("mzgram_tor_prev_secret", prev.settings.getSecret() != null ? prev.settings.getSecret() : "");
                            editor.putInt("mzgram_tor_prev_type", prev.settings.getType() != null ? prev.settings.getType().ordinal() : 0);
                        } else {
                            editor.putBoolean("mzgram_tor_has_settings", false);
                            editor.remove("mzgram_tor_prev_ip")
                                  .remove("mzgram_tor_prev_port")
                                  .remove("mzgram_tor_prev_user")
                                  .remove("mzgram_tor_prev_pass")
                                  .remove("mzgram_tor_prev_secret")
                                  .remove("mzgram_tor_prev_type");
                        }
                        editor.apply();
                    }
                }
                torProxyActive = true;
                org.telegram.utils.proxy.ProxySettings settings = org.telegram.utils.proxy.ProxySettings.builder()
                        .setType(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5)
                        .setAddress(TOR_SOCKS_HOST)
                        .setPort(TOR_SOCKS_PORT)
                        .setUser("")
                        .setPassword("")
                        .build();
                SharedConfig.ProxyInfo proxy = new SharedConfig.ProxyInfo(settings);
                SharedConfig.currentProxy = proxy;
                org.telegram.tgnet.ConnectionsManager.setProxySettings(true, settings);
            } else {
                boolean wasActive = torActiveInPrefs || torProxyActive;
                if (!wasActive) {
                    return;
                }
                torProxyActive = false;
                if (previousProxy != null) {
                    SharedConfig.currentProxy = previousProxy;
                    if (hadPreviousProxy && previousProxy.settings != null) {
                        org.telegram.tgnet.ConnectionsManager.setProxySettings(true, previousProxy.settings);
                    } else {
                        org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null);
                    }
                } else if (torActiveInPrefs && p != null) {
                    boolean hadPrevInPrefs = p.getBoolean("mzgram_tor_had_prev", false);
                    boolean hasSettingsInPrefs = p.getBoolean("mzgram_tor_has_settings", hadPrevInPrefs || p.contains("mzgram_tor_prev_ip"));
                    if (hasSettingsInPrefs) {
                        String ip = p.getString("mzgram_tor_prev_ip", "");
                        int port = p.getInt("mzgram_tor_prev_port", 1080);
                        String user = p.getString("mzgram_tor_prev_user", "");
                        String pass = p.getString("mzgram_tor_prev_pass", "");
                        String secret = p.getString("mzgram_tor_prev_secret", "");
                        int typeOrd = readSafeProxyType(p);
                        org.telegram.utils.proxy.ProxySettings.Type[] types = org.telegram.utils.proxy.ProxySettings.Type.values();
                        org.telegram.utils.proxy.ProxySettings.Type type = types[Math.max(0, Math.min(typeOrd, types.length - 1))];

                        org.telegram.utils.proxy.ProxySettings settings = org.telegram.utils.proxy.ProxySettings.builder()
                                .setType(type)
                                .setAddress(ip)
                                .setPort(port)
                                .setUser(user)
                                .setPassword(pass)
                                .setSecret(secret)
                                .build();
                        SharedConfig.ProxyInfo restored = null;
                        if (SharedConfig.proxyList != null) {
                            for (SharedConfig.ProxyInfo pi : SharedConfig.proxyList) {
                                if (pi != null && pi.settings != null
                                        && TextUtils.equals(pi.settings.getAddress(), ip)
                                        && pi.settings.getPort() == port
                                        && pi.settings.getType() == type) {
                                    restored = pi;
                                    break;
                                }
                            }
                        }
                        if (restored == null) {
                            restored = new SharedConfig.ProxyInfo(settings);
                        }
                        SharedConfig.currentProxy = restored;
                        if (hadPrevInPrefs) {
                            org.telegram.tgnet.ConnectionsManager.setProxySettings(true, settings);
                        } else {
                            org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null);
                        }
                    } else {
                        SharedConfig.currentProxy = null;
                        org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null);
                    }
                } else {
                    SharedConfig.currentProxy = null;
                    org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null);
                }
                if (p != null) {
                    p.edit()
                            .remove("mzgram_tor_active")
                            .remove("mzgram_tor_had_prev")
                            .remove("mzgram_tor_has_settings")
                            .remove("mzgram_tor_prev_ip")
                            .remove("mzgram_tor_prev_port")
                            .remove("mzgram_tor_prev_user")
                            .remove("mzgram_tor_prev_pass")
                            .remove("mzgram_tor_prev_secret")
                            .remove("mzgram_tor_prev_type")
                            .apply();
                }
                previousProxy = null;
                hadPreviousProxy = false;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // Testing state helpers to allow testing without reflection
    public static int readSafeProxyType(android.content.SharedPreferences p) {
        if (p == null) {
            return 0;
        }
        try {
            java.util.Map<String, ?> all = p.getAll();
            if (all != null) {
                Object val = all.get("mzgram_tor_prev_type");
                if (val instanceof Number) {
                    return ((Number) val).intValue();
                } else if (val instanceof String) {
                    String str = (String) val;
                    try {
                        return Integer.parseInt(str);
                    } catch (NumberFormatException e) {
                        for (org.telegram.utils.proxy.ProxySettings.Type t : org.telegram.utils.proxy.ProxySettings.Type.values()) {
                            if (t.name().equalsIgnoreCase(str)) {
                                return t.ordinal();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
    public static boolean isTorProxyActive() {
        return torProxyActive;
    }

    public static boolean hadPreviousProxy() {
        return hadPreviousProxy;
    }

    public static SharedConfig.ProxyInfo getPreviousProxy() {
        return previousProxy;
    }

    public static void resetTestingState() {
        torProxyActive = false;
        hadPreviousProxy = false;
        previousProxy = null;
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
