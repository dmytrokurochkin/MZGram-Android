// Ported from exteraless (GPL-3.0).

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.io.File;
import java.util.Objects;

/**
 * Cell for emoji pack selection and download management.
 */
@SuppressLint("ViewConstructor")
public class MZGramEmojiSetCell extends FrameLayout {

    public static class EmojiPackBase {
        protected String packName;
        protected String packId;
        protected String fileLocation;
        private String preview;
        protected long fileSize;

        public EmojiPackBase() {
            this(null, null, null, null, 0);
        }

        public EmojiPackBase(String packName, String packId, String fileLocation, String preview, long fileSize) {
            this.packName = packName;
            this.packId = packId;
            this.fileLocation = fileLocation;
            this.preview = preview;
            this.fileSize = fileSize;
        }

        public void loadFromFile(File file) {
            String fileName = file.getName();
            int versionSep = fileName.lastIndexOf("_v");
            if (versionSep == -1) {
                packName = fileName;
                packId = fileName;
            } else {
                packName = fileName.substring(0, versionSep);
                packId = packName;
            }
            File fileFont = new File(file, packName + ".ttf");
            fileLocation = fileFont.getAbsolutePath();
            preview = file.getAbsolutePath() + "/preview.png";
            fileSize = fileFont.length();
        }

        public String getPackName() {
            return packName;
        }

        public String getPackId() {
            return packId;
        }

        public String getFileLocation() {
            return fileLocation;
        }

        public String getPreview() {
            return preview;
        }

        public Long getFileSize() {
            return fileSize;
        }
    }

    public static class EmojiPackInfo extends EmojiPackBase {
        private int flags;
        private int previewId;
        private int fileId;
        private int packVersion;

        private TLRPC.Document previewDocument;
        private TLRPC.Document fileDocument;

        public EmojiPackInfo(String packName, int fileId, int previewId, String packId, int packVersion) {
            super(packName, Objects.equals(packId, "apple") ? "default" : packId, null, null, 0);
            this.previewId = previewId;
            this.fileId = fileId;
            this.packVersion = packVersion;
        }

        public EmojiPackInfo() {
        }

        public TLRPC.Document getPreviewDocument() {
            return previewDocument;
        }

        public void setPreviewDocument(TLRPC.Document document) {
            this.previewDocument = document;
        }

        public TLRPC.Document getFileDocument() {
            return fileDocument;
        }

        public void setFileDocument(TLRPC.Document document) {
            this.fileDocument = document;
        }

        public int getPackVersion() {
            return packVersion;
        }

        public static EmojiPackInfo deserialize(AbstractSerializedData stream) {
            EmojiPackInfo pack = new EmojiPackInfo();
            pack.flags = stream.readInt32(false);
            pack.packId = stream.readString(false);
            pack.packName = stream.readString(false);

            pack.fileId = stream.readInt32(false);
            pack.previewId = stream.readInt32(false);
            pack.packVersion = stream.readInt32(false);
            if ((pack.flags & 1) != 0) {
                pack.previewDocument = TLRPC.Document.TLdeserialize(stream, stream.readInt32(false), false);
                pack.fileSize = stream.readInt64(false);
                pack.fileLocation = stream.readString(false);
            }
            if ((pack.flags & 2) != 0) {
                pack.fileDocument = TLRPC.Document.TLdeserialize(stream, stream.readInt32(false), false);
            }
            return pack;
        }

        public void serializeToStream(AbstractSerializedData serializedData) {
            serializedData.writeInt32(flags);
            serializedData.writeString(packId);
            serializedData.writeString(packName);

            serializedData.writeInt32(fileId);
            serializedData.writeInt32(previewId);
            serializedData.writeInt32(packVersion);
            if ((flags & 1) != 0) {
                previewDocument.serializeToStream(serializedData);
                serializedData.writeInt64(fileSize);
                serializedData.writeString(fileLocation);
            }
            if ((flags & 2) != 0) {
                fileDocument.serializeToStream(serializedData);
            }
        }
    }

    private final TextView textView;
    private final AnimatedTextView valueTextView;
    private final BackupImageView imageView;

    private ImageView optionsButton;
    private RadialProgressView radialProgress;
    private CheckBox2 checkBox;

    private EmojiPackBase pack;
    private boolean needDivider;
    private final boolean selection;

    public MZGramEmojiSetCell(Context context, boolean selection) {
        super(context);
        this.selection = selection;

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        imageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(8));
        boolean rtl = selection == LocaleController.isRTL;
        addView(imageView, LayoutHelper.createFrame(40, 40, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                rtl ? 0 : !selection ? 21 : 13, 9, rtl ? !selection ? 21 : 13 : 0, 0));

        if (selection) {
            optionsButton = new ImageView(context);
            optionsButton.setFocusable(false);
            optionsButton.setScaleType(ImageView.ScaleType.CENTER);
            optionsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
            optionsButton.setImageResource(R.drawable.floating_check);
            optionsButton.setVisibility(GONE);
            addView(optionsButton, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP,
                    (LocaleController.isRTL ? 10 : 0), 9, (LocaleController.isRTL ? 0 : 10), 0));

