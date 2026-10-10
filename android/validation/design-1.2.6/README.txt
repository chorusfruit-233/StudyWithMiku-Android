Study With Miku Android 1.2.6 design validation

Product change: 33c4a5fb721212e2ea3306a62610932acaf6b991
Final build: ec97ad0f4b28f098640e410176fb1a06a3b3d3e1
GitHub Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38056241528
Historical Debug draft: ci-ec97ad0f4b28 (unpublished; visible to maintainers with write access)
Public signed releases: https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/latest
APK: StudyWithMiku-Android-1.2.6-ec97ad0.apk
SHA-256: 8a58c770ece19ae8583d0ce07f39a9261a00ff91c0e9141e5495283a66e01345
Application ID: io.github.chorusfruit233.studywithmiku
Version code: 9; min API: 26; target API: 35
APK v2 signature verified.
Signing certificate SHA-256: 765ff0c894e7c4e1bd687ae12aba822eb322ebc0e9d16eb08b1501ac588dac1b
The signing certificate differs from the 1.2.5 CI APK. It cannot overwrite that install.

Downloaded reports: 70 JVM tests and 10 Android 35 emulator tests, 0 failures/errors/skips; Lint 0 errors.
Android tests exercise cloud radio selection before confirmation, latest cloud import and restart, isolated persisted profiles, deletion cancellation/confirmation, editing the same task, capacity refusal, and chat sheet gestures.
Screenshots are real offline Compose test windows containing synthetic data. sync-choice.png shows three equal alternatives and a single primary confirmation action. task-edit.png shows editing with a save action and no irrelevant date. The test activity does not include the main app Scaffold; its background screen insets do not represent the production app layout.
These two dialogs were visually inspected. Other font scales, full-screen pages, real-account cloud writes and physical devices are outside this screenshot validation.

Earlier run 38055557331 passed the same functional checks, but screenshot export made directories instead of files because UiAutomation does not interpret shell operators. The export helper now runs separate commands and validates PNG data; the CI script also requires real nonempty files. Both final screenshots were extracted from the final cloud report ZIP.
