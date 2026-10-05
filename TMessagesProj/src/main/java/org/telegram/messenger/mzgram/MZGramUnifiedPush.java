/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Push notifications through a UnifiedPush distributor app (ntfy, microG,
 * Sunup and others) instead of Google. Used by itself on devices without
 * Google services, or when the user turns it on in Notifications and Sounds.
 *
 * The distributor gives an endpoint; Telegram gets it as a WebPush token
 * (push type 10) with this device's keys, through the gateway the user
 * chose (MZGramUnifiedPushRules), and as a Simple Push token (type 4) for
 * wake-ups without content. A notification that cannot be decrypted wakes
 * the app up and it fetches new messages itself.
 */

package org.telegram.messenger.mzgram;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_account;
import org.unifiedpush.android.connector.UnifiedPush;

import java.util.List;

public final class MZGramUnifiedPush implements PushListenerController.IPushListenerServiceProvider {

    public static final MZGramUnifiedPush INSTANCE = new MZGramUnifiedPush();

    // The registration's name at the distributor.
    public static final String INSTANCE_NAME = "default";

    private static final String PREFERENCES_NAME = "mzgram_push";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_SIMPLE_TOKEN = "simplePushToken";

    // A wake-up resumes the connection; the next ones in a burst add nothing.
    private static final long WAKE_UP_INTERVAL_MS = 10_000;
    private static long lastWakeUp = -WAKE_UP_INTERVAL_MS;

    public enum Status {
        OFF, NO_DISTRIBUTOR, CHOOSE_DISTRIBUTOR, WAITING, REGISTERED
    }

    private MZGramUnifiedPush() {
    }

    private static Context context() {
        return ApplicationLoader.applicationContext;
    }

