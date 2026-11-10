# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# The sink gates of plugins, Python side: the checks where the harm is done,
# not on the names a plugin imports. A module the app has loaded already is
# one line away from any plugin (sys.modules["socket"]), so the gate sits on
# the actions themselves: an audit hook (PEP 578) sees socket.connect,
# subprocess.Popen, open() and ctypes in the C code of CPython, however the
# plugin got the module.
#
#   install()          once, before the first plugin starts: the audit hook,
#                      the checks of jclass and of imports from Java, threads
#                      that keep their plugin, and the Java gates
#                      (MZGramPluginSinkGate, when Aliuhook is there);
#   register(id, mod)  the module of a plugin, so that its frames are known
#                      (they stay its code after it stops: a callback left
#                      behind is still the plugin's);
#   owner()            the plugin whose code runs here: its frames on the
#                      stack (the one with fewer permissions when there are
#                      several), else the mark of permissions.plugin_context;
#   frame_owner()      the same for MZGramPluginSinkGate (Java callbacks
#                      into Python have no mark);
#   data_dir(id)       the plugin's own folder, free to it.
#
# What a plugin needs (the rules and the record are in
# MZGramPluginSinkPolicy on the Java side):
#   never      other processes (os.system, os.exec*, os.spawn*, os.fork,
#              subprocess), its own audit hooks, tracing other code;
#   network    sockets, name lookups, urllib, ftplib, smtplib and the rest;
#              without Java (tests on a computer) the network stays closed;
#   native     ctypes (but not the libraries of the Python runtime itself);
#   hooks      the private storage of the app (settings, databases, the
#              session) and writing to the runtime or the plugin files;
#   files      other files; its own folder, its own .plugin file and the
#              Python runtime (reading) are free.
# Java classes by name (jclass, "from java.net import URL") follow
# MZGramPluginSinkPolicy.classRule.
#
# A refusal raises PermissionError (an OSError, which network code expects
# from a failed connection), so the app never breaks; the activity log and
# a notice to the user tell what happened.
#
# The honest limit: everything runs in one process and one interpreter.
# Code written to get around the gates (reading frames, patching this
# module) can; they stop what ordinary plugin code does and show it.
#
# Based on exteraless extera_utils/audit_gate.py (GPL-3.0): the audit hook
# with the events, their permissions, the runtime paths, the sealed
# folders of the app and the attribution by frames; written anew for
# MZGram.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import builtins
import json
import os
import sys
import tempfile
import threading

from mzgram_plugins import permissions

POLICY_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramPluginSinkPolicy"
GATE_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramPluginSinkGate"

# Marks a refusal of this gate, so that the hook never swallows it
_DENIAL = "_mzgram_denial"

# Never, for any plugin
_NEVER = {
    "os.system": "run a shell command",
    "os.exec": "replace the process",
    "os.spawn": "start a process",
    "os.posix_spawn": "start a process",
    "os.fork": "fork the process",
    "os.forkpty": "fork the process",
    "os.startfile": "start a process",
    "os.kill": "send a signal",
    "os.killpg": "send a signal",
    "subprocess.Popen": "start a process",
    "sys.addaudithook": "add an audit hook",
    "sys.settrace": "trace other code",
    "sys.setprofile": "trace other code",
    "sys._current_frames": "read other threads",
}

_NATIVE = {
    "ctypes.dlopen": "load a native library",
    "ctypes.dlsym": "find a native symbol",
    "ctypes.dlsym/handle": "find a native symbol",
    "ctypes.call_function": "call native code",
    "ctypes.cdata": "read raw memory",
    "ctypes.cdata/buffer": "read raw memory",
    "ctypes.string_at": "read raw memory",
    "ctypes.wstring_at": "read raw memory",
    "ctypes.addressof": "take a memory address",
    "ctypes.create_string_buffer": "allocate raw memory",
    "ctypes.create_unicode_buffer": "allocate raw memory",
    "ctypes.set_exception": "change native errors",
    "ctypes.get_errno": "read native errors",
    "ctypes.set_errno": "change native errors",
}

# Name lookups only check: the connection right after them is what the log shows
_LOOKUPS = {
    "socket.getaddrinfo": "look up a host",
    "socket.gethostbyname": "look up a host",
    "socket.gethostbyaddr": "look up a host",
    "socket.getnameinfo": "look up a host",
}

