package org.mzgram.chats;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;

import org.mzgram.core.config.ConfigItem;
import org.mzgram.ext.ExtConfig;

public final class TextStyleDialog {

    private static final String DEFAULT_ORDER = "translate,bold,italic,mono,code,strike,underline,quote,spoiler,link,mention,date,regular";

    private TextStyleDialog() {
    }

    private static final class Item {
        final String key;
        final CharSequence title;
        final ConfigItem config;

        Item(String key, CharSequence title, ConfigItem config) {
            this.key = key;
            this.title = title;
            this.config = config;
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextCheckCell cell;

        Holder(TextCheckCell cell) {
            super(cell);
            this.cell = cell;
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public static AlertDialog create(Context context) {
        ArrayList<Item> items = createItems();
        ArrayList<Item> ordered = new ArrayList<>();
        applyOrder(items, ordered, ExtConfig.INSTANCE.getTextStyleOrder().String());

        RecyclerView listView = new RecyclerView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        RecyclerView.Adapter<Holder> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextCheckCell cell = new TextCheckCell(context);
                cell.setBackground(Theme.getSelectorDrawable(false));
                return new Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                Item item = ordered.get(position);
                holder.cell.setTextAndCheck(item.title, item.config.Bool(), false);
                holder.cell.setOnClickListener(v -> {
                    int current = holder.getAdapterPosition();
                    if (current < 0 || current >= ordered.size()) {
                        return;
                    }
                    ConfigItem config = ordered.get(current).config;
                    config.toggleConfigBool();
                    holder.cell.setChecked(config.Bool());
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
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
                ordered.add(to, ordered.remove(from));
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        }).attachToRecyclerView(listView);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.TextStyle));
        builder.setView(listView);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            ArrayList<String> keys = new ArrayList<>();
            for (Item item : ordered) {
                keys.add(item.key);
            }
            ExtConfig.INSTANCE.getTextStyleOrder().setConfigString(TextUtils.join(",", keys));
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
            ExtConfig.INSTANCE.getTextStyleOrder().setConfigString(DEFAULT_ORDER);
            applyOrder(items, ordered, DEFAULT_ORDER);
            adapter.notifyDataSetChanged();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
        return builder.create();
    }

    private static void applyOrder(ArrayList<Item> items, ArrayList<Item> ordered, String order) {
        ordered.clear();
        if (!TextUtils.isEmpty(order)) {
            for (String key : order.split(",")) {
                for (Item item : items) {
                    if (item.key.equals(key) && !ordered.contains(item)) {
                        ordered.add(item);
                    }
                }
            }
        }
        for (Item item : items) {
            if (!ordered.contains(item)) {
                ordered.add(item);
            }
        }
    }

    private static ArrayList<Item> createItems() {
        ArrayList<Item> items = new ArrayList<>();
        items.add(new Item("translate", getString(R.string.TranslateMessage), ExtConfig.INSTANCE.getShowTextTranslate()));
        items.add(new Item("bold", styled(getString(R.string.Bold), new TypefaceSpan(AndroidUtilities.bold())), ExtConfig.INSTANCE.getShowTextBold()));
        items.add(new Item("italic", styled(getString(R.string.Italic), new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf"))), ExtConfig.INSTANCE.getShowTextItalic()));
        items.add(new Item("mono", styled(getString(R.string.Mono), new TypefaceSpan(AndroidUtilities.mono())), ExtConfig.INSTANCE.getShowTextMono()));
        items.add(new Item("code", styled(getString(R.string.MonoCode), new TypefaceSpan(AndroidUtilities.mono())), ExtConfig.INSTANCE.getShowTextMonoCode()));
        items.add(new Item("strike", styled(getString(R.string.Strike), styleSpan(TextStyleSpan.FLAG_STYLE_STRIKE)), ExtConfig.INSTANCE.getShowTextStrikethrough()));
        items.add(new Item("underline", styled(getString(R.string.Underline), styleSpan(TextStyleSpan.FLAG_STYLE_UNDERLINE)), ExtConfig.INSTANCE.getShowTextUnderline()));
        items.add(new Item("quote", getString(R.string.Quote), ExtConfig.INSTANCE.getShowTextQuote()));
        items.add(new Item("spoiler", getString(R.string.Spoiler), ExtConfig.INSTANCE.getShowTextSpoiler()));
        items.add(new Item("link", getString(R.string.CreateLink), ExtConfig.INSTANCE.getShowTextCreateLink()));
        items.add(new Item("mention", getString(R.string.CreateMention), ExtConfig.INSTANCE.getShowTextCreateMention()));
        items.add(new Item("date", getString(R.string.FormattedDate), ExtConfig.INSTANCE.getShowTextCreateDate()));
        items.add(new Item("regular", getString(R.string.Regular), ExtConfig.INSTANCE.getShowTextRegular()));
        return items;
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
}
