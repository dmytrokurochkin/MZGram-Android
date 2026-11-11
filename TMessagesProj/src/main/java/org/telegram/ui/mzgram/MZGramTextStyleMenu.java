package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * MZGram: own order and hidden styles in the formatting menu of the input
 * field (text selection → Bold, Italic, ...). While nothing is customised the
 * menu stays exactly as in Telegram. The editor dialog is ported from
 * exteraGram-less TextStyleDialog (GPL-3.0, app/exteraless/chats/TextStyleDialog.java);
 * applying the order to the menu is MZGram's own.
 */
public final class MZGramTextStyleMenu {

    /** Keys and menu ids in Telegram's own order. */
    static final String[] KEYS = {"quote", "spoiler", "bold", "italic", "mono", "code", "mention", "strike", "underline", "link", "date", "regular"};
    static final int[] IDS = {R.id.menu_quote, R.id.menu_spoiler, R.id.menu_bold, R.id.menu_italic, R.id.menu_mono,
        R.id.menu_code, R.id.menu_mention, R.id.menu_strike, R.id.menu_underline, R.id.menu_link, R.id.menu_date, R.id.menu_regular};

    private MZGramTextStyleMenu() {
    }

    /** True when the user changed the order or hid something. */
    public static boolean isCustomised() {
        return !TextUtils.isEmpty(MZGramConfig.textStyleOrder) || !TextUtils.isEmpty(MZGramConfig.textStyleHidden);
    }

    /** Styles MZGram adds to Telegram's menu: off until switched on in this menu. */
    static final String[] ADDED = {"code", "mention"};

    /** Whether an added style (code block, mention) is in the menu; without a saved menu it is not, as in Telegram. */
    public static boolean shows(String key) {
        return isCustomised() && !hiddenKeys(MZGramConfig.textStyleHidden).contains(key);
    }

    /** The hidden styles as the dialog shows them: without a saved menu the added ones are off. */
    static HashSet<String> hiddenNow() {
        HashSet<String> hidden = hiddenKeys(MZGramConfig.textStyleHidden);
        if (!isCustomised()) {
            hidden.addAll(Arrays.asList(ADDED));
        }
        return hidden;
    }

