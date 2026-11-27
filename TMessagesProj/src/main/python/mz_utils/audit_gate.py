
import os
import sys
import threading
import time
from collections import deque
from typing import Optional

__all__ = ["install", "get_journal", "get_profile", "clear_plugin"]

_loader = None

_local = threading.local()



_DENY_ALWAYS = {
    "os.system": "run a shell command",
    "os.exec": "replace the process image",
    "os.spawn": "spawn a process",
    "os.posix_spawn": "spawn a process",
    "os.startfile": "spawn a process",
    "os.fork": "fork the process",
    "os.forkpty": "fork the process",
    "subprocess.Popen": "start a subprocess",
    "sys.addaudithook": "install an audit hook",
    "sys.settrace": "trace other code",
    "sys.setprofile": "profile other code",
    "sys._current_frames": "read other threads' stacks",
}

#
#
_NATIVE = {
    "ctypes.dlopen": "load a native library",
    "ctypes.dlsym": "resolve a native symbol",
    "ctypes.dlsym/handle": "resolve a native symbol",
    "ctypes.dlclose": "unload a native library",
    "ctypes.call_function": "call a native function",
    "ctypes.cdata": "cast a raw memory address",
    "ctypes.cdata/buffer": "wrap raw memory",
    "ctypes.string_at": "read raw memory",
    "ctypes.wstring_at": "read raw memory",
    "ctypes.set_exception": "hijack native error handling",
    "ctypes.addressof": "take the address of an object",
    "ctypes.create_string_buffer": "allocate a raw buffer",
    "ctypes.create_unicode_buffer": "allocate a raw buffer",
    "ctypes.get_errno": "read the native error code",
    "ctypes.set_errno": "set the native error code",
    "ctypes.get_last_error": "read the native error code",
    "ctypes.set_last_error": "set the native error code",
}

_NETWORK = {
    "socket.connect": "connect to the network",
    "socket.bind": "listen on a socket",
    "socket.sendto": "send network data",
    "socket.getaddrinfo": "resolve a host name",
    "socket.gethostbyname": "resolve a host name",
    "socket.gethostbyaddr": "resolve a host name",
    "socket.sethostname": "change the host name",
    "urllib.Request": "make an HTTP request",
    "ftplib.connect": "connect to the network",
    "imaplib.open": "connect to the network",
    "poplib.connect": "connect to the network",
    "smtplib.connect": "connect to the network",
    "nntplib.connect": "connect to the network",
    "telnetlib.Telnet.open": "connect to the network",
}

_DATABASE = {
    "sqlite3.connect": "open a database",
    "sqlite3.connect/handle": "open a database",
}

_FILES = {
    "open": 0,
    "os.remove": 0,
    "os.rename": (0, 1),
    "os.rmdir": 0,
    "os.mkdir": 0,
    "os.chdir": 0,
    "os.chmod": 0,
    "os.chown": 0,
    "os.truncate": 0,
    "os.link": (0, 1),
    "os.symlink": (0, 1),
    "os.utime": 0,
    "os.listdir": 0,
    "os.scandir": 0,
    "glob.glob": 0,
    "pathlib.Path.glob": 1,
    "shutil.copyfile": (0, 1),
    "shutil.copymode": 0,
    "shutil.copystat": 0,
    "shutil.copytree": (0, 1),
    "shutil.move": (0, 1),
    "shutil.rmtree": 0,
    "shutil.unpack_archive": 0,
    "tempfile.mkstemp": 0,
}

_FRAME_ATTRS = frozenset({"f_globals", "f_builtins", "f_locals"})

_JOURNAL_ONLY = frozenset({
    "exec", "compile", "import", "marshal.loads", "pickle.find_class",
    "code.__new__", "builtins.input",
})

_WATCHED = frozenset(set(_DENY_ALWAYS) | set(_NATIVE) | set(_NETWORK) | set(_FILES)
                     | set(_DATABASE) | set(_JOURNAL_ONLY))

