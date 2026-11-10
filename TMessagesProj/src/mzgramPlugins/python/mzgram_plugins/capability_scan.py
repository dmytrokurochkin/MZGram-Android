# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# What a plugin may do, read from its code without running it: the prompt
# before a plugin is added (MZGramPluginInstallSheet) shows it next to the
# permissions the plugin declares, so a plugin that says it needs no network
# but has network code in it is pointed out.
#
# The scan is a guess, not a guarantee: names built at run time and code made
# hard to read get past it, which is why code made hard to read is reported
# by itself. What is not found is not impossible either; the real line is
# held while the plugin runs, by the sink gates (sink_gate.py and
# MZGramPluginSinkGate) and the permissions (permissions.py).
#
# The evidence is given by technical names (requests, SendMessagesHelper,
# ctypes): they read the same in every language and point straight at the
# code, which the code screen shows.
#
# Based on exteraless extera_utils/capability_scan.py (GPL-3.0): the markers,
# the import rules, the signs of code made hard to read and the DEX reader.
# Changed for MZGram: one .plugin file (no archives), processes and the
# plugin engine are "never" (the sink gates never allow them), Java classes
# named in jclass()/find_class() strings follow
# MZGramPluginSinkPolicy.classRule, and a DEX in the file counts as "hooks"
# (loading it needs that permission).
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import ast
import base64
import json
import re
import struct
import zlib

PERM_MESSAGES_READ = "messages.read"
PERM_MESSAGES_SEND = "messages.send"
PERM_NETWORK = "network"
PERM_FILES = "files"
PERM_INTENTS = "intents"
PERM_SETTINGS = "settings"
PERM_HOOKS = "hooks"
PERM_NATIVE = "native"

#: Not a permission: what MZGram never allows a plugin (sink gates).
KEY_NEVER = "never"
#: Not a permission: the code is made hard to read.
KEY_OBFUSCATION = "obfuscation"
#: Not a permission: compiled Android code carried in the file ("size:N", "class:X", "more:N").
KEY_DEX = "dex"

PERMISSIONS = (PERM_MESSAGES_READ, PERM_MESSAGES_SEND, PERM_NETWORK, PERM_FILES,
               PERM_INTENTS, PERM_SETTINGS, PERM_HOOKS, PERM_NATIVE)
KEYS = PERMISSIONS + (KEY_NEVER, KEY_OBFUSCATION, KEY_DEX)

