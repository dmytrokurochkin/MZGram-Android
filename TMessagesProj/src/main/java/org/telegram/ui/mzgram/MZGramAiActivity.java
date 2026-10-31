/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * "Ask AI": the box and the AI site's window. Not written yet: the tests of
 * MZGramAiWindowTest come first. What the WebView of the device can do is
 * told already, for the logs of the tests.
 */

package org.telegram.ui.mzgram;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.view.View;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;

public class MZGramAiActivity extends BaseFragment {

    public static final int STATE_OPENING = 0;
    public static final int STATE_INSERTED = 1;
    public static final int STATE_NO_FIELD = 2;
    public static final int STATE_NO_PROFILE = 3;
    public static final int STATE_NO_WEBVIEW = 4;

    public MZGramAiActivity(String providerKey, String text) {
    }

    public static MZGramAiActivity forTest(String providerKey, String text, String page) {
        return new MZGramAiActivity(providerKey, text);
    }

    public static void ask(BaseFragment from, String message) {
    }

    public static void open(BaseFragment from, MZGramAiActivity window) {
    }

    public static boolean profileSupported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        } catch (Throwable e) {
            return false;
        }
    }

    public static String webViewState() {
        String name;
        try {
            PackageInfo info = WebViewCompat.getCurrentWebViewPackage(ApplicationLoader.applicationContext);
            name = info == null ? "no WebView" : info.packageName + " " + info.versionName;
        } catch (Throwable e) {
            name = "no WebView (" + e.getClass().getSimpleName() + ")";
        }
        return name + ", profiles " + profileSupported();
    }

    public static void signOut() {
    }

    public int getAiState() {
        return STATE_OPENING;
    }

    public String getProfileName() {
        return null;
    }

    public void evaluateForTest(String script, ValueCallback<String> callback) {
        callback.onReceiveValue(null);
    }

    @Override
    public View createView(Context context) {
        fragmentView = new FrameLayout(context);
        return fragmentView;
    }
}
