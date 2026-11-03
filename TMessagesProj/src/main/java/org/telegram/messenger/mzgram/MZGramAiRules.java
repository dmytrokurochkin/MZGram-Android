/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * "Ask AI": a message and the user's own prompt go into the chat of an AI
 * site (Gemini, Claude, ChatGPT, DeepSeek, Grok) in MZGram's own window
 * (MZGramAiActivity), with no paid API: the user signs in to the site
 * with their own account. Here: the sites, the text, the script that puts
 * it into the site's input field, and where the window may go. No Android
 * views here, so all of it can be tested directly.
 *
 * Photos and files of the messages go with the text: classifyFile sorts
 * them, chooseAttachments keeps the first MAX_ATTACHMENTS, and give says
 * which of the checked ones the site's file field takes when the site asks
 * for files (its own add-files button, MZGramAiFilesSheet).
 *
 * The text gets into the site only by that script: none of the five sites
 * has a documented address parameter that fills its input field without
 * sending it (ChatGPT's and Grok's "q" parameter sends it at once), and none has a
 * reliable one for the model, so the model is chosen on the site.
 */

package org.telegram.messenger.mzgram;

import android.net.Uri;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MZGramAiRules {

    // The WebView profile of the AI sites (androidx.webkit Profile): their
    // sign-ins and data stay apart from Telegram's WebViews (Mini Apps).
    public static final String PROFILE = "mzgram-ai";
    public static final String DEFAULT_PROVIDER = "gemini";

    // What the script says (parseStatus)
    public static final int STATUS_NONE = 0;
    public static final int STATUS_WAITING = 1;
    public static final int STATUS_INSERTED = 2;
    public static final int STATUS_NO_FIELD = 3;

    // Where a link the site opens in a new window goes (newWindowTarget)
    public static final int NEW_WINDOW_HERE = 0;
    public static final int NEW_WINDOW_BROWSER = 1;
    public static final int NEW_WINDOW_REFUSE = 2;

    // The window tries the script every half second, for half a minute: the
    // sites build their input field after the page has loaded.
    public static final int TRIES = 60;
    public static final int TRY_INTERVAL_MS = 500;

    public static final class Provider {
        public final String key;
        public final String name;
        public final String startUrl;
        public final List<String> hosts;
        public final List<String> inputSelectors;

        Provider(String key, String name, String startUrl, String[] hosts, String[] inputSelectors) {
            this.key = key;
            this.name = name;
            this.startUrl = startUrl;
            this.hosts = Collections.unmodifiableList(Arrays.asList(hosts));
            this.inputSelectors = Collections.unmodifiableList(Arrays.asList(inputSelectors));
        }
    }

    // The input fields as the sites had them in 2026. A site may change its
    // page: then the window says no field was found and the text can still be
    // copied and pasted by hand.
    private static final List<Provider> PROVIDERS = Collections.unmodifiableList(Arrays.asList(
            new Provider("gemini", "Gemini", "https://gemini.google.com/app",
                    new String[]{"gemini.google.com"},
                    new String[]{"rich-textarea .ql-editor[contenteditable=\"true\"]", "div.ql-editor[contenteditable=\"true\"]"}),
            new Provider("claude", "Claude", "https://claude.ai/new",
                    new String[]{"claude.ai"},
                    new String[]{"div.ProseMirror[contenteditable=\"true\"]", "[data-testid=\"chat-input\"]"}),
            new Provider("chatgpt", "ChatGPT", "https://chatgpt.com/",
                    new String[]{"chatgpt.com", "openai.com"},
                    new String[]{"#prompt-textarea", "div.ProseMirror[contenteditable=\"true\"]"}),
            new Provider("deepseek", "DeepSeek", "https://chat.deepseek.com/",
                    new String[]{"deepseek.com"},
                    new String[]{"textarea#chat-input", "textarea"}),
            new Provider("grok", "Grok", "https://grok.com/",
                    new String[]{"grok.com", "x.ai", "x.com"},
                    new String[]{"textarea[aria-label]", "div.ProseMirror[contenteditable=\"true\"]", "textarea"})
    ));

    // Pages the sites sign in with; they stay in the window.
    private static final List<String> SIGN_IN_HOSTS = Arrays.asList(
            "accounts.google.com",
            "appleid.apple.com",
            "login.microsoftonline.com",
            "login.live.com"
    );

    // Generic fields after the site's own: a site that changed its page may
    // still have one of these.
    private static final List<String> ANY_INPUT = Arrays.asList(
            "textarea:not([readonly]):not([disabled])",
            "[contenteditable=\"true\"]"
    );

    private MZGramAiRules() {
    }

    public static List<Provider> providers() {
        return PROVIDERS;
    }

    // null for a key that is not one of the five
    public static Provider provider(String key) {
        for (Provider provider : PROVIDERS) {
            if (provider.key.equals(key)) {
                return provider;
            }
        }
        return null;
    }

    public static Provider providerOrDefault(String key) {
        Provider provider = provider(key);
        return provider != null ? provider : provider(DEFAULT_PROVIDER);
    }

    // The site "Ask AI" starts with, kept in MZGram's settings.
    public static Provider defaultProvider() {
        return providerOrDefault(MZGramConfig.aiProvider);
    }

    public static void setDefaultProvider(String key) {
        MZGramConfig.setAiProvider(providerOrDefault(key).key);
    }

    // The prompt, an empty line, the message; either may be empty.
    public static String composeText(String prompt, String message) {
        String first = clean(prompt);
        String second = clean(message);
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first + "\n\n" + second;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    // A JavaScript string literal of only printable ASCII: everything else,
    // quotes, the backslash and the HTML characters are \\u escapes, so no
    // text can end the string or the script.
    public static String scriptString(String text) {
        StringBuilder result = new StringBuilder(text.length() + 2);
        result.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean plain = ch >= 0x20 && ch < 0x7F
                    && ch != '"' && ch != '\\' && ch != '\''
                    && ch != '<' && ch != '>' && ch != '&';
            if (plain) {
                result.append(ch);
            } else {
                result.append(String.format(Locale.US, "\\u%04x", (int) ch));
            }
        }
        result.append('"');
        return result.toString();
    }

    // One try: puts the text into the first visible field, the way typing or
    // pasting would, reads it back and gives "inserted", "no_field" or, with
    // no field yet, "waiting". Nothing here presses a key or a button.
    public static String insertScript(Provider provider, String text) {
        StringBuilder selectors = new StringBuilder();
        List<String> all = new ArrayList<>(provider.inputSelectors);
        all.addAll(ANY_INPUT);
        for (String selector : all) {
            if (selectors.length() > 0) {
                selectors.append(',');
            }
            selectors.append(scriptString(selector));
        }
        return "(function(){\n"
                + "var text = " + scriptString(text) + ";\n"
                + "var selectors = [" + selectors + "];\n"
                + "function shown(e) { var r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; }\n"
                + "function field() {\n"
                + "  for (var i = 0; i < selectors.length; i++) {\n"
                + "    var list = document.querySelectorAll(selectors[i]);\n"
                + "    for (var j = 0; j < list.length; j++) { if (shown(list[j])) return list[j]; }\n"
                + "  }\n"
                + "  return null;\n"
                + "}\n"
                + "function flat(s) { return (s || '').replace(/\\s+/g, ' ').trim(); }\n"
                + "function plain(e) { return e.tagName === 'TEXTAREA' || e.tagName === 'INPUT'; }\n"
                + "function holds(e) { return flat(plain(e) ? e.value : e.innerText).indexOf(flat(text)) >= 0; }\n"
                + "function put(e) {\n"
                + "  e.focus();\n"
                + "  if (plain(e)) {\n"
                + "    var proto = e.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;\n"
                + "    Object.getOwnPropertyDescriptor(proto, 'value').set.call(e, text);\n"
                + "    e.dispatchEvent(new Event('input', { bubbles: true }));\n"
                + "    return;\n"
                + "  }\n"
                + "  var range = document.createRange();\n"
                + "  range.selectNodeContents(e);\n"
                + "  var selection = window.getSelection();\n"
                + "  selection.removeAllRanges();\n"
                + "  selection.addRange(range);\n"
                + "  document.execCommand('insertText', false, text);\n"
                + "  if (holds(e)) return;\n"
                + "  try {\n"
                + "    var data = new DataTransfer();\n"
                + "    data.setData('text/plain', text);\n"
                + "    e.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));\n"
                + "  } catch (x) {}\n"
                + "  if (holds(e)) return;\n"
                + "  e.textContent = text;\n"
                + "  e.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: text }));\n"
                + "}\n"
                + "var e = field();\n"
                + "if (!e) return 'waiting';\n"
                + "put(e);\n"
                + "return holds(e) ? 'inserted' : 'no_field';\n"
                + "})();";
    }

    // What WebView.evaluateJavascript gives for insertScript: the returned
    // string as a JSON value.
    public static int parseStatus(String result) {
        if (result == null) {
            return STATUS_NONE;
        }
        switch (result.trim()) {
            case "\"inserted\"": return STATUS_INSERTED;
            case "\"no_field\"": return STATUS_NO_FIELD;
            case "\"waiting\"": return STATUS_WAITING;
            default: return STATUS_NONE;
        }
    }

    // Attachments: photos and documents of the chosen messages go with the
    // text, at most MAX_ATTACHMENTS (what the sites take in one message),
    // each at most MAX_ATTACHMENT_SIZE (the smallest limit of the five sites).
    public static final int MAX_ATTACHMENTS = 10;
    public static final long MAX_ATTACHMENT_SIZE = 30L * 1024 * 1024;
    // The window waits this long for the files to download.
    public static final int DOWNLOAD_TIMEOUT_MS = 120_000;

    public static final int KIND_PHOTO = 0; // A photo, or a jpg, png or webp sent as a file
    public static final int KIND_FILE = 1; // A document an AI reads: text, code, pdf, office files
    public static final int KIND_ARCHIVE = 2; // zip, rar, 7z, tar, gz, apk and the like: never sent
    public static final int KIND_MEDIA = 3; // Video, GIF, sticker, voice, round video, music: never sent
    public static final int KIND_UNSUPPORTED = 4; // Any other file

    private static final Set<String> IMAGE_EXTENSIONS = set("jpg", "jpeg", "png", "webp");
    private static final Set<String> IMAGE_TYPES = set("image/jpeg", "image/png", "image/webp");
    private static final Set<String> FILE_EXTENSIONS = set(
            "txt", "text", "md", "markdown", "rst", "csv", "tsv", "json", "jsonl",
            "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "log", "tex", "srt",
            "vtt", "html", "htm", "css", "js", "mjs", "cjs", "ts", "jsx", "tsx",
            "py", "ipynb", "java", "kt", "kts", "c", "h", "cpp", "cc", "cxx", "hpp",
            "cs", "go", "rs", "rb", "php", "swift", "m", "mm", "scala", "dart",
            "lua", "pl", "r", "sql", "sh", "bash", "zsh", "ps1", "bat", "cmd",
            "gradle", "properties", "vue", "svelte", "pdf", "doc", "docx", "xls",
            "xlsx", "ppt", "pptx", "rtf", "odt", "ods", "odp", "epub");
    // Text files that usually have no extension
    private static final Set<String> FILE_NAMES = set(
            "readme", "license", "licence", "changelog", "makefile", "dockerfile",
            "notice", "authors", "contributing");
    private static final Set<String> FILE_TYPES = set(
            "application/pdf", "application/json", "application/xml",
            "application/rtf", "application/msword", "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint");
    private static final String[] FILE_TYPE_PREFIXES = {
            "application/vnd.openxmlformats-officedocument.",
            "application/vnd.oasis.opendocument.",
    };
    // Archives and packages: large, and the sites read them badly or not at all
    private static final Set<String> ARCHIVE_EXTENSIONS = set(
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz",
            "txz", "zst", "lz", "lzma", "lz4", "z", "cab", "iso", "dmg", "apk",
            "apks", "xapk", "aab", "ipa", "jar", "war", "deb", "rpm", "msix",
            "appx");
    private static final Set<String> ARCHIVE_TYPES = set(
            "application/zip", "application/x-zip-compressed", "application/gzip",
            "application/x-gzip", "application/x-tar", "application/x-7z-compressed",
            "application/x-rar-compressed", "application/vnd.rar",
            "application/x-bzip2", "application/x-xz", "application/zstd",
            "application/vnd.android.package-archive", "application/java-archive",
            "application/x-iso9660-image");
    private static final Set<String> MEDIA_EXTENSIONS = set(
            "gif", "tgs", "webm", "mp4", "mov", "mkv", "avi", "m4v", "mp3",
            "ogg", "oga", "opus", "m4a", "wav", "flac", "aac");

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    // A file by its name and MIME type (a photo: KIND_PHOTO with no name).
    public static int classifyFile(String name, String mime) {
        String lower = name == null ? "" : name.trim().toLowerCase(Locale.US);
        String type = mime == null ? "" : mime.trim().toLowerCase(Locale.US);
        int dot = lower.lastIndexOf('.');
        String extension = dot >= 0 ? lower.substring(dot + 1) : "";
        String base = dot > 0 ? lower.substring(0, dot) : lower;
        if (ARCHIVE_EXTENSIONS.contains(extension) || ARCHIVE_TYPES.contains(type)) {
            return KIND_ARCHIVE;
        } else if (IMAGE_EXTENSIONS.contains(extension) || (extension.isEmpty() && IMAGE_TYPES.contains(type))) {
            return KIND_PHOTO;
        } else if (type.startsWith("video/") || type.startsWith("audio/") || type.equals("image/gif") || MEDIA_EXTENSIONS.contains(extension)) {
            return KIND_MEDIA;
        } else if (FILE_EXTENSIONS.contains(extension) || FILE_NAMES.contains(base) || FILE_NAMES.contains(lower)
                || type.startsWith("text/") || FILE_TYPES.contains(type)) {
            return KIND_FILE;
        }
        for (String prefix : FILE_TYPE_PREFIXES) {
            if (type.startsWith(prefix)) {
                return KIND_FILE;
            }
        }
        return KIND_UNSUPPORTED;
    }

    public static final class Candidate {
        public final int kind;
        public final long size;

        public Candidate(int kind, long size) {
            this.kind = kind;
            this.size = size;
        }
    }

    public static final class Choice {
        public final List<Integer> chosen = new ArrayList<>(); // Indices into the candidates, in order
        public int overLimit; // Past MAX_ATTACHMENTS
        public int tooLarge; // Over MAX_ATTACHMENT_SIZE
        public int archives;
        public int media;
        public int unsupported;

        public int leftOut() {
            return overLimit + tooLarge + archives + media + unsupported;
        }
    }

    // The first MAX_ATTACHMENTS photos and files that are not too large, and
    // what is left out and why.
    public static Choice chooseAttachments(List<Candidate> candidates) {
        Choice result = new Choice();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            switch (candidate.kind) {
                case KIND_ARCHIVE: result.archives++; break;
                case KIND_MEDIA: result.media++; break;
                case KIND_PHOTO:
                case KIND_FILE:
                    if (candidate.size > MAX_ATTACHMENT_SIZE) {
                        result.tooLarge++;
                    } else if (result.chosen.size() >= MAX_ATTACHMENTS) {
                        result.overLimit++;
                    } else {
                        result.chosen.add(i);
                    }
                    break;
                default: result.unsupported++; break;
            }
        }
        return result;
    }

    // The files chooser: what goes to the site's file field now and what
    // waits. A field for several files takes every checked file at once; a
    // field for one file takes the first, and the rest wait for the next
    // press of the site's button.
    public static final class Give {
        public final List<Integer> now = new ArrayList<>();
        public final List<Integer> later = new ArrayList<>();
    }

    public static Give give(List<Integer> checked, boolean multiple) {
        Give result = new Give();
        // Not done yet: the tests come first.
        return result;
    }

    // Whether the site's file field takes the file, by its accept list
    // ("image/*", "application/pdf", ".pdf"); an empty list or "*/*" takes
    // everything.
    public static boolean accepts(String[] accept, String name, String mime) {
        // Not done yet: the tests come first.
        return false;
    }

    // Settings > MZGram > AI: a button for each site in the chat list menu,
    // which opens the site with no text; all off by default.
    public static boolean menuButton(Provider provider) {
        return provider != null && MZGramConfig.aiMenuButtons.contains(provider.key);
    }

    public static void setMenuButton(Provider provider, boolean shown) {
        if (provider != null) {
            MZGramConfig.setAiMenuButton(provider.key, shown);
        }
    }

    // The window loads only https pages.
    public static boolean allowedNavigation(String url) {
        if ("about:blank".equals(url)) {
            return true;
        }
        if (url == null || url.isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null && !host.isEmpty();
    }

    // A link the site opens in a new window: the site itself and its sign-in
    // pages stay in MZGram's window, other https pages go to the browser,
    // anything else is refused.
    public static int newWindowTarget(Provider provider, String url) {
        if ("about:blank".equals(url) || !allowedNavigation(url)) {
            return NEW_WINDOW_REFUSE;
        }
        String host = Uri.parse(url).getHost().toLowerCase(Locale.US);
        for (String domain : provider.hosts) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return NEW_WINDOW_HERE;
            }
        }
        if (SIGN_IN_HOSTS.contains(host)) {
            return NEW_WINDOW_HERE;
        }
        return NEW_WINDOW_BROWSER;
    }
}
