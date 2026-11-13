/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Online indicator: a dot on the sender's avatar beside messages in groups.
 * Green while the person is online, then yellow, orange and red for the
 * first 15, 30 and 60 minutes after they were last seen; nothing after
 * that, for bots or when the last seen time is hidden.
 * Idea from exteraless (GPL-3.0).
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public final class MZGramOnlineDot {

    private static Paint paint;
    private static Path path;

    private MZGramOnlineDot() {
    }

    /** The colour of the dot for this person now, 0 for none. */
    public static int color(int account, TLRPC.User user, Theme.ResourcesProvider resourcesProvider) {
        if (!MZGramConfig.showOnlineStatus || user == null || user.status == null || user.bot || user.self) {
            return 0;
        }
        if (user.status.expires <= 0 && MessagesController.getInstance(account).onlinePrivacy.containsKey(user.id)) {
            return Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider);
        }
        if (user.status.expires <= 0) {
            return 0;
        }
        int diff = user.status.expires - ConnectionsManager.getInstance(account).getCurrentTime();
        if (diff > 0) {
            return Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider);
        } else if (diff > -15 * 60) {
            return Color.rgb(234, 234, 30);
        } else if (diff > -30 * 60) {
            return Color.rgb(234, 132, 30);
        } else if (diff > -60 * 60) {
            return Color.rgb(234, 30, 30);
        }
        return 0;
    }

    /** Draws the avatar, with the dot cut into its lower right corner when there is one. */
    public static void draw(Canvas canvas, ImageReceiver image, int account, TLRPC.User user, Theme.ResourcesProvider resourcesProvider) {
        int color = color(account, user, resourcesProvider);
        if (color == 0) {
            image.draw(canvas);
            return;
        }
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            path = new Path();
        }
        int radius = dp(7);
        float x = image.getImageX2() - radius;
        float y = image.getImageY2() - radius;
        path.rewind();
        path.addCircle(x, y, radius, Path.Direction.CW);
        path.setFillType(Path.FillType.INVERSE_WINDING);
        canvas.save();
        canvas.clipPath(path);
        image.draw(canvas);
        canvas.restore();
        paint.setColor(color);
        paint.setAlpha((int) (255 * image.getAlpha()));
        canvas.drawCircle(x, y, radius - dp(2.25f), paint);
    }
}
