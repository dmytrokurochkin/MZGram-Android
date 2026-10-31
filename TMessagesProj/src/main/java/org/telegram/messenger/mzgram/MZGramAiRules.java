/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * "Ask AI": the AI sites, the text, the script that puts it into the site's
 * input field, and where the window may go. Not written yet: the tests of
 * MZGramAiTest and MZGramAiWindowTest come first.
 */

package org.telegram.messenger.mzgram;

import java.util.Collections;
import java.util.List;

public final class MZGramAiRules {

    public static final String PROFILE = "mzgram-ai";
    public static final String DEFAULT_PROVIDER = "gemini";

    public static final int STATUS_NONE = 0;
    public static final int STATUS_WAITING = 1;
    public static final int STATUS_INSERTED = 2;
    public static final int STATUS_NO_FIELD = 3;

    public static final int NEW_WINDOW_HERE = 0;
    public static final int NEW_WINDOW_BROWSER = 1;
    public static final int NEW_WINDOW_REFUSE = 2;

    public static final int TRIES = 60;
    public static final int TRY_INTERVAL_MS = 500;

    public static final class Provider {
        public final String key;
        public final String name;
        public final String startUrl;
        public final List<String> hosts;
        public final List<String> inputSelectors;

        Provider(String key, String name, String startUrl) {
            this.key = key;
            this.name = name;
            this.startUrl = startUrl;
            this.hosts = Collections.emptyList();
            this.inputSelectors = Collections.emptyList();
        }
    }

    private MZGramAiRules() {
    }

    public static List<Provider> providers() {
        return Collections.emptyList();
    }

    public static Provider provider(String key) {
        return null;
    }

    public static Provider providerOrDefault(String key) {
        return null;
    }

    public static Provider defaultProvider() {
        return null;
    }

    public static void setDefaultProvider(String key) {
    }

    public static String composeText(String prompt, String message) {
        return "";
    }

    public static String scriptString(String text) {
        return "\"\"";
    }

    public static String insertScript(Provider provider, String text) {
        return "";
    }

    public static int parseStatus(String result) {
        return STATUS_NONE;
    }

    public static boolean allowedNavigation(String url) {
        return false;
    }

    public static int newWindowTarget(Provider provider, String url) {
        return NEW_WINDOW_REFUSE;
    }
}
