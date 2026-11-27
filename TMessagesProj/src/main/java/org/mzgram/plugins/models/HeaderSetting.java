package org.mzgram.plugins.models;

public class HeaderSetting extends SettingItem {

    private String text;

    public HeaderSetting(String text) {
        super("header", null, null, null);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
