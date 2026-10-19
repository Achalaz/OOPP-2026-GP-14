# Results and grades module

The implementation was matched to `SHOW CREATE TABLE` output from the local
`ftms_group14` database. It adds no schema changes. `CourseResult` represents every
column of `course_results`, including nullable marks/grades, audit and publication
fields. Student labels use the existing `students` and `users` tables.

## Navigation and integration

Open the existing application, go back from Eligibility to Marks Entry, then choose
**Results & Grades**. `MainApp` is unchanged. The results screen reuses
`NavigationUtil`, the existing offering projection/query, JavaFX alerts, and the
existing inline FTMS visual style; the CSS directory currently has no stylesheet.
All controller data access goes through `ResultService` and runs off the UI thread.

The service requires the saved `eligibility_records.overall_eligible` decision.
It does not invent attendance rules or recalculate eligibility. Missing/null
eligibility gives `PENDING`; false gives `NOT_ELIGIBLE`, with no normal grade or
grade point. The eligibility module must maintain that saved decision as inputs
change. Draft saves now require an authenticated, active database account and derive
`calculated_by` from `SessionManager`. The legacy user-ID argument/setter rejects
impersonation; it cannot select another calculator. Existing restrictions on
unscoped lecturer draft operations are retained. Broader legacy result-read/draft
role permissions are not expanded by this publication phase.

## Calculation and explicit assumptions

For each assessment with positive weight:

`contribution = marks_obtained / max_mark * weight_percentage`

All non-`FINAL_EXAM` assessment types form CA. All `FINAL_EXAM` assessments form
the exam component, including offerings with multiple exam assessments.
`ca_mark` and `final_exam_mark` are each normalized to a percentage within their
component, matching existing saved data. Overall is the sum of unrounded
contributions, not an average of the displayed percentages. Offerings can use
different splits, or have only one weighted component. An absent component is NULL.

Weights must total 100%; incomplete/invalid plans produce an actionable validation
error instead of silently rescaling. Marks must be between zero and that
assessment's maximum. Marks belonging to another offering are rejected. Withdrawn
enrollments are rejected. Zero-weight assessments do not affect the result.

Decimal arithmetic is used. Displayed/stored marks round to two decimal places
using HALF_UP; the grade lookup uses that exact stored overall value. Active grade
rules are matched inclusively on `min_mark` and `max_mark`. Gaps and overlaps are
errors. Grade letter, point, and PASS/FAIL are taken from the matched rule, including
`is_pass`; no grade boundaries are encoded in application logic. Current database
rules are demo data and are not represented as official grading policy.

No replacement scores, medical concessions, repeat-grade caps, or special
exam-pass thresholds are invented. Until official policies are supplied, unresolved
positive-weight marks are handled conservatively:

| Situation after confirmed eligibility | Result status |
| --- | --- |
| All required marks PRESENT and numeric | PASS or FAIL from the grade rule |
| No marks entered; every mark missing/PENDING | PENDING |
| All unresolved marks are explicitly ABSENT | ABSENT |
| All unresolved marks are explicitly MEDICAL | MEDICAL |
| Partially entered marks, PRESENT with NULL, or mixed unresolved statuses | INCOMPLETE |
| No assessment plan | INCOMPLETE |

The complete CA/exam component may still display for an unresolved result, but
overall mark, grade rule, grade letter, and grade point stay NULL. Missing marks
are never converted to zero or removed to inflate a component percentage.

## Saving

Calculate is a preview. Save re-reads eligibility, assessments, marks, and grade
rules in one transaction and upserts on the existing unique `enrollment_id` key.
The enrollment is locked during saving to serialize result calculations. Errors
roll back the transaction. Updating an ungraded result clears any previous grade.

Saving always produces an **unpublished draft**, including when replacing a
published result; `published` becomes false and `published_at` becomes NULL.
This behavior is stated on the screen and enforced by the DAO upsert, even if a
caller supplies publication flags. Refresh discards unsaved previews and reloads
saved results. GPA calculation changes are reserved for Phase 2.

## Phase 1 publication workflow (19 October 2026)

The existing service, DAO, model, controller and FXML are extended; no duplicate
controllers/services, schema migration, dashboard change or grading policy is added.

Publication is **disabled for every production role**. The repository SRS
(`docs/SRS/OOP_project_group_14_SRS.pdf`, sections 2.3, 3.1/FR-AUTH-02 and 3.8)
defines academic viewing and lecturer marks entry, but assigns no result-publishing
authority. Admin academic access is explicitly unconfirmed. The decision required
before enabling this action is: **which existing role(s) may publish results, and
for which course offerings (assigned offerings only, or an explicitly approved wider
scope)?** Confirm with the course coordinator; do not infer Admin authority.
The SRS also requires confirmation/loading of official grading rules before go-live;
the existing sample grade table is not evidence of that approval.

