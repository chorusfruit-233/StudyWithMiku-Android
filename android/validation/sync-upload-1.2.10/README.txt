Study With Miku Android 1.2.10 — sync regression evidence

Old runtime implementation: 6ef15e2 (same runtime as public 1.2.9).
Six newly added scenarios fail before the fix: four sparse/invalid-number merge
cases with org.json.JSONException, and two assertions that uploads must match
the original web writable root fields and exact application/json media type.
old-*.xml contains these expected failures, not successful validation results.

The HTTP contract is taken from the original project's useStudySync.js and
getAuthHeaders. This fixture does not reproduce the production server's
internal implementation or prove why it returned 500 to a particular account.
All test credentials, messages and documents are synthetic. No real account
login, study upload or chat write is performed.

Post-fix application source: f214b1c75e8d827fb34a9bc016a024470809b4db
Debug Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38267583544
Signed Release Actions: https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38267665980

Downloaded reports confirm 95 Debug and 95 Release JVM tests pass, with 0
failures/errors/skips and 0 Lint errors. All 25 Android 35 emulator tests pass.
New device cases click the production merge choice for disjoint daily history,
verify the actual original HTTP fields/media type/no cookies, preserve both
sides' record fields through storage recreation, and retain local data/pending
upload after Calendar HTTP 500 until a manual retry succeeds. Another case
imports non-finite legacy setting strings without invalid native preferences.

Android35-tests.xml is copied verbatim from CI. sync-sparse-merge.png shows the
production choice dialog mounted in a Compose test window before submission,
using synthetic data. Assertions validate the successful upload and recreation;
the image does not validate the full app Scaffold, all font scales/themes or
production account/backend behavior. Device tests exercised Debug; the signed
Release APK has not had real-device end-to-end testing.

Release APK package, versionCode=13, fixed certificate and debuggable=false
were verified after downloading from GitHub. See VALIDATION.json for hash and
provenance. No keystore or passwords are in this directory.
