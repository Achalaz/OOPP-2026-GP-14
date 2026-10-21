# Achala academic implementation report

> **21 October 2026 finalization:** The historical inventory and test results below
> describe the earlier implementation session. See [the current evaluation report](achala-evaluation-readiness.md)
> for the actual branch, preserved working tree, repaired Notices fixture, fresh test results,
> credential-login acceptance coverage and remaining policy decisions.

Developer: **Achala — TG/2024/2122**  
Project: FTMS Group 14, Java 25 / JavaFX 25 / Maven / MySQL  
Implementation date: 19 October 2026

## Scope and safety audit

The actual branch is `TG2122-other-funtions`, tracking
`origin/TG2122-other-funtions` at
`https://github.com/Achalaz/OOPP-2026-GP-14.git`.
No branch switch, staging, commit, push, merge or reset was performed.

The initial worktree had six untracked files: four Notices files and two
publication test runners. Those runners already expected API changes absent from
the checked-in service/DAO, so the initial Java 25 Maven test compilation failed.
They were retained and repaired as part of completing publication.

The four pre-existing Notices files match their saved SHA-256 hashes exactly.
No Technical Officer Dashboard, Login, attendance-management,
medical-management, notices-management, course-management or timetable source was
modified. At the user's follow-up request, the existing Student Dashboard's Grades
and GPA section now opens self-only academic views; its other features are preserved.
The shared navigation change only adds the authorized lecturer academic
routes. Existing profile, lecturer-workspace and team module regressions were run.

## Previously implemented functionality retained

- Decimal course-result calculations, configured grade-rule matching and unresolved-result states.
- Draft previews/saves, publication snapshots, confirmation UI and transactional publication validation.
- SessionManager login generations, lecturer assignments and active database-account checks.
- Existing marks form/table and assignment-scoped saves.
- Attendance Theory/Practical counts, including the distinction between no sessions and 0% present.
- Existing GPA/report models and academic FXML layouts.
- Existing dashboards, role routing, profile, attendance, medical and Notices behavior.

## Completed or corrected behavior

| Module | Delivered behavior | Readiness |
| --- | --- | --- |
| Results publication | Enabled only for active assigned lecturers; validated assignment types/context; saved-draft review; stale-input checks; timestamp; duplicate rejection; rollback; published-data protection | Ready for demonstration with confirmed eligibility and configured grade fixtures |
| SGPA / CGPA | Published snapshots only; exact credit weighting; chronological semester order; cumulative recomputation; SQL persistence; refreshed views; role/scope checks; repeat ambiguity withheld | Ready for ordinary attempts; repeat/replacement policy pending |
| Assessment management | Add/list/update/delete in assigned offerings; schema enum, name, maximum/precision and total-weight checks; referenced marks protected; published offerings protected; JavaFX editor/table | Ready |
| Marks entry | Assigned offering/enrollment/assessment selectors; numeric zero versus NULL; PRESENT/PENDING/ABSENT/MEDICAL preservation; validation; update/reload; session checks; underlying published marks protected | Ready |
| Eligibility | Complete-plan CA calculation; 40% CA threshold; missing assessment values remain unknown; real Theory/Practical/combined attendance; pending values persist as SQL NULL; mark/plan edits invalidate stale decisions | Calculation/pending workflow ready; final attendance decision blocked by policy |
| Reports | Individual student, intake batch and offering reports; published assessment details; PASS/FAIL/pending counts; averages excluding missing scores; empty reports; refresh; lecturer scope/student self-only access | Ready |
| Integration | Lecturer dashboard directly links all six academic modules in workflow order; Student dashboard links personal results/GPA; back-to-dashboard retains exact login; lecturer navigation allowlist corrected | Automated integration passed |

Assessment plan editing permits a partial plan while it is being constructed,
but prevents totals over 100%. Final result calculation requires exactly 100%.
Eligibility also treats an incomplete plan as pending. Assessment type, maximum
and weight cannot change once marks reference the assessment; name/date/description
can be edited only while the offering has no published result. Deletion is permitted
only for an unreferenced assessment and requires UI confirmation.

No existing export implementation was found in these report files, and no new export feature was introduced.

Mark changes invalidate the affected eligibility CA/overall decision and clear stale
draft grade fields within the same transaction. They never silently unpublish a result.
The pre-existing administrator mark-deletion route remains restricted to active
administrators and unpublished records. Technical Officers cannot edit marks.

GPA uses the published result's grade-point and PASS/FAIL snapshot. It does not
reinterpret historical results when grade_rules changes. Withdrawn enrollments,
cancelled offerings, unpublished/missing results and non-PASS/FAIL results do not
contribute to GPA. Registered-credit totals include applicable active enrollments,
while the GPA denominator includes only published GPA-applicable credits.
No additional course-type exclusions were invented.

