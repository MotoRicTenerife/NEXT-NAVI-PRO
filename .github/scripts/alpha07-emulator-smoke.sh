#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.nextnavipro.app.debug"

adb wait-for-device
adb logcat -c
adb install -r "$APK"
adb shell monkey -p "$PACKAGE" 1 >/dev/null

# Wait for the app process, not for unrelated Android services such as com.android.phone.
started=0
for attempt in $(seq 1 45); do
  if adb shell pidof "$PACKAGE" 2>/dev/null | grep -q '[0-9]'; then
    started=1
    break
  fi
  sleep 2
done

if [[ "$started" -ne 1 ]]; then
  echo "Alpha07 process did not start."
  adb logcat -d -t 2500 | tail -n 500
  exit 1
fi

sleep 8
if ! adb shell dumpsys activity activities | grep -q "$PACKAGE"; then
  echo "Alpha07 process exists but its Activity is not present in the activity stack."
  adb logcat -d -t 2500 | tail -n 500
  exit 1
fi
if adb logcat -d -t 2500 | grep -E "FATAL EXCEPTION|Process: $PACKAGE" | grep -q "FATAL EXCEPTION"; then
  echo "Fatal exception detected in Alpha07."
  adb logcat -d -t 2500 | tail -n 500
  exit 1
fi

# Rotate the display while keeping the app alive.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
adb shell settings put system user_rotation 0
sleep 3
if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
  echo "Alpha07 stopped after display rotation."
  adb logcat -d -t 2500 | tail -n 500
  exit 1
fi

adb shell screencap -p /sdcard/alpha07-smoke.png
adb pull /sdcard/alpha07-smoke.png alpha07-emulator-smoke.png
test -s alpha07-emulator-smoke.png
echo "PASS: Alpha07 installed, its Activity entered the Android task stack, survived rotation and produced a non-empty screenshot."
