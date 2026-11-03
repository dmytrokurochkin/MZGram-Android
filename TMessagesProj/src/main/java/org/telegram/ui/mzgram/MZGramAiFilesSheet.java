package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ask AI: what MZGram shows when the site asks for files (its own add-files
 * button). The photos and files of the messages, each with its thumbnail
 * (the small one Telegram already has, nothing big is decoded) or the icon
 * of its kind, its name and size and a check; Add gives the site every
 * checked file at once, and another file can still be chosen on the device.
 */
public class MZGramAiFilesSheet extends BottomSheet {

    public interface Listener {
        void onAdd(List<MZGramAiActivity.Attachment> chosen);

        void onOther();

        void onCancel();
    }

    public interface Ready {
        File fileOf(MZGramAiActivity.Attachment attachment);
    }

    private final List<MZGramAiActivity.Attachment> items;
    private final boolean[] allowed;
    private final boolean[] checked;
    private final List<CheckBox2> boxes = new ArrayList<>();
    private final List<TextView> infos = new ArrayList<>();
    private final Ready ready;
    private final Listener listener;
    private final TextView titleView;
    private final ButtonWithCounterView addButton;
    private final TextView otherButton;
    private boolean answered;
    private final Runnable refresh = this::refresh;

    public MZGramAiFilesSheet(Context context, Theme.ResourcesProvider resources, String site,
                              List<MZGramAiActivity.Attachment> items, boolean[] allowed, String leftOut,
                              Ready ready, Listener listener) {
        super(context, false, resources);
        this.items = new ArrayList<>(items);
        this.allowed = allowed;
        this.checked = new boolean[items.size()];
        this.ready = ready;
        this.listener = listener;
        for (int i = 0; i < checked.length; i++) {
            checked[i] = allowed[i];
        }
        fixNavigationBar();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        titleView = new TextView(context);
        titleView.setText(formatString(R.string.MZGramAiFilesTitle, site));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 16, 22, 4));

        layout.addView(grey(context, getString(R.string.MZGramAiFilesHint), resources), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 0, 22, 8));

        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        boolean someNotAllowed = false;
        for (int i = 0; i < items.size(); i++) {
            rows.addView(row(context, resources, i), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 60));
            someNotAllowed |= !allowed[i];
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(rows);
        layout.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        if (someNotAllowed) {
            layout.addView(grey(context, getString(R.string.MZGramAiFilesPhotosOnly), resources), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 6, 22, 0));
        }
        if (!TextUtils.isEmpty(leftOut)) {
            layout.addView(grey(context, leftOut, resources), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 6, 22, 0));
        }

        otherButton = new TextView(context);
        otherButton.setText(getString(R.string.MZGramAiFilesOther));
        otherButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        otherButton.setGravity(Gravity.CENTER);
        otherButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resources));
        otherButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), Theme.RIPPLE_MASK_ALL));
        otherButton.setOnClickListener(v -> answer(listener::onOther));
        layout.addView(otherButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 16, 8, 16, 0));

        addButton = new ButtonWithCounterView(context, resources);
        addButton.setOnClickListener(v -> {
            List<MZGramAiActivity.Attachment> chosen = chosen();
            if (!chosen.isEmpty()) {
                answer(() -> listener.onAdd(chosen));
            }
        });
        layout.addView(addButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 16));

        setCustomView(layout);
        setOnHideListener(d -> {
            AndroidUtilities.cancelRunOnUIThread(refresh);
            if (!answered) {
                answered = true;
                listener.onCancel();
            }
        });
        refresh();
    }

    private static TextView grey(Context context, String text, Theme.ResourcesProvider resources) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resources));
        return view;
    }

    private View row(Context context, Theme.ResourcesProvider resources, int index) {
        MZGramAiActivity.Attachment item = items.get(index);
        FrameLayout row = new FrameLayout(context);
        row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), Theme.RIPPLE_MASK_ALL));

        BackupImageView thumb = new BackupImageView(context);
        thumb.setRoundRadius(AndroidUtilities.dp(6));
        setThumb(thumb, item);
        row.addView(thumb, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 0, 0));

        TextView name = new TextView(context);
        name.setText(item.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        row.addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 74, 9, 60, 0));

        TextView info = new TextView(context);
        info.setSingleLine(true);
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        info.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resources));
        row.addView(info, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 74, 33, 60, 0));
        infos.add(info);

        CheckBox2 box = new CheckBox2(context, 22, resources);
        box.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        box.setDrawUnchecked(true);
        box.setDrawBackgroundAsArc(10);
        box.setChecked(checked[index], false);
        row.addView(box, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 20, 0));
        boxes.add(box);

        row.setOnClickListener(v -> {
            if (!allowed[index] || ready.fileOf(item) == null) {
                return;
            }
            checked[index] = !checked[index];
            box.setChecked(checked[index], true);
            refresh();
        });
        row.setAlpha(allowed[index] ? 1f : 0.5f);
        return row;
    }

    // The small thumbnail Telegram already has for the photo, or for a
    // picture sent as a file; the icon of the file's kind otherwise
    static void setThumb(BackupImageView view, MZGramAiActivity.Attachment item) {
        MessageObject message = item.message;
        if (message != null) {
            TLRPC.Document document = message.getDocument();
            if (document == null && message.photoThumbs != null) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 90);
                if (size != null) {
                    view.setImage(ImageLocation.getForObject(size, message.photoThumbsObject), "44_44", null, null, message);
                    return;
                }
            }
            if (document != null && document.thumbs != null && !document.thumbs.isEmpty()) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                if (size != null) {
                    view.setImage(ImageLocation.getForDocument(size, document), "44_44", null, null, message);
                    return;
                }
            }
        }
        view.setImageResource(AndroidUtilities.getThumbForNameOrMime(item.name, item.mime, true));
    }

    // Sizes, the downloads still going, and the count on Add
    private void refresh() {
        boolean waiting = false;
        for (int i = 0; i < items.size(); i++) {
            MZGramAiActivity.Attachment item = items.get(i);
            boolean there = ready.fileOf(item) != null;
            waiting |= !there && allowed[i];
            String size = item.size > 0 ? AndroidUtilities.formatFileSize(item.size) : "";
            infos.get(i).setText(there ? size : getString(R.string.MZGramAiFilesDownloading));
            boolean on = checked[i] && there && allowed[i];
            if (boxes.get(i).isChecked() != on) {
                boxes.get(i).setChecked(on, true);
            }
        }
        int count = chosen().size();
        addButton.setText(formatString(R.string.MZGramAiFilesAdd, count), true);
        addButton.setEnabled(count > 0);
        addButton.setAlpha(count > 0 ? 1f : 0.5f);
        if (waiting) {
            AndroidUtilities.runOnUIThread(refresh, 500);
        }
    }

    private List<MZGramAiActivity.Attachment> chosen() {
        List<MZGramAiActivity.Attachment> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (checked[i] && allowed[i] && ready.fileOf(items.get(i)) != null) {
                result.add(items.get(i));
            }
        }
        return result;
    }

    private void answer(Runnable action) {
        if (answered) {
            return;
        }
        answered = true;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        dismiss();
        action.run();
    }

    // The tests
    public String titleForTest() {
        return titleView.getText().toString();
    }

    public int rowsForTest() {
        return items.size();
    }

    public View addButtonForTest() {
        return addButton;
    }

    public View otherButtonForTest() {
        return otherButton;
    }
}
