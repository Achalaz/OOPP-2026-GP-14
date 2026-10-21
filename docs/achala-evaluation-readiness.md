# Achala academic evaluation report - 21 October 2026

Developer: **Achala - TG/2024/2122**. Java 25.0.4.1, JavaFX 25, Maven, JDBC/MySQL.

**Evaluation readiness: CONDITIONAL.** The existing academic implementation passes the
verification recorded below. Human acceptance with owner-designated persistent development
accounts and official academic policy approval remain outstanding. No working academic module
or other member's business logic was rewritten.

## 1. Modules verified

| Module | Evidence and resulting behavior |
| --- | --- |
| Assessment CRUD | Real JavaFX create, select/read, update and confirmed delete; SQL rereads; plan/precision/weight validation; referenced and published inputs protected; assignment scope enforced |
| Marks Entry | Real selectors, Save/Update and refresh; SQL persistence; zero versus NULL; status and numeric validation; role/enrollment/assessment checks; published marks protected |
| CA and attendance eligibility | CA fixture computes 80%; recorded Theory/Practical attendance and genuine zero checked; missing/special-status data remains unresolved; overall eligibility persists as NULL/PENDING |
| Results calculation | Decimal weighted calculation, assessment completeness, configured grade-rule validation and unresolved result states exercised |
| Save Draft | Real Calculate and Save controls persist an unpublished draft; unresolved drafts cannot be published; ordinary saving cannot overwrite a publication |
| Lecturer publication | Active assigned lecturer succeeds; unassigned, inactive, anonymous and other roles rejected; cancellation, incomplete/stale inputs, rollback, changed session and duplicate publication checked |
| SGPA / CGPA | Published snapshots only; credit weighting and chronological cumulative calculations; real GPA controls and persistence; historical grade-rule edits do not reinterpret published grades; repeat ambiguity withheld |
| Individual and batch reports | Real report buttons, pending rows, pass count and average excluding missing scores; offering scope and published assessment detail service checks |
| Student published results / GPA | Password login for two distinct fixture students; only own published grade and semester GPA visible; own draft and other student's grade excluded; read-only controls; stale session rejected |

Back to Dashboard preserves the exact authenticated login. Lecturer and student Logout clear
the session and return to the real login screen. Existing navigation runners also cover sidebar
and card entry points, role routing and viewport resizing.

## 2. Confirmed bug fixed

`NoticeDatabaseSmokeTest` assumed four real notices and fixed titles/dates. Its assertions
now use controlled, connection-local temporary notice fixtures and a connection-local frozen
clock. They cover empty/populated management views, CRUD, all audiences, inactive/expired/future
notices, publication timing, search, Unicode and unauthorized mutations. Synthetic session
fixtures remove dependence on the current production account list.

Real-table reads check schema/mapping and audience invariants without assuming row counts or
titles. A SHA-256 snapshot of the real notice schema and every column of every row is compared
before/after. **143 checks passed; the real notice table was unchanged.** Notices production
DAO/service/controller logic was not changed. No notice records were modified or deleted.

No additional application defect was demonstrated by the academic verification. New acceptance
test accessor/button-lookup errors were corrected while developing that runner; they were test
harness errors, not product bugs.

## 3. Exact files changed in this finalization session

| File | Change |
| --- | --- |
| `src/test/java/lk/ac/ruhuna/ftms/dao/NoticeDatabaseSmokeTest.java` | Repaired fixture isolation and retained production preservation check |
| `src/test/java/lk/ac/ruhuna/ftms/service/AcademicMySqlIT.java` | Added actual-MySQL incomplete/inactive/session-replacement publication rejection and rollback scenario |
| `src/test/java/lk/ac/ruhuna/ftms/controller/AcademicAcceptanceSmokeTest.java` | New real credential-login/control/dialog/JDBC acceptance runner |
| `scripts/test-achala.ps1` | Included the acceptance runner in JavaFX and all-regression modes |
| `docs/achala-academic-completion.md` | Added a current-report link; retained the historical report |
| `docs/achala-evaluation-readiness.md` | This verification, policy, acceptance and Git report |

The first, second, fourth and fifth files were already untracked at session start. This table
describes changes relative to that baseline; it does not claim authorship of their existing work.
No `src/main` source/resource, `pom.xml`, database configuration or tracked file was modified.

## 4. Build and automated results