    private static SharedPreferences preferences() {
        return context().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    // The provider the app uses: UnifiedPush or the build's own (Google or
    // Huawei), by the user's choice or by whether Google services are here.
    public static PushListenerController.IPushListenerServiceProvider choose(PushListenerController.IPushListenerServiceProvider base) {
        return MZGramUnifiedPushRules.usesUnifiedPush(MZGramConfig.useUnifiedPush, hasGoogleServices(base)) ? INSTANCE : base;
    }

    public static boolean hasGoogleServices(PushListenerController.IPushListenerServiceProvider base) {
        try {
            return base != null && base != INSTANCE && base.hasServices();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean isActive() {
        return ApplicationLoader.getPushProvider() == INSTANCE;
    }

    @Override
    public boolean hasServices() {
        return UnifiedPush.getSavedDistributor(context()) != null || !UnifiedPush.getDistributors(context()).isEmpty();
    }

    @Override
    public String getLogTitle() {
        return "UnifiedPush";
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_WEB;
    }

    @Override
    public void onRequestPushToken() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                SharedConfig.pushStringGetTimeStart = SystemClock.elapsedRealtime();
                if (UnifiedPush.getSavedDistributor(context()) == null) {
                    // Without a screen to ask on, only a single distributor
                    // is taken; with more the user picks one in the settings.
                    List<String> distributors = UnifiedPush.getDistributors(context());
                    if (distributors.size() != 1) {
                        return;
                    }
                    UnifiedPush.saveDistributor(context(), distributors.get(0));
                }
                UnifiedPush.register(context(), INSTANCE_NAME, null, null);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // The distributor's endpoint came (also again, after a restart).
    public static void onNewEndpoint(String endpoint) {
        if (!isActive()) {
            UnifiedPush.unregister(context(), INSTANCE_NAME);
            return;
        }
        preferences().edit().putString(KEY_ENDPOINT, endpoint).commit();
        SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
        registerTokens();
    }

    // Registers the tokens for the saved endpoint with the gateway in use;
    // also after the gateway setting changed. Old tokens are dropped first,
    // so Telegram does not keep sending to an address nobody reads.
    public static void registerTokens() {
        String endpoint = preferences().getString(KEY_ENDPOINT, null);
        if (TextUtils.isEmpty(endpoint) || !isActive()) {
            return;
        }
        String gateway = MZGramUnifiedPushRules.gateway(MZGramConfig.unifiedPushGatewayEnabled, MZGramConfig.unifiedPushGateway);
        String webToken;
        try {
            MZGramWebPushCrypto.Keys keys = MZGramWebPushCrypto.keys();
            webToken = MZGramUnifiedPushRules.webPushToken(MZGramUnifiedPushRules.webPushEndpoint(endpoint, gateway), keys.publicKey, keys.authSecret);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        String simpleToken = MZGramUnifiedPushRules.simplePushToken(endpoint, gateway);
        String oldSimpleToken = preferences().getString(KEY_SIMPLE_TOKEN, null);
        if (oldSimpleToken != null && !oldSimpleToken.equals(simpleToken)) {
            unregisterAtServer(PushListenerController.PUSH_TYPE_SIMPLE, oldSimpleToken);
        }
        if (SharedConfig.pushType == PushListenerController.PUSH_TYPE_WEB && !TextUtils.isEmpty(SharedConfig.pushString) && !SharedConfig.pushString.equals(webToken)) {
            unregisterAtServer(PushListenerController.PUSH_TYPE_WEB, SharedConfig.pushString);
        }
        preferences().edit().putString(KEY_SIMPLE_TOKEN, simpleToken).commit();
        PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEB, webToken);
    }

    // Telegram took the WebPush token for this account; the Simple Push one
    // goes along with it.
    public static void onRegisteredForPush(int account, int pushType) {
        if (pushType != PushListenerController.PUSH_TYPE_WEB) {
            return;
        }
        String token = preferences().getString(KEY_SIMPLE_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            return;
        }
        TL_account.registerDevice req = new TL_account.registerDevice();
        req.token_type = PushListenerController.PUSH_TYPE_SIMPLE;
        req.token = token;
        req.secret = SharedConfig.pushAuthKey;
        addOtherAccounts(account, req.other_uids);
        ConnectionsManager.getInstance(account).sendRequest(req, null);
    }

    // The distributor dropped the registration or refused it.
    public static void onRegistrationLost() {
        dropTokens();
        PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEB, null);
    }

    private static void dropTokens() {
        String simpleToken = preferences().getString(KEY_SIMPLE_TOKEN, null);
        if (!TextUtils.isEmpty(simpleToken)) {
            unregisterAtServer(PushListenerController.PUSH_TYPE_SIMPLE, simpleToken);
        }
        if (SharedConfig.pushType == PushListenerController.PUSH_TYPE_WEB && !TextUtils.isEmpty(SharedConfig.pushString)) {
            unregisterAtServer(PushListenerController.PUSH_TYPE_WEB, SharedConfig.pushString);
            SharedConfig.pushString = "";
            SharedConfig.saveConfig();
        }
        preferences().edit().remove(KEY_SIMPLE_TOKEN).remove(KEY_ENDPOINT).commit();
    }

    private static void unregisterAtServer(int type, String token) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            TL_account.unregisterDevice req = new TL_account.unregisterDevice();
            req.token_type = type;
            req.token = token;
            addOtherAccounts(a, req.other_uids);
            ConnectionsManager.getInstance(a).sendRequest(req, null);
        }
    }