_CATEGORY = {}
for _e in _DENY_ALWAYS:
    _CATEGORY[_e] = "process" if _e.startswith(("os.", "subprocess")) else "introspection"
for _e in _NATIVE:
    _CATEGORY[_e] = "native"
for _e in _NETWORK:
    _CATEGORY[_e] = "network"
for _e in _FILES:
    _CATEGORY[_e] = "files"
for _e in _DATABASE:
    _CATEGORY[_e] = "database"
for _e in _JOURNAL_ONLY:
    _CATEGORY[_e] = "code" if _e in ("exec", "compile", "marshal.loads",
                                     "code.__new__", "pickle.find_class") else "misc"
_CATEGORY["import"] = "imports"
_CATEGORY["object.__getattr__"] = "introspection"
_CATEGORY["java class"] = "reflection"



_JOURNAL_LIMIT = 400
_journal = deque(maxlen=_JOURNAL_LIMIT)
_profile = {}
_journal_lock = threading.Lock()


_LOOKBACK = 8


def _recent(count: int):
    total = len(_journal)
    return [_journal[i] for i in range(total - 1, max(-1, total - count - 1), -1)]


def _relation(candidate: dict, entry: dict) -> Optional[str]:
    if candidate["plugin"] != entry["plugin"] or candidate["event"] != entry["event"]:
        return None
    if entry["event"] != "import" or candidate["allowed"] != entry["allowed"]:
        return None
    old_detail, new_detail = candidate["detail"], entry["detail"]
    if not old_detail or not new_detail:
        return None
    if new_detail.startswith(old_detail + "."):
        return "child"
    if old_detail.startswith(new_detail + "."):
        return "parent"
    return None


def _repeats(previous: dict, entry: dict) -> bool:
    return (previous["plugin"] == entry["plugin"]
            and previous["event"] == entry["event"]
            and previous["detail"] == entry["detail"]
            and previous["allowed"] == entry["allowed"])


def _record(plugin_id: str, event: str, detail: Optional[str], allowed: bool) -> None:
    category = _CATEGORY.get(event, "misc")
    entry = {
        "ts": int(time.time() * 1000),
        "plugin": plugin_id,
        "event": event,
        "category": category,
        "detail": (detail or "")[:200],
        "allowed": allowed,
        "count": 1,
    }
    with _journal_lock:
        for candidate in _recent(_LOOKBACK):
            relation = _relation(candidate, entry)
            if relation == "child":
                candidate["detail"] = entry["detail"]
                candidate["ts"] = entry["ts"]
                return
            if relation == "parent":
                candidate["ts"] = entry["ts"]
                return
        previous = _journal[-1] if _journal else None
        if previous is not None and _repeats(previous, entry):
            previous["count"] = previous.get("count", 1) + 1
            previous["ts"] = entry["ts"]
        else:
            _journal.append(entry)
        per_plugin = _profile.setdefault(plugin_id, {})
        bucket = per_plugin.setdefault(category, {"allowed": 0, "denied": 0})
        bucket["allowed" if allowed else "denied"] += 1


def get_journal(plugin_id: Optional[str] = None, limit: int = 100):
    with _journal_lock:
        items = [e for e in _journal if plugin_id is None or e["plugin"] == plugin_id]
    return items[-limit:]


def get_profile(plugin_id: Optional[str] = None):
    with _journal_lock:
        if plugin_id is None:
            return {pid: dict(cats) for pid, cats in _profile.items()}
        return dict(_profile.get(plugin_id, {}))


def note_denied_class(plugin_id: str, name: str, permission: str) -> None:
    _record(plugin_id, "java class", name, False)


def clear_plugin(plugin_id: str) -> None:
    with _journal_lock:
        _profile.pop(plugin_id, None)
        kept = [e for e in _journal if e["plugin"] != plugin_id]
        _journal.clear()
        _journal.extend(kept)



