package org.telegram.messenger.mzgram;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MZGram: approximate month and year an account was created, estimated from its
 * numeric user ID with a table of known ID and date pairs stored in the app
 * (assets/mzgram_id_date.json). Works fully offline.
 *
 * Idea and data table taken from the ID-date estimate in Nekogram (GPL-3.0),
 * TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/ProfileDateHelper.java
 * and assets/id_date.json.
 */
public class MZGramRegistrationDate {

    private static long[] ids;
    private static long[] dates;

    private static synchronized void load() {
        if (ids != null) {
            return;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            InputStream in = context.getAssets().open("mzgram_id_date.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            JSONArray data = new JSONObject(out.toString("UTF-8")).getJSONArray("data");
            long[] newIds = new long[data.length()];
            long[] newDates = new long[data.length()];
            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.getJSONObject(i);
                newIds[i] = row.getLong("id");
                newDates[i] = row.getLong("date");
            }
            ids = newIds;
            dates = newDates;
        } catch (Exception e) {
            FileLog.e(e);
            ids = new long[0];
            dates = new long[0];
        }
    }

    /** Approximate creation time in seconds since 1970, or -1 when the table is not available. */
    public static long estimateSeconds(long userId) {
        load();
        if (ids.length < 2 || userId <= 0) {
            return -1;
        }
        if (userId <= ids[0]) {
            return dates[0];
        }
        for (int i = 1; i < ids.length; i++) {
            if (userId <= ids[i]) {
                double fraction = (double) (userId - ids[i - 1]) / (double) (ids[i] - ids[i - 1]);
                return Math.round(dates[i - 1] + fraction * (dates[i] - dates[i - 1]));
            }
        }
        return dates[dates.length - 1];
    }

    /** "~ Nov 2019" style text, or null when the switch is off or nothing is known. */
    public static String text(long userId) {
        if (!MZGramConfig.showRegistrationDate) {
            return null;
        }
        long seconds = estimateSeconds(userId);
        if (seconds < 0) {
            return null;
        }
        // The row's label is MZGram's, so the month follows the MZGram language
        Locale locale = MZGramLanguage.locale();
        String date = new SimpleDateFormat("LLL yyyy", locale).format(new Date(seconds * 1000L));
        return "~ " + date;
    }
}
