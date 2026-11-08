/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Chats > "Clean tracking from links": tracking
 * parameters (utm_*, fbclid, gclid, si and the rest) are cut from a link
 * when it is opened (Browser.openUrl) and when it is pasted into a message
 * field (EditTextCaption: the paste menu, the keyboard's clipboard and
 * pasted formatted text). The rules are the AdGuard URL Tracking filter
 * shipped in assets/adguard_url_tracking.txt (GPL-3.0, AdguardTeam/
 * AdguardFilters); they are never downloaded or updated from the network.
 * Only $removeparam rules that apply to a page address are used.
 *
 * Ported from exteraless (GPL-3.0), TMessagesProj/src/main/kotlin/app/
 * exteraless/links/LinkCleaner.kt, UrlCleaner.kt and AdGuardRule.kt,
 * from Kotlin to Java; without the filter download.
 */

package org.telegram.messenger.mzgram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.URLSpanReplacement;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MZGramLinkCleaner {

    private static final String FILE = "adguard_url_tracking.txt";

    private static volatile List<Rule> rules;

    private MZGramLinkCleaner() {
    }

    // The link to open, without its tracking parameters; the same object
    // when there is nothing to cut or the switch is off.
    public static Uri clean(Uri uri) {
        if (uri == null || !MZGramConfig.cleanLinks) {
            return uri;
        }
        String original = uri.toString();
        String cleaned = cleanUrl(original);
        return cleaned.equals(original) ? uri : Uri.parse(cleaned);
    }

    // Pasted text with the links in it cleaned; the same object when
    // nothing changed.
    public static CharSequence cleanText(CharSequence text) {
        if (TextUtils.isEmpty(text) || !MZGramConfig.cleanLinks) {
            return text;
        }
        Pattern pattern = AndroidUtilities.WEB_URL;
        if (pattern == null) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        SpannableStringBuilder out = null;
        int shift = 0;
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String original = text.subSequence(start, end).toString();
            String cleaned = cleanUrl(original);
            if (cleaned.equals(original)) {
                continue;
            }
            if (out == null) {
                out = new SpannableStringBuilder(text);
            }
            out.replace(start + shift, end + shift, cleaned);
            shift += cleaned.length() - (end - start);
        }
        if (out == null) {
            return text;
        }
        BaseInputConnection.removeComposingSpans(out);
        for (SuggestionSpan span : out.getSpans(0, out.length(), SuggestionSpan.class)) {
            out.removeSpan(span);
        }
        return out;
    }

    // The links behind formatted pasted text (text links).
    public static void cleanSpannedUrls(Spannable text) {
        if (text == null || !MZGramConfig.cleanLinks) {
            return;
        }
        for (URLSpan span : text.getSpans(0, text.length(), URLSpan.class)) {
            String url = span.getURL();
            if (url == null) {
                continue;
            }
            String cleaned = cleanUrl(url);
            if (cleaned.equals(url)) {
                continue;
            }
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            int flags = text.getSpanFlags(span);
            text.removeSpan(span);
            URLSpan replacement = span instanceof URLSpanReplacement
                    ? new URLSpanReplacement(cleaned, ((URLSpanReplacement) span).getTextStyleRun())
                    : new URLSpan(cleaned);
            text.setSpan(replacement, start, end, flags);
        }
    }

    // Paste from the menu with the links cleaned; false leaves the paste
    // to Telegram (nothing to clean, the switch off).
    public static boolean handleContextMenuPaste(EditText editText) {
        if (!MZGramConfig.cleanLinks) {
            return false;
        }
        Context context = editText.getContext();
        if (context == null) {
            return false;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() < 1) {
                return false;
            }
            CharSequence raw = clip.getItemAt(0).coerceToText(context);
            if (raw == null) {
                return false;
            }
            CharSequence cleaned = cleanText(raw);
            if (cleaned == raw) {
                return false;
            }
            Editable text = editText.getText();
            if (text == null) {
                return false;
            }
            int start = Math.max(0, editText.getSelectionStart());
            int end = Math.min(text.length(), editText.getSelectionEnd());
            text.replace(start, end, cleaned);
            editText.setSelection(start + cleaned.length());
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // Text the keyboard commits (its clipboard chip, a pasted link).
    public static InputConnection wrapInputConnection(InputConnection ic) {
        if (ic == null) {
            return null;
        }
        return new InputConnectionWrapper(ic, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                CharSequence cleaned = text != null && MZGramConfig.cleanLinks && (text.toString().contains("https://") || text.toString().contains("http://")) ? cleanText(text) : text;
                return super.commitText(cleaned, newCursorPosition);
            }
        };
    }

    private static String cleanUrl(String url) {
        int colon = url.indexOf(':');
        if (colon <= 0) {
            return url;
        }
        String scheme = url.substring(0, colon).toLowerCase(Locale.US);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return url;
        }
        List<Rule> list = rules();
        return list == null ? url : clean(list, url);
    }

    private static List<Rule> rules() {
        List<Rule> list = rules;
        if (list != null) {
            return list;
        }
        synchronized (MZGramLinkCleaner.class) {
            if (rules == null) {
                ArrayList<Rule> parsed = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getAssets().open(FILE), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Rule rule = RuleParser.parse(line);
                        if (rule != null) {
                            parsed.add(rule);
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                rules = parsed;
            }
            return rules;
        }
    }

    // Also used by the tests
    static String clean(List<Rule> rules, String url) {
        ParsedUrl parsed = ParsedUrl.parse(url);
        if (parsed == null) {
            return url;
        }
        String host = parsed.host.toLowerCase(Locale.US);
        String pathAndQuery = parsed.pathAndQuery();
        ArrayList<Rule> exceptions = new ArrayList<>();
        ArrayList<Rule> removals = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.matchesUrl(host, pathAndQuery, url)) {
                if (rule.exception) {
                    if (rule.param.kind == ParamMatcher.ALL) {
                        return url;
                    }
                    exceptions.add(rule);
                } else {
                    removals.add(rule);
                }
            }
        }
        if (removals.isEmpty()) {
            return url;
        }
        ArrayList<String[]> kept = new ArrayList<>();
        boolean dropped = false;
        for (String[] param : parsed.params) {
            boolean remove = false;
            for (Rule rule : removals) {
                if (rule.param.matches(param[0], param[1])) {
                    remove = true;
                    break;
                }
            }
            if (remove) {
                for (Rule rule : exceptions) {
                    if (rule.param.matches(param[0], param[1])) {
                        remove = false;
                        break;
                    }
                }
            }
            if (remove) {
                dropped = true;
            } else {
                kept.add(param);
            }
        }
        boolean removesAll = false;
        for (Rule rule : removals) {
            if (rule.param.kind == ParamMatcher.ALL) {
                removesAll = true;
                break;
            }
        }
        boolean stripDelimiter = parsed.hadQuery && kept.isEmpty() && removesAll;
        if (!dropped && !stripDelimiter) {
            return url;
        }
        return parsed.rebuild(kept);
    }

    private static boolean hostMatches(String host, String rule) {
        return host.equals(rule) || host.endsWith("." + rule);
    }

    static final class ParamMatcher {
        static final int ALL = 0, LITERAL = 1, PATTERN = 2, INVERTED = 3;
        final int kind;
        final String name;
        final Pattern regex;
        final ParamMatcher inner;

        ParamMatcher(int kind, String name, Pattern regex, ParamMatcher inner) {
            this.kind = kind;
            this.name = name;
            this.regex = regex;
            this.inner = inner;
        }

        boolean matches(String name, String value) {
            switch (kind) {
                case ALL: return true;
                case LITERAL: return this.name.equals(name);
                case PATTERN: return regex.matcher(value == null ? name : name + "=" + value).find();
                default: return !inner.matches(name, value);
            }
        }
    }

    static final class Rule {
        final boolean exception;
        // null for any address
        final String host;
        final Pattern hostRegex;
        final Pattern pathRegex;
        final Pattern fullRegex;
        final boolean any;
        final ParamMatcher param;
        final List<String> domainInclude;
        final List<String> domainExclude;

        Rule(boolean exception, boolean any, String host, Pattern hostRegex, Pattern pathRegex, Pattern fullRegex, ParamMatcher param, List<String> include, List<String> exclude) {
            this.exception = exception;
            this.any = any;
            this.host = host;
            this.hostRegex = hostRegex;
            this.pathRegex = pathRegex;
            this.fullRegex = fullRegex;
            this.param = param;
            this.domainInclude = include;
            this.domainExclude = exclude;
        }

        boolean matchesUrl(String urlHost, String pathAndQuery, String fullUrl) {
            if (!domainInclude.isEmpty()) {
                boolean found = false;
                for (String d : domainInclude) {
                    if (hostMatches(urlHost, d)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            for (String d : domainExclude) {
                if (hostMatches(urlHost, d)) {
                    return false;
                }
            }
            if (any) {
                return true;
            }
            if (fullRegex != null) {
                return fullRegex.matcher(fullUrl).find();
            }
            boolean hostOk = hostRegex != null ? hostRegex.matcher(urlHost).matches() : hostMatches(urlHost, host);
            return hostOk && (pathRegex == null || pathRegex.matcher(pathAndQuery).find());
        }
    }

    static final class RuleParser {
        private static final Set<String> NON_DOCUMENT_TYPES = new HashSet<>(Arrays.asList(
                "xmlhttprequest", "script", "image", "stylesheet", "font", "media", "object",
                "subdocument", "websocket", "webrtc", "ping", "other", "popup", "object-subrequest",
                "csp", "webtransport"));
        private static final Set<String> DOCUMENT_TYPES = new HashSet<>(Arrays.asList("document", "main_frame", "main-frame", "all"));
        private static final Set<String> NEUTRAL_MODIFIERS = new HashSet<>(Arrays.asList("important", "third-party", "match-case"));
        private static final Set<String> DISQUALIFYING_MODIFIERS = new HashSet<>(Arrays.asList("first-party", "specifichide", "generichide", "elemhide"));
        private static final String REGEX_META = "\\.+?()[]{}|^$";
        private static final String WILDCARD_META = "\\.+?()[]{}|";

        static Rule parse(String rawLine) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) {
                return null;
            }
            if (line.contains("##") || line.contains("#@#") || line.contains("#%#") || line.contains("#$#")) {
                return null;
            }
            boolean exception = line.startsWith("@@");
            String body = exception ? line.substring(2) : line;
            int dollar = findModifierDollar(body);
            if (dollar < 0) {
                return null;
            }
            String pattern = body.substring(0, dollar);
            String paramSpec = null;
            boolean hasRemoveparam = false;
            String domainSpec = null;
            String denyallowSpec = null;
            boolean sawDocumentType = false;
            boolean sawNonDocumentType = false;
            for (String m : splitModifiers(body.substring(dollar + 1))) {
                if (m.equals("removeparam")) {
                    hasRemoveparam = true;
                    paramSpec = null;
                } else if (m.startsWith("removeparam=")) {
                    hasRemoveparam = true;
                    paramSpec = m.substring("removeparam=".length());
                } else if (m.startsWith("domain=")) {
                    domainSpec = m.substring("domain=".length());
                } else if (m.startsWith("denyallow=")) {
                    denyallowSpec = m.substring("denyallow=".length());
                } else if (NEUTRAL_MODIFIERS.contains(m)) {
                    // no effect on a page address
                } else if (DOCUMENT_TYPES.contains(m)) {
                    sawDocumentType = true;
                } else if (NON_DOCUMENT_TYPES.contains(m)) {
                    sawNonDocumentType = true;
                } else if (m.startsWith("~")) {
                    if (DISQUALIFYING_MODIFIERS.contains(m.substring(1))) {
                        return null;
                    }
                } else {
                    // a modifier this cleaner cannot honour: the rule is left out
                    return null;
                }
            }
            if (!hasRemoveparam || (sawNonDocumentType && !sawDocumentType)) {
                return null;
            }
            ParamMatcher param = parseParamSpec(paramSpec);
            if (param == null) {
                return null;
            }
            List<String>[] domains = parseDomains(domainSpec);
            List<String> exclude = new ArrayList<>(domains[1]);
            exclude.addAll(parseDomains(denyallowSpec)[0]);
            try {
                if (pattern.isEmpty() || pattern.equals("*")) {
                    return new Rule(exception, true, null, null, null, null, param, domains[0], exclude);
                }
                if (pattern.length() >= 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
                    return new Rule(exception, false, null, null, null, Pattern.compile(pattern.substring(1, pattern.length() - 1)), param, domains[0], exclude);
                }
                if (!pattern.startsWith("||")) {
                    return null;
                }
                String rest = pattern.substring(2);
                int hostEnd = rest.length();
                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (c == '^' || c == '/') {
                        hostEnd = i;
                        break;
                    }
                }
                String host = rest.substring(0, hostEnd);
                if (host.isEmpty()) {
                    return null;
                }
                String path = rest.substring(hostEnd);
                Pattern hostRegex = host.indexOf('*') >= 0 ? buildHostRegex(host) : null;
                Pattern pathRegex = path.isEmpty() ? null : wildcardToRegex(path);
                return new Rule(exception, false, host.toLowerCase(Locale.US), hostRegex, pathRegex, null, param, domains[0], exclude);
            } catch (Exception e) {
                return null;
            }
        }

        private static ParamMatcher parseParamSpec(String spec) {
            if (spec == null) {
                return new ParamMatcher(ParamMatcher.ALL, null, null, null);
            }
            if (spec.startsWith("~")) {
                ParamMatcher inner = parseParamSpec(spec.substring(1));
                if (inner == null || inner.kind == ParamMatcher.ALL) {
                    return null;
                }
                return new ParamMatcher(ParamMatcher.INVERTED, null, null, inner);
            }
            if (spec.startsWith("/")) {
                int endSlash = spec.lastIndexOf('/');
                if (endSlash <= 0) {
                    return null;
                }
                String body = spec.substring(1, endSlash);
                int flags = spec.substring(endSlash + 1).indexOf('i') >= 0 ? Pattern.CASE_INSENSITIVE : 0;
                try {
                    return new ParamMatcher(ParamMatcher.PATTERN, null, Pattern.compile(body, flags), null);
                } catch (Exception e) {
                    return null;
                }
            }
            return new ParamMatcher(ParamMatcher.LITERAL, spec, null, null);
        }

        private static int findModifierDollar(String s) {
            boolean inRegex = false;
            int lastDollar = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    i++;
                } else if (c == '/') {
                    char prev = i > 0 ? s.charAt(i - 1) : ' ';
                    inRegex = !inRegex && (prev == '=' || prev == '$' || i == 0);
                } else if (c == '$' && !inRegex) {
                    lastDollar = i;
                }
            }
            return lastDollar;
        }

        private static List<String> splitModifiers(String s) {
            ArrayList<String> out = new ArrayList<>();
            StringBuilder buf = new StringBuilder();
            boolean inRegex = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    buf.append(c).append(s.charAt(i + 1));
                    i++;
                } else if (c == '/') {
                    char prev = buf.length() > 0 ? buf.charAt(buf.length() - 1) : ' ';
                    inRegex = !inRegex && prev == '=';
                    buf.append(c);
                } else if (c == ',' && !inRegex) {
                    out.add(buf.toString());
                    buf.setLength(0);
                } else {
                    buf.append(c);
                }
            }
            if (buf.length() > 0) {
                out.add(buf.toString());
            }
            return out;
        }

        private static Pattern buildHostRegex(String host) {
            StringBuilder b = new StringBuilder("^(?:.+\\.)?");
            for (int i = 0; i < host.length(); i++) {
                char c = host.charAt(i);
                if (c == '*') {
                    b.append("[^/]+");
                } else if (REGEX_META.indexOf(c) >= 0) {
                    b.append('\\').append(c);
                } else {
                    b.append(Character.toLowerCase(c));
                }
            }
            return Pattern.compile(b.append('$').toString());
        }

        private static Pattern wildcardToRegex(String glob) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    b.append(".*");
                } else if (c == '^') {
                    b.append("(?:[^A-Za-z0-9._%~-]|$)");
                } else if (WILDCARD_META.indexOf(c) >= 0) {
                    b.append('\\').append(c);
                } else {
                    b.append(c);
                }
            }
            return Pattern.compile(b.toString());
        }

        @SuppressWarnings("unchecked")
        private static List<String>[] parseDomains(String spec) {
            ArrayList<String> include = new ArrayList<>();
            ArrayList<String> exclude = new ArrayList<>();
            if (spec != null) {
                for (String raw : spec.split("\\|")) {
                    String d = raw.trim();
                    if (d.isEmpty()) {
                        continue;
                    }
                    if (d.startsWith("~")) {
                        exclude.add(d.substring(1).toLowerCase(Locale.US));
                    } else {
                        include.add(d.toLowerCase(Locale.US));
                    }
                }
            }
            return new List[]{include, exclude};
        }
    }

    static final class ParsedUrl {
        final String host;
        final List<String[]> params;
        final boolean hadQuery;
        private final String scheme;
        private final String authority;
        private final String path;
        private final String fragment;

        private ParsedUrl(String host, List<String[]> params, boolean hadQuery, String scheme, String authority, String path, String fragment) {
            this.host = host;
            this.params = params;
            this.hadQuery = hadQuery;
            this.scheme = scheme;
            this.authority = authority;
            this.path = path;
            this.fragment = fragment;
        }

        String pathAndQuery() {
            return hadQuery ? path + "?" + query(params) : path;
        }

        String rebuild(List<String[]> kept) {
            StringBuilder b = new StringBuilder(scheme).append("://").append(authority).append(path);
            if (!kept.isEmpty()) {
                b.append('?').append(query(kept));
            }
            if (fragment != null) {
                b.append('#').append(fragment);
            }
            return b.toString();
        }

        static ParsedUrl parse(String url) {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd <= 0) {
                return null;
            }
            String scheme = url.substring(0, schemeEnd);
            String afterScheme = url.substring(schemeEnd + 3);
            int fragIdx = afterScheme.indexOf('#');
            String beforeFrag = fragIdx < 0 ? afterScheme : afterScheme.substring(0, fragIdx);
            String fragment = fragIdx < 0 ? null : afterScheme.substring(fragIdx + 1);
            int qIdx = beforeFrag.indexOf('?');
            String authAndPath = qIdx < 0 ? beforeFrag : beforeFrag.substring(0, qIdx);
            String query = qIdx < 0 ? "" : beforeFrag.substring(qIdx + 1);
            int pathStart = authAndPath.indexOf('/');
            String authority = pathStart < 0 ? authAndPath : authAndPath.substring(0, pathStart);
            String path = pathStart < 0 ? "" : authAndPath.substring(pathStart);
            String hostPort = authority.substring(authority.lastIndexOf('@') + 1);
            int portColon = hostPort.indexOf(':');
            String host = portColon < 0 ? hostPort : hostPort.substring(0, portColon);
            if (host.isEmpty()) {
                return null;
            }
            ArrayList<String[]> params = new ArrayList<>();
            if (!query.isEmpty()) {
                for (String piece : query.split("&")) {
                    if (piece.isEmpty()) {
                        continue;
                    }
                    int eq = piece.indexOf('=');
                    params.add(eq < 0 ? new String[]{piece, null} : new String[]{piece.substring(0, eq), piece.substring(eq + 1)});
                }
            }
            return new ParsedUrl(host, params, qIdx >= 0, scheme, authority, path, fragment);
        }

        private static String query(List<String[]> params) {
            StringBuilder b = new StringBuilder();
            for (String[] p : params) {
                if (b.length() > 0) {
                    b.append('&');
                }
                b.append(p[0]);
                if (p[1] != null) {
                    b.append('=').append(p[1]);
                }
            }
            return b.toString();
        }
    }
}
