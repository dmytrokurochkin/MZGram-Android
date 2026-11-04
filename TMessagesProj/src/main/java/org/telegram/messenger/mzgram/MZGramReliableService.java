/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Notifications and Sounds > Reliable delivery: a foreground service with a
 * permanent quiet notification keeps the app's process and its connection
 * to Telegram, so messages and calls come on time on phones that stop apps
 * in the background. Off by default; the user turns it on and off only in
 * the settings (the notification has no buttons; a tap opens the setting).
 *
 * Unlike Telegram's Keep-Alive Service (a background service without a
 * notification, stopped by Android 8 and later), this one runs in the
 * foreground, of the specialUse type: dataSync has a daily limit and may
 * not start after a reboot. It starts when the switch is turned on, when
 * the process starts, after a reboot or an update, and from a watch job
 * every 15 minutes; whatever refuses the start is written to the journal
 * (MZGramReliableJournal), never swallowed. It is one switch for every
 * account. While it runs, every account keeps Telegram's low impact push
 * connection too.
 */

package org.telegram.messenger.mzgram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

public class MZGramReliableService extends Service {

    public static final String CHANNEL_ID = "mzgram_reliable";
    public static final int NOTIFICATION_ID = 7319;
    public static final int WATCH_JOB_ID = 7320;
    public static final long WATCH_PERIOD_MS = 15 * 60 * 1000L;
    // Where the notification leads: the switch in Notifications and Sounds
    public static final String SETTINGS_LINK = "tg://settings/mzgram_reliable";

    public static final String REASON_SWITCH = "switch";
    public static final String REASON_PROCESS = "process start";
    public static final String REASON_BOOT = "boot";
    public static final String REASON_UPDATE = "update";
    public static final String REASON_WATCH = "watch";

    private static final String EXTRA_REASON = "reason";
    private static final long HEARTBEAT_MS = 5 * 60 * 1000L;

    private static volatile boolean running;
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            MZGramReliableJournal.alive();
            AndroidUtilities.runOnUIThread(this, HEARTBEAT_MS);
        }
    };

    public static boolean isRunning() {
        return running;
    }

    // Starts or stops the service by the switch.
    public static void apply(Context context, String reason) {
        if (context == null) {
            return;
        }
        MZGramConfig.loadConfig(false);
        if (MZGramConfig.reliableDelivery) {
            start(context, reason);
        } else {
            stop(context, reason);
        }
    }

    private static void start(Context context, String reason) {
        if (context != null) {
            return;
        }
        scheduleWatch(context);
        if (running) {
            return;
        }
        Intent intent = new Intent(context, MZGramReliableService.class).putExtra(EXTRA_REASON, reason);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            MZGramReliableJournal.event("start asked: " + reason);
        } catch (Throwable e) {
            // ForegroundServiceStartNotAllowedException and the like: said,
            // not swallowed
            MZGramReliableJournal.event("start refused (" + reason + "): " + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }

    private static void stop(Context context, String reason) {
        cancelWatch(context);
        if (!running) {
            return;
        }
        try {
            context.stopService(new Intent(context, MZGramReliableService.class));
            MZGramReliableJournal.event("stopped: " + reason);
        } catch (Throwable e) {
            MZGramReliableJournal.event("stop failed: " + e.getClass().getSimpleName());
        }
    }

    private static void scheduleWatch(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null || scheduler.getPendingJob(WATCH_JOB_ID) != null) {
                return;
            }
            JobInfo job = new JobInfo.Builder(WATCH_JOB_ID, new ComponentName(context, MZGramReliableWatch.class))
                    .setPeriodic(WATCH_PERIOD_MS)
                    .setPersisted(false)
                    .build();
            scheduler.schedule(job);
        } catch (Throwable e) {
            MZGramReliableJournal.event("watch not scheduled: " + e.getClass().getSimpleName());
        }
    }

    private static void cancelWatch(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.cancel(WATCH_JOB_ID);
            }
        } catch (Throwable ignore) {
        }
    }

    public static boolean watchScheduled(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            return scheduler != null && scheduler.getPendingJob(WATCH_JOB_ID) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    // The notification: quiet, without buttons; a tap opens the switch.
    public static Notification buildNotification(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, LocaleController.getString(R.string.MZGramReliable), NotificationManager.IMPORTANCE_LOW);
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.enableLights(false);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(SETTINGS_LINK));
        open.setClassName(context, "org.telegram.ui.LaunchActivity");
        PendingIntent pending = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentText(LocaleController.getString(R.string.MZGramReliableNotification))
                .setContentIntent(pending)
                .setOngoing(true)
                .setShowWhen(false)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String reason = intent != null && intent.getStringExtra(EXTRA_REASON) != null ? intent.getStringExtra(EXTRA_REASON) : "restarted by Android";
        try {
            Notification notification = buildNotification(this);
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable e) {
            MZGramReliableJournal.event("foreground refused: " + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            MZGramReliableJournal.event("running: " + reason);
            setPushConnections(true);
            AndroidUtilities.cancelRunOnUIThread(heartbeat);
            AndroidUtilities.runOnUIThread(heartbeat);
        }
        if (!MZGramConfig.reliableDelivery) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        MZGramReliableJournal.event("timeout from Android");
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AndroidUtilities.cancelRunOnUIThread(heartbeat);
        if (running) {
            running = false;
            setPushConnections(false);
            MZGramReliableJournal.event("service ended");
        }
    }

    // While it runs every account keeps the low impact push connection; then
    // each account's own Background Connection setting again.
    private static void setPushConnections(boolean on) {
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (!UserConfig.getInstance(a).isClientActivated()) {
                    continue;
                }
                ConnectionsManager manager = ConnectionsManager.getInstance(a);
                manager.setPushConnectionEnabled(on || manager.isPushConnectionEnabled());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
