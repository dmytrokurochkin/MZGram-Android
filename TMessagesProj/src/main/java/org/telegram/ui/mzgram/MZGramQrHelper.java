/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Ported from Nekogram (tw.nekomimi.nekogram.helpers.QrHelper and
 * MessageHelper.readQrFromMessage, commit d769499). Finds QR codes in the photo
 * of a message on the device, with Google Play Services vision or zxing, and
 * shows what they contain. Nothing leaves the device.
 */

package org.telegram.ui.mzgram;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MZGramQrHelper {

    // Reads QR codes from the photos of the selected message, or its album,
    // that are on screen. The menu waits for the result through waitForQr and
    // onQrDetectionDone, but no longer than 250 ms.
    public static void readQrFromMessage(View parent, MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup, ViewGroup viewGroup, Utilities.Callback<ArrayList<String>> callback, AtomicBoolean waitForQr, AtomicReference<Runnable> onQrDetectionDone) {
        waitForQr.set(true);
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<String> qrResults = new ArrayList<>();
            final ArrayList<MessageObject> messageObjects = new ArrayList<>();
            if (selectedObjectGroup != null) {
                messageObjects.addAll(selectedObjectGroup.messages);
            } else {
                messageObjects.add(selectedObject);
            }
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                final View child = viewGroup.getChildAt(i);
                if (child instanceof ChatMessageCell) {
                    final ChatMessageCell cell = (ChatMessageCell) child;
                    if (messageObjects.contains(cell.getMessageObject())) {
                        qrResults.addAll(readQr(cell.getPhotoImage().getBitmap()));
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                callback.run(qrResults);
                waitForQr.set(false);
                final Runnable done = onQrDetectionDone.getAndSet(null);
                if (done != null) {
                    done.run();
                }
            });
        });
        parent.postDelayed(() -> {
            final Runnable done = onQrDetectionDone.getAndSet(null);
            if (done != null) {
                done.run();
            }
        }, 250);
    }

    public static void showQrDialog(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, ArrayList<String> qrResults) {
        if (fragment == null || fragment.getParentActivity() == null || qrResults == null || qrResults.isEmpty()) {
            return;
        }
        if (qrResults.size() == 1) {
            final String text = qrResults.get(0);
            if (text.startsWith("http://") || text.startsWith("https://")) {
                AlertsCreator.showOpenUrlAlert(fragment, text, true, true, resourcesProvider);
                return;
            }
        }
        final android.app.Activity context = fragment.getParentActivity();
        final LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog dialog = new AlertDialog.Builder(context, resourcesProvider)
                .setView(ll)
                .create();

        for (int i = 0; i < qrResults.size(); i++) {
            final String text = qrResults.get(i);
            final String username = Browser.extractUsername(text);
            final boolean linkOrUsername = username != null || text.startsWith("http://") || text.startsWith("https://");
            final LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setDisablePaddingsOffsetY(true);
            textView.setTextColor(linkOrUsername ? Theme.getColor(Theme.key_dialogTextLink, resourcesProvider) : Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setMaxLines(0);
            textView.setSingleLine(false);
            textView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
            textView.setBackground(Theme.getSelectorDrawable(false));
            if (linkOrUsername) {
                final SpannableStringBuilder sb = new SpannableStringBuilder(text);
                sb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        if (text.startsWith("http://") || text.startsWith("https://")) {
                            AlertsCreator.showOpenUrlAlert(fragment, text, true, false, resourcesProvider);
                        }
                    }
                }, 0, text.length(), 0);
                textView.setOnLinkLongPressListener(span -> textView.performLongClick());
                textView.setText(sb);
            } else {
                textView.setText(text);
                textView.setOnClickListener(v1 -> {
                    AndroidUtilities.addToClipboard(text);
                    BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                });
            }
            textView.setOnLongClickListener(v -> {
                final BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
                builder.setTitle(username != null ? "@" + username : text);
                builder.setItems(linkOrUsername ? new CharSequence[]{LocaleController.getString(R.string.Open), LocaleController.getString(R.string.ShareFile), LocaleController.getString(R.string.Copy)} : new CharSequence[]{null, null, null, LocaleController.getString(R.string.Copy)}, (d, which) -> {
                    if (which == 0) {
                        AlertsCreator.showOpenUrlAlert(fragment, text, true, false, resourcesProvider);
                    } else if (which == 1 || which == 2) {
                        String url1 = text;
                        boolean tel = false;
                        if (url1.startsWith("mailto:")) {
                            url1 = url1.substring(7);
                        } else if (url1.startsWith("tel:")) {
                            url1 = url1.substring(4);
                            tel = true;
                        }
                        if (which == 2) {
                            AndroidUtilities.addToClipboard(url1);
                            final String bulletinMessage;
                            if (tel) {
                                bulletinMessage = LocaleController.getString(R.string.PhoneCopied);
                            } else if (url1.startsWith("#")) {
                                bulletinMessage = LocaleController.getString(R.string.HashtagCopied);
                            } else if (url1.startsWith("@")) {
                                bulletinMessage = LocaleController.getString(R.string.UsernameCopied);
                            } else {
                                bulletinMessage = LocaleController.getString(R.string.LinkCopied);
                            }
                            if (AndroidUtilities.shouldShowClipboardToast()) {
                                BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.voip_invite, bulletinMessage).show();
                            }
                        } else {
                            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_TEXT, url1);
                            final Intent chooserIntent = Intent.createChooser(shareIntent, LocaleController.getString(R.string.ShareFile));
                            chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ApplicationLoader.applicationContext.startActivity(chooserIntent);
                        }
                    } else {
                        AndroidUtilities.addToClipboard(text);
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                    }
                });
                builder.create().show();
                return true;
            });
            ll.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        fragment.showDialog(dialog);
    }

    // Tries the photo as is, then inverted, then inverted and thresholded, so
    // light-on-dark codes are found too.
    public static ArrayList<String> readQr(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return new ArrayList<>();
        }
        final ArrayList<String> results = new ArrayList<>(readQrInternal(bitmap));
        Bitmap inverted = null;
        try {
            if (results.isEmpty()) {
                inverted = invert(bitmap);
                results.addAll(readQrInternal(inverted));
            }
        } catch (Throwable ignored) {
        }
        try {
            if (inverted != null && results.isEmpty()) {
                final Bitmap monochrome = monochrome(inverted);
                results.addAll(readQrInternal(monochrome));
                AndroidUtilities.recycleBitmap(monochrome);
            }
        } catch (Throwable ignored) {
        }
        if (inverted != null) {
            AndroidUtilities.recycleBitmap(inverted);
        }
        return results;
    }

    private static QRCodeMultiReader qrReader;
    private static BarcodeDetector visionQrReader;

    private static ArrayList<String> readQrInternal(Bitmap bitmap) {
        final ArrayList<String> results = new ArrayList<>();
        try {
            if (visionQrReader == null) {
                visionQrReader = new BarcodeDetector.Builder(ApplicationLoader.applicationContext).setBarcodeFormats(Barcode.QR_CODE).build();
            }
            if (visionQrReader.isOperational()) {
                final Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                final SparseArray<Barcode> codes = visionQrReader.detect(frame);
                for (int i = 0; i < codes.size(); i++) {
                    results.add(codes.valueAt(i).rawValue);
                }
            } else {
                if (qrReader == null) {
                    qrReader = new QRCodeMultiReader();
                }
                final int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                final LuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), pixels);
                Result[] codes;
                try {
                    codes = qrReader.decodeMultiple(new BinaryBitmap(new GlobalHistogramBinarizer(source)));
                } catch (NotFoundException e) {
                    codes = null;
                }
                if (codes != null) {
                    for (Result code : codes) {
                        results.add(code.getText());
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return results;
    }

    private static Bitmap invert(Bitmap bitmap) {
        final Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(newBitmap);
        final Paint paint = new Paint();
        final ColorMatrix matrixGrayscale = new ColorMatrix();
        matrixGrayscale.setSaturation(0);
        final ColorMatrix matrixInvert = new ColorMatrix();
        matrixInvert.set(new float[]{
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        });
        matrixInvert.preConcat(matrixGrayscale);
        paint.setColorFilter(new ColorMatrixColorFilter(matrixInvert));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return newBitmap;
    }

    private static Bitmap monochrome(Bitmap bitmap) {
        final Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(newBitmap);
        final Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(createThresholdMatrix(90)));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return newBitmap;
    }

    private static ColorMatrix createThresholdMatrix(int threshold) {
        return new ColorMatrix(new float[]{
                85.f, 85.f, 85.f, 0.f, -255.f * threshold,
                85.f, 85.f, 85.f, 0.f, -255.f * threshold,
                85.f, 85.f, 85.f, 0.f, -255.f * threshold,
                0f, 0f, 0f, 1f, 0f
        });
    }
}
