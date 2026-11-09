/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The choice of pills for the chat list's search bar (MZGramPills): a tap
 * shows or hides a pill, holding and dragging a row moves it. The last row
 * puts everything back as on a fresh install (no pill chosen).
 *
 * Idea from exteraless PillStackSettingsActivity (GPL-3.0); written anew
 * for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.mzgram.pills.MZGramPillStack;

import java.util.ArrayList;

public class MZGramPillsActivity extends UniversalFragment {

    private static final int PILL_ROW = 1000;
    private static final int ROW_RESET = 1;

    private int orderSectionId = -1;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramPills);
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        listView.listenReorder(this::whenReordered);
        listView.allowReorder(true);
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(null));
        orderSectionId = adapter.reorderSectionStart();
        for (int id : MZGramPills.order()) {
            String title = MZGramPillStack.titleOf(id);
            if (title == null) {
                continue;
            }
            items.add(UItem.asCheck(PILL_ROW + id, title).setChecked(MZGramPills.isShown(id)));
        }
        adapter.reorderSectionEnd();
        items.add(UItem.asShadow(getString(R.string.MZGramPillsInfo)));
        items.add(UItem.asButton(ROW_RESET, getString(R.string.MZGramPillsReset)).red());
        items.add(UItem.asShadow(null));
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        if (id != orderSectionId || items == null) {
            return;
        }
        ArrayList<Integer> order = new ArrayList<>();
        for (UItem item : items) {
            order.add(item.id - PILL_ROW);
        }
        MZGramPills.setOrder(order);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_RESET) {
            MZGramPills.reset();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            return;
        }
        int id = item.id - PILL_ROW;
        if (!MZGramPills.isKnown(id)) {
            return;
        }
        boolean show = !MZGramPills.isShown(id);
        MZGramPills.setShown(id, show);
        item.setChecked(show);
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(show);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
