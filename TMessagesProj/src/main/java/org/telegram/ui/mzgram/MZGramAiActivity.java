/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * "Ask AI" (message menu, or the selected messages): a box with the
 * messages, their photos and files, the user's own prompt and the AI site,
 * then the site in MZGram's own window. The window's WebView has a profile
 * of its own (MZGramAiRules.PROFILE), so the sign-ins of the AI sites stay
 * apart from Telegram's WebViews. The text is put into the site's input
 * field by MZGramAiRules.insertScript and the files are given to the site's
 * message by the site's own add-files button (MZGramAiFilesSheet); nothing is sent: the user presses
 * Send on the site. The site gets no JavaScript interface of the app.
 *
 * A WebView without profiles (Android System WebView too old) does not
 * open the site: the window says so and the text can still be copied.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramAiRules;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MZGramAiActivity extends BaseFragment {

    // What the window shows under the site (getAiState)
    public static final int STATE_OPENING = 0;
    public static final int STATE_INSERTED = 1;
    public static final int STATE_NO_FIELD = 2;
    public static final int STATE_NO_PROFILE = 3;
    public static final int STATE_NO_WEBVIEW = 4;
    public static final int STATE_DOWNLOADING = 5;
    public static final int STATE_ATTACHED = 6;
    // The site alone, with no message
    public static final int STATE_SITE = 8;
    // The files are downloaded: the site's own add-files button gives them
    public static final int STATE_FILES_READY = 9;
    // The site's field takes one file at a time: some wait for the next press
    public static final int STATE_ONE_BY_ONE = 10;

    // The message text in the box, at most this long
    private static final int PREVIEW_LENGTH = 600;
    private static final int REQUEST_CODE_FILES = 8851;

    // A photo or file of the messages that goes with the text
    public static final class Attachment {
        public final String name;
        public final String mime;
        public final int kind;
        public final long size;
        final MessageObject message;
        File file;

        Attachment(String name, String mime, int kind, long size, MessageObject message, File file) {
            this.name = name;
            this.mime = mime;
            this.kind = kind;
            this.size = size;
            this.message = message;
            this.file = file;
        }

        // A file that is there already (the tests)
        public static Attachment ofFile(String name, String mime, File file) {
            return new Attachment(name, mime, MZGramAiRules.classifyFile(name, mime), file.length(), null, file);
        }
    }

    // The attachments of some messages and what is left out of them
    public static final class Collected {
        public final List<Attachment> attachments = new ArrayList<>();
        public MZGramAiRules.Choice choice = new MZGramAiRules.Choice();

        public boolean any() {
            return !attachments.isEmpty() || choice.leftOut() > 0;
        }
    }

    private final MZGramAiRules.Provider provider;
    private final String text;
    private final List<Attachment> attachments;
    // The tests' page, shown under the site's address instead of the site
    private final String testPage;

    private WebView webView;
    private String profileName;
    private TextView statusView;
    private TextView insertButton;
    private TextView copyButton;
    private TextView saveButton;
    private int state = STATE_OPENING;
    private boolean inserted;
    private boolean attaching;
    // The files not given to the site yet
    private final List<Attachment> remaining = new ArrayList<>();
    private int given;
    private boolean filesReady;
    private MZGramAiFilesSheet filesSheet;
    // What of the messages does not go, said in the chooser
    private String leftOut;
    private int tries;
    private long downloadStarted;
    private String attachedHow;
    private ValueCallback<Uri[]> fileCallback;
    private final Runnable tryInsert = this::tryInsert;
    private final Runnable checkDownloads = this::checkDownloads;

    public MZGramAiActivity(String providerKey, String text) {
        this(providerKey, text, Collections.emptyList(), null);
    }

    public MZGramAiActivity(String providerKey, String text, List<Attachment> attachments) {
        this(providerKey, text, attachments, null);
    }

    private MZGramAiActivity(String providerKey, String text, List<Attachment> attachments, String testPage) {
        this.provider = MZGramAiRules.providerOrDefault(providerKey);
        this.text = text == null ? "" : text;
        this.attachments = attachments == null ? Collections.emptyList() : new ArrayList<>(attachments);
        this.testPage = testPage;
        this.remaining.addAll(this.attachments);
    }

    // The window with the tests' page in place of the site: the same window,
    // profile and scripts, with no network and no sign-in.
    public static MZGramAiActivity forTest(String providerKey, String text, String page) {
        return new MZGramAiActivity(providerKey, text, Collections.emptyList(), page);
    }

    public static MZGramAiActivity forTest(String providerKey, String text, String page, List<Attachment> files) {
        return new MZGramAiActivity(providerKey, text, files, page);
    }

    // The photos and files of the messages, in their order: photos, pictures
    // and documents an AI reads go, at most MAX_ATTACHMENTS; archives,
    // video, GIFs, stickers, voice and music do not.
    public static Collected collect(List<MessageObject> messages) {
        Collected result = new Collected();
        List<Attachment> all = new ArrayList<>();
        List<MZGramAiRules.Candidate> candidates = new ArrayList<>();
        if (messages != null) {
            for (MessageObject message : messages) {
                Attachment attachment = attachmentOf(message);
                if (attachment != null) {
                    all.add(attachment);
                    candidates.add(new MZGramAiRules.Candidate(attachment.kind, attachment.size));
                }
            }
        }
        result.choice = MZGramAiRules.chooseAttachments(candidates);
        for (int index : result.choice.chosen) {
            result.attachments.add(all.get(index));
        }
        return result;
    }

    // Whether the message has a photo or file at all (the menu item is also
    // shown for a message with no text then)
    public static boolean hasAttachment(MessageObject message) {
        return attachmentOf(message) != null;
    }

    private static Attachment attachmentOf(MessageObject message) {
        if (message == null || message.messageOwner == null || message.messageOwner.media == null) {
            return null;
        }
        if (message.isSticker() || message.isAnimatedSticker() || message.isVoice() || message.isRoundVideo()
                || message.isVideo() || message.isGif() || message.isMusic()) {
            return new Attachment("", "", MZGramAiRules.KIND_MEDIA, 0, message, null);
        }
        // View-once media and paid media stay where they are
        if (message.messageOwner.media.ttl_seconds != 0 || message.type == MessageObject.TYPE_PAID_MEDIA) {
            return new Attachment("", "", MZGramAiRules.KIND_UNSUPPORTED, 0, message, null);
        }
        if (message.isPhoto()) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
            if (size == null) {
                return null;
            }
            return new Attachment("photo_" + message.getId() + ".jpg", "image/jpeg", MZGramAiRules.KIND_PHOTO, size.size, message, null);
        }
        TLRPC.Document document = message.getDocument();
        if (document == null) {
            return null;
        }
        String name = FileLoader.getDocumentFileName(document);
        if (TextUtils.isEmpty(name)) {
            name = "file_" + message.getId();
        }
        return new Attachment(name, document.mime_type, MZGramAiRules.classifyFile(name, document.mime_type), document.size, message, null);
    }

    // The message menu's "Ask AI" for a message with only text
    public static void ask(BaseFragment from, String message) {
        ask(from, message, Collections.emptyList());
    }

    // The message menu's "Ask AI" (the messages: the one, its album, or the
    // selected ones): the box, then the warning (once), then the window.
    public static void ask(BaseFragment from, String message, List<MessageObject> messages) {
        Context context = from == null ? null : from.getParentActivity();
        if (context == null) {
            return;
        }
        Collected collected = collect(messages);
        Theme.ResourcesProvider resources = from.getResourceProvider();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        String preview = message == null ? "" : message.trim();
        if (!preview.isEmpty()) {
            layout.addView(header(context, getString(R.string.MZGramAiMessage), resources));
            if (preview.length() > PREVIEW_LENGTH) {
                preview = preview.substring(0, PREVIEW_LENGTH) + "…";
            }
            TextView messageView = grey(context, preview, 15, resources);
            messageView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
            messageView.setMaxLines(6);
            messageView.setEllipsize(TextUtils.TruncateAt.END);
            layout.addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        }
        if (!collected.attachments.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Attachment attachment : collected.attachments) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(attachment.name);
            }
            TextView view = grey(context, formatString(R.string.MZGramAiAttachments, collected.attachments.size(), names.toString()), 14, resources);
            view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
            view.setMaxLines(4);
            view.setEllipsize(TextUtils.TruncateAt.END);
            layout.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        }
        String leftOut = leftOutText(collected.choice);
        if (leftOut != null) {
            layout.addView(grey(context, leftOut, 13, resources), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        }

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
        // No site has a reliable way to be told the model: it is chosen there
        TextView modelView = grey(context, formatString(R.string.MZGramAiModelOnSite, MZGramAiRules.providerOrDefault(chosen[0]).name), 13, resources);
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
        layout.addView(modelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 12));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(context, resources)
                .setTitle(getString(R.string.MZGramAskAi))
                .setView(scroll)
                .setPositiveButton(getString(R.string.MZGramOpen), (d, which) -> {
                    String prompt = promptView.getText() == null ? "" : promptView.getText().toString();
                    MZGramAiActivity window = new MZGramAiActivity(chosen[0], MZGramAiRules.composeText(prompt, message), collected.attachments);
                    window.leftOut = leftOut;
                    open(from, window);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        from.showDialog(dialog);
    }

    // "Not attached: over the limit of 10: 2, archives: 1", or null
    public static String leftOutText(MZGramAiRules.Choice choice) {
        List<String> parts = new ArrayList<>();
        if (choice.overLimit > 0) parts.add(formatString(R.string.MZGramAiLeftOverLimit, choice.overLimit));
        if (choice.tooLarge > 0) parts.add(formatString(R.string.MZGramAiLeftTooLarge, choice.tooLarge));
        if (choice.archives > 0) parts.add(formatString(R.string.MZGramAiLeftArchives, choice.archives));
        if (choice.media > 0) parts.add(formatString(R.string.MZGramAiLeftMedia, choice.media));
        if (choice.unsupported > 0) parts.add(formatString(R.string.MZGramAiLeftOther, choice.unsupported));
        return parts.isEmpty() ? null : formatString(R.string.MZGramAiLeftOut, TextUtils.join("; ", parts));
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

    private static TextView grey(Context context, String value, int size, Theme.ResourcesProvider resources) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources));
        view.setText(value);
        return view;
    }

    // The window, after the warning that the text and the attachments go to
    // a service outside Telegram; the warning comes once, before the first
    // window with a message.
    public static void open(BaseFragment from, MZGramAiActivity window) {
        if (from == null || from.getParentActivity() == null) {
            return;
        }
        if (MZGramConfig.aiWarned || window.emptyRequest()) {
            from.presentFragment(window);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(from.getParentActivity(), from.getResourceProvider())
                .setTitle(getString(R.string.MZGramAiPrivacyTitle))
                .setMessage(formatString(R.string.MZGramAiPrivacyText, window.provider.name) + "\n\n" + getString(R.string.MZGramAiWarningFiles))
                .setPositiveButton(getString(R.string.MZGramContinue), (d, which) -> {
                    MZGramConfig.setAiWarned(true);
                    from.presentFragment(window);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .create();
        from.showDialog(dialog);
    }

    // The AI site with no message: nothing leaves Telegram, so no warning.
    public static void openSite(BaseFragment from, String providerKey) {
        if (from != null) {
            from.presentFragment(new MZGramAiActivity(providerKey, ""));
        }
    }

    // The chat list menu (three dots): a button for each site whose switch
    // is on in Settings > MZGram > AI.
    public static void addMenuButtons(ItemOptions io, BaseFragment fragment) {
        if (io == null || fragment == null) {
            return;
        }
        boolean gap = false;
        for (MZGramAiRules.Provider item : MZGramAiRules.providers()) {
            if (!MZGramAiRules.menuButton(item)) {
                continue;
            }
            if (!gap) {
                io.addGap();
                gap = true;
            }
            io.add(R.drawable.msg_bot, item.name, () -> openSite(fragment, item.key));
        }
    }

    boolean emptyRequest() {
        return text.trim().isEmpty() && attachments.isEmpty();
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

    // How the files went to the page: "input" or "paste", null before
    public String getAttachedHow() {
        return attachedHow;
    }

    public MZGramAiFilesSheet filesSheetForTest() {
        return filesSheet;
    }

    public View webViewForTest() {
        return webView;
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
        copyButton = button(context, getString(R.string.MZGramAiCopy));
        copyButton.setOnClickListener(v -> {
            AndroidUtilities.addToClipboard(text);
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramTextCopied)).show();
        });
        saveButton = button(context, getString(R.string.MZGramAiSaveFiles));
        saveButton.setOnClickListener(v -> saveFiles());
        buttons.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        buttons.addView(insertButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        buttons.addView(copyButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        bar.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fragmentView = layout;
        openSite(context, site);
        if (state == STATE_OPENING) {
            // With the WebView there: Insert text is shown
            setState(emptyRequest() && webView != null ? STATE_SITE : STATE_OPENING);
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
                if (!inserted && !emptyRequest()) {
                    startInserting();
                }
                startAttaching();
            }
        });
        // The site's own "attach" button: the files of the messages first,
        // or Android's file picker
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return chooseFiles(callback, params);
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
                boolean first = !inserted;
                inserted = true;
                // The files hint stays until the files are given
                setState(filesReady && given == 0 ? STATE_FILES_READY : STATE_INSERTED);
                if (first) {
                    startAttaching();
                }
            } else if (status == MZGramAiRules.STATUS_NO_FIELD || ++tries >= MZGramAiRules.TRIES) {
                setState(STATE_NO_FIELD);
            } else {
                // No field yet, or the page is still loading
                AndroidUtilities.runOnUIThread(tryInsert, MZGramAiRules.TRY_INTERVAL_MS);
            }
        });
    }

    // The local file of an attachment, null while it is not downloaded
    private File localFile(Attachment attachment) {
        if (attachment.file != null && attachment.file.exists()) {
            return attachment.file;
        }
        MessageObject message = attachment.message;
        if (message == null) {
            return null;
        }
        List<File> places = new ArrayList<>();
        if (!TextUtils.isEmpty(message.messageOwner.attachPath)) {
            places.add(new File(message.messageOwner.attachPath));
        }
        FileLoader loader = FileLoader.getInstance(message.currentAccount);
        places.add(loader.getPathToMessage(message.messageOwner));
        if (message.getDocument() != null) {
            places.add(loader.getPathToAttach(message.getDocument(), true));
        }
        for (File place : places) {
            if (place != null && place.exists() && place.length() > 0) {
                attachment.file = place;
                return place;
            }
        }
        return null;
    }

    private void download(Attachment attachment) {
        MessageObject message = attachment.message;
        if (message == null) {
            return;
        }
        FileLoader loader = FileLoader.getInstance(message.currentAccount);
        if (attachment.kind == MZGramAiRules.KIND_PHOTO && message.getDocument() == null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
            if (size != null) {
                loader.loadFile(ImageLocation.getForObject(size, message.photoThumbsObject), message, "jpg", FileLoader.PRIORITY_NORMAL, 0);
            }
        } else if (message.getDocument() != null) {
            loader.loadFile(message.getDocument(), message, FileLoader.PRIORITY_NORMAL, 0);
        }
    }

    // The attachments are downloaded where needed; the site's own add-files
    // button then gives them (chooseFiles). Nothing is put into the page by
    // itself: a site may ignore files given that way and show nothing.
    private void startAttaching() {
        if (attachments.isEmpty() || attaching || webView == null) {
            return;
        }
        attaching = true;
        downloadStarted = System.currentTimeMillis();
        for (Attachment attachment : attachments) {
            if (localFile(attachment) == null) {
                download(attachment);
            }
        }
        checkDownloads();
    }

    private void checkDownloads() {
        if (webView == null) {
            return;
        }
        int ready = 0;
        for (Attachment attachment : attachments) {
            if (localFile(attachment) != null) {
                ready++;
            }
        }
        boolean late = System.currentTimeMillis() - downloadStarted > MZGramAiRules.DOWNLOAD_TIMEOUT_MS;
        if (ready < attachments.size() && !late) {
            setState(STATE_DOWNLOADING);
            if (statusView != null) {
                statusView.setText(formatString(R.string.MZGramAiStatusDownloading, ready, attachments.size()));
            }
            AndroidUtilities.runOnUIThread(checkDownloads, 500);
            return;
        }
        filesReady = true;
        if (given == 0) {
            setState(STATE_FILES_READY);
        }
    }

    // The site asks for files (its own add-files button): the files of the
    // messages not given yet, to check and add all at once, or Android's
    // file picker.
    private boolean chooseFiles(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (getParentActivity() == null) {
            return false;
        }
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
        }
        fileCallback = callback;
        if (remaining.isEmpty()) {
            return pickFiles(params);
        }
        boolean multiple = params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        String[] accept = params.getAcceptTypes();
        boolean[] allowed = new boolean[remaining.size()];
        for (int i = 0; i < allowed.length; i++) {
            allowed[i] = MZGramAiRules.accepts(accept, remaining.get(i).name, remaining.get(i).mime);
        }
        List<Attachment> offered = new ArrayList<>(remaining);
        filesSheet = new MZGramAiFilesSheet(getParentActivity(), getResourceProvider(), provider.name, offered, allowed,
                given == 0 ? leftOut : null, this::localFile, new MZGramAiFilesSheet.Listener() {
            @Override
            public void onAdd(List<Attachment> chosen) {
                List<Integer> checked = new ArrayList<>();
                for (Attachment attachment : chosen) {
                    checked.add(offered.indexOf(attachment));
                }
                MZGramAiRules.Give give = MZGramAiRules.give(checked, multiple);
                List<Uri> uris = new ArrayList<>();
                for (int index : give.now) {
                    try {
                        uris.add(FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", localFile(offered.get(index))));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                // What was not checked is not wanted; what waits stays
                remaining.clear();
                for (int index : give.later) {
                    remaining.add(offered.get(index));
                }
                given += uris.size();
                attachedHow = "chooser";
                giveFiles(uris.isEmpty() ? null : uris.toArray(new Uri[0]));
                if (!remaining.isEmpty()) {
                    setState(STATE_ONE_BY_ONE);
                } else if (given > 0) {
                    setState(STATE_ATTACHED);
                }
            }

            @Override
            public void onOther() {
                if (!pickFiles(params)) {
                    giveFiles(null);
                }
            }

            @Override
            public void onCancel() {
                giveFiles(null);
            }
        });
        filesSheet.show();
        return true;
    }

    private boolean pickFiles(WebChromeClient.FileChooserParams params) {
        try {
            Intent intent = params.createIntent();
            if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            startActivityForResult(intent, REQUEST_CODE_FILES);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            giveFiles(null);
            return true;
        }
    }

    private void giveFiles(Uri[] uris) {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(uris);
            fileCallback = null;
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FILES) {
            giveFiles(resultCode == Activity.RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(resultCode, data) : null);
        }
    }

    // Save files: the attachments in Downloads/MZGram AI, to add on the site
    // by hand.
    private void saveFiles() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MZGram AI");
        int saved = 0;
        for (Attachment attachment : attachments) {
            File file = localFile(attachment);
            if (file == null) {
                continue;
            }
            try {
                if (!folder.exists() && !folder.mkdirs()) {
                    break;
                }
                String name = attachment.name.replaceAll("[\\\\/:*?\"<>|]", "_");
                File target = new File(folder, name);
                for (int n = 1; target.exists(); n++) {
                    target = new File(folder, n + "-" + name);
                }
                try (InputStream in = new FileInputStream(file); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                saved++;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (saved > 0) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_download, formatString(R.string.MZGramAiFilesSaved, folder.getAbsolutePath())).show();
        }
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
            case STATE_DOWNLOADING:
                // Set by the caller, with the counts
                break;
            case STATE_FILES_READY:
                statusView.setText(formatString(R.string.MZGramAiStatusPressAdd, remaining.size()));
                break;
            case STATE_ONE_BY_ONE:
                statusView.setText(formatString(R.string.MZGramAiStatusOneByOne, remaining.size()));
                break;
            case STATE_ATTACHED:
                statusView.setText(formatString(R.string.MZGramAiStatusAttached, given));
                break;
            case STATE_SITE:
                statusView.setText(provider.name);
                break;
            default:
                statusView.setText(formatString(R.string.MZGramAiStatusOpening, provider.name));
                break;
        }
        boolean empty = emptyRequest();
        if (insertButton != null) {
            insertButton.setVisibility(webView == null || empty ? View.GONE : View.VISIBLE);
        }
        if (copyButton != null) {
            copyButton.setVisibility(text.trim().isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (saveButton != null) {
            saveButton.setVisibility(attachments.isEmpty() ? View.GONE : View.VISIBLE);
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
        AndroidUtilities.cancelRunOnUIThread(checkDownloads);
        giveFiles(null);
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
