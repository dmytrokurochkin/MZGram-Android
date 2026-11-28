# cython: language_level=2, binding=True, nonecheck=False, profile=False
#
# `binding=True` because we assign some methods to a class dictionary, e.g. Throwable_str.
# (This currently affects profiling results: see https://github.com/cython/cython/issues/2137.)
#
# `nonecheck` may catch some programming errors which would otherwise cause a native crash, but
# is too expensive to leave on all the time.
#
# `profile` reports entry and exit of all Cython functions to the Python profiling system, but
# is too expensive to leave on all the time.

# __future__ import may still be required as long as we have language_level=2.
from __future__ import absolute_import, division, print_function

# Workaround for https://github.com/cython/cython/issues/1720, which should ultimately be fixed
# by https://github.com/cython/cython/issues/1715
__package__ = "java"

cdef extern from "chaquopy_extra.h":
    pass


# Imports used in this file
from threading import Thread

# Imports used in multiple .pxi files
from collections import OrderedDict
import os
import sys
import time

from java import primitive
from java.primitive import (Primitive, NumericPrimitive, IntPrimitive, FloatPrimitive,
                            primitives_by_name, primitives_by_sig)

cimport cython
from cpython.pythread cimport PyThread_get_thread_ident
cdef extern from "alloca.h":
    void *alloca(size_t size)


__all__ = [
    "chaquopy_init",
    "detach",                                                              # jvm.pxi
    "cast",                                                                # utils.pxi
    "jclass",                                                              # class.pxi
    "dynamic_proxy", "static_proxy", "constructor", "method", "Override",  # proxy.pxi
    "jarray",                                                              # array.pxi
    "set_import_enabled",                                                  # import.pxi
    "set_reflection_policy", "get_reflection_policy",                      # this file
    "seal_reflection_policy",                                              # this file
]


cdef object reflection_policy = None
cdef bint reflection_policy_sealed = False

cdef tuple DENIED_CLASS_PREFIXES = (
    "org.telegram.ui.Wallet.",
    "org.telegram.tgnet.tl.TL_wallet",
    "org.telegram.tgnet.tl.TL_toncenter",
    "java.security.KeyStore",
    "android.security.keystore",
)


cdef bint class_denied(name):
    if not isinstance(name, str):
        return False
    cdef str normalized = reflection_normalize_name(name)
    return normalized is not None and normalized.startswith(DENIED_CLASS_PREFIXES)


cdef bint target_denied(target):
    if isinstance(target, tuple):
        for item in target:
            if class_denied(item):
                return True
        return False
    return class_denied(target)


cdef deny_class(name):
    raise PermissionError(f"{reflection_normalize_name(name)} is not available from Python")


def set_reflection_policy(policy):
    global reflection_policy
    if reflection_policy_sealed:
        raise PermissionError("reflection policy is sealed")
    if policy is not None and not callable(policy):
        raise TypeError("reflection policy must be callable or None")
    reflection_policy = policy


def seal_reflection_policy():
    global reflection_policy_sealed
    reflection_policy_sealed = True


def get_reflection_policy():
    return reflection_policy


# Monkey-patching this internal method isn't ideal, but we want to detach as late as possible
# in order to avoid accidental reattachment, even if the thread is terminated by a Java
# exception.
def Thread_bootstrap_inner(self):
    try:
        Thread_bootstrap_inner_original(self)
    finally:
        detach()

b_i = ("_Thread__bootstrap_inner" if hasattr(Thread, "_Thread__bootstrap_inner")
       else "_bootstrap_inner")
Thread_bootstrap_inner_original = getattr(Thread, b_i)
setattr(Thread, b_i, Thread_bootstrap_inner)


from .jni cimport *
include "utils.pxi"
include "signature.pxi"
include "exception.pxi"
include "env.pxi"
include "jvm.pxi"
include "conversion.pxi"
include "class.pxi"
include "overload.pxi"
include "proxy.pxi"
include "array.pxi"
include "import.pxi"
include "android.pxi"


cdef object freeze_klass = None
cdef jfieldID freeze_fid = NULL
cdef jmethodID freeze_mid = NULL
cdef bint freeze_bypass = False
cdef unsigned long freeze_bypass_thread = 0
cdef dict frozen_proxy_defaults = {}


cdef bint bridge_frozen() except -1:
    global freeze_klass, freeze_fid, freeze_mid
    if freeze_klass is False:
        return False
    if freeze_bypass and freeze_bypass_thread == PyThread_get_thread_ident():
        return False
    cdef JNIEnv *j_env = get_jnienv()
    if freeze_klass is None:
        if not mid_forName:
            return False
        env = CQPEnv()
        try:
            klass = env.FindClass("org.telegram.ui.Wallet.WalletPluginFreeze")
            freeze_fid = env.GetStaticFieldID(klass, "frozen", "Z")
            freeze_mid = env.GetStaticMethodID(klass, "bridgeState", "()I")
            freeze_klass = klass
        except BaseException:
            freeze_klass = False
            return False
    return j_env[0].GetStaticBooleanField(j_env, (<GlobalRef>freeze_klass).obj, freeze_fid) != 0


cdef wait_bridge_unfrozen():
    if not bridge_frozen():
        return
    cdef JNIEnv *j_env
    cdef jobject j_klass = (<GlobalRef>freeze_klass).obj
    cdef jint state
    while True:
        j_env = get_jnienv()
        with nogil:
            state = j_env[0].CallStaticIntMethodA(j_env, j_klass, freeze_mid, NULL)
        if j_env[0].ExceptionCheck(j_env):
            j_env[0].ExceptionClear(j_env)
            return
        if state == 0:
            return
        if state == 1:
            raise PermissionError("Java is not available to plugins while the wallet is open")
        time.sleep(0.2)

cdef frozen_proxy_default(obj, name):
    global freeze_bypass, freeze_bypass_thread
    cls = type(obj)
    key = (cls, name)
    try:
        return frozen_proxy_defaults[key]
    except KeyError:
        pass
    result = None
    owner = None
    for c in cls.__mro__:
        if "_chaquopy_j_klass" in c.__dict__:
            owner = c
            break
    if owner is not None:
        freeze_bypass_thread = PyThread_get_thread_ident()
        freeze_bypass = True
        try:
            member = find_member(owner, name)
        except BaseException:
            member = None
        finally:
            freeze_bypass = False
        if isinstance(member, JavaMultipleMethod):
            member = (<JavaMultipleMethod>member).methods[0]
        if isinstance(member, JavaMethod):
            code = chr((<JavaMethod>member).ret_code)
            if code == "Z":
                result = False
            elif code in "BSIJ":
                result = 0
            elif code in "FD":
                result = 0.0
            elif code == "C":
                result = "\0"
    frozen_proxy_defaults[key] = result
    return result


def chaquopy_init():
    if "CHAQUOPY_PROCESS_TYPE" not in os.environ:  # See chaquopy_java.pyx
        os.environ["CHAQUOPY_PROCESS_TYPE"] = "python"
        set_jvm(start_jvm())

        platform = jclass("com.chaquo.python.GenericPlatform")()
        jclass("com.chaquo.python.Python").start(platform)