#: Marker -> (key, evidence). A marker is looked for as a part of the text.
_MARKERS = (
    # ---- network ----
    ("import requests", PERM_NETWORK, "requests"),
    ("urllib.request", PERM_NETWORK, "urllib"),
    ("http.client", PERM_NETWORK, "http.client"),
    ("import socket", PERM_NETWORK, "socket"),
    ("websocket", PERM_NETWORK, "websocket"),
    ("java.net", PERM_NETWORK, "java.net"),
    ("HttpURLConnection", PERM_NETWORK, "HttpURLConnection"),
    ("OkHttpClient", PERM_NETWORK, "okhttp"),
    ("WebView", PERM_NETWORK, "WebView"),
    ("DownloadManager", PERM_NETWORK, "DownloadManager"),
    ("loadHttpFile", PERM_NETWORK, "loadHttpFile"),
    ("setDataSource", PERM_NETWORK, "setDataSource"),
    # ---- reading chats ----
    ("MessagesStorage", PERM_MESSAGES_READ, "MessagesStorage"),
    ("SQLiteDatabase", PERM_MESSAGES_READ, "MessagesStorage"),
    ("queryFinalized", PERM_MESSAGES_READ, "SQLite"),
    ("on_update", PERM_MESSAGES_READ, "on_update"),
    ("add_hook(", PERM_MESSAGES_READ, "request hooks"),
    ("add_request_hook", PERM_MESSAGES_READ, "request hooks"),
    ("pre_request_hook", PERM_MESSAGES_READ, "request hooks"),
    ("post_request_hook", PERM_MESSAGES_READ, "request hooks"),
    ("send_request(", PERM_MESSAGES_READ, "send_request"),
    ("sendRequest(", PERM_MESSAGES_READ, "sendRequest"),
    ("get_messages", PERM_MESSAGES_READ, "getMessages"),
    ("getMessages", PERM_MESSAGES_READ, "getMessages"),
    # ---- sending ----
    ("SendMessagesHelper", PERM_MESSAGES_SEND, "SendMessagesHelper"),
    ("send_message(", PERM_MESSAGES_SEND, "send_message"),
    ("send_text(", PERM_MESSAGES_SEND, "send_text"),
    ("send_photo(", PERM_MESSAGES_SEND, "send_photo"),
    ("send_video(", PERM_MESSAGES_SEND, "send_video"),
    ("send_document(", PERM_MESSAGES_SEND, "send_document"),
    ("send_audio(", PERM_MESSAGES_SEND, "send_audio"),
    ("edit_message(", PERM_MESSAGES_SEND, "edit_message"),
    ("get_send_messages_helper", PERM_MESSAGES_SEND, "SendMessagesHelper"),
    ("prepareSending", PERM_MESSAGES_SEND, "prepareSending"),
    ("sendSticker", PERM_MESSAGES_SEND, "sendSticker"),
    ("sendGame", PERM_MESSAGES_SEND, "sendGame"),
    ("sendCurrentLocation", PERM_MESSAGES_SEND, "sendCurrentLocation"),
    ("retrySendMessage", PERM_MESSAGES_SEND, "retrySendMessage"),
    ("editMessage(", PERM_MESSAGES_SEND, "editMessage"),
    ("on_send_message", PERM_MESSAGES_SEND, "on_send_message"),
    ("TL_messages_send", PERM_MESSAGES_SEND, "TL_messages_send"),
    ("TL_messages_edit", PERM_MESSAGES_SEND, "TL_messages_edit"),
    ("TL_messages_delete", PERM_MESSAGES_SEND, "TL_messages_delete"),
    ("ConnectionsManager", PERM_MESSAGES_SEND, "ConnectionsManager"),
    # ---- files ----
    ("java.io.File", PERM_FILES, "java.io.File"),
    ("FileOutputStream", PERM_FILES, "FileOutputStream"),
    ("ContentResolver", PERM_FILES, "ContentResolver"),
    ("MediaStore", PERM_FILES, "MediaStore"),
    ("import shutil", PERM_FILES, "shutil"),
    ("os.remove", PERM_FILES, "os.remove"),
    ("os.listdir", PERM_FILES, "os.listdir"),
    ("os.walk", PERM_FILES, "os.walk"),
    ("sqlite3", PERM_FILES, "sqlite3"),
    ("get_files_dir", PERM_FILES, "get_files_dir"),
    ("get_cache_dir", PERM_FILES, "get_cache_dir"),
    ("get_images_dir", PERM_FILES, "get_images_dir"),
    ("get_videos_dir", PERM_FILES, "get_videos_dir"),
    ("get_audios_dir", PERM_FILES, "get_audios_dir"),
    ("get_documents_dir", PERM_FILES, "get_documents_dir"),
    ("getFilesDir", PERM_FILES, "getFilesDir"),
    ("getCacheDir", PERM_FILES, "getCacheDir"),
    ("getExternalFilesDir", PERM_FILES, "getExternalFilesDir"),
    ("getExternalStorage", PERM_FILES, "getExternalStorage"),
    # ---- intents ----
    ("startActivity", PERM_INTENTS, "startActivity"),
    ("android.content.Intent", PERM_INTENTS, "Intent"),
    ("register_intent_handler", PERM_INTENTS, "intent hooks"),
    # ---- app settings ----
    ("MZGramConfig", PERM_SETTINGS, "app config"),
    ("SharedConfig", PERM_SETTINGS, "app config"),
    ("getSharedPreferences", PERM_SETTINGS, "SharedPreferences"),
    ("getGlobalMainSettings", PERM_SETTINGS, "SharedPreferences"),
    ("NekoConfig", PERM_SETTINGS, "app config"),
    ("NaConfig", PERM_SETTINGS, "app config"),
    ("ExteraConfig", PERM_SETTINGS, "app config"),
    # ---- hooks and code made at run time ----
    ("MethodHook", PERM_HOOKS, "Xposed"),
    ("MethodReplacement", PERM_HOOKS, "Xposed"),
    ("hook_method", PERM_HOOKS, "Xposed"),
    ("hook_all_methods", PERM_HOOKS, "Xposed"),
    ("hook_all_constructors", PERM_HOOKS, "Xposed"),
    ("XposedBridge", PERM_HOOKS, "Xposed"),
    ("InMemoryDexClassLoader", PERM_HOOKS, "DexClassLoader"),
    ("DexClassLoader", PERM_HOOKS, "DexClassLoader"),
    ("generate_proxy_class", PERM_HOOKS, "class proxy"),
    ("java_subclass", PERM_HOOKS, "class proxy"),
    ("joverride", PERM_HOOKS, "class proxy"),
    ("allocate_instance", PERM_HOOKS, "class proxy"),
    ("deoptimize", PERM_HOOKS, "deoptimize"),
    ("TYPE_APPLICATION_OVERLAY", PERM_HOOKS, "overlay window"),
    ("TYPE_SYSTEM_ALERT", PERM_HOOKS, "overlay window"),
    ("TYPE_SYSTEM_OVERLAY", PERM_HOOKS, "overlay window"),
    ("LayoutParams.TYPE_PHONE", PERM_HOOKS, "overlay window"),
    # ---- native code ----
    ("import ctypes", PERM_NATIVE, "ctypes"),
    ("ctypes.CDLL", PERM_NATIVE, "ctypes"),
    ("CDLL(", PERM_NATIVE, "ctypes"),
    ("loadLibrary", PERM_NATIVE, "loadLibrary"),
    (".so\"", PERM_NATIVE, "native library"),
    (".so'", PERM_NATIVE, "native library"),
    # ---- never available to plugins ----
    ("ProcessBuilder", KEY_NEVER, "ProcessBuilder"),
    ("Runtime.getRuntime().exec", KEY_NEVER, "Runtime.exec"),
    ("addaudithook", KEY_NEVER, "sys.addaudithook"),
    ("org.telegram.messenger.mzgram.plugins", KEY_NEVER, "MZGram plugin engine"),
    ("org.telegram.ui.mzgram.MZGramPlugin", KEY_NEVER, "MZGram plugin engine"),
)

