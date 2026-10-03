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
change. Authentication may call `ResultsController.setCalculatedBy(userId)`;
until integrated, `calculated_by` is SQL NULL. Authorization remains an integration
gap in the existing application.

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
This behavior is stated on the screen. Publishing and GPA calculations are outside
this module. Refresh discards unsaved previews and reloads saved results.

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

Verified locally: a clean Maven `test-compile` succeeded; 89 service assertions,
database checks for 11 configured rules and 36 enrollments, rollback upsert checks,
and FXML/controller interaction checks passed. No result-row changes were retained.
