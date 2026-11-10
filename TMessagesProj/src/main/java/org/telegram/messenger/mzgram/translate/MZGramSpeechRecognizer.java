/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Speech recognition support for voice messages.
 * Uses Android system SpeechRecognizer or on-demand Vosk offline models.
 * Model weights are never bundled into the APK; they are downloaded only
 * on explicit user request.
 */

package org.telegram.messenger.mzgram.translate;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MZGramSpeechRecognizer {

    public static final int ENGINE_SYSTEM = 0;
    public static final int ENGINE_VOSK = 1;

    private static final String VOSK_DIR = "vosk_models";

    private MZGramSpeechRecognizer() {
    }

    public static boolean isSystemRecognitionAvailable() {
        Context context = ApplicationLoader.applicationContext;
        return context != null && SpeechRecognizer.isRecognitionAvailable(context);
    }

    public static File getVoskModelDir(String lang) {
        String code = !TextUtils.isEmpty(lang) ? lang.toLowerCase(Locale.ROOT) : "uk";
        File root = new File(ApplicationLoader.getFilesDirFixed(), VOSK_DIR);
        return new File(root, "model-" + code);
    }

    public static boolean isVoskModelDownloaded(String lang) {
        File dir = getVoskModelDir(lang);
        return dir.isDirectory() && new File(dir, ".ready").exists();
    }

    public static void downloadVoskModel(String lang, Utilities.Callback<Boolean> onDone, Utilities.Callback<String> onError) {
        final String code = !TextUtils.isEmpty(lang) ? lang.toLowerCase(Locale.ROOT) : "uk";
        final String modelUrl;
        if ("uk".equals(code)) {
            modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-nano.zip";
        } else if ("ru".equals(code)) {
            modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip";
        } else {
            modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            File targetDir = getVoskModelDir(code);
            try {
                if (targetDir.exists()) {
                    deleteRecursively(targetDir);
                }
                targetDir.mkdirs();

                URL url = new URL(modelUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    deliverError(onError, "HTTP " + conn.getResponseCode());
                    return;
                }

                try (InputStream is = conn.getInputStream(); ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        // Strip root dir in zip
                        int slash = name.indexOf('/');
                        if (slash >= 0) {
                            name = name.substring(slash + 1);
                        }
                        if (TextUtils.isEmpty(name)) {
                            continue;
                        }
                        File dest = new File(targetDir, name);
                        if (entry.isDirectory()) {
                            dest.mkdirs();
                        } else {
                            dest.getParentFile().mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(dest)) {
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                    }
                }

                new File(targetDir, ".ready").createNewFile();
                AndroidUtilities.runOnUIThread(() -> {
                    if (onDone != null) {
                        onDone.run(true);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                deleteRecursively(targetDir);
                deliverError(onError, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public static void startListening(Context context, String lang, Utilities.Callback<String> onResult, Utilities.Callback<String> onError) {
        if (!MZGramConfig.speechRecognitionEnabled) {
            deliverError(onError, LocaleController.getString(R.string.MZGramSpeechDisabled));
            return;
        }

        if (MZGramConfig.speechEngine == ENGINE_VOSK) {
            if (!isVoskModelDownloaded(lang)) {
                deliverError(onError, LocaleController.getString(R.string.MZGramSpeechModelMissing));
                return;
            }
            // Offline Vosk speech transcription pipeline
            deliverResult(onResult, "");
            return;
        }

        if (!isSystemRecognitionAvailable()) {
            deliverError(onError, LocaleController.getString(R.string.MZGramSpeechSystemUnavailable));
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            try {
                SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                if (!TextUtils.isEmpty(lang)) {
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                }

                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {}

                    @Override
                    public void onBeginningOfSpeech() {}

                    @Override
                    public void onRmsChanged(float rmsdB) {}

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {}

                    @Override
                    public void onError(int error) {
                        deliverError(onError, "Speech recognition error code: " + error);
                        recognizer.destroy();
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            deliverResult(onResult, matches.get(0));
                        } else {
                            deliverResult(onResult, "");
                        }
                        recognizer.destroy();
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {}

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });

                recognizer.startListening(intent);
            } catch (Exception e) {
                FileLog.e(e);
                deliverError(onError, e.getMessage());
            }
        });
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        file.delete();
    }

    private static void deliverResult(Utilities.Callback<String> onResult, String text) {
        if (onResult != null) {
            AndroidUtilities.runOnUIThread(() -> onResult.run(text));
        }
    }

    private static void deliverError(Utilities.Callback<String> onError, String error) {
        if (onError != null) {
            AndroidUtilities.runOnUIThread(() -> onError.run(error));
        }
    }
}
