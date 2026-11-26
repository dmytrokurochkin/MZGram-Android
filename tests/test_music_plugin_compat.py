import importlib
import os
import re
import sys
import types

import pytest

import corpus
from test_sdk_nx_port import loader, sdk

MZPACKCORE_ALL = {
    "Asset", "AssetNotFoundException", "Assets", "AssetsDirNotFoundException",
    "Callback", "Callback2", "Callback3", "CallbackReturn", "LazyDict",
    "OnClickListener", "Runnable", "SettingsController", "Strings",
    "gen", "gen2", "get_environment", "import_module", "mvel_execute",
}

JAVA_ROOT = os.path.join(corpus.REPO, "TMessagesProj", "src", "main", "java")
MEDIA_CONTROLLER = os.path.join(JAVA_ROOT, "org", "telegram", "messenger", "MediaController.java")
VIDEO_PLAYER = os.path.join(JAVA_ROOT, "org", "telegram", "ui", "Components", "VideoPlayer.java")
PROGUARD_RULES = os.path.join(corpus.REPO, "TMessagesProj", "proguard-rules.pro")


@pytest.fixture
def mzpackcore(monkeypatch):
    monkeypatch.syspath_prepend(corpus.PYTHON_ROOT)
    before = set(sys.modules)
    try:
        module = importlib.import_module("mzpackcore")
    except Exception as exc:
        pytest.skip(f"mzpackcore is not importable here: {exc}")
    yield module
    for name in set(sys.modules) - before:
        sys.modules.pop(name, None)


def test_mzpackcore_exports_the_mzgram_public_api(mzpackcore):
    from mzpack_runtime import facade, localization, settings, utils

    assert set(mzpackcore.__all__) == MZPACKCORE_ALL
    assert mzpackcore.Strings is localization.Strings
    assert mzpackcore.SettingsController is settings.SettingsController
    assert mzpackcore.LazyDict is utils.LazyDict
    assert mzpackcore.gen is facade.gen
    with pytest.raises(AttributeError):
        mzpackcore.MzPackEngine


def test_mzpackcore_callback_proxies_are_the_mzpack_facade_ones(mzpackcore, monkeypatch):
    from mzpack_runtime import facade

    built = []
    monkeypatch.setattr(facade, "_jclass", lambda name: name)
    monkeypatch.setattr(facade, "gen", lambda java_class, method, return_value=False:
                        built.append((java_class, method)) or ("proxy", java_class))
    try:
        from mzpackcore import Runnable, Strings

        assert Runnable == ("proxy", "java.lang.Runnable")
        assert sys.modules["mzpack"].Runnable is Runnable
        assert Strings is mzpackcore.Strings
        assert built == [("java.lang.Runnable", "run")]
    finally:
        sys.modules["mzpack"].__dict__.pop("Runnable", None)


def _fake_java(monkeypatch, **native_members):
    java = types.ModuleType("java")
    native = types.ModuleType("java.chaquopy")
    for name, value in native_members.items():
        setattr(native, name, value)
    java.chaquopy = native
    monkeypatch.setitem(sys.modules, "java", java)
    monkeypatch.setitem(sys.modules, "java.chaquopy", native)
    return java, native


def test_java_class_loader_api_uses_the_native_bridge(loader, monkeypatch):
    java, native = _fake_java(monkeypatch,
                              unload_class_loader=lambda target: ["a.B"],
                              unload_classes=lambda *targets: ["c.D"])
    loader._install_java_class_loader_api()
    assert java.unload_class_loader is native.unload_class_loader
    assert java.unload_classes is native.unload_classes


def test_java_class_loader_api_fallback_validates_like_mzgram(loader, monkeypatch):
    java, _ = _fake_java(monkeypatch)

    class ClassLoader:
        def equals(self, other):
            return self is other

    main = ClassLoader()

    class Python:
        @staticmethod
        def getClass():
            return types.SimpleNamespace(getClassLoader=lambda: main)

    classes = {"java.lang.ClassLoader": ClassLoader, "com.chaquo.python.Python": Python}
    monkeypatch.setattr(loader, "engine_java_class", classes.get)
    existing = object()
    java.unload_classes = existing
    loader._install_java_class_loader_api()

    assert java.unload_classes is existing
    assert java.unload_class_loader(ClassLoader()) == []
    with pytest.raises(ValueError, match="main ClassLoader"):
        java.unload_class_loader(main)
    with pytest.raises(TypeError, match="not a java.lang.ClassLoader"):
        java.unload_class_loader(object())


def test_music_speed_reaches_the_pitch_overload_plugins_hook():
    with open(VIDEO_PLAYER, encoding="utf-8") as fh:
        player = fh.read()
    with open(MEDIA_CONTROLLER, encoding="utf-8") as fh:
        controller = fh.read()
    assert re.search(r"public void setPlaybackSpeed\(float \w+, boolean \w+\)", player)
    music_calls = re.findall(
        r"audioPlayer\.setPlaybackSpeed\(Math\.round\((?:speed|currentMusicPlaybackSpeed) "
        r"\* 10f\) / 10f, ([^)]+)\)", controller)
    assert music_calls and set(music_calls) == {"false"}


def test_proguard_keeps_the_media3_aux_effect_api():
    with open(PROGUARD_RULES, encoding="utf-8") as fh:
        rules = fh.read()
    block = re.search(r"-keepclassmembers class androidx\.media3\.exoplayer\.ExoPlayerImpl \{([^}]*)\}",
                      rules)
    assert block and "setAuxEffectInfo(androidx.media3.common.AuxEffectInfo)" in block.group(1)
