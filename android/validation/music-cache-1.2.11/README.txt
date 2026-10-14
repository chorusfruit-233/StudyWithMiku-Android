Source: 6d3eeacbc67b463f8a3d6509c68df20d4f24e983 (Android 1.2.11 / versionCode 14).
Debug Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38653426858
Signed Release Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38653430117

Android35-tests.xml is copied verbatim from the Debug report ZIP, preserving line endings.
31 Android 35 tests passed. Audio tests use generated PCM WAV and local MockWebServer:
- Persist full audio, release and recreate cache/index, shut down server, then play and seek through ExoPlayer with a renewed URL.
- Commit partial download fragments, pause, recreate, resume with a positive HTTP Range offset, complete and remove cached bytes.
- Reject typed JSON error responses; endpoints without Content-Type can supply valid audio.
- Start the production DownloadService from a resumed activity and play through the production MediaController after server shutdown.
- UI offline queue contains completed songs only; clearing requires confirmation. Queued/paused songs do not show a duplicate add-download action.
- Playlist membership and separate audio qualities survive storage recreation and switching without network requests.

Screenshot: production cache panel in a component test window, synthetic partial playlist and storage numbers. It does not show the full app scaffold or claim all screen sizes/themes are accepted.

99 Debug JVM and 99 Release JVM tests passed, no failures/errors/skips; both Lint checks have zero errors.
Signed Release APK was downloaded again and its SHA256, fixed certificate, package, version and non-debuggable flag checked. See VALIDATION.json.
Debug device tests do not constitute signed Release phone acceptance, production music-provider tests, or real account end-to-end sync acceptance.
