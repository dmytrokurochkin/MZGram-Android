package org.mzgram.core.translate.source.fallback;

import app.nekogram.translator.GoogleAppTranslator;

public class GoogleTranslatorCore {

    public static String translate(String text, String from, String to) throws Exception {
        var translator = GoogleAppTranslator.getInstance();

        var result = translator.translate(text, from, to);
        return result.translation;
    }
}