Both public `ResultService` constructors use a deny-all publication policy. There
is no environment-variable/configuration switch granting permission. Package-only
connection/policy seams let tests exercise successful publication with synthetic
authorization; those grants do not affect production constructors or create a role.

When authority is approved, the prepared workflow:

- Shows **Publish Result** for an authorized offering and enables it only for a saved,
  complete PASS/FAIL draft. A confirmation dialog identifies the student, enrollment,
  offering, overall mark, grade and status; Cancel is the default.
- Captures an immutable snapshot of the reviewed saved row. It rejects a different
  saved row/version, an unsaved preview, a missing row or an already-published row.
- Independently authorizes the service call, checks the same login generation and
  locks the active account/role. Any authorized lecturer must also have a current
  offering assignment, read under the same transaction.
- Checks the actual enrollment/offering relationship, rejecting withdrawn
  enrollments and cancelled offerings. No caller-supplied grade is trusted.
- Rejects PENDING, INCOMPLETE, ABSENT, MEDICAL, NOT_ELIGIBLE, invalid grades and
  missing audit fields. Legacy drafts without a calculator must be recalculated
  and saved by a signed-in user before publication.
- Recalculates current saved eligibility, assessments, marks and grade rules in an
  InnoDB SERIALIZABLE transaction. It compares every derived mark/grade/status with
  the reviewed draft. Input/rule locks protect the validation-to-update interval.
  Changed results require a new calculation, save, review and confirmation.
- Updates `published = TRUE` and `published_at = CURRENT_TIMESTAMP` atomically,
  preserving marks, grades, `calculated_by` and `calculated_at`. SQL/validation/session
  failures roll back. No `published_by` column is invented.

Freshness means the reviewed saved result still exactly matches the recalculated
academic values. The existing schema has no input revision history: edits producing
identical stored marks/grades are equivalent, not separately audited. This phase
does not recalculate eligibility or GPA and does not repair later input-edit paths;
their invalidation requirements remain in the subsequent phases. In particular,
revalidating a result does not prove that an existing eligibility decision was
derived from current attendance/CA inputs.

## Verification

Compile using the existing Maven setup: `mvn compile`.

`src/test/java/lk/ac/ruhuna/ftms/service/ResultServiceTest.java` is a dependency-free
regression runner with a `main` method. After `mvn test-compile`, run it using:

```powershell
java -cp 'target/test-classes;target/classes' lk.ac.ruhuna.ftms.service.ResultServiceTest
```

It covers varying maxima and weights, multiple exam assessments, rounding and rule
boundaries, all result statuses, missing CA marks, invalid inputs, and unavailable
or overlapping rules. Test-only grade ranges are synthetic fixtures.

Two additional explicit runners are provided (these `main` runners are not
automatically executed by Surefire):

- `lk.ac.ruhuna.ftms.dao.ResultDatabaseSmokeTest` reads the configured database and
  checks active rule boundaries, individual/batch mapping, and calculations. Add
  `--rollback-write` to check upserts against an existing row in a transaction that
  is always rolled back. Include MySQL Connector/J on the runtime classpath. Like
  any MySQL auto-increment upsert, this can reserve IDs even when rolled back.
- `lk.ac.ruhuna.ftms.controller.ResultsViewSmokeTest` loads the real FXML using an
  in-memory service, with no visible window or database access. Add the four JavaFX
  platform JARs to the module path and use
  `--add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics`.

Additional explicit runners for Phase 1:

```powershell
java -cp 'target/test-classes;target/classes' lk.ac.ruhuna.ftms.service.ResultPublicationTest
java -cp 'target/test-classes;target/classes;PATH_TO_MYSQL_CONNECTOR_JAR' lk.ac.ruhuna.ftms.service.ResultPublicationDatabaseTest
```

`ResultPublicationTest` covers production deny-all authorization, draft audit
identity, PASS/FAIL (including numeric zero), missing/invalid/unresolved/stale drafts,
changed rules/eligibility/marks, unauthorized offerings, inactive/changed roles,
session replacement and SQL rollback. Its permission grants and grades are synthetic.

`ResultPublicationDatabaseTest` uses existing local MySQL fixtures and exercises the
actual service/DAO SQL, publication fields, audit preservation and rollback. Four
second connections verify that concurrent edits of marks, assessments, eligibility
and grade rules wait for publication locks. Commit calls are intercepted so no result
is durably published; a fresh connection checks the original result after rollback.
An upsert may reserve an auto-increment value even when rolled back.

