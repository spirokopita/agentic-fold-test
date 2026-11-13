#!/usr/bin/env bash
# Run against the exact build-job APK, not an emulator-job rebuild.
set -euo pipefail
mkdir -p install-evidence
exec > >(tee install-evidence/installation.log) 2>&1
trap 'adb logcat -d > install-evidence/logcat.txt; adb shell dumpsys activity lastanr > install-evidence/last-anr.txt' EXIT
# The test launches activities directly; the emulator launcher is not under test.
# Stop it before instrumentation to avoid the observed Quickstep startup ANR.
# Keep error dialogs enabled, and reject screenshots if another window covers us.
adb shell am force-stop com.android.launcher3
old=legacy/AgentFold-v0.5.0-debug.apk
app=delivered/AgentFold-v0.6.3-Recovery.apk
test_apk=delivered/AgentFold-v0.6.3-androidTest.apk
tools_dir="$ANDROID_HOME/build-tools/35.0.0"
(cd delivered && sha256sum --check SHA256SUMS)
printf '%s  %s\n' 2288ca638f27da23d5b5dabf61a4c33bc7ef117d0fea031886d7dc631fd05bba "$old" | sha256sum --check
"$tools_dir/apksigner" verify --verbose --print-certs "$old"
"$tools_dir/apksigner" verify --verbose --print-certs "$app"
"$tools_dir/apksigner" verify --verbose --print-certs "$test_apk"
"$tools_dir/aapt" dump badging "$app"
"$tools_dir/aapt" dump badging "$app" > install-evidence/package-report.txt
head -n 1 install-evidence/package-report.txt | grep -F "name='com.agentfold.app.recovery'"
adb install "$old"
adb shell pm path com.agentfold.app | grep -F package:
adb install "$app"
adb shell pm path com.agentfold.app.recovery | grep -F package:
adb shell pm path com.agentfold.app | grep -F package:
# A same-certificate replacement must retain this app's private data.
adb shell "run-as com.agentfold.app.recovery sh -c 'mkdir -p files && echo upgrade-preserved > files/install-check.txt'"
adb install -r "$app"
adb shell run-as com.agentfold.app.recovery cat files/install-check.txt | tr -d '\r' | grep -Fx upgrade-preserved
adb shell run-as com.agentfold.app.recovery rm files/install-check.txt
adb install "$test_apk"
adb shell settings put global animator_duration_scale 1
adb shell am instrument -w -r -e requireAnimations true com.agentfold.app.recovery.test/androidx.test.runner.AndroidJUnitRunner | tee install-evidence/instrumentation.log
adb exec-out run-as com.agentfold.app.recovery cat files/ui-evidence/pdf-tool-result.json > install-evidence/pdf-tool-result.json
adb exec-out run-as com.agentfold.app.recovery cat files/ui-evidence/pdf-investigation.md > install-evidence/pdf-investigation.md
adb exec-out run-as com.agentfold.app.recovery cat files/ui-evidence/model-activity-01.png > install-evidence/model-activity-01.png
adb exec-out run-as com.agentfold.app.recovery cat files/ui-evidence/model-activity-02.png > install-evidence/model-activity-02.png
adb exec-out run-as com.agentfold.app.recovery cat files/ui-evidence/model-activity-03.png > install-evidence/model-activity-03.png
adb exec-out run-as com.agentfold.app.recovery cat files/ui-evidence/network-activity.png > install-evidence/network-activity.png
for panel in task files control history; do
  adb exec-out run-as com.agentfold.app.recovery cat "files/ui-evidence/workspace-$panel.png" > "install-evidence/workspace-$panel.png"
done
python3 - <<'PY'
from pathlib import Path
import re
s=Path('install-evidence/instrumentation.log').read_text()
if not re.search(r'OK \([1-9][0-9]* tests?\)',s) or any(x in s for x in ('FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed')):
    raise SystemExit('Delivered APK instrumentation failed or provided no passing result')
print('PASS: delivered APK coexists with the legacy package, reinstalls with data intact, and passes instrumentation.')
PY

adb shell settings put global animator_duration_scale 0
# Actual idle dashboard images, not a simulated run.
adb shell am force-stop com.agentfold.app.recovery
adb shell am start -W -n com.agentfold.app.recovery/com.agentfold.app.MainActivity
sleep 2
capture_dashboard() {
  adb shell dumpsys window > "install-evidence/$1-window.txt"
  grep -E 'mCurrentFocus=.*com\.agentfold\.app\.recovery/.*MainActivity' "install-evidence/$1-window.txt"
  adb exec-out screencap -p > "install-evidence/$1.png"
}
capture_dashboard dashboard-narrow
adb shell wm size 1600x1200
adb shell wm density 160
adb shell am force-stop com.agentfold.app.recovery
adb shell am start -W -n com.agentfold.app.recovery/com.agentfold.app.MainActivity
sleep 2
capture_dashboard dashboard-wide
python3 ci/select-workspace.py Task
capture_dashboard workspace-wide-task
adb shell settings put system font_scale 1.6
adb shell am force-stop com.agentfold.app.recovery
adb shell am start -W -n com.agentfold.app.recovery/com.agentfold.app.MainActivity
sleep 2
capture_dashboard dashboard-large-text
adb shell settings put system font_scale 1.0
adb shell wm size reset
adb shell wm density reset
