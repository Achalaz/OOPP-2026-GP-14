# Student Grades and GPA investigation and fix

Verified on 2026-10-31 with Oracle Java 25.0.5, JavaFX 25, Maven 3.9.16 and the actual MySQL 8.0.46 database `ftms_group14`.

## 1. Confirmed cause

The existing `stu` account (`DEMO-STU-20`, database user/student 20) has an active STUDENT account, a valid student profile and three valid enrollments (407, 408, 409), but **zero saved course results and zero published results**. Its empty grades table is a data/publication gap, not a broken Java query. No production grades were invented or published to hide that gap.

The prompt did not identify the failing login, and no account clarification was received during verification. The account finding above is confirmed for `stu`; all other active student accounts in this database were checked too.

A separate confirmed GPA defect was fixed: `GPAService.semesterView()` calculated legitimate published-result GPA, then replaced both values with null whenever the saved `semester_results` cache was absent or stale. Publishing invalidates that cache, so a student could not see newly calculated GPA after Refresh until a lecturer separately saved GPA. Student reads now display the current published-result calculation, clearly identify a missing/stale saved summary, and never update it. Legitimately unresolved GPA remains Pending.

## 2. Exact files changed

Production files, all under `src/main/java/lk/ac/ruhuna/ftms/`:

| File | Change |
| --- | --- |
| `controller/StudentRecordsController.java` | Requested empty message supplied by the service; separate connection/query errors; readable GPA explanations; result column sizing. Existing Refresh, session cancellation, read-only controls and navigation retained. |
| `controller/GPAController.java` | Labels identify a current calculation when the saved summary is unavailable/stale. |
| `dao/CourseResultDAO.java` | Added a prepared, student-scoped enrollment availability SELECT for empty-state diagnosis. The published-results query and its filters remain intact. |
| `service/ResultService.java` | Distinguishes no valid enrollments from waiting for publication, inside the same authenticated read snapshot as grades/GPA. |
| `service/StudentAccessService.java` | Distinguishes a missing student profile from an inactive/unavailable account, while continuing to reject both. |
| `service/GPAService.java` | Displays legitimate current GPA independently of the saved cache; explains no applicable credits and repeat-policy Pending states. No GPA formula or grade mapping changed. |

Test files, under `src/test/java/lk/ac/ruhuna/ftms/`:

- `controller/StudentGradesLoadingSmokeTest.java`: empty/enrollment/error feedback and optional SELECT-only live JavaFX mode.
- `controller/StudentPortalViewSmokeTest.java`: requested empty message and publication appearing after Refresh.
- `service/AcademicMySqlIT.java`: calculated-versus-saved GPA and unchanged saved summaries.
- `service/StudentPortalMySqlIT.java`: missing profiles/enrollments, query failures, completed historical records, unsaved and invalidated GPA, incomplete/repeat behavior.
- `service/StudentResultsLiveMySqlIT.java` (new): existing active students, exact published result fields, known demo GPA, repeated reads and unchanged academic tables.

Documentation: this file and `docs/database/diagnose_student_results.sql` (new, SELECT-only).

All eleven requested Java classes and relevant student/results/GPA/dashboard FXML were inspected. No duplicate application classes, new GPA subsystem, schema migration, FXML replacement, commit or push was created. The initial working tree was clean.

## 3. Workflow and SQL findings

Both student dashboard Grades/GPA entry points open `student_results.fxml`, whose `userData="results"` selects `StudentRecordsController.results()`. That calls `ResultService.getMyPublishedRecords(identity)`. `StudentAccessService.readSnapshot()` validates the session generation, active database STUDENT role and profile, then passes `identity.userId()` into both the course results and GPA reads.

The actual schema defines `students.student_id` as a foreign key to `users.user_id`. Using the authenticated user ID as student ID is correct. There is no separate student-profile ID to substitute and no hardcoded student ID in the application path.

The published result joins are correct:

`course_results.enrollment_id -> enrollments.enrollment_id -> course_offerings.offering_id -> courses.course_id / semesters.semester_id -> academic_years.academic_year_id`.

`e.student_id=?` and `r.published=TRUE` are applied in SQL with PreparedStatements and try-with-resources. COMPLETED enrollments/offerings and inactive historical courses/semesters/years are not excluded. WITHDRAWN enrollments and CANCELLED offerings remain excluded by the existing authorization policy; the GPA DAO uses the same eligibility filters. No join/filter defect excluding valid published results was reproduced. Missing-profile feedback was previously indistinguishable from an unavailable account; the guarded LEFT JOIN now distinguishes it without authorizing a missing profile.