            radialProgress = new RadialProgressView(context);
            radialProgress.setNoProgress(false);
            radialProgress.setProgressColor(Theme.getColor(Theme.key_featuredStickers_addedIcon));
            radialProgress.setStrokeWidth(2.8F);
            radialProgress.setSize(AndroidUtilities.dp(30));
            radialProgress.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40)));
            radialProgress.setVisibility(INVISIBLE);
            addView(radialProgress, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP,
                    (LocaleController.isRTL ? 10 : 0), 9, (LocaleController.isRTL ? 0 : 10), 0));
        }

        textView = new AppCompatTextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(textView);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        if (selection) {
            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        }
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START, !selection ? 21 : 71, 9, 70, 0));

        valueTextView = new AnimatedTextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        valueTextView.setAnimationProperties(.55f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        valueTextView.setTextSize(AndroidUtilities.dp(13));
        valueTextView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(valueTextView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START, !selection ? 21 : 71, 25, 70, 0));

        if (selection) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrameRelatively(24, 24, Gravity.START, 34, 30, 0, 0));
        }
    }

    public void setData(EmojiPackBase emojiPackInfo, boolean animated, boolean divider) {
        needDivider = divider;
        if (selection) {
            textView.setText(emojiPackInfo != null ? emojiPackInfo.getPackName() : "");
            pack = emojiPackInfo;
            if (pack != null && "default".equals(pack.getPackId())) {
                // Telegram's own text: default emoji set label
                valueTextView.setText(getString(R.string.Default), animated);
            } else {
                valueTextView.setText(getString(R.string.MZGramInstalledEmojiSet), animated);
            }
            setPackPreview(pack);
        } else {
            textView.setText(getString(R.string.MZGramEmojiSets));
            if (emojiPackInfo == null) {
                // Telegram's own text: default emoji set label
                valueTextView.setText(getString(R.string.Default), animated);
                imageView.setImageBitmap(null);
            } else {
                valueTextView.setText(emojiPackInfo.getPackName(), animated);
                setPackPreview(emojiPackInfo);
            }
        }
    }

    private void setPackPreview(EmojiPackBase pack) {
        if (pack == null) {
            imageView.setImageBitmap(null);
            return;
        }
        if (pack instanceof EmojiPackInfo) {
            TLRPC.Document document = ((EmojiPackInfo) pack).getPreviewDocument();
            if (document != null) {
                java.util.ArrayList<TLRPC.PhotoSize> thumbs = document.thumbs;
                BitmapDrawable strippedThumb = null;
                for (int a = 0, N = thumbs != null ? thumbs.size() : 0; a < N; a++) {
                    TLRPC.PhotoSize photoSize = thumbs.get(a);
                    if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                        strippedThumb = new BitmapDrawable(ApplicationLoader.applicationContext.getResources(),
                                ImageLoader.getStrippedPhotoBitmap(photoSize.bytes, "b"));
                        break;
                    }
                }
                if (strippedThumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(document), "146_146", strippedThumb, pack);
                } else if (thumbs != null) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 146);
                    imageView.setImage(ImageLocation.getForDocument(document), "146_146",
                            ImageLocation.getForDocument(thumb, document), "146_146_B", thumb != null ? thumb.size : 0, pack);
                }
            }
        } else if (pack.getPreview() != null) {
            imageView.setImage(pack.getPreview(), null, null);
        }
    }

    public void setChecked(boolean checked, boolean animated) {
        if (optionsButton == null) return;
        if (animated) {
            optionsButton.animate().cancel();
            optionsButton.animate().setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!checked) {
                        optionsButton.setVisibility(INVISIBLE);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    if (checked) {
                        optionsButton.setVisibility(VISIBLE);
                    }
                }
            }).alpha(checked ? 1 : 0).scaleX(checked ? 1 : 0.1f).scaleY(checked ? 1 : 0.1f).setDuration(150).start();
        } else {
            optionsButton.setVisibility(checked ? VISIBLE : INVISIBLE);
            optionsButton.setScaleX(checked ? 1f : 0.1f);
            optionsButton.setScaleY(checked ? 1f : 0.1f);
            optionsButton.setAlpha(checked ? 1f : 0f);
        }
    }

    public void setSelected(boolean selected, boolean animated) {
        if (checkBox != null) {
            checkBox.setChecked(selected, animated);
        }
    }

    public boolean isChecked() {
        return optionsButton != null && optionsButton.getVisibility() == VISIBLE;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(!selection ? 21 : 71),
                    getHeight() - 1,
                    getWidth() - getPaddingRight() - (LocaleController.isRTL ? AndroidUtilities.dp(!selection ? 21 : 71) : 0),
                    getHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setProgress(boolean visible, boolean animated) {
        if (radialProgress == null) return;
        if (animated) {
            radialProgress.animate().cancel();
            radialProgress.animate().setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        radialProgress.setVisibility(INVISIBLE);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) {
                        radialProgress.setVisibility(VISIBLE);
                    }
                }
            }).alpha(visible ? 1 : 0).setDuration(150).start();
        } else {
            radialProgress.setVisibility(visible ? VISIBLE : INVISIBLE);
        }
    }

    public void setProgress(float percentage, long downBytes, boolean animated) {
        if (radialProgress != null) {
            radialProgress.setProgress(percentage);
        }
        if (pack != null) {
            // Telegram's own text: accessibility download progress format
            valueTextView.setText(LocaleController.formatString(
                    R.string.AccDescrDownloadProgress,
                    AndroidUtilities.formatFileSize(downBytes),
                    AndroidUtilities.formatFileSize(pack.getFileSize())
            ), animated);
        }
    }

    public EmojiPackBase getPack() {
        return pack;
    }
}
