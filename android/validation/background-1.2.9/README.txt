Study With Miku Android 1.2.9 — background access regression evidence

Application source commit: 0f9eb03203d7af7a3e3195a8599a5fd5f10b585e
Debug CI: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38230310646

Downloaded reports confirm 82 JVM and 22 Android 35 emulator tests pass,
with 0 failures, errors or skips, and 0 Debug Lint errors.
Android35-tests.xml is copied verbatim from the CI attachment.

Six new device cases verify:
- Automatic permission requests and guidance persist across recreation;
  declining a request does not imply a grant or repeatedly interrupt a timer.
- Manual requests remain available, and actual exemption state can refresh.
- Already-exempt installs show guidance; revoked exemption can be requested.
- Unsupported or forbidden settings pages fall back to another system screen.
- The production settings section has independent permission/settings actions,
  and unrestricted-battery guidance can be deferred.
- The real FocusService acquires a PARTIAL_WAKE_LOCK without music while the
  stopwatch runs, and releases it after pause. This is checked using the
  emulator's dumpsys power, not a mock service or private implementation field.
The original 16 interaction/persistence cases also pass.

Policy cases use synthetic permission state and isolated SharedPreferences.
The screenshot mounts the production BackgroundSettingsSection in a Compose
fixture with synthetic "exempt" state. It validates text/layout and separate
buttons, not the phone's actual battery settings, full app Scaffold, vendor
ROM settings, all font scales or themes. No real user accounts or chat writes.

This device evidence describes Debug. Release-VALIDATION.json describes the
separate local signed Release build: 82 JVM tests pass, 0 Lint errors, expected
package and fixed certificate, and debuggable=false. The private keystore was
removed after building and is absent from source and published reports. The
signed Release APK has not had device end-to-end testing.
