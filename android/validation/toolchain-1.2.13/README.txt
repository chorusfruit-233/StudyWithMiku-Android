Source: 7a16176872c00d547c51d81a2806d2761886c6f5 (Android 1.2.13 / versionCode 16).
Debug Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/39453175512
Signed Release Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/39453175128
Publish Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/39455009645
Public Release: https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.13

Android35-tests.xml and Android37-tests.xml are copied verbatim from the final Debug report ZIP, preserving line endings. Both suites passed all 41 tests, with no failures/errors/skips. Debug and Release each passed 104 JVM tests; both Lint checks have zero errors. Eight release certificate guard tests also passed.

This upgrade targets/compiles API 37 with AGP 9.4.1, Gradle 9.6.0, built-in Kotlin and Compose Compiler 2.4.21, and Build Tools 37.0.0. minSdk remains 26. Material 3 1.5.0-beta01 retains the existing Expressive APIs; other version choices and migration details are documented in android/DEPENDENCIES.md.

The two new device tests execute the production Coil 3 SingletonImageLoader: fetch/decode a PNG from local MockWebServer, and decode an actual bundled animated GIF while asserting Animatable output. The previous 39 player, offline-cache, timer, permission-state, sync, chat and UI cases passed on both APIs.

before-fix.json records historical failed candidates, not the final result. An old transitive Espresso reflected on removed InputManager.getInstance; explicitly pinning Espresso 3.7.0 resolves that incompatibility. The emulator workflow then still used runner-preinstalled Command-line Tools 12.0 because emulator-runner prepended cmdline-tools/latest. That path now points to the installed 23.0 tools, with an AVD target check before boot. environment.txt preserves final log evidence for tool revision, target=android-37.0, emulator version and completed device counts. Fresh XML/count checks reject install errors and zero-test Gradle successes.

sync-choice-api37.png and offline-cache-api37.png were captured on API 37 and visually inspected. They show production components with synthetic data in a light-theme test window; they are not whole-app acceptance for every theme, font size or device.

The final signed APK was downloaded again before publication. SHA-256, fixed certificate, package, version, min/target SDK, non-debuggable flag and zip alignment were checked. After publishing, draft=false, prerelease=false, Latest and the tag commit were checked; see VALIDATION.json. Later documentation-only commits do not alter compiled application inputs or the published APK.

Device tests use the Debug APK, generated WAV, local services, synthetic account data and a fake chat socket. They do not constitute signed Release phone acceptance, production music-provider downloads or real-account end-to-end sync acceptance.
