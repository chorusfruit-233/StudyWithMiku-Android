Android 1.2.15 / versionCode 18, source 8196ad21f5e85a682b35fb611f4ce0193606a04a.
Upstream bcc6627b80e47515c26c7677823bf68227cd90ef, checked 2026-10-31.
Debug: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/41222479706
Signed: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/41222479966
Publish: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/41223505915

Both device XML reports are copied verbatim from the final Debug ZIP. Each API passed 41 tests; Debug and Release each passed 105 JVM tests and lint with zero errors. The new default-routing test intercepts actual default-client requests for data/calendar GET and PUT and profile GET, asserting the API host, login host and credential rules.

production-routing.json records the public first-party web bundle and SHA-256 used to confirm both data and profiles use https://mikumods.com. Login remains https://mikumod.com. Public unauthenticated requests returned HTTP 403; this is recorded in public-api-probe.json and is not authenticated account acceptance.

workflows-after.json records three active core workflows and the manually disabled historical 1.2.7/1.2.9 upload workflows. android-diagnostics.yml was deleted from the repository. Existing failure-report uploads remain in the normal CI workflow.

The signed APK package, version, SDK levels, non-debuggable flag, fixed certificate, SHA-256 and all four public asset digests were checked. Device regression uses Debug APK, generated audio, local HTTP servers, synthetic accounts and a fake chat socket; it is not signed phone or production account end-to-end acceptance.