Commands were run from the project root, without `-Offline`, using the script's installed
NetBeans Maven fallback and local Maven repository. The initial sandboxed build could not read
the JavaFX graphics JAR. Authorized execution outside the sandbox resolved that environment
restriction. Final verification uses Java compiler release 25.

| Requested command | Actual result |
| --- | --- |
| `./scripts/test-achala.ps1` | PASS: Maven package; 20 discovered tests, zero failures/errors/skips |
| `./scripts/test-achala.ps1 -IncludeDatabase -IncludeJavaFx` | PASS: package + 20 default tests; 11 MySQL tests; 5 explicit UI/integration runners |
| `./scripts/test-achala.ps1 -IncludeDatabase -AllRegressions` | PASS: package + 20 default tests; 11 MySQL tests; **23/23 explicit runners**, all exit 0 |

Compared with the historical 17/27 counts, the current tree already includes three student
access tests; this session adds one MySQL scenario. There are now **20 default + 11 integration
= 31 discovered test executions** in an extended script run. Main-method regressions are
executed separately; their assertions must not be counted as extra JUnit methods.

There are no remaining failures in these final runs. The original 22 explicit runners all passed after the notice fix. The added acceptance runner
passed **71 checks**. Other current counts include 89 result-calculation assertions, 159
publication assertions, 35 real-table publication/locking checks, 66 academic JavaFX checks,
64 marks checks and 344 lecturer navigation checks. Expected synthetic failure stack traces
in the profile runner exercise error handling; the runner's exit code and assertions passed.

Evidence is retained in:

- `target/achala-finalization/default.log`
- `target/achala-finalization/database-javafx.log`
- `target/achala-finalization/all-regressions.log`
- `target/achala-regression-logs/summary.json` and individual runner logs
- `target/surefire-reports/`

## 5. MySQL and publication safety

The 11 academic integration scenarios use actual JDBC/MySQL and temporary tables copied from
the configured live schema. Commits and application rereads are exercised within that connection;
closing it removes the fixtures. Temporary tables omit unsupported foreign
keys, so they do not alone prove production FK behavior. The retained real-table publication
runner separately exercises actual schema constraints and four concurrent-write lock cases,
intercepts commits, rolls back and verifies the original result and marks from fresh connections.
Other retained real-table write runners also roll back; auto-increment sequences can advance.

Publication coverage includes valid draft publication, unassigned/inactive lecturer rejection,
incomplete and stale result rejection, injected post-write rollback, login-generation replacement,
duplicate/publication protection, GPA visibility and historical grade snapshots. New UI fixtures
publish through the real confirmation dialog/service; no SQL shortcut sets their results published.

No persistent production publication, notice mutation, migration, table truncation, account
creation or password change was performed. The new credential hashes exist only in temporary
fixture users. No persistent data changes are needed to reproduce the automated acceptance.

## 6. Application acceptance and remaining manual work

**Automated real JavaFX acceptance: PASS.** `AcademicAcceptanceSmokeTest` loads the actual login
FXML and authenticates randomized password hashes through `AuthService`, `UserDAO` and MySQL.
It runs Lecturer Login -> Assessments CRUD -> Marks -> Eligibility -> Results -> Save Draft ->
Publish cancel/confirm -> GPA save -> Individual/Batch Reports -> Dashboard -> Logout.
Then two student logins exercise My Grades/My GPA, own-publication filtering, Dashboard, Logout
and rejection of an old login identity. These are controlled development accounts in temporary
tables, not sessions injected in place of authentication for this acceptance runner.

The eligibility UI first proves that an unknown policy saves PENDING and blocks publication.
The successful publication branch then uses the existing explicitly labeled confirmed-eligibility
fixture. Its sample grade bands and confirmation are **test data, not approved university policy**.
There is no ordinary application action that invents a final attendance decision.

**Human/live-account acceptance: NOT PERFORMED.** Owner-designated existing lecturer/student
development usernames and locally entered credentials are still required. Automated button
actions do not establish human visual approval, all Windows DPI behavior, or application-restart
persistence in the live database. The requested usernames were asked for without requesting passwords.

Safe manual acceptance steps:

1. Use an owner-designated disposable development database/account set. Identify an active
   LECTURER, an assigned offering, its enrolled STUDENT, another student and an unassigned lecturer.
   Enter passwords locally. Do not reset real account credentials for testing.
2. Begin with read-only navigation on existing accounts: inspect assigned offerings, own results,
   GPA and reports; use Back to Dashboard and Logout. Verify another student's results cannot be selected.
