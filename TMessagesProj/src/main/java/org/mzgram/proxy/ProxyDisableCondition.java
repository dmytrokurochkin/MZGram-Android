package org.mzgram.proxy;

public enum ProxyDisableCondition {
    VPN(1),
    MOBILE_DATA(2),
    WIFI(4);

    public final int flag;

    ProxyDisableCondition(int flag) {
        this.flag = flag;
    }

    public int getFlag() {
        return flag;
    }
}
