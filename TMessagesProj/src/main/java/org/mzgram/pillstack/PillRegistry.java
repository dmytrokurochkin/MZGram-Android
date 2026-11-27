package org.mzgram.pillstack;

import android.content.Context;

import androidx.annotation.Keep;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mzgram.pillstack.pills.BasePill;
import org.mzgram.pillstack.pills.CachePill;
import org.mzgram.pillstack.pills.DcPingPill;
import org.mzgram.pillstack.pills.GhostPill;
import org.mzgram.pillstack.pills.LastSeenPill;
import org.mzgram.pillstack.pills.NetSpeedPill;
import org.mzgram.pillstack.pills.ProxyPill;
import org.mzgram.pillstack.pills.RamPill;
import org.mzgram.pillstack.pills.WeatherPill;

public class PillRegistry {

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    public static class PillInfo {
        public final int id;
        public final CharSequence name;
        /** MZGram: the name's string, read on every call so a language switch renames the pill at once. */
        public final int nameRes;
        public final int iconRes;
        public final int iconColorTop;
        public final int iconColorBottom;
        public final PillCreator creator;

        public PillInfo(int id, CharSequence name, int iconRes, int iconColorTop, int iconColorBottom, PillCreator creator) {
            this(id, name, 0, iconRes, iconColorTop, iconColorBottom, creator);
        }

        public PillInfo(int id, int nameRes, int iconRes, int iconColorTop, int iconColorBottom, PillCreator creator) {
            this(id, LocaleController.getString(nameRes), nameRes, iconRes, iconColorTop, iconColorBottom, creator);
        }

        private PillInfo(int id, CharSequence name, int nameRes, int iconRes, int iconColorTop, int iconColorBottom, PillCreator creator) {
            this.id = id;
            this.name = name;
            this.nameRes = nameRes;
            this.iconRes = iconRes;
            this.iconColorTop = iconColorTop;
            this.iconColorBottom = iconColorBottom;
            this.creator = creator;
        }

        public int id() {
            return id;
        }

        public CharSequence name() {
            return nameRes != 0 ? LocaleController.getString(nameRes) : name;
        }

        public int iconRes() {
            return iconRes;
        }

        public int iconColorTop() {
            return iconColorTop;
        }

        public int iconColorBottom() {
            return iconColorBottom;
        }

        public PillCreator creator() {
            return creator;
        }
    }

    private static final Map<Integer, PillInfo> registry = new LinkedHashMap<>();
    private static volatile boolean batchRegistration;

    static {
        beginTransaction();
        registerDefaultPills();
        endTransaction();
    }

    private static void registerDefaultPills() {
        register(new PillInfo(PillType.WEATHER.id, R.string.PillStackWeather,
                R.drawable.weather_cloudy,
                IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom,
                WeatherPill::new));
        register(new PillInfo(PillType.CACHE.id, R.string.StorageUsage,
                R.drawable.msg_filled_storageusage,
                IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom,
                CachePill::new));
        register(new PillInfo(PillType.PROXY.id, R.string.PillStackProxy,
                R.drawable.drawer_proxy_on,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                ProxyPill::new));
        register(new PillInfo(PillType.GHOST.id, R.string.GhostMode,
                R.drawable.arc_ghost,
                IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                GhostPill::new));
        register(new PillInfo(PillType.LAST_SEEN.id, R.string.PillStackLastSeen,
                R.drawable.msg_online,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                LastSeenPill::new));
        register(new PillInfo(PillType.RAM.id, R.string.PillStackRam,
                R.drawable.pillstack_ram,
                IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom,
                RamPill::new));
        register(new PillInfo(PillType.NET_SPEED.id, R.string.PillStackNetSpeed,
                R.drawable.pillstack_netspeed,
                IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom,
                NetSpeedPill::new));
        register(new PillInfo(PillType.DC_PING.id, R.string.PillStackDcPing,
                R.drawable.pillstack_ping,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                DcPingPill::new));
    }


    @Keep
    public static void beginTransaction() {
        batchRegistration = true;
    }

    @Keep
    public static void endTransaction() {
        batchRegistration = false;
        if (PillStackConfig.isConfigLoaded()) {
            PillStackConfig.sanitizePills();
            PillStackEvents.notifyLayoutChanged();
        }
    }


    public static void register(PillInfo info) {
        if (info == null) {
            return;
        }
        synchronized (registry) {
            registry.put(info.id, info);
        }
        invalidate();
    }

    @Keep
    public static void unregister(int id) {
        boolean removed;
        synchronized (registry) {
            removed = registry.remove(id) != null;
        }
        if (removed) {
            invalidate();
        }
    }

    @Keep
    public static void activatePill(int id) {
        if (!isRegistered(id) || PillStackConfig.getActivePills().contains(id)) {
            return;
        }
        PillStackConfig.setPillActive(id, true);
        PillStackEvents.notifyLayoutChanged();
    }

    private static void invalidate() {
        if (batchRegistration) {
            return;
        }
        PillStackConfig.sanitizePills();
        PillStackEvents.notifyLayoutChanged();
    }

    public static PillInfo getPillInfo(int id) {
        synchronized (registry) {
            return registry.get(id);
        }
    }

    public static Collection<PillInfo> getRegisteredPills() {
        synchronized (registry) {
            return new ArrayList<>(registry.values());
        }
    }

    public static List<Integer> getRegisteredIds() {
        synchronized (registry) {
            return new ArrayList<>(registry.keySet());
        }
    }

    public static boolean isRegistered(int id) {
        synchronized (registry) {
            return registry.containsKey(id);
        }
    }

    public static BasePill createPill(int id, Context context, Theme.ResourcesProvider resourcesProvider) {
        PillInfo info = getPillInfo(id);
        if (info == null) {
            return null;
        }
        try {
            return info.creator.create(context, resourcesProvider);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}
