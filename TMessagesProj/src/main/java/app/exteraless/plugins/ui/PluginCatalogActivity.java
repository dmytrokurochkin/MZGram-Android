package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import app.exteraless.plugins.Plugin;
import app.exteraless.plugins.PluginInstallHelper;
import app.exteraless.plugins.PluginsController;

/**
 * MZGram plugin catalog. The list is the index.json of the MZGram-Plugins repository on GitHub
 * (raw.githubusercontent.com, see third-party-connections.md). A plugin is downloaded only when
 * the person taps Install, its size and SHA-256 are checked against the index, and then it goes
 * through the usual install sheet that shows what the plugin may access.
 */
public class PluginCatalogActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final String REPO_URL = "https://github.com/dmytrokurochkin/MZGram-Plugins";
    private static final String RAW_BASE = "https://raw.githubusercontent.com/dmytrokurochkin/MZGram-Plugins/main/";
    private static final String INDEX_URL = RAW_BASE + "index.json";
    private static final String CACHE_NAME = "mzgram_plugin_catalog.json";
    private static final int MAX_INDEX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PLUGIN_BYTES = 32 * 1024 * 1024;

    // Entries that name other apps, link to channels or sites, or name people are left out:
    // nothing in MZGram's interface points to other projects or their authors.
    private static final Pattern OTHER_PROJECTS = Pattern.compile(
            "extera|экстер|екстер|ayu|аюграм|neko|nagram|некограм|cherrygram|octogram|telegram ?x\\b"
                    + "|@[A-Za-z0-9_]{3,}|t\\.me/|https?://",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final int MENU_SEARCH = 1;
    private static final int MENU_OTHER = 2;
    private static final int MENU_GITHUB = 3;

    private static final int STATE_INSTALL = 0;
    private static final int STATE_UPDATE = 1;
    private static final int STATE_INSTALLED = 2;

    static final class Entry {
        String id;
        String name;
        String version;
        String file;
        String sha256;
        long size;
        String description;
        final Map<String, String> descriptions = new HashMap<>();
    }

    private final ArrayList<Entry> all = new ArrayList<>();
    private final ArrayList<Entry> shown = new ArrayList<>();
    private String query = "";
    private boolean loading;
    private boolean failed;
    private boolean downloading;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private FrameLayout statusView;
    private RadialProgressView progressView;
    private TextView statusText;
    private TextView retryButton;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pluginsUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pluginsUpdated && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.MZPluginCatalog));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_GITHUB) {
                    Browser.openUrl(getParentActivity(), REPO_URL);
                }
            }
        });
        ActionBarMenuItem search = actionBar.createMenu().addItem(MENU_SEARCH, R.drawable.ic_ab_search_solar)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        setQuery("");
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        setQuery(editText.getText().toString());
                    }
                });
        search.setSearchFieldHint(getString(R.string.Search));
        ActionBarMenuItem other = actionBar.createMenu().addItem(MENU_OTHER, R.drawable.ic_ab_other);
        other.addSubItem(MENU_GITHUB, R.drawable.msg_language, getString(R.string.MZPluginsOnGitHub));

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof CatalogCell) {
                Entry entry = ((CatalogCell) view).entry;
                if (entry != null && stateOf(entry) != STATE_INSTALLED) {
                    install(entry);
                }
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        statusView = new FrameLayout(context);
        LinearLayout statusColumn = new LinearLayout(context);
        statusColumn.setOrientation(LinearLayout.VERTICAL);
        statusColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        progressView = new RadialProgressView(context);
        progressView.setSize(dp(32));
        statusColumn.addView(progressView, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL));
        statusText = new TextView(context);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        statusText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusText.setGravity(Gravity.CENTER);
        statusColumn.addView(statusText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 12, 32, 0));
        retryButton = new TextView(context);
        retryButton.setText(getString(R.string.MZPluginCatalogRetry));
        retryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        retryButton.setTypeface(AndroidUtilities.bold());
        retryButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        retryButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16),
                Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        retryButton.setPadding(dp(20), dp(8), dp(20), dp(8));
        retryButton.setOnClickListener(v -> loadIndex());
        statusColumn.addView(retryButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
        statusView.addView(statusColumn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        contentView.addView(statusView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = contentView;
        loadIndex();
        return fragmentView;
    }

    private void setQuery(String text) {
        query = text == null ? "" : text.trim();
        applyFilter();
    }

    private void loadIndex() {
        if (loading) {
            return;
        }
        loading = true;
        failed = false;
        updateStatus();
        Utilities.globalQueue.postRunnable(() -> {
            File cache = new File(ApplicationLoader.getFilesDirFixed(), CACHE_NAME);
            if (all.isEmpty() && cache.exists()) {
                try {
                    ArrayList<Entry> cached = parse(new String(readAll(new java.io.FileInputStream(cache), MAX_INDEX_BYTES), StandardCharsets.UTF_8));
                    AndroidUtilities.runOnUIThread(() -> setEntries(cached, false));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            ArrayList<Entry> fresh = null;
            try {
                byte[] data = download(INDEX_URL, MAX_INDEX_BYTES, null);
                fresh = parse(new String(data, StandardCharsets.UTF_8));
                try (OutputStream out = new FileOutputStream(cache)) {
                    out.write(data);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            ArrayList<Entry> result = fresh;
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (result != null) {
                    setEntries(result, true);
                } else {
                    failed = all.isEmpty();
                    updateStatus();
                }
            });
        });
    }

    private void setEntries(ArrayList<Entry> entries, boolean fromNetwork) {
        all.clear();
        all.addAll(entries);
        if (fromNetwork) {
            failed = false;
        }
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT);
        for (Entry entry : all) {
            if (q.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(q)
                    || localizedDescription(entry).toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(entry);
            }
        }
        if (actionBar != null) {
            actionBar.setSubtitle(all.isEmpty() ? null : LocaleController.formatPluralString("MZPluginCatalogCount", all.size()));
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateStatus();
    }

    private void updateStatus() {
        if (statusView == null) {
            return;
        }
        boolean empty = all.isEmpty();
        statusView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) {
            return;
        }
        boolean showError = failed && !loading;
        progressView.setVisibility(showError ? View.GONE : View.VISIBLE);
        statusText.setText(showError ? getString(R.string.MZPluginCatalogError) : "");
        statusText.setVisibility(showError ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(showError ? View.VISIBLE : View.GONE);
    }

    static ArrayList<Entry> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray plugins = root.getJSONArray("plugins");
        ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject p = plugins.optJSONObject(i);
            if (p == null) {
                continue;
            }
            Entry entry = new Entry();
            entry.id = p.optString("id", "");
            entry.name = p.optString("name", entry.id);
            entry.version = p.optString("version", "");
            entry.file = p.optString("file", "");
            entry.sha256 = p.optString("sha256", "");
            entry.size = p.optLong("size", 0);
            entry.description = p.optString("description", "");
            JSONObject i18n = p.optJSONObject("description_i18n");
            if (i18n != null) {
                for (java.util.Iterator<String> it = i18n.keys(); it.hasNext(); ) {
                    String lang = it.next();
                    entry.descriptions.put(lang.toLowerCase(Locale.ROOT), i18n.optString(lang, ""));
                }
            }
            if (TextUtils.isEmpty(entry.id) || TextUtils.isEmpty(entry.file) || entry.sha256.length() != 64
                    || entry.file.contains("..") || namesOtherProjects(entry)) {
                continue;
            }
            entries.add(entry);
        }
        Collections.sort(entries, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return entries;
    }

    static boolean namesOtherProjects(Entry entry) {
        if (OTHER_PROJECTS.matcher(entry.name).find() || OTHER_PROJECTS.matcher(entry.description).find()) {
            return true;
        }
        for (String text : entry.descriptions.values()) {
            if (OTHER_PROJECTS.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static String localizedDescription(Entry entry) {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale != null) {
            String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
            String country = locale.getCountry().toLowerCase(Locale.ROOT);
            String text = entry.descriptions.get(lang + "-" + country);
            if (TextUtils.isEmpty(text)) {
                text = entry.descriptions.get(lang);
            }
            if (!TextUtils.isEmpty(text)) {
                return text;
            }
        }
        return entry.description;
    }

    private static int stateOf(Entry entry) {
        Plugin installed = PluginsController.getInstance().getPlugin(entry.id);
        if (installed == null) {
            return STATE_INSTALL;
        }
        return compareVersions(entry.version, installed.version) > 0 ? STATE_UPDATE : STATE_INSTALLED;
    }

    static int compareVersions(String a, String b) {
        String[] x = (a == null ? "" : a).split("[^0-9]+");
        String[] y = (b == null ? "" : b).split("[^0-9]+");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.length && !x[i].isEmpty() ? parseLong(x[i]) : 0;
            long q = i < y.length && !y[i].isEmpty() ? parseLong(y[i]) : 0;
            if (p != q) {
                return p > q ? 1 : -1;
            }
        }
        return 0;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.length() > 18 ? s.substring(0, 18) : s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void install(Entry entry) {
        Activity activity = getParentActivity();
        if (activity == null || downloading) {
            return;
        }
        downloading = true;
        AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        showDialog(progress);
        Utilities.globalQueue.postRunnable(() -> {
            File file = null;
            int error = 0;
            try {
                File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "mzgram_plugin_catalog");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("cannot create " + dir);
                }
                File target = new File(dir, entry.id.replaceAll("[^A-Za-z0-9_.-]", "_") + ".plugin");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                int limit = entry.size > 0 ? (int) Math.min(MAX_PLUGIN_BYTES, entry.size) : MAX_PLUGIN_BYTES;
                byte[] data = download(fileUrl(entry.file), limit, digest);
                String hash = toHex(digest.digest());
                if ((entry.size > 0 && data.length != entry.size) || !hash.equalsIgnoreCase(entry.sha256)) {
                    error = R.string.MZPluginCatalogBadFile;
                } else {
                    try (OutputStream out = new FileOutputStream(target)) {
                        out.write(data);
                    }
                    file = target;
                }
            } catch (Exception e) {
                FileLog.e(e);
                error = R.string.MZPluginCatalogDownloadError;
            }
            File result = file;
            int errorText = error;
            AndroidUtilities.runOnUIThread(() -> {
                downloading = false;
                progress.dismiss();
                Activity current = getParentActivity();
                if (result != null && current != null) {
                    PluginInstallHelper.confirmAndInstall(current, result);
                } else if (errorText != 0) {
                    BulletinFactory.of(this).createErrorBulletin(getString(errorText)).show();
                }
            });
        });
    }

    private static String fileUrl(String path) {
        StringBuilder url = new StringBuilder(RAW_BASE);
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                url.append('/');
            }
            url.append(Uri.encode(parts[i]));
        }
        return url.toString();
    }

    private static byte[] download(String address, int maxBytes, MessageDigest digest) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + code + " for " + address);
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] data = readAll(in, maxBytes);
                if (digest != null) {
                    digest.update(data);
                }
                return data;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws Exception {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > maxBytes) {
                    throw new IllegalStateException("too large");
                }
            }
            return out.toByteArray();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private static final int TYPE_ENTRY = 0;
        private static final int TYPE_INFO = 1;
        private static final int TYPE_EMPTY = 2;

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            if (all.isEmpty()) {
                return 0;
            }
            return shown.isEmpty() ? 1 : shown.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (shown.isEmpty()) {
                return TYPE_EMPTY;
            }
            return position < shown.size() ? TYPE_ENTRY : TYPE_INFO;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_ENTRY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_ENTRY) {
                view = new CatalogCell(context);
            } else {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
                if (viewType == TYPE_EMPTY) {
                    cell.setTextGravity(Gravity.CENTER_HORIZONTAL);
                }
                view = cell;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = holder.getItemViewType();
            if (type == TYPE_ENTRY) {
                Entry entry = shown.get(position);
                ((CatalogCell) holder.itemView).bind(entry, stateOf(entry), position != shown.size() - 1);
            } else if (type == TYPE_INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.MZPluginCatalogInfo));
            } else {
                ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.PluginsNotFound));
            }
        }
    }

    private class CatalogCell extends FrameLayout {

        Entry entry;
        private final TextView nameView;
        private final TextView versionView;
        private final TextView descriptionView;
        private final TextView actionView;
        private boolean divider;

        CatalogCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setWillNotDraw(false);

            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(nameView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            versionView = new TextView(context);
            versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            versionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            versionView.setSingleLine(true);
            titleRow.addView(versionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0, 0));
            column.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            descriptionView = new TextView(context);
            descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descriptionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            descriptionView.setMaxLines(3);
            descriptionView.setEllipsize(TextUtils.TruncateAt.END);
            descriptionView.setLineSpacing(dp(1), 1f);
            column.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            actionView = new TextView(context);
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            actionView.setTypeface(AndroidUtilities.bold());
            actionView.setGravity(Gravity.CENTER);
            actionView.setPadding(dp(14), 0, dp(14), 0);
            actionView.setSingleLine(true);
            actionView.setOnClickListener(v -> {
                if (entry != null && stateOf(entry) != STATE_INSTALLED) {
                    install(entry);
                }
            });

            boolean rtl = LocaleController.isRTL;
            addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP | (rtl ? Gravity.RIGHT : Gravity.LEFT), rtl ? 112 : 21, 12, rtl ? 21 : 112, 12));
            addView(actionView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32,
                    Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT), 16, 0, 16, 0));
        }

        void bind(Entry entry, int state, boolean divider) {
            this.entry = entry;
            this.divider = divider;
            nameView.setText(entry.name);
            versionView.setText(TextUtils.isEmpty(entry.version) ? "" : entry.version);
            String description = localizedDescription(entry);
            descriptionView.setText(description);
            descriptionView.setVisibility(TextUtils.isEmpty(description) ? GONE : VISIBLE);
            if (state == STATE_INSTALLED) {
                actionView.setText(getString(R.string.MZPluginCatalogInstalled));
                actionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                actionView.setBackground(null);
                actionView.setClickable(false);
            } else {
                actionView.setText(getString(state == STATE_UPDATE ? R.string.MZPluginCatalogUpdate : R.string.MZPluginCatalogInstall));
                actionView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                actionView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16),
                        Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
                actionView.setClickable(true);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (divider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : dp(21), getMeasuredHeight() - 1,
                        getMeasuredWidth() - (LocaleController.isRTL ? dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }
}
