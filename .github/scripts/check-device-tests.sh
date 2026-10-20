#!/usr/bin/env bash
set -euo pipefail
cd android
device_api=${MIKU_DEVICE_API:-$(adb shell getprop ro.build.version.sdk | tr -d '\r')}
device_report="app/build/reports/device-tests/api-$device_api"
mkdir -p "$device_report"
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
cp -R app/build/outputs/androidTest-results/. "$device_report/"
screenshots="app/build/reports/design-screenshots/api-$device_api"
mkdir -p "$screenshots"
adb pull /sdcard/Download/studywithmiku-design/. "$screenshots/"
test -s "$screenshots/sync-choice.png"
test -s "$screenshots/task-edit.png"