Full-transcript GPA is visible to the student or a lecturer assigned to every
included offering. A lecturer with a narrower assignment can still use scoped
offering reports. Caller-supplied GPA totals are ignored on Save: all semester
values are recomputed in one transaction. Publication clears stale stored GPA
caches; loading/refreshing the GPA screen recalculates current published data.

## Actual database verification

Read-only `SHOW CREATE TABLE` inspected the configured local `ftms_group14` schema:

| Table | Verified relevant columns / constraints |
| --- | --- |
| assessments | assessment_id, offering_id, assessment_name, assessment_type, max_mark DECIMAL(7,2), weight_percentage DECIMAL(5,2), assessment_date, description, created_by; unique offering/name |
| marks | mark_id, assessment_id, enrollment_id, marks_obtained nullable, mark_status, entered_by, updated_at; unique assessment/enrollment |
| enrollments | enrollment_id, offering_id, student_id, attempt_no, status; WITHDRAWN and REPEAT exist |
| course_offerings | offering_id, course_id, semester_id, offering_group, status; CANCELLED exists |
| lecturer_course_assignments | lecturer_id, offering_id, assignment_type; PRIMARY / ASSISTANT / PRACTICAL |
| eligibility_records | ca_score, ca_eligible, attendance_percentage, attendance_eligible, overall_eligible are nullable; calculated_at, calculated_by |
| course_results | unique enrollment_id; nullable marks/grade fields; published, published_at, calculated_at, calculated_by |
| grade_rules | grade_letter, min_mark, max_mark, grade_point, is_pass, is_active |
| semester_results | unique student_id/semester_id; registered/earned credits, quality points, nullable sgpa/cgpa, calculated_at |
| semesters / academic_years | semester_no, academic-year label and start dates available for chronology |
| courses | credits; no dedicated GPA-exclusion column was found |
| attendance_sessions / attendance_records | Theory/Practical sessions; PRESENT, ABSENT, LATE, EXCUSED, MEDICAL records and timestamps |

**No migration is required.** No production table was created, dropped, truncated
or structurally changed. No production academic write was committed for testing.
New integration tests create connection-local temporary tables from the actual
DDL; closing the connection removes their fixtures. They preserve keys/checks but
omit foreign keys because MySQL temporary tables do not support those constraints.
The retained real-table publication runner separately tests real SQL/locks with
commit interception and rollback, and verifies original result/mark data from a
fresh connection. Rollback-only inserts/upserts can consume auto-increment IDs.

## Executed verification

The machine uses Java `25.0.4.1`, JavaFX `25`, MySQL Connector/J `9.4.0`,
and the installed NetBeans Maven executable. Maven is not on PATH. The compiler
used `release 25`. The sandbox initially denied access to the JavaFX JAR; the
authorized Maven run outside the sandbox compiled normally.

- Maven package: **BUILD SUCCESS**, artifact `target/OOPP_Group_14-1.0-SNAPSHOT.jar`.
- Default Maven tests: **17 tests**, zero failures/errors/skips.
- Explicit combined unit/MySQL run: **27 tests**, zero failures/errors/skips.
- Academic calculations: 10 discovered tests, including manually checked CA and GPA examples.
- FXML action mappings: 1 discovered test across eight real FXML/controller pairs, including both dashboards.
- Legacy wrapper: 6 discovered tests executing the existing assertion-rich runners.
- MySQL academic integration: 10 discovered scenarios covering CRUD, authorization,
  persistence, NULL/zero, pending eligibility, publication, stale drafts, transaction
  rollback, session replacement, cancellation, historical grade snapshots and privacy.
- Real JavaFX academic workflow: 14 checks.
- Real marks/JavaFX integration: 64 checks using isolated fixture tables and rollback.
- Lecturer navigation: 320 checks, including role routing/logout/session retention.
  Windows can clamp requested sizes; checks use actual viewport bounds and logs
  record requested versus observed sizes. This is not a claim of identical rendering
  at every requested DPI or window dimension.

Manually verified calculation fixtures:

- CA: `(10/20*10 + 45/50*30) / 40 * 100 = 80%`.
- SGPA: `(3*4 + 2*3 + 1*0) / 6 = 3.00`; earned credits = 5.
- Cumulative GPA: `(2*4 + 8*2) / 10 = 2.40`, not the average of 4.00 and 2.00.
- MySQL semesters deliberately have IDs 9 then 1 in chronological order:
  earlier `3*4/3=4.00`, later `2*3/2=3.00`, cumulative `18/5=3.60`.

