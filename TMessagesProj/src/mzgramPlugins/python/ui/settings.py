# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Settings rows of a plugin, as plugins write them: create_settings() returns
# a list of these objects and MZGram draws them in the style of the app
# (MZGramPluginSettingsActivity), from the JSON of mzgram_plugins.settings.
#
#   from ui.settings import Header, Switch, Selector, Input, Text, Divider
#
#   def create_settings(self):
#       return [
#           Header(text="General"),
#           Switch(key="enabled", text="Enabled", default=True),
#           Selector(key="mode", text="Mode", default=0, items=["Fast", "Exact"]),
#           Divider(text="Applies to new messages."),
#       ]
#
# Names and fields follow the exteraless plugin SDK, ui/settings.py
# (GPL-3.0), so plugins written for it keep working. Custom rows (a view of
# the plugin's own) are accepted but not drawn yet: they need the UI
# components of the SDK.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

from dataclasses import dataclass
from typing import Any, Callable, List, Optional

__all__ = ["Header", "Divider", "Switch", "Selector", "Input", "Text", "EditText", "Custom"]


@dataclass
class Header:
    """A section title."""
    text: str


@dataclass
class Divider:
    """The gap between sections, with an optional note under the section."""
    text: Optional[str] = None


@dataclass
class Switch:
    """On or off; the value is a bool kept under key."""
    key: str
    text: str
    default: bool
    subtext: Optional[str] = None
    icon: Optional[str] = None
    # on_change(new_value) or on_change()
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None


@dataclass
class Selector:
    """One of items; the value is the index of the chosen item."""
    key: str
    text: str
    default: int
    items: List[str]
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None


@dataclass
class Input:
    """A text value, edited in a dialog; shown to the right of the row."""
    key: str
    text: str
    default: Optional[str] = None
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None


@dataclass
class Text:
    """A row to tap: runs on_click, or opens the rows of create_sub_fragment()."""
    text: str
    subtext: Optional[str] = None
    icon: Optional[str] = None
    accent: bool = False
    red: bool = False
    on_click: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], list]] = None
    link_alias: Optional[str] = None


@dataclass
class EditText:
    """A longer text value, edited in a dialog; the hint is the row's title."""
    key: str
    hint: str
    default: str = ""
    multiline: bool = False
    max_length: Optional[int] = None
    # Kept for plugins written for exteraless; MZGram does not use it
    mask: Optional[str] = None
    on_change: Optional[Callable] = None


@dataclass
class Custom:
    """A row with a view of the plugin's own (not drawn by MZGram yet)."""
    item: Optional[Any] = None
    view: Optional[Any] = None
    factory: Optional[Any] = None
    factory_args: Optional[tuple] = None
    on_click: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], list]] = None
    link_alias: Optional[str] = None
