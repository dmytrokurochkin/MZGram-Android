package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;

/**
 * Turns the proxy off by itself on the networks the user picked (a VPN, mobile data,
 * Wi-Fi) and back on when the phone leaves them, only if it was this class that
 * turned it off. The idea and the three conditions come from exteraless
 * (ProxyDisableCondition, ProxyUtil.registerNetworkCallback, GPL-3.0); the code is
 * MZGram's own. Nothing is sent anywhere: the conditions are read from Android.
 *
 * The conditions are a bit mask in MZGramConfig.proxyDisableConditions, 0 (off) by
 * default. The device state (the proxy was turned off here) lives in its own file,
 * outside the settings backup.
 */
public final class MZGramProxyAutoDisable {

    public static final int VPN = 1;
    public static final int MOBILE_DATA = 2;
    public static final int WIFI = 4;
    public static final int ALL = VPN | MOBILE_DATA | WIFI;

    private static final String PREFS = "mzgram_proxy";
    private static final String KEY_AUTO_DISABLED = "autoDisabled";

    private static ConnectivityManager.NetworkCallback callback;
    private static NotificationCenter.NotificationCenterDelegate observer;
    // The decision for the last network seen; only a change acts, so a proxy turned
    // back on by hand stays on until the network changes
    private static Boolean lastDecision;

    private MZGramProxyAutoDisable() {
    }

    /** The default network callback is API 24+. */
    public static boolean available() {
        return Build.VERSION.SDK_INT >= 24;
    }

    public static boolean isOn(int condition) {
        return (MZGramConfig.proxyDisableConditions & condition) != 0;
    }

    /** Turns one condition on or off and applies the new mask right away. */
    public static void set(int condition, boolean on) {
        final int mask = on
                ? MZGramConfig.proxyDisableConditions | condition
                : MZGramConfig.proxyDisableConditions & ~condition;
        MZGramConfig.setProxyDisableConditions(mask);
        lastDecision = null;
        if (mask == 0) {
            stop();
            apply(false);
        } else {
            start();
            check();
        }
    }

    /** Whether the proxy goes off on a network with these transports (pure, for the tests). */
    public static boolean shouldDisable(int mask, boolean vpn, boolean cellular, boolean wifi) {
        return (mask & VPN) != 0 && vpn
                || (mask & MOBILE_DATA) != 0 && cellular
                || (mask & WIFI) != 0 && wifi;
    }

    /** Listens to the network while a condition is on; called from LaunchActivity.onResume. */
    public static void start() {
        if (MZGramConfig.proxyDisableConditions == 0 || !available() || callback != null) {
            return;
        }
        try {
            final ConnectivityManager manager = connectivity();
            if (manager == null) {
                return;
            }
            final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    onNetwork(capabilities);
                }
            };
            manager.registerDefaultNetworkCallback(networkCallback);
            callback = networkCallback;
        } catch (Exception e) {
            FileLog.e("MZGramProxyAutoDisable.start", e);
            return;
        }
        AndroidUtilities.runOnUIThread(MZGramProxyAutoDisable::observe);
    }

    private static void stop() {
        final ConnectivityManager.NetworkCallback networkCallback = callback;
        callback = null;
        if (networkCallback == null) {
            return;
        }
        try {
            final ConnectivityManager manager = connectivity();
            if (manager != null) {
                manager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            FileLog.e("MZGramProxyAutoDisable.stop", e);
        }
    }

    // The current default network, without waiting for a change
    private static void check() {
        if (!available()) {
            return;
        }
        try {
            final ConnectivityManager manager = connectivity();
            final Network network = manager == null ? null : manager.getActiveNetwork();
            final NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
            if (capabilities != null) {
                onNetwork(capabilities);
            }
        } catch (Exception e) {
            FileLog.e("MZGramProxyAutoDisable.check", e);
        }
    }

    private static void onNetwork(NetworkCapabilities capabilities) {
        final boolean disable = shouldDisable(MZGramConfig.proxyDisableConditions,
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
        AndroidUtilities.runOnUIThread(() -> {
            if (MZGramConfig.proxyDisableConditions == 0) {
                return;
            }
            if (lastDecision != null && lastDecision == disable) {
                return;
            }
            lastDecision = disable;
            apply(disable);
        });
    }

    // On the UI thread
    private static void apply(boolean disable) {
        final SharedPreferences main = MessagesController.getGlobalMainSettings();
        final boolean enabled = main.getBoolean("proxy_enabled", false);
        if (disable) {
            if (!enabled || SharedConfig.currentProxy == null) {
                return;
            }
            state().edit().putBoolean(KEY_AUTO_DISABLED, true).apply();
            setProxy(main, false);
        } else {
            if (!state().getBoolean(KEY_AUTO_DISABLED, false)) {
                return;
            }
            state().edit().putBoolean(KEY_AUTO_DISABLED, false).apply();
            if (enabled || SharedConfig.currentProxy == null) {
                return;
            }
            setProxy(main, true);
        }
    }

    // The same steps as Telegram's switch in ProxyListActivity
    private static void setProxy(SharedPreferences main, boolean enabled) {
        main.edit().putBoolean("proxy_enabled", enabled).commit();
        ConnectionsManager.setProxySettings(enabled, SharedConfig.currentProxy.settings);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    // A proxy the user turned on again is theirs: a later change of network does not
    // turn it on a second time. Notifications can arrive late, so the state is read
    // instead of who posted (this class posts "on" only after clearing the mark).
    private static void observe() {
        if (observer != null) {
            return;
        }
        observer = (id, account, args) -> {
            if (id == NotificationCenter.proxySettingsChanged
                    && MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false)) {
                state().edit().putBoolean(KEY_AUTO_DISABLED, false).apply();
            }
        };
        NotificationCenter.getGlobalInstance().addObserver(observer, NotificationCenter.proxySettingsChanged);
    }

    private static SharedPreferences state() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static ConnectivityManager connectivity() {
        return (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
}
