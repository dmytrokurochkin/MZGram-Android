package org.mzgram.plugins.ui;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;

import org.mzgram.plugins.Plugin;

public final class PluginIcons {

    private PluginIcons() {
    }

    public static boolean apply(BackupImageView imageView, Plugin plugin, Runnable onLoaded) {
        if (imageView == null || plugin == null || TextUtils.isEmpty(plugin.icon)) {
            return false;
        }
        final int slash = plugin.icon.lastIndexOf('/');
        if (slash <= 0 || slash == plugin.icon.length() - 1) {
            return false;
        }
        final String pack = plugin.icon.substring(0, slash);
        final int index;
        try {
            index = Integer.parseInt(plugin.icon.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (index < 0) {
            return false;
        }

        final String tag = "plugin_icon_" + pack + "_" + index;
        imageView.setTag(tag);
        imageView.setImageDrawable(null);
        imageView.setVisibility(android.view.View.GONE);

        TLRPC.TL_inputStickerSetShortName input = new TLRPC.TL_inputStickerSetShortName();
        input.short_name = pack;
        MediaDataController.getInstance(UserConfig.selectedAccount)
                .getStickerSet(input, 0, false, set -> {
                    if (set == null || set.documents == null || index >= set.documents.size()) {
                        return;
                    }
                    if (!tag.equals(imageView.getTag())) {
                        return;
                    }
                    TLRPC.Document document = set.documents.get(index);
                    Drawable thumb = DocumentObject.getSvgThumb(document,
                            Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                    imageView.setImage(ImageLocation.getForDocument(document), "100_100",
                            thumb, 0, document);
                    imageView.setVisibility(android.view.View.VISIBLE);
                    imageView.invalidate();
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                });
        return true;
    }
}
