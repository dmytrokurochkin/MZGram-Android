// Ported from exteraless (GPL-3.0).

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.util.Calendar;

/**
 * Live preview bubble of a deleted message with trash can icon / mark at 12:58.
 */
@SuppressLint("ViewConstructor")
public class MZGramDeletedMarkPreviewCell extends FrameLayout {

    private static final int HORIZONTAL_PADDING_DP = 12;
    private static final int VERTICAL_PADDING_DP = 10;

    private final ChatMessageCell cell;
    private final MessageObject messageObject;
    private final Drawable shadowDrawable;
    private final Theme.ResourcesProvider resourcesProvider;

    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private int previewContentWidth;

    public MZGramDeletedMarkPreviewCell(Context context, BaseFragment fragment) {
        super(context);
        resourcesProvider = fragment != null ? fragment.getResourceProvider() : null;
        setWillNotDraw(false);
        setClipChildren(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setFocusable(false);
        setClickable(false);

        shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom,
                Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));

        messageObject = createMessage();
        cell = createCell(context, messageObject);
        addView(cell);
    }

    private ChatMessageCell createCell(Context context, MessageObject messageObject) {
        ChatMessageCell cell = new ChatMessageCell(context, UserConfig.selectedAccount, false, null, resourcesProvider) {
            @Override
            public int getParentWidth() {
                int width = MZGramDeletedMarkPreviewCell.this.previewContentWidth;
                return width > 0 ? width : super.getParentWidth();
            }
        };
        cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            @Override
            public boolean canPerformActions() {
                return false;
            }
        });
        cell.isChat = false;
        cell.setFullyDraw(true);
        cell.setMessageObject(messageObject, null, false, false, false);
        return cell;
    }

    private MessageObject createMessage() {
        int account = UserConfig.selectedAccount;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 58);
        cal.set(Calendar.SECOND, 0);
        int date = (int) (cal.getTimeInMillis() / 1000L);

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.date = date;
        message.dialog_id = -1;
        message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.id = 1;
        message.message = getString(R.string.MZGramDeletedMarkPreviewText);
        message.media = new TLRPC.TL_messageMediaEmpty();

        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = 777000;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = 777000;
        message.out = false;
        message.mzgramDeleted = true;

        MessageObject obj = new MessageObject(account, message, true, false);
        obj.customReplyName = getString(R.string.MZGramDeletedMarkPreviewName);
        obj.resetLayout();
        return obj;
    }

    public void updatePreview() {
        if (messageObject != null && messageObject.messageOwner != null) {
            messageObject.messageOwner.mzgramDeleted = true;
            messageObject.resetLayout();
        }
        if (cell != null) {
            cell.forceResetMessageObject();
            cell.invalidate();
        }
        invalidate();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (cell != null) {
            cell.invalidate();
        }
    }

    private void measureCell() {
        cell.measure(MeasureSpec.makeMeasureSpec(Math.max(dp(1), previewContentWidth), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int contentWidth = Math.max(dp(1), width - dp(HORIZONTAL_PADDING_DP * 2));
        if (previewContentWidth != contentWidth) {
            previewContentWidth = contentWidth;
            cell.forceResetMessageObject();
        }
        measureCell();

        int height = dp(VERTICAL_PADDING_DP * 2) + cell.getMeasuredHeight();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int childLeft = LocaleController.isRTL
                ? width - dp(HORIZONTAL_PADDING_DP) - cell.getMeasuredWidth()
                : dp(HORIZONTAL_PADDING_DP);
        int childTop = dp(VERTICAL_PADDING_DP);
        cell.layout(childLeft, childTop, childLeft + cell.getMeasuredWidth(), childTop + cell.getMeasuredHeight());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Drawable drawable = Theme.getCachedWallpaperNonBlocking();
        if (drawable == null) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        } else {
            drawable.setAlpha(255);
            if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (drawable instanceof BackgroundGradientDrawable) {
                    backgroundGradientDisposable = ((BackgroundGradientDrawable) drawable).drawExactBoundsSize(canvas, this);
                } else {
                    drawable.draw(canvas);
                }
            } else if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    drawable.setBounds(0, 0,
                            (int) Math.ceil(getMeasuredWidth() / scale),
                            (int) Math.ceil(getMeasuredHeight() / scale));
                } else {
                    float scale = Math.max(
                            getMeasuredWidth() / (float) drawable.getIntrinsicWidth(),
                            getMeasuredHeight() / (float) drawable.getIntrinsicHeight());
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                    int x = (getMeasuredWidth() - width) / 2;
                    int y = (getMeasuredHeight() - height) / 2;
                    canvas.save();
                    canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    drawable.setBounds(x, y, x + width, y + height);
                }
                drawable.draw(canvas);
                canvas.restore();
            }
        }
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
        super.onDetachedFromWindow();
    }
}
