/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Message filters: rules with regular expressions that hide other people's
 * messages on this phone (Settings > MZGram > Ads and filters > Message
 * filters, off by default). A hidden message gets the empty row of the
 * shadow ban (MZGramShadowBan.VIEW_TYPE) in the chat and does not notify;
 * by a separate switch its media is not downloaded on its own. Nothing is
 * sent to the server and nothing changes for anyone else.
 *
 * Rules are the user's own, kept in the prefs file mzgram_regex_filters:
 * common rules for groups and channels (and private chats by a switch) and
 * rules for one chat, which apply in that chat always. A chat can be left
 * out of all filters (filters.minor, dialog exclusions). There are no rule
 * lists from the network.
 *
 * A rule is checked against the text or caption, the texts and links of
 * the buttons and the links of the message; an album is checked as a whole,
 * so that it is hidden or shown together. A rule can be turned around:
 * then a message is hidden when the expression is NOT found. Own messages,
 * service messages and Saved Messages are never hidden.
 *
 * Android runs expressions in ICU, which cannot be stopped from Java, so an
 * expression that can take very long (a repeated group with a repeat
 * inside, like (a+)+) is refused when it is saved, the text checked is cut
 * at MAX_TEXT characters and every result is kept in a small cache keyed by
 * the message and its edit date.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class MZGramRegexFilters {

    /** Longest expression that can be saved. */
    public static final int MAX_PATTERN = 1000;
    /** Most rules in one list (the common one or one chat's). */
    public static final int MAX_RULES = 200;
    /** Part of the message text that is checked. */
    public static final int MAX_TEXT = 4096;

    public static final int PROBLEM_NONE = 0;
    public static final int PROBLEM_EMPTY = 1;
    public static final int PROBLEM_TOO_LONG = 2;
    public static final int PROBLEM_SYNTAX = 3;
    public static final int PROBLEM_SLOW = 4;

    /** Dialog id of the common rules in the methods that take a dialog. */
    public static final long COMMON = 0;

    private static final String PREFS = "mzgram_regex_filters";
    private static final String KEY_RULES = "rules";
    private static final String KEY_CHAT_RULES = "chat_rules";
    private static final String KEY_EXCLUDED = "excluded";
    private static final int CACHE_SIZE = 2000;

    private static final Object lock = new Object();
    private static volatile SharedPreferences preferences;
    private static volatile State state;

    private static final LinkedHashMap<String, Boolean> cache = new LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private MZGramRegexFilters() {
    }

    /** One rule. Immutable; the expression is compiled once. */
    public static final class Rule {
        public final String pattern;
        public final boolean caseInsensitive;
        public final boolean reversed;
        public final boolean enabled;
        private final Pattern compiled;

        public Rule(String pattern, boolean caseInsensitive, boolean reversed, boolean enabled) {
            this.pattern = pattern == null ? "" : pattern;
            this.caseInsensitive = caseInsensitive;
            this.reversed = reversed;
            this.enabled = enabled;
            this.compiled = problemOf(this.pattern, caseInsensitive) == PROBLEM_NONE ? compileOrNull(this.pattern, caseInsensitive) : null;
        }

        /** False for a rule whose expression is broken or refused; such a rule hides nothing. */
        public boolean isValid() {
            return compiled != null;
        }

        public Rule withEnabled(boolean value) {
            return new Rule(pattern, caseInsensitive, reversed, value);
        }

        boolean hides(CharSequence text) {
            if (!enabled || compiled == null) {
                return false;
            }
            boolean found;
            try {
                found = compiled.matcher(text).find();
            } catch (Throwable e) {
                FileLog.e(e);
                return false;
            }
            return found != reversed;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("p", pattern);
            o.put("i", caseInsensitive);
            o.put("r", reversed);
            o.put("e", enabled);
            return o;
        }

        static Rule fromJson(JSONObject o) {
            String p = o.optString("p", "");
            if (TextUtils.isEmpty(p)) {
                return null;
            }
            return new Rule(p, o.optBoolean("i", false), o.optBoolean("r", false), o.optBoolean("e", true));
        }
    }

    private static final class State {
        final List<Rule> common;
        final HashMap<Long, List<Rule>> chats;
        final HashSet<Long> excluded;
        final boolean anyEnabled;

        State(List<Rule> common, HashMap<Long, List<Rule>> chats, HashSet<Long> excluded) {
            this.common = common;
            this.chats = chats;
            this.excluded = excluded;
            boolean any = hasEnabled(common);
            for (List<Rule> list : chats.values()) {
                any |= hasEnabled(list);
            }
            this.anyEnabled = any;
        }

        private static boolean hasEnabled(List<Rule> list) {
            for (int i = 0, N = list.size(); i < N; i++) {
                if (list.get(i).enabled && list.get(i).isValid()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static SharedPreferences preferences() {
        if (preferences == null) {
            synchronized (lock) {
                if (preferences == null) {
                    preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                }
            }
        }
        return preferences;
    }

    private static State state() {
        State s = state;
        if (s == null) {
            synchronized (lock) {
                s = state;
                if (s == null) {
                    s = load();
                    state = s;
                }
            }
        }
        return s;
    }

    private static List<Rule> parseRules(JSONArray array) {
        ArrayList<Rule> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0, N = array.length(); i < N && list.size() < MAX_RULES; i++) {
            JSONObject o = array.optJSONObject(i);
            Rule rule = o == null ? null : Rule.fromJson(o);
            if (rule != null) {
                list.add(rule);
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static State load() {
        List<Rule> common = Collections.emptyList();
        HashMap<Long, List<Rule>> chats = new HashMap<>();
        HashSet<Long> excluded = new HashSet<>();
        try {
            SharedPreferences p = preferences();
            String rules = p.getString(KEY_RULES, null);
            if (!TextUtils.isEmpty(rules)) {
                common = parseRules(new JSONArray(rules));
            }
            String chatRules = p.getString(KEY_CHAT_RULES, null);
            if (!TextUtils.isEmpty(chatRules)) {
                JSONObject o = new JSONObject(chatRules);
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        long dialogId = Long.parseLong(key);
                        List<Rule> list = parseRules(o.optJSONArray(key));
                        if (dialogId != COMMON && !list.isEmpty()) {
                            chats.put(dialogId, list);
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            String ex = p.getString(KEY_EXCLUDED, null);
            if (!TextUtils.isEmpty(ex)) {
                JSONArray a = new JSONArray(ex);
                for (int i = 0, N = a.length(); i < N; i++) {
                    long dialogId = a.optLong(i, 0);
                    if (dialogId != 0) {
                        excluded.add(dialogId);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return new State(common, chats, excluded);
    }

    private static void save(State s) {
        try {
            JSONArray common = new JSONArray();
            for (Rule rule : s.common) {
                common.put(rule.toJson());
            }
            JSONObject chats = new JSONObject();
            for (Map.Entry<Long, List<Rule>> e : s.chats.entrySet()) {
                JSONArray a = new JSONArray();
                for (Rule rule : e.getValue()) {
                    a.put(rule.toJson());
                }
                chats.put(String.valueOf(e.getKey()), a);
            }
            JSONArray excluded = new JSONArray();
            for (Long dialogId : s.excluded) {
                excluded.put(dialogId);
            }
            preferences().edit()
                    .putString(KEY_RULES, common.toString())
                    .putString(KEY_CHAT_RULES, chats.toString())
                    .putString(KEY_EXCLUDED, excluded.toString())
                    .apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void commit(State s) {
        synchronized (lock) {
            state = s;
            save(s);
        }
        clearCache();
    }

    /** Forgets every kept result; called when rules, exclusions or the switches change. */
    public static void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    // ---- rules

    /** The rules of a chat, or the common ones for COMMON; a copy. */
    public static ArrayList<Rule> rules(long dialogId) {
        State s = state();
        List<Rule> list = dialogId == COMMON ? s.common : s.chats.get(dialogId);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public static int count(long dialogId) {
        State s = state();
        List<Rule> list = dialogId == COMMON ? s.common : s.chats.get(dialogId);
        return list == null ? 0 : list.size();
    }

    /** Chats that have rules of their own, in no particular order. */
    public static ArrayList<Long> chatsWithRules() {
        return new ArrayList<>(state().chats.keySet());
    }

    /** Replaces the rules of a chat (or the common ones); an empty list removes them. */
    public static void setRules(long dialogId, List<Rule> rules) {
        synchronized (lock) {
            State s = state();
            ArrayList<Rule> list = new ArrayList<>();
            if (rules != null) {
                for (Rule rule : rules) {
                    if (rule != null && !TextUtils.isEmpty(rule.pattern) && list.size() < MAX_RULES) {
                        list.add(rule);
                    }
                }
            }
            List<Rule> common = s.common;
            HashMap<Long, List<Rule>> chats = new HashMap<>(s.chats);
            if (dialogId == COMMON) {
                common = Collections.unmodifiableList(list);
            } else if (list.isEmpty()) {
                chats.remove(dialogId);
            } else {
                chats.put(dialogId, Collections.unmodifiableList(list));
            }
            commit(new State(common, chats, new HashSet<>(s.excluded)));
        }
    }

    /** Adds a rule at the top; false when the list is full. */
    public static boolean addRule(long dialogId, Rule rule) {
        synchronized (lock) {
            ArrayList<Rule> list = rules(dialogId);
            if (rule == null || list.size() >= MAX_RULES) {
                return false;
            }
            list.add(0, rule);
            setRules(dialogId, list);
            return true;
        }
    }

    public static void replaceRule(long dialogId, int index, Rule rule) {
        synchronized (lock) {
            ArrayList<Rule> list = rules(dialogId);
            if (rule == null || index < 0 || index >= list.size()) {
                return;
            }
            list.set(index, rule);
            setRules(dialogId, list);
        }
    }

    public static void removeRule(long dialogId, int index) {
        synchronized (lock) {
            ArrayList<Rule> list = rules(dialogId);
            if (index < 0 || index >= list.size()) {
                return;
            }
            list.remove(index);
            setRules(dialogId, list);
        }
    }

    // ---- chats left out of the filters

    public static boolean isExcluded(long dialogId) {
        return dialogId != 0 && state().excluded.contains(dialogId);
    }

    public static void setExcluded(long dialogId, boolean excluded) {
        if (dialogId == 0) {
            return;
        }
        synchronized (lock) {
            State s = state();
            HashSet<Long> set = new HashSet<>(s.excluded);
            if (excluded ? !set.add(dialogId) : !set.remove(dialogId)) {
                return;
            }
            commit(new State(s.common, new HashMap<>(s.chats), set));
        }
    }

    public static ArrayList<Long> excluded() {
        return new ArrayList<>(state().excluded);
    }

    // ---- checking expressions

    private static Pattern compileOrNull(String pattern, boolean caseInsensitive) {
        try {
            return compile(pattern, caseInsensitive);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Pattern compile(String pattern, boolean caseInsensitive) {
        int flags = Pattern.MULTILINE;
        if (caseInsensitive) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        return Pattern.compile(pattern, flags);
    }

    /** What is wrong with an expression, PROBLEM_NONE when it can be saved. */
    public static int problemOf(String pattern, boolean caseInsensitive) {
        if (TextUtils.isEmpty(pattern)) {
            return PROBLEM_EMPTY;
        }
        if (pattern.length() > MAX_PATTERN) {
            return PROBLEM_TOO_LONG;
        }
        try {
            compile(pattern, caseInsensitive);
        } catch (Throwable e) {
            return PROBLEM_SYNTAX;
        }
        return looksSlow(pattern) ? PROBLEM_SLOW : PROBLEM_NONE;
    }

    /** The parser's own words about a broken expression, or null. */
    public static String syntaxDetail(String pattern, boolean caseInsensitive) {
        try {
            compile(pattern == null ? "" : pattern, caseInsensitive);
            return null;
        } catch (PatternSyntaxException e) {
            String d = e.getDescription();
            return e.getIndex() >= 0 ? d + " (" + (e.getIndex() + 1) + ")" : d;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Whether a rule made of these parts would hide a message with this text. */
    public static boolean test(String pattern, boolean caseInsensitive, boolean reversed, String sample) {
        if (problemOf(pattern, caseInsensitive) != PROBLEM_NONE || sample == null) {
            return false;
        }
        return new Rule(pattern, caseInsensitive, reversed, true).hides(cut(sample));
    }

    /** The text as an expression that finds exactly this text. */
    public static String quote(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(text.length() + 8);
        for (int i = 0, N = text.length(); i < N; i++) {
            char c = text.charAt(i);
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                b.append('\\');
            }
            b.append(c);
        }
        return b.toString();
    }

    private static boolean isRepeatAt(String p, int i) {
        if (i >= p.length()) {
            return false;
        }
        char c = p.charAt(i);
        if (c == '*' || c == '+') {
            return true;
        }
        if (c != '{') {
            return false;
        }
        int close = p.indexOf('}', i);
        if (close < 0) {
            return false;
        }
        String body = p.substring(i + 1, close);
        int comma = body.indexOf(',');
        if (comma < 0) {
            return false;
        }
        String upper = body.substring(comma + 1).trim();
        if (upper.isEmpty()) {
            return true;
        }
        try {
            return Integer.parseInt(upper) >= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * True for a repeated group that has a repeat inside, like (a+)+, (\w*)*
     * or (x+y?){2,}: such an expression can take very long on some texts.
     */
    static boolean looksSlow(String p) {
        int n = p.length();
        boolean[] inner = new boolean[n + 2];
        int depth = 0;
        for (int i = 0; i < n; i++) {
            char c = p.charAt(i);
            if (c == '\\') {
                if (i + 1 < n && p.charAt(i + 1) == 'Q') {
                    int end = p.indexOf("\\E", i + 2);
                    if (end < 0) {
                        break;
                    }
                    i = end + 1;
                } else {
                    i++;
                }
                if (isRepeatAt(p, i + 1)) {
                    inner[depth] = true;
                }
                continue;
            }
            if (c == '[') {
                int j = i + 1;
                if (j < n && p.charAt(j) == '^') {
                    j++;
                }
                if (j < n && p.charAt(j) == ']') {
                    j++;
                }
                int level = 1;
                while (j < n && level > 0) {
                    char d = p.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == '[') {
                        level++;
                    } else if (d == ']') {
                        level--;
                        if (level == 0) {
                            break;
                        }
                    }
                    j++;
                }
                i = Math.min(j, n - 1);
                if (isRepeatAt(p, i + 1)) {
                    inner[depth] = true;
                }
                continue;
            }
            if (c == '(') {
                if (depth + 1 < inner.length) {
                    depth++;
                    inner[depth] = false;
                }
                continue;
            }
            if (c == ')') {
                boolean had = inner[depth];
                if (depth > 0) {
                    depth--;
                }
                if (isRepeatAt(p, i + 1)) {
                    if (had) {
                        return true;
                    }
                    inner[depth] = true;
                } else if (had) {
                    inner[depth] = true;
                }
                continue;
            }
            if (isRepeatAt(p, i + 1)) {
                inner[depth] = true;
            }
        }
        return false;
    }

    // ---- checking messages

    private static CharSequence cut(CharSequence text) {
        return text.length() > MAX_TEXT ? text.subSequence(0, MAX_TEXT) : text;
    }

    private static void appendLine(StringBuilder b, String part) {
        if (TextUtils.isEmpty(part)) {
            return;
        }
        if (b.length() > 0) {
            b.append('\n');
        }
        b.append(part);
    }

    private static void appendText(StringBuilder b, TLRPC.Message message) {
        if (message == null) {
            return;
        }
        appendLine(b, message.message);
        if (message.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            for (TL_keyboard.KeyboardInlineButtonRow row : ((TLRPC.TL_replyInlineMarkup) message.reply_markup).rows) {
                if (row == null) {
                    continue;
                }
                for (TL_keyboard.KeyboardInlineButton button : row.buttons) {
                    appendButton(b, button);
                }
            }
        } else if (message.reply_markup instanceof TLRPC.TL_replyKeyboardMarkup) {
            for (TL_keyboard.KeyboardButtonRow row : ((TLRPC.TL_replyKeyboardMarkup) message.reply_markup).rows) {
                if (row == null) {
                    continue;
                }
                for (TL_keyboard.KeyboardButton button : row.buttons) {
                    appendButton(b, button);
                }
            }
        }
        if (message.entities != null) {
            for (TLRPC.MessageEntity entity : message.entities) {
                if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                    appendLine(b, ((TLRPC.TL_messageEntityTextUrl) entity).url);
                }
            }
        }
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            TLRPC.WebPage page = ((TLRPC.TL_messageMediaWebPage) message.media).webpage;
            if (page != null) {
                appendLine(b, page.url);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void appendButton(StringBuilder b, TL_keyboard.KeyboardButtonProto button) {
        if (button == null) {
            return;
        }
        try {
            appendLine(b, button.getText());
            appendLine(b, button.getUrl());
        } catch (Throwable ignore) {
        }
    }

    private static boolean decide(State s, long dialogId, CharSequence text) {
        List<Rule> chat = s.chats.get(dialogId);
        if (chat != null) {
            for (int i = 0, N = chat.size(); i < N; i++) {
                if (chat.get(i).hides(text)) {
                    return true;
                }
            }
        }
        if (dialogId > 0 && !MZGramConfig.regexFiltersInPrivate) {
            return false;
        }
        for (int i = 0, N = s.common.size(); i < N; i++) {
            if (s.common.get(i).hides(text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canFilter(State s, int account, long dialogId) {
        if (!s.anyEnabled || dialogId == 0 || s.excluded.contains(dialogId)) {
            return false;
        }
        if (dialogId > 0 && dialogId == UserConfig.getInstance(account).getClientUserId()) {
            return false;
        }
        if (s.chats.containsKey(dialogId)) {
            return true;
        }
        return !s.common.isEmpty() && (dialogId < 0 || MZGramConfig.regexFiltersInPrivate);
    }

    private static boolean verdict(State s, String key, long dialogId, TLRPC.Message[] messages) {
        synchronized (cache) {
            Boolean kept = cache.get(key);
            if (kept != null) {
                return kept;
            }
        }
        StringBuilder text = new StringBuilder();
        for (TLRPC.Message m : messages) {
            appendText(text, m);
            if (text.length() > MAX_TEXT) {
                break;
            }
        }
        boolean result = text.length() > 0 && decide(s, dialogId, cut(text));
        synchronized (cache) {
            if (state == s) {
                cache.put(key, result);
            }
        }
        return result;
    }

    private static boolean skips(TLRPC.Message m) {
        return m == null || m.out || m instanceof TLRPC.TL_messageService || m instanceof TLRPC.TL_messageEmpty;
    }

    /** Whether the filters hide this message; checked first that they are on. */
    public static boolean isFiltered(MessageObject message) {
        return isFiltered(message, null);
    }

    /** As isFiltered(message), with the album of the message checked as a whole. */
    public static boolean isFiltered(MessageObject message, MessageObject.GroupedMessages group) {
        if (!MZGramConfig.regexFilters || message == null || message.isDateObject || message.isSponsored()
                || message.isOutOwner() || skips(message.messageOwner)) {
            return false;
        }
        State s = state();
        long dialogId = message.getDialogId();
        int account = message.currentAccount;
        if (!canFilter(s, account, dialogId)) {
            return false;
        }
        if (group != null && group.messages != null && group.messages.size() > 1 && message.getGroupId() != 0) {
            ArrayList<MessageObject> list = group.messages;
            TLRPC.Message[] messages = new TLRPC.Message[list.size()];
            int maxEdit = 0;
            for (int i = 0; i < messages.length; i++) {
                MessageObject m = list.get(i);
                messages[i] = m == null ? null : m.messageOwner;
                if (messages[i] != null) {
                    maxEdit = Math.max(maxEdit, messages[i].edit_date);
                }
            }
            String key = "g" + account + ":" + dialogId + ":" + group.groupId + ":" + messages.length + ":" + maxEdit;
            return verdict(s, key, dialogId, messages);
        }
        TLRPC.Message owner = message.messageOwner;
        String key = "m" + account + ":" + dialogId + ":" + owner.id + ":" + owner.edit_date;
        return verdict(s, key, dialogId, new TLRPC.Message[]{owner});
    }

    /** As isFiltered(message) for a message that has no MessageObject yet. */
    public static boolean isFiltered(int account, TLRPC.Message message) {
        if (!MZGramConfig.regexFilters || skips(message)) {
            return false;
        }
        State s = state();
        long dialogId = MessageObject.getDialogId(message);
        if (!canFilter(s, account, dialogId)) {
            return false;
        }
        String key = "m" + account + ":" + dialogId + ":" + message.id + ":" + message.edit_date;
        return verdict(s, key, dialogId, new TLRPC.Message[]{message});
    }

    /** Whether the media of this message is kept from downloading on its own. */
    public static boolean blocksAutoDownload(MessageObject message) {
        return MZGramConfig.regexFilters && MZGramConfig.regexFiltersNoDownload && isFiltered(message);
    }

    public static boolean blocksAutoDownload(int account, TLRPC.Message message) {
        return MZGramConfig.regexFilters && MZGramConfig.regexFiltersNoDownload && isFiltered(account, message);
    }
}