def _arg(args, index):
    try:
        return args[index]
    except Exception:
        return None


def _as_text(value) -> Optional[str]:
    if value is None:
        return None
    try:
        if isinstance(value, (str, bytes, os.PathLike)):
            return os.fsdecode(value)
        return str(value)
    except Exception:
        return None


_runtime_prefixes = None


def _runtime_paths():
    global _runtime_prefixes
    if _runtime_prefixes is not None:
        return _runtime_prefixes
    prefixes = set()
    try:
        for path in (sys.prefix, sys.base_prefix, os.path.dirname(os.__file__)):
            if path:
                prefixes.add(os.path.realpath(path))
        for entry in list(sys.path):
            if entry and os.path.isabs(entry):
                prefixes.add(os.path.realpath(entry))
        for entry in list(prefixes):
            marker = os.sep + "chaquopy" + os.sep
            index = entry.find(marker)
            if index > 0:
                prefixes.add(entry[:index + len(marker) - 1])
    except Exception:
        pass
    _runtime_prefixes = tuple(sorted(prefixes))
    return _runtime_prefixes


def _is_runtime_file(path: Optional[str]) -> bool:
    if not path:
        return False
    try:
        target = os.path.realpath(path)
    except Exception:
        target = path
    if is_sensitive_app_path(target):
        return False
    for prefix in _runtime_paths():
        if target == prefix or target.startswith(prefix + os.sep):
            return True
    return False


_SENSITIVE_NAMES = ("tgnet.dat", "cache4.db", "userconfing", "userconfig", "accounts_map.json",
                    "passcode", "file_to_path.db")
_SENSITIVE_DIRS = ("/shared_prefs/", "/databases/", "/plugins/.data/")


def is_sensitive_app_path(path: Optional[str]) -> bool:
    if not path:
        return False
    text = str(path)
    name = os.path.basename(text)
    if any(name.startswith(prefix) for prefix in _SENSITIVE_NAMES):
        return True
    return any(part in text for part in _SENSITIVE_DIRS)


_SESSION_NAMES = ("tgnet.dat", "userconfing", "userconfig", "accounts_map.json", "passcode")


def is_session_credential_path(path: Optional[str]) -> bool:
    if not path:
        return False
    text = str(path)
    try:
        text = os.path.realpath(text)
    except Exception:
        pass
    name = os.path.basename(text)
    if any(name.startswith(prefix) for prefix in _SESSION_NAMES):
        return True
    return "/shared_prefs/" in text


def _is_own_file(plugin_id: str, path: Optional[str]) -> bool:
    if not path:
        return False
    try:
        from file_utils import _is_own_path
        return bool(_is_own_path(plugin_id, path))
    except Exception:
        return False


def _hook(event, args, _watched=None, _loader_ref=None):
    if _watched is None:
        _watched = _WATCHED
    if event not in _watched:
        return
    if _loader_ref is None:
        _loader_ref = _loader
    if getattr(_local, "busy", False):
        return
    _local.busy = True
    try:
        _check(event, args, _loader_ref)
    except BaseException as e:
        if getattr(e, _DENIAL_MARK, False):
            raise
        if isinstance(e, PermissionError):
            raise
    finally:
        _local.busy = False


_DENIAL_MARK = "_mzgram_denial"


def _denial(error):
    setattr(error, _DENIAL_MARK, True)
    return error


def _network_detail(event, args) -> Optional[str]:
    if event in ("socket.connect", "socket.bind", "socket.sendto"):
        address = _arg(args, 1)
        if isinstance(address, tuple) and len(address) >= 2:
            return "%s:%s" % (address[0], address[1])
        return _as_text(address)
    if event == "urllib.Request":
        return _as_text(_arg(args, 0))
    return _as_text(_arg(args, 0))


