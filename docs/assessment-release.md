# Assessment release and student view

Implementation completed in the existing assessment and student records modules. Existing uncommitted changes were preserved and completed in place. No duplicate controller, submission feature, grading workflow, commit, or push was added.

## Database inspection and deployment

Read-only JDBC inspection of the configured `ftms_group14` database on 2026-10-30 reported MySQL **8.0.46**. `SHOW CREATE TABLE assessments` contained these columns:

`assessment_id`, `offering_id`, `assessment_name`, `assessment_type`, `max_mark`, `weight_percentage`, `assessment_date`, `description`, `created_by`, `created_at`, `updated_at`.

There were no publication columns. Existing keys, foreign keys, and maximum/weight checks remain unchanged. The migration is [database/add_assessment_publication.sql](database/add_assessment_publication.sql):

```sql
ALTER TABLE assessments
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN published_at TIMESTAMP NULL DEFAULT NULL;
```

**Live migration status: not applied; awaiting the requested review approval.** Apply the script once to `ftms_group14` before running the new build against the live database. Do not rerun it when both columns already exist. No startup migration or automatic backfill is performed. Existing and new assessments default to Draft. No live assessment, mark, result, eligibility, or GPA records were modified by this work.

The integration fixture applies this exact script only to its connection-local temporary assessment table when the live schema has not yet been migrated. This allows validation without changing the deployed schema.

## Lecturer behavior and authorization

Lecturer Dashboard → Manage Assessments → select an assigned offering and a saved assessment → Release Assessment.

The existing controller has Draft/Released and release timestamp columns. Its confirmation dialog explains that saved assessment details become visible and that release protects the assessment from further edits/deletion. Cancel leaves it Draft. Selection of a released assessment disables its edit, delete, and release controls.

`AssessmentService.release` uses the existing serializable academic transaction. It rechecks the authenticated session, active database account, LECTURER role, lecturer profile, valid assignment, non-cancelled offering, assessment ownership, and current draft state. Permission rows and the offering are locked for the transaction; session changes or interruption roll the write back. PRIMARY, ASSISTANT, and PRACTICAL assignments follow the existing assignment policy.

`AssessmentDAO.release` uses a prepared, conditional update of only the assessment publication flag/timestamp. CRUD cannot publish through caller-supplied model fields. Service checks and DAO predicates protect released assessments against stale or altered objects. There is no unrelease API: a correction policy and explicitly authorized history-preserving workflow would be needed before adding one.

## Student behavior and authorization

Student Dashboard → My Assessments (sidebar) or View Released Assessments (card) → Back to Student Dashboard.

`student_assessments.fxml` reuses `StudentRecordsController`. It displays enrolled course, semester/offering, assessment name/type, maximum marks, weight, assessment date, description, release status, and release timestamp. A read-only text area displays the selected assessment's full description. There are no create, edit, delete, or release controls.

`StudentAssessmentService` binds to `SessionManager` through `StudentAccessService`, which rechecks the current login, active STUDENT database account, and student profile. It retrieves courses using the existing `EnrollmentDAO`. Following the existing course-material access policy, ENROLLED, REPEAT, and COMPLETED enrollments in PLANNED, ACTIVE, or COMPLETED offerings are readable. WITHDRAWN enrollments and CANCELLED offerings are excluded. Duplicate attempts do not duplicate assessments.

Direct `getAssessments(offeringId)` calls verify enrollment and reject inaccessible or altered IDs. The DAO additionally requires publication and valid student enrollment in its prepared SELECT. Navigation remains role-restricted, and asynchronous results from an expired student login are discarded by the existing controller.

## Separation from results

Release does not call result publication, mark saving, eligibility invalidation, or GPA calculation. ResultService, MarkService, EligibilityService, and GPAService were not changed. Released assessments still support the existing marks entry workflow, subject to its existing protections for published results. Students receive assessment metadata only.

## Executed verification

Java **25.0.4.1**, JavaFX **25**, and the project's Maven installation were used. A sandbox restriction on the JavaFX graphics JAR required the authorized Maven rerun outside the sandbox.

