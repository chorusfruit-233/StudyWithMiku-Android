Android 1.2.17 / versionCode 20, source 64c7c83de2c17fc072232eb5f3d7bbb61d34d172.
Debug: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/42445863717
Signed: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/42445863541
Publish: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/42446586067

The raw device XML is preserved verbatim from the final Debug report ZIP: 55 tests on each of API 35 and API 37, no failures, errors or skips. Both build variants pass 105 JVM tests and lint with zero errors.

Six new device tests cover the production settings and application shell in portrait/landscape; disabled and re-enabled shared song cards; device-local persistence across reconstruction, cloud import and account switching; a playing production MediaSessionService disconnecting and rejecting an external controller while disabled, then restoring a paused queue; an active local HTTP download being paused and refusing resume, with completed cache surviving recreation; and an in-flight playlist request cancelled without a late callback, write or new request while disabled.

The cache regression waits for the server to observe a request before disabling. The service regression waits for service removal before reopening and restoring fixture data. The isolated fixtures do not use production credentials or send chat messages. Device interactions run with Debug APKs on emulators; no signed-phone or real-account end-to-end acceptance is claimed.

The signed APK checksum, fixed certificate, package, version, min/target SDK, non-debuggable flag and ZIP alignment were checked. All four public asset digests match downloaded files. The public tag points to the verified application commit; later documentation edits do not change compiled inputs.
