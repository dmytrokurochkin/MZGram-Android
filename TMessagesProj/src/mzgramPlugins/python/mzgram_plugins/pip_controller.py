# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Mini-pip package manager for Python plugins.
# Downloads pure-Python wheels (*-none-any.whl) from PyPI directly on device
# upon explicit user request and confirmation.
# Packages with native compiled binaries (.so) or requiring C compilers
# are refused with a clear error.
#
# Packages are extracted into:
#   <files>/mzgram_plugins/shared_libs/<normalized_name>/<version>/
# and shared across plugins using reference counting in manifest.json.

import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import threading
import urllib.request
import zipfile

_PYPI_JSON = "https://pypi.org/pypi/{}/json"
_PURE_WHEEL_SUFFIX = "none-any.whl"
_MAX_WHEEL_BYTES = 50 * 1024 * 1024  # 50 MB limit per wheel
_HTTP_TIMEOUT = 20

_lock = threading.RLock()
_pypi_cache = {}
_sys_path_added = set()
_manifest_cache = None


def _files_dir() -> str:
    """Returns the app's files directory."""
    try:
        from org.telegram.messenger import ApplicationLoader
        path = ApplicationLoader.getFilesDirFixed()
        if path:
            return str(path.getAbsolutePath())
    except Exception:
        pass
    env = os.environ.get("EXTERALESS_FILES_DIR") or os.environ.get("MZGRAM_FILES_DIR")
    if env:
        return env
    return os.path.join(tempfile.gettempdir(), "mzgram_files")


def _shared_libs_dir() -> str:
    path = os.path.join(_files_dir(), "mzgram_plugins", "shared_libs")
    os.makedirs(path, exist_ok=True)
    return path


def _manifest_path() -> str:
    return os.path.join(_shared_libs_dir(), "manifest.json")


def _load_manifest() -> dict:
    global _manifest_cache
    with _lock:
        if _manifest_cache is not None:
            return _manifest_cache
        try:
            with open(_manifest_path(), "r", encoding="utf-8") as handle:
                data = json.load(handle)
            if not isinstance(data, dict) or not isinstance(data.get("packages"), dict):
                data = {"packages": {}}
            _manifest_cache = data
        except Exception:
            _manifest_cache = {"packages": {}}
        return _manifest_cache


def _save_manifest() -> None:
    with _lock:
        if _manifest_cache is None:
            return
        tmp = _manifest_path() + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(_manifest_cache, handle, indent=2, sort_keys=True)
        os.replace(tmp, _manifest_path())


def normalize_name(name: str) -> str:
    """PEP 503 normalization."""
    return re.sub(r"[-_.]+", "-", name).lower().strip()


def parse_requirement_name(req_str: str) -> str:
    """Extracts package name from requirement string."""
    name = re.split(r"[\s\[<>=!~;,]", str(req_str).strip(), 1)[0]
    return normalize_name(name)


def _ensure_on_sys_path(path: str) -> None:
    with _lock:
        if path and os.path.isdir(path) and path not in _sys_path_added:
            if path not in sys.path:
                sys.path.insert(0, path)
            _sys_path_added.add(path)


def restore_sys_path() -> None:
    """Restores all previously installed packages to sys.path."""
    manifest = _load_manifest()
    packages = manifest.get("packages", {})
    for name, info in packages.items():
        path = info.get("path")
        if path and os.path.isdir(path):
            _ensure_on_sys_path(path)


def is_installed(package_name: str) -> bool:
    """Returns True if package is currently installed in shared_libs."""
    norm = normalize_name(package_name)
    manifest = _load_manifest()
    pkg = manifest.get("packages", {}).get(norm)
    if pkg and pkg.get("path") and os.path.isdir(pkg["path"]):
        return True
    return False


