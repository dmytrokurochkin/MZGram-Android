/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Weather pill for the chat list's search bar (navigation.pills-weather):
 * displays current weather condition emoji and temperature from open-meteo.com.
 * Opt-in with clear consent for location and third-party data request.
 */

package org.telegram.ui.mzgram.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Stories.recorder.Weather;

@SuppressLint("ViewConstructor")
public class MZGramWeatherPill extends MZGramPill {

    private boolean fetching;

    public MZGramWeatherPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setIcon(R.drawable.filled_location);
        update(false);
    }

    @Override
    public int pillId() {
        return MZGramPills.WEATHER;
    }

    @Override
    public long refreshInterval() {
        return 15 * 60 * 1000L;
    }

    @Override
    public void update(boolean animated) {
        Weather.State state = Weather.getCached();
        if (state != null) {
            String emoji = state.getEmoji();
            int temp = Math.round(state.temperature);
            String text = (emoji != null ? emoji + " " : "") + (temp > 0 ? "+" : "") + temp + "°";
            setText(text, animated);
        } else {
            setText("--°", animated);
            fetchWeather();
        }
    }

    private void fetchWeather() {
        if (fetching) {
            return;
        }
        fetching = true;
        Weather.fetch(false, state -> {
            fetching = false;
            if (state != null) {
                update(true);
            }
        });
    }

    @Override
    public void onTap() {
        fetchWeather();
    }

    @Override
    protected void addOptions(ItemOptions options, BaseFragment fragment) {
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh), this::fetchWeather);
    }
}