#: What is imported from the app's packages: the package itself says nothing
#: (AndroidUtilities.dp() comes from the same package as MessagesController).
_IMPORTED_NAMES = {
    "MessagesController": (PERM_MESSAGES_READ, "MessagesController"),
    "MessagesStorage": (PERM_MESSAGES_READ, "MessagesStorage"),
    "MessageObject": (PERM_MESSAGES_READ, "MessageObject"),
    "NotificationCenter": (PERM_MESSAGES_READ, "NotificationCenter"),
    "SendMessagesHelper": (PERM_MESSAGES_SEND, "SendMessagesHelper"),
    "ConnectionsManager": (PERM_MESSAGES_SEND, "ConnectionsManager"),
    "ContentResolver": (PERM_FILES, "ContentResolver"),
    "MediaStore": (PERM_FILES, "MediaStore"),
    "WebView": (PERM_NETWORK, "WebView"),
    "InMemoryDexClassLoader": (PERM_HOOKS, "DexClassLoader"),
    "DexClassLoader": (PERM_HOOKS, "DexClassLoader"),
    "XposedBridge": (PERM_HOOKS, "Xposed"),
    "XposedHelpers": (PERM_HOOKS, "Xposed"),
    "ProcessBuilder": (KEY_NEVER, "ProcessBuilder"),
}

_IMPORTED_MODULES = {
    "ctypes": (PERM_NATIVE, "ctypes"),
    "_ctypes": (PERM_NATIVE, "ctypes"),
    "cffi": (PERM_NATIVE, "cffi"),
    "requests": (PERM_NETWORK, "requests"),
    "httpx": (PERM_NETWORK, "httpx"),
    "aiohttp": (PERM_NETWORK, "aiohttp"),
    "urllib": (PERM_NETWORK, "urllib"),
    "urllib3": (PERM_NETWORK, "urllib"),
    "http": (PERM_NETWORK, "http.client"),
    "socket": (PERM_NETWORK, "socket"),
    "socketserver": (PERM_NETWORK, "socket"),
    "ssl": (PERM_NETWORK, "ssl"),
    "ftplib": (PERM_NETWORK, "ftplib"),
    "smtplib": (PERM_NETWORK, "smtplib"),
    "poplib": (PERM_NETWORK, "poplib"),
    "imaplib": (PERM_NETWORK, "imaplib"),
    "telnetlib": (PERM_NETWORK, "telnetlib"),
    "websocket": (PERM_NETWORK, "websocket"),
    "websockets": (PERM_NETWORK, "websocket"),
    "shutil": (PERM_FILES, "shutil"),
    "sqlite3": (PERM_FILES, "sqlite3"),
    "subprocess": (KEY_NEVER, "subprocess"),
    "pty": (KEY_NEVER, "pty"),
    "multiprocessing": (KEY_NEVER, "multiprocessing"),
}