_NETWORK = {
    "socket.connect": "connect",
    "socket.bind": "listen",
    "socket.sendto": "send",
    "socket.sendmsg": "send",
    "socket.sethostname": "change the host name",
    "urllib.Request": "request",
    "ftplib.connect": "connect",
    "imaplib.open": "connect",
    "poplib.connect": "connect",
    "smtplib.connect": "connect",
    "nntplib.connect": "connect",
    "telnetlib.Telnet.open": "connect",
    "http.client.connect": "connect",
    "webbrowser.open": "open a page",
}

# event -> ((argument index, writes), ...)
_FILES = {
    "open": None,  # writes or not by its mode and flags
    "os.listdir": ((0, False),),
    "os.scandir": ((0, False),),
    "glob.glob": ((0, False),),
    "pathlib.Path.glob": ((0, False),),
    "os.remove": ((0, True),),
    "os.rename": ((0, True), (1, True)),
    "os.rmdir": ((0, True),),
    "os.mkdir": ((0, True),),
    "os.chmod": ((0, True),),
    "os.chown": ((0, True),),
    "os.truncate": ((0, True),),
    "os.link": ((0, False), (1, True)),
    "os.symlink": ((0, False), (1, True)),
    "os.utime": ((0, True),),
    "shutil.copyfile": ((0, False), (1, True)),
    "shutil.copymode": ((0, False), (1, True)),
    "shutil.copystat": ((0, False), (1, True)),
    "shutil.copytree": ((0, False), (1, True)),
    "shutil.move": ((0, True), (1, True)),
    "shutil.rmtree": ((0, True),),
    "shutil.make_archive": ((0, True),),
    "shutil.unpack_archive": ((0, False), (1, True)),
    "tempfile.mkstemp": ((0, True),),
    "tempfile.mkdtemp": ((0, True),),
    "sqlite3.connect": ((0, True),),
}

_WATCHED = frozenset(set(_NEVER) | set(_NATIVE) | set(_LOOKUPS) | set(_NETWORK) | set(_FILES))

_WRITE_FLAGS = os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND

# Read-only system folders a plugin may read (time zones, fonts, certificates)
_SYSTEM_DIRS = ("/system", "/apex", "/vendor", "/product", "/odm", "/system_ext", "/etc")
_FREE_DEVICES = ("/dev/null", "/dev/zero", "/dev/random", "/dev/urandom")

_JAVA_ROOTS = frozenset(("java", "javax", "android", "androidx", "dalvik", "org", "com", "de",
                         "kotlin", "kotlinx", "sun", "jdk", "libcore", "okhttp3", "okio"))

_local = threading.local()
_lock = threading.Lock()
_installed = False

# id(module dict) -> (plugin id, the dict); the dict is kept so its id is not reused
_owners = {}
# plugin id -> the real path of its .plugin file
_own_files = {}

_UNSET = object()
_policy = _UNSET
_paths = None
_runtime = None
_importer_globals = None


# ---------- Java ----------

def _java_policy():
    global _policy
    if _policy is _UNSET:
        try:
            from java import jclass
            _policy = jclass(POLICY_CLASS)
        except Exception:
            _policy = None
    return _policy


def _set_java(policy):
    """Tests: a stand-in for MZGramPluginSinkPolicy, or None for no Java."""
    global _policy, _paths
    _policy = policy
    _paths = None


def _allow(plugin_id, key, what):
    # permissions.check: without Java every check passes
    return permissions.check(key, what, plugin_id)


def _allow_network(plugin_id, what, target):
    policy = _java_policy()
    if policy is None:
        # Without Java the network stays closed, as before the gates
        return False
    try:
        return bool(policy.allowNetwork(plugin_id, what, target or ""))
    except Exception:
        return False


def _blocked(plugin_id, what):
    policy = _java_policy()
    if policy is not None:
        try:
            policy.blocked(plugin_id, what)
        except Exception:
            pass


def _allow_class(plugin_id, name):
    policy = _java_policy()
    if policy is None:
        return True
    try:
        return bool(policy.allowClass(plugin_id, name))
    except Exception:
        return False


# ---------- whose code ----------

def register(plugin_id, module):
    """The frames of this module are the code of plugin_id, for good: the
    module of an earlier start stays known too."""
    namespace = vars(module)
    path = getattr(module, "__file__", None)
    with _lock:
        _owners[id(namespace)] = (plugin_id, namespace)
        if isinstance(path, str):
            _own_files[plugin_id] = _real(path)


def unregister(plugin_id):
    """The plugin stopped: its file is no longer read as its own. Its
    frames stay its code."""
    with _lock:
        _own_files.pop(plugin_id, None)


def _frame_plugins():
    found = []
    try:
        frame = sys._getframe(1)
    except Exception:
        return found
    owners = _owners
    depth = 0
    while frame is not None and depth < 400:
        entry = owners.get(id(frame.f_globals))
        if entry is not None and entry[1] is frame.f_globals and entry[0] not in found:
            found.append(entry[0])
        frame = frame.f_back
        depth += 1
    return found


