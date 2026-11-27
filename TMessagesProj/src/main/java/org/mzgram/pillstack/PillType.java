package org.mzgram.pillstack;

public enum PillType {
    WEATHER(1),
    GRAM(2),
    BTC(3),
    USD(4),
    CACHE(5),
    PROXY(6),
    GHOST(7),
    RAM(8),
    CPU(9),
    NET_SPEED(10),
    DC_PING(11),
    GOLD(12),
    ETH(13),
    EUR(14),
    LAST_SEEN(15);

    public final int id;

    PillType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
