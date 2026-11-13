/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Credits to other open-source projects are in the class comment below and
 * in NOTICE.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/*
 * Local backup of the MZGram settings (system.backup-cloud-sync, the local
 * part; the cloud part waits for the owner).
 *
 * The idea follows exteraless EtgBackup (GPL-3.0): one JSON file, a table of
 * what goes in it, and every value of a file checked against the table before
 * it is written back, because a file from elsewhere is not trusted. A value of
 * the wrong type or out of its range is left out, so a file cannot make
 * MZGram read a setting it cannot handle.
 *
 * What goes in: the switches of MZGramConfig ("mzgram_config") and the
 * mzgram_* files that hold settings: the side menu, the pills, the regex
 * filters, the style of the music player and the archive limits. Not in it:
 * the push keys and the push gateway, the plugins and their grants, the
 * passcode accounts, the app font (a file of this device), counters, journals
 * and the other state of this device. Nothing is sent anywhere: the file is
 * written where the user picks it (Android's "save as").
 *
 * The file:
 * {"format": "mzgram-settings", "version": 1,
 *  "files": {"mzgram_config": {"hideStories": true, ...}, ...}}
 * Restoring writes the values of the file over the current ones; the settings
 * that are not in the file stay as they are.
 */
public final class MZGramSettingsBackup {

    public static final String FORMAT = "mzgram-settings";
    public static final int VERSION = 1;
    public static final String FILE_NAME = "mzgram_settings.json";
    public static final String MIME_TYPE = "application/json";
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final String KEY_PLUGINS = "plugins";

    static final String CONFIG = "mzgram_config";
    static final String DRAWER = "mzgram_drawer";
    static final String PILLS = "mzgram_pills";
    static final String REGEX_FILTERS = "mzgram_regex_filters";
    static final String PLAYER_STYLE = "mzgram_player_style";
    static final String ARCHIVE = "mzgram_archive";

    private static final int BOOL = 0;
    private static final int INT = 1;
    private static final int TEXT = 2;
    private static final int TEXT_SET = 3;

    private static final int TEXT_MAX = 64 * 1024;
    private static final int RULES_MAX = 512 * 1024;
    private static final int SET_MAX = 200;
    private static final int SET_ITEM_MAX = 256;

    private static final class Spec {
        final String file;
        final String key;
        // Not null: the key is a family, e.g. "limit0".."limit3"
        final Pattern family;
        final int type;
        final long min;
        final long max;

        Spec(String file, String key, Pattern family, int type, long min, long max) {
            this.file = file;
            this.key = key;
            this.family = family;
            this.type = type;
            this.min = min;
            this.max = max;
        }

        boolean matches(String name) {
            return family != null ? family.matcher(name).matches() : key.equals(name);
        }
    }

    private static final ArrayList<Spec> SPECS = new ArrayList<>();

