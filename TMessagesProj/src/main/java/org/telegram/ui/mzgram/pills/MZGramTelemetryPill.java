/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Pills of the device's telemetry (navigation.pills-telemetry): MZGram's
 * share of the processor, the memory in use, MZGram's network speed and
 * the ping to Telegram's data centre. Each one is read on the device every
 * two seconds while the pill is on screen; nothing is sent anywhere. The
 * ping is the one MZGram's connection to Telegram already measures
 * (ConnectionsManager.native_getCurrentPingTime): no extra requests, and
 * no server other than Telegram's.
 *
 * A tap reads the value at once.
 *
 * Idea from exteraless TelemetryPill, CpuPill, RamPill, NetSpeedPill and
 * DcPingPill (GPL-3.0); written anew for MZGram. The processor share is
 * MZGram's own (Process.getElapsedCpuTime): since Android 8 an app may no
 * longer read the whole device's /proc/stat.
 */

package org.telegram.ui.mzgram.pills;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.net.TrafficStats;
import android.os.Process;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public abstract class MZGramTelemetryPill extends MZGramPill {

    public static final long INTERVAL = 2000;

    protected MZGramTelemetryPill(Context context, Theme.ResourcesProvider resourcesProvider, int icon) {
        super(context, resourcesProvider);
        setIcon(icon);
        setText("...", false);
    }

    /** The value to show, or null while there is nothing to show yet. */
    protected abstract String read();

    @Override
    public long refreshInterval() {
        return INTERVAL;
    }

    @Override
    public void update(boolean animated) {
        String value;
        try {
            value = read();
        } catch (Throwable e) {
            value = null;
        }
        if (value == null) {
            // Keep the last value until there is a new one
            CharSequence old = textView.getText();
            if (old != null && old.length() > 0) {
                return;
            }
            value = "...";
        }
        setText(value, animated);
    }

    @Override
    public void onTap() {
        update(true);
    }

    /** MZGram's share of the processor since the last reading, all cores together. */
    public static int cpuShare(long cpuMs, long wallMs, int cores) {
        if (wallMs <= 0 || cores <= 0 || cpuMs < 0) {
            return 0;
        }
        long share = cpuMs * 100 / (wallMs * cores);
        return (int) Math.max(0, Math.min(100, share));
    }

    /** Bytes per second from two byte counters; 0 when a counter went back (a reset). */
    public static long perSecond(long bytesBefore, long bytesNow, long wallMs) {
        if (wallMs <= 0 || bytesNow < bytesBefore) {
            return 0;
        }
        return (bytesNow - bytesBefore) * 1000 / wallMs;
    }

    @SuppressLint("ViewConstructor")
    public static class Cpu extends MZGramTelemetryPill {

        private long lastCpu = -1;
        private long lastWall;

        public Cpu(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider, R.drawable.msg_stats);
        }

        @Override
        public int pillId() {
            return MZGramPills.CPU;
        }

        @Override
        protected String read() {
            long cpu = Process.getElapsedCpuTime();
            long wall = SystemClock.elapsedRealtime();
            String value = null;
            if (lastCpu >= 0 && wall - lastWall >= 200) {
                value = cpuShare(cpu - lastCpu, wall - lastWall, Runtime.getRuntime().availableProcessors()) + "%";
            } else if (lastCpu >= 0) {
                return null;
            }
            lastCpu = cpu;
            lastWall = wall;
            return value;
        }
    }

    @SuppressLint("ViewConstructor")
    public static class Ram extends MZGramTelemetryPill {

        public Ram(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider, R.drawable.msg2_devices);
        }

        @Override
        public int pillId() {
            return MZGramPills.RAM;
        }

        @Override
        protected String read() {
            ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            if (info.totalMem <= 0) {
                return null;
            }
            // The device's memory in use, of all of it
            return AndroidUtilities.formatFileSize(info.totalMem - info.availMem) + " / " + AndroidUtilities.formatFileSize(info.totalMem);
        }
    }

    @SuppressLint("ViewConstructor")
    public static class NetSpeed extends MZGramTelemetryPill {

        private long lastRx = -1;
        private long lastTx;
        private long lastWall;

        public NetSpeed(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider, R.drawable.msg2_data);
        }

        @Override
        public int pillId() {
            return MZGramPills.NET_SPEED;
        }

        @Override
        protected String read() {
            // MZGram's own traffic, not the whole device's
            int uid = Process.myUid();
            long rx = TrafficStats.getUidRxBytes(uid);
            long tx = TrafficStats.getUidTxBytes(uid);
            if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
                return LocaleController.getString(R.string.MZGramPillUnsupported);
            }
            long wall = SystemClock.elapsedRealtime();
            String value = null;
            if (lastRx >= 0 && wall - lastWall >= 200) {
                long down = perSecond(lastRx, rx, wall - lastWall);
                long up = perSecond(lastTx, tx, wall - lastWall);
                // Arrows and sizes only: the sizes come translated, a second is the unit of the pill's title
                value = "↓" + AndroidUtilities.formatFileSize(down) + " ↑" + AndroidUtilities.formatFileSize(up);
            } else if (lastRx >= 0) {
                return null;
            }
            lastRx = rx;
            lastTx = tx;
            lastWall = wall;
            return value;
        }
    }

    @SuppressLint("ViewConstructor")
    public static class DcPing extends MZGramTelemetryPill {

        public DcPing(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider, R.drawable.msg_speed);
        }

        @Override
        public int pillId() {
            return MZGramPills.DC_PING;
        }

        @Override
        protected String read() {
            int ping = ConnectionsManager.native_getCurrentPingTime(UserConfig.selectedAccount);
            if (ping <= 0) {
                return null;
            }
            return LocaleController.formatString(R.string.MZGramPillPingValue, ping);
        }
    }
}
