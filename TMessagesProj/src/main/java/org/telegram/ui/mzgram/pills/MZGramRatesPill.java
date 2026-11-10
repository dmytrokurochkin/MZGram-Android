/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Financial rates pill for the chat list's search bar (navigation.pills-financial):
 * displays currency exchange rates (USD/EUR, USD/UAH), cryptocurrencies (BTC, ETH, TON),
 * and gold prices (XAU) from public endpoints (Coinbase, gold-api.com).
 * Tap cycles between currency pairs.
 */

package org.telegram.ui.mzgram.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.MZGramPills;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@SuppressLint("ViewConstructor")
public class MZGramRatesPill extends MZGramPill {

    private static final String COINBASE_URL = "https://api.coinbase.com/v2/exchange-rates?currency=USD";
    private static final String GOLD_URL = "https://api.gold-api.com/price/XAU";

    private static final String[] PAIRS = {"EUR", "UAH", "BTC", "ETH", "TON", "XAU"};
    private static int currentPairIndex = 0;

    private static final Map<String, Double> cachedRates = new HashMap<>();
    private static long lastFetchTime = 0;
    private static boolean fetching = false;

    public MZGramRatesPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setIcon(R.drawable.msg_language);
        update(false);
    }

    @Override
    public int pillId() {
        return MZGramPills.RATES;
    }

    @Override
    public long refreshInterval() {
        return 5 * 60 * 1000L;
    }

    @Override
    public void update(boolean animated) {
        String pair = PAIRS[currentPairIndex % PAIRS.length];
        Double rate = cachedRates.get(pair);
        if (rate != null) {
            setText(formatRate(pair, rate), animated);
        } else {
            setText(pair + " ...", animated);
        }
        if (System.currentTimeMillis() - lastFetchTime > refreshInterval()) {
            fetchRates();
        }
    }

    private String formatRate(String pair, double rate) {
        switch (pair) {
            case "EUR":
                return "€" + String.format(java.util.Locale.US, "%.2f", rate);
            case "UAH":
                return "₴" + String.format(java.util.Locale.US, "%.1f", rate);
            case "BTC": {
                double btcUsd = rate > 0 ? 1.0 / rate : 0;
                return "₿$" + (btcUsd >= 1000 ? Math.round(btcUsd / 1000.0) + "K" : Math.round(btcUsd));
            }
            case "ETH": {
                double ethUsd = rate > 0 ? 1.0 / rate : 0;
                return "Ξ$" + (ethUsd >= 1000 ? String.format(java.util.Locale.US, "%.1fK", ethUsd / 1000.0) : Math.round(ethUsd));
            }
            case "TON": {
                double tonUsd = rate > 0 ? 1.0 / rate : 0;
                return "💎$" + String.format(java.util.Locale.US, "%.2f", tonUsd);
            }
            case "XAU":
                return "Au$" + (rate >= 1000 ? String.format(java.util.Locale.US, "%.1fK", rate / 1000.0) : Math.round(rate));
            default:
                return pair + " " + String.format(java.util.Locale.US, "%.2f", rate);
        }
    }

    private void fetchRates() {
        if (fetching) {
            return;
        }
        fetching = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                // 1. Fetch Coinbase rates
                URL cbUrl = new URL(COINBASE_URL);
                HttpURLConnection conn = (HttpURLConnection) cbUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    in.close();
                    JSONObject json = new JSONObject(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                    JSONObject rates = json.getJSONObject("data").getJSONObject("rates");
                    for (String cur : new String[]{"EUR", "UAH", "BTC", "ETH", "TON"}) {
                        if (rates.has(cur)) {
                            cachedRates.put(cur, rates.getDouble(cur));
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            try {
                // 2. Fetch Gold price
                URL goldUrl = new URL(GOLD_URL);
                HttpURLConnection conn = (HttpURLConnection) goldUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    in.close();
                    JSONObject json = new JSONObject(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                    if (json.has("price")) {
                        cachedRates.put("XAU", json.getDouble("price"));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            fetching = false;
            lastFetchTime = System.currentTimeMillis();
            AndroidUtilities.runOnUIThread(() -> update(true));
        });
    }

    @Override
    public void onTap() {
        currentPairIndex = (currentPairIndex + 1) % PAIRS.length;
        update(true);
    }

    @Override
    protected void addOptions(ItemOptions options, BaseFragment fragment) {
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh), this::fetchRates);
    }
}