#: os functions that start other programs (the sink gates never allow them).
_PROCESS_CALLS = re.compile(r"^(system|popen|fork|forkpty|exec[lv]p?e?|spawn[lv]p?e?|posix_spawnp?)$")

#: The rule of a Java class name as MZGramPluginSinkPolicy gives it.
_RULE_KEYS = {1: PERM_NETWORK, 2: PERM_HOOKS, 3: KEY_NEVER}

#: Without the Java side (tests on a computer): the main part of the same
#: groups; a name ending in "." is a package, one ending in "*" a prefix.
_FALLBACK_RULES = (
    (("java.lang.ProcessBuilder", "java.lang.ProcessImpl", "java.lang.UNIXProcess", "java.lang.ProcessManager",
      "org.telegram.messenger.mzgram.plugins.", "org.telegram.ui.mzgram.MZGramPlugin*"), KEY_NEVER),
    (("dalvik.system.DexClassLoader", "dalvik.system.PathClassLoader", "dalvik.system.InMemoryDexClassLoader",
      "dalvik.system.BaseDexClassLoader", "dalvik.system.DelegateLastClassLoader", "dalvik.system.DexFile",
      "dalvik.system.VMRuntime", "dalvik.system.VMDebug", "de.robv.android.xposed.", "com.aliucord.",
      "org.lsposed.", "sun.misc.Unsafe", "jdk.internal.misc.Unsafe", "java.security.KeyStore",
      "android.security.keystore."), PERM_HOOKS),
    (("java.net.URL", "java.net.URLConnection", "java.net.HttpURLConnection", "java.net.JarURLConnection",
      "java.net.Socket", "java.net.ServerSocket", "java.net.DatagramSocket", "java.net.MulticastSocket",
      "java.net.InetAddress", "java.net.Inet4Address", "java.net.Inet6Address",
      "java.nio.channels.SocketChannel", "java.nio.channels.ServerSocketChannel",
      "java.nio.channels.DatagramChannel", "javax.net.", "android.net.http.", "okhttp3.", "okio.",
      "org.apache.http.", "android.app.DownloadManager"), PERM_NETWORK),
)

#: Packers that sign their output.
_OBF_PACKERS = (
    ("__pyarmor__", "pyarmor"),
    ("pytransform", "pyarmor"),
    ("pyarmor_runtime", "pyarmor"),
    ("PYARMOR", "pyarmor"),
    ("pyminifier", "pyminifier"),
    ("Sourcedefender", "sourcedefender"),
    ("sourcedefender", "sourcedefender"),
)

#: Unpackers: fine by themselves, evidence only next to exec/eval.
_OBF_DECODERS = (
    ("marshal.loads(", "marshal"),
    ("zlib.decompress(", "zlib"),
    ("lzma.decompress(", "lzma"),
    ("bz2.decompress(", "bz2"),
    ("b64decode(", "base64"),
    ("b85decode(", "base85"),
    ("a85decode(", "base85"),
    ("b32decode(", "base32"),
    ("b16decode(", "base16"),
    ("unhexlify(", "unhexlify"),
    ("codecs.decode(", "codecs"),
)

#: A call of exec/eval/compile, not re.compile and not some .exec().
_OBF_EXEC = re.compile(r"(?<![\w.])(?:exec|eval|compile)\s*\(")
#: Names such as _lt6i9dini5txqwg60v06rmg44mp6x5tv: nobody names things so.
_OBF_NAME = re.compile(r"\b_[A-Za-z]{0,2}[0-9a-z]{16,}\b")
_OBF_HEX = re.compile(r"\\x[0-9a-fA-F]{2}")
_OBF_NAME_LIMIT = 8
_OBF_LINE_LIMIT = 2000
_OBF_HEX_LIMIT = 50

