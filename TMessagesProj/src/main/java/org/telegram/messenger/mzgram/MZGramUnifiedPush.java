/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Push notifications through UnifiedPush: a distributor app (ntfy, microG,
 * Sunup and others) or the built-in Google FCM distributor
 * (MZGramFcmDistributor) on phones with Google Play Services or microG.
 * On unless the user turns it off in Notifications and Sounds: Telegram's
 * own Google push cannot work in MZGram builds.
 *
 * The distributor gives an endpoint; Telegram gets it as a WebPush token
 * (push type 10) with this device's keys, through the gateway the user
 * chose (MZGramUnifiedPushRules), and as a Simple Push token (type 4) for
 * wake-ups without content. A notification that cannot be decrypted wakes
 * the app up and it fetches new messages itself.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
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

    // The provider the app uses: UnifiedPush, or the build's own (Google or
    // Huawei) when the user turned UnifiedPush off.
    public static PushListenerController.IPushListenerServiceProvider choose(PushListenerController.IPushListenerServiceProvider base) {
        return MZGramUnifiedPushRules.usesUnifiedPush(MZGramConfig.useUnifiedPush) ? INSTANCE : base;
    }

    public static boolean isActive() {
        return ApplicationLoader.getPushProvider() == INSTANCE;
    }

    public static String ownPackage() {
        return context().getPackageName();
    }

    public static boolean isBuiltIn(String distributor) {
        return ownPackage().equals(distributor);
    }

    // What the built-in Google FCM distributor registers with: the key of
    // the gateway in use, and its /fcm/ route.
    public static String fcmVapidKey() {
        return MZGramUnifiedPushRules.vapidKey(MZGramConfig.unifiedPushVapidKey);
    }

    public static String fcmEndpoint(String token) {
        return MZGramUnifiedPushRules.fcmEndpointPrefix(MZGramConfig.unifiedPushGateway) + token;
    }

    public static boolean hasPlayServices() {
        return MZGramPushDiagnostics.playServices() != null;
    }

    @Override
    public boolean hasServices() {
        return !distributors().isEmpty();
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
                String saved = UnifiedPush.getSavedDistributor(context());
                String distributor = MZGramUnifiedPushRules.pickDistributor(saved, distributors(), ownPackage());
                if (distributor == null) {
                    MZGramPushDiagnostics.log("no distributor: no distributor app and no " + MZGramUnifiedPushRules.PLAY_SERVICES_PACKAGE);
                    return;
                }
                if (!distributor.equals(saved)) {
                    UnifiedPush.saveDistributor(context(), distributor);
                }
                MZGramPushDiagnostics.log("register -> " + distributor);
                UnifiedPush.register(context(), INSTANCE_NAME, null, null);
            } catch (Throwable e) {
                FileLog.e(e);
                MZGramPushDiagnostics.setLastFailure(e.toString());
            }
        });
    }

    // The distributor's endpoint came (also again, after a restart).
    public static void onNewEndpoint(String endpoint) {
        if (!isActive()) {
            UnifiedPush.unregister(context(), INSTANCE_NAME);
            return;
        }
        MZGramPushDiagnostics.log("endpoint: " + Uri.parse(endpoint).getHost());
        MZGramPushDiagnostics.setLastFailure(null);
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
        boolean builtIn = isBuiltIn(UnifiedPush.getSavedDistributor(context())) || endpoint.startsWith(MZGramUnifiedPushRules.fcmEndpointPrefix(MZGramConfig.unifiedPushGateway));
        String gateway = MZGramUnifiedPushRules.gateway(MZGramConfig.unifiedPushGatewayEnabled, MZGramConfig.unifiedPushGateway);
        String webToken;
        try {
            MZGramWebPushCrypto.Keys keys = MZGramWebPushCrypto.keys();
            webToken = MZGramUnifiedPushRules.webPushToken(MZGramUnifiedPushRules.webPushEndpoint(endpoint, gateway, builtIn), keys.publicKey, keys.authSecret);
        } catch (Exception e) {
            FileLog.e(e);
            MZGramPushDiagnostics.setLastFailure("WebPush keys: " + e);
            return;
        }
        String simpleToken = MZGramUnifiedPushRules.simplePushToken(endpoint, gateway, builtIn);
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

    // Where Telegram sends WebPush notifications for this device: the
    // gateway's route for the saved endpoint, or null without a gateway or
    // an endpoint. The test push goes there too.
    public static String gatewayEndpoint() {
        String endpoint = preferences().getString(KEY_ENDPOINT, null);
        if (TextUtils.isEmpty(endpoint) || !isActive()) {
            return null;
        }
        // The built-in Google FCM endpoint is the gateway's /fcm/ route itself.
        if (isBuiltIn(UnifiedPush.getSavedDistributor(context())) || endpoint.startsWith(MZGramUnifiedPushRules.fcmEndpointPrefix(MZGramConfig.unifiedPushGateway))) {
            return endpoint;
        }
        String gateway = MZGramUnifiedPushRules.gateway(MZGramConfig.unifiedPushGatewayEnabled, MZGramConfig.unifiedPushGateway);
        return gateway == null ? null : MZGramUnifiedPushRules.webPushEndpoint(endpoint, gateway);
    }

    // The gateway or its key changed. A distributor app's endpoint stays the
    // same and only the tokens change; the built-in Google FCM endpoint is
    // made from the gateway and its key, so it is registered again.
    public static void onGatewayChanged() {
        Utilities.globalQueue.postRunnable(() -> {
            if (isBuiltIn(UnifiedPush.getSavedDistributor(context()))) {
                reregister();
            } else {
                registerTokens();
            }
        });
    }

    private static void reregister() {
        String distributor = UnifiedPush.getSavedDistributor(context());
        if (distributor == null) {
            INSTANCE.onRequestPushToken();
            return;
        }
        UnifiedPush.unregister(context(), INSTANCE_NAME);
        dropTokens();
        UnifiedPush.saveDistributor(context(), distributor);
        MZGramPushDiagnostics.log("register -> " + distributor);
        UnifiedPush.register(context(), INSTANCE_NAME, null, null);
    }

    // Telegram took the WebPush token for this account; the Simple Push one
    // goes along with it.
    public static void onRegisteredForPush(int account, int pushType) {
        if (pushType != PushListenerController.PUSH_TYPE_WEB) {
            return;
        }
        MZGramPushDiagnostics.log("registered with Telegram (account " + account + ")");
        String token = preferences().getString(KEY_SIMPLE_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            return;
        }
        TL_account.registerDevice req = new TL_account.registerDevice();
        req.token_type = PushListenerController.PUSH_TYPE_SIMPLE;
        req.token = token;
        req.secret = SharedConfig.pushAuthKey;
        addOtherAccounts(account, req.other_uids);
        MZGramPushDiagnostics.onTelegramRegisterSent(account, req.token_type);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                MZGramPushDiagnostics.onTelegramRegisterAnswer(account, PushListenerController.PUSH_TYPE_SIMPLE, response instanceof TLRPC.TL_boolTrue, error));
    }

    public static void onRegistrationFailed(String reason) {
        MZGramPushDiagnostics.setLastFailure(reason);
        onRegistrationLost();
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
        String[] reason = new String[1];
        String payload = decode(content, reason);
        if (payload != null) {
            MZGramPushDiagnostics.onReceived(MZGramPushDiagnostics.Kind.PUSH, content.length, null);
            if (MZGramPushTest.onPayload(payload)) {
                return;
            }
            PowerManager.WakeLock lock = acquireWakeLock();
            new Thread(() -> {
                try {
                    PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_WEB, payload, System.currentTimeMillis());
                } finally {
                    release(lock);
                }
            }, "MZGramPush").start();
        } else {
            boolean webPush = MZGramUnifiedPushRules.looksLikeWebPush(content);
            MZGramPushDiagnostics.onReceived(webPush ? MZGramPushDiagnostics.Kind.DECRYPT_FAILED : MZGramPushDiagnostics.Kind.WAKE_UP, content.length, reason[0]);
            wakeUp();
        }
    }

    // The notification's data, or null when it is a wake-up or cannot be
    // decrypted.
    public static String decode(byte[] content) {
        return decode(content, new String[1]);
    }

    // reason[0] says why there is no data.
    private static String decode(byte[] content, String[] reason) {
        if (!MZGramUnifiedPushRules.looksLikeWebPush(content)) {
            reason[0] = "not a WebPush message";
            return null;
        }
        try {
            String payload = MZGramUnifiedPushRules.payload(MZGramWebPushCrypto.decrypt(content, MZGramWebPushCrypto.keys()));
            if (payload == null) {
                reason[0] = "no Telegram data in it";
            }
            return payload;
        } catch (Exception e) {
            reason[0] = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("UnifiedPush: cannot decrypt, waking up instead: " + e);
            }
            return null;
        }
    }

    private static PowerManager.WakeLock acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) context().getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mzgram:push");
            wakeLock.acquire(30_000);
            return wakeLock;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void release(PowerManager.WakeLock lock) {
        try {
            if (lock != null && lock.isHeld()) {
                lock.release();
            }
        } catch (RuntimeException ignored) {
            // Released by its timeout already.
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
        PowerManager.WakeLock lock = acquireWakeLock();
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
                    release(lock);
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
            MZGramPushDiagnostics.log("turned off by the user");
            UnifiedPush.unregister(context(), INSTANCE_NAME);
            dropTokens();
        } else if (!TextUtils.isEmpty(SharedConfig.pushString)) {
            unregisterAtServer(SharedConfig.pushType, SharedConfig.pushString);
            SharedConfig.pushString = "";
            SharedConfig.saveConfig();
        }
        ApplicationLoader.restartPushServices();
    }

    // The user picked a distributor from the list.
    public static void useDistributor(String packageName) {
        Utilities.globalQueue.postRunnable(() -> {
            String current = UnifiedPush.getSavedDistributor(context());
            if (current != null && !current.equals(packageName)) {
                UnifiedPush.unregister(context(), INSTANCE_NAME);
                dropTokens();
            }
            UnifiedPush.saveDistributor(context(), packageName);
            MZGramPushDiagnostics.log("register -> " + packageName);
            UnifiedPush.register(context(), INSTANCE_NAME, null, null);
        });
    }

    // The distributors to choose from, the built-in Google FCM one included
    // when Google Play Services or microG is installed.
    public static List<String> distributors() {
        return MZGramUnifiedPushRules.offeredDistributors(UnifiedPush.getDistributors(context()), ownPackage(), hasPlayServices());
    }

    public static String distributor() {
        return UnifiedPush.getSavedDistributor(context());
    }

    public static String ackedDistributor() {
        return UnifiedPush.getAckDistributor(context());
    }

    public static String label(String packageName) {
        if (packageName == null) {
            return null;
        }
        if (isBuiltIn(packageName)) {
            return LocaleController.getString(R.string.MZGramEmbeddedFcm);
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
        if (distributors().isEmpty()) {
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
