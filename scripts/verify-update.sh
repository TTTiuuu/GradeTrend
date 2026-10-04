#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Destructive fixtures, restricted to an explicitly named emulator. Never run on a user's phone.
UPDATE_DEVICE="${1:?Usage: verify-update.sh emulator-serial}"
[[ "$UPDATE_DEVICE" == emulator-* ]] || { echo 'A dedicated emulator is required.' >&2; exit 1; }
UPDATE_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' local.properties)}}"
UPDATE_ADB="$UPDATE_SDK/platform-tools/adb"
[[ "$("$UPDATE_ADB" -s "$UPDATE_DEVICE" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Device is not an emulator.' >&2; exit 1; }
UPDATE_OLD="${2:-dist/grandeTrend-v1.2.apk}"
UPDATE_NEW="${3:-dist/grandeTrend-v1.3.apk}"
UPDATE_OLD_VERSION="${4:-3}"
UPDATE_NEW_VERSION="${5:-4}"
UPDATE_REPORT="${6:-docs/verification/v1.3}"
[[ -f "$UPDATE_OLD" && -f "$UPDATE_NEW" && -f verification/build/outputs/apk/debug/verification-debug.apk ]]
mkdir -p "$UPDATE_REPORT"

"$UPDATE_ADB" -s "$UPDATE_DEVICE" uninstall com.grandetrend.app > /dev/null
"$UPDATE_ADB" -s "$UPDATE_DEVICE" install "$UPDATE_OLD"
"$UPDATE_ADB" -s "$UPDATE_DEVICE" install -r -t verification/build/outputs/apk/debug/verification-debug.apk
"$UPDATE_ADB" -s "$UPDATE_DEVICE" shell am start -W -n com.grandetrend.app/.MainActivity > /dev/null
sleep 2
"$UPDATE_ADB" -s "$UPDATE_DEVICE" shell am force-stop com.grandetrend.app
"$UPDATE_ADB" -s "$UPDATE_DEVICE" shell am instrument -w -e phase seed -e expectedVersion "$UPDATE_OLD_VERSION" \
    com.grandetrend.verification/.UpdateInstrumentation > "$UPDATE_REPORT/update-seed.txt"
rg -q 'retention=passed' "$UPDATE_REPORT/update-seed.txt"

# The actual user update: no uninstall, no pm clear, no migration of files outside the app.
"$UPDATE_ADB" -s "$UPDATE_DEVICE" install -r "$UPDATE_NEW"
"$UPDATE_ADB" -s "$UPDATE_DEVICE" shell am start -W -n com.grandetrend.app/.MainActivity > /dev/null
sleep 2
"$UPDATE_ADB" -s "$UPDATE_DEVICE" shell am force-stop com.grandetrend.app
"$UPDATE_ADB" -s "$UPDATE_DEVICE" shell am instrument -w -e phase verify -e expectedVersion "$UPDATE_NEW_VERSION" \
    com.grandetrend.verification/.UpdateInstrumentation > "$UPDATE_REPORT/update-verify.txt"
rg -q 'retention=passed' "$UPDATE_REPORT/update-verify.txt"
echo "$UPDATE_OLD to $UPDATE_NEW overwrite installation: all grade and preference values retained."
