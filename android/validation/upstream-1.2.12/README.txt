Source: 9f24ad6db34e839519fc4acca2327303e45b88cc (Android 1.2.12 / versionCode 15).
Upstream: 2a19db44ae7d06e5d8945086030ae78a1acce9ae, checked 2026-10-18.
Debug Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/39106638965
Signed Release Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/39106638524
Publish Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/39107099783
Public Release: https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.12

Android35-tests.xml is copied verbatim from the final Debug report ZIP, preserving line endings.
All 39 Android 35 tests passed. Eight additional tests cover:
- Real dual-ExoPlayer playback with generated PCM WAV: manual overlap and position-preserving handoff; automatic handoff while both voices have partial gain; cleanup after completion; rapid next followed by pause.
- Production MediaController user volume and explicit mute across track and queue switches.
- Partial/malformed legacy preferences, default completion, and independent music queue/index/volume persistence.
- Gated local HTTP playlist responses: an obsolete success cannot change newer songs/config/bookmarks or invoke an old playback callback; an obsolete failure cannot replace a later offline choice.
The previous 31 tests, including production DownloadService offline playback, sync, chat, tasks and WakeLock, still passed.

Debug and Release each passed 104 JVM tests, no failures/errors/skips. Both Lint checks have zero errors.
Additional JVM tests cover chat delivery failure/history merge with a fake WebSocket, the fade curve, and explicit forwarding of all Java Player.Listener callbacks.

before-fix.json records the earlier 6698779 candidate (39 device tests, 2 failed). Its listener bridge only declared onVolumeChanged and inherited Java default no-op callbacks, causing production controller state timeouts. This is historical failure evidence, not the final result. The final UserVolumeListener explicitly forwards every callback, and all 39 final device cases passed.

The final signed APK was downloaded again. Its SHA-256, fixed certificate, package, version and non-debuggable flag were checked before publishing. Public release metadata and the version tag were checked after publishing; see VALIDATION.json. Later documentation-only commits do not change the verified application.

Device tests use the Debug APK, generated WAV/local audio, local MockWebServer, synthetic account data and a fake chat socket. They do not constitute signed Release phone acceptance, production music-provider tests, or real-account end-to-end sync acceptance.
