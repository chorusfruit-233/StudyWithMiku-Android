Android 1.2.16 / versionCode 19, source c92f56756ac5864060a29b43b2a5ccac72c92447.
Debug: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/42443380155
Signed: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/42443380154
Publish: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/42444310932

Final raw device XML reports are copied verbatim from the Debug report archive: 49 tests on each API, no failures, errors or skipped tests. Both build variants pass 105 JVM tests and lint with zero errors.

Eight new device tests exercise landscape navigation, visible focus controls, rotation and selection preservation, the player and queue, day records, task text across rotation, chat with an explicitly visible software keyboard, settings/calendar scrolling, cloud-choice confirmation and a narrow window with large fonts. Fixtures use production composables with synthetic data; the test activity uses the same configuration-change handling as MainActivity and is excluded from Release.

First-Android35-tests.xml retains the initial 48/49 result: three-button navigation left slightly less usable width than the first focus two-pane breakpoint. The threshold was corrected and the full suite rerun. The final input test enables the onscreen keyboard even when the emulator exposes a hardware keyboard, checks WindowInsets.isImeVisible, and restores the original setting.

Second-Android37-tests.xml and Third-Android37-tests.xml retain the subsequent 48/49 results: the expanded queue existed, but its visibility assertion after the semantic performScrollTo action in portrait failed, including after waiting for resized windows. The final rotation test waits for the decor and player viewport dimensions, performs an actual upward touch gesture, asserts the queue control is displayed, rotates back, and touches it to verify collapse. This validates the user interaction; it does not claim that waiting alone fixed the semantic scrolling failure. Portrait before/after screenshots are included in the complete Debug report archive.

API 35 screenshots are retained under api-35. VALIDATION.json records dimensions and SHA-256 for all 16 landscape screenshots from both APIs; the full set is also in the Debug report archive. These are emulator screenshots, not signed-phone or authenticated production-account acceptance.

The signed APK package/version/SDK levels, non-debuggable flag, fixed certificate, file checksum and all four published asset digests were checked. The release tag points to the verified source, and later documentation changes do not alter compiled inputs.