def _deny_network(plugin_id: str, event: str, what: str, detail: Optional[str]):
    _loader.log_denial(plugin_id, event, what)
    message = ("plugin %r is not allowed to reach the network: grant it the "
               "'network' permission on the plugin's screen" % plugin_id)
    if event in ("socket.getaddrinfo", "socket.gethostbyname", "socket.gethostbyaddr"):
        socket_module = sys.modules.get("socket")
        error = getattr(socket_module, "gaierror", None) if socket_module else None
        if error is not None:
            raise _denial(error(-2, message))
    raise _denial(ConnectionRefusedError(message))


def _adopt_executed_code(args, plugin_id, loader):
    path = getattr(_arg(args, 0), "co_filename", None)
    if not isinstance(path, str) or not path or path.startswith("<"):
        return
    if _is_runtime_file(path):
        return
    try:
        if loader._resolve_owner(path) is None and os.path.isfile(path):
            loader._register_owner(path, plugin_id)
    except Exception:
        pass


def _foreign_code_owner(event, args, plugin_id, loader):
    if loader is None:
        return None
    try:
        if event == "compile":
            path = _arg(args, 1)
        else:
            path = getattr(_arg(args, 0), "co_filename", None)
        if not isinstance(path, str) or not path or path.startswith("<"):
            return None
        owner = loader._resolve_owner(path)
    except Exception:
        return None
    return owner if owner is not None and owner != plugin_id else None


def _check(event, args, loader):
    if event == "object.__getattr__":
        name = _arg(args, 1)
        if name not in _FRAME_ATTRS:
            return

    plugin_id = loader.plugin_frame_owner() if loader is not None else None
    if plugin_id is None:
        return

    if loader.unsafe_mode():
        return

    if event == "exec":
        _adopt_executed_code(args, plugin_id, loader)

    if event in ("compile", "exec"):
        victim = _foreign_code_owner(event, args, plugin_id, loader)
        if victim is not None:
            _record(plugin_id, event, victim, False)
            raise _denial(PermissionError(
                f"plugin {plugin_id!r} cannot run code labelled as {victim!r}: "
                f"co_filename of another plugin"))

    if event in _JOURNAL_ONLY:
        detail = _as_text(_arg(args, 0))
        _record(plugin_id, event, detail, True)
        return

    if event == "object.__getattr__":
        _record(plugin_id, event, _as_text(_arg(args, 1)), False)
        raise _denial(PermissionError(
            f"plugin {plugin_id!r} cannot read {_arg(args, 1)!r} of another frame: "
            f"frame introspection is never available to plugins"))

    what = _DENY_ALWAYS.get(event)
    if what is not None:
        detail = _as_text(_arg(args, 0))
        _record(plugin_id, event, detail, False)
        loader.log_denial(plugin_id, event, what)
        raise _denial(PermissionError(
            f"plugin {plugin_id!r} cannot {what}: this is never available to "
            f"plugins ({event})"))

    what = _NATIVE.get(event)
    if what is not None:
        detail = _as_text(_arg(args, 0))
        if _is_runtime_file(detail):
            return
        allowed = loader.has_permission(loader.PERM_NATIVE, plugin_id)
        _record(plugin_id, event, detail, allowed)
        loader.require_permission(loader.PERM_NATIVE, what,
                                   detail=detail, plugin_id=plugin_id)
        return

    what = _NETWORK.get(event)
    if what is not None:
        detail = _network_detail(event, args)
        allowed = loader.has_permission(loader.PERM_NETWORK, plugin_id)
        _record(plugin_id, event, detail, allowed)
        if not allowed:
            _deny_network(plugin_id, event, what, detail)
        return

    if event in _FILES or event in _DATABASE:
        _check_protected_write(event, args, plugin_id, loader)

    if event in _DATABASE:
        path = _as_text(_arg(args, 0))
        if _is_own_file(plugin_id, path) or _is_runtime_file(path):
            return
        allowed = loader.has_permission(loader.PERM_FILES, plugin_id)
        _record(plugin_id, event, path, allowed)
        loader.require_permission(loader.PERM_FILES, _DATABASE[event],
                                   detail=path, plugin_id=plugin_id)
        return

    if event in _FILES:
        index = _FILES[event]
        indexes = index if isinstance(index, tuple) else (index,)
        for one in indexes:
            path = _as_text(_arg(args, one)) if one is not None else None
            if path is None:
                continue
            if event == "open" and getattr(loader._context_state, "open_checked", None) == path:
                continue
            if _is_own_file(plugin_id, path):
                continue
            if _is_runtime_file(path):
                continue
            if is_session_credential_path(path):
                allowed = loader.has_permission(loader.PERM_HOOKS, plugin_id)
                _record(plugin_id, event, path, allowed)
                loader.require_permission(loader.PERM_HOOKS, "read session files",
                                           detail=path, plugin_id=plugin_id)
                return
            allowed = loader.has_permission(loader.PERM_FILES, plugin_id)
            _record(plugin_id, event, path, allowed)
            if allowed:
                return
            loader.require_permission(loader.PERM_FILES, _file_verb(event),
                                       detail=path, plugin_id=plugin_id)
            return
        return


