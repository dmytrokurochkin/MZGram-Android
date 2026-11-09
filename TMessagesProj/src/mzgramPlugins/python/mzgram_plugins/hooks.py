# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Hooks of Java methods for plugins, Python side: what BasePlugin.hook_method,
# hook_all_methods, hook_all_constructors and unhook_method hand to the hook
# engine (MZGramPluginHooks on the Java side, by name with jclass), and the
# classes a plugin hooks with:
#
#   class Hook(MethodHook):
#       def before_hooked_method(self, param):
#           param.args[0] = "changed"
#       def after_hooked_method(self, param):
#           param.result = param.getResult() + 1
#
#   self.hook_method(member, Hook())                    # a Method or Constructor
#   self.hook_method((clazz, "name"), Hook())           # declared methods by name
#   self.hook_method(member, before=lambda param: ...)  # functions instead
#
# A MethodReplacement (replace_hooked_method) runs instead of the method and
# its return value is the result. HookFilter descriptors (filters=,
# before_filters=, after_filters=, or @hook_filters on the hook method) let
# the Java side skip calls before Python is entered; a "condition" filter is
# accepted but never passes in MZGram.
#
# The hook engine checks everything again (the plugin runs, "hooks" is
# granted, the method is not protected); what it refuses goes to the plugin's
# log and the call returns no ids. All hooks of a plugin come off when it
# stops (loader._take_back -> unhook_all). Without Java (tests on a
# computer) nothing is hooked.
#
# Names follow the exteraless plugin SDK, base_plugin.py (MethodHook,
# MethodReplacement, XposedHook, HookFilter, hook_filters, HookParam,
# UnhookId; GPL-3.0), so plugins written for it fit; written anew for
# MZGram.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import json
from typing import Any, Dict

HOOKS_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramPluginHooks"

DEFAULT_PRIORITY = 50

KIND_MEMBER = "member"
KIND_METHODS = "methods"
KIND_CONSTRUCTORS = "constructors"

BEFORE = "before_hooked_method"
AFTER = "after_hooked_method"
REPLACE = "replace_hooked_method"
FILTERS_ATTR = "__hook_filters__"


# ---------- Java ----------

_facade = None
_facade_resolved = False


def _java():
    global _facade, _facade_resolved
    if not _facade_resolved:
        _facade_resolved = True
        try:
            from java import jclass
            _facade = jclass(HOOKS_CLASS)
        except Exception:
            _facade = None
    return _facade


def _set_facade(facade):
    """Tests: a stand-in for the Java side (None: no Java)."""
    global _facade, _facade_resolved
    _facade = facade
    _facade_resolved = True


# ---------- what a plugin hooks with ----------

class HookParam:
    """What a hook gets: Xposed's MethodHookParam, with param.result and
    param.throwable besides getResult()/setResult(v) and the fields
    thisObject, args and method. param.java is the Java object itself."""

    __slots__ = ("_param",)

    def __init__(self, param):
        object.__setattr__(self, "_param", param)

    @property
    def java(self):
        return object.__getattribute__(self, "_param")

    def __getattr__(self, name):
        param = object.__getattribute__(self, "_param")
        if name == "result":
            return param.getResult()
        if name == "throwable":
            return param.getThrowable()
        return getattr(param, name)

    def __setattr__(self, name, value):
        param = object.__getattribute__(self, "_param")
        if name == "result":
            param.setResult(value)
        elif name == "throwable":
            param.setThrowable(value)
        else:
            setattr(param, name, value)

    def __repr__(self):
        return "HookParam(%r)" % (object.__getattribute__(self, "_param"),)


class BaseHook:
    """A hook handler; before= and after= may give the functions."""

    def __init__(self, xposed_hook=None, before=None, after=None,
                 before_filters=None, after_filters=None):
        if before is not None:
            self.before_hooked_method = before
        if after is not None:
            self.after_hooked_method = after


