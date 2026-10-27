Source: 88459b5baa037d5e3a6ca88746009476003b43a9 (Android 1.2.14 / versionCode 17).
Upstream: 2bb2379cfa0b04d413f1406ecf5f70aa494dddad, checked 2026-10-27.
Debug Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/40486132522
Signed Release Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/40486132753
Publish Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/40487287182
Public Release: https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.14

upstream-review.json records the canonical GitHub comparison: 11 commits, 3 changed files, and 15 npm lockfile version changes. Only quotes.js changes user-visible content. The npm updates belong to the website; native mapping details are in android/UPSTREAM.md.

quotes-parity.json records the complete 48-entry quote comparison against the pinned upstream quotesList: text, source and order match. The previous 47 entries were checked against the old baseline. After downloading the signed APK, assets/quotes.json was compared byte-for-byte with the validated source, including the new quote and attribution.

Android35-tests.xml and Android37-tests.xml are copied verbatim from the final Debug report ZIP, preserving line endings. Each suite passed all 41 existing tests, with no failures/errors/skips. Debug and Release each passed 104 JVM tests; both Lint checks have zero errors. Eight signing certificate guard tests also passed. No new runtime behavior test was added for the quote-only content change.

The signed APK was downloaded before publishing. Package, version, minimum/target SDK, fixed certificate, non-debuggable flag, ZIP alignment, SHA-256 and packaged quotes were checked. After publishing, all four public asset digests were matched to downloaded files; draft=false, prerelease=false, Latest and the tag commit were verified. Later documentation-only commits do not change the compiled application or published APK.

Device regressions use the Debug APK, x86_64 emulators, generated audio, local services, synthetic accounts and a fake chat socket. They cover existing player, offline-cache, timer, permission-state, sync, chat and UI behavior. They do not constitute signed Release phone acceptance, production music-provider downloads or real-account end-to-end sync acceptance.