Database errors propagate instead of being converted into successful empty lists. Refresh clears stale rows, opens a new authenticated repeatable-read transaction, and reloads grades/GPA together. Old-session callbacks remain rejected.

## 4. Actual database publication status

| Finding | Observed |
| --- | --- |
| Saved course results | 57 |
| Published results / drafts | 56 / 1 |
| Active students checked | 26 |
| Published results visible to active students | 53 |
| Active students with no results | `stu` only |
| Inactive account | `tg2101`, owning the other three published results; access remains denied |
| Existing demo | `demo_stu01`, `DEMO/2024/0001`: two published results |
| Existing draft | `tg2102`, enrollment 102: remains unpublished |
| Published rows with missing numeric grades | Two legacy NOT_ELIGIBLE rows, enrollments 104 and 105; excluded from GPA |
| Published rows missing publication timestamp | 0 |
| Broken result/student/offering/course/semester/year mappings | 0 |
| Snapshot letter/point mismatches against linked grade rules | 0 |

The existing lecturer workflow is Calculate Result -> Save/Update Draft -> review the saved result -> Publish Result confirmation. Saving uses `published=FALSE` and a null publication timestamp. Publishing rechecks lecturer assignment, enrollment, current calculation, eligibility, grade rules and reviewed snapshot; it sets the flag/timestamp and commits the saved grade letter, grade point and result status. Duplicate publication and overwriting a published result remain rejected. Assessment release is a separate operation and does not publish final results.

Published demo results already exist, so no seed script or demo inserts were necessary. The supplied diagnostic SQL is safe to rerun as SELECT-only. The existing `stu` enrollments require the normal authorized lecturer workflow if legitimate results are expected.

## 5. GPA verification

Existing grade snapshots and course credits remain authoritative. Current grade-rule edits do not reinterpret historical published grades. SGPA and CGPA use `sum(credits * grade_point) / sum(included credits)`, rounded HALF_UP to two decimals. CGPA accumulates quality points/credits chronologically rather than averaging SGPAs.

- Existing demo: ICT2122 B (3 credits, 3.00) and ICT2132 B+ (3 credits, 3.30): `(9 + 9.9) / 6 = 3.15` SGPA and CGPA.
- Regression: `(3*4 + 2*3 + 1*0) / 6 = 3.00`; failed zero-point results count in the denominator.
- Regression: semesters with 3 credits at 4.0 and 2 credits at 3.0 produce SGPAs 4.00 and 3.00, CGPA 3.60.
- Regression: different-size semesters `(2*4 + 8*2) / 10 = 2.40`, not the average of semester GPAs.
- Unpublished/incomplete/non-numeric results are excluded under existing rules; partial calculations are described as provisional. No applicable published credits yields Pending, not zero.
- Repeat/replacement policy is still unconfirmed in this project. Affected GPA stays Pending and repeated courses are not counted twice. Published repeat grades remain visible.
- A newly published result invalidates saved GPA; the next student read calculates correctly while the saved cache stays unchanged. Matching saved summaries remain identified as current.

## 6. Executed validation

| Check | Result |
| --- | --- |
| Java 25 `mvn clean compile` | PASS |
| Java 25 `mvn test` | PASS: 45 tests, zero failures/errors |
| `AcademicMySqlIT` | PASS: 16 tests |
| `AssessmentPublicationMySqlIT` | PASS: 11 tests |
| `StudentPortalMySqlIT` | PASS: 12 tests |
| `StudentResultsLiveMySqlIT` | PASS: 1 test, 26 active accounts / 53 published rows; before/after academic snapshots identical |
| `StudentGradesLoadingSmokeTest` | PASS: 38 checks, including failures, timeout, cancellation, recovery and session changes |
| `StudentPortalViewSmokeTest` | PASS: 125 checks, including credential login, read-only results, Refresh, pending GPA, dashboard and logout |
| `AcademicAcceptanceSmokeTest` | PASS: 98 checks through assessment release, Marks, Eligibility, Results, publication confirmation, GPA, reports and student views |
| `StudentGradesLoadingSmokeTest --live` | PASS: 11 checks against actual `stu` and `demo_stu01`; repeated in a fresh JVM, with existing published results still visible |
| `git diff --check` | PASS |

