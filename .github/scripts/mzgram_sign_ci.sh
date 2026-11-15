#!/usr/bin/env bash
# CI wrapper of mzgram_sign.sh: takes the MZGram key from GitHub Secrets.
#
#   mzgram_sign_ci.sh <in.apk> <out.apk> <out.lineage>
#
# Secrets (env): ANDROID_KEYSTORE_B64 (the keystore file in base64),
# ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD (optional,
# a PKCS12 keystore uses the keystore password for the key).
# Without them the APK stays signed with the public development key, and the
# step outputs signed=false, so nothing is published as a stable release.
# Step outputs: signed=true|false, cert_sha256=<certificate SHA-256 Android 9+ checks>.
set -euo pipefail

IN=$1
OUT=$2
LINEAGE=$3
OUTPUT=${GITHUB_OUTPUT:-/dev/null}
BUILD_TOOLS=${BUILD_TOOLS:-$ANDROID_HOME/build-tools/36.0.0}

if [ -z "${ANDROID_KEYSTORE_B64:-}" ] || [ -z "${ANDROID_KEYSTORE_PASSWORD:-}" ] || [ -z "${ANDROID_KEY_ALIAS:-}" ]; then
    echo "::warning::No MZGram signing key in GitHub Secrets: the APK is signed with the public development key and must not be published as a stable release."
    cp "$IN" "$OUT"
    rm -f "$LINEAGE"
    echo "signed=false" >>"$OUTPUT"
    echo "cert_sha256=" >>"$OUTPUT"
    exit 0
fi

# The keystore lives only in the runner's temp directory, readable by this user, for this step
umask 077
KEYDIR=$(mktemp -d "${RUNNER_TEMP:-/tmp}/mzgram-key.XXXXXX")
trap 'rm -rf "$KEYDIR"' EXIT
printf '%s' "$ANDROID_KEYSTORE_B64" | base64 -d >"$KEYDIR/mzgram.jks" ||
    { echo "::error::ANDROID_KEYSTORE_B64 is not valid base64"; exit 1; }

export MZGRAM_KEYSTORE=$KEYDIR/mzgram.jks
export MZGRAM_KEYSTORE_PASSWORD=$ANDROID_KEYSTORE_PASSWORD
export MZGRAM_KEY_ALIAS=$ANDROID_KEY_ALIAS
export MZGRAM_KEY_PASSWORD=${ANDROID_KEY_PASSWORD:-$ANDROID_KEYSTORE_PASSWORD}
bash "$(dirname "$0")/mzgram_sign.sh" "$IN" "$OUT" "$LINEAGE"

digest=$("$BUILD_TOOLS/apksigner" verify --min-sdk-version 28 --print-certs "$OUT" |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
echo "signed=true" >>"$OUTPUT"
echo "cert_sha256=$digest" >>"$OUTPUT"
