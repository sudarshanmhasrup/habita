#!/usr/bin/env bash
# Signs a release APK with Habita's release key and the rotation lineage, which tells Android the
# new key replaced the old debug key, so phones that have a debug-signed build update in place.
#
#   scripts/sign-apk.sh <unsigned.apk> <signed.apk>
#
# Needs HABITA_KEYSTORE (path to the .jks) and HABITA_KEYSTORE_PASSWORD. Only APK Signature Scheme
# v3 is used, which is why the app needs Android 9 or later.
set -euo pipefail

unsigned="$1"
signed="$2"
: "${HABITA_KEYSTORE:?Set HABITA_KEYSTORE to the release keystore}"
: "${HABITA_KEYSTORE_PASSWORD:?Set HABITA_KEYSTORE_PASSWORD}"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
apksigner="$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[ -x "$apksigner" ] || { echo "apksigner not found under $sdk/build-tools" >&2; exit 1; }
lineage="$(cd "$(dirname "$0")/.." && pwd)/android/signing/habita.lineage"

"$apksigner" sign \
    --min-sdk-version 28 \
    --v1-signing-enabled false \
    --v2-signing-enabled false \
    --ks "$HABITA_KEYSTORE" \
    --ks-key-alias habita \
    --ks-pass env:HABITA_KEYSTORE_PASSWORD \
    --lineage "$lineage" \
    --rotation-min-sdk-version 28 \
    --out "$signed" \
    "$unsigned"
"$apksigner" verify --min-sdk-version 28 "$signed"
echo "Signed $signed"