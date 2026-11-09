/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Pill: the proxy. Shows whether Telegram's proxy is on and connected; a
 * tap turns the chosen proxy on or off (the same as the switch in
 * Telegram's proxy settings), or opens the proxy settings when no proxy
 * is chosen yet. The options lead to the proxy settings.
 *
 * Idea from exteraless ProxyPill (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram.pills;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProxyListActivity;

@SuppressLint("ViewConstructor")
public class MZGramProxyPill extends MZGramPill {

    private static final int OFF = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED = 2;

    private int state = -1;

    public MZGramProxyPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setIcon(R.drawable.outline_shield_plain_24);
        update(false);
    }

    @Override
    public int pillId() {
        return MZGramPills.PROXY;
    }

    @Override
    public long refreshInterval() {
        return 2000;
    }

    private static boolean enabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) && SharedConfig.currentProxy != null;
    }

    @Override
    protected int accentColor() {
        if (state == CONNECTED) {
            return Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, resourcesProvider);
        }
        return super.accentColor();
    }

    @Override
    public void update(boolean animated) {
        int now = OFF;
        if (enabled()) {
            int connection = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
            boolean connected = connection == ConnectionsManager.ConnectionStateConnected || connection == ConnectionsManager.ConnectionStateUpdating;
            now = connected ? CONNECTED : CONNECTING;
        }
        if (now != state) {
            state = now;
            updateColors();
        }
        int text = state == CONNECTED ? R.string.MZGramPillProxyOn : state == CONNECTING ? R.string.MZGramPillProxyConnecting : R.string.MZGramPillProxyOff;
        setText(LocaleController.getString(text), animated);
    }

    @Override
    public void onTap() {
        if (SharedConfig.currentProxy == null || SharedConfig.proxyList.isEmpty()) {
            openSettings();
            return;
        }
        // As the "Use proxy" switch of ProxyListActivity does
        boolean enable = !enabled();
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", enable);
        editor.commit();
        ConnectionsManager.setProxySettings(enable, SharedConfig.currentProxy.settings);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        update(true);
    }

    private void openSettings() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            fragment.presentFragment(new ProxyListActivity());
        }
    }

    @Override
    protected void addOptions(ItemOptions options, BaseFragment fragment) {
        options.add(R.drawable.outline_shield_plain_24, LocaleController.getString(R.string.ProxySettings), // Telegram's own text: "Proxy Settings"
                () -> fragment.presentFragment(new ProxyListActivity()));
    }
}
