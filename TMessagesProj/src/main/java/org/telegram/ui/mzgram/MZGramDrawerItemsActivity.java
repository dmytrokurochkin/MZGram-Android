/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The items of the MZGram side menu (MZGramDrawerItems): a tap hides or
 * shows an item, holding and dragging a row moves it. "Full menu" is always
 * shown. The last row puts everything back as on a fresh install.
 *
 * Idea from exteraless MainMenuLayout (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramDrawerItems;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MZGramDrawerItemsActivity extends UniversalFragment {

    private static final int ITEM_ROW = 1000;
    private static final int ROW_RESET = 1;

    private int orderSectionId = -1;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramDrawerItems);
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
        orderSectionId = adapter.reorderSectionStart();
        for (int id : MZGramDrawerItems.order()) {
            String title = MZGramDrawer.titleOf(id);
            if (title == null) {
                continue;
            }
            items.add(UItem.asCheck(ITEM_ROW + id, title).setChecked(!MZGramDrawerItems.isHidden(id)));
        }
        adapter.reorderSectionEnd();
        items.add(UItem.asShadow(getString(R.string.MZGramDrawerItemsInfo)));
        items.add(UItem.asButton(ROW_RESET, getString(R.string.MZGramDrawerItemsReset)).red());
        items.add(UItem.asShadow(null));
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        if (id != orderSectionId || items == null) {
            return;
        }
        ArrayList<Integer> order = new ArrayList<>();
        for (UItem item : items) {
            order.add(item.id - ITEM_ROW);
        }
        MZGramDrawerItems.setOrder(order);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_RESET) {
            MZGramDrawerItems.reset();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            return;
        }
        int id = item.id - ITEM_ROW;
        if (!MZGramDrawerItems.isKnown(id)) {
            return;
        }
        if (!MZGramDrawerItems.canHide(id)) {
            // "Full menu" stays: the rest of the chat list menu is reached through it
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(true);
            }
            return;
        }
        boolean hide = !MZGramDrawerItems.isHidden(id);
        MZGramDrawerItems.setHidden(id, hide);
        item.setChecked(!hide);
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(!hide);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