`ResultsViewSmokeTest` loads the actual FXML, checks handler/field wiring, hidden
unauthorized controls, cancel/confirm behavior, the published display and disabled
repeat clicks. Its confirmation seam avoids opening a visible dialog; manual visual
inspection of the actual confirmation window is still outstanding.

Executed results and scope limitations for the Phase 1 review are recorded below.
Surefire does not execute these `main` runners: `mvn test` reporting zero tests is
not treated as a test pass. Run the explicit runners as well.

### Executed Phase 1 verification

Java `25.0.4.1`, JavaFX `25`, MySQL Connector/J `9.4.0`, and the installed NetBeans
Maven were used. `mvn` is not on PATH on this machine; the actual successful build was:

```powershell
& 'C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd' '-Dmaven.repo.local=C:/Users/achal/.m2/repository' -o clean compile test
```

Compilation succeeded (101 main sources, 22 test sources). Surefire executed zero
tests. The following 21 runners were invoked separately; 18 passed and three have
the failures described below. No complete-suite pass is claimed.

| Explicit runner | Executed result |
| --- | --- |
| ResultServiceTest | PASS: 89 assertions |
| ResultPublicationTest | PASS: 149 assertions |
| ResultPublicationDatabaseTest | PASS: 19 JDBC/locking/rollback assertions |
| ResultsViewSmokeTest | PASS: FXML and controller flow |
| ResultDatabaseSmokeTest --rollback-write | PASS: 11 grade rules, 36 enrollments, rollback upserts |
| ProfileServiceTest | PASS: 79 assertions |
| ProfilePictureServiceTest | PASS: 93 assertions |
| CourseMaterialServiceTest | PASS: 21 assertions |
| NoticeServiceTest | PASS: 105 assertions |
| NoticeDatabaseSmokeTest --temporary-table | FAIL: fixture expects exactly four existing notices |
| NoticeViewSmokeTest | PASS: 219 assertions |
| LecturerAccessSmokeTest | PASS: 414 assertions |
| LecturerViewSmokeTest | PASS: 63 assertions |
| LecturerNavigationSmokeTest | FAIL: layout/navigation checks; see below |
| MarksEntryIntegrationTest --rollback-write | FAIL: requires a mark in an offering unassigned to its chosen lecturer |
| AttendanceIntegrationTest | PASS: 60 assertions, connection-local temporary tables |
| AttendanceViewSmokeTest | PASS: 71 assertions, connection-local temporary tables |
| TechnicalOfficerDashboardSmokeTest | PASS: 336 assertions |
| ProfileDatabaseSmokeTest --rollback-write | PASS: 145 assertions, rollback verified |
| ProfilePictureDatabaseSmokeTest --rollback-write | PASS: 873 assertions, rollback verified |
| ProfileViewSmokeTest | PASS: 241 assertions |

The notice database runner was initially invoked without its required flag and
stopped; the corrected invocation reached the existing four-row fixture assertion.
No notice files or fixtures were edited.

The marks fixture failure was reproduced using the unchanged HEAD versions of all
five Phase 1 production files in a separate `target/phase1-baseline` classpath.
The navigation runner initially timed out waiting for a window size. The unchanged
HEAD baseline also failed, at `STUDENT returns to its own dashboard`. No branch
switch, source overwrite or unrelated navigation repair was performed. A diagnostic
rerun against the changed tree reached the same student-dashboard assertion as HEAD.

The actual confirmation dialog has not been manually clicked, real credential login
has not been re-tested, and durable publication/restart persistence is not claimed:
MySQL publication tests deliberately rolled back. Publication authority and official
grading approval remain unresolved. Phase 2 has not started.

### Phase 1 review files and Git state

The starting and retained branch is `TG2122-other-funtions`, tracking
`origin/TG2122-other-funtions`. Confirm this as Achala's destination before any push.
No files were staged, committed or pushed. The four pre-existing untracked notice
files are untouched. The Phase 1 change set is exactly:

- `src/main/java/lk/ac/ruhuna/ftms/service/ResultService.java`
- `src/main/java/lk/ac/ruhuna/ftms/dao/CourseResultDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/model/CourseResult.java`
- `src/main/java/lk/ac/ruhuna/ftms/controller/ResultsController.java`
- `src/main/resources/fxml/results.fxml`
- `src/test/java/lk/ac/ruhuna/ftms/service/ResultPublicationTest.java` (new)
- `src/test/java/lk/ac/ruhuna/ftms/service/ResultPublicationDatabaseTest.java` (new)
- `src/test/java/lk/ac/ruhuna/ftms/controller/ResultsViewSmokeTest.java`
- `docs/results-module.md`

Temporary build, test-log, baseline and diff-review artifacts are under ignored
`target/`; they are not part of the source change set. `git diff --check` passed.
