package org.mzgram.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

import kotlin.Unit;
import org.mzgram.core.CoreConfig;
import org.mzgram.core.translate.Translator;
import org.mzgram.core.translate.TranslatorKt;

@SuppressLint("ViewConstructor")
public abstract class TranslateBeforeSendWrapper extends ActionBarMenuSubItem {

    private static final int MIN_WIDTH_DP = 196;
    private static final int ITEM_HEIGHT_DP = 56;

    public TranslateBeforeSendWrapper(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context, top, bottom, resourcesProvider);
        setTextAndIcon(LocaleController.getString(R.string.TranslateTo), R.drawable.msg_translate);
        setSubtext(getTargetLanguageTitle());
        setMinimumWidth(AndroidUtilities.dp(MIN_WIDTH_DP));
        setItemHeight(ITEM_HEIGHT_DP);
        setOnClickListener(v -> onClick());
        setOnLongClickListener(this::showLanguageSelect);
        setRightIcon(R.drawable.msg_arrowright);
        getRightIcon().setOnClickListener(v -> showLanguageSelect(this));
    }

    public static Locale getTargetLanguage() {
        return TranslatorKt.getCode2Locale(CoreConfig.translateInputLang.String());
    }

    private static CharSequence getTargetLanguageTitle() {
        Locale currentLocale = LocaleController.getInstance().getCurrentLocale();
        return getTargetLanguage().getDisplayName(currentLocale != null ? currentLocale : Locale.getDefault());
    }

    private boolean showLanguageSelect(View anchor) {
        Translator.showTargetLangSelect(anchor, true, locale -> {
            CoreConfig.translateInputLang.setConfigString(TranslatorKt.getLocale2code(locale));
            setSubtext(getTargetLanguageTitle());
            return Unit.INSTANCE;
        });
        return true;
    }

    public abstract void onClick();
}