| Check | Actual result |
| --- | --- |
| Maven compilation | Passed; 139 production sources compiled with `javac release 25` |
| `mvn -o package` | Passed; 45 tests, zero failures/errors/skips; JAR produced |
| `AssessmentPublicationMySqlIT` | Passed; 11 tests against real MySQL temporary tables |
| `AcademicMySqlIT` | Passed; 16 existing academic regression tests |
| `AcademicAcceptanceSmokeTest` | Passed; 98 real JavaFX/login/dialog/navigation/MySQL checks |
| Expanded run including `StudentPortalMySqlIT` | 46 tests total; 45 passed, one error because the live schema lacks `attendance_medical_links` |
| `git diff --check` | Passed |

The expanded student failure is `StudentPortalMySqlIT.testAttendanceAndCoursesAreSelfOnlyAndReadOnly`: `Table 'ftms_group14.attendance_medical_links' doesn't exist`. That unrelated attendance schema/module was not modified.

| Requested scenario | Evidence |
| --- | --- |
| Lecturer creates assessment; existing CRUD works | Integration CRUD and real JavaFX create/update/confirmed delete |
| Draft hidden from students | Integration read returns no drafts; cancelled confirmation remains Draft |
| Assigned lecturer releases | Service integration plus real JavaFX confirmation |
| Enrolled student sees released metadata | Integration and both student dashboard entry points |
| Other offering inaccessible | Direct altered-ID and cross-student integration checks; second student UI sees only its offerings |
| Student cannot edit/release | Service rejections, role-restricted navigation, and read-only UI controls |
| Unassigned/inactive lecturer rejected | Integration covers assignment removal, wrong role, inactive account, missing profile, and altered assessment ID |
| Final results unchanged | Exact before/after rows for marks, course results, semester results, and eligibility; existing result publication/GPA UI still passes |
| Persistent publication state | Committed SQL flag/timestamp and fresh service reads verified; a real application/database process restart was **not** tested |
| Dashboard navigation | Both student links, Back to Dashboard, lecturer assessment route, login/logout |
| FXML fields/handlers valid | Reflection validation and actual FXMLLoader/controller execution |
| Failure safety | Injected SQL failure, logout during update, and cancellation roll back release |

Reproduce the focused database tests with:

```powershell
& 'C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd' -o '-Dmaven.repo.local=C:/Users/achal/.m2/repository' '-Dtest=AssessmentPublicationMySqlIT,AcademicMySqlIT,StudentAccessTest,AcademicFxmlTest,LegacyRegressionTest' test
```

The JavaFX runner is `lk.ac.ruhuna.ftms.controller.AcademicAcceptanceSmokeTest`; `scripts/test-achala.ps1 -IncludeDatabase -IncludeJavaFx -Offline` invokes it alongside the existing academic checks. Logs from this session are under `target/assessment-package.log`, `target/assessment-publication-tests.log`, `target/assessment-ui-tests.log`, and `target/assessment-schema-inspection.log`.

## Exact feature files in the working tree

Production Java:

- `src/main/java/lk/ac/ruhuna/ftms/model/Assessment.java`
- `src/main/java/lk/ac/ruhuna/ftms/dao/AssessmentDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/dao/EnrollmentDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/service/AssessmentService.java`
- `src/main/java/lk/ac/ruhuna/ftms/service/StudentAssessmentService.java` (new)
- `src/main/java/lk/ac/ruhuna/ftms/controller/AssessmentsController.java`
- `src/main/java/lk/ac/ruhuna/ftms/controller/StudentRecordsController.java`
- `src/main/java/lk/ac/ruhuna/ftms/controller/StudentDashboardController.java`
- `src/main/java/lk/ac/ruhuna/ftms/util/NavigationUtil.java`

FXML:

- `src/main/resources/fxml/assessments.fxml`
- `src/main/resources/fxml/student_assessments.fxml` (new)
- `src/main/resources/fxml/student_dashboard.fxml`

Tests:

- `src/test/java/lk/ac/ruhuna/ftms/service/AssessmentPublicationMySqlIT.java` (new)
- `src/test/java/lk/ac/ruhuna/ftms/service/AcademicDatabaseFixture.java`
- `src/test/java/lk/ac/ruhuna/ftms/service/StudentAccessTest.java`
- `src/test/java/lk/ac/ruhuna/ftms/controller/AcademicAcceptanceSmokeTest.java`

SQL and documentation:

- `docs/database/add_assessment_publication.sql` (new)
- `docs/assessment-release.md` (new)

Remaining deployment work: approve/apply the additive live migration, then verify publication after an actual application restart. The unrelated missing attendance table also prevents the broader student integration suite from passing in this database.
