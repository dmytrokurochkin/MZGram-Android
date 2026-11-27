package org.mzgram.glyph;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphFrame;
import com.nothing.ketchum.GlyphManager;
import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphMatrixObject;
import com.nothing.ketchum.GlyphMatrixUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;

import java.util.List;

public final class GlyphController {

    private static volatile GlyphController instance;

    public static GlyphController getInstance() {
        GlyphController local = instance;
        if (local == null) {
            synchronized (GlyphController.class) {
                local = instance;
                if (local == null) {
                    local = instance = new GlyphController();
                }
            }
        }
        return local;
    }

    private static final long LOGO_SHOW_MS = 3000L;

    private static final int RECORDING_MAX_CYCLES = 600;

    private static final long BREATH_STEP_MS = 120L;
    private static final int BREATH_STEP = 10;
    private static final int BREATH_MIN = 12;
    private static final int BREATH_MAX = 255;

    private static final int RECORDING_MAX_BREATH_STEPS = 5000;

    private static final long FLASH_COOLDOWN_MS = 2500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean supported;

    private boolean initialized;

    private GlyphManager stripManager;
    private volatile boolean stripSessionOpen;

    private GlyphMatrixManager matrixManager;
    private volatile boolean matrixRegistered;

    private final Runnable hideLogoRunnable = this::hideMatrixLogo;

    private Bitmap matrixLogoBitmap;

    private boolean recordingActive;
    private boolean callActive;
    private boolean glyphBusy;
    private long lastFlashAt;
    private int breathBrightness = BREATH_MAX;
    private int breathDirection = -1;
    private int breathSteps;

    private final Runnable breathRunnable = new Runnable() {
        @Override
        public void run() {
            if (!glyphBusy || matrixManager == null || !matrixRegistered) {
                return;
            }
            if (breathSteps++ > RECORDING_MAX_BREATH_STEPS) {
                stopMatrixBreathing();
                return;
            }
            breathBrightness += breathDirection * BREATH_STEP;
            if (breathBrightness <= BREATH_MIN) {
                breathBrightness = BREATH_MIN;
                breathDirection = 1;
            } else if (breathBrightness >= BREATH_MAX) {
                breathBrightness = BREATH_MAX;
                breathDirection = -1;
            }
            showMatrixLogo(breathBrightness, false);
            handler.postDelayed(this, BREATH_STEP_MS);
        }
    };