def _poorest(candidates):
    """The plugin with fewer permissions, so that mixing code never adds any."""
    if len(candidates) == 1 or permissions._java() is None:
        return candidates[0]
    best, best_count = None, None
    for pid in candidates:
        count = sum(1 for key in permissions.ALL if permissions.has(key, pid))
        if best_count is None or count < best_count:
            best, best_count = pid, count
    return best


def owner():
    """The plugin whose code runs on this thread, or None (MZGram's own code)."""
    candidates = _frame_plugins()
    marked = getattr(permissions._state, "plugin_id", None)
    if marked is not None and marked not in candidates:
        candidates.append(marked)
    if not candidates:
        return None
    return _poorest(candidates)


def frame_owner():
    """owner() for MZGramPluginSinkGate: a Java callback into Python has no mark."""
    busy = getattr(_local, "busy", False)
    _local.busy = True
    try:
        return owner()
    except Exception:
        return None
    finally:
        _local.busy = busy


# ---------- folders ----------

def _real(path):
    try:
        return os.path.realpath(path)
    except Exception:
        return path


def _within(target, root):
    return bool(root) and (target == root or target.startswith(root.rstrip(os.sep) + os.sep))


def _folders():
    global _paths
    if _paths is not None:
        return _paths
    data = {}
    policy = _java_policy()
    if policy is not None:
        try:
            data = json.loads(str(policy.paths()))
        except Exception:
            data = {}
    private = tuple(_real(p) for p in data.get("private", ()) if isinstance(p, str) and p)
    plugins = _real(data["plugins"]) if isinstance(data.get("plugins"), str) else None
    plugin_data = data.get("plugin_data")
    if not isinstance(plugin_data, str) or not plugin_data:
        # Without Java: a folder of the computer's temp folder
        plugin_data = os.path.join(tempfile.gettempdir(), "mzgram_plugin_data")
    try:
        temp = _real(tempfile.gettempdir())
    except Exception:
        temp = None
    _paths = {"private": private, "plugins": plugins, "plugin_data": _real(plugin_data), "temp": temp}
    return _paths


def _runtime_dirs():
    """The Python runtime: the standard library, the bundled packages, the
    files Chaquopy extracts. Reading there is loading a module."""
    global _runtime
    if _runtime is not None:
        return _runtime
    roots = set()
    try:
        for path in (sys.prefix, sys.base_prefix, os.path.dirname(os.__file__), os.path.dirname(json.__file__)):
            if path:
                roots.add(_real(path))
        for entry in list(sys.path):
            if isinstance(entry, str) and os.path.isabs(entry):
                roots.add(_real(entry))
        marker = os.sep + "chaquopy" + os.sep
        for entry in list(roots):
            at = entry.find(marker)
            if at > 0:
                roots.add(entry[:at + len(marker) - 1])
    except Exception:
        pass
    plugins = _folders()["plugins"]
    # The plugin files are not runtime, even when a path entry lies above them
    _runtime = tuple(sorted(r for r in roots if r and r != os.sep and not (plugins and _within(plugins, r) and r != plugins)))
    return _runtime


def data_dir(plugin_id, create=True):
    """The plugin's own folder: free to it, deleted with it."""
    root = _folders()["plugin_data"]
    path = os.path.join(root, plugin_id)
    if create:
        busy = getattr(_local, "busy", False)
        _local.busy = True
        try:
            os.makedirs(path, exist_ok=True)
        except Exception:
            pass
        finally:
            _local.busy = busy
    return path


def _extracting():
    """Chaquopy extracts a module of the runtime (it writes into its own folder)."""
    target = _importer_globals
    if target is None:
        return False
    try:
        frame = sys._getframe(1)
    except Exception:
        return False
    while frame is not None:
        if frame.f_globals is target:
            return True
        frame = frame.f_back
    return False


def file_key(plugin_id, path, writes):
    """None when the plugin may use the path freely, else the permission it needs."""
    target = _real(path)
    folders = _folders()
    if _within(target, os.path.join(folders["plugin_data"], plugin_id)):
        return None
    if not writes and target == _own_files.get(plugin_id):
        return None
    plugins = folders["plugins"]
    if plugins and _within(target, plugins):
        return permissions.HOOKS
    for root in _runtime_dirs():
        if _within(target, root):
            if not writes or _extracting():
                return None
            return permissions.HOOKS
    if target in _FREE_DEVICES:
        return None
    if target.startswith("/proc/") and target.rsplit("/", 1)[-1] in ("mem", "pagemap", "environ", "maps", "smaps"):
        return permissions.HOOKS
    temp = folders["temp"]
    if temp and _within(target, temp):
        return permissions.FILES
    for root in folders["private"]:
        if _within(target, root):
            return permissions.HOOKS
    if not writes and any(_within(target, root) for root in _SYSTEM_DIRS):
        return None
    if writes and any(_within(target, root) for root in _SYSTEM_DIRS):
        return permissions.HOOKS
    return permissions.FILES