Database workflow mutations were confined to existing test fixtures that shadow live tables with connection-local temporary tables. Live account verification used SELECTs only. No production enrollment, result, GPA or grade rule was changed. The legacy main-method tests that Maven reports as zero tests are not counted as executed UI tests; the UI runners listed above were launched explicitly.

Maven is not on PATH in this environment. Commands used:

```powershell
$maven = 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
$repository = "$env:USERPROFILE/.m2/repository"
& $maven "-Dmaven.repo.local=$repository" clean compile
& $maven "-Dmaven.repo.local=$repository" test
& $maven "-Dmaven.repo.local=$repository" '-Dtest=StudentPortalMySqlIT,AcademicMySqlIT,AssessmentPublicationMySqlIT,StudentResultsLiveMySqlIT' test

$fx = @('base','graphics','controls','fxml') | ForEach-Object {
    "$repository/org/openjfx/javafx-$_/25/javafx-$_-25-win.jar"
}
$classpath = "target/test-classes;target/classes;$repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
java "-Djavafx.cachedir=$pwd/target/javafx-cache" --module-path ($fx -join ';') --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $classpath lk.ac.ruhuna.ftms.controller.StudentGradesLoadingSmokeTest --live
```

Use the same Java arguments with `StudentGradesLoadingSmokeTest` (no `--live`), `StudentPortalViewSmokeTest`, or `AcademicAcceptanceSmokeTest` to reproduce the other UI checks. JavaFX scenes and controls were exercised automatically; a human-operated full desktop application restart was not performed. The fresh-JVM check proves existing database-backed results remain readable across process restarts; it does not claim a new production publication was performed.

## 7. Remaining issues

`stu` still has no legitimate saved/published results. Its new empty message is expected until an assigned lecturer saves and publishes valid academic results. Existing NOT_ELIGIBLE rows and stale stored GPA summaries are preserved. Repeat policy remains unresolved. These conditions were not repaired by inventing grades or changing academic rules. The build emits an existing unchecked-operation warning in `MarksEntryIntegrationTest`; there are no related compilation errors.

## 8. Exact manual acceptance steps

1. Launch the rebuilt application with Java 25. Sign in as `stu` using its existing credentials. Open Student Dashboard -> My Grades and GPA. Expect no rows and exactly: "No published results yet. Please contact your lecturer if your results were expected." There must be no fabricated GPA.
2. Log out and sign in as `demo_stu01`. Open the same screen. Verify ICT2122/B/3.00 and ICT2132/B+/3.30, their database course names, semester, three credits each and attempt 1. SGPA and CGPA should both be 3.15.
3. Press Refresh. Verify a loading indicator, disabled Refresh during loading, then the same current grades/GPA. The screen must have no student selector, editable cells or save/publish controls.
4. Use Back to Student Dashboard, reopen results, then Logout. Confirm login identity is retained during dashboard navigation and cleared on logout. Sign in as a different student and verify only that student's records.
5. Close the whole application and relaunch it. Sign in as `demo_stu01` again and repeat steps 2-3. Published results should still be present.
6. For publication acceptance, use a designated test database/account and a real enrolled student/offering assigned to the lecturer. Enter valid assessment marks and an eligibility decision consistent with the existing policy. Do not change production academic records solely for this test.
7. As that lecturer: Results -> assigned course -> enrolled student -> Calculate Result -> Save/Update Draft. Reopen the selection and review the saved grade, grade point and status. Confirm draft state and no publication timestamp. In a separate student application instance, Refresh and confirm the draft remains hidden.
8. In the lecturer instance, select Publish Result and cancel once; it must remain draft. Review again, publish and confirm. Reopen it to verify published state/timestamp. In the already-open student instance, Refresh; the result and legitimately calculated GPA must appear without a separate GPA save. This is separate from Release Assessment.
9. Check an incomplete/non-numeric result and a repeated attempt in the test database. Confirm no invented numeric GPA, an explanation for Pending, and no double counting. Check a zero-point FAIL still contributes its credits to the GPA denominator.
10. Exercise a missing student profile and an account with no usable enrollment only in the test database. Expect respectively the missing-profile error and the faculty-office enrollment message, not a successful no-publication state.
11. With only a test application instance using an unavailable database connection, press Refresh. Expect a connection/query error and cleared old grades/GPA. Restore the connection and Refresh to recover. Do not stop a shared production database for this check.
12. Reopen Lecturer Results, Marks, Eligibility and GPA; verify the same assignment restrictions, protected published results, draft/publication separation, legitimate calculations and existing save behavior.