class MethodHook(BaseHook):
    """Runs before and/or after the Java method; override either."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class MethodReplacement(BaseHook):
    """Runs instead of the Java method; the return value is its result."""

    def replace_hooked_method(self, param):
        return None


# Several published plugins subclass this name; it is MethodHook
XposedHook = MethodHook


class _Functions:
    """The handler made of before=/after= functions."""


def _value(value):
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)


def _class_name(cls):
    if isinstance(cls, str):
        return cls
    try:
        return str(cls.getName())
    except Exception:
        pass
    try:
        return str(cls.__name__)
    except Exception:
        return str(cls)


class HookFilter:
    """A filter checked on the Java side before the hook is entered."""

    __slots__ = ("kind", "data")

    def __init__(self, kind: str, **data):
        self.kind = kind
        self.data = data

    def to_dict(self) -> Dict[str, Any]:
        out = {"type": self.kind}
        out.update(self.data)
        return out

    def __eq__(self, other):
        return isinstance(other, HookFilter) and self.to_dict() == other.to_dict()

    def __hash__(self):
        return hash((self.kind, repr(sorted(self.data.items()))))

    def __repr__(self):
        args = ", ".join("%s=%r" % item for item in self.data.items())
        return "HookFilter(%s%s)" % (self.kind, ", " + args if args else "")

    @staticmethod
    def argument_is_null(index: int) -> "HookFilter":
        return HookFilter("argument_is_null", index=int(index))

    @staticmethod
    def argument_not_null(index: int) -> "HookFilter":
        return HookFilter("argument_not_null", index=int(index))

    @staticmethod
    def argument_is_true(index: int) -> "HookFilter":
        return HookFilter("argument_is_true", index=int(index))

    @staticmethod
    def argument_is_false(index: int) -> "HookFilter":
        return HookFilter("argument_is_false", index=int(index))

    @staticmethod
    def argument_equal(index: int, value) -> "HookFilter":
        return HookFilter("argument_equal", index=int(index), value=_value(value))

    @staticmethod
    def argument_not_equal(index: int, value) -> "HookFilter":
        return HookFilter("argument_not_equal", index=int(index), value=_value(value))

    @staticmethod
    def argument_is_instance_of(index: int, cls) -> "HookFilter":
        return HookFilter("argument_is_instance_of", index=int(index), **{"class": _class_name(cls)})

    @staticmethod
    def result_is_null() -> "HookFilter":
        return HookFilter("result_is_null")

    @staticmethod
    def result_not_null() -> "HookFilter":
        return HookFilter("result_not_null")

    @staticmethod
    def result_is_true() -> "HookFilter":
        return HookFilter("result_is_true")

    @staticmethod
    def result_is_false() -> "HookFilter":
        return HookFilter("result_is_false")

    @staticmethod
    def result_equal(value) -> "HookFilter":
        return HookFilter("result_equal", value=_value(value))

    @staticmethod
    def result_not_equal(value) -> "HookFilter":
        return HookFilter("result_not_equal", value=_value(value))

    @staticmethod
    def result_is_instance_of(cls) -> "HookFilter":
        return HookFilter("result_is_instance_of", **{"class": _class_name(cls)})

    @staticmethod
    def condition(expression: str, object=None) -> "HookFilter":
        """Accepted for plugins written for exteraless; never passes in MZGram."""
        data = {"expression": str(expression)}
        if object is not None:
            data["object"] = _value(object)
        return HookFilter("condition", **data)

    @staticmethod
    def or_(*filters) -> "HookFilter":
        return HookFilter("or", filters=[f.to_dict() if hasattr(f, "to_dict") else f for f in filters])


HookFilter.ArgumentIsNull = HookFilter.argument_is_null
HookFilter.ArgumentNotNull = HookFilter.argument_not_null
HookFilter.ArgumentIsTrue = HookFilter.argument_is_true
HookFilter.ArgumentIsFalse = HookFilter.argument_is_false
HookFilter.ArgumentEqual = HookFilter.argument_equal
HookFilter.ArgumentNotEqual = HookFilter.argument_not_equal
HookFilter.ArgumentIsInstanceOf = HookFilter.argument_is_instance_of
HookFilter.ResultIsNull = HookFilter.result_is_null
HookFilter.ResultNotNull = HookFilter.result_not_null
HookFilter.ResultIsTrue = HookFilter.result_is_true
HookFilter.ResultIsFalse = HookFilter.result_is_false
HookFilter.ResultEqual = HookFilter.result_equal
HookFilter.ResultNotEqual = HookFilter.result_not_equal
HookFilter.ResultIsInstanceOf = HookFilter.result_is_instance_of
HookFilter.Condition = HookFilter.condition
HookFilter.Or = HookFilter.or_
HookFilter.RESULT_IS_NULL = HookFilter.result_is_null()
HookFilter.RESULT_NOT_NULL = HookFilter.result_not_null()
HookFilter.RESULT_IS_TRUE = HookFilter.result_is_true()
HookFilter.RESULT_IS_FALSE = HookFilter.result_is_false()


def hook_filters(*filters, before=None, after=None):
    """Decorator for a hook method: its filters (positional ones and
    before=[...] gate the call before the method, after=[...] the one after)."""
    def decorator(fn):
        setattr(fn, FILTERS_ATTR, {"before": [*(before or ()), *filters], "after": list(after or ())})
        return fn
    return decorator


class UnhookId(str):
    """The id of one hook; .unhook() takes it off."""

    __slots__ = ("_owner",)

    def __new__(cls, value, owner=None):
        obj = super().__new__(cls, value)
        obj._owner = owner
        return obj

    def unhook(self):
        if self._owner is not None:
            self._owner.unhook_method(str(self))


class UnhookIdList(list):
    """The ids of several hooks; .unhook() takes them all off."""

    def __init__(self, ids=(), owner=None):
        super().__init__(ids)
        self._owner = owner

    def unhook(self):
        if self._owner is not None:
            self._owner.unhook_method(list(self))


# ---------- building a hook ----------

def _own(handler, attr):
    """The handler's method attr, unless it is missing or the empty one of
    MethodHook/MethodReplacement (calling that would cost a Python call for
    nothing)."""
    fn = getattr(handler, attr, None)
    if fn is None or not callable(fn):
        return None
    for base in (MethodHook, MethodReplacement):
        empty = base.__dict__.get(attr)
        if empty is not None and getattr(fn, "__func__", None) is empty:
            return None
    return fn


def _with_param(fn):
    def call(param):
        return fn(HookParam(param))
    return call


def callables(handler=None, before=None, after=None):
    """(before, after, replace, handler): the functions for the Java side
    (None where there is none) and the handler they came from; raises
    TypeError when nothing would be called."""
    if handler is not None and not any(hasattr(handler, attr) for attr in (BEFORE, AFTER, REPLACE)):
        if not callable(handler):
            raise TypeError("the handler must be a MethodHook, a MethodReplacement or a function")
        if before is not None:
            raise TypeError("give either a handler function or before=, not both")
        before, handler = handler, None
    if handler is None:
        if before is None and after is None:
            raise TypeError("a hook needs a handler or before=/after= functions")
        handler = _Functions()
        if before is not None:
            handler.before_hooked_method = before
        if after is not None:
            handler.after_hooked_method = after
    replace = _own(handler, REPLACE)
    if replace is not None:
        return None, None, _with_param(replace), handler
    first = _own(handler, BEFORE)
    last = _own(handler, AFTER)
    if first is None and last is None:
        raise TypeError("the handler overrides none of before_hooked_method, after_hooked_method, replace_hooked_method")
    return (_with_param(first) if first else None), (_with_param(last) if last else None), None, handler


def filters_json(handler=None, filters=None, before_filters=None, after_filters=None) -> str:
    before = []
    after = []
    for attr, bucket in ((BEFORE, before), (REPLACE, before), (AFTER, after)):
        spec = getattr(getattr(handler, attr, None), FILTERS_ATTR, None)
        if spec:
            before.extend(spec.get("before") or ())
            after.extend(spec.get("after") or ())
    before.extend(filters or ())
    before.extend(before_filters or ())
    after.extend(after_filters or ())

    def dump(items):
        return [f.to_dict() if hasattr(f, "to_dict") else f for f in items]
    return json.dumps({"before": dump(before), "after": dump(after)}, ensure_ascii=False)


def resolve_class(clazz):
    """A class name, a jclass or a java.lang.Class; LookupError when the name
    is not a class of the app."""
    if isinstance(clazz, str):
        facade = _java()
        found = facade.findClass(clazz) if facade is not None else None
        if found is None:
            raise LookupError("no class %s" % clazz)
        return found
    if clazz is None:
        raise LookupError("no class")
    return clazz


def _describe(kind, target, name):
    if kind == KIND_MEMBER:
        try:
            return "%s.%s" % (target.getDeclaringClass().getSimpleName(), target.getName())
        except Exception:
            return str(target)
    text = _class_name(target).rsplit(".", 1)[-1]
    return text + "." + (name if kind == KIND_METHODS else "<init>")


def install(plugin_id, kind, target, name=None, handler=None, before=None, after=None,
            priority=DEFAULT_PRIORITY, filters=None, before_filters=None, after_filters=None, log=None) -> list:
    """Hooks and returns the ids of the hooks that went on; every refusal
    and error goes to log (the plugin's log)."""
    def tell(text):
        if log is not None:
            try:
                log(text)
            except Exception:
                pass

    what = "hook"
    try:
        what = "hook %s" % _describe(kind, target, name)
        first, last, replace, handler = callables(handler, before, after)
        spec = filters_json(handler, filters, before_filters, after_filters)
        facade = _java()
        if facade is None:
            tell(what + ": hooks need MZGram on Android")
            return []
        if kind != KIND_MEMBER:
            target = resolve_class(target)
        if kind == KIND_MEMBER:
            raw = facade.hookMember(plugin_id, target, first, last, replace, int(priority), spec)
        elif kind == KIND_METHODS:
            raw = facade.hookMethods(plugin_id, target, str(name), first, last, replace, int(priority), spec)
        elif kind == KIND_CONSTRUCTORS:
            raw = facade.hookConstructors(plugin_id, target, first, last, replace, int(priority), spec)
        else:
            raise ValueError("unknown kind %r" % (kind,))
        reply = json.loads(str(raw)) if raw is not None else {}
    except Exception as e:
        tell("%s failed: %s: %s" % (what, type(e).__name__, e))
        return []
    for denied in reply.get("denied") or ():
        tell("%s: may not hook %s" % (what, denied))
    if reply.get("error"):
        tell("%s failed: %s" % (what, reply["error"]))
    if reply.get("warning"):
        tell("%s: %s" % (what, reply["warning"]))
    return [str(x) for x in reply.get("ids") or ()]


def unhook(plugin_id, ids, log=None):
    facade = _java()
    if facade is None:
        return
    for unhook_id in ids:
        if unhook_id is None:
            continue
        try:
            facade.unhook(plugin_id, str(unhook_id))
        except Exception as e:
            if log is not None:
                log("unhook_method(%r) failed: %s" % (str(unhook_id), e))


def unhook_all(plugin_id):
    """Every hook of the plugin comes off; never raises."""
    facade = _java()
    if facade is None:
        return 0
    try:
        return int(facade.unhookAll(plugin_id))
    except Exception:
        return 0
