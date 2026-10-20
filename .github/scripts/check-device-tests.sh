#!/usr/bin/env bash
set -euo pipefail
cd android
actual_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
device_api=${MIKU_DEVICE_API:-$actual_api}
test "$device_api" = "$actual_api"
device_report="app/build/reports/device-tests/api-$device_api"
mkdir -p "$device_report"
# Each emulator must produce its own result; AGP 9 can return success after an install error.
rm -rf app/build/outputs/androidTest-results
if ! ./gradlew connectedDebugAndroidTest; then
  if [[ -d app/build/outputs/androidTest-results ]]; then
    cp -R app/build/outputs/androidTest-results/. "$device_report/"
  fi
  python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for path in Path('app/build/outputs/androidTest-results').rglob('*.xml'):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            print(f"{case.get('classname')}.{case.get('name')}: {failure.get('message', '')}")
            print(failure.text or '')
PY
  exit 1
fi
MIKU_DEVICE_API="$device_api" python3 - <<'PY'
import os
from pathlib import Path
import xml.etree.ElementTree as ET
reports = list(Path('app/build/outputs/androidTest-results').rglob('TEST-*.xml'))
totals = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
for report in reports:
    root = ET.parse(report).getroot()
    for suite in root.iter('testsuite'):
        for key in totals:
            totals[key] += int(suite.get(key, '0'))
if totals['tests'] < 41 or any(totals[key] for key in ('failures', 'errors', 'skipped')):
    raise SystemExit(f"API {os.environ['MIKU_DEVICE_API']}: incomplete or failed device suite: {totals}")
print(f"API {os.environ['MIKU_DEVICE_API']}: verified device test results: {totals}")
PY
cp -R app/build/outputs/androidTest-results/. "$device_report/"
screenshots="app/build/reports/design-screenshots/api-$device_api"
mkdir -p "$screenshots"
adb pull /sdcard/Download/studywithmiku-design/. "$screenshots/"
test -s "$screenshots/sync-choice.png"
test -s "$screenshots/task-edit.png"
