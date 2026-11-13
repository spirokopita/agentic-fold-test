#!/usr/bin/env bash
set -u
test_status=0
gradle --no-daemon connectedDebugAndroidTest || test_status=$?
mkdir -p neural-evidence
# Recover successful state captures even when another regression test fails.
adb pull /sdcard/Android/data/com.agentfold.app.recovery/files/neural-evidence neural-evidence || {
  if [ "$test_status" -eq 0 ]; then test_status=1; fi
}
exit "$test_status"
