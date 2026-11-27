package org.mzgram.drawer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mzgram.appearance.AppearanceConfig;

import org.telegram.ui.MainTabsLayout;

public final class MainMenuLayout {

    private static final String SECTION_SEPARATOR = ";";
    private static final String ID_SEPARATOR = ",";

    private MainMenuLayout() {
    }

    public static List<Integer> getDefaultLayout() {
        final boolean bottomBarHidden = isBottomNavigationBarHidden();
        final ArrayList<Integer> layout = new ArrayList<>();
        layout.add(MainMenuItem.ARCHIVE.getId());
        if (bottomBarHidden) {
            layout.add(MainMenuItem.PROFILE.getId());
        }
        layout.add(MainMenuItem.NEW_GROUP.getId());
        if (bottomBarHidden) {
            layout.add(MainMenuItem.CONTACTS.getId());
        }
        layout.add(MainMenuItem.SAVED.getId());
        layout.add(MainMenuItem.WALLET.getId());
        layout.add(MainMenuItem.FEED.getId());
        layout.add(MainMenuItem.BOTS.getId());
        if (bottomBarHidden) {
            layout.add(MainMenuItem.SETTINGS.getId());
        }
        return layout;
    }

    public static List<Integer> getLayout() {
        return parse()[0];
    }

    public static List<Integer> getHiddenItems() {
        return parse()[1];
    }

    public static void save(List<Integer> layout, List<Integer> hidden) {
        final ArrayList<Integer> visibleCopy = new ArrayList<>(layout);
        final ArrayList<Integer> hiddenCopy = new ArrayList<>(hidden);
        ensureSettingsVisibility(visibleCopy, hiddenCopy);
        AppearanceConfig.mainMenuLayout.setConfigString(serialize(visibleCopy, hiddenCopy));
    }

    public static void reset() {
        AppearanceConfig.mainMenuLayout.setConfigString("");
    }

    public static boolean isCustomized() {
        AppearanceConfig.ensureLoaded();
        final String raw = AppearanceConfig.mainMenuLayout.String();
        return raw != null && !raw.isEmpty();
    }

    public static List<Integer> getAllItemIds() {
        final ArrayList<Integer> ids = new ArrayList<>();
        for (MainMenuItem item : MainMenuItem.values()) {
            if (item != MainMenuItem.DIVIDER) {
                ids.add(item.getId());
            }
        }
        return ids;
    }


    private static boolean isBottomNavigationBarHidden() {
        try {
            return MainTabsLayout.isBottomNavigationHidden();
        } catch (Exception e) {
            return false;
        }
    }

    private static List<Integer>[] parse() {
        AppearanceConfig.ensureLoaded();
        final String raw = AppearanceConfig.mainMenuLayout.String();

        final ArrayList<Integer> visible = new ArrayList<>();
        final ArrayList<Integer> hidden = new ArrayList<>();

        if (raw == null || raw.isEmpty()) {
            visible.addAll(getDefaultLayout());
        } else {
            final String[] sections = raw.split(SECTION_SEPARATOR, -1);
            readIds(sections.length > 0 ? sections[0] : "", visible, true);
            readIds(sections.length > 1 ? sections[1] : "", hidden, false);
        }

        for (Integer id : getAllItemIds()) {
            if (!visible.contains(id) && !hidden.contains(id)) {
                hidden.add(id);
            }
        }
        ensureSettingsVisibility(visible, hidden);

        @SuppressWarnings("unchecked") final List<Integer>[] result = new List[]{
                Collections.unmodifiableList(visible),
                Collections.unmodifiableList(hidden)
        };
        return result;
    }

    private static void readIds(String section, ArrayList<Integer> out, boolean allowDivider) {
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String part : section.split(ID_SEPARATOR)) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int id;
            try {
                id = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                continue;
            }
            if (id == MainMenuItem.DIVIDER.getId()) {
                if (allowDivider) {
                    out.add(id);
                }
                continue;
            }
            if (MainMenuItem.getById(id) != null && !out.contains(id)) {
                out.add(id);
            }
        }
    }

    private static void ensureSettingsVisibility(List<Integer> visible, List<Integer> hidden) {
        if (!isBottomNavigationBarHidden()) {
            return;
        }
        final Integer settings = MainMenuItem.SETTINGS.getId();
        if (visible.contains(settings)) {
            return;
        }
        hidden.remove(settings);
        visible.add(settings);
    }

    private static String serialize(List<Integer> visible, List<Integer> hidden) {
        return join(visible) + SECTION_SEPARATOR + join(hidden);
    }

    private static String join(List<Integer> ids) {
        final StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(ID_SEPARATOR);
            }
            sb.append(id);
        }
        return sb.toString();
    }

    public static ArrayList<Integer> getLayoutMutable() {
        return new ArrayList<>(getLayout());
    }

    public static ArrayList<Integer> getHiddenItemsMutable() {
        return new ArrayList<>(getHiddenItems());
    }

    static List<Integer> parseIds(String section) {
        final ArrayList<Integer> out = new ArrayList<>();
        readIds(section, out, true);
        return out;
    }
}
