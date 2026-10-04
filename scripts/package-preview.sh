#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Personal preview only: optimized release code, signed with the local Android debug key.
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
export PATH="$JAVA_HOME/bin:$PATH"
PREVIEW_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$PREVIEW_SDK" && -f local.properties ]]; then
    PREVIEW_SDK="$(sed -n 's/^sdk.dir=//p' local.properties)"
fi
: "${PREVIEW_SDK:?Set ANDROID_HOME or configure sdk.dir in local.properties}"
PREVIEW_KEYSTORE="${PREVIEW_KEYSTORE:-${ANDROID_USER_HOME:-$HOME/.android}/debug.keystore}"
PREVIEW_SIGNER="$PREVIEW_SDK/build-tools/35.0.0/apksigner"
PREVIEW_EXPECTED_CERT="$(cat scripts/preview-signing-cert.sha256)"
./gradlew :app:assembleDebug :app:assembleRelease "$@"
mkdir -p dist
PREVIEW_TEMP_APK="$(mktemp dist/.signed-XXXXXX.apk)"
trap 'rm -f "$PREVIEW_TEMP_APK" "$PREVIEW_TEMP_APK.idsig"' EXIT
"$PREVIEW_SIGNER" sign --ks "$PREVIEW_KEYSTORE" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out "$PREVIEW_TEMP_APK" app/build/outputs/apk/release/app-release-unsigned.apk
PREVIEW_ACTUAL_CERT="$("$PREVIEW_SIGNER" verify --print-certs "$PREVIEW_TEMP_APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [[ "$PREVIEW_ACTUAL_CERT" != "$PREVIEW_EXPECTED_CERT" ]]; then
    echo 'Signing certificate differs from v1. Refusing to produce an incompatible update.' >&2
    exit 1
fi
"$PREVIEW_SIGNER" verify --verbose "$PREVIEW_TEMP_APK"
mv "$PREVIEW_TEMP_APK" dist/grandeTrend-v1.3.apk
if [[ -f "$PREVIEW_TEMP_APK.idsig" ]]; then mv "$PREVIEW_TEMP_APK.idsig" dist/grandeTrend-v1.3.apk.idsig; fi
sha256sum dist/grandeTrend-v*.apk > dist/SHA256SUMS