_SEALED_DIRS = ("shared_prefs", "databases", "files/chaquopy")
_PLUGIN_CODE_SUFFIXES = (".py", ".pyc", ".pyo", ".pyw", ".pth", ".so", ".plugin", ".eaf", ".mzpack")
_PLUGIN_PRIVATE_DIRS = (".data", ".mzpack_extracted", "mzpack_local_libs", "MzPackPlugins")
_PLUGIN_ENGINE_DIRS = ("__pycache__", "shared_libs")

_WRITE_TARGETS = {
    "os.remove": (0,),
    "os.rename": (0, 1),
    "os.rmdir": (0,),
    "os.mkdir": (0,),
    "os.chmod": (0,),
    "os.chown": (0,),
    "os.truncate": (0,),
    "os.link": (1,),
    "os.symlink": (1,),
    "os.utime": (0,),
    "shutil.copyfile": (1,),
    "shutil.copymode": (1,),
    "shutil.copystat": (1,),
    "shutil.copytree": (1,),
    "shutil.move": (0, 1),
    "shutil.rmtree": (0,),
    "shutil.unpack_archive": (1,),
    "sqlite3.connect": (0,),
    "sqlite3.connect/handle": (0,),
}

_DIR_FD_ARGS = {"os.rename": (2, 3), "os.remove": (1,), "os.rmdir": (1,), "os.mkdir": (2,),
                "os.chmod": (2,), "os.chown": (3,), "os.link": (2, 3), "os.symlink": (2,),
                "os.utime": (3,)}

_WRITE_FLAGS = os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND

_storage_roots = None


def _storage_paths():
    global _storage_roots
    if _storage_roots is not None:
        return _storage_roots
    try:
        from file_utils import get_files_dir
        files_dir = get_files_dir()
    except Exception:
        files_dir = None
    if not files_dir:
        return (), None
    files_dir = os.path.realpath(files_dir)
    data_dir = os.path.dirname(files_dir)
    sealed = tuple(os.path.realpath(os.path.join(data_dir, name)) for name in _SEALED_DIRS)
    _storage_roots = (sealed, os.path.realpath(os.path.join(files_dir, "plugins")))
    return _storage_roots


def _within(target: str, root: str) -> bool:
    return target == root or target.startswith(root + os.sep)


def _protected_reason(plugin_id: str, path: str) -> Optional[str]:
    try:
        target = os.path.realpath(path)
    except Exception:
        return None
    sealed, plugins_dir = _storage_paths()
    for root in sealed:
        if _within(target, root):
            return "app settings and databases"
    if plugins_dir is None or not _within(target, plugins_dir) or target == plugins_dir:
        return None
    parts = os.path.relpath(target, plugins_dir).split(os.sep)
    if target.lower().endswith(_PLUGIN_CODE_SUFFIXES):
        return "plugin code"
    if parts[0] in _PLUGIN_ENGINE_DIRS:
        return "plugin engine files"
    if parts[0] in _PLUGIN_PRIVATE_DIRS and (len(parts) < 2 or parts[1] != plugin_id):
        return "data of other plugins"
    return None


