#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner after the AVD has booted.
# Verifies Android services, installs the already-signed CI APK, launches MainActivity,
# checks ActivityManager's result and confirms the app process remains alive.
set -Eeuo pipefail

APK="${APK_PATH:?APK_PATH must point to the staged, signature-verified APK}"
PKG="${APP_PACKAGE:-com.nextnavipro.app.debug}"
ACT="${APP_ACTIVITY:-com.nextnavipro.app.MainActivity}"
OUT="${DIAG_DIR:-ci-diagnostics}"
SERVICES_TIMEOUT_S="${SERVICES_TIMEOUT_S:-240}"
ADB=(adb -e)

mkdir -p "$OUT"

collect() {
  # Diagnostics must never replace the original test exit status.
  set +e
  mkdir -p "$OUT"
  echo "::group::Android emulator diagnostics"
  {
    echo "--- UTC"; date -u
    echo "--- runner"; id; nproc; free -m
    echo "--- KVM"; ls -l /dev/kvm
    echo "--- adb"; adb version; adb devices -l
    echo "--- APK"; ls -l "$APK"; sha256sum "$APK"
  } > "$OUT/host-adb-apk.txt" 2>&1
  "${ADB[@]}" logcat -b all -d -v threadtime > "$OUT/logcat.txt" 2>&1
  "${ADB[@]}" shell getprop > "$OUT/getprop.txt" 2>&1
  "${ADB[@]}" shell service list > "$OUT/service-list.txt" 2>&1
  "${ADB[@]}" shell ps -A > "$OUT/processes.txt" 2>&1
  "${ADB[@]}" shell dumpsys activity processes > "$OUT/activity-processes.txt" 2>&1
  "${ADB[@]}" shell dumpsys package "$PKG" > "$OUT/package-dump.txt" 2>&1
  "${ADB[@]}" shell pidof "$PKG" > "$OUT/final-pid.txt" 2>&1
  echo "Diagnostic files written to $OUT"
  echo "::endgroup::"
  return 0
}
trap collect EXIT

fail() {
  echo "::error::$*"
  exit 1
}

echo "Checking that exactly one Android emulator is available."
adb devices -l
STATE="$( "${ADB[@]}" get-state 2>&1 | tr -d '\r' )"
[ "$STATE" = "device" ] || fail "ADB emulator state is '$STATE', expected 'device'."
[ -s "$APK" ] || fail "Staged APK is missing or empty: $APK"

echo "Waiting for Android package and activity services (max ${SERVICES_TIMEOUT_S}s)."
deadline=$(( $(date +%s) + SERVICES_TIMEOUT_S ))
ready=0
boot=""; package_service=""; activity_service=""
while [ "$(date +%s)" -lt "$deadline" ]; do
  boot="$( "${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true )"
  package_service="$( "${ADB[@]}" shell service check package 2>/dev/null | tr -d '\r' || true )"
  activity_service="$( "${ADB[@]}" shell service check activity 2>/dev/null | tr -d '\r' || true )"
  if [ "$boot" = "1" ] &&
     [[ "$package_service" == *"found"* && "$package_service" != *"not found"* ]] &&
     [[ "$activity_service" == *"found"* && "$activity_service" != *"not found"* ]]; then
    ready=1
    break
  fi
  sleep 3
done
[ "$ready" = "1" ] || fail "Android services not ready: boot='$boot', package='$package_service', activity='$activity_service'."

echo "Android services are ready. Installing the exact staged APK: $APK"
if ! install_output="$( "${ADB[@]}" install --no-streaming -r -t "$APK" 2>&1 )"; then
  printf '%s\n' "$install_output"
  "${ADB[@]}" logcat -b all -d -v threadtime 2>/dev/null |
    grep -Ei 'PackageManager|installd|system_server|FATAL EXCEPTION|lowmemory|lmkd|binder|Failure' |
    tail -n 250 || true
  fail "APK installation failed. The original ADB/PackageManager error is shown above; diagnostics will be uploaded."
fi
printf '%s\n' "$install_output"
[[ "$install_output" == *"Success"* ]] || fail "ADB did not report a successful APK installation."
package_path="$( "${ADB[@]}" shell pm path "$PKG" 2>&1 | tr -d '\r' )"
[[ "$package_path" == *"package:"* ]] || fail "Package manager cannot find installed package $PKG: $package_path"

"${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1 || true
start_output="$( "${ADB[@]}" shell am start -W -n "$PKG/$ACT" 2>&1 | tr -d '\r' )"
printf '%s\n' "$start_output"
[[ "$start_output" == *"Status: ok"* ]] || fail "MainActivity did not start successfully (ActivityManager output above)."

pid1=""
for _ in $(seq 1 20); do
  pid1="$( "${ADB[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true )"
  [ -n "$pid1" ] && break
  sleep 1
done
[ -n "$pid1" ] || fail "App process $PKG did not appear after MainActivity launch."
sleep 10
pid2="$( "${ADB[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true )"
[ -n "$pid2" ] || fail "App process $PKG exited within 10 seconds (initial PID $pid1)."
[ "$pid1" = "$pid2" ] || fail "App process changed during the 10-second stability check (PID $pid1 -> $pid2)."

echo "PASS: $PKG installed; MainActivity returned Status: ok; process remained alive (PID $pid2)."