    static {
        bools(CONFIG,
                "disableNumberRounding", "formatTimeWithSeconds", "askBeforeCall", "hideStories",
                "storiesNoBackgroundLoad", "disableGreetingSticker", "hideChannelBottomButtons",
                "openArchiveOnPull", "disableInstantCamera", "hideCameraTile", "checkUpdates",
                "preferOriginalQuality", "autoPauseVideo", "showCopyPhoto", "showDeleteDownloadedFile",
                "showAddToSavedMessages", "showSetReminder", "showRepeat", "showOpenIn",
                "showMessageDetails", "showQrCode", "showNoQuoteForward", "showAskAi",
                "saveProtectedContent", "deleteForAllByDefault", "predictiveBackAnimation",
                "gooeyAvatarAnimation", "losslessPhotoCrop", "crashReports", "hideBottomNavigationBar",
                "accountsInMenu", "recentChatsOnBack", "extendedPins", "localFolders", "lastSeenExact", "pillStack", "iconPacks",
                "monetThemes", "useCameraX", "cameraXWideLens", "cameraXZoomSlider",
                "round_video_camera2_enabled", "round_video_high_fps", "musicPlayer",
                "musicPlayerBar", "folderTabsAtBottom", "confirmAVMessage", "mediaPreviewOnLongPress",
                "ghostMode", "ghostAutoDelaySend", "ghostSilentSend", "offerGhostModeBeforeStories",
                "ghostModeExclusions", "confirmInternalLinks", "disableReadStickers",
                "disableLinkPreviews", "disableCloudDrafts", "lockScreenVoiceProtection",
                "ownLastSeen", "keepGroupReads", "botStartConfirm", "reduceNetworkTracking",
                "saveDeletedAndEdited", "saveDeletedMessages", "saveEditHistory", "saveArchiveMedia",
                "reliableDelivery", "saveFormatting", "saveReactions", "saveFromUsers", "saveForBots",
                "saveInGroups", "saveInChannels", "saveExclusions", "keptReplyPreviews",
                "replyToDeletedAsQuote", "deletedMessagesList", "deletedDialogsList", "deletedDialogsInMainList",
                "m3Switches", "cardsDesign", "profileMusicCard", "openFreeMapPreview", "pipEnabled",
                "chatTranslationBar", "translateBeforeSend", "translationAutoFallback", "speechRecognitionEnabled",
                "torCompanionEnabled", "customBadgesEnabled", "nothingGlyphEnabled", "customEmojiFontEnabled",
                "syncedLyricsOnline", "iconPacksCatalogEnabled",
                "forwardDeletedAsCopy", "semiTransparentDeleted", "disableSponsoredMessages",
                "stripZalgoText", "foldFancyFontsInSearch", "characterCounter", "searchOperators",
                "cleanLinks", "doubleTapActions", "copyPlainText", "inlineMath", "stickerRoundCorners",
                "hideStickerTime", "wideChannelPosts", "granularGroupPermissions",
                "splitGifInlineRights", "linkedCustomEmoji", "linkTypeface", "hideReactionsPrivate",
                "hideReactionsGroups", "hideReactionsChannels", "shadowBan", "hideBlockedInGroups",
                "regexFilters", "regexFiltersInPrivate", "regexFiltersNoDownload", "customDrawer",
                "drawerShowQr", "drawerLargeAvatar", "drawerGlass", "plainCodeBlocks",
                "hexColorSwatch", "scheduleSlider", "sendSilentByDefault", "showRegistrationDate",
                "audioEnhance", "cameraStabilization", "pauseRoundOnLeave", "alwaysSendHd", "videoSubtitles", "persianCalendar",
                "hideChatStatus", "chat_header_compact", "staticInputIcons", "panguSpacing", "usePinnedReactionsChats",
                "usePinnedReactionsChannels", "relativeLastSeen", "uploadSpeedBoost", "disableNotificationDelay", "inAppVibration");
        // MZGramAvatarShape.MIN..MAX
        integer(CONFIG, "avatarCorners", 0, 28);
        // MZGramGlassOutline GLARE, SOLID, HIDDEN
        integer(CONFIG, "glassOutline", 0, 2);
        // MZGramFolderIcons TITLES, ICONS_AND_TITLES, ICONS
        integer(CONFIG, "folderTabIcons", 0, 2);
        // Download boost: off, fast, ultra
        integer(CONFIG, "downloadSpeedBoost", 0, 2);
        // MZGramVoiceQuality TELEGRAM, HIGH, HIGHEST
        integer(CONFIG, "voiceQuality", 0, 2);
        // MZGramMonet STYLE_ANDROID, STYLE_TELEGRAM
        integer(CONFIG, "monetStyle", 0, 1);
        // MZGramPeerId HIDDEN, TELEGRAM_API, BOT_API
        integer(CONFIG, "peerIdMode", 0, 2);
        // MZGramStickers.SIZE_MIN..SIZE_MAX
        integer(CONFIG, "stickerSize", 50, 150);
        // MZGramDrawer.HEADER_TITLES
        integer(CONFIG, "drawerHeaderStyle", 0, 3);
        // MZGramDoubleTap.TITLES
        integer(CONFIG, "doubleTapOwn", 0, 3);
        integer(CONFIG, "doubleTapOther", 0, 3);
        // MZGramFabShape.TITLES
        integer(CONFIG, "fabShape", 0, 2);
        // MZGramSchedulePresets picks the closest step; up to a year
        integer(CONFIG, "scheduleDefaultMinutes", 1, 366 * 24 * 60);
        // MZGramProxyAutoDisable.ALL; the mark "turned off here" is device state (mzgram_proxy)
        integer(CONFIG, "proxyDisableConditions", 0, 7);
        // MZGramDoH PROVIDERS 0..3
        integer(CONFIG, "dohProvider", 0, 3);
        // Translation provider 0..5
        integer(CONFIG, "translationProvider", 0, 5);
        // AI provider type 0..4
        integer(CONFIG, "aiProviderType", 0, 4);
        // Speech engine 0..1
        integer(CONFIG, "speechEngine", 0, 1);
        // Tor idle timeout minutes 1..60
        integer(CONFIG, "torIdleTimeoutMinutes", 1, 60);
        // Nothing glyph brightness 1..100
        integer(CONFIG, "nothingGlyphBrightness", 1, 100);
        // Round video bitrate
        integer(CONFIG, "round_video_bitrate", 0, 10_000_000);
        // Never in the file: API keys (deeplApiKey, aiApiKey, nothingGlyphApiKey) and the
        // servers that get the user's texts (aiCustomEndpoint, mozhiCustomInstance), so a
        // file from someone else cannot send them elsewhere
        texts(CONFIG, TEXT_MAX,
                "aiProvider", "language", "deletedMark", "editedMark", "hiddenReactions",
                "shadowBanList", "pinnedReactionsChats", "pinnedReactionsChannels",
                "textStyleOrder", "textStyleHidden", "pluginsCatalogUrl",
                "translationTargetLang", "aiCustomModel", "voskModelLang",
                "torCompanionPackage", "customEmojiFontPath", "saveFolder");
        textSets(CONFIG, "aiMenuButtons", "hiddenSettingsRows");

        // MZGramDrawerItems, MZGramPills: comma-separated ids, unknown ids are dropped when read
        texts(DRAWER, TEXT_MAX, "order", "hidden");
        texts(PILLS, TEXT_MAX, "order", "shown");
        integer(PILLS, "last", 0, 1000);

        // MZGramRegexFilters: JSON, read with a try/catch
        texts(REGEX_FILTERS, RULES_MAX, "rules", "chat_rules", "excluded");

        // MZGramPlayerStyle
        integer(PLAYER_STYLE, "coverStyle", 0, 3);
        integer(PLAYER_STYLE, "coverSize", 60, 100);
        integer(PLAYER_STYLE, "coverRadius", 0, 48);
        integer(PLAYER_STYLE, "titleAlign", 0, 1);
        integer(PLAYER_STYLE, "titleSize", 0, 2);
        integer(PLAYER_STYLE, "actionsStyle", 0, 1);
        integer(PLAYER_STYLE, "controlsSize", 0, 2);
        integer(PLAYER_STYLE, "colors", 0, 1);
        integer(PLAYER_STYLE, "background", 0, 2);
        bools(PLAYER_STYLE, "discSpin", "coverSwipe", "coverExtend", "titleAbove", "showActions",
                "showModes", "showLike", "showSource", "wavy");
        texts(PLAYER_STYLE, 64, "order");

        // MZGramArchiveLimits: megabytes per source (MZGramKeepFrom), -1 = no limit; not the counters
        SPECS.add(new Spec(ARCHIVE, "limit", Pattern.compile("limit[0-9]"), INT, -1, 1024 * 1024));
    }

