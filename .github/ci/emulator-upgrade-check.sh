#!/usr/bin/env bash
# Installs the exact alpha09 APK previously supplied to the user, then performs
# an in-place update to alpha13. Captures PackageManager's real failure reason.
set -Eeuo pipefail

PKG="com.nextnavipro.app.debug"
ACT="com.nextnavipro.app.MainActivity"
OLD_APK="${OLD_APK_PATH:?OLD_APK_PATH is required}"
NEW_APK="${NEW_APK_PATH:?NEW_APK_PATH is required}"
OUT="${DIAG_DIR:-ci-upgrade-diagnostics}"
ADB=(adb -e)
mkdir -p "$OUT"

collect() {
  set +e
  {
    echo "--- UTC"; date -u
    echo "--- devices"; adb devices -l
    echo "--- old/new APK hashes"; sha256sum "$OLD_APK" "$NEW_APK"
    echo "--- package dump"; "${ADB[@]}" shell dumpsys package "$PKG"
    echo "--- package path"; "${ADB[@]}" shell pm path "$PKG"
    echo "--- process"; "${ADB[@]}" shell pidof "$PKG"
  } > "$OUT/upgrade-state.txt" 2>&1
  "${ADB[@]}" logcat -b all -d -v threadtime > "$OUT/logcat.txt" 2>&1
  "${ADB[@]}" shell getprop > "$OUT/getprop.txt" 2>&1
  return 0
}
trap collect EXIT

fail() {
  echo "::error::$*"
  exit 1
}

[ -s "$OLD_APK" ] || fail "The alpha09 APK from GitHub Actions run 1184 is missing."
[ -s "$NEW_APK" ] || fail "The alpha13 APK built by this workflow is missing."
state="$( "${ADB[@]}" get-state 2>&1 | tr -d '\r' )"
[ "$state" = "device" ] || fail "Emulator ADB state is '$state', not device."

echo "Installing original alpha09 APK (versionCode 8) from run 1184."
if ! out="$( "${ADB[@]}" install --no-streaming -t "$OLD_APK" 2>&1 )"; then
  printf '%s\n' "$out"
  fail "Fresh alpha09 installation failed before upgrade test."
fi
printf '%s\n' "$out"
"${ADB[@]}" shell dumpsys package "$PKG" | grep -m1 -E 'versionCode=8|versionCode 8' || true

echo "Updating the installed alpha09 in place to alpha13 (versionCode 13)."
if ! out="$( "${ADB[@]}" install --no-streaming -r -t "$NEW_APK" 2>&1 )"; then
  printf '%s\n' "$out"
  "${ADB[@]}" logcat -b all -d -v threadtime |
    grep -Ei 'PackageManager|PackageInstaller|installd|system_server|INSTALL_FAILED|Failure|signature|version downgrade|parse error' |
    tail -n 300 || true
  fail "In-place alpha09 -> alpha13 update failed. PackageManager's exact error is above."
fi
printf '%s\n' "$out"
[[ "$out" == *"Success"* ]] || fail "ADB did not report successful in-place update."

dump="$( "${ADB[@]}" shell dumpsys package "$PKG" | tr -d '\r' )"
printf '%s\n' "$dump" > "$OUT/package-after-upgrade.txt"
echo "$dump" | grep -m1 -E 'versionCode=13|versionCode 13' ||
  fail "Package manager did not report versionCode 13 after update."

"${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1 || true
start="$( "${ADB[@]}" shell am start -W -n "$PKG/$ACT" 2>&1 | tr -d '\r' )"
printf '%s\n' "$start"
[[ "$start" == *"Status: ok"* ]] || fail "MainActivity did not start after the update."
pid1="$( "${ADB[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true )"
[ -n "$pid1" ] || fail "App process did not start after alpha13 upgrade."
sleep 10
pid2="$( "${ADB[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true )"
[ -n "$pid2" ] || fail "App process exited within 10 seconds after upgrade."
echo "PASS: alpha09 (versionCode 8) installed, updated in place to alpha13 (versionCode 13), and launched."
