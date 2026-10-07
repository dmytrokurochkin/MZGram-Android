#!/usr/bin/env bash
# Checks on the emulator (API 34) that the key rotation of mzgram_sign.sh lets an
# installed app signed with the old key update to the MZGram key without losing data.
# Called from the "MZGram instrumented tests" job after the tests.
#
# Uses a throwaway key made here, never the release key, so it runs on every push.
#   1. install the APK signed with the old key, write a file into the app's data
#   2. an APK signed with the new key alone must be refused (the control: without
#      the lineage, Android would make the user uninstall and lose the data)
#   3. the APK from mzgram_sign.sh (old key -> new key) installs as an update, file kept
#   4. after that an APK signed with the new key alone installs too, file kept
#   5. the old key alone is refused now: it cannot push updates any more
set -uo pipefail
trap 'echo "::error::Key rotation check stopped at line $LINENO: $BASH_COMMAND"' ERR

PKG=org.telegram.messenger.web
MARKER=files/mzgram-rotation-marker
BUILD_TOOLS=${BUILD_TOOLS:-$ANDROID_HOME/build-tools/36.0.0}
APK=$(find TMessagesProj_AppTests/build/outputs/apk/afat/debug -name '*.apk' | head -1)

fail() {
    echo "::error::Key rotation check: $*"
    exit 1
}
[ -n "$APK" ] || fail "no debug APK"
echo "APK: $APK"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

export MZGRAM_KEYSTORE=$WORK/throwaway.jks
export MZGRAM_KEYSTORE_PASSWORD=rotation-check
export MZGRAM_KEY_ALIAS=mzgram
keytool -genkeypair -keystore "$MZGRAM_KEYSTORE" -storetype PKCS12 -alias mzgram -keyalg RSA -keysize 2048     -validity 365 -dname "CN=MZGram rotation check" -storepass:env MZGRAM_KEYSTORE_PASSWORD >/dev/null 2>&1 ||
    fail "keytool could not make the throwaway key"

bash .github/scripts/mzgram_sign.sh "$APK" "$WORK/rotated.apk" "$WORK/rotated.lineage" || fail "mzgram_sign.sh failed"
"$BUILD_TOOLS/apksigner" sign --in "$APK" --out "$WORK/new-only.apk" --v4-signing-enabled false     --ks "$MZGRAM_KEYSTORE" --ks-key-alias mzgram --ks-pass env:MZGRAM_KEYSTORE_PASSWORD     --key-pass env:MZGRAM_KEYSTORE_PASSWORD || fail "could not sign the new-key-only APK"
echo "APKs signed"

install() { adb install -r "$1" 2>&1 | tr -d '\r'; }
marker() { adb shell "run-as $PKG cat $MARKER" 2>&1 | tr -d '\r'; }

adb uninstall "$PKG" >/dev/null 2>&1 || true
out=$(install "$APK")
echo "old key: $out"
grep -q Success <<<"$out" || fail "the old-key APK did not install"
out=$(adb shell "run-as $PKG sh -c 'mkdir -p files && echo kept > $MARKER'" 2>&1 | tr -d '\r')
[ -z "$out" ] || echo "run-as: $out"
[ "$(marker)" = kept ] || fail "could not write into the app's data: $(marker)"
echo "ok: development-key install with a file in its data"

out=$(install "$WORK/new-only.apk")
echo "new key alone: $out"
grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$out" || fail "the new key without the lineage was not refused"
echo "ok: new key alone refused over the development key"

out=$(install "$WORK/rotated.apk")
echo "rotated: $out"
grep -q Success <<<"$out" || fail "the rotated APK did not install as an update"
[ "$(marker)" = kept ] || fail "data lost on the rotated update"
echo "ok: rotated APK installed as an update, data kept"

out=$(install "$WORK/new-only.apk")
echo "new key alone after the rotation: $out"
grep -q Success <<<"$out" || fail "the new key alone did not install after the rotation"
[ "$(marker)" = kept ] || fail "data lost on the new-key update"
echo "ok: after the rotation the new key alone installs, data kept"

out=$(install "$APK")
echo "development key after the rotation: $out"
grep -q Success <<<"$out" && fail "the development key can still update the app"
echo "ok: the development key alone is refused after the rotation"

adb uninstall "$PKG" >/dev/null 2>&1 || true
echo "Key rotation check passed"
