# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# The .plugin format: one Python file with its metadata as top-level literal
# constants (__id__, __name__, __description__, __author__, __version__,
# __icon__, __app_version__ or the older __min_version__, __sdk_version__,
# __beta__, __requirements__, __permissions__) and one BasePlugin subclass.
# This module reads the metadata through the AST, without running the file,
# so a plugin can be shown before anything of it is executed. Constants that
# are not literals are ignored on purpose.
#
# Based on exteraless extera_utils/metadata_parser.py (GPL-3.0): the keys, the
# __id__ rules, the permission keys and the normalisation of __requirements__
# and __permissions__. The disk cache next to the plugin file is left out
# (MZGram writes nothing beside a plugin); a cache in memory is kept.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import ast
import copy
import json
import os
import re
import sys

PLUGIN_EXTENSION = ".plugin"

# Largest .plugin file read; a bigger one is refused before parsing
MAX_SOURCE_BYTES = 2 * 1024 * 1024


class PluginMetadataError(Exception):
    """The metadata block of a plugin is missing or invalid."""


_METADATA_KEYS = (
    "__id__", "__name__", "__description__", "__author__", "__version__",
    "__icon__", "__app_version__", "__sdk_version__", "__min_version__",
    "__beta__", "__requirements__", "__permissions__",
)

# Permission keys a plugin may declare in __permissions__ (the permission model
# itself comes with plugins.security.permissions-model)
KNOWN_PERMISSIONS = (
    "ui",
    "messages.read",
    "messages.send",
    "network",
    "files",
    "intents",
    "settings",
    "hooks",
    "native",
)

_ID_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_-]{1,31}$")

_cache = {}


def _file_key(path):
    try:
        st = os.stat(path)
    except OSError:
        return None
    return st.st_size, st.st_mtime_ns


def _extract_constants(path):
    try:
        if os.path.getsize(path) > MAX_SOURCE_BYTES:
            raise PluginMetadataError("plugin file is larger than 2 MB")
        with open(path, "r", encoding="utf-8") as handle:
            source = handle.read()
    except PluginMetadataError:
        raise
    except (OSError, UnicodeDecodeError) as e:
        raise PluginMetadataError("cannot read plugin file: %s" % e)

    try:
        tree = ast.parse(source, filename=os.path.basename(path))
    except SyntaxError as e:
        raise PluginMetadataError("plugin file has a syntax error: %s" % e)

    constants = {}
    for node in tree.body:
        target = None
        value_node = None
        if isinstance(node, ast.Assign) and len(node.targets) == 1 \
                and isinstance(node.targets[0], ast.Name):
            target = node.targets[0].id
            value_node = node.value
        elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) \
                and node.value is not None:
            target = node.target.id
            value_node = node.value
        if target is None or target not in _METADATA_KEYS or target in constants:
            continue
        try:
            constants[target] = ast.literal_eval(value_node)
        except (ValueError, SyntaxError, TypeError, MemoryError):
            # Dynamic metadata is ignored by design
            pass
    return constants


def _validate_id(plugin_id):
    if plugin_id is None:
        raise PluginMetadataError("plugin metadata is missing the required __id__ constant")
    if not isinstance(plugin_id, str):
        raise PluginMetadataError("__id__ must be a string, got %s" % type(plugin_id).__name__)
    if not _ID_PATTERN.match(plugin_id):
        raise PluginMetadataError(
            "invalid __id__ %r: must be 2-32 characters, start with a letter "
            "and contain only Latin letters, digits, '_' or '-'" % plugin_id)
    if plugin_id in getattr(sys, "stdlib_module_names", ()):
        raise PluginMetadataError(
            "__id__ %r collides with a Python standard library module name" % plugin_id)
    return plugin_id


def _split_bare(text):
    parts = []
    for chunk in text.replace("\n", ",").split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        if parts and chunk[0] in "<>=!~":
            # "pkg>=1,<2" stays one requirement
            parts[-1] = parts[-1] + "," + chunk
        else:
            parts.append(chunk)
    return parts


def _validate_permissions(declared):
    if isinstance(declared, str):
        declared = _split_bare(declared)
    if not isinstance(declared, (list, tuple, set, frozenset)):
        raise PluginMetadataError("__permissions__ must be a list of permission keys")
    out = []
    for item in declared:
        if not isinstance(item, str):
            raise PluginMetadataError(
                "__permissions__ entries must be strings, got %s" % type(item).__name__)
        key = item.strip()
        if key not in KNOWN_PERMISSIONS:
            # A typo must not turn silently into a missing permission
            raise PluginMetadataError(
                "unknown permission %r in __permissions__; known keys: %s"
                % (item, ", ".join(KNOWN_PERMISSIONS)))
        if key not in out:
            out.append(key)
    return out


def _optional_string(constants, key):
    value = constants.get(key)
    if value is None:
        return None
    return value if isinstance(value, str) else str(value)


def _read_uncached(path):
    constants = _extract_constants(path)

    plugin_id = _validate_id(constants.get("__id__"))

    name = constants.get("__name__")
    if name is None:
        raise PluginMetadataError("plugin metadata is missing the required __name__ constant")
    if not isinstance(name, str) or not name.strip():
        raise PluginMetadataError("__name__ must be a non-empty string")

    version = constants.get("__version__", "1.0")
    if not isinstance(version, str):
        version = str(version)

    app_version = _optional_string(constants, "__app_version__")
    min_version = _optional_string(constants, "__min_version__")
    if app_version is None and min_version is not None:
        # Older form: __min_version__ = "12.5.1" means __app_version__ = ">=12.5.1"
        app_version = ">=" + min_version

    requirements = constants.get("__requirements__") or []
    if isinstance(requirements, str):
        requirements = _split_bare(requirements)
    if not isinstance(requirements, (list, tuple)) \
            or not all(isinstance(item, str) for item in requirements):
        raise PluginMetadataError("__requirements__ must be a string or a list of PEP 508 strings")

    declared = "__permissions__" in constants
    permissions = _validate_permissions(constants.get("__permissions__") or []) if declared else []

    description = constants.get("__description__") or ""
    author = constants.get("__author__") or ""
    icon = constants.get("__icon__")
    return {
        "id": plugin_id,
        "name": name.strip(),
        "description": description if isinstance(description, str) else str(description),
        "author": author if isinstance(author, str) else str(author),
        "version": version,
        "icon": icon if isinstance(icon, str) else None,
        "app_version": app_version,
        "sdk_version": _optional_string(constants, "__sdk_version__"),
        "beta": bool(constants.get("__beta__", False)),
        "requirements": list(requirements),
        # "declared nothing" and "declared an empty list" stay apart
        "permissions": permissions,
        "permissions_declared": declared,
    }


def read_metadata(path):
    """The metadata of the .plugin file at path, without running it.

    Raises PluginMetadataError with a readable message on any problem.
    """
    key = _file_key(path)
    cached = _cache.get(path)
    if key is not None and cached is not None and cached[0] == key:
        return copy.deepcopy(cached[1])
    meta = _read_uncached(path)
    if key is not None:
        _cache[path] = (key, copy.deepcopy(meta))
    return meta


def read_metadata_json(path):
    """For Java (MZGramPluginFormat): always a JSON string, {"ok": true, "meta": {...}}
    or {"ok": false, "error": "..."}."""
    try:
        return json.dumps({"ok": True, "meta": read_metadata(path)}, ensure_ascii=False)
    except PluginMetadataError as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": "%s: %s" % (type(e).__name__, e)},
                          ensure_ascii=False)
