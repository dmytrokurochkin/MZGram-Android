/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The language of MZGram's own strings (settings, switches, menus, banner,
 * dialogs), chosen in Settings > MZGram > Interface, apart from Telegram's
 * language: System or one of LANGUAGES. System is the phone's language when
 * MZGram has it, English otherwise.
 *
 * MZGram's strings are read from their own Resources in that language, not
 * from the app's Resources: Telegram sets the language of those to its own,
 * and Android itself puts them back to the phone's language (on a
 * configuration change, when a WebView starts), so a screen showed some
 * MZGram strings in the phone's language and some in Telegram's.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.Locale;

public class MZGramLanguage {

    // Codes as saved, in the order the picker lists them; each has its
    // values-xx/mzgram_strings.xml (pt-BR: values-pt-rBR).
    public static final String[] LANGUAGES = {"en", "uk", "ru", "pl", "es", "de", "fr", "pt-BR", "tr", "ar", "be", "kk"};
    // Each language's own name; not translated.
    public static final String[] NAMES = {"English", "Українська", "Русский", "Polski", "Español", "Deutsch", "Français", "Português (Brasil)", "Türkçe", "العربية", "Беларуская", "Қазақша"};
    public static final String SYSTEM = "";
    public static final String FALLBACK = "en";

    private static volatile Resources resources;
    private static volatile String resourcesLanguage;
    // Swapped by the tests: the phone's language
    public static Locale systemOverride;

    // The language MZGram's strings are shown in now.
    public static String current() {
        String chosen = MZGramConfig.language;
        if (!TextUtils.isEmpty(chosen) && indexOf(chosen) >= 0) {
            return chosen;
        }
        return supported(systemLocale());
    }

    // The MZGram language for a locale: one MZGram has, else English.
    public static String supported(Locale locale) {
        if (locale == null) {
            return FALLBACK;
        }
        String language = locale.getLanguage();
        if ("pt".equals(language)) {
            // Brazilian is MZGram's only Portuguese
            return "pt-BR";
        }
        return indexOf(language) >= 0 ? language : FALLBACK;
    }

    public static int indexOf(String code) {
        for (int a = 0; a < LANGUAGES.length; a++) {
            if (LANGUAGES[a].equals(code)) {
                return a;
            }
        }
        return -1;
    }

    public static String name(String code) {
        int index = indexOf(code);
        return index < 0 ? code : NAMES[index];
    }

    public static boolean isRtl() {
        return "ar".equals(current());
    }

    private static Locale systemLocale() {
        if (systemOverride != null) {
            return systemOverride;
        }
        Configuration configuration = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= 24) {
            return configuration.getLocales().isEmpty() ? Locale.getDefault() : configuration.getLocales().get(0);
        }
        return configuration.locale;
    }

    private static Locale locale(String code) {
        int dash = code.indexOf('-');
        return dash < 0 ? new Locale(code) : new Locale(code.substring(0, dash), code.substring(dash + 1));
    }

    // Resources in the MZGram language, made again when it changes.
    public static Resources resources() {
        String language = current();
        Resources result = resources;
        if (result == null || !language.equals(resourcesLanguage)) {
            synchronized (MZGramLanguage.class) {
                result = resources;
                if (result == null || !language.equals(resourcesLanguage)) {
                    Context context = ApplicationLoader.applicationContext;
                    Configuration configuration = new Configuration(context.getResources().getConfiguration());
                    configuration.setLocale(locale(language));
                    result = context.createConfigurationContext(configuration).getResources();
                    resources = result;
                    resourcesLanguage = language;
                }
            }
        }
        return result;
    }

    // An MZGram string in the MZGram language; null when res is not one.
    public static String string(String key, int res) {
        if (key == null || !key.startsWith("MZGram")) {
            return null;
        }
        try {
            return resources().getString(res);
        } catch (Exception e) {
            return null;
        }
    }

    // After the choice changes
    public static void reset() {
        resources = null;
        resourcesLanguage = null;
    }
}
