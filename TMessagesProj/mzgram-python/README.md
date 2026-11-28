# MZGram Python runtime

The native part of the Python ↔ Java bridge that runs MZGram plugins: `chaquopy.so` and
`libchaquopy_java.so`. The app builds both libraries from the sources in this folder on every
build, so the APK contains exactly this code. The sources live in this repository, not in a
submodule.

The bridge is based on Chaquopy 17.0.0 (MIT licence, see `LICENSE.txt`). Only these two native
libraries are built here. The Gradle plugin, the Java classes and the Python builds come from the
stock `com.chaquo.python` 17.0.0 packages on Maven Central, and they expect these exact file names,
so the library names stay as they are.

## What differs from stock 17.0.0

- Faster method calls, attribute access and object wrapping: Cython-level caches for classes,
  members and wrappers, and argument conversion without intermediate tuples.
- JNI reference fixes: dynamic proxy wrappers and their weak references are released right after
  use instead of waiting for Java or Python finalization.
- A settable reflection policy (`set_reflection_policy`) that the app uses to restrict reflective
  calls from plugins, and denied wallet and keystore classes.
- `GuardedMethod` and a constructor call fallback used by the plugin SDK (`mz_utils/plugin_loader.py`).
- Python access to Java can be paused while a wallet screen is open; proxy methods called during the
  pause return their declared default.

## Layout

| Path | What it is |
|---|---|
| `build.sh` | builds both libraries for one ABI |
| `runtime/CMakeLists.txt` | native build of the two libraries |
| `runtime/src/main/c/` | C helpers |
| `runtime/src/main/python/chaquopy_java.pyx` | JNI entry points |
| `runtime/src/main/python/java/` | the bridge in Cython (`chaquopy.pyx` and its `.pxi`/`.pxd` parts) |

## Building

The Gradle task `buildMzgramPythonRuntime<Abi>` runs `build.sh` and sets every variable itself.
By hand it needs bash, Python 3, the Android NDK, CMake and the Python target archive
(`com.chaquo.python:target`):

```sh
ABI=arm64-v8a OUT=out/arm64-v8a TMessagesProj/mzgram-python/build.sh
```

| Variable | Default |
|---|---|
| `ABI` | `arm64-v8a` |
| `OUT` | `$WORKSPACE/out/$ABI` |
| `WORKSPACE` | `TMessagesProj/build/mzgram-python` |
| `NDK` | `~/Android/Sdk/ndk/27.2.12479018` |
| `CMAKE` | `cmake` on `PATH`, else the newest one in the Android SDK |
| `PYTHON` | `python3`, used to create a venv with Cython 3.0.11 |
| `PYTHON_VERSION` | `3.11` |
| `PYTHON_TARGET_ZIP` | the target archive from the Gradle cache |