_DEX_BLOCK = re.compile(r"^[ \t]*#[ \t]*__DEX_BEGIN__[ \t\r]*$(.*?)^[ \t]*#[ \t]*__DEX_END__[ \t\r]*$",
                        re.M | re.S)
_DEX_LITERAL = re.compile(r"(\"\"\"|'''|\"|')([A-Za-z0-9+/=\s]{512,})\1")
_DEX_LITERAL_PREFIXES = ("ZGV4C", "eJ", "eN", "eA", "eF")
_MAX_DEX_BYTES = 8 * 1024 * 1024
_DEX_CLASS_LIMIT = 6

#: A larger file is not scanned further (MZGramPlugins takes 4 MB at most).
_MAX_SOURCE_BYTES = 4 * 1024 * 1024

_policy = None


def _note(found, key, evidence):
    bucket = found.setdefault(key, [])
    if evidence not in bucket:
        bucket.append(evidence)


def _merge(target, addition):
    for key, evidence in addition.items():
        for item in evidence:
            _note(target, key, item)


def _java_policy():
    global _policy
    if _policy is None:
        try:
            from java import jclass
            _policy = jclass("org.telegram.messenger.mzgram.plugins.MZGramPluginSinkPolicy")
        except Exception:
            _policy = False
    return _policy or None


def class_key(name):
    """The key a Java class name needs (network, hooks, never) or None, as the sink gates see it."""
    if not isinstance(name, str) or not name.strip():
        return None
    clean = name.strip().replace("/", ".")
    policy = _java_policy()
    if policy is not None:
        try:
            return _RULE_KEYS.get(int(policy.classRule(clean)))
        except Exception:
            pass
    while clean.startswith("["):
        clean = clean[1:]
    if clean.startswith("L") and clean.endswith(";"):
        clean = clean[1:-1]
    for prefixes, key in _FALLBACK_RULES:
        for prefix in prefixes:
            if clean == prefix or (prefix.endswith(".") and clean.startswith(prefix)) \
                    or (prefix.endswith("*") and clean.startswith(prefix[:-1])) \
                    or clean.startswith(prefix + "$"):
                return key
    return None


def _note_module(found, name):
    rule = _IMPORTED_MODULES.get(name.partition(".")[0])
    if rule is not None:
        _note(found, rule[0], rule[1])
    if name.startswith(("java.", "javax.", "android.", "dalvik.", "org.", "com.", "de.", "okhttp3", "okio")):
        key = class_key(name + ".")
        if key is not None:
            _note(found, key, name)


def _note_name(found, name, module=None):
    rule = _IMPORTED_NAMES.get(name)
    if rule is not None:
        _note(found, rule[0], rule[1])
    if module:
        full = module + "." + name
        key = class_key(full)
        if key is not None:
            _note(found, key, full)


def _call_name(func):
    """'os.system' for os.system(...), 'jclass' for jclass(...); '' for anything else."""
    parts = []
    while isinstance(func, ast.Attribute):
        parts.append(func.attr)
        func = func.value
    if isinstance(func, ast.Name):
        parts.append(func.id)
        return ".".join(reversed(parts))
    return ""


def _scan_tree(tree):
    found = {}
    os_names = {"os"}
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            module = node.module or ""
            if module:
                _note_module(found, module)
            for alias in node.names:
                _note_name(found, alias.name, module)
                if module == "os" and _PROCESS_CALLS.match(alias.name):
                    _note(found, KEY_NEVER, "os." + alias.name)
        elif isinstance(node, ast.Import):
            for alias in node.names:
                _note_module(found, alias.name)
                _note_name(found, alias.name.rpartition(".")[2])
                if alias.name == "os" and alias.asname:
                    os_names.add(alias.asname)
        elif isinstance(node, ast.Call):
            name = _call_name(node.func)
            head, _, last = name.rpartition(".")
            if head in os_names and _PROCESS_CALLS.match(last):
                _note(found, KEY_NEVER, "os." + last)
            # Java classes named by a literal: jclass("java.net.URL"), find_class(...), Class.forName(...)
            if last in ("jclass", "find_class", "findClass", "forName", "loadClass") and node.args:
                arg = node.args[0]
                if isinstance(arg, ast.Constant) and isinstance(arg.value, str):
                    key = class_key(arg.value)
                    if key is not None:
                        _note(found, key, arg.value.strip())
            if last in ("__import__", "import_module") and node.args:
                arg = node.args[0]
                if isinstance(arg, ast.Constant) and isinstance(arg.value, str):
                    _note_module(found, arg.value)
    return found


