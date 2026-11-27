package org.mzgram.icons;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstallIconPackBottomSheet extends BottomSheet {

    private static final int PREVIEW_COUNT = 10;
    private static final int PREVIEW_PER_ROW = 5;

    private final BaseFragment fragment;
    private final File archive;
    private final IconPack pack;
    private final LinearLayout previewsContainer;

    public InstallIconPackBottomSheet(BaseFragment fragment, File archive, IconPack pack) {
        super(fragment.getParentActivity(), false);
        this.fragment = fragment;
        this.archive = archive;
        this.pack = pack;

        final Context context = fragment.getParentActivity();
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setGravity(Gravity.CENTER);
        title.setText(pack.getName());
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                22, 18, 22, 0));

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText(buildSubtitle());
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                22, 4, 22, 0));

        previewsContainer = new LinearLayout(context);
        previewsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(previewsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 14, 0, 6));

        TextCell install = new TextCell(context);
        install.setTextAndIcon(getString(R.string.IconPackInstall), R.drawable.msg_add, false);
        install.setOnClickListener(v -> install());
        root.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        setCustomView(root);
        loadPreviews();
    }

    private CharSequence buildSubtitle() {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(pack.getVersion())) {
            parts.add(pack.getVersion());
        }
        parts.add(LocaleController.formatString(R.string.IconPackIconsCount, pack.getIconCount()));
        return TextUtils.join(" • ", parts);
    }


    private void loadPreviews() {
        Utilities.globalQueue.postRunnable(() -> {
            final List<Bitmap> previews = readPreviews();
            AndroidUtilities.runOnUIThread(() -> showPreviews(previews));
        });
    }

    private List<Bitmap> readPreviews() {
        List<Bitmap> result = new ArrayList<>();
        if (archive == null || !archive.exists()) {
            return result;
        }
        try (ZipFile zip = new ZipFile(archive)) {
            for (Map.Entry<String, String> entry : pack.getIcons().entrySet()) {
                if (result.size() >= PREVIEW_COUNT) {
                    break;
                }
                String path = entry.getValue();
                if (TextUtils.isEmpty(path)) {
                    continue;
                }
                ZipEntry zipEntry = findEntry(zip, path);
                if (zipEntry == null || zipEntry.isDirectory()) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(zipEntry)) {
                    Bitmap bitmap;
                    if (IconPackManager.isSvg(path)) {
                        bitmap = IconPackManager.renderSvg(in, AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                    } else {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        bitmap = BitmapFactory.decodeStream(in, null, options);
                    }
                    if (bitmap != null) {
                        result.add(bitmap);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    private static ZipEntry findEntry(ZipFile zip, String path) {
        ZipEntry direct = zip.getEntry(path);
        if (direct != null) {
            return direct;
        }
        String suffix = "/" + path;
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().endsWith(suffix)) {
                return entry;
            }
        }
        return null;
    }

    private void showPreviews(List<Bitmap> previews) {
        if (previewsContainer == null || previews.isEmpty()) {
            return;
        }
        final Context context = previewsContainer.getContext();
        LinearLayout row = null;
        for (int i = 0; i < previews.size(); i++) {
            if (i % PREVIEW_PER_ROW == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                previewsContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageBitmap(previews.get(i));
            row.addView(image, LayoutHelper.createLinear(40, 40, 6, 0, 6, 0));
        }
    }


    private void install() {
        dismiss();
        IconPackManager.getInstance().installPack(archive, error -> {
            if (fragment == null || fragment.getParentActivity() == null) {
                return;
            }
            if (error != null) {
                BulletinFactory.of(fragment).createErrorBulletin(error.getLocalizedMessage()).show();
                return;
            }
            BulletinFactory.of(fragment)
                    .createSimpleBulletin(R.raw.done, getString(R.string.IconPackInstalledToast))
                    .show();
        });
    }

    public static void show(BaseFragment fragment, File archive, IconPack pack) {
        if (fragment == null || fragment.getParentActivity() == null || pack == null) {
            return;
        }
        new InstallIconPackBottomSheet(fragment, archive, pack).show();
    }
}