3. Before persistent writes, review the exact development offering/enrollment/assessment IDs,
   intended mark values, approved grade-rule version, existing confirmed eligibility decision and
   backup/restore plan. Obtain explicit approval for those concrete records and writes.
4. On that approved disposable data, add/update/delete an unreferenced assessment, save marks,
   calculate/save eligibility and a draft, and inspect the database through application reloads.
   If eligibility is PENDING, publication must remain blocked; do not manufacture approval in SQL.
5. For an already legitimately confirmed eligible development enrollment, calculate/save a fresh
   draft. Cancel publication once, then confirm as its assigned lecturer. Verify timestamp, disabled
   repeat publication, protected marks, GPA and individual/batch reports. Log out and log in again
   as its student; only its own published result/GPA should appear. Restart the app and repeat reads.
6. Attempt the same publication as an unassigned lecturer, and exercise incomplete/stale draft
   rejection only on the approved disposable data. Restore that disposable environment according
   to the approved plan; never unpublish or overwrite historical real results to clean up a demo.

No persistent write approval was assumed or requested for unspecified records. The exact-ID
live-write plan remains dependent on the account/fixture selection and policy confirmation.

## 7. Academic policy decisions still required

| Decision | Required confirmation / current behavior |
| --- | --- |
| Attendance percentage | Official cutoff, rounding and denominator; no new threshold was invented; unknown attendance/overall decisions stay PENDING |
| Theory/Practical requirements | Whether each component must independently meet a cutoff, missing-component handling and any combined weighting |
| MEDICAL, LATE, EXCUSED | Whether each counts as present, absent, excluded or a separate approval; special attendance remains unresolved; no medical-to-grade conversion invented |
| Official grading boundaries | Approved ranges, precision, grade points and pass flags; configured/sample rules are not asserted to be official |
| Repeat examinations | Attempt choice, replacement, caps and credit/GPA treatment; ambiguous repeat GPA remains withheld |
| Published corrections | Authorized revision workflow, audit trail, version history and downstream GPA/report changes; ordinary edits cannot change published results |

The implementation retains its existing 40% CA check. This finalization did not add or approve
new attendance, grading, repeat or correction policy.

## 8. Git preservation and recommended commit groups

Actual branch: **`TG2075_Notices`**, HEAD **`d45c4e5`**, tracking the locally recorded
`origin/TG2075_Notices`. No fetch or branch switch was performed. The old report's
`TG2122-other-funtions` branch and tracked-diff inventory are historical.

At entry: **31 untracked files, no tracked modifications, empty index**. Final changes remain
unstaged. `git diff` cannot display the contents of these untracked files; their contents and
session patches were reviewed directly. A SHA-256 manifest records every original file in
`target/achala-finalization/baseline-files.json`, with the initial status beside it. Final hash
comparison identified only the four existing files listed in section 3 as changed; all other
baseline files matched and none were missing. There are two new files, for a final total of
33 untracked files. The index is empty; `git diff --check` passed.

Keep these responsibilities separate when approval is granted:

1. **Existing academic implementation:** the untracked AssessmentsController, AcademicAccessDAO,
   AcademicTransaction, AssessmentService and assessments.fxml. The other implemented academic
   production files already belong to HEAD; do not rewrite or restage unchanged versions.
2. **Academic verification:** named academic/publication test files, the new acceptance runner
   and `scripts/test-achala.ps1`; include the additional MySQL scenario with those tests.
3. **Notices fixture repair:** `NoticeDatabaseSmokeTest.java` only for this fix. Keep the pre-existing
   `docs/notices-module.md`, `NoticeServiceTest.java` and `NoticeViewSmokeTest.java` with their
   owner's Notices work rather than attributing them to Achala.
4. **Academic documentation:** `docs/achala-academic-completion.md` and this report.

Preserve the 12 existing student-portal files for a separate owner-reviewed group:
`scripts/test-student.ps1`, StudentRecordsController, StudentAccessService, StudentCourseService,
student_attendance.fxml, student_courses.fxml, student_medical.fxml, student_results.fxml,
StudentPortalViewSmokeTest, StudentAccessTest, StudentDatabaseFixture and StudentPortalMySqlIT.
They are required by the current student integration and were not modified in this session.

Use explicit named paths or selected hunks. No `git add .`, `git add -A`, commit, push, reset,
merge, checkout or force push was performed. A clean-checkout evaluation requires the relevant
untracked dependencies to be included in approved commits; the current working-tree build is
the artifact verified here.
