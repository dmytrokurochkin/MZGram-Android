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

## The plugin list

In the build with plugins, MZGram → Other → **Plugins** lists the added
plugins, each with its version, author and an on/off switch.

- **Add plugin file** picks a `.plugin` file. MZGram reads its metadata
  without running it and shows its name, description and permissions before
  it keeps the file. A file with the id of an added plugin updates it; the
  plugin's settings and on/off state stay.
- Tapping a plugin opens its screen: the on/off switch, version, author,
  id, the app and SDK versions it needs, its permissions and Python
  packages, and buttons to view its code (read-only, coloured as Python),
  share the file and delete the plugin with its settings.
- An added plugin is off. MZGram doesn't run plugins yet, and the screen
  says so; the on/off state is kept for when it does.

Added files are kept in the app's own folder (`files/mzgram_plugins`), out
of reach of other apps.

The format and the metadata reader are based on the exteraless plugin SDK
(GPL-3.0).

## Settings

A plugin describes its settings screen as a list of rows from
`ui.settings`, returned by `create_settings()`. MZGram draws them in the
style of its own settings:

```python
from ui.settings import Header, Switch, Selector, Input, EditText, Text, Divider

def create_settings(self):
    return [
        Header(text="General"),
        Switch(key="enabled", text="Enabled", default=True,
               on_change=lambda value: self.log(f"enabled: {value}")),
        Selector(key="mode", text="Mode", default=0, items=["Fast", "Exact"]),
        Input(key="prefix", text="Prefix", default=">"),
        Text(text="More", create_sub_fragment=lambda: [
            EditText(key="note", hint="Note", multiline=True, max_length=200),
        ]),
        Divider(text="Applies to new messages."),
    ]
```

