package org.mzgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mzgram.core.ui.icons.IconsResources;

public class IconPackManager {

    private static final IconPackManager instance = new IconPackManager();

    public static IconPackManager getInstance() {
        return instance;
    }

    private final CopyOnWriteArrayList<IconPack> activePacks = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, IconPack> iconOwnerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> resourceNames = new ConcurrentHashMap<>();
    private final Set<Integer> missing = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final LruCache<Long, Bitmap> resolvedCache;

    private volatile boolean initialized;
    private volatile boolean hasReplacements;

    private IconPackManager() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        resolvedCache = new LruCache<Long, Bitmap>(Math.max(2048, maxMemory / 16)) {
            @Override
            protected int sizeOf(Long key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }


    public void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            initialized = true;
        }
        reloadInternal();
    }

    public void reload() {
        initialized = true;
        Utilities.globalQueue.postRunnable(this::reloadInternal);
    }

    public void reloadInternal() {
        try {
            IconPacksConfig.loadConfig(false);
            List<IconPack> packs = new ArrayList<>();
            if (IconPacksConfig.enabled()) {
                for (String id : IconPacksConfig.getActivePackIds()) {
                    IconPack pack = IconPackStorage.findPackById(id);
                    if (pack != null) {
                        packs.add(pack);
                    }
                }
            }
            synchronized (this) {
                activePacks.clear();
                activePacks.addAll(packs);
                rebuildOwnerMap();
                resolvedCache.evictAll();
                missing.clear();
            }
            launchPrewarm();
        } catch (Throwable t) {
            FileLog.e("mzGram: failed to load icon packs", t);
        }
    }

    private void rebuildOwnerMap() {
        iconOwnerMap.clear();
        for (int i = activePacks.size() - 1; i >= 0; i--) {
            IconPack pack = activePacks.get(i);
            for (Map.Entry<String, String> entry : pack.getIcons().entrySet()) {
                if (isBlacklisted(entry.getKey())) {
                    continue;
                }
                iconOwnerMap.put(entry.getKey(), pack);
            }
        }
        hasReplacements = !iconOwnerMap.isEmpty();
    }

    public List<IconPack> getActivePacks() {
        return new ArrayList<>(activePacks);
    }

    public boolean hasReplacements() {
        return hasReplacements;
    }


    @Nullable
    public Drawable getDrawable(IconsResources resources, int resId, int density, @Nullable Resources.Theme theme) {
        if (!initialized) {
            ensureInitialized();
        }
        org.mzgram.icons.picker.IconObserver.log(resId);
        Drawable fromPack = getUserPackDrawable(resources, resId, density, theme);
        if (fromPack != null) {
            return fromPack;
        }
        return getBasePackDrawable(resources, resId, density, theme);
    }

    @Nullable
    private Drawable getBasePackDrawable(@Nullable IconsResources resources, int resId,
                                         int density, @Nullable Resources.Theme theme) {
        if (resources == null || resId == 0) {
            return null;
        }
        int mapped = BaseIconPacks.getIcon(resId);
        if (mapped == resId || mapped == 0) {
            return null;
        }
        try {
            return resources.getOriginalDrawableForDensity(mapped, density, theme);
        } catch (Throwable t) {
            FileLog.e("mzGram: base icon pack lookup failed", t);
            return null;
        }
    }

    @Nullable
    private Drawable getUserPackDrawable(IconsResources resources, int resId, int density,
                                         @Nullable Resources.Theme theme) {
        if (!hasReplacements || resId == 0) {
            return null;
        }
        if (missing.contains(resId)) {
            return null;
        }
        try {
            if (density == 0) {
                density = AndroidUtilities.displayMetrics.densityDpi;
            }
            long cacheKey = cacheKey(resId, density);
            Bitmap cached;
            synchronized (this) {
                cached = resolvedCache.get(cacheKey);
            }
            if (cached != null && !cached.isRecycled()) {
                return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), cached);
            }

            String name = getResourceName(resources, resId);
            if (name == null) {
                missing.add(resId);
                return null;
            }
            IconPack pack = iconOwnerMap.get(name);
            if (pack == null) {
                missing.add(resId);
                return null;
            }
            String relative = pack.getIcons().get(name);
            if (relative == null) {
                missing.add(resId);
                return null;
            }
            File file = IconPackStorage.resolveIconFile(pack, relative);
            if (file == null) {
                missing.add(resId);
                return null;
            }
            Bitmap bitmap = createBitmapFromFile(resources, file.getAbsolutePath(), resId, density, theme);
            if (bitmap == null) {
                missing.add(resId);
                return null;
            }
            synchronized (this) {
                resolvedCache.put(cacheKey, bitmap);
            }
            return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap);
        } catch (Throwable t) {
            FileLog.e("mzGram: icon pack lookup failed", t);
            return null;
        }
    }

    @Nullable
    public Drawable getPackIconDrawable(IconPack pack, int resId) {
        try {
            Resources res = ApplicationLoader.applicationContext.getResources();
            IconsResources iconsResources = res instanceof IconsResources ? (IconsResources) res : null;
            String name = getResourceName(iconsResources, resId);
            if (name == null) {
                return null;
            }
            String relative = pack.getIcons().get(name);
            if (relative == null) {
                return null;
            }
            File file = IconPackStorage.resolveIconFile(pack, relative);
            if (file == null) {
                return null;
            }
            Bitmap bitmap = createBitmapFromFile(iconsResources, file.getAbsolutePath(), resId,
                    AndroidUtilities.displayMetrics.densityDpi, null);
            return bitmap == null ? null : new BitmapDrawable(res, bitmap);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private String getResourceName(@Nullable IconsResources resources, int resId) {
        String cachedName = resourceNames.get(resId);
        if (cachedName != null) {
            return cachedName;
        }
        try {
            Resources res = resources != null ? resources : ApplicationLoader.applicationContext.getResources();
            String name = res.getResourceEntryName(resId);
            if (name != null) {
                resourceNames.put(resId, name);
            }
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    private static long cacheKey(int resId, int density) {
        return ((long) resId << 32) | (density & 0xffffffffL);
    }

    @Nullable
    private Bitmap createBitmapFromFile(@Nullable IconsResources resources, String path, int originalResId,
                                        int density, @Nullable Resources.Theme theme) {
        try {
            Drawable original = null;
            if (resources != null) {
                original = resources.getOriginalDrawableForDensity(originalResId, density, theme);
            }
            int width = Math.max(1, original != null ? original.getIntrinsicWidth() : AndroidUtilities.dp(24));
            int height = Math.max(1, original != null ? original.getIntrinsicHeight() : AndroidUtilities.dp(24));

            if (isSvg(path)) {
                try (InputStream in = new FileInputStream(path)) {
                    Bitmap bitmap = renderSvg(in, width, height);
                    if (bitmap != null) {
                        bitmap.setDensity(density);
                    }
                    return bitmap;
                }
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            int outWidth = options.outWidth;
            int outHeight = options.outHeight;
            if (outWidth <= 0 || outHeight <= 0 || (long) outWidth * outHeight > 100_000_000L) {
                return null;
            }

            options.inSampleSize = 1;
            if (outHeight > height || outWidth > width) {
                int halfHeight = outHeight / 2;
                int halfWidth = outWidth / 2;
                while (halfHeight / options.inSampleSize >= height && halfWidth / options.inSampleSize >= width) {
                    options.inSampleSize *= 2;
                }
            }
            long maxPixels = Math.max((long) width * height, 1024L * 1024L);
            while (true) {
                int sample = options.inSampleSize;
                long w = (outWidth + sample - 1L) / sample;
                long h = (outHeight + sample - 1L) / sample;
                if (w * h <= maxPixels) {
                    break;
                }
                options.inSampleSize = sample * 2;
            }
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeFile(path, options);
            if (decoded == null) {
                return null;
            }
            if (decoded.getWidth() == width && decoded.getHeight() == height) {
                decoded.setDensity(density);
                return decoded;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
            if (scaled != decoded) {
                decoded.recycle();
            }
            scaled.setDensity(density);
            return scaled;
        } catch (OutOfMemoryError e) {
            FileLog.e("mzGram: out of memory loading icon " + path);
            return null;
        } catch (Throwable t) {
            FileLog.e("mzGram: error loading icon " + path, t);
            return null;
        }
    }

    public static boolean isSvg(@Nullable String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".svg");
    }

    @Nullable
    public static Bitmap renderSvg(InputStream in, int width, int height) {
        try {
            SVG svg = SVG.getFromInputStream(in);
            RectF viewBox = svg.getDocumentViewBox();
            if (viewBox == null || viewBox.width() <= 0 || viewBox.height() <= 0) {
                float documentWidth = svg.getDocumentWidth();
                float documentHeight = svg.getDocumentHeight();
                if (documentWidth <= 0 || documentHeight <= 0) {
                    return null;
                }
                svg.setDocumentViewBox(0, 0, documentWidth, documentHeight);
            }
            svg.setDocumentWidth(width);
            svg.setDocumentHeight(height);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new Canvas(bitmap));
            return bitmap;
        } catch (Throwable t) {
            FileLog.e("mzGram: cannot render svg icon", t);
            return null;
        }
    }


    public void installPack(File archive, InstallCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            IconPackStorageError error = IconPackStorage.installPack(archive);
            if (error == null) {
                reloadInternal();
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onResult(error);
                }
            });
        });
    }

    public void deletePack(String packId) {
        IconPacksConfig.forgetPack(packId);
        Utilities.globalQueue.postRunnable(() -> {
            IconPackStorage.deletePack(packId);
            reloadInternal();
        });
    }

    public interface InstallCallback {
        void onResult(@Nullable IconPackStorageError error);
    }


    @Nullable
    public String getResourceName(int resId) {
        return getResourceName((IconsResources) null, resId);
    }

    private static final java.util.Map<String, String> RESOURCE_ALIASES = new java.util.HashMap<>();

    static {
        RESOURCE_ALIASES.put("ic_ab_search", "outline_header_search");
        RESOURCE_ALIASES.put("message", "filled_profile_message_24");
        RESOURCE_ALIASES.put("mute", "filled_profile_mute_24");
        RESOURCE_ALIASES.put("unmute", "filled_profile_unmute_24");
        RESOURCE_ALIASES.put("video", "filled_profile_video_24");
        RESOURCE_ALIASES.put("block", "filled_profile_stop_24");
        RESOURCE_ALIASES.put("join", "filled_profile_member_24");
        RESOURCE_ALIASES.put("story", "filled_profile_story");
        RESOURCE_ALIASES.put("msg_bots", "msg_bot");
    }

    public int getResourceId(String name) {
        if (name == null) {
            return 0;
        }
        int id = resolveResourceId(name);
        if (id != 0) {
            return id;
        }
        String alias = RESOURCE_ALIASES.get(name);
        return alias == null ? 0 : resolveResourceId(alias);
    }

    private int resolveResourceId(String name) {
        try {
            return ApplicationLoader.applicationContext.getResources()
                    .getIdentifier(name, "drawable", ApplicationLoader.applicationContext.getPackageName());
        } catch (Throwable t) {
            return 0;
        }
    }

    private volatile java.util.LinkedHashMap<String, Integer> systemIcons;

    public java.util.Map<String, Integer> getSystemIcons() {
        java.util.LinkedHashMap<String, Integer> cached = systemIcons;
        if (cached != null) {
            return cached;
        }
        java.util.TreeMap<String, Integer> collected = new java.util.TreeMap<>();
        try {
            Resources res = ApplicationLoader.applicationContext.getResources();
            int typeMask = org.telegram.messenger.R.drawable.msg_edit & 0xffff0000;
            int misses = 0;
            for (int entry = 0; entry < 0x10000 && misses < 64; entry++) {
                String name;
                try {
                    name = res.getResourceEntryName(typeMask | entry);
                } catch (Throwable notFound) {
                    misses++;
                    continue;
                }
                misses = 0;
                if (name == null || isBlacklisted(name)) {
                    continue;
                }
                collected.put(name, typeMask | entry);
            }
        } catch (Throwable t) {
            FileLog.e("mzGram: cannot enumerate app icons", t);
        }
        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>(collected);
        if (!result.isEmpty()) {
            systemIcons = result;
        }
        return result;
    }

    public boolean saveCustomIcon(String packId, int resId, File source) {
        IconPack pack = IconPackStorage.findPackById(packId);
        String resourceName = getResourceName(resId);
        if (pack == null || source == null || !source.isFile() || resourceName == null) {
            return false;
        }
        if (isBlacklisted(resourceName)) {
            return false;
        }
        try {
            String extension = extensionOf(source.getName());
            File packDir = new File(IconPackStorage.getIconPacksDirectory(), pack.getId());
            //noinspection ResultOfMethodCallIgnored
            packDir.mkdirs();
            File destination = new File(packDir, resourceName + "." + extension);

            String previous = pack.getIcons().get(resourceName);
            copy(source, destination);

            Map<String, String> icons = new java.util.LinkedHashMap<>(pack.getIcons());
            icons.put(resourceName, destination.getName());
            IconPack updated = new IconPack(pack.getId(), pack.getName(), pack.getAuthor(), pack.getVersion(), icons, pack.getLocation());
            if (!IconPackStorage.saveIconPackMetadata(updated)) {
                return false;
            }
            if (previous != null && !previous.equals(destination.getName())) {
                File old = IconPackStorage.resolveIconFile(pack, previous);
                if (old != null) {
                    //noinspection ResultOfMethodCallIgnored
                    old.delete();
                }
            }
            invalidateIconCaches(resId);
            reloadInternal();
            return true;
        } catch (Throwable t) {
            FileLog.e("mzGram: failed to save custom icon", t);
            return false;
        }
    }

    public boolean resetCustomIcon(String packId, int resId) {
        IconPack pack = IconPackStorage.findPackById(packId);
        String resourceName = getResourceName(resId);
        if (pack == null || resourceName == null) {
            return false;
        }
        String relative = pack.getIcons().get(resourceName);
        if (relative == null) {
            return false;
        }
        try {
            File file = IconPackStorage.resolveIconFile(pack, relative);
            Map<String, String> icons = new java.util.LinkedHashMap<>(pack.getIcons());
            icons.remove(resourceName);
            IconPack updated = new IconPack(pack.getId(), pack.getName(), pack.getAuthor(), pack.getVersion(), icons, pack.getLocation());
            if (!IconPackStorage.saveIconPackMetadata(updated)) {
                return false;
            }
            if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            invalidateIconCaches(resId);
            reloadInternal();
            return true;
        } catch (Throwable t) {
            FileLog.e("mzGram: failed to reset custom icon", t);
            return false;
        }
    }

    public void invalidateIconCaches(int resId) {
        missing.remove(resId);
        synchronized (this) {
            resolvedCache.evictAll();
        }
    }

    @Nullable
    public IconPack createPack(String packId, String name, String author, String version) {
        if (!IconPackStorage.isValidPackId(packId)) {
            return null;
        }
        IconPack pack = new IconPack(packId, name, author, version,
                java.util.Collections.emptyMap(), new File(IconPackStorage.getIconPacksDirectory(), packId));
        if (!IconPackStorage.saveIconPackMetadata(pack)) {
            return null;
        }
        reloadInternal();
        return IconPackStorage.findPackById(packId);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        String ext = dot > 0 ? fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
        if (!Arrays.asList("png", "webp", "jpg", "jpeg", "svg").contains(ext)) {
            ext = "png";
        }
        return ext;
    }

    private static void copy(File from, File to) throws java.io.IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }


    private volatile boolean prewarmScheduled;

    public void launchPrewarm() {
        if (prewarmScheduled || !IconPacksConfig.enabled()) {
            return;
        }
        prewarmScheduled = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                ensureInitialized();
                Resources res = ApplicationLoader.applicationContext.getResources();
                IconsResources iconsResources = res instanceof IconsResources ? (IconsResources) res : null;
                int density = AndroidUtilities.displayMetrics.densityDpi;
                int warmed = 0;
                for (String name : new ArrayList<>(iconOwnerMap.keySet())) {
                    int resId = getResourceId(name);
                    if (resId == 0) {
                        continue;
                    }
                    long key = cacheKey(resId, density);
                    synchronized (this) {
                        if (resolvedCache.get(key) != null) {
                            continue;
                        }
                    }
                    IconPack pack = iconOwnerMap.get(name);
                    if (pack == null) {
                        continue;
                    }
                    String relative = pack.getIcons().get(name);
                    File file = relative == null ? null : IconPackStorage.resolveIconFile(pack, relative);
                    if (file == null) {
                        continue;
                    }
                    Bitmap bitmap = createBitmapFromFile(iconsResources, file.getAbsolutePath(), resId, density, null);
                    if (bitmap == null) {
                        continue;
                    }
                    synchronized (this) {
                        resolvedCache.put(key, bitmap);
                    }
                    warmed++;
                    if (warmed % 32 == 0) {
                        try {
                            Thread.sleep(4);
                        } catch (InterruptedException ignore) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e("mzGram: icon prewarm failed", t);
            } finally {
                prewarmScheduled = false;
            }
        });
    }


    @Nullable
    public Bitmap decodeForNotification(String path, int originalResId, int density) {
        Resources res = ApplicationLoader.applicationContext.getResources();
        IconsResources iconsResources = res instanceof IconsResources ? (IconsResources) res : null;
        return createBitmapFromFile(iconsResources, path, originalResId, density, null);
    }

    @Nullable
    public androidx.core.graphics.drawable.IconCompat getNotificationIcon() {
        if (!IconPacksConfig.enabled() || !IconPacksConfig.customNotificationIcon()) {
            return null;
        }
        try {
            ensureInitialized();
            String name = getResourceName(org.telegram.messenger.R.drawable.notification);
            if (name == null) {
                return null;
            }
            IconPack pack = iconOwnerMap.get(name);
            if (pack == null) {
                return null;
            }
            android.net.Uri uri = IconPackProvider.getIconUri(pack.getId(), name);
            return uri == null ? null : androidx.core.graphics.drawable.IconCompat.createWithContentUri(uri);
        } catch (Throwable t) {
            FileLog.e("mzGram: failed to build notification icon", t);
            return null;
        }
    }


    public static boolean isIconPack(org.telegram.messenger.MessageObject messageObject) {
        if (messageObject == null || messageObject.getDocument() == null) {
            return false;
        }
        String name = messageObject.getDocumentName();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(IconPackStorage.PACK_EXTENSION);
    }

    public void handleIconPack(org.telegram.ui.ActionBar.BaseFragment fragment, File file) {
        if (fragment == null || fragment.getParentActivity() == null || file == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            IconPack parsed = IconPackStorage.parsePackFromArchive(file);
            AndroidUtilities.runOnUIThread(() -> {
                if (fragment.getParentActivity() == null) {
                    return;
                }
                if (parsed == null) {
                    org.telegram.ui.Components.BulletinFactory.of(fragment)
                            .createErrorBulletin(org.telegram.messenger.LocaleController.getString(
                                    org.telegram.messenger.R.string.IconPackErrorInvalidArchive))
                            .show();
                    return;
                }
                InstallIconPackBottomSheet.show(fragment, file, parsed);
            });
        });
    }


    private static final Set<String> BLACKLIST = new HashSet<>(Arrays.asList(
            "bar_selector", "blockpanel", "circle", "circle_big", "dice", "dino_pic",
            "camera_btn", "cancel_big", "etg_splash", "ev_minus", "ev_plus",
            "chats_archive_arrow", "chats_archive_box", "chats_archive_muted", "chats_archive_pin",
            "chats_widget_preview", "contacts_widget_preview", "fast_scroll_empty", "field_carret_empty"
    ));

    public static boolean isBlacklisted(String name) {
        if (name == null) {
            return true;
        }
        if (BLACKLIST.contains(name)) {
            return true;
        }
        return name.contains("avd")
                || name.startsWith("abc_")
                || name.endsWith("_solar")
                || name.endsWith("_remix")
                || name.contains("$")
                || name.contains("animationpin")
                || name.contains("googlepay")
                || name.contains("shadow")
                || name.startsWith("ic_monochrome")
                || name.startsWith("nocover")
                || name.startsWith("gradient_")
                || name.startsWith("stickers_back_")
                || name.startsWith("media_doc_")
                || name.startsWith("loading_animation")
                || name.startsWith("intro_")
                || name.startsWith("minibubble_")
                || name.startsWith("book_")
                || name.startsWith("call_")
                || name.startsWith("groupsintro")
                || name.startsWith("profile_level")
                || name.startsWith("widget_")
                || name.startsWith("zoom_slide")
                || name.startsWith("zoom_round")
                || name.startsWith("popup_fixed_alert")
                || name.startsWith("search_dark")
                || name.startsWith("bar_selector");
    }
}