    /** All keys in the saved order; unknown keys are dropped, missing ones go to the end. */
    static ArrayList<String> orderedKeys(String order) {
        ArrayList<String> result = new ArrayList<>();
        if (!TextUtils.isEmpty(order)) {
            for (String key : order.split(",")) {
                if (Arrays.asList(KEYS).contains(key) && !result.contains(key)) {
                    result.add(key);
                }
            }
        }
        for (String key : KEYS) {
            if (!result.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    static HashSet<String> hiddenKeys(String hidden) {
        HashSet<String> result = new HashSet<>();
        if (!TextUtils.isEmpty(hidden)) {
            result.addAll(Arrays.asList(hidden.split(",")));
        }
        return result;
    }

    private static int idFor(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return IDS[i];
            }
        }
        return 0;
    }

    /**
     * Re-adds the styles that the menu already has in the saved order and hides
     * the switched-off ones. Hidden items stay in the menu (invisible), so the
     * "menu already filled" check of ChatActivity keeps working. Styles that
     * this field does not offer (for example Quote outside chats) stay absent.
     */
    public static void apply(Menu menu) {
        if (menu == null || !isCustomised()) {
            return;
        }
        int group = R.id.menu_groupbolditalic;
        int order = Integer.MAX_VALUE;
        ArrayList<String> present = new ArrayList<>();
        ArrayList<CharSequence> titles = new ArrayList<>();
        for (String key : KEYS) {
            MenuItem item = menu.findItem(idFor(key));
            if (item != null) {
                order = Math.min(order, item.getOrder());
                present.add(key);
                titles.add(item.getTitle());
            }
        }
        if (present.isEmpty()) {
            return;
        }
        for (String key : present) {
            menu.removeItem(idFor(key));
        }
        HashSet<String> hidden = hiddenKeys(MZGramConfig.textStyleHidden);
        for (String key : orderedKeys(MZGramConfig.textStyleOrder)) {
            int index = present.indexOf(key);
            if (index < 0) {
                continue;
            }
            MenuItem item = menu.add(group, idFor(key), order++, titles.get(index));
            item.setVisible(!hidden.contains(key));
        }
    }

    private static CharSequence title(String key) {
        switch (key) {
            case "quote":
                return getString(R.string.Quote);  // Telegram's own text: the name of Telegram's formatting menu item
            case "spoiler":
                return getString(R.string.Spoiler);  // Telegram's own text: the name of Telegram's formatting menu item
            case "bold":
                return styled(getString(R.string.Bold), new TypefaceSpan(AndroidUtilities.bold()));  // Telegram's own text: the name of Telegram's formatting menu item
            case "italic":
                return styled(getString(R.string.Italic), new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")));  // Telegram's own text: the name of Telegram's formatting menu item
            case "mono":
                return styled(getString(R.string.Mono), new TypefaceSpan(Typeface.MONOSPACE));  // Telegram's own text: the name of Telegram's formatting menu item
            case "code":
                return getString(R.string.MZGramMenuCode);
            case "mention":
                return getString(R.string.MZGramMenuMention);
            case "strike":
                return styled(getString(R.string.Strike), styleSpan(TextStyleSpan.FLAG_STYLE_STRIKE));  // Telegram's own text: the name of Telegram's formatting menu item
            case "underline":
                return styled(getString(R.string.Underline), styleSpan(TextStyleSpan.FLAG_STYLE_UNDERLINE));  // Telegram's own text: the name of Telegram's formatting menu item
            case "link":
                return getString(R.string.CreateLink);  // Telegram's own text: the name of Telegram's formatting menu item
            case "date":
                return getString(R.string.FormattedDate);  // Telegram's own text: the name of Telegram's formatting menu item
            default:
                return getString(R.string.Regular);  // Telegram's own text: the name of Telegram's formatting menu item
        }
    }

    private static CharSequence styled(String text, Object span) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        builder.setSpan(span, 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private static TextStyleSpan styleSpan(int flag) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= flag;
        return new TextStyleSpan(run);
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextCheckCell cell;

        Holder(TextCheckCell cell) {
            super(cell);
            this.cell = cell;
        }
    }

    /** Dialog: switch styles on or off, drag to reorder; saved only on OK. */
    @SuppressLint("NotifyDataSetChanged")
    public static AlertDialog createDialog(Context context) {
        final ArrayList<String> ordered = orderedKeys(MZGramConfig.textStyleOrder);
        final HashSet<String> hidden = hiddenNow();

        RecyclerView listView = new RecyclerView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        RecyclerView.Adapter<Holder> adapter = new RecyclerView.Adapter<Holder>() {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextCheckCell cell = new TextCheckCell(context);
                cell.setBackground(Theme.getSelectorDrawable(false));
                return new Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                String key = ordered.get(position);
                holder.cell.setTextAndCheck(title(key), !hidden.contains(key), false);
                holder.cell.setOnClickListener(v -> {
                    int current = holder.getAdapterPosition();
                    if (current < 0 || current >= ordered.size()) {
                        return;
                    }
                    String currentKey = ordered.get(current);
                    if (!hidden.remove(currentKey)) {
                        hidden.add(currentKey);
                    }
                    holder.cell.setChecked(!hidden.contains(currentKey));
                });
            }

            @Override
            public int getItemCount() {
                return ordered.size();
            }
        };
        listView.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from < 0 || to < 0) {
                    return false;
                }
                ordered.add(to, ordered.remove(from));
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        }).attachToRecyclerView(listView);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.MZGramTextStyleMenu));
        builder.setView(listView);
        builder.setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
            boolean defaultOrder = ordered.equals(Arrays.asList(KEYS));
            ArrayList<String> hiddenList = new ArrayList<>();
            for (String key : ordered) {
                if (hidden.contains(key)) {
                    hiddenList.add(key);
                }
            }
            MZGramConfig.setTextStyleMenu(defaultOrder ? "" : TextUtils.join(",", ordered), TextUtils.join(",", hiddenList));
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), null);
        builder.setNeutralButton(getString(R.string.MZGramReset), (dialog, which) -> MZGramConfig.setTextStyleMenu("", ""));
        return builder.create();
    }
}
