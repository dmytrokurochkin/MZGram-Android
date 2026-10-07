#!/usr/bin/env bash
# Signs an MZGram APK with the MZGram key, rotating from the old key.
#
#   mzgram_sign.sh <in.apk> <out.apk> <out.lineage>
#
# Builds before the MZGram key were signed with TMessagesProj/config/release.keystore
# (upstream's public key, password "android"). An installed app accepts an update
# only from the same key or from a key its lineage names as the successor, so the
# APK carries a signing lineage old key -> MZGram key (APK Signature Scheme v3):
#   - Android 9+ (API 28) checks the MZGram key and records the lineage, so the
#     update keeps all data, and later APKs signed with the MZGram key alone install too;
#   - Android 5 to 8 has no key rotation and still checks the old key (v1/v2).
#
# The MZGram key comes from the environment (GitHub Secrets in CI), never from the repo:
#   MZGRAM_KEYSTORE           path to the keystore file
#   MZGRAM_KEYSTORE_PASSWORD  keystore password
#   MZGRAM_KEY_ALIAS          key alias
#   MZGRAM_KEY_PASSWORD       key password (defaults to the keystore password)
# Passwords go to apksigner and keytool as env: references, never as arguments.
set -euo pipefail

IN=$1
OUT=$2
LINEAGE=$3

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OLD_KS=$ROOT/TMessagesProj/config/release.keystore
OLD_ALIAS=androidkey
export MZGRAM_OLD_PASSWORD=android
: "${MZGRAM_KEYSTORE:?}" "${MZGRAM_KEYSTORE_PASSWORD:?}" "${MZGRAM_KEY_ALIAS:?}"
export MZGRAM_KEY_PASSWORD=${MZGRAM_KEY_PASSWORD:-$MZGRAM_KEYSTORE_PASSWORD}

BUILD_TOOLS=${BUILD_TOOLS:-$ANDROID_HOME/build-tools/36.0.0}
APKSIGNER=$BUILD_TOOLS/apksigner

# SHA-256 of a key's certificate, as apksigner prints it
cert_digest() {
    keytool -exportcert -keystore "$1" -alias "$2" -storepass:env "$3" 2>/dev/null |
        sha256sum | cut -d' ' -f1
}

OLD_DIGEST=$(cert_digest "$OLD_KS" "$OLD_ALIAS" MZGRAM_OLD_PASSWORD)
NEW_DIGEST=$(cert_digest "$MZGRAM_KEYSTORE" "$MZGRAM_KEY_ALIAS" MZGRAM_KEYSTORE_PASSWORD) || NEW_DIGEST=""
if [ -z "$NEW_DIGEST" ] || [ "$NEW_DIGEST" = "$(printf '' | sha256sum | cut -d' ' -f1)" ]; then
    echo "::error::Cannot read the MZGram key: check the keystore, the alias and the password"
    exit 1
fi
if [ "$NEW_DIGEST" = "$OLD_DIGEST" ]; then
    echo "::error::The MZGram keystore holds the public upstream key"
    exit 1
fi

"$APKSIGNER" rotate --out "$LINEAGE" \
    --old-signer --ks "$OLD_KS" --ks-key-alias "$OLD_ALIAS" \
        --ks-pass env:MZGRAM_OLD_PASSWORD --key-pass env:MZGRAM_OLD_PASSWORD \
    --new-signer --ks "$MZGRAM_KEYSTORE" --ks-key-alias "$MZGRAM_KEY_ALIAS" \
        --ks-pass env:MZGRAM_KEYSTORE_PASSWORD --key-pass env:MZGRAM_KEY_PASSWORD

# --rotation-min-sdk-version 28: rotate on every Android that supports it
# (apksigner's default is 33, which would leave Android 9 to 12 on the old key)
"$APKSIGNER" sign --in "$IN" --out "$OUT" \
    --v4-signing-enabled false \
    --ks "$OLD_KS" --ks-key-alias "$OLD_ALIAS" \
        --ks-pass env:MZGRAM_OLD_PASSWORD --key-pass env:MZGRAM_OLD_PASSWORD \
    --next-signer --ks "$MZGRAM_KEYSTORE" --ks-key-alias "$MZGRAM_KEY_ALIAS" \
        --ks-pass env:MZGRAM_KEYSTORE_PASSWORD --key-pass env:MZGRAM_KEY_PASSWORD \
    --lineage "$LINEAGE" \
    --rotation-min-sdk-version 28

# What each Android version checks: the old key from the APK's minSdk up to 8,
# the MZGram key (and only it) from 9
expect_signer() {
    local want=$1 range=$2 certs
    shift 2
    certs=$("$APKSIGNER" verify "$@" --print-certs "$OUT")
    if ! grep -q "certificate SHA-256 digest: $want" <<<"$certs" ||
        [ "$(grep -c 'certificate SHA-256 digest' <<<"$certs")" != 1 ]; then
        echo "$certs"
        echo "::error::$range: the APK is not signed with the expected key alone"
        exit 1
    fi
}
expect_signer "$OLD_DIGEST" "Android 5 to 8" --max-sdk-version 27
expect_signer "$NEW_DIGEST" "Android 9+" --min-sdk-version 28
"$APKSIGNER" lineage --in "$LINEAGE" --print-certs >/dev/null

echo "Signed with the MZGram key, certificate SHA-256 $NEW_DIGEST (rotated from $OLD_DIGEST)"