    private GlyphController() {
        supported = "nothing".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public boolean isSupported() {
        return supported;
    }

    public synchronized void init() {
        if (initialized || !supported || !GlyphConfig.enabled()) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        try {
            if (isMatrixDevice()) {
                initMatrix(context);
            } else {
                initStrip(context);
            }
            initialized = true;
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public synchronized void shutdown() {
        initialized = false;
        stripSessionOpen = false;
        matrixRegistered = false;
        previewPending = false;
        recordingActive = false;
        callActive = false;
        glyphBusy = false;
        lastFlashAt = 0;
        handler.removeCallbacks(hideLogoRunnable);
        handler.removeCallbacks(breathRunnable);
        try {
            if (stripManager != null) {
                stripManager.unInit();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        stripManager = null;
        try {
            if (matrixManager != null) {
                matrixManager.unInit();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        matrixManager = null;
    }

    public void onNewMessages(List<MessageObject> messages, int account) {
        if (!GlyphConfig.enabled() || !GlyphConfig.onNewMessage() || messages == null) {
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(account);
        boolean hasIncoming = false;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message != null && !message.isOutOwner()
                    && !messagesController.isDialogMuted(message.getDialogId())) {
                hasIncoming = true;
                break;
            }
        }
        if (!hasIncoming) {
            return;
        }
        handler.post(() -> {
            if (!ensureReady() || glyphBusy) {
                return;
            }
            if (GlyphConfig.screenOffOnly() && isScreenOn()) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (now - lastFlashAt < FLASH_COOLDOWN_MS) {
                return;
            }
            lastFlashAt = now;
            if (matrixRegistered) {
                showMatrixLogo();
            } else {
                pulseStrip();
            }
        });
    }

    public void onRecordingStarted() {
        if (!GlyphConfig.enabled() || !GlyphConfig.onRecording()) {
            return;
        }
        handler.post(() -> {
            recordingActive = true;
            updateGlyphActivity();
        });
    }

    public void onRecordingStopped() {
        handler.post(() -> {
            recordingActive = false;
            updateGlyphActivity();
        });
    }

    public void onIncomingCallStarted() {
        if (!GlyphConfig.enabled() || !GlyphConfig.onCall()) {
            return;
        }
        handler.post(() -> {
            callActive = true;
            updateGlyphActivity();
        });
    }

    public void onIncomingCallStopped() {
        handler.post(() -> {
            callActive = false;
            updateGlyphActivity();
        });
    }

    private void updateGlyphActivity() {
        boolean active = recordingActive || callActive;
        if (active == glyphBusy) {
            return;
        }
        if (!active) {
            glyphBusy = false;
            stopMatrixBreathing();
            if (stripManager != null && stripSessionOpen) {
                try {
                    stripManager.turnOff();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
            return;
        }
        if (!ensureReady()) {
            return;
        }
        glyphBusy = true;
        if (matrixRegistered) {
            startMatrixBreathing();
        } else if (stripSessionOpen) {
            breatheStrip();
        }
    }

    private boolean isScreenOn() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return true;
        }
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }

    private boolean previewPending;

    public void preview() {
        handler.post(() -> {
            if (!ensureReady()) {
                previewPending = supported && GlyphConfig.enabled();
                return;
            }
            if (matrixRegistered) {
                showMatrixLogo();
            }
            pulseStrip();
        });
    }

    private void runPendingPreview() {
        if (!previewPending) {
            return;
        }
        previewPending = false;
        handler.post(this::preview);
    }

    private boolean ensureReady() {
        if (!supported || !GlyphConfig.enabled()) {
            return false;
        }
        if (!initialized) {
            init();
        }
        return initialized && (stripSessionOpen || matrixRegistered);
    }

    private boolean isMatrixDevice() {
        try {
            return Common.is23112() || Common.is25111p();
        } catch (Throwable t) {
            return false;
        }
    }

    private void initStrip(Context context) {
        stripManager = GlyphManager.getInstance(context);
        stripManager.init(new GlyphManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName componentName) {
                boolean registered = false;
                if (Common.is20111()) {
                    registered = stripManager.register(Glyph.DEVICE_20111);
                } else if (Common.is22111()) {
                    registered = stripManager.register(Glyph.DEVICE_22111);
                } else if (Common.is23111()) {
                    registered = stripManager.register(Glyph.DEVICE_23111);
                } else if (Common.is23113()) {
                    registered = stripManager.register(Glyph.DEVICE_23113);
                } else if (Common.is24111()) {
                    registered = stripManager.register(Glyph.DEVICE_24111);
                } else if (Common.is25111()) {
                    registered = stripManager.register(Glyph.DEVICE_25111);
                }
                if (!registered) {
                    return;
                }
                try {
                    stripManager.openSession();
                    stripSessionOpen = true;
                    runPendingPreview();
                } catch (GlyphException e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                stripSessionOpen = false;
            }
        });
    }

    private void initMatrix(Context context) {
        matrixManager = GlyphMatrixManager.getInstance(context);
        matrixManager.init(new GlyphMatrixManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName componentName) {
                matrixRegistered = matrixManager.register(
                        Common.is23112() ? Glyph.DEVICE_23112 : Glyph.DEVICE_25111p);
                if (matrixRegistered) {
                    runPendingPreview();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                matrixRegistered = false;
            }
        });
    }

    private void pulseStrip() {
        if (stripManager == null || !stripSessionOpen) {
            return;
        }
        try {
            GlyphFrame frame = stripManager.getGlyphFrameBuilder()
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .buildChannelD()
                    .buildChannelE()
                    .buildPeriod(600)
                    .buildCycles(2)
                    .buildInterval(120)
                    .build();
            stripManager.animate(frame);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void breatheStrip() {
        try {
            GlyphFrame frame = stripManager.getGlyphFrameBuilder()
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .buildChannelD()
                    .buildChannelE()
                    .buildPeriod(1500)
                    .buildCycles(RECORDING_MAX_CYCLES)
                    .buildInterval(0)
                    .build();
            stripManager.animate(frame);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void showMatrixLogo() {
        showMatrixLogo(BREATH_MAX, true);
    }

    private void showMatrixLogo(int brightness, boolean autoHide) {
        if (matrixManager == null || !matrixRegistered) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        Bitmap bitmap = getMatrixLogoBitmap(context);
        if (bitmap == null) {
            return;
        }
        try {
            GlyphMatrixObject object = new GlyphMatrixObject.Builder()
                    .setImageSource(bitmap)
                    .setScale(150)
                    .setBrightness(brightness)
                    .build();
            GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder()
                    .addTop(object)
                    .build(context);
            matrixManager.setAppMatrixFrame(frame.render());
            handler.removeCallbacks(hideLogoRunnable);
            if (autoHide) {
                handler.postDelayed(hideLogoRunnable, LOGO_SHOW_MS);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private Bitmap getMatrixLogoBitmap(Context context) {
        if (matrixLogoBitmap != null) {
            return matrixLogoBitmap;
        }
        Drawable icon = context.getDrawable(R.drawable.mzgram_icon_monochrome);
        if (icon == null) {
            return null;
        }
        icon = icon.mutate();
        icon.setTint(Color.WHITE);
        matrixLogoBitmap = GlyphMatrixUtils.drawableToBitmap(icon);
        return matrixLogoBitmap;
    }

    private void startMatrixBreathing() {
        breathSteps = 0;
        breathBrightness = BREATH_MAX;
        breathDirection = -1;
        handler.removeCallbacks(hideLogoRunnable);
        handler.post(breathRunnable);
    }

    private void stopMatrixBreathing() {
        handler.removeCallbacks(breathRunnable);
        hideMatrixLogo();
    }

    private void hideMatrixLogo() {
        if (matrixManager == null || !matrixRegistered) {
            return;
        }
        try {
            matrixManager.closeAppMatrix();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