Maven previously discovered zero executable tests because all test classes were
main-method runners. `LegacyRegressionTest` now bridges retained unit runners to
JUnit, and the new calculation/FXML/database tests are discoverable. The installed
JUnit 3.8.1 and Surefire 3.5.4 are used without downloading dependencies. Some retained
main-only classes still appear as zero-method classes in Surefire output; their
actual executions are recorded separately below. `failIfNoTests=true` prevents a
zero-total-test build from being accepted.

### Explicit regression results

| Runner | Actual result |
| --- | --- |
| ResultServiceTest | PASS, 89 assertions |
| ResultPublicationTest | PASS, 159 assertions |
| ResultPublicationDatabaseTest | PASS, 35 real MySQL checks, including four concurrent-write lock checks |
| ResultsViewSmokeTest | PASS, real FXML and publication confirm/cancel flow |
| ResultDatabaseSmokeTest --rollback-write | PASS, 11 grade rules, 24 assigned enrollments, rollback upserts |
| ProfileServiceTest | PASS, 79 assertions |
| ProfilePictureServiceTest | PASS, 93 assertions |
| CourseMaterialServiceTest | PASS, 21 assertions |
| NoticeServiceTest | PASS, 105 assertions |
| NoticeDatabaseSmokeTest --temporary-table | FAIL: pre-existing fixture assertion expects exactly four existing notices |
| NoticeViewSmokeTest | PASS, 219 assertions |
| LecturerAccessSmokeTest | PASS, 414 checks, scoped read-only integration |
| LecturerViewSmokeTest | PASS, 63 assertions |
| LecturerNavigationSmokeTest | PASS, 320 assertions |
| MarksEntryIntegrationTest --rollback-write | PASS, 64 assertions |
| AttendanceIntegrationTest | PASS, 60 checks, temporary tables |
| AttendanceViewSmokeTest | PASS, 71 checks, temporary tables |
| TechnicalOfficerDashboardSmokeTest | PASS, 336 checks |
| ProfileDatabaseSmokeTest --rollback-write | PASS, 145 checks |
| ProfilePictureDatabaseSmokeTest --rollback-write | PASS, 873 checks |
| ProfileViewSmokeTest | PASS, 241 checks |

Final explicit run: **21/22 runners passed** (20/21 retained runners plus the new academic UI runner). The full regression suite is **not all green** because the Notices fixture
failure remains. It was already documented before this work and the four Notices
files are byte-for-byte preserved. The old marks test's dependence on a particular
production fixture was removed; it now creates isolated MySQL fixtures. Lecturer
tests were updated for the newly authorized academic routes and current dashboard
root IDs. No passing result is inferred from source inspection.

### Reproduction

From the project root on Windows:

```powershell
# Default build + discovered unit/FXML-wrapper tests
./scripts/test-achala.ps1 -Offline

# MySQL academic tests and real JavaFX academic checks
./scripts/test-achala.ps1 -Offline -IncludeDatabase -IncludeJavaFx

# All old runners plus the new academic UI runner
# Expected overall failure until the unrelated Notices fixture is corrected.
./scripts/test-achala.ps1 -Offline -IncludeDatabase -AllRegressions
```

Set `-Maven` and `-Repository` when installed paths differ. Omit `-Offline`
on a machine that needs dependencies. MySQL must be reachable using the project's
existing DBConnection configuration. Do not place credentials in documentation.

The explicit Maven command for all discovered academic checks is:

```powershell
& 'C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd' '-Dmaven.repo.local=C:/Users/achal/.m2/repository' -o '-Dtest=AcademicCalculationsTest,AcademicFxmlTest,LegacyRegressionTest,AcademicMySqlIT' test
```

Logs and regression summaries are under `target/achala-*` and
`target/achala-regression-logs/`.

## Remaining policy decisions and acceptance limits

1. Official attendance threshold, separate Theory/Practical requirements, and LATE,
   EXCUSED or MEDICAL treatment remain unconfirmed. New overall eligibility stays
   NULL/PENDING, even when CA passes or fails. The application does not save an
   invented final eligibility decision.
2. Official grade-rule approval is still required. Configured/sample data is not
   represented as university policy.
3. Repeat examination, grade replacement and grade caps are unconfirmed. Ambiguous
   repeat GPA is withheld. No best/latest-attempt rule was invented.
4. Corrections to already-published results require a formal revision policy and
   audit design. Ordinary editing remains blocked.
5. Real credential-entry login and human visual acceptance at all Windows scaling
   settings were not performed. Automated sessions use existing active accounts or
   isolated fixture accounts; production authentication was not bypassed in code.
