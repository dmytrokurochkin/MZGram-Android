package org.telegram.messenger.mzgram.system;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Solar Hijri (Shamsi / Persian) calendar implementation for MZGram.
 * Converts Gregorian dates to Persian dates and formats month/day representations.
 *
 * Based on algorithms from Nekogram / Saman Zarandifar (GPLv3 / MIT).
 */
public class MZGramPersianDate {

    private static final String[] MONTH_NAMES_FA = {
            "فروردین", "اردیبهشت", "خرداد",
            "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر",
            "دی", "بهمن", "اسفند"
    };

    private static final String[] MONTH_NAMES_LATIN = {
            "Farvardin", "Ordibehesht", "Khordad",
            "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar",
            "Dey", "Bahman", "Esfand"
    };

    private static final String[] WEEK_DAY_NAMES_FA = {
            "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"
    };

    private long timeInMilliSecond;
    private int shYear;
    private int shMonth;
    private int shDay;
    private int grgYear;
    private int grgMonth;
    private int grgDay;
    private int hour;
    private int minute;
    private int second;

    public MZGramPersianDate() {
        this(System.currentTimeMillis());
    }

    public MZGramPersianDate(long timeInMilliSecond) {
        setTime(timeInMilliSecond);
    }

    public MZGramPersianDate(int year, int month, int day) {
        initGrgDate(year, month, day);
    }

    public void setTime(long timeInMilliSecond) {
        this.timeInMilliSecond = timeInMilliSecond;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMilliSecond);
        this.hour = calendar.get(Calendar.HOUR_OF_DAY);
        this.minute = calendar.get(Calendar.MINUTE);
        this.second = calendar.get(Calendar.SECOND);
        initGrgDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    private void initGrgDate(int year, int month, int day) {
        this.grgYear = year;
        this.grgMonth = month;
        this.grgDay = day;
        int[] jalali = gregorianToJalali(year, month, day);
        this.shYear = jalali[0];
        this.shMonth = jalali[1];
        this.shDay = jalali[2];
    }

    public int getShYear() {
        return shYear;
    }

    public int getShMonth() {
        return shMonth;
    }

    public int getShDay() {
        return shDay;
    }

    public int getGrgYear() {
        return grgYear;
    }

    public int getGrgMonth() {
        return grgMonth;
    }

    public int getGrgDay() {
        return grgDay;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    public long getTimeInMillis() {
        return timeInMilliSecond;
    }

    public String monthName() {
        if (shMonth >= 1 && shMonth <= 12) {
            return MONTH_NAMES_FA[shMonth - 1];
        }
        return "";
    }

    public String monthNameLatin() {
        if (shMonth >= 1 && shMonth <= 12) {
            return MONTH_NAMES_LATIN[shMonth - 1];
        }
        return "";
    }

    /**
     * Formats as Day + MonthName (e.g. "9 شهریور").
     */
    public String getPersianMonthDay() {
        return toPersianDigits(String.valueOf(shDay)) + " " + monthName();
    }

    public String getPersianMonthDayLatin() {
        return shDay + " " + monthNameLatin();
    }

    /**
     * Formats as Day + MonthName + Year (e.g. "9 شهریور 1403").
     */
    public String getPersianNormalDate() {
        return toPersianDigits(String.valueOf(shDay)) + " " + monthName() + " " + toPersianDigits(String.valueOf(shYear));
    }

    public String getPersianNormalDateLatin() {
        return shDay + " " + monthNameLatin() + " " + shYear;
    }

    public static String toPersianDigits(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('0', '\u06F0')
                .replace('1', '\u06F1')
                .replace('2', '\u06F2')
                .replace('3', '\u06F3')
                .replace('4', '\u06F4')
                .replace('5', '\u06F5')
                .replace('6', '\u06F6')
                .replace('7', '\u06F7')
                .replace('8', '\u06F8')
                .replace('9', '\u06F9');
    }

    public static boolean isLeapJalaliYear(int year) {
        int r = year % 33;
        return r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30;
    }

    public static boolean isLeapGregorianYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    /**
     * Converts Gregorian year, month (1-12), day (1-31) to Jalali [year, month, day].
     */
    public static int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] gDaysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int[] jDaysInMonth = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};

        int gy2 = gy - 1600;
        int gm2 = gm - 1;
        int gd2 = gd - 1;

        long gDayNo = 365L * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400;

        for (int i = 0; i < gm2; ++i) {
            gDayNo += gDaysInMonth[i];
        }
        if (gm2 > 1 && isLeapGregorianYear(gy)) {
            gDayNo++;
        }
        gDayNo += gd2;

        long jDayNo = gDayNo - 79;

        long jNp = jDayNo / 12053;
        jDayNo %= 12053;

        long jy = 979 + 33 * jNp + 4 * (jDayNo / 1461);
        jDayNo %= 1461;

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365;
            jDayNo = (jDayNo - 1) % 365;
        }

        int jm = 0;
        for (int i = 0; i < 11 && jDayNo >= jDaysInMonth[i]; ++i) {
            jDayNo -= jDaysInMonth[i];
            jm++;
        }
        int jd = (int) (jDayNo + 1);

        return new int[]{(int) jy, jm + 1, jd};
    }

    /**
     * Converts Jalali year, month (1-12), day (1-31) to Gregorian [year, month, day].
     */
    public static int[] jalaliToGregorian(int jy, int jm, int jd) {
        int[] gDaysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int[] jDaysInMonth = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};

        int jy2 = jy - 979;
        int jm2 = jm - 1;
        int jd2 = jd - 1;

        long jDayNo = 365L * jy2 + (jy2 / 33) * 8 + ((jy2 % 33 + 3) / 4);
        for (int i = 0; i < jm2; ++i) {
            jDayNo += jDaysInMonth[i];
        }
        jDayNo += jd2;

        long gDayNo = jDayNo + 79;

        long gy = 1600 + 400 * (gDayNo / 146097);
        gDayNo %= 146097;

        boolean leap = true;
        if (gDayNo >= 36525) {
            gDayNo--;
            gy += 100 * (gDayNo / 36524);
            gDayNo %= 36524;

            if (gDayNo >= 365) {
                gDayNo++;
            } else {
                leap = false;
            }
        }

        gy += 4 * (gDayNo / 1461);
        gDayNo %= 1461;

        if (gDayNo >= 366) {
            leap = false;
            gDayNo--;
            gy += gDayNo / 365;
            gDayNo %= 365;
        }

        int gm = 0;
        for (int i = 0; i < 12; i++) {
            int days = gDaysInMonth[i];
            if (i == 1 && leap) {
                days++;
            }
            if (gDayNo < days) {
                break;
            }
            gDayNo -= days;
            gm++;
        }
        int gd = (int) (gDayNo + 1);

        return new int[]{(int) gy, gm + 1, gd};
    }
}