    private MZGramSettingsBackup() {
    }

    private static void bools(String file, String... keys) {
        for (String key : keys) {
            SPECS.add(new Spec(file, key, null, BOOL, 0, 0));
        }
    }

    private static void integer(String file, String key, long min, long max) {
        SPECS.add(new Spec(file, key, null, INT, min, max));
    }

    private static void texts(String file, int maxLength, String... keys) {
        for (String key : keys) {
            SPECS.add(new Spec(file, key, null, TEXT, 0, maxLength));
        }
    }

    private static void textSets(String file, String... keys) {
        for (String key : keys) {
            SPECS.add(new Spec(file, key, null, TEXT_SET, 0, SET_MAX));
        }
    }

    /** The preference files that go into a backup, in the order of the file. */
    public static ArrayList<String> files() {
        ArrayList<String> result = new ArrayList<>();
        for (Spec spec : SPECS) {
            if (!result.contains(spec.file)) {
                result.add(spec.file);
            }
        }
        return result;
    }

    private static Spec spec(String file, String key) {
        if (file == null || key == null) {
            return null;
        }
        for (Spec spec : SPECS) {
            if (spec.file.equals(file) && spec.matches(key)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * The value to write for this key, or null if the key is not in the table
     * or the value is not the right one: a Boolean, an Integer, a String or a
     * HashSet of Strings. Takes the values of org.json (JSONArray for a set)
     * and of SharedPreferences (a Set).
     */
    public static Object accepted(String file, String key, Object value) {
        Spec spec = spec(file, key);
        if (spec == null || value == null) {
            return null;
        }
        switch (spec.type) {
            case BOOL:
                return value instanceof Boolean ? value : null;
            case INT: {
                if (!(value instanceof Integer) && !(value instanceof Long)) {
                    return null;
                }
                long number = ((Number) value).longValue();
                return number >= spec.min && number <= spec.max ? (int) number : null;
            }
            case TEXT: {
                if (!(value instanceof String)) {
                    return null;
                }
                String text = (String) value;
                return text.length() <= spec.max ? text : null;
            }
            case TEXT_SET: {
                ArrayList<Object> items = new ArrayList<>();
                if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    for (int i = 0; i < array.length(); i++) {
                        items.add(array.opt(i));
                    }
                } else if (value instanceof Collection) {
                    items.addAll((Collection<?>) value);
                } else {
                    return null;
                }
                if (items.size() > spec.max) {
                    return null;
                }
                HashSet<String> set = new HashSet<>();
                for (Object item : items) {
                    if (!(item instanceof String) || ((String) item).length() > SET_ITEM_MAX) {
                        return null;
                    }
                    set.add((String) item);
                }
                return set;
            }
            default:
                return null;
        }
    }

    /** The backup text of these values (file, then key, then value). */
    public static String toJson(Map<String, ? extends Map<String, Object>> values) {
        try {
            JSONObject files = new JSONObject();
            for (Map.Entry<String, ? extends Map<String, Object>> file : values.entrySet()) {
                JSONObject entries = new JSONObject();
                for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                    Object value = accepted(file.getKey(), entry.getKey(), entry.getValue());
                    if (value == null) {
                        continue;
                    }
                    if (value instanceof Set) {
                        JSONArray array = new JSONArray();
                        for (Object item : (Set<?>) value) {
                            array.put(item);
                        }
                        value = array;
                    }
                    entries.put(entry.getKey(), value);
                }
                if (entries.length() > 0) {
                    files.put(file.getKey(), entries);
                }
            }
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("version", VERSION);
            root.put("files", files);
            if (MZGramConfig.pluginsBackup) {
                try {
                    JSONArray pArray = org.telegram.messenger.mzgram.plugins.MZGramPluginsBackup.exportPluginsJson();
                    if (pArray != null && pArray.length() > 0) {
                        root.put(KEY_PLUGINS, pArray);
                    }
                } catch (Exception e) {
                    FileLog.e("MZGramSettingsBackup: failed to export plugins section", e);
                }
            }
            return root.toString(2);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * The values of a backup text that may be written back, or null if the
     * text is not an MZGram settings backup. Unknown files and keys, wrong
     * types and values out of range are left out.
     */
    public static LinkedHashMap<String, LinkedHashMap<String, Object>> parse(String text) {
        if (text == null || text.length() > MAX_FILE_BYTES) {
            return null;
        }
        JSONObject files;
        try {
            JSONObject root = new JSONObject(text);
            if (!FORMAT.equals(root.optString("format")) || root.optInt("version", 0) < 1) {
                return null;
            }
            files = root.optJSONObject("files");
        } catch (Exception e) {
            return null;
        }
        LinkedHashMap<String, LinkedHashMap<String, Object>> result = new LinkedHashMap<>();
        if (files == null) {
            return result;
        }
        for (String file : files()) {
            JSONObject entries = files.optJSONObject(file);
            if (entries == null) {
                continue;
            }
            LinkedHashMap<String, Object> accepted = new LinkedHashMap<>();
            Iterator<String> keys = entries.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = accepted(file, key, entries.opt(key));
                if (value != null) {
                    accepted.put(key, value);
                }
            }
            if (!accepted.isEmpty()) {
                result.put(file, accepted);
            }
        }
        return result;
    }

    /** How many settings these values hold. */
    public static int count(Map<String, ? extends Map<String, Object>> values) {
        int count = 0;
        if (values != null) {
            for (Map<String, Object> entries : values.values()) {
                count += entries.size();
            }
        }
        return count;
    }

    private static SharedPreferences preferences(String file) {
        return ApplicationLoader.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    /** The current settings of this device that go into a backup. */
    public static LinkedHashMap<String, LinkedHashMap<String, Object>> current() {
        LinkedHashMap<String, LinkedHashMap<String, Object>> result = new LinkedHashMap<>();
        for (String file : files()) {
            Map<String, ?> all = preferences(file).getAll();
            LinkedHashMap<String, Object> entries = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                Object value = accepted(file, entry.getKey(), entry.getValue());
                if (value != null) {
                    entries.put(entry.getKey(), value);
                }
            }
            if (!entries.isEmpty()) {
                result.put(file, entries);
            }
        }
        return result;
    }

    /** The backup text of this device's settings (not on the UI thread). */
    public static String export() {
        return toJson(current());
    }

    public static String exportToJson() {
        return export();
    }

    /** Reads a backup file of at most MAX_FILE_BYTES; null if it is longer or unreadable. */
    public static String read(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) > 0) {
            if (out.size() + read > MAX_FILE_BYTES) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Writes the values of a backup over the current settings (not on the UI
     * thread) and reloads MZGramConfig. Other screens read their settings
     * when they open; some only after a restart. Returns how many were written.
     */
    @SuppressWarnings("unchecked")
    public static int restore(Map<String, ? extends Map<String, Object>> values) {
        return restore(values, null);
    }

    @SuppressWarnings("unchecked")
    public static int restore(Map<String, ? extends Map<String, Object>> values, String fullJson) {
        final boolean hadPluginsBackupEnabled = MZGramConfig.pluginsBackup;
        int written = 0;
        if (values == null) {
            return 0;
        }
        for (Map.Entry<String, ? extends Map<String, Object>> file : values.entrySet()) {
            if (!files().contains(file.getKey())) {
                continue;
            }
            SharedPreferences.Editor editor = preferences(file.getKey()).edit();
            int inFile = 0;
            for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                Object value = accepted(file.getKey(), entry.getKey(), entry.getValue());
                if (value instanceof Boolean) {
                    editor.putBoolean(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(entry.getKey(), (Integer) value);
                } else if (value instanceof String) {
                    editor.putString(entry.getKey(), (String) value);
                } else if (value instanceof Set) {
                    editor.putStringSet(entry.getKey(), (Set<String>) value);
                } else {
                    continue;
                }
                inFile++;
            }
            if (inFile > 0 && editor.commit()) {
                written += inFile;
            }
        }
        MZGramConfig.loadConfig(true);

        // Corrupted plugins section does not break import of settings;
        // only restore plugins if the user had explicitly enabled pluginsBackup prior to restore
        if (hadPluginsBackupEnabled && fullJson != null) {
            try {
                org.telegram.messenger.mzgram.plugins.MZGramPluginsBackup.restorePluginsFromJson(fullJson);
            } catch (Exception e) {
                FileLog.e("MZGramSettingsBackup: corrupted plugins section ignored", e);
            }
        }
        return written;
    }
}
