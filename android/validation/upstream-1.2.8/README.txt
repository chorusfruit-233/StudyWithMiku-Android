Study With Miku Android 1.2.8 — upstream update regression evidence

Application source commit: f7fee3b317893be255f63f43b441eb00ae698580
Upstream source: a3c4e5e0d4be48b2e21662ac85ff6022f8aa926d
Debug CI: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38068345768

Downloaded CI reports confirm 82 JVM tests and 16 Android 35 emulator tests,
with 0 failures, errors or skipped tests, and 0 Debug Lint errors.
Android35-tests.xml is copied verbatim from the CI report attachment.

New device cases cover playlist bookmarks surviving store recreation,
separate native song playlists/favorites, successful music and name requests,
HTTP music failures preserving the last active playlist, failed naming falling
back to the ID, reply references and reply cancellation, missing references and
stickers, and cancel/confirm deletion without loading a saved playlist.
HTTP requests use local MockWebServer fixtures; no real accounts or chat writes.
The original 10 device cases (including cloud-choice persistence and chat-sheet
scrolling) also pass in this run.

chat-reply.png is a screenshot of the production ChatBubble and ChatReplyPreview
components mounted in a Compose test window with synthetic messages. It verifies
quoted text, visible reply text, reply action and cancel control. It does not
show the full app Scaffold and does not validate all font scales or themes.

Release-VALIDATION.json describes the separately built signed Release APK.
Release has 82 passed JVM tests, 0 Lint errors, the fixed user certificate,
the expected package, versionCode=11, and debuggable=false. The device tests
above exercised Debug; the signed Release has not had device end-to-end testing.