# ---------- the audit hook ----------

def _denial(error):
    setattr(error, _DENIAL, True)
    return error


def _text(value):
    if value is None:
        return None
    try:
        if isinstance(value, (str, bytes, os.PathLike)):
            return os.fsdecode(value)
    except Exception:
        return None
    return None


def _arg(args, index):
    try:
        return args[index]
    except Exception:
        return None


def _address(event, args):
    if event in ("socket.connect", "socket.bind", "socket.sendto", "socket.sendmsg"):
        address = _arg(args, 1)
        if isinstance(address, tuple) and len(address) >= 2:
            return "%s:%s" % (address[0], address[1])
        return _text(address) or ""
    if event == "urllib.Request":
        url = _text(_arg(args, 0)) or ""
        policy = _java_policy()
        if policy is not None:
            try:
                return str(policy.hostOf(url))
            except Exception:
                pass
        rest = url.split("://", 1)[-1]
        return rest.split("/", 1)[0].split("?", 1)[0].rsplit("@", 1)[-1].lower()
    if event in ("http.client.connect", "ftplib.connect", "smtplib.connect", "imaplib.open",
                 "poplib.connect", "nntplib.connect", "telnetlib.Telnet.open"):
        host, port = _arg(args, 1), _arg(args, 2)
        if host is not None:
            return "%s:%s" % (host, port) if port else str(host)
    return ""


def _network_refused(plugin_id, what):
    return _denial(PermissionError(
        13, "MZGram: the plugin %r may not %s without the permission 'network'" % (plugin_id, what)))


def _file_targets(event, args):
    if event == "open":
        path = _text(_arg(args, 0))
        if path is None:
            return ()
        mode, flags = _arg(args, 1), _arg(args, 2)
        writes = (isinstance(mode, str) and any(c in mode for c in "wax+")) \
            or (type(flags) is int and bool(flags & _WRITE_FLAGS))
        return ((path, writes),)
    out = []
    for index, writes in _FILES.get(event) or ():
        path = _text(_arg(args, index))
        if path is None and index == 0 and event in ("os.listdir", "os.scandir"):
            path = "."
        if path is not None:
            out.append((path, writes))
    return out


def _check(event, args):
    plugin_id = owner()
    if plugin_id is None:
        return

    what = _NEVER.get(event)
    if what is not None:
        _blocked(plugin_id, what)
        raise _denial(PermissionError(13, "MZGram: plugins may never %s (%s)" % (what, event)))

    what = _NATIVE.get(event)
    if what is not None:
        library = _text(_arg(args, 0))
        if library and any(_within(_real(library), root) for root in _runtime_dirs()):
            return  # the runtime loads a library of its own module
        if not _allow(plugin_id, permissions.NATIVE, what):
            raise _denial(PermissionError(13, "MZGram: the plugin may not %s without the permission 'native'" % what))
        return

    what = _LOOKUPS.get(event)
    if what is not None:
        if permissions._java() is None:
            raise _network_refused(plugin_id, what)
        if not permissions.has(permissions.NETWORK, plugin_id):
            permissions.check(permissions.NETWORK, what, plugin_id)
            raise _network_refused(plugin_id, what)
        return

    what = _NETWORK.get(event)
    if what is not None:
        if not _allow_network(plugin_id, what, _address(event, args)):
            raise _network_refused(plugin_id, what)
        return

    if event in _FILES:
        if event == "sqlite3.connect":
            name = _text(_arg(args, 0))
            if not name or name == ":memory:" or name.startswith("file::memory:"):
                return
        for path, writes in _file_targets(event, args):
            key = file_key(plugin_id, path, writes)
            if key is None:
                continue
            verb = ("change " if writes else "read ") + ("app data" if key == permissions.HOOKS else "files")
            if not _allow(plugin_id, key, verb):
                raise _denial(PermissionError(
                    13, "MZGram: the plugin may not %s here without the permission %r" % (verb, key)))


