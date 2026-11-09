/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The settings screen of one plugin, drawn by MZGram from what the plugin
 * declares (ui.settings: Header, Divider, Switch, Selector, Input, EditText,
 * Text): mzgram_plugins.settings.settings_json gives the rows as JSON and
 * they become UItems, so the screen looks like the rest of the app. A
 * change goes back through setting_changed_json (the value is kept, then
 * the plugin's on_change runs) and a tap through setting_click_json; after
 * each the rows are read again, since a plugin may show other rows then.
 * A Text row with create_sub_fragment opens its rows as a page of its own.
 *
 * Only a build with -PmzgramPlugins=true has Python; anywhere else the
 * screen says that the settings could not be loaded. Rows with a view of
 * the plugin's own (Custom) are not drawn yet.
 *
 * Based on exteraless plugins/ui/PluginSettingsActivity.java (GPL-3.0):
 * rows from JSON, the selector and input dialogs; written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.plugins.MZGramPythonRuntime;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.HashMap;

public class MZGramPluginSettingsActivity extends UniversalFragment {

    private static final String MODULE = "mzgram_plugins.settings";
    // Keys of a row in the JSON of mzgram_plugins.settings
    private static final String TEXT = "text";
    private static final String VALUE = "value";

    private final String pluginId;
    private final String title;
    // Row ids from the root page down to this one (empty on the root page)
    private final ArrayList<String> path;

    private boolean loading = true;
    private String error;
    private JSONArray rows;
    // Python's row ids to the ids of the list, kept while the screen is open
    private final HashMap<String, Integer> rowNumbers = new HashMap<>();
    private final SparseArray<JSONObject> rowsById = new SparseArray<>();
    // Only the newest read of the rows is shown
    private int loadRequest;

    public MZGramPluginSettingsActivity(String pluginId, String title) {
        this(pluginId, title, new ArrayList<>());
    }

    private MZGramPluginSettingsActivity(String pluginId, String title, ArrayList<String> path) {
        this.pluginId = pluginId;
        this.title = title;
        this.path = path;
    }

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        // A sub page may have changed values shown here
        if (!loading) {
            load();
        }
    }

    @Override
    protected CharSequence getTitle() {
        return title;
    }

    private void load() {
        final int current = ++loadRequest;
        if (!MZGramPythonRuntime.isAvailable()) {
            show(current, null, null);
            return;
        }
        MZGramPythonRuntime.call(MODULE, "settings_json", pluginId, json -> {
            if (json == null) {
                show(current, null, MZGramPythonRuntime.getLastError());
                return;
            }
            try {
                JSONObject reply = new JSONObject(json);
                if (!reply.optBoolean("ok", false)) {
                    show(current, null, reply.optString("error", null));
                    return;
                }
                JSONArray page = pageOf(reply.optJSONArray("rows"));
                if (page == null) {
                    show(current, null, null);
                } else {
                    show(current, page, null);
                }
            } catch (Exception e) {
                show(current, null, e.getMessage());
            }
        });
    }

    // The rows of this page: the root rows, then down the sub pages of path
    private JSONArray pageOf(JSONArray root) {
        JSONArray page = root;
        for (int step = 0; step < path.size() && page != null; step++) {
            JSONArray next = null;
            for (int i = 0; i < page.length(); i++) {
                JSONObject row = page.optJSONObject(i);
                if (row != null && path.get(step).equals(row.optString("row_id"))) {
                    next = row.optJSONArray("sub_page");
                    break;
                }
            }
            page = next;
        }
        return page;
    }

    private void show(int current, JSONArray page, String problem) {
        if (current != loadRequest) {
            return;
        }
        loading = false;
        rows = page;
        // What Python said went wrong, shown under MZGram's own text
        error = page == null && !TextUtils.isEmpty(problem) ? problem : null;
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private int numberOf(JSONObject row) {
        String rowId = row.optString("row_id");
        Integer number = rowNumbers.get(rowId);
        if (number == null) {
            number = rowNumbers.size() + 1;
            rowNumbers.put(rowId, number);
        }
        return number;
    }

    private static String opt(JSONObject row, String key) {
        if (row.isNull(key)) {
            return null;
        }
        String value = row.optString(key, null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    private static boolean isListRow(JSONObject row) {
        if (row == null) {
            return false;
        }
        switch (row.optString("type")) {
            case "switch":
            case "selector":
            case "input":
            case "edittext":
            case "text":
                return true;
            default:
                return false;
        }
    }

    private int iconOf(JSONObject row) {
        String icon = opt(row, "icon");
        if (icon == null || getContext() == null) {
            return 0;
        }
        return getContext().getResources().getIdentifier(icon, "drawable", getContext().getPackageName());
    }

    // The row's title: an EditText has only its hint
    private static String titleOf(JSONObject row) {
        String text = opt(row, TEXT);
        if (text != null) {
            return text;
        }
        String hint = opt(row, "hint");
        return hint != null ? hint : "";
    }

    // The value on the right: the chosen item of a selector, the text of an input
    private static String valueOf(JSONObject row) {
        if ("selector".equals(row.optString("type"))) {
            JSONArray items = row.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return "";
            }
            int selected = row.optInt(VALUE, 0);
            if (selected < 0 || selected >= items.length()) {
                selected = 0;
            }
            return items.optString(selected);
        }
        return row.optString(VALUE, "");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rowsById.clear();
        if (loading) {
            items.add(UItem.asShadow(getString(R.string.MZGramLoading)));
            return;
        }
        if (rows == null) {
            String failed = getString(R.string.MZGramPluginSettingsFailed);
            items.add(UItem.asShadow(error == null ? failed : failed + "\n\n" + error));
            return;
        }
        if (rows.length() == 0) {
            items.add(UItem.asShadow(getString(R.string.MZGramPluginSettingsEmpty)));
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            int id = numberOf(row);
            boolean divider = isListRow(rows.optJSONObject(i + 1));
            UItem item;
            switch (row.optString("type")) {
                case "header":
                    item = UItem.asHeader(id, row.optString(TEXT));
                    break;
                case "divider":
                    item = UItem.asShadow(id, opt(row, TEXT));
                    break;
                case "unsupported":
                    item = UItem.asShadow(id, getString(R.string.MZGramPluginSettingsUnsupported));
                    break;
                case "switch": {
                    item = UItem.asCheck(id, row.optString(TEXT));
                    item.checked = row.optBoolean(VALUE);
                    final String subtext = opt(row, "subtext");
                    if (subtext != null) {
                        final String text = row.optString(TEXT);
                        final UItem checkItem = item;
                        item.onBind(view -> {
                            if (view instanceof TextCheckCell) {
                                ((TextCheckCell) view).setTextAndValueAndCheck(text, subtext, checkItem.checked, true, divider);
                            }
                        });
                    }
                    break;
                }
                case "selector":
                case "input":
                case "edittext":
                case "text": {
                    boolean isText = "text".equals(row.optString("type"));
                    item = UItem.asButton(id, iconOf(row), titleOf(row), isText ? null : valueOf(row));
                    item.red = row.optBoolean("red");
                    item.accent = row.optBoolean("accent");
                    final String subtext = opt(row, "subtext");
                    // Every text row sets its subtitle: the cells are reused
                    item.onBind(view -> {
                        if (view instanceof TextCell) {
                            TextCell cell = (TextCell) view;
                            cell.setSubtitle(subtext);
                            cell.heightDp = subtext != null ? 60 : 50;
                        }
                    });
                    break;
                }
                default:
                    continue;
            }
            rowsById.put(id, row);
            items.add(item);
        }
        // The last section ends with a gap, as a divider row would end it
        if (isListRow(rows.optJSONObject(rows.length() - 1))) {
            items.add(UItem.asShadow(null));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        JSONObject row = item == null ? null : rowsById.get(item.id);
        if (row == null) {
            return;
        }
        String key = opt(row, "key");
        switch (row.optString("type")) {
            case "switch": {
                boolean value = !row.optBoolean(VALUE);
                try {
                    row.put(VALUE, value);
                } catch (Exception ignore) {
                }
                item.checked = value;
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(value);
                }
                changed(key, value);
                break;
            }
            case "selector":
                showSelector(row, key);
                break;
            case "input":
                showInput(row, key, false);
                break;
            case "edittext":
                showInput(row, key, row.optBoolean("multiline"));
                break;
            case "text": {
                JSONArray subPage = row.optJSONArray("sub_page");
                if (subPage != null) {
                    ArrayList<String> next = new ArrayList<>(path);
                    next.add(row.optString("row_id"));
                    presentFragment(new MZGramPluginSettingsActivity(pluginId, row.optString(TEXT), next));
                } else if (row.optBoolean("click")) {
                    clicked(row, false);
                }
                break;
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        JSONObject row = item == null ? null : rowsById.get(item.id);
        if (row == null || !row.optBoolean("long_click")) {
            return false;
        }
        clicked(row, true);
        return true;
    }

    private void changed(String key, Object value) {
        if (key == null) {
            return;
        }
        try {
            JSONObject request = new JSONObject();
            request.put("plugin", pluginId);
            request.put("key", key);
            request.put(VALUE, value);
            MZGramPythonRuntime.call(MODULE, "setting_changed_json", request.toString(), this::replied);
        } catch (Exception e) {
            replied(null);
        }
    }

    private void clicked(JSONObject row, boolean longClick) {
        try {
            JSONObject request = new JSONObject();
            request.put("plugin", pluginId);
            request.put("row", row.optString("row_id"));
            request.put("long", longClick);
            MZGramPythonRuntime.call(MODULE, "setting_click_json", request.toString(), this::replied);
        } catch (Exception e) {
            replied(null);
        }
    }

    // After a change or a tap: a plugin's failure shows, then the rows are read again
    private void replied(String json) {
        boolean ok = false;
        if (json != null) {
            try {
                ok = new JSONObject(json).optBoolean("ok", false);
            } catch (Exception ignore) {
            }
        }
        if (!ok && getParentActivity() != null) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginSettingsActionFailed)).show();
        }
        load();
    }

    private void showSelector(JSONObject row, String key) {
        Activity activity = getParentActivity();
        JSONArray options = row.optJSONArray("items");
        if (activity == null || options == null || options.length() == 0) {
            return;
        }
        int selected = row.optInt(VALUE, 0);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] dialog = new AlertDialog[1];
        for (int i = 0; i < options.length(); i++) {
            final int index = i;
            RadioColorCell cell = new RadioColorCell(activity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(options.optString(i), selected == i);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            cell.setOnClickListener(v -> {
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
                if (index == row.optInt(VALUE, 0)) {
                    return;
                }
                try {
                    row.put(VALUE, index);
                } catch (Exception ignore) {
                }
                if (listView != null) {
                    listView.adapter.update(true);
                }
                changed(key, index);
            });
            content.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(titleOf(row));
        builder.setView(content);
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        dialog[0] = builder.create();
        showDialog(dialog[0]);
    }

    private void showInput(JSONObject row, String key, boolean multiline) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        String subtext = opt(row, "subtext");
        if (subtext != null) {
            TextView description = new TextView(activity);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            description.setText(subtext);
            content.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));
        }

        final EditTextBoldCursor input = new EditTextBoldCursor(activity);
        input.lineYFix = true;
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setText(row.optString(VALUE, ""));
        input.setSelection(input.getText().length());
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        String hint = opt(row, "hint");
        input.setHintText(hint != null ? hint : getString(R.string.MZGramPluginSettingsEnterValue));
        input.setFocusable(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) {
            input.setMinLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input.setSingleLine(true);
        }
        input.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        input.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        input.setBackground(null);
        input.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        int maxLength = row.optInt("max_length", 0);
        if (maxLength > 0) {
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }
        content.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(titleOf(row));
        builder.makeCustomMaxHeight();
        builder.setView(content);
        builder.setWidth(AndroidUtilities.dp(292));
        builder.setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
            String value = input.getText().toString();
            dialog.dismiss();
            if (value.equals(row.optString(VALUE, ""))) {
                return;
            }
            try {
                row.put(VALUE, value);
            } catch (Exception ignore) {
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
            changed(key, value);
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.setDismissDialogByButtons(false);
        dialog.setOnDismissListener(d -> AndroidUtilities.hideKeyboard(input));
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        showDialog(dialog);
    }
}