| Row | Value under `key` | Fields |
|---|---|---|
| `Header` | none | `text` |
| `Divider` | none | optional `text`, shown as a note under the section |
| `Switch` | `bool` | `text`, `default`, `subtext`, `icon`, `on_change`, `on_long_click` |
| `Selector` | `int`, the index in `items` | `text`, `default`, `items`, `subtext`, `icon`, `on_change`, `on_long_click` |
| `Input` | `str` | `text`, `default`, `subtext`, `icon`, `on_change`, `on_long_click` |
| `EditText` | `str` | `hint` (the row's title), `default`, `multiline`, `max_length`, `on_change` |
| `Text` | none | `text`, `subtext`, `icon`, `accent`, `red`, `on_click`, `on_long_click`, `create_sub_fragment` |

- `on_change` gets the new value, or nothing if it takes no argument. The
  value is saved before `on_change` runs, even if `on_change` fails.
- `on_click` and `on_long_click` get `None` for now. In exteraless they get
  the row's view, but views can't be passed to Python yet.
- After each change and each tap, the rows are read again, so
  `create_settings()` can show different rows depending on the values.
- `create_sub_fragment()` returns the rows of a page of their own. Sub-pages
  can be nested up to 8 levels.
- `icon` is the name of a drawable in the app. An unknown name shows no
  icon.
- `Custom` rows, with a view of the plugin's own, are accepted but shown as
  a note until the SDK has its UI components. `EditText.mask` is accepted and
  ignored.

Values are saved on the device in one file per plugin and keep their JSON
type.

## Menu items

A plugin can add items to Telegram's menus with `MenuItemData`, named as in
the exteraless SDK:

```python
from mzgram_plugins.menus import MenuItemData, MenuItemType

MenuItemData(
    menu_type=MenuItemType.MESSAGE_CONTEXT_MENU,
    text="Translate locally",
    icon="msg_language",
    on_click=lambda context: self.log(f"message {context['message_id']}"),
)
```

| `menu_type` | Where | `context` of a tap |
|---|---|---|
| `MESSAGE_CONTEXT_MENU` | menu of a message | `account`, `dialog_id`, `message_id`, `from_id`, `out`, `date`, `text` |
| `CHAT_ACTION_MENU` | three dots of a chat | `account`, `dialog_id` |
| `PROFILE_ACTION_MENU` | three dots of a profile | `account`, `user_id`, `chat_id`, `dialog_id` |
| `MAIN_MENU`, `DRAWER_MENU` | menu of the chat list (MZGram has no side drawer) | `account` |

- `context` is a dict of ids and texts, not Java objects as in exteraless.
- When a menu has more than two items of plugins, they are folded into one
  **Plugins** item that opens their list, so the menu doesn't grow past the
  screen.
- Higher `priority` comes first. `text` is one line of at most 64
  characters. A plugin can add up to 16 items; adding an item with the same
  `item_id` replaces it.
- `condition` (an MVEL expression in exteraless) is accepted and not
  evaluated: the item always shows, so `on_click` should check `context`.
- Only items of plugins that are turned on show. The menu of a chat and of
  a profile take items when they are opened.

The engine will add `add_menu_item()` and `remove_menu_item()` to
`BasePlugin`; until then they are in `mzgram_plugins.menus`, taking the
plugin id first.

## Developer server

While you write a plugin, the developer server lets a computer add and
update it without picking the file on the phone each time. Turn it on in
MZGram → Other → Plugins → **Developer server**. It is off by default, it
stays on only while MZGram runs, and it never starts by itself.

- It listens on `127.0.0.1:42690` only, the address and port of the
  exteraless developer server, so nothing outside the phone reaches it.
  From a computer, connect the phone with adb and forward the port:
  `adb forward tcp:42690 tcp:42690`.
- Other apps on the phone can reach `127.0.0.1` too, so every command but
  `ping` needs the **token** shown under the switch (tap it to copy it). Each
  start makes a new token. A wrong token closes the connection, and after 20
  wrong tokens the server turns off.
- The protocol is one UTF-8 JSON object per line, as in exteraless: `"@"` is
  the command, `"#"` an id of yours that comes back in the reply, and
  `"token"` the token. Each reply is one line
  `{"#": ..., "ok": true}` or `{"#": ..., "ok": false, "error": "..."}`.

| Command | Fields | Does |
|---|---|---|
| `ping` | none, no token | replies `{"pong": true}` |
| `get_plugins` | none | lists the added plugins: `id`, `name`, `version`, `author`, `enabled` |
| `write_plugin` | `plugin_id`, `content` (the `.plugin` file in base64, up to 2 MB) | checks the metadata as for a picked file and keeps the file; `__id__` must be `plugin_id`. A new plugin is off; an update keeps its settings and on/off state. The reply has `"updated"`. |
| `enable_plugin`, `disable_plugin` | `plugin_id` | turns the plugin on or off |
| `remove_plugin` | `plugin_id` | deletes the plugin and its settings |
| `reload_plugin` | `plugin_id` | refused until MZGram runs plugins |

```sh
adb forward tcp:42690 tcp:42690
python3 - <<'PY'
import base64, json, socket
token = "paste the token here"
source = open("hello.plugin", "rb").read()
with socket.create_connection(("127.0.0.1", 42690)) as s:
    f = s.makefile("rwb")
    request = {"@": "write_plugin", "#": 1, "token": token,
               "plugin_id": "hello_mzgram", "content": base64.b64encode(source).decode()}
    f.write(json.dumps(request).encode() + b"\n")
    f.flush()
    print(f.readline().decode())
PY
```

`start_debugger` and `stop_debugger` reply that debugpy is not available:
MZGram has no debugger for plugins.

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
| Plugin list in MZGram settings, adding from a file, plugin screen, code viewer | done |
| Running plugins (the on/off switch is kept until then) | planned |
| Settings rows (`ui.settings`) and the settings screen | done; opened from the plugin screen once plugins run |
| Menu items of plugins (`mzgram_plugins.menus`) in the menus of messages, chats, profiles and the chat list | done; shown once plugins run |
| Developer server on `127.0.0.1:42690` (add, update, turn on or off, delete plugins from a computer through adb) | done; reloading waits for plugins to run |
| SDK: `base_plugin`, hooks, UI components | planned |
| Permissions, prompt before installing, local audit log, safe mode after crashes | planned |
| Installing packages with pip on the device | not planned (it would make network calls to PyPI) |
