/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Puts the pill bar (MZGramPillStackView) at the end of the chat list's
 * search bar and keeps it up to date: it is built from the chosen pills
 * (MZGramPills.active()) while MZGramConfig.pillStack is on, rebuilt when
 * the choice changes, hidden while something is typed in the search, and
 * recoloured with the theme. With the bar off or no pill chosen nothing is
 * added, so the search bar is exactly Telegram's.
 *
 * The pills themselves are made in create(); each pill feature adds its
 * own there and in MZGramPills.ALL.
 *
 * Idea from exteraless PillStackController (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram.pills;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public final class MZGramPillStack implements NotificationCenter.NotificationCenterDelegate {

    private final FrameLayout container;
    private final EditText editText;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable rebuild = this::rebuild;
    private MZGramPillStackView stackView;
    private boolean attached;

    /**
     * Adds the pill bar to a search bar. The search bar keeps its views;
     * the bar goes on top of its end side.
     */
    public static MZGramPillStack attach(FrameLayout container, EditText editText, Theme.ResourcesProvider resourcesProvider) {
        if (container == null) {
            return null;
        }
        return new MZGramPillStack(container, editText, resourcesProvider);
    }

    /** A pill by its id, or null when MZGram has no such pill. */
    public static MZGramPill create(int id, Context context, Theme.ResourcesProvider resourcesProvider) {
        switch (id) {
            case MZGramPills.GHOST:
                return new MZGramGhostPill(context, resourcesProvider);
            case MZGramPills.LAST_SEEN:
                return new MZGramLastSeenPill(context, resourcesProvider);
            case MZGramPills.PROXY:
                return new MZGramProxyPill(context, resourcesProvider);
            case MZGramPills.CACHE:
                return new MZGramCachePill(context, resourcesProvider);
            case MZGramPills.CPU:
                return new MZGramTelemetryPill.Cpu(context, resourcesProvider);
            case MZGramPills.RAM:
                return new MZGramTelemetryPill.Ram(context, resourcesProvider);
            case MZGramPills.NET_SPEED:
                return new MZGramTelemetryPill.NetSpeed(context, resourcesProvider);
            case MZGramPills.DC_PING:
                return new MZGramTelemetryPill.DcPing(context, resourcesProvider);
            case MZGramPills.WEATHER:
                return new MZGramWeatherPill(context, resourcesProvider);
            case MZGramPills.RATES:
                return new MZGramRatesPill(context, resourcesProvider);
            default:
                return null;
        }
    }

    /** The title of a pill for the choice of pills, or null. */
    public static String titleOf(int id) {
        switch (id) {
            case MZGramPills.GHOST:
                return LocaleController.getString(R.string.MZGramGhostMode);
            case MZGramPills.LAST_SEEN:
                return LocaleController.getString(R.string.MZGramPillLastSeen);
            case MZGramPills.PROXY:
                return LocaleController.getString(R.string.MZGramPillProxy);
            case MZGramPills.CACHE:
                return LocaleController.getString(R.string.MZGramPillCache);
            case MZGramPills.CPU:
                return LocaleController.getString(R.string.MZGramPillCpu);
            case MZGramPills.RAM:
                return LocaleController.getString(R.string.MZGramPillRam);
            case MZGramPills.NET_SPEED:
                return LocaleController.getString(R.string.MZGramPillNetSpeed);
            case MZGramPills.DC_PING:
                return LocaleController.getString(R.string.MZGramPillPing);
            case MZGramPills.WEATHER:
                return LocaleController.getString(R.string.MZGramPillWeather);
            case MZGramPills.RATES:
                return LocaleController.getString(R.string.MZGramPillRates);
            default:
                return null;
        }
    }

    private MZGramPillStack(FrameLayout container, EditText editText, Theme.ResourcesProvider resourcesProvider) {
        this.container = container;
        this.editText = editText;
        this.resourcesProvider = resourcesProvider;
        container.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                start();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                stop();
            }
        });
        if (editText != null) {
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateVisibility();
                }
            });
        }
        if (container.isAttachedToWindow()) {
            start();
        }
    }

    private void start() {
        if (attached) {
            return;
        }
        attached = true;
        MZGramPills.addListener(rebuild);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        rebuild();
    }

    private void stop() {
        if (!attached) {
            return;
        }
        attached = false;
        MZGramPills.removeListener(rebuild);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        if (stackView != null) {
            // The pills stop updating; the next attach builds them again
            stackView.clear();
            container.removeView(stackView);
            stackView = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme && stackView != null) {
            stackView.updateColors();
        }
    }

    /** Builds the bar from the chosen pills, or removes it. */
    public void rebuild() {
        if (stackView != null) {
            stackView.clear();
        }
        ArrayList<Integer> active = MZGramConfig.pillStack ? MZGramPills.active() : new ArrayList<>();
        if (!active.isEmpty()) {
            if (stackView == null) {
                stackView = new MZGramPillStackView(container.getContext());
                container.addView(stackView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT,
                        (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 6, 0, 6, 0));
            }
            int last = MZGramPills.lastActive();
            int selected = 0;
            for (int id : active) {
                MZGramPill pill = create(id, container.getContext(), resourcesProvider);
                if (pill == null) {
                    continue;
                }
                pill.updateColors();
                stackView.addPill(pill);
                if (id == last) {
                    selected = stackView.count() - 1;
                }
            }
            stackView.setCurrent(selected);
        }
        if (stackView != null && stackView.count() == 0) {
            container.removeView(stackView);
            stackView = null;
        }
        updateVisibility();
    }

    // While something is typed in the search the pills step aside
    private void updateVisibility() {
        if (stackView == null) {
            return;
        }
        boolean typed = editText != null && editText.getText() != null && editText.getText().length() > 0;
        stackView.setVisibility(typed ? View.GONE : View.VISIBLE);
    }
}