6. Production publication was deliberately not committed. Durable persistence was
   exercised inside isolated temporary-table fixtures; real production-schema
   publication was checked with rollback. Production restart persistence is not claimed.
7. Existing timestamp precision and absent input-revision history limit provenance
   detection for equivalent or externally edited inputs. Application source edits
   are guarded transactionally.

**Evaluation readiness:** Achala's implemented functions are ready for an assessed
demonstration using explicitly labeled test policies/fixtures. The complete live
academic workflow cannot be declared policy-approved while final attendance,
official grading, repeat handling and correction policies remain unresolved.
The pending-state behavior itself is implemented and tested. Unmarked or unresolved special-status attendance is displayed as unknown, while fully recorded all-ABSENT attendance is a genuine 0%.

## Recommended commits (only after approval)

1. `feat(academic): enforce scoped transactions and protect published source data`
   — shared guards, assignment validation, publication and marks consistency.
2. `feat(assessments): add lecturer assessment management`
   — assessment DAO/service/controller/FXML and marks selectors.
3. `fix(eligibility): preserve incomplete CA and pending attendance decisions`
   — nullable model, DAO, service, controller and UI.
4. `feat(gpa): calculate and persist scoped published-result GPA`
   — GPA DAO/service/controller, chronological ordering and repeat withholding.
5. `feat(reports): add scoped student batch and offering reports`
   — report DAO/service/controller/FXML and published assessment details.
6. `test(academic): discover regressions and verify integrated JavaFX MySQL workflow`
   — Maven, tests, navigation integration, verification script and documentation.
7. `feat(dashboards): connect lecturer academic workflow and student results`
   — lecturer cards/sidebar, student Grades and GPA links, self-only view presentation,
   assessment dashboard return, and dashboard navigation checks.

These commits share some files; review and stage named files or selected hunks in
dependency order. Do not use `git add .` or `git add -A`. Keep the four pre-existing
Notices files out of Achala's commits unless their owner explicitly approves.
No commit or push has been made.

## Complete file inventory

The generated inventory below identifies every tracked modification and relevant
untracked file in this implementation. The two publication tests were already
untracked when work began; they are retained/repaired, not claimed as newly authored.

| Git state | File |
| --- | --- |
| Modified | `docs/results-module.md` |
| Modified | `pom.xml` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/EligibilityController.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/GPAController.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/LecturerDashboardController.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/MarksController.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/ReportsController.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/ResultsController.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/StudentDashboardController.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/AssessmentDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/AttendanceEligibilityDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/CourseResultDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/EligibilityDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/LecturerCourseAssignmentDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/MarkDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/ReportDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/SemesterResultDAO.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/model/Assessment.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/model/EligibilityResult.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/model/Mark.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/service/EligibilityService.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/service/GPAService.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/service/MarkService.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/service/ReportService.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/service/ResultService.java` |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/util/NavigationUtil.java` |
| Modified | `src/main/resources/fxml/eligibility.fxml` |
| Modified | `src/main/resources/fxml/gpa.fxml` |
| Modified | `src/main/resources/css/lecturer_dashboard.css` |
| Modified | `src/main/resources/fxml/lecturer_dashboard.fxml` |
| Modified | `src/main/resources/fxml/student_dashboard.fxml` |
| Modified | `src/main/resources/fxml/marks_entry.fxml` |
| Modified | `src/main/resources/fxml/reports.fxml` |
| Modified | `src/main/resources/fxml/results.fxml` |
| Modified | `src/test/java/lk/ac/ruhuna/ftms/controller/LecturerNavigationSmokeTest.java` |
| Modified | `src/test/java/lk/ac/ruhuna/ftms/controller/LecturerViewSmokeTest.java` |
| Modified | `src/test/java/lk/ac/ruhuna/ftms/controller/MarksEntryIntegrationTest.java` |
| Modified | `src/test/java/lk/ac/ruhuna/ftms/dao/ResultDatabaseSmokeTest.java` |
| Modified | `src/test/java/lk/ac/ruhuna/ftms/service/LecturerAccessSmokeTest.java` |
| Created | `docs/achala-academic-completion.md` |
| Created | `scripts/test-achala.ps1` |
| Created | `src/main/java/lk/ac/ruhuna/ftms/controller/AssessmentsController.java` |
| Created | `src/main/java/lk/ac/ruhuna/ftms/dao/AcademicAccessDAO.java` |
| Created | `src/main/java/lk/ac/ruhuna/ftms/service/AcademicTransaction.java` |
| Created | `src/main/java/lk/ac/ruhuna/ftms/service/AssessmentService.java` |
| Created | `src/main/resources/fxml/assessments.fxml` |
| Created | `src/test/java/lk/ac/ruhuna/ftms/controller/AcademicViewsSmokeTest.java` |
| Created | `src/test/java/lk/ac/ruhuna/ftms/service/AcademicCalculationsTest.java` |
| Created | `src/test/java/lk/ac/ruhuna/ftms/service/AcademicDatabaseFixture.java` |
| Created | `src/test/java/lk/ac/ruhuna/ftms/service/AcademicFxmlTest.java` |
| Created | `src/test/java/lk/ac/ruhuna/ftms/service/AcademicMySqlIT.java` |
| Created | `src/test/java/lk/ac/ruhuna/ftms/service/LegacyRegressionTest.java` |
| Pre-existing untracked; repaired | `src/test/java/lk/ac/ruhuna/ftms/service/ResultPublicationDatabaseTest.java` |
| Pre-existing untracked; repaired | `src/test/java/lk/ac/ruhuna/ftms/service/ResultPublicationTest.java` |