def _scan_imports_by_text(source):
    """Python that does not parse: the imports by a regular expression, so the prompt still shows something."""
    found = {}
    for module, names in re.findall(r"^\s*from\s+([A-Za-z0-9_.]+)\s+import\s+([^\n#]+)", source, re.M):
        _note_module(found, module)
        for name in names.replace("(", " ").replace(")", " ").split(","):
            _note_name(found, name.strip().split(" as ")[0].strip(), module)
    for names in re.findall(r"^\s*import\s+([^\n#]+)", source, re.M):
        for name in names.split(","):
            _note_module(found, name.strip().split(" as ")[0].strip())
    return found


def _detect_obfuscation(source):
    """Signs that the code was made hard to read on purpose: the markers above then find nothing."""
    strong = []
    weak = []
    for marker, evidence in _OBF_PACKERS:
        if marker in source and evidence not in strong:
            strong.append(evidence)
    if _OBF_EXEC.search(source):
        for marker, evidence in _OBF_DECODERS:
            if marker in source and "exec+" + evidence not in strong:
                strong.append("exec+" + evidence)
    if len(set(_OBF_NAME.findall(source))) >= _OBF_NAME_LIMIT:
        strong.append("mangled names")
    lines = source.splitlines() or [""]
    if max(len(line) for line in lines) >= _OBF_LINE_LIMIT:
        weak.append("long lines")
    if len(_OBF_HEX.findall(source)) >= _OBF_HEX_LIMIT:
        weak.append("escaped strings")
    if strong:
        return strong + weak
    if len(weak) >= 2:
        return weak
    return []


# ---------- DEX carried in the file ----------

def _uleb128(data, offset):
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, offset
        shift += 7


def _dex_strings(data):
    count, table = struct.unpack_from("<II", data, 0x38)
    strings = []
    for index in range(count):
        offset, = struct.unpack_from("<I", data, table + index * 4)
        _, start = _uleb128(data, offset)
        end = data.index(b"\0", start)
        strings.append(data[start:end].decode("utf-8", errors="replace"))
    return strings


def _dex_symbols(data):
    strings = _dex_strings(data)
    type_count, type_table = struct.unpack_from("<II", data, 0x40)
    types = [strings[struct.unpack_from("<I", data, type_table + i * 4)[0]] for i in range(type_count)]
    method_count, method_table = struct.unpack_from("<II", data, 0x58)
    methods = []
    for i in range(method_count):
        class_index, _, name_index = struct.unpack_from("<HHI", data, method_table + i * 8)
        methods.append((types[class_index], strings[name_index]))
    class_count, class_table = struct.unpack_from("<II", data, 0x60)
    defined = {types[struct.unpack_from("<I", data, class_table + i * 32)[0]] for i in range(class_count)}
    return defined, types, methods


def _dotted(descriptor):
    if descriptor.startswith("L") and descriptor.endswith(";"):
        return descriptor[1:-1].replace("/", ".")
    return descriptor


def _decode_dex_block(body):
    text = "".join(line.strip().lstrip("#").strip() for line in body.splitlines())
    raw = base64.b64decode(text)
    try:
        raw = zlib.decompressobj().decompress(raw, _MAX_DEX_BYTES + 1)
    except zlib.error:
        pass
    if not raw.startswith(b"dex\n") or len(raw) > _MAX_DEX_BYTES:
        return None
    return raw


