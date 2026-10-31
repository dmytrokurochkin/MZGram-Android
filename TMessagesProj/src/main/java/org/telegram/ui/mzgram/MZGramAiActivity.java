/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * "Ask AI" (message menu): a box with the message, the user's own prompt,
 * the AI site and its model, then the site in MZGram's own window. The
 * window's WebView has a profile of its own (MZGramAiRules.PROFILE), so the
 * sign-ins of the AI sites stay apart from Telegram's WebViews. The text is
 * put into the site's input field by MZGramAiRules.insertScript and never
 * sent: the user presses Send on the site. The site gets no JavaScript
 * interface of the app.
 *
 * A WebView without profiles (Android System WebView too old) does not
 * open the site: the window says so and the text can still be copied.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramAiRules;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

public class MZGramAiActivity extends BaseFragment {

    // What the window shows under the site (getAiState)
    public static final int STATE_OPENING = 0;
    public static final int STATE_INSERTED = 1;
    public static final int STATE_NO_FIELD = 2;
    public static final int STATE_NO_PROFILE = 3;
    public static final int STATE_NO_WEBVIEW = 4;

    // The message text in the box, at most this long
    private static final int PREVIEW_LENGTH = 600;

    private final MZGramAiRules.Provider provider;
    private final String text;
    // The tests' page, shown under the site's address instead of the site
    private final String testPage;

    private WebView webView;
    private String profileName;
    private TextView statusView;
    private TextView insertButton;
    private int state = STATE_OPENING;
    private boolean inserted;
    private int tries;
    private final Runnable tryInsert = this::tryInsert;

    public MZGramAiActivity(String providerKey, String text) {
        this(providerKey, text, null);
    }

    private MZGramAiActivity(String providerKey, String text, String testPage) {
        this.provider = MZGramAiRules.providerOrDefault(providerKey);
        this.text = text == null ? "" : text;
        this.testPage = testPage;
    }

    // The window with the tests' page in place of the site: the same window,
    // profile and script, with no network and no sign-in.
    public static MZGramAiActivity forTest(String providerKey, String text, String page) {
        return new MZGramAiActivity(providerKey, text, page);
    }