def _fetch_pypi_json(normalized_name: str) -> dict:
    with _lock:
        if normalized_name in _pypi_cache:
            return _pypi_cache[normalized_name]

    url = _PYPI_JSON.format(normalized_name)
    req = urllib.request.Request(url, headers={"User-Agent": "MZGram-Pip/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=_HTTP_TIMEOUT) as response:
            data = json.loads(response.read().decode("utf-8"))
    except Exception as e:
        raise RuntimeError(f"Failed to fetch PyPI info for '{normalized_name}': {e}")

    with _lock:
        _pypi_cache[normalized_name] = data
    return data


def _pick_pure_wheel(pypi_data: dict, normalized_name: str) -> tuple:
    """Finds the latest release with a pure-Python wheel."""
    info = pypi_data.get("info", {})
    version = info.get("version")
    urls = pypi_data.get("urls", [])

    candidate = None
    has_native_wheels = False

    for item in urls:
        filename = item.get("filename", "")
        if filename.endswith(".whl"):
            if filename.endswith(_PURE_WHEEL_SUFFIX):
                candidate = item
                break
            else:
                has_native_wheels = True

    if candidate is None:
        if has_native_wheels:
            raise RuntimeError(f"Package '{normalized_name}' contains native binary code (.so) and cannot be compiled on Android")
        raise RuntimeError(f"Package '{normalized_name}' does not provide a pure-Python wheel (*-none-any.whl)")

    size = candidate.get("size", 0)
    if size > _MAX_WHEEL_BYTES:
        raise RuntimeError(f"Package '{normalized_name}' wheel size ({size // (1024*1024)} MB) exceeds 50 MB limit")

    return version, candidate


def _safe_extract(zip_bytes: bytes, target_dir: str) -> None:
    """Safely extracts zip bytes preventing Zip Slip vulnerabilities."""
    os.makedirs(target_dir, exist_ok=True)
    real_target = os.path.realpath(target_dir)

    import io
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        for member in zf.infolist():
            filename = member.filename
            if filename.startswith("/") or filename.startswith("\\") or ".." in filename:
                continue
            dest_path = os.path.realpath(os.path.join(target_dir, filename))
            if not (dest_path == real_target or dest_path.startswith(real_target + os.sep)):
                raise RuntimeError(f"Unsafe zip entry detected: {filename}")
            if member.is_dir():
                os.makedirs(dest_path, exist_ok=True)
            else:
                os.makedirs(os.path.dirname(dest_path), exist_ok=True)
                with zf.open(member) as src, open(dest_path, "wb") as dst:
                    shutil.copyfileobj(src, dst)


def _download_and_install_wheel(normalized_name: str, version: str, wheel_info: dict) -> str:
    url = wheel_info.get("url")
    expected_digest = wheel_info.get("digests", {}).get("sha256")
    if not url:
        raise RuntimeError(f"No download URL for '{normalized_name}'")

    req = urllib.request.Request(url, headers={"User-Agent": "MZGram-Pip/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=_HTTP_TIMEOUT) as response:
            payload = response.read()
    except Exception as e:
        raise RuntimeError(f"Failed to download '{normalized_name}': {e}")

    if expected_digest:
        actual_digest = hashlib.sha256(payload).hexdigest().lower()
        if actual_digest != expected_digest.lower():
            raise RuntimeError(f"SHA256 checksum mismatch for '{normalized_name}'")

    target = os.path.join(_shared_libs_dir(), normalized_name, str(version))
    if os.path.isdir(target):
        shutil.rmtree(target, ignore_errors=True)

    _safe_extract(payload, target)
    return target


# ---------- Public API called from Java ----------

def query_requirements_json(request_json: str) -> str:
    """
    Checks requirements against PyPI.
    Input: {"requirements": ["requests", "pyyaml>=5.0"]}
    Output: {"ok": true, "total_size": 12345, "packages": [...]} or {"ok": false, "error": "..."}
    """
    try:
        data = json.loads(request_json) if isinstance(request_json, str) else request_json
        reqs = data.get("requirements", [])
        packages = []
        total_size = 0

        manifest = _load_manifest()

        for req in reqs:
            norm = parse_requirement_name(req)
            if not norm:
                continue

            existing = manifest.get("packages", {}).get(norm)
            if existing and existing.get("path") and os.path.isdir(existing["path"]):
                packages.append({
                    "name": norm,
                    "version": existing.get("version", "unknown"),
                    "filename": "already installed",
                    "size": 0,
                    "installed": True
                })
                continue

            pypi_data = _fetch_pypi_json(norm)
            version, wheel_info = _pick_pure_wheel(pypi_data, norm)
            size = wheel_info.get("size", 0)
            total_size += size
            packages.append({
                "name": norm,
                "version": version,
                "filename": wheel_info.get("filename", ""),
                "size": size,
                "installed": False
            })

        return json.dumps({
            "ok": True,
            "total_size": total_size,
            "packages": packages
        }, ensure_ascii=False)
    except Exception as e:
        return json.dumps({
            "ok": False,
            "error": str(e)
        }, ensure_ascii=False)


def install_requirements_json(request_json: str) -> str:
    """
    Installs requirements for plugin_id.
    Input: {"plugin_id": "my_plugin", "requirements": ["six", "urllib3"]}
    Output: {"ok": true, "installed": [...]} or {"ok": false, "error": "..."}
    """
    try:
        data = json.loads(request_json) if isinstance(request_json, str) else request_json
        plugin_id = data.get("plugin_id", "")
        reqs = data.get("requirements", [])
        if not plugin_id:
            raise RuntimeError("Missing plugin_id")

        installed_list = []
        manifest = _load_manifest()

        for req in reqs:
            norm = parse_requirement_name(req)
            if not norm:
                continue

            existing = manifest.get("packages", {}).get(norm)
            if existing and existing.get("path") and os.path.isdir(existing["path"]):
                with _lock:
                    plugins = existing.setdefault("plugins", [])
                    if plugin_id not in plugins:
                        plugins.append(plugin_id)
                        _save_manifest()
                _ensure_on_sys_path(existing["path"])
                installed_list.append(f"{norm}=={existing.get('version')}")
                continue

            pypi_data = _fetch_pypi_json(norm)
            version, wheel_info = _pick_pure_wheel(pypi_data, norm)
            path = _download_and_install_wheel(norm, version, wheel_info)

            with _lock:
                manifest = _load_manifest()
                manifest["packages"][norm] = {
                    "version": str(version),
                    "path": path,
                    "plugins": [plugin_id]
                }
                _save_manifest()

            _ensure_on_sys_path(path)
            installed_list.append(f"{norm}=={version}")

        return json.dumps({
            "ok": True,
            "installed": installed_list
        }, ensure_ascii=False)
    except Exception as e:
        return json.dumps({
            "ok": False,
            "error": str(e)
        }, ensure_ascii=False)


def remove_requirements_json(request_json: str) -> str:
    """
    Decrements plugin_id from manifest refcounts and removes orphaned packages.
    Input: {"plugin_id": "my_plugin"}
    """
    try:
        data = json.loads(request_json) if isinstance(request_json, str) else request_json
        plugin_id = data.get("plugin_id", "")
        if not plugin_id:
            return json.dumps({"ok": True})

        with _lock:
            manifest = _load_manifest()
            packages = manifest.get("packages", {})
            to_delete = []

            for name, entry in list(packages.items()):
                plugins = entry.setdefault("plugins", [])
                if plugin_id in plugins:
                    plugins.remove(plugin_id)
                if not plugins:
                    path = entry.get("path")
                    if path and os.path.isdir(path):
                        shutil.rmtree(path, ignore_errors=True)
                    to_delete.append(name)

            for name in to_delete:
                del packages[name]

            _save_manifest()

        return json.dumps({"ok": True}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)


def installed_packages_json(request_json: str = None) -> str:
    """Returns snapshot of installed packages."""
    manifest = _load_manifest()
    return json.dumps(manifest.get("packages", {}), ensure_ascii=False)
