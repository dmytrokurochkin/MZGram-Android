"""Exception hierarchy for the MzPack structured-plugin runtime.

All errors raised by mzpack_runtime derive from MzPackError so the host loader can
present a single, clear failure category to the user.
"""


class MzPackError(Exception):
    """Base class for every error raised by the MzPack runtime."""


class MzPackArchiveError(MzPackError):
    """The .mzpack/.eaf archive is malformed (not a ZIP, unsafe paths, ...)."""


class RefmapNotFoundError(MzPackArchiveError):
    """No refmap.yaml / refmap.yml / refmap.json at the archive root."""


class RefmapError(MzPackArchiveError):
    """The refmap file exists but cannot be parsed into a path mapping."""


class MetainfoNotFoundError(MzPackArchiveError):
    """No metainfo file found (refmap pointer and root discovery both failed)."""


class MetainfoError(MzPackError):
    """The metainfo file exists but its content is invalid."""


class MainModuleNotFoundError(MzPackArchiveError):
    """The entry module (refmap `main` or root main.py) does not exist."""


class PluginClassNotFoundError(MzPackError):
    """The entry module defines no concrete BasePlugin subclass."""
