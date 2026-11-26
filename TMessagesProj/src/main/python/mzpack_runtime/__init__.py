"""MzPack structured-plugin runtime for the mzgram plugin engine.

Loads .mzpack/.eaf archives (ZIP with a root refmap) as ordinary BasePlugin
instances inside an isolated MzPackPlugins.<plugin_id> import namespace, with
bundled assets, localization and plugin-local wheels.

Public contract consumed by mz_utils.plugin_loader:

    load_plugin_record(record, path)  -> None  (raises MzPackError on bad archives)
    unload_plugin_record(record)      -> None
    is_available()                    -> True

read_metadata()/read_metadata_json() mirror mz_utils.metadata_parser so the
scanner can read archive metadata without executing plugin code.

Importing this package also installs the plugin-facing `mzpack` facade module
(assets / metainfo / refmap / settings / strings, bound to the calling plugin).
"""

from .errors import (
    MzPackArchiveError,
    MzPackError,
    MainModuleNotFoundError,
    MetainfoError,
    MetainfoNotFoundError,
    PluginClassNotFoundError,
    RefmapError,
    RefmapNotFoundError,
)
from .dev_commands import handle_dev_command
from .facade import install_facade
from .loader import (
    get_state,
    is_available,
    load_plugin_record,
    purge_plugin,
    unload_plugin_record,
)
from .metadata import read_metadata, read_metadata_json

install_facade()

__all__ = [
    "load_plugin_record",
    "unload_plugin_record",
    "is_available",
    "read_metadata",
    "read_metadata_json",
    "purge_plugin",
    "get_state",
    "handle_dev_command",
    "MzPackError",
    "MzPackArchiveError",
    "RefmapNotFoundError",
    "RefmapError",
    "MetainfoNotFoundError",
    "MetainfoError",
    "MainModuleNotFoundError",
    "PluginClassNotFoundError",
]

__version__ = "1.0.0"
