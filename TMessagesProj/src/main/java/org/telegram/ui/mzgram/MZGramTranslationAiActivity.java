/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Translation and AI suite settings screen.
 * Configures multi-engine translation providers, direct LLM API keys
 * (stored locally only), and offline/system speech recognition.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.translate.MZGramAiService;
import org.telegram.messenger.mzgram.translate.MZGramSpeechRecognizer;
import org.telegram.messenger.mzgram.translate.MZGramTranslator;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MZGramTranslationAiActivity extends UniversalFragment {

    private static final int BUTTON_TRANSLATION_PROVIDER = 1;
    private static final int BUTTON_DEEPL_KEY = 2;
    private static final int BUTTON_MOZHI_URL = 3;
    private static final int BUTTON_CHAT_BAR = 4;
    private static final int BUTTON_TRANSLATE_SEND = 5;
    private static final int BUTTON_AUTO_FALLBACK = 6;

    private static final int BUTTON_AI_PROVIDER = 10;
    private static final int BUTTON_AI_KEY = 11;
    private static final int BUTTON_AI_ENDPOINT = 12;
    private static final int BUTTON_AI_MODEL = 13;
    private static final int BUTTON_AI_TEST = 14;

    private static final int BUTTON_SPEECH_ENABLED = 20;
    private static final int BUTTON_SPEECH_ENGINE = 21;
    private static final int BUTTON_SPEECH_VOSK_LANG = 22;
    private static final int BUTTON_SPEECH_VOSK_DOWNLOAD = 23;

    private boolean testingAi;
    private boolean downloadingVosk;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramTranslationAiSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.MZGramTranslationHeader)));

        String providerName = MZGramTranslator.getProviderName(MZGramConfig.translationProvider);
        items.add(UItem.asButton(BUTTON_TRANSLATION_PROVIDER, getString(R.string.MZGramTranslationProvider), providerName));

        if (MZGramConfig.translationProvider == MZGramTranslator.PROVIDER_DEEPL) {
            String keyMasked = TextUtils.isEmpty(MZGramConfig.deeplApiKey) ? "" : "••••••••";
            items.add(UItem.asButton(BUTTON_DEEPL_KEY, getString(R.string.MZGramDeeplApiKey), keyMasked));
        } else if (MZGramConfig.translationProvider == MZGramTranslator.PROVIDER_MOZHI) {
            String url = TextUtils.isEmpty(MZGramConfig.mozhiCustomInstance) ? "" : MZGramConfig.mozhiCustomInstance;
            items.add(UItem.asButton(BUTTON_MOZHI_URL, getString(R.string.MZGramMozhiInstance), url));
        }

        items.add(UItem.asCheck(BUTTON_CHAT_BAR, getString(R.string.MZGramChatTranslationBar)).setChecked(MZGramConfig.chatTranslationBar));
        items.add(UItem.asCheck(BUTTON_TRANSLATE_SEND, getString(R.string.MZGramTranslateBeforeSend)).setChecked(MZGramConfig.translateBeforeSend));
        items.add(UItem.asCheck(BUTTON_AUTO_FALLBACK, getString(R.string.MZGramTranslationAutoFallback)).setChecked(MZGramConfig.translationAutoFallback));
        items.add(UItem.asShadow(getString(R.string.MZGramTranslationAutoFallbackInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramAiHeader)));

        String aiName = MZGramAiService.getAiProviderName(MZGramConfig.aiProviderType);
        items.add(UItem.asButton(BUTTON_AI_PROVIDER, getString(R.string.MZGramAiDirectProvider), aiName));

        String aiKeyMasked = TextUtils.isEmpty(MZGramConfig.aiApiKey) ? "" : "••••••••";
        items.add(UItem.asButton(BUTTON_AI_KEY, getString(R.string.MZGramAiApiKey), aiKeyMasked));

        if (MZGramConfig.aiProviderType == MZGramAiService.AI_CUSTOM) {
            String endpoint = TextUtils.isEmpty(MZGramConfig.aiCustomEndpoint) ? "" : MZGramConfig.aiCustomEndpoint;
            items.add(UItem.asButton(BUTTON_AI_ENDPOINT, getString(R.string.MZGramAiCustomEndpoint), endpoint));
        }

        String model = TextUtils.isEmpty(MZGramConfig.aiCustomModel) ? MZGramAiService.getDefaultModel(MZGramConfig.aiProviderType) : MZGramConfig.aiCustomModel;
        items.add(UItem.asButton(BUTTON_AI_MODEL, getString(R.string.MZGramAiCustomModel), model));

        items.add(UItem.asButton(BUTTON_AI_TEST, getString(R.string.MZGramAiTestConnection)));
        items.add(UItem.asShadow(getString(R.string.MZGramAiApiKeyInfo)));

        items.add(UItem.asHeader(getString(R.string.MZGramSpeechHeader)));
        items.add(UItem.asCheck(BUTTON_SPEECH_ENABLED, getString(R.string.MZGramSpeechRecognition)).setChecked(MZGramConfig.speechRecognitionEnabled));

        if (MZGramConfig.speechRecognitionEnabled) {
            String engineName = MZGramConfig.speechEngine == MZGramSpeechRecognizer.ENGINE_VOSK
                    ? getString(R.string.MZGramSpeechEngineVosk)
                    : getString(R.string.MZGramSpeechEngineSystem);
            items.add(UItem.asButton(BUTTON_SPEECH_ENGINE, getString(R.string.MZGramSpeechEngine), engineName));

            if (MZGramConfig.speechEngine == MZGramSpeechRecognizer.ENGINE_VOSK) {
                items.add(UItem.asButton(BUTTON_SPEECH_VOSK_LANG, getString(R.string.MZGramSpeechVoskLang), MZGramConfig.voskModelLang.toUpperCase(java.util.Locale.ROOT)));

                boolean downloaded = MZGramSpeechRecognizer.isVoskModelDownloaded(MZGramConfig.voskModelLang);
                String voskStatus = downloaded ? getString(R.string.MZGramSpeechVoskDownloaded) : getString(R.string.MZGramSpeechDownloadVosk);
                items.add(UItem.asButton(BUTTON_SPEECH_VOSK_DOWNLOAD, voskStatus));
            }
        }
        items.add(UItem.asShadow(getString(R.string.MZGramSpeechRecognitionInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }

        if (item.id == BUTTON_TRANSLATION_PROVIDER) {
            showTranslationProviderDialog(activity);
        } else if (item.id == BUTTON_DEEPL_KEY) {
            showDeeplKeyDialog(activity);
        } else if (item.id == BUTTON_MOZHI_URL) {
            showMozhiUrlDialog(activity);
        } else if (item.id == BUTTON_CHAT_BAR) {
            MZGramConfig.toggleChatTranslationBar();
            adapter.update(true);
        } else if (item.id == BUTTON_TRANSLATE_SEND) {
            MZGramConfig.toggleTranslateBeforeSend();
            adapter.update(true);
        } else if (item.id == BUTTON_AUTO_FALLBACK) {
            MZGramConfig.toggleTranslationAutoFallback();
            adapter.update(true);
        } else if (item.id == BUTTON_AI_PROVIDER) {
            showAiProviderDialog(activity);
        } else if (item.id == BUTTON_AI_KEY) {
            showAiKeyDialog(activity);
        } else if (item.id == BUTTON_AI_ENDPOINT) {
            showAiEndpointDialog(activity);
        } else if (item.id == BUTTON_AI_MODEL) {
            showAiModelDialog(activity);
        } else if (item.id == BUTTON_AI_TEST) {
            testAiConnection();
        } else if (item.id == BUTTON_SPEECH_ENABLED) {
            MZGramConfig.toggleSpeechRecognitionEnabled();
            adapter.update(true);
        } else if (item.id == BUTTON_SPEECH_ENGINE) {
            showSpeechEngineDialog(activity);
        } else if (item.id == BUTTON_SPEECH_VOSK_LANG) {
            showVoskLangDialog(activity);
        } else if (item.id == BUTTON_SPEECH_VOSK_DOWNLOAD) {
            confirmDownloadVosk(activity);
        }
    }

    private void showTranslationProviderDialog(Activity activity) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.MZGramTranslatorTelegram),
                getString(R.string.MZGramTranslatorGoogle),
                getString(R.string.MZGramTranslatorDeepL),
                getString(R.string.MZGramTranslatorAi),
                getString(R.string.MZGramTranslatorAidl),
                getString(R.string.MZGramTranslatorMozhi)
        };
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramTranslationProvider))
                .setItems(options, (dialog, which) -> {
                    MZGramConfig.setTranslationProvider(which);
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showDeeplKeyDialog(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        EditTextBoldCursor editText = new EditTextBoldCursor(activity);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!TextUtils.isEmpty(MZGramConfig.deeplApiKey)) {
            editText.setText(MZGramConfig.deeplApiKey);
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramDeeplApiKey))
                .setMessage(getString(R.string.MZGramDeeplApiKeyPrompt))
                .setView(container)
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    MZGramConfig.setDeeplApiKey(editText.getText().toString());
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showMozhiUrlDialog(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        EditTextBoldCursor editText = new EditTextBoldCursor(activity);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (!TextUtils.isEmpty(MZGramConfig.mozhiCustomInstance)) {
            editText.setText(MZGramConfig.mozhiCustomInstance);
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramMozhiInstance))
                .setMessage(getString(R.string.MZGramMozhiInstancePrompt))
                .setView(container)
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    MZGramConfig.setMozhiCustomInstance(editText.getText().toString());
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showAiProviderDialog(Activity activity) {
        CharSequence[] options = new CharSequence[MZGramAiService.AI_PROVIDERS_COUNT];
        for (int i = 0; i < options.length; i++) {
            // Telegram's own text: AI provider names are brand names
            options[i] = MZGramAiService.getAiProviderName(i);
        }
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramAiDirectProvider))
                .setItems(options, (dialog, which) -> {
                    MZGramConfig.setAiProviderType(which);
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showAiKeyDialog(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        EditTextBoldCursor editText = new EditTextBoldCursor(activity);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!TextUtils.isEmpty(MZGramConfig.aiApiKey)) {
            editText.setText(MZGramConfig.aiApiKey);
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramAiApiKey))
                .setMessage(getString(R.string.MZGramAiApiKeyPrompt))
                .setView(container)
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    MZGramConfig.setAiApiKey(editText.getText().toString());
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showAiEndpointDialog(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        EditTextBoldCursor editText = new EditTextBoldCursor(activity);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (!TextUtils.isEmpty(MZGramConfig.aiCustomEndpoint)) {
            editText.setText(MZGramConfig.aiCustomEndpoint);
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramAiCustomEndpoint))
                .setMessage(getString(R.string.MZGramAiCustomEndpointPrompt))
                .setView(container)
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    MZGramConfig.setAiCustomEndpoint(editText.getText().toString());
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showAiModelDialog(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        EditTextBoldCursor editText = new EditTextBoldCursor(activity);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!TextUtils.isEmpty(MZGramConfig.aiCustomModel)) {
            editText.setText(MZGramConfig.aiCustomModel);
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramAiCustomModel))
                .setMessage(getString(R.string.MZGramAiCustomModelPrompt))
                .setView(container)
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    MZGramConfig.setAiCustomModel(editText.getText().toString());
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void testAiConnection() {
        if (testingAi) {
            return;
        }
        testingAi = true;
        BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_launcher, getString(R.string.MZGramAiTesting)).show();

        MZGramAiService.askAi("Respond with OK if you receive this message.", null, response -> {
            testingAi = false;
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramAiTestSuccess)).show();
        }, error -> {
            testingAi = false;
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, formatString(R.string.MZGramAiTestFailed, error)).show();
        });
    }

    private void showSpeechEngineDialog(Activity activity) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.MZGramSpeechEngineSystem),
                getString(R.string.MZGramSpeechEngineVosk)
        };
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramSpeechEngine))
                .setItems(options, (dialog, which) -> {
                    MZGramConfig.setSpeechEngine(which);
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showVoskLangDialog(Activity activity) {
        CharSequence[] options = new CharSequence[]{"uk", "en", "ru"};
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramSpeechVoskLang))
                .setItems(options, (dialog, which) -> {
                    MZGramConfig.setVoskModelLang(options[which].toString());
                    adapter.update(true);
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void confirmDownloadVosk(Activity activity) {
        if (downloadingVosk) {
            return;
        }
        final String lang = MZGramConfig.voskModelLang;
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramSpeechDownloadVosk))
                .setMessage(formatString(R.string.MZGramSpeechVoskPrompt, lang.toUpperCase(java.util.Locale.ROOT)))
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    downloadingVosk = true;
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_launcher, getString(R.string.MZGramSpeechVoskDownloading)).show();
                    MZGramSpeechRecognizer.downloadVoskModel(lang, done -> {
                        downloadingVosk = false;
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramSpeechVoskDownloadSuccess)).show();
                        adapter.update(true);
                    }, error -> {
                        downloadingVosk = false;
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, error).show();
                        adapter.update(true);
                    });
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }
}
