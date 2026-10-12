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

Post-fix JVM, Android device and signing results will be recorded after checks.
