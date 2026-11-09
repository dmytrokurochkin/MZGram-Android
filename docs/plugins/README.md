# MZGram plugins (work in progress)

MZGram is getting a plugin platform: plugins written in Python and run by an
interpreter built into the app. It is not finished yet. This page covers what
exists today, the build that has it, and the plugin file format. Each part
below says whether it works already or is still planned.

Plugins are **off by default** and the default MZGram build has no Python
inside it at all. MZGram has no plugin catalog and does not download plugins
or Python packages.

## The build with plugins

Python is only in a build made with the Gradle property `mzgramPlugins`:

```sh
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone -PmzgramPlugins=true
```

Without the property, the build is the same as before.

With the property:

- Chaquopy (MIT) is applied to `TMessagesProj`, with CPython 3.11 for
  `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`;
- `minSdk` becomes 24 (Android 7.0) for the library and the apps, because
  Chaquopy 16 needs it;
- these packages are bundled, installed at build time: `requests`,
  `beautifulsoup4`, `pillow`, `PyYAML`, `packaging`;
- `src/mzgramPlugins/java` and `src/mzgramPlugins/python` are compiled in.

The build machine needs Python 3.11. If it is not the first `python` on the
`PATH`, pass it with `-PmzgramPluginsBuildPython=/path/to/python3.11`.
`-PmzgramChaquopyVersion=` changes the Chaquopy version (default 16.1.0).

In that build, MZGram → Other → Diagnostics has a **Python for plugins** row.
Tap it to start the interpreter and see the Python version and the versions
of the bundled packages. The interpreter never starts by itself.

## The `.plugin` format

A plugin is a single UTF-8 Python file with the extension `.plugin`, up to
2 MB. It contains:

1. **Metadata** as top-level constants with literal values. MZGram reads them
   from the syntax tree without running the file, so a plugin can be shown
   before any of its code runs. A value that is not a literal, such as an
   f-string or a function call, is ignored.
2. **One class** that inherits from `BasePlugin`.

| Constant | Required | Meaning |
|---|---|---|
| `__id__` | yes | 2–32 characters: a Latin letter first, then Latin letters, digits, `_` or `-`. It must not be the name of a Python standard library module. |
| `__name__` | yes | Name shown to the user. |
| `__description__` | no | Short description. |
| `__author__` | no | Author. |
| `__version__` | no | Plugin version. Default `"1.0"`. |
| `__icon__` | no | Name of a sticker set to use as the icon. |
| `__app_version__` | no | Required app version, such as `">=13.1.1"`. The older `__min_version__ = "13.1.1"` means the same as `">=13.1.1"`. MZGram does not check this yet. |
| `__sdk_version__` | no | Required SDK version. |
| `__beta__` | no | `True` for a test version. |
| `__requirements__` | no | Python packages, as a list or a comma-separated string of PEP 508 requirements. Only the bundled packages are available. MZGram installs nothing on the device. |
| `__permissions__` | no | List of permission keys. A key MZGram doesn't know makes the plugin invalid, so a typo can't quietly drop a permission. Leaving the constant out is not the same as writing an empty list. |

The permission keys are `ui`, `messages.read`, `messages.send`, `network`,
`files`, `intents`, `settings`, `hooks` and `native`. What each one allows
comes with the permission model, which is not done yet.

Archives (`.elyx`, `.eaf`) are not supported.

The format and the metadata reader are based on the exteraless plugin SDK
(GPL-3.0).

## Examples

[`examples/hello.plugin`](examples/hello.plugin) is the smallest plugin. It
shows a message when it is turned on and when it is turned off.

[`examples/shrug.plugin`](examples/shrug.plugin) replaces `:shrug:` in your
outgoing messages with `¯\_(ツ)_/¯`, using the hook on sending a message.

Both files are valid `.plugin` files today: their metadata can already be
read. Running them needs the parts that are still planned: the plugin engine,
the SDK modules (`base_plugin`, `ui.bulletin`) and the permission prompts.
Until those parts exist, the method names in the examples follow the
exteraless SDK and may still change.

## Status

| Part | State |
|---|---|
| Python runtime (Chaquopy) in a separate build | done |
| `.plugin` format and metadata reader | done |
| Plugin list in MZGram settings, installing, turning plugins on and off | planned |
| SDK: `base_plugin`, settings, menus, hooks | planned |
| Permissions, prompt before installing, local audit log, safe mode after crashes | planned |
| Installing packages with pip on the device | not planned (it would make network calls to PyPI) |
