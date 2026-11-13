/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Calculation results in the message field, besides the calculator
 * (MZGramInlineMath): typing "=" after a conversion appends its result.
 *   Units:      5 km in mi=3.10686 mi      (also "to", "в", "у")
 *   Time zones: 15:00 Kyiv in Tokyo=22:00  (cities, UTC+3, EST and other abbreviations)
 * Everything is computed on the device; there are no currency rates, as
 * MZGram makes no calls to outside services.
 */

package org.telegram.ui.mzgram;

import org.telegram.messenger.mzgram.MZGramConfig;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MZGramUnitConvert {

    private static final String WORD = "(?:in|to|into|в|у|во)";
    private static final Pattern UNITS = Pattern.compile(
            "(-?\\d+(?:[.,]\\d+)?)\\s*([\\p{L}°/²³0-9]+?)\\s+" + WORD + "\\s+([\\p{L}°/²³0-9]+)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TIME = Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s+([\\p{L}_/+\\-0-9 .]+?)\\s+" + WORD + "\\s+([\\p{L}_/+\\-0-9 .]+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final class Unit {
        final String kind;
        final double factor;

        Unit(String kind, double factor) {
            this.kind = kind;
            this.factor = factor;
        }
    }

    private static final HashMap<String, Unit> UNIT_TABLE = new HashMap<>();
    private static final HashMap<String, String> ZONES = new HashMap<>();

    private static void unit(String kind, double factor, String... names) {
        Unit unit = new Unit(kind, factor);
        for (String name : names) {
            UNIT_TABLE.put(name, unit);
        }
    }

    private static void zone(String id, String... names) {
        for (String name : names) {
            ZONES.put(name, id);
        }
    }

    static {
        unit("length", 0.001, "mm", "мм");
        unit("length", 0.01, "cm", "см");
        unit("length", 1, "m", "м", "meter", "meters", "metre", "metres", "метр", "метри", "метрів", "метра", "метров");
        unit("length", 1000, "km", "км", "kilometer", "kilometers", "кілометр", "кілометрів", "километр", "километров");
        unit("length", 0.0254, "in", "inch", "inches", "дюйм", "дюйми", "дюймів", "дюймов");
        unit("length", 0.3048, "ft", "foot", "feet", "фут", "фути", "футів", "футов");
        unit("length", 0.9144, "yd", "yard", "yards", "ярд", "ярди", "ярдів", "ярдов");
        unit("length", 1609.344, "mi", "mile", "miles", "миля", "милі", "миль", "міля", "мілі", "міль");
        unit("length", 1852, "nmi");
        unit("mass", 1e-6, "mg", "мг");
        unit("mass", 0.001, "g", "г", "gram", "grams", "грам", "грамів", "грамм", "граммов");
        unit("mass", 1, "kg", "кг", "kilogram", "kilograms", "кілограм", "кілограмів", "килограмм", "килограммов");
        unit("mass", 1000, "t", "т", "ton", "tons", "тонна", "тонни", "тонн");
        unit("mass", 0.028349523125, "oz", "ounce", "ounces", "унція", "унцій", "унция", "унций");
        unit("mass", 0.45359237, "lb", "lbs", "pound", "pounds", "фунт", "фунти", "фунтів", "фунтов");
        unit("mass", 6.35029318, "st", "stone");
        unit("volume", 0.001, "ml", "мл");
        unit("volume", 1, "l", "л", "liter", "liters", "litre", "litres", "літр", "літри", "літрів", "литр", "литров");
        unit("volume", 1000, "m3", "m³", "м3", "м³");
        unit("volume", 3.785411784, "gal", "gallon", "gallons", "галон", "галонів", "галлон", "галлонов");
        unit("volume", 0.946352946, "qt", "quart", "quarts");
        unit("volume", 0.473176473, "pt", "pint", "pints", "пінта", "пінт", "пинта", "пинт");
        unit("volume", 0.0295735295625, "floz");
        unit("volume", 0.2365882365, "cup", "cups");
        unit("speed", 1, "m/s", "м/с", "mps");
        unit("speed", 1 / 3.6, "km/h", "kmh", "kph", "км/год", "км/ч");
        unit("speed", 0.44704, "mph");
        unit("speed", 0.514444, "kn", "knot", "knots", "вузол", "вузлів", "узел", "узлов");
        unit("area", 1e-4, "cm2", "cm²", "см2", "см²");
        unit("area", 1, "m2", "m²", "м2", "м²");
        unit("area", 1e4, "ha", "га");
        unit("area", 1e6, "km2", "km²", "км2", "км²");
        unit("area", 0.09290304, "ft2", "ft²", "sqft");
        unit("area", 4046.8564224, "acre", "acres", "акр", "акрів", "акров");
        unit("data", 0.125, "bit", "bits", "біт", "бит");
        unit("data", 1, "b", "byte", "bytes", "байт", "байти", "байтів", "байтов");
        unit("data", 1024, "kb", "кб");
        unit("data", 1024d * 1024, "mb", "мб");
        unit("data", 1024d * 1024 * 1024, "gb", "гб");
        unit("data", 1024d * 1024 * 1024 * 1024, "tb", "тб");
        unit("time", 0.001, "ms", "мс");
        unit("time", 1, "s", "sec", "second", "seconds", "с", "сек", "секунд", "секунди");
        unit("time", 60, "min", "minute", "minutes", "хв", "хвилин", "хвилини", "мин", "минут", "минуты");
        unit("time", 3600, "h", "hr", "hour", "hours", "год", "годин", "години", "ч", "час", "часов", "часа");
        unit("time", 86400, "d", "day", "days", "дн", "день", "дні", "днів", "дня", "дней");
        unit("time", 604800, "wk", "week", "weeks", "тиж", "тиждень", "тижні", "тижнів", "нед", "неделя", "недели", "недель");
        unit("time", 31557600, "yr", "year", "years", "рік", "роки", "років", "год.", "лет");
        // Temperature has an offset: the factor is unused, see toKelvin
        unit("temperature", 0, "c", "°c", "celsius", "цельсій", "цельсия");
        unit("temperature", 0, "f", "°f", "fahrenheit", "фаренгейт", "фаренгейта");
        unit("temperature", 0, "k", "kelvin", "кельвін", "кельвина");

        zone("UTC", "utc", "gmt", "z");
        zone("Europe/Kiev", "kyiv", "kiev", "київ", "киев", "ukraine", "україна", "украина", "eet", "eest");
        zone("Europe/Warsaw", "warsaw", "варшава", "poland", "польща", "польша");
        zone("Europe/Berlin", "berlin", "берлін", "берлин", "cet", "cest", "germany", "німеччина", "германия");
        zone("Europe/Paris", "paris", "париж");
        zone("Europe/Madrid", "madrid", "мадрид");
        zone("Europe/Rome", "rome", "рим");
        zone("Europe/London", "london", "лондон", "uk", "bst");
        zone("Europe/Moscow", "moscow", "москва", "msk");
        zone("Europe/Minsk", "minsk", "мінськ", "минск", "мінск");
        zone("Europe/Istanbul", "istanbul", "стамбул", "turkey", "туреччина", "турция");
        zone("Asia/Almaty", "almaty", "алмати", "алматы");
        zone("Asia/Dubai", "dubai", "дубай");
        zone("Asia/Kolkata", "india", "індія", "индия", "ist", "delhi", "делі", "дели");
        zone("Asia/Shanghai", "china", "китай", "beijing", "пекін", "пекин", "shanghai");
        zone("Asia/Tokyo", "tokyo", "токіо", "токио", "japan", "японія", "япония", "jst");
        zone("Asia/Seoul", "seoul", "сеул", "kst");
        zone("Australia/Sydney", "sydney", "сідней", "сидней", "aest");
        zone("America/New_York", "new york", "ny", "nyc", "нью-йорк", "est", "edt", "et");
        zone("America/Chicago", "chicago", "чикаго", "cst", "cdt", "ct");
        zone("America/Denver", "denver", "денвер", "mst", "mdt", "mt");
        zone("America/Los_Angeles", "los angeles", "la", "лос-анджелес", "pst", "pdt", "pt");
        zone("America/Sao_Paulo", "sao paulo", "são paulo", "сан-паулу", "brt");
        zone("America/Toronto", "toronto", "торонто");
    }

    private MZGramUnitConvert() {
    }

    /** The result of a conversion that ends the text, or null when there is none or it is off. */
    public static String resultFor(String text) {
        if (text == null || text.length() > 200) {
            return null;
        }
        if (MZGramConfig.inlineTimeZones) {
            String time = timeFor(text);
            if (time != null) {
                return time;
            }
        }
        return MZGramConfig.inlineUnits ? unitsFor(text) : null;
    }

    static String unitsFor(String text) {
        Matcher m = UNITS.matcher(text);
        if (!m.find()) {
            return null;
        }
        Unit from = UNIT_TABLE.get(m.group(2).toLowerCase(Locale.ROOT));
        Unit to = UNIT_TABLE.get(m.group(3).toLowerCase(Locale.ROOT));
        if (from == null || to == null || !from.kind.equals(to.kind)) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(m.group(1).replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
        double result;
        if (from.kind.equals("temperature")) {
            result = fromKelvin(toKelvin(value, m.group(2)), m.group(3));
        } else {
            result = value * from.factor / to.factor;
        }
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            return null;
        }
        return MZGramInlineMath.format(round(result)) + " " + m.group(3);
    }

    private static double round(double value) {
        return Math.abs(value) >= 1e-6 ? Double.parseDouble(String.format(Locale.ROOT, "%.6g", value)) : value;
    }

    private static char scale(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("°", "");
        return n.isEmpty() ? 'c' : n.charAt(0) == 'ц' ? 'c' : n.charAt(0) == 'ф' ? 'f' : n.charAt(0) == 'к' ? 'k' : n.charAt(0);
    }

    private static double toKelvin(double value, String name) {
        switch (scale(name)) {
            case 'f': return (value - 32) * 5 / 9 + 273.15;
            case 'k': return value;
            default: return value + 273.15;
        }
    }

    private static double fromKelvin(double kelvin, String name) {
        switch (scale(name)) {
            case 'f': return (kelvin - 273.15) * 9 / 5 + 32;
            case 'k': return kelvin;
            default: return kelvin - 273.15;
        }
    }

    static String timeFor(String text) {
        Matcher m = TIME.matcher(text);
        if (!m.find()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        if (hour > 23 || minute > 59) {
            return null;
        }
        TimeZone from = zoneOf(m.group(3));
        TimeZone to = zoneOf(m.group(4));
        if (from == null || to == null) {
            return null;
        }
        Calendar source = Calendar.getInstance(from);
        source.set(Calendar.HOUR_OF_DAY, hour);
        source.set(Calendar.MINUTE, minute);
        source.set(Calendar.SECOND, 0);
        source.set(Calendar.MILLISECOND, 0);
        Calendar target = Calendar.getInstance(to);
        target.setTimeInMillis(source.getTimeInMillis());
        long days = dayNumber(target) - dayNumber(source);
        String result = String.format(Locale.ROOT, "%02d:%02d", target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE));
        if (days != 0) {
            result += days > 0 ? " (+" + days + ")" : " (" + days + ")";
        }
        return result;
    }

    // The local date of the calendar as a day count, so that dates across a new year subtract right
    private static long dayNumber(Calendar calendar) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis() / 86400000L;
    }

    static TimeZone zoneOf(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return null;
        }
        String id = ZONES.get(key);
        if (id != null) {
            return TimeZone.getTimeZone(id);
        }
        Matcher offset = Pattern.compile("^(?:utc|gmt)\\s*([+-])\\s*(\\d{1,2})(?::?(\\d{2}))?$").matcher(key);
        if (offset.find()) {
            int hours = Integer.parseInt(offset.group(2));
            int minutes = offset.group(3) != null ? Integer.parseInt(offset.group(3)) : 0;
            if (hours > 14 || minutes > 59) {
                return null;
            }
            return TimeZone.getTimeZone(String.format(Locale.ROOT, "GMT%s%02d:%02d", offset.group(1), hours, minutes));
        }
        // A city as written in a zone name: Lisbon, Buenos Aires, Europe/Prague
        String city = key.replace(' ', '_');
        for (String available : TimeZone.getAvailableIDs()) {
            String lower = available.toLowerCase(Locale.ROOT);
            if (lower.equals(city) || lower.endsWith("/" + city)) {
                return TimeZone.getTimeZone(available);
            }
        }
        return null;
    }
}
