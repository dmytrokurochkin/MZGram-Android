/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Group message menu: the main actions of the message menu (Reply, Copy,
 * Forward, Edit, Pin or Unpin, Delete) move to the bottom part of the menu,
 * after a gap, in Telegram's order; the other items stay above them. Off by
 * default.
 */

package org.telegram.ui.mzgram;

import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public final class MZGramMenuGroup {

    private static final int[] MAIN = {
            ChatActivity.OPTION_REPLY,
            ChatActivity.OPTION_COPY,
            ChatActivity.OPTION_FORWARD,
            ChatActivity.OPTION_EDIT,
            ChatActivity.OPTION_PIN,
            ChatActivity.OPTION_UNPIN,
            ChatActivity.OPTION_DELETE,
    };

    private MZGramMenuGroup() {
    }

    static boolean isMain(Integer option) {
        if (option == null) {
            return false;
        }
        for (int main : MAIN) {
            if (main == option) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the main actions to the end of the three parallel lists of the
     * menu, keeping the order within each part. Returns the position where
     * the main part starts, or -1 when the menu stays as it was (switch off,
     * or one of the parts is empty).
     */
    public static <T> int apply(ArrayList<T> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        if (!MZGramConfig.groupMessageMenu || items == null || options == null || icons == null
                || items.size() != options.size() || icons.size() != options.size()) {
            return -1;
        }
        ArrayList<T> otherItems = new ArrayList<>();
        ArrayList<Integer> otherOptions = new ArrayList<>();
        ArrayList<Integer> otherIcons = new ArrayList<>();
        ArrayList<T> mainItems = new ArrayList<>();
        ArrayList<Integer> mainOptions = new ArrayList<>();
        ArrayList<Integer> mainIcons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            boolean main = isMain(options.get(i));
            (main ? mainItems : otherItems).add(items.get(i));
            (main ? mainOptions : otherOptions).add(options.get(i));
            (main ? mainIcons : otherIcons).add(icons.get(i));
        }
        if (mainOptions.isEmpty() || otherOptions.isEmpty()) {
            return -1;
        }
        items.clear();
        items.addAll(otherItems);
        items.addAll(mainItems);
        options.clear();
        options.addAll(otherOptions);
        options.addAll(mainOptions);
        icons.clear();
        icons.addAll(otherIcons);
        icons.addAll(mainIcons);
        return otherOptions.size();
    }
}
