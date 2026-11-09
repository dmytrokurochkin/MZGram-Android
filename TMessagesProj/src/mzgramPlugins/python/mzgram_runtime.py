# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Self-check of the Python runtime for plugins (MZGramPythonRuntime.describe):
# the Python version and the versions of the packages bundled with the build
# (TMessagesProj/build.gradle, chaquopy pip). Reads only what is installed;
# nothing is fetched.

import platform

# Distribution name -> import name, for the fallback through __version__
BUNDLED = (
    ("requests", "requests"),
    ("beautifulsoup4", "bs4"),
    ("pillow", "PIL"),
    ("PyYAML", "yaml"),
    ("packaging", "packaging"),
)


def _version(distribution, module):
    try:
        from importlib import metadata
        return metadata.version(distribution)
    except Exception:
        pass
    try:
        imported = __import__(module)
        return str(getattr(imported, "__version__", "?"))
    except Exception:
        return None


def describe():
    lines = ["Python " + platform.python_version()]
    for distribution, module in BUNDLED:
        version = _version(distribution, module)
        lines.append(distribution + " " + (version if version else "-"))
    return "\n".join(lines)
