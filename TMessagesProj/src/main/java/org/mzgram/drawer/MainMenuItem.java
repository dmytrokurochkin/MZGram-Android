package org.mzgram.drawer;

public enum MainMenuItem {

    DIVIDER(-1),
    PROFILE(18),
    ARCHIVE(14),
    BOTS(105),
    NEW_GROUP(2),
    CONTACTS(6),
    NEW_CHANNEL(3),
    CALLS(10),
    SAVED(11),
    SETTINGS(8),
    BROWSER(101),
    PLUGINS(102),
    QR(17),
    FEED(106),
    GHOST_MODE(107),
    RECENT_CHATS(108),
    WALLET(109),
    MZGRAM_SETTINGS(110),
    KILL_APP(111);

    private final int id;

    MainMenuItem(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static MainMenuItem getById(int id) {
        for (MainMenuItem item : values()) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }
}