Preserved pre-existing untracked files (not part of this implementation):

- `docs/notices-module.md`
- `src/test/java/lk/ac/ruhuna/ftms/controller/NoticeViewSmokeTest.java`
- `src/test/java/lk/ac/ruhuna/ftms/dao/NoticeDatabaseSmokeTest.java`
- `src/test/java/lk/ac/ruhuna/ftms/service/NoticeServiceTest.java`

Final tracked diff summary (untracked files are listed above and are not included by Git in diff --stat):

```text
 docs/results-module.md                             | 335 ++-------
 pom.xml                                            |   2 +
 .../ftms/controller/EligibilityController.java     | 233 +-----
 .../ac/ruhuna/ftms/controller/GPAController.java   |  28 +-
 .../controller/LecturerDashboardController.java    |   8 +-
 .../ac/ruhuna/ftms/controller/MarksController.java |  49 +-
 .../ruhuna/ftms/controller/ReportsController.java  | 759 +++----------------
 .../ruhuna/ftms/controller/ResultsController.java  |   9 +-
 .../java/lk/ac/ruhuna/ftms/dao/AssessmentDAO.java  |  77 +-
 .../ruhuna/ftms/dao/AttendanceEligibilityDAO.java  |   9 +-
 .../lk/ac/ruhuna/ftms/dao/CourseResultDAO.java     |  54 +-
 .../java/lk/ac/ruhuna/ftms/dao/EligibilityDAO.java |  27 +-
 .../ftms/dao/LecturerCourseAssignmentDAO.java      |   4 +-
 src/main/java/lk/ac/ruhuna/ftms/dao/MarkDAO.java   |  78 +-
 src/main/java/lk/ac/ruhuna/ftms/dao/ReportDAO.java | 467 ++----------
 .../lk/ac/ruhuna/ftms/dao/SemesterResultDAO.java   | 802 +++------------------
 .../java/lk/ac/ruhuna/ftms/model/Assessment.java   |   1 +
 .../lk/ac/ruhuna/ftms/model/EligibilityResult.java |  40 +-
 src/main/java/lk/ac/ruhuna/ftms/model/Mark.java    |  17 +-
 .../ac/ruhuna/ftms/service/EligibilityService.java | 273 ++-----
 .../java/lk/ac/ruhuna/ftms/service/GPAService.java | 416 ++---------
 .../lk/ac/ruhuna/ftms/service/MarkService.java     |   7 +
 .../lk/ac/ruhuna/ftms/service/ReportService.java   | 273 ++-----
 .../lk/ac/ruhuna/ftms/service/ResultService.java   |  79 +-
 .../lk/ac/ruhuna/ftms/util/NavigationUtil.java     |   2 +
 src/main/resources/fxml/eligibility.fxml           |   7 +-
 src/main/resources/fxml/gpa.fxml                   |   6 +-
 src/main/resources/fxml/marks_entry.fxml           |  14 +-
 src/main/resources/fxml/reports.fxml               |  15 +-
 src/main/resources/fxml/results.fxml               |  12 +-
 .../controller/LecturerNavigationSmokeTest.java    |  10 +-
 .../ftms/controller/LecturerViewSmokeTest.java     |   2 +-
 .../ftms/controller/MarksEntryIntegrationTest.java |  20 +-
 .../ruhuna/ftms/dao/ResultDatabaseSmokeTest.java   |   6 +-
 .../ftms/service/LecturerAccessSmokeTest.java      |   8 +-
 35 files changed, 952 insertions(+), 3197 deletions(-)
```

Index check: git diff --cached --name-only returned no paths. All changes remain unstaged. git diff --check passed.
