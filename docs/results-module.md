# Results and grades module

Updated 19 October 2026 for Achala (TG/2024/2122). This supersedes the earlier
Phase 1 deny-all publication decision and draft-overwrite behavior.

## Preserved implementation

The existing CourseResult, CourseResultDAO, ResultService, ResultsController and
results.fxml remain the publication implementation. The calculation engine still:

- Uses every positive-weight assessment in the offering; weights must total 100%.
- Uses marks / maximum * weight, with decimal arithmetic and two-decimal HALF_UP storage.
- Separates FINAL_EXAM from CA, supporting different maxima, splits and multiple exams.
- Uses configured active grade rules, including is_pass; rejects gaps and overlapping rules.
- Preserves NULL for missing data. PENDING, INCOMPLETE, ABSENT, MEDICAL and NOT_ELIGIBLE do not acquire normal grades.
- Calculates previews, recalculates before saving, and compares an immutable reviewed snapshot before publication.

## Confirmed publication authority

Only an authenticated, active database LECTURER assigned to the selected offering
may calculate/save academic drafts or publish results. The publication authority
was explicitly confirmed by the project owner in this session. PRIMARY, ASSISTANT
and PRACTICAL are recognized schema assignment types; no extra restriction between
those types was invented. Cancelled offerings and withdrawn enrollments are rejected.
ADMIN, STUDENT, TECHNICAL_OFFICER and anonymous direct publication calls are denied.

The UI is an affordance, not the security boundary. The service independently
locks the active actor, assignment and enrollment context and verifies the same
SessionManager login generation before committing.

## Publication and corrections

Save produces an unpublished draft only when no published result already exists.
A published result cannot be converted back into a draft by ordinary saving.
Marks and assessment edits that would affect published results are also rejected.
A formal academic correction/revision policy is required before corrections can
be enabled; no correction, unpublish or republish feature was invented.

Publication uses a SERIALIZABLE InnoDB transaction. It re-reads eligibility,
assessment inputs and grade rules, checks the reviewed saved version, recalculates
derived values, and updates published/published_at atomically. Missing audit fields,
unresolved statuses, stale values and duplicate publication are rejected. Failures
roll back. No published_by column was assumed.

Existing saved attendance decisions are accepted only when supported by current
fully marked Theory/Practical attendance, matching raw percentages and source
timestamps. Unresolved special statuses block publication. Current CA must support
the existing 40% threshold when a CA component exists. These checks do not approve
an official attendance cutoff: new eligibility evaluations remain pending until
that policy is confirmed.

Input timestamps have one-second precision and there is no immutable input revision
history. Changes that leave all relevant academic values equivalent are not a
separate version. External database writes bypass application guards; restrict
database access accordingly.

## GPA and reports

Published grade letters, grade points and PASS/FAIL are historical snapshots.
Changing a grade rule later does not retroactively change GPA for published results.
Publication invalidates stored semester GPA caches. GPA screens calculate current
published data on load/refresh, and authorized Save recalculates/persists the full
student history transactionally. Repeat/replacement cases yield pending GPA rather
than an invented rule.

Reports hide draft marks and grades, while retaining pending enrollment rows.
Lecturers see assigned offerings only; students see only their own reports/GPA.
A lecturer's full GPA view is withheld if its transcript includes an unassigned
offering. Offering reports remain available within scope.

## Navigation and validation

Lecturer Dashboard has an Assessments and Results section with direct sidebar and
card links to Assessments, Marks, Eligibility, Result Publication, GPA and Reports.
Student Dashboard's Grades and GPA card opens personal published results and GPA;
the corresponding sidebar links are My Published Results and My SGPA / CGPA.
Student reports load automatically and hide offering/batch controls. Student GPA
selects the current student's latest semester and has no Save action.
Results -> GPA / Reports. Academic screens return to the authenticated dashboard.
The shared NavigationUtil lecturer allowlist includes these screens; Technical
Officer restrictions remain in place.

Run the reproducible checks with scripts/test-achala.ps1. The detailed executed
results, remaining policy decisions and complete changed-file inventory are in
[Achala academic completion report](achala-academic-completion.md).
