#!/usr/bin/env bash
# Install the debug APK and drive past vivo's "unknown source app" gate.
#
# Why the verify loop: the installer's risk checkbox ignores taps that land
# while its enter animation is still running, and adb install then fails with
# INSTALL_FAILED_ABORTED: User rejected permissions. Blind tapping once is
# unreliable, so we tap, read the actual checked= state back, and retry.
set -uo pipefail

ADB="${ADB:-adb}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.jianyi.outfit"
CHECKBOX_XY="${CHECKBOX_XY:-540 2089}"
INSTALL_XY="${INSTALL_XY:-540 2237}"

[ -f "$APK" ] || { echo "no such apk: $APK" >&2; exit 1; }

before="$($ADB shell dumpsys package $PKG 2>/dev/null | grep -oE 'lastUpdateTime=[0-9: -]*' | head -1)"
echo "before: ${before:-<not installed>}"

# The adb client blocks until the installer answers, so keep it in background.
$ADB install -r -t "$APK" > /tmp/jianyi_install.log 2>&1 &
install_pid=$!

dialog_up() { $ADB shell "dumpsys window | grep -q packageinstaller" 2>/dev/null; }

for _ in $(seq 1 15); do
  sleep 1
  dialog_up && break
done

if dialog_up; then
  for attempt in 1 2 3 4; do
    sleep 2                       # let the enter animation finish
    $ADB shell input tap $CHECKBOX_XY
    sleep 1
    state="$($ADB shell uiautomator dump /data/local/tmp/tick.xml >/dev/null 2>&1; \
             $ADB shell cat /data/local/tmp/tick.xml 2>/dev/null | tr '<' '\n' | \
             grep CheckBox | head -1 | grep -oE 'checked="[a-z]+"')"
    echo "  attempt $attempt -> $state"
    case "$state" in *'checked="true"'*) break ;; esac
  done

  sleep 1
  $ADB shell input tap $INSTALL_XY
fi

wait "$install_pid" 2>/dev/null
tail -1 /tmp/jianyi_install.log

for _ in $(seq 1 12); do
  sleep 3
  after="$($ADB shell dumpsys package $PKG 2>/dev/null | grep -oE 'lastUpdateTime=[0-9: -]*' | head -1)"
  if [ -n "$after" ] && [ "$after" != "$before" ]; then
    echo "installed at $after"
    $ADB shell dumpsys package $PKG | grep -oE 'versionName=[0-9.]*' | head -1
    exit 0
  fi
done

echo "install did not take effect" >&2
exit 1