def _fd_path(fd) -> Optional[str]:
    if type(fd) is not int or fd < 0:
        return None
    try:
        return os.readlink(f"/proc/self/fd/{fd}")
    except Exception:
        return None


def _write_targets(event, args):
    if event == "open":
        path = _as_text(_arg(args, 0))
        if path is None:
            return ()
        mode = _arg(args, 1)
        flags = _arg(args, 2)
        writes = (isinstance(mode, str) and any(c in mode for c in "wax+")) \
            or (type(flags) is int and flags & _WRITE_FLAGS)
        if writes:
            return (path,)
        return (path,) if os.path.isdir(path) else ()
    targets = []
    for index in _WRITE_TARGETS.get(event, ()):
        path = _as_text(_arg(args, index))
        if path is not None:
            targets.append(path)
    for index in _DIR_FD_ARGS.get(event, ()):
        directory = _fd_path(_arg(args, index))
        if directory is not None:
            targets.append(directory)
    return targets


_extraction = (None, None, ())


def _capture_extraction() -> None:
    global _extraction
    try:
        importer = sys.modules["java.android.importer"]
        zips = []
        for finder in list(sys.path_importer_cache.values()):
            if isinstance(finder, importer.AssetFinder):
                for archive in finder.zip_files:
                    if not any(archive is known for known in zips):
                        zips.append(archive)
        _extraction = (os.path.realpath(importer.ASSET_PREFIX),
                       importer.AssetZipFile.extract_if_changed.__code__,
                       tuple(zips))
    except Exception:
        _extraction = (None, None, ())


def _runtime_extraction(path: str, loader) -> bool:
    root, code, zips = _extraction
    if root is None:
        return False
    try:
        target = os.path.realpath(path)
    except Exception:
        return False
    if not _within(target, root):
        return False
    frame = sys._getframe(1)
    while frame is not None:
        if frame.f_code is code:
            archive = frame.f_locals.get("self")
            return any(archive is known for known in zips)
        if loader._owner_of_frame(frame) is not None:
            return False
        frame = frame.f_back
    return False


def _check_protected_write(event, args, plugin_id, loader) -> None:
    for path in _write_targets(event, args):
        reason = _protected_reason(plugin_id, path)
        if reason is None:
            continue
        if _runtime_extraction(path, loader):
            continue
        if getattr(loader._context_state, "engine_write", False) \
                or loader.has_permission(loader.PERM_HOOKS, plugin_id):
            _record(plugin_id, event, path, True)
            return
        _record(plugin_id, event, path, False)
        loader.log_denial(plugin_id, event, "modify " + reason)
        raise _denial(PermissionError(
            f"plugin {plugin_id!r} cannot modify {path!r}: {reason} are only "
            f"writable by trusted plugins"))


def _file_verb(event: str) -> str:
    if event == "open":
        return "open a file"
    if event in ("os.listdir", "os.scandir", "glob.glob", "pathlib.Path.glob"):
        return "list a directory"
    return "modify files"


_installed = False


def install(loader_module) -> bool:
    global _loader, _installed
    _loader = loader_module
    if _installed:
        return True
    _capture_extraction()
    try:
        bound_watched = _WATCHED
        bound_hook = _hook
        bound_loader = loader_module
        sys.addaudithook(
            lambda event, args: event in bound_watched
            and bound_hook(event, args, bound_watched, bound_loader))
        _installed = True
    except Exception as e:
        print(f"[mzgram:audit_gate] install failed: {e}", file=sys.stderr)
        return False
    return True
