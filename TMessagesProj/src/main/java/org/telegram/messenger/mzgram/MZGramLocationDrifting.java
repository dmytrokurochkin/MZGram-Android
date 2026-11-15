// Ported from exteraless (GPL-3.0).
package org.telegram.messenger.mzgram;

import android.location.Location;
import android.util.Pair;

import org.telegram.messenger.FileLog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MZGramLocationDrifting {

    private static final double PI = 3.14159265358979324;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private static final Set<Integer> recent = Collections.synchronizedSet(Collections.newSetFromMap(new Cache<Integer, Boolean>()));

    private MZGramLocationDrifting() {
    }

    public static boolean isFixActive() {
        return MZGramConfig.mapDriftingFix && MZGramConfig.mapProvider == 0;
    }

    public static void transform(Location location) {
        if (location == null) {
            return;
        }
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();

        if (recent.contains(new Pair<>(latitude, longitude).hashCode())) {
            return;
        }

        final Pair<Double, Double> trans = transform(latitude, longitude);
        location.setLatitude(trans.first);
        location.setLongitude(trans.second);

        recent.add(trans.hashCode());
        FileLog.d(String.format(Locale.US, "MZGramLocationDrifting: %.4f,%.4f => %.4f,%.4f", latitude, longitude, trans.first, trans.second));
    }

    public static Pair<Double, Double> transform(final double wgLat, final double wgLon) {
        if (outOfChina(wgLat, wgLon)) {
            return new Pair<>(wgLat, wgLon);
        }

        double dLat = transformLat(wgLon - 105.0, wgLat - 35.0);
        double dLon = transformLon(wgLon - 105.0, wgLat - 35.0);
        final double radLat = wgLat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        final double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new Pair<>(wgLat + dLat, wgLon + dLon);
    }

    private static boolean outOfChina(final double lat, final double lon) {
        if (lon < 72.004 || lon > 137.8347) {
            return true;
        } else {
            return lat < 0.8293 || lat > 55.8271;
        }
    }

    private static double transformLat(final double x, final double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(final double x, final double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    private static class Cache<K, V> extends LinkedHashMap<K, V> {
        private static final int MAX_ENTRIES = 128;
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
            return size() > MAX_ENTRIES;
        }
    }
}