def _decode_dex_literal(body):
    text = "".join(body.split())
    if not text.startswith(_DEX_LITERAL_PREFIXES) or len(text) > _MAX_DEX_BYTES * 2:
        return None
    try:
        raw = base64.b64decode(text)
    except Exception:
        return None
    if not raw.startswith(b"dex\n"):
        try:
            if not zlib.decompressobj().decompress(raw, 4).startswith(b"dex\n"):
                return None
            raw = zlib.decompressobj().decompress(raw, _MAX_DEX_BYTES + 1)
        except zlib.error:
            return None
    if len(raw) > _MAX_DEX_BYTES:
        return None
    return raw


def _scan_embedded_dex(source):
    payloads = []
    for block in _DEX_BLOCK.finditer(source):
        try:
            payloads.append(_decode_dex_block(block.group(1)))
        except Exception:
            payloads.append(None)
    for literal in _DEX_LITERAL.finditer(source):
        raw = _decode_dex_literal(literal.group(2))
        if raw is not None:
            payloads.append(raw)
    if not payloads:
        return {}
    found = {}
    total = 0
    defined_all = []
    for raw in payloads:
        if raw is None:
            _note(found, KEY_OBFUSCATION, "unreadable DEX block")
            continue
        total += len(raw)
        # Loading compiled code into the app needs "hooks"
        _note(found, PERM_HOOKS, "DEX")
        try:
            defined, types, methods = _dex_symbols(raw)
        except Exception:
            _note(found, KEY_OBFUSCATION, "unreadable DEX block")
            continue
        defined_all.extend(sorted(_dotted(name) for name in defined))
        external = [_dotted(name) for name in types if name not in defined]
        calls = ["%s.%s" % (_dotted(owner), name) for owner, name in methods if owner not in defined]
        text = "\n".join(external + calls)
        for marker, key, evidence in _MARKERS:
            if marker in text:
                _note(found, key, "DEX: " + evidence)
        for name in external:
            key = class_key(name)
            if key is not None:
                _note(found, key, "DEX: " + name)
        if any(call.startswith("java.lang.Runtime.exec") for call in calls):
            _note(found, KEY_NEVER, "DEX: Runtime.exec")
    if total:
        summary = ["size:%d" % total]
        for name in defined_all[:_DEX_CLASS_LIMIT]:
            summary.append("class:" + name)
        if len(defined_all) > _DEX_CLASS_LIMIT:
            summary.append("more:%d" % (len(defined_all) - _DEX_CLASS_LIMIT))
        found[KEY_DEX] = summary
    return found


# ---------- the scan ----------

def scan_source(source):
    """{key: [evidence]}: what the code may do (permission keys), plus "never", "obfuscation", "dex"."""
    found = {}
    for marker, key, evidence in _MARKERS:
        if marker in source:
            _note(found, key, evidence)
    try:
        tree = ast.parse(source)
    except Exception:
        tree = None
    _merge(found, _scan_tree(tree) if tree is not None else _scan_imports_by_text(source))
    obfuscation = _detect_obfuscation(source)
    if obfuscation:
        found[KEY_OBFUSCATION] = obfuscation
    _merge(found, _scan_embedded_dex(source))
    return {key: found[key] for key in KEYS if found.get(key)}


def _is_bytecode(path, raw):
    if path.endswith(".pyc"):
        return True
    if raw[2:4] != b"\r\n":
        return False
    try:
        raw.decode("utf-8")
    except UnicodeDecodeError:
        return True
    return False


def scan(path):
    """The scan of the .plugin file at path, without running it."""
    with open(path, "rb") as handle:
        raw = handle.read(_MAX_SOURCE_BYTES + 1)
    truncated = len(raw) > _MAX_SOURCE_BYTES
    raw = raw[:_MAX_SOURCE_BYTES]
    if _is_bytecode(path, raw):
        return {KEY_OBFUSCATION: ["compiled bytecode"]}
    if raw.startswith(b"\xef\xbb\xbf"):
        raw = raw[3:]
    found = scan_source(raw.decode("utf-8", errors="replace"))
    if truncated:
        found.setdefault(KEY_OBFUSCATION, []).append("truncated source")
    return found


def scan_json(path):
    """For Java (MZGramPluginCapabilityScan): always a JSON string,
    {"ok": true, "found": {key: [evidence]}} or {"ok": false, "error": "..."}."""
    try:
        return json.dumps({"ok": True, "found": scan(path)}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": "%s: %s" % (type(e).__name__, e)}, ensure_ascii=False)