    private static void addOtherAccounts(int account, java.util.ArrayList<Long> uids) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (a != account && config.isClientActivated()) {
                uids.add(config.getClientUserId());
            }
        }
    }

    // What arrived from the distributor. Decrypted, it goes the same way as
    // a notification through Google; anything else wakes the app up.
    public static void onMessage(byte[] content) {
        String payload = decode(content);
        if (payload != null) {
            new Thread(() -> PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_WEB, payload, System.currentTimeMillis()), "MZGramPush").start();
        } else {
            wakeUp();
        }
    }

    // The notification's data, or null when it is a wake-up or cannot be
    // decrypted.
    public static String decode(byte[] content) {
        if (!MZGramUnifiedPushRules.looksLikeWebPush(content)) {
            return null;
        }
        try {
            return MZGramUnifiedPushRules.payload(MZGramWebPushCrypto.decrypt(content, MZGramWebPushCrypto.keys()));
        } catch (Exception e) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("UnifiedPush: cannot decrypt, waking up instead: " + e);
            }
            return null;
        }
    }

    private static void wakeUp() {
        long now = SystemClock.elapsedRealtime();
        synchronized (MZGramUnifiedPush.class) {
            if (now - lastWakeUp < WAKE_UP_INTERVAL_MS) {
                return;
            }
            lastWakeUp = now;
        }
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) context().getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mzgram:push");
            wakeLock.acquire(30_000);
        } catch (Exception e) {
            FileLog.e(e);
        }
        PowerManager.WakeLock lock = wakeLock;
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            Utilities.stageQueue.postRunnable(() -> {
                try {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        if (UserConfig.getInstance(a).isClientActivated()) {
                            ConnectionsManager.onInternalPushReceived(a);
                            ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                        }
                    }
                } finally {
                    if (lock != null && lock.isHeld()) {
                        lock.release();
                    }
                }
            });
        });
    }

    // The user turned UnifiedPush on or off: the old provider's token is
    // dropped at Telegram and the new provider registers.
    public static void applyChoice() {
        PushListenerController.IPushListenerServiceProvider before = ApplicationLoader.getPushProvider();
        ApplicationLoader.resetPushProvider();
        PushListenerController.IPushListenerServiceProvider after = ApplicationLoader.getPushProvider();
        if (before == after) {
            return;
        }
        if (before == INSTANCE) {
            UnifiedPush.unregister(context(), INSTANCE_NAME);
            dropTokens();
        } else if (!TextUtils.isEmpty(SharedConfig.pushString)) {
            unregisterAtServer(SharedConfig.pushType, SharedConfig.pushString);
            SharedConfig.pushString = "";
            SharedConfig.saveConfig();
        }
        ApplicationLoader.restartPushServices();
    }

    // Lets the user pick the distributor the standard way: the system's
    // default for UnifiedPush links, or its chooser; when that is not
    // available, a list of the installed ones (shown by the caller).
    public static void pickDistributor(Activity activity, Runnable showList, Runnable done) {
        String old = UnifiedPush.getSavedDistributor(context());
        if (old != null) {
            UnifiedPush.unregister(context(), INSTANCE_NAME);
            dropTokens();
        }
        UnifiedPush.tryUseDefaultDistributor(activity, success -> {
            if (success) {
                UnifiedPush.register(context(), INSTANCE_NAME, null, null);
                done.run();
            } else {
                showList.run();
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    public static void useDistributor(String packageName) {
        String current = UnifiedPush.getSavedDistributor(context());
        if (current != null && !current.equals(packageName)) {
            UnifiedPush.unregister(context(), INSTANCE_NAME);
            dropTokens();
        }
        UnifiedPush.saveDistributor(context(), packageName);
        UnifiedPush.register(context(), INSTANCE_NAME, null, null);
    }

    public static List<String> distributors() {
        return UnifiedPush.getDistributors(context());
    }

    public static String distributor() {
        return UnifiedPush.getSavedDistributor(context());
    }

    public static String appName(String packageName) {
        if (packageName == null) {
            return null;
        }
        try {
            PackageManager manager = context().getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return manager.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    public static Status status() {
        if (!isActive()) {
            return Status.OFF;
        }
        if (UnifiedPush.getDistributors(context()).isEmpty()) {
            return Status.NO_DISTRIBUTOR;
        }
        if (UnifiedPush.getSavedDistributor(context()) == null) {
            return Status.CHOOSE_DISTRIBUTOR;
        }
        if (UnifiedPush.getAckDistributor(context()) == null || TextUtils.isEmpty(preferences().getString(KEY_ENDPOINT, null))) {
            return Status.WAITING;
        }
        return Status.REGISTERED;
    }
}