def _make_hook(watched, check):
    # Bound now: replacing the globals of this module later changes nothing
    local = _local

    def hook(event, args):
        if event not in watched or getattr(local, "busy", False):
            return
        local.busy = True
        try:
            check(event, args)
        except BaseException as e:
            if getattr(e, _DENIAL, False):
                raise
            # A broken check never breaks the app
        finally:
            local.busy = False
    return hook


# ---------- threads ----------

def _patch_threads():
    import _thread
    thread_class = threading.Thread
    start = thread_class.start
    bootstrap = thread_class._bootstrap_inner

    def gated_start(self, *args, **kwargs):
        try:
            self._mzgram_plugin = owner()
        except Exception:
            self._mzgram_plugin = None
        return start(self, *args, **kwargs)

    def gated_bootstrap(self):
        plugin_id = getattr(self, "_mzgram_plugin", None)
        if plugin_id is None:
            return bootstrap(self)
        with permissions.plugin_context(plugin_id):
            return bootstrap(self)

    thread_class.start = gated_start
    thread_class._bootstrap_inner = gated_bootstrap

    new_thread = _thread.start_new_thread

    def start_new_thread(function, args, kwargs=None):
        plugin_id = owner()
        if plugin_id is not None:
            inner = function

            def function(*a, **k):
                with permissions.plugin_context(plugin_id):
                    return inner(*a, **k)
        if kwargs is None:
            return new_thread(function, args)
        return new_thread(function, args, kwargs)

    _thread.start_new_thread = start_new_thread
    if hasattr(_thread, "start_new"):
        _thread.start_new = start_new_thread


# ---------- Java classes ----------

def _class_refused(name):
    return ImportError("MZGram: the Java class %s is not available to this plugin" % name)


def _patch_java():
    try:
        import java
    except Exception:
        return False
    modules = [java, sys.modules.get("java.chaquopy")]
    for module in modules:
        original = getattr(module, "jclass", None) if module is not None else None
        if original is None or getattr(original, "_mzgram_gated", False):
            continue

        def gated_jclass(name, *args, _original=original, **kwargs):
            if isinstance(name, str):
                plugin_id = owner()
                if plugin_id is not None and not _allow_class(plugin_id, name):
                    raise _class_refused(name)
            return _original(name, *args, **kwargs)

        gated_jclass._mzgram_gated = True
        try:
            module.jclass = gated_jclass
        except Exception:
            pass
    _patch_import()
    return True


def _patch_import():
    original = builtins.__import__
    if getattr(original, "_mzgram_gated", False):
        return

    def gated_import(name, globals=None, locals=None, fromlist=(), level=0):
        if level == 0 and fromlist and isinstance(name, str) and name.split(".", 1)[0] in _JAVA_ROOTS:
            plugin_id = owner()
            if plugin_id is not None:
                for item in fromlist:
                    if isinstance(item, str) and item != "*" and item[:1].isupper():
                        if not _allow_class(plugin_id, name + "." + item):
                            raise _class_refused(name + "." + item)
        return original(name, globals, locals, fromlist, level)

    gated_import._mzgram_gated = True
    builtins.__import__ = gated_import


# ---------- install ----------

def _warm():
    """The Java classes of the SDK are taken now, outside plugin code: the
    gates refuse the classes of the engine to plugins."""
    try:
        permissions._java()
    except Exception:
        pass
    try:
        from mzgram_plugins import hooks, menus, settings
        hooks._java()
        menus._backend()
        settings._backend()
    except Exception:
        pass
    try:
        from mzgram_plugins import loader
        loader._java_engine()
    except Exception:
        pass
    _java_policy()
    _folders()
    _runtime_dirs()


def install():
    """Puts the gates on once; True when the audit hook stands."""
    global _installed, _importer_globals
    with _lock:
        if _installed:
            return True
        _installed = True
    # Bytecode of plugins would be written next to the plugin files
    sys.dont_write_bytecode = True
    _warm()
    importer = sys.modules.get("java.android.importer")
    _importer_globals = vars(importer) if importer is not None else None
    try:
        sys.addaudithook(_make_hook(_WATCHED, _check))
        hooked = True
    except Exception as e:
        print("[mzgram:sink_gate] the audit hook failed: %s" % e, file=sys.stderr)
        hooked = False
    try:
        _patch_threads()
    except Exception as e:
        print("[mzgram:sink_gate] threads: %s" % e, file=sys.stderr)
    _patch_java()
    gate = None
    try:
        from java import jclass
        gate = jclass(GATE_CLASS)
    except Exception:
        gate = None
    if gate is not None:
        try:
            gate.install()
        except Exception as e:
            print("[mzgram:sink_gate] the Java gates failed: %s" % e, file=sys.stderr)
    return hooked


def installed():
    return _installed
