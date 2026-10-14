#!/usr/bin/env bash
set -euo pipefail
cd android
if ! ./gradlew connectedDebugAndroidTest; then
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
mkdir -p app/build/reports/design-screenshots
adb pull /sdcard/Download/studywithmiku-design/. app/build/reports/design-screenshots/
test -s app/build/reports/design-screenshots/sync-choice.png
test -s app/build/reports/design-screenshots/task-edit.png
