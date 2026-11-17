StudyWithMiku Android 1.2.18 — upstream main 981c9c9

Final application source: 0345102f6a4b8c970903a56e52e65bfc8448f6c4
Debug Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/44125479320
Release Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/44125480963
Publish Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/44127726526
Public release: https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.18

VALIDATION.json: final source, workflow IDs, public release state and artifact digests.
upstream-review.json: pinned base/head, 17 commits, 25 files and 168 npm package-path version changes (including additions/removals).
upstream-tests.txt: original upstream Node output; 30 passed, 0 failed/skipped.
content-check.json: quotes/updates equality and all 21 sticker file hashes.
apk-check.json: final package/version/certificate/alignment/resource checks.
report-check.json: downloaded Debug/Release report counts; each 134 JVM tests, each API 59 device tests, no failures/errors/skips.
Android35-tests.xml and Android37-tests.xml: unmodified final device reports.
api-*-chat-outbox.png: production chat components in an isolated test window with synthetic text; not the full community screen.
api-*-music-module-disabled.png and api-*-landscape-chat-keyboard.png: existing module/landscape regressions.
local-check.json: local build history, followed by the final unit checks; final full verification is provided by Actions above.

Earlier evidence is retained separately:
Android35-before-metadata-fix.xml and metadata-regression.json: the two original failing sync assertions (59 tests, 2 failures) on 416c423, before retaining read-only metadata; not final results.
first-device-attempt.json: emulator archive download failed before tests.
device-dependency-attempt.json: Maven 429 prevented device tests on 23db11e.

Tests use synthetic accounts, local HTTP services, fake sockets and generated audio. No production account writes or public chat messages were sent. The original backend was not modified or deployed. Emulator Debug results do not establish signed Release behavior on every physical device.