    // The message menu's "Ask AI": the box, then the warning (once), then
    // the window.
    public static void ask(BaseFragment from, String message) {
        Context context = from == null ? null : from.getParentActivity();
        if (context == null) {
            return;
        }
        Theme.ResourcesProvider resources = from.getResourceProvider();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(header(context, getString(R.string.MZGramAiMessage), resources));
        TextView messageView = new TextView(context);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        messageView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        messageView.setMaxLines(6);
        messageView.setEllipsize(TextUtils.TruncateAt.END);
        String preview = message == null ? "" : message.trim();
        if (preview.length() > PREVIEW_LENGTH) {
            preview = preview.substring(0, PREVIEW_LENGTH) + "…";
        }
        messageView.setText(preview);
        layout.addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));

        layout.addView(header(context, getString(R.string.MZGramAiPrompt), resources));
        EditTextBoldCursor promptView = new EditTextBoldCursor(context);
        promptView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        promptView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        promptView.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        promptView.setLineColors(
                Theme.getColor(Theme.key_dialogInputField, resources),
                Theme.getColor(Theme.key_dialogInputFieldActivated, resources),
                Theme.getColor(Theme.key_text_RedBold, resources));
        promptView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        promptView.setSingleLine(false);
        promptView.setMaxLines(5);
        promptView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        layout.addView(promptView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));

        layout.addView(header(context, getString(R.string.MZGramAiProvider), resources));
        final String[] chosen = {MZGramAiRules.defaultProvider().key};
        TextView modelView = new TextView(context);
        RadioColorCell[] cells = new RadioColorCell[MZGramAiRules.providers().size()];
        for (int i = 0; i < cells.length; i++) {
            MZGramAiRules.Provider item = MZGramAiRules.providers().get(i);
            RadioColorCell cell = new RadioColorCell(context, resources);
            cell.setCheckColor(Theme.getColor(Theme.key_dialogRadioBackground, resources), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resources));
            cell.setTextAndValue(item.name, item.key.equals(chosen[0]));
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), Theme.RIPPLE_MASK_ALL));
            cell.setOnClickListener(v -> {
                chosen[0] = item.key;
                for (int j = 0; j < cells.length; j++) {
                    cells[j].setChecked(cells[j] == v, true);
                }
                modelView.setText(formatString(R.string.MZGramAiModelOnSite, item.name));
            });
            cells[i] = cell;
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // No site has a reliable way to be told the model: it is chosen there
        layout.addView(header(context, getString(R.string.MZGramAiModel), resources));
        modelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        modelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources));
        modelView.setText(formatString(R.string.MZGramAiModelOnSite, MZGramAiRules.providerOrDefault(chosen[0]).name));
        layout.addView(modelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 12));

        TextView noteView = new TextView(context);
        noteView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        noteView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources));
        noteView.setText(getString(R.string.MZGramAiPrivacyNote));
        layout.addView(noteView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(context, resources)
                .setTitle(getString(R.string.MZGramAskAi))
                .setView(scroll)
                .setPositiveButton(getString(R.string.MZGramOpen), (d, which) -> {
                    String prompt = promptView.getText() == null ? "" : promptView.getText().toString();
                    open(from, new MZGramAiActivity(chosen[0], MZGramAiRules.composeText(prompt, message)));
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        from.showDialog(dialog);
    }

    private static TextView header(Context context, String title, Theme.ResourcesProvider resources) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resources));
        view.setText(title);
        view.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        view.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), AndroidUtilities.dp(6));
        return view;
    }

    // The window, after the warning that the text goes to a service outside
    // Telegram; the warning comes once, before the first window.
    public static void open(BaseFragment from, MZGramAiActivity window) {
        if (from == null || from.getParentActivity() == null) {
            return;
        }
        if (MZGramConfig.aiWarned) {
            from.presentFragment(window);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(from.getParentActivity(), from.getResourceProvider())
                .setTitle(getString(R.string.MZGramAiPrivacyTitle))
                .setMessage(formatString(R.string.MZGramAiPrivacyText, window.provider.name))
                .setPositiveButton(getString(R.string.MZGramContinue), (d, which) -> {
                    MZGramConfig.setAiWarned(true);
                    from.presentFragment(window);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        from.showDialog(dialog);
    }

    // Profiles need a recent Android System WebView (androidx.webkit MULTI_PROFILE).
    public static boolean profileSupported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        } catch (Throwable e) {
            return false;
        }
    }

    // The WebView of this device, for the logs of the tests.
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

    // Settings > MZGram > AI > "Sign out of AI sites": the cookies and site
    // data of the AI profile, then the profile itself when no window uses it.
    public static void signOut() {
        if (!profileSupported()) {
            return;
        }
        try {
            ProfileStore store = ProfileStore.getInstance();
            Profile profile = store.getProfile(MZGramAiRules.PROFILE);
            if (profile == null) {
                return;
            }
            profile.getCookieManager().removeAllCookies(null);
            profile.getCookieManager().flush();
            profile.getWebStorage().deleteAllData();
            try {
                store.deleteProfile(MZGramAiRules.PROFILE);
            } catch (IllegalStateException e) {
                // A window still uses it; its data is gone already
                FileLog.e(e);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public int getAiState() {
        return state;
    }

    // The profile the window's WebView has, null with none
    public String getProfileName() {
        return profileName;
    }

    // Runs a script in the window's page (the tests read the page with it)
    public void evaluateForTest(String script, ValueCallback<String> callback) {
        if (webView == null) {
            callback.onReceiveValue(null);
            return;
        }
        webView.evaluateJavascript(script, callback);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(provider.name);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()));
        FrameLayout site = new FrameLayout(context);
        layout.addView(site, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider()));
        bar.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
        bar.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        insertButton = button(context, getString(R.string.MZGramAiInsert));
        insertButton.setOnClickListener(v -> startInserting());
        TextView copyButton = button(context, getString(R.string.MZGramAiCopy));
        copyButton.setOnClickListener(v -> {
            AndroidUtilities.addToClipboard(text);
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramTextCopied)).show();
        });
        buttons.addView(insertButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        buttons.addView(copyButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        bar.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fragmentView = layout;
        openSite(context, site);
        if (state == STATE_OPENING) {
            // With the WebView there: Insert text is shown
            setState(STATE_OPENING);
        }
        return fragmentView;
    }

    private TextView button(Context context, String title) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, getResourceProvider()));
        view.setGravity(Gravity.CENTER);
        view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, getResourceProvider()), Theme.RIPPLE_MASK_ALL));
        view.setText(title);
        return view;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openSite(Context context, FrameLayout site) {
        if (!profileSupported()) {
            setState(STATE_NO_PROFILE);
            return;
        }
        WebView view;
        try {
            view = new WebView(context);
        } catch (Throwable e) {
            FileLog.e(e);
            setState(STATE_NO_WEBVIEW);
            return;
        }
        try {
            // Before anything else is done with the WebView
            ProfileStore.getInstance().getOrCreateProfile(MZGramAiRules.PROFILE);
            WebViewCompat.setProfile(view, MZGramAiRules.PROFILE);
            profileName = WebViewCompat.getProfile(view).getName();
        } catch (Throwable e) {
            FileLog.e(e);
            view.destroy();
            setState(STATE_NO_PROFILE);
            return;
        }
        if (!MZGramAiRules.PROFILE.equals(profileName)) {
            view.destroy();
            setState(STATE_NO_PROFILE);
            return;
        }
        webView = view;
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // A link to a new window opens here, where the rules below see it
        settings.setSupportMultipleWindows(false);
        try {
            Profile profile = WebViewCompat.getProfile(view);
            profile.getCookieManager().setAcceptCookie(true);
            profile.getCookieManager().setAcceptThirdPartyCookies(view, true);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return leaves(request.getUrl().toString(), request.isForMainFrame() && request.hasGesture() && !request.isRedirect());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return leaves(url, false);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!inserted) {
                    startInserting();
                }
            }
        });
        site.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (testPage != null) {
            view.loadDataWithBaseURL(provider.startUrl, testPage, "text/html", "UTF-8", null);
        } else {
            view.loadUrl(provider.startUrl);
        }
    }

    // Whether a page the site goes to is kept out of the window: only https
    // pages load here, and a link the user taps to another site opens in the
    // browser (the AI site itself and its sign-in pages stay here).
    private boolean leaves(String url, boolean tapped) {
        if (!MZGramAiRules.allowedNavigation(url)) {
            return true;
        }
        if (tapped && MZGramAiRules.newWindowTarget(provider, url) == MZGramAiRules.NEW_WINDOW_BROWSER) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ApplicationLoader.applicationContext.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return true;
        }
        return false;
    }

    private void startInserting() {
        if (webView == null) {
            return;
        }
        tries = 0;
        AndroidUtilities.cancelRunOnUIThread(tryInsert);
        tryInsert();
    }

    private void tryInsert() {
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript(MZGramAiRules.insertScript(provider, text), value -> {
            if (webView == null) {
                return;
            }
            int status = MZGramAiRules.parseStatus(value);
            if (status == MZGramAiRules.STATUS_INSERTED) {
                inserted = true;
                setState(STATE_INSERTED);
            } else if (status == MZGramAiRules.STATUS_NO_FIELD || ++tries >= MZGramAiRules.TRIES) {
                setState(STATE_NO_FIELD);
            } else {
                // No field yet, or the page is still loading
                AndroidUtilities.runOnUIThread(tryInsert, MZGramAiRules.TRY_INTERVAL_MS);
            }
        });
    }

    private void setState(int value) {
        state = value;
        if (statusView == null) {
            return;
        }
        switch (value) {
            case STATE_INSERTED:
                statusView.setText(getString(R.string.MZGramAiStatusInserted));
                break;
            case STATE_NO_FIELD:
                statusView.setText(getString(R.string.MZGramAiStatusNoField));
                break;
            case STATE_NO_PROFILE:
                statusView.setText(getString(R.string.MZGramAiNoProfile));
                break;
            case STATE_NO_WEBVIEW:
                statusView.setText(getString(R.string.MZGramAiNoWebView));
                break;
            default:
                statusView.setText(formatString(R.string.MZGramAiStatusOpening, provider.name));
                break;
        }
        if (insertButton != null) {
            insertButton.setVisibility(webView == null ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (webView != null && webView.canGoBack()) {
            if (invoked) {
                webView.goBack();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        // Sideways swipes belong to the site
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(tryInsert);
        if (webView != null) {
            WebView view = webView;
            webView = null;
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            view.destroy();
        }
        super.onFragmentDestroy();
    }
}
