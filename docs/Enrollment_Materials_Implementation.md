# Enrollment and student course materials implementation

Implemented on 2026-10-23 on branch `tg2122`. The working tree was clean before work began.
No commits or pushes were made. No production data or database schema was changed.

## Existing components reused

- `EnrollmentDAO.StudentCourse` and the unchanged `EnrollmentDAO.findByStudent(Connection, studentId)` query.
- `StudentCourseService`, `StudentAccessService`, `SessionManager`, `StudentDashboardController.handleCourses()`.
- `StudentRecordsController` and `student_courses.fxml`, retaining all six existing course columns.
- The previously empty `Enrollment` model now represents enrollment records and their joined display values.
- `CourseOffering` and `CourseOfferingDAO`'s existing query/mapper for offering choices.
- `CourseMaterial`, `CourseMaterialDAO`, and lecturer `CourseMaterialService`.
- Existing admin dashboard, `NavigationUtil`, and `profile.css`.

The alternative `CourseMaterialsDAO`/`CourseMaterialsService` are compatibility facades for the primary lecturer implementation.
`CourseMaterialsController` includes `lecturer_workspace.fxml`. They were inspected and preserved; no duplicate material model,
DAO, service, dashboard, or student course screen was created.

## New production files

Under `src/main/java/lk/ac/ruhuna/ftms/`:

- `controller/AdminEnrollmentController.java`
- `controller/StudentCourseMaterialsController.java`
- `service/AdminEnrollmentService.java`
- `service/StudentCourseMaterialService.java` — student read-only authorization API.
- `service/MaterialStorage.java` — package-private extraction of shared storage operations, with no role permissions.

Under `src/main/resources/fxml/`:

- `admin_enrollments.fxml`
- `student_materials.fxml`

New verification files:

- `src/test/java/lk/ac/ruhuna/ftms/service/EnrollmentMaterialsAccessTest.java`
- `src/test/java/lk/ac/ruhuna/ftms/service/EnrollmentMaterialsMySqlIT.java`
- `src/test/java/lk/ac/ruhuna/ftms/service/EnrollmentMaterialsLiveMySqlIT.java`
- `src/test/java/lk/ac/ruhuna/ftms/controller/EnrollmentMaterialsViewSmokeTest.java`
- This implementation report.

## Modified production files

Under `src/main/java/lk/ac/ruhuna/ftms/`:

- `controller/AdminDashboardController.java` — enrollment navigation.
- `controller/StudentRecordsController.java` — selection mapping and materials navigation only; other record views retained.
- `dao/EnrollmentDAO.java` — enrollment queries/writes and student material permission checks.
- `dao/CourseOfferingDAO.java` — transaction-aware eligible offering query reusing its existing mapper.
- `dao/CourseMaterialDAO.java` — transaction-aware read overload reusing existing material mapping.
- `model/Enrollment.java` — populated the existing empty model.
- `service/CourseMaterialService.java` — delegates existing storage operations to shared storage code.
- `util/NavigationUtil.java` — new role guards and controller context configuration in the existing Stage.

Under `src/main/resources/fxml/`:

- `admin_dashboard.fxml` — sidebar entry and dashboard shortcut.
- `student_courses.fxml` — selection-dependent View Course Materials button.

No Marks, GPA, Results, Attendance, Timetable, or Medical implementation was modified.

## Verified live database facts and enrollment policy

Inspection used `SHOW CREATE TABLE` on the live `ftms_group14` database, foreign-key metadata, and trigger metadata.

- Enrollment columns: `enrollment_id`, `offering_id`, `student_id`, `attempt_no`, `status`, `enrolled_at`.
- Status enum: `ENROLLED`, `WITHDRAWN`, `COMPLETED`, `REPEAT`; default `ENROLLED`.
- Attempt is a positive signed integer with default 1; the database supplies no smaller upper limit.
- Unique key: `(offering_id, student_id, attempt_no)`. A withdrawn row still owns this identity.
- Foreign keys reference the student subtype row and the course offering.
- Dependent tables include marks, attendance records, eligibility records, and course results.
- Offering statuses: `PLANNED`, `ACTIVE`, `COMPLETED`, `CANCELLED`; group column is `offering_group`.
- No enrollment/offering transition triggers were present.

Application policy: new enrollments and reactivation (`ENROLLED`/`REPEAT`) require a planned or active offering.
Attempt 1 initially receives `ENROLLED`; later attempts receive `REPEAT`. Administrators may select any verified enrollment
enum status, subject to the offering rule; cancelled offerings permit withdrawal only. Concurrent stale status edits are rejected.
No additional institutional prerequisite or maximum-attempt policy was inferred.

Only enrollment INSERTs and status UPDATEs are performed. Enrollment identity, attempts, and dependent academic records are
never deleted or replaced. Missing student profiles produce an actionable error rather than automatic repair.

No migration is needed.

## Workflow and permissions

Admin: Dashboard -> Student Enrollments -> search/select student -> search/select offering -> enter positive attempt -> Enroll.
The enrollment table supports search, refresh, and status updates. Loading, empty, duplicate, validation, and success/error feedback
are present. Database work runs on background JavaFX tasks, and navigation cancels outstanding work.

Every admin operation checks its original login identity. Database transactions revalidate the active ADMIN account, lock the
student/offering/enrollment records as appropriate, and recheck the session before commit. Failures roll back; database uniqueness
also protects concurrent duplicate inserts. JavaFX button state is not an authorization boundary.

Student: Dashboard -> My Courses -> select a course -> View Course Materials. The shared generic records table retains a mapping
by row object identity to the original `StudentCourse`. Sorting and refresh do not derive an offering ID from row index or course code.

The student material service uses `StudentAccessService`, never lecturer authorization. Listing and every download revalidate the
active database account and student profile, require an enrollment in `ENROLLED`, `REPEAT`, or `COMPLETED`, and reject cancelled
offerings. Completed-course materials remain readable. A material ID must belong to the authorized offering. Permission rows stay
share-locked during a download/export transaction. Stale sessions cannot adopt a later login.

The screen has metadata, description, upload time, availability, Download Selected, Back to My Courses, Back to Student Dashboard,
Refresh, and Logout. It has no upload/edit/delete actions. Empty offerings show "No materials available for this course."
Unavailable resources show "File unavailable. Contact your lecturer.". Unavailable files cannot be selected for download.

Navigation guards protect the new admin/student resources and both primary/legacy lecturer materials routes. Back navigation keeps
the session and Stage; Logout clears the session.

## File storage and actual live data

Both roles use `ftms.materials.dir`, defaulting to `<user-home>/.ftms/course-materials`. Only server-generated managed filenames are
read. Legacy relative paths, absolute paths, traversal, and external URLs are not interpreted as downloadable files. Non-managed
resource paths are removed from student-facing material objects. File leaves are opened without following links; redirected storage
directories and destination paths are rejected. Reads retain the existing 20 MiB bound. Export stages a temporary file and rechecks
authorization before atomically replacing a destination, preserving existing destination bytes on failure.

Malformed/unavailable storage configuration still allows authorized metadata to be shown. Student errors do not reveal absolute
managed-storage paths. Database/file operations run off the JavaFX thread; the native save chooser runs on the UI thread.

The actual live database differed from the ZIP description before any edits:

| Offering/course | Live material records |
| --- | ---: |
| 3 / ICT2122 | 4 |
| 4 / ICT2132 | 2 |
| 5 / ICT2142 | 1 |
| 6 / unauthorized to `stu` | 1 |

All original seven legacy metadata rows and the existing eighth managed DOCX row were retained. The eighth file was already present
on this machine. A read-only service test verified its 1,796,053 downloaded bytes against the stored file. The seven legacy files
remain unavailable; no fake PDFs were generated or inserted.

The PDF upload/download integration test uses the real existing `docs/SRS/OOP_project_group_14_SRS.pdf`, temporary material metadata,
and temporary managed storage under `target`. Lecturer upload, student read, export, revocation, and byte equality are exercised.

Cross-computer access is **not verified**. The default directory is local to each operating-system account; sharing MySQL alone does
not share files. Separate computers require `ftms.materials.dir` to resolve to the same real shared directory with suitable access.
Test that deployment with a real upload and download on both computers before claiming it works.

## Verification results

Java: Oracle JDK 25.0.4.1. JavaFX: 25. Maven: installed Maven 3.8.5.
Maven was not on PATH, so the existing Maven executable and dependency cache under the user's `.m2` directory were used.

Commands (with the installed Maven executable substituted for `mvn`):

```text
mvn clean test
mvn -Dtest=EnrollmentMaterialsMySqlIT,EnrollmentMaterialsLiveMySqlIT,StudentPortalMySqlIT test
mvn package
```

Automated offline suite: 44 tests passed, zero failures/errors. This includes existing lecturer file/storage regression checks.
The main-based legacy regression runners are invoked by the existing `LegacyRegressionTest` bridge.

Both requested builds succeeded: `mvn clean test` and `mvn package`. The package is
`target/OOPP_Group_14-1.0-SNAPSHOT.jar`. Existing Maven/JDK deprecation and JavaFX classpath warnings are non-failing.

Database integration: the new transactional suite, separate live SELECT-only verification, and existing StudentPortalMySqlIT are
explicit opt-in tests. **19 tests passed** (9 new transactional cases, 1 live read-only case, and 9 existing student portal cases),
with zero failures/errors. Integration writes use connection-local TEMPORARY copies of live DDL; no production writes occur. These
copies preserve enum/check/unique constraints. MySQL does not allow foreign keys on temporary tables, so foreign keys were inspected
in the live schema rather than claimed as exercised by the temporary fixtures.

Automated JavaFX verification: 30 checks passed using hidden stages and live SELECTs only. It exercises dashboard entry,
student/offering search, course sorting, selection-dependent buttons, all three authorized material views, refresh, same-Stage back
navigation, and logout. Rendered snapshots were inspected under `target/enrollment-materials-ui/`. This is automated UI verification,
not a claim that a person manually clicked the native file chooser or logged into real accounts.

Manual UI acceptance still to perform:

1. Start the app with Java 25 and sign in as an active Admin using the normal login form.
2. Open Student Enrollments from both the sidebar and dashboard shortcut.
3. Search an existing student by ID, registration, name, and username; select the correct student.
4. Search and select a planned/active offering; verify course, semester, academic year, group, and status.
5. In a test database, enroll a student with a new offering/attempt; verify the new table row and success feedback.
6. Repeat the same enrollment and verify the duplicate warning; try an invalid attempt.
7. Verify inactive/non-student/missing-profile accounts cannot enroll and cancelled offerings cannot be chosen.
8. Select an enrollment and update status. Verify WITHDRAWN keeps academic records and revokes student material access.
9. Return to Admin Dashboard, then log out.
10. Sign in as the student and open My Courses. Verify all six original columns and any newly authorized enrollment.
11. Sort the table, select ICT2122, and open materials. On this live database expect four records, including the existing extra upload.
12. Return and check ICT2132 (two records) and ICT2142 (one record). Offering 6 must remain inaccessible to `stu`.
13. Verify no upload/edit/delete actions exist. Check legacy metadata availability messages and an empty authorized offering.
14. In a test database/storage setup, sign in as an assigned lecturer and upload a real PDF through the existing lecturer UI.
15. Sign in as an enrolled student, refresh, select the upload, and use Download Selected to save it. Compare source/destination hashes.
16. Exercise native save-dialog cancellation and overwriting an existing destination. Confirm successful saves and failure feedback.
17. Revoke enrollment/deactivate the account after listing; confirm the next service download is rejected.
18. Test Back to My Courses, Back to Student Dashboard, refresh, and logout, including navigation during a background load.
19. Repeat a real upload/download across separate machines only after configuring shared managed storage.

Service-level validation, authorization, preservation, rollback, legacy errors, and byte comparisons are already automated; the
manual steps cover visible interaction and deployment acceptance without changing the supplied production fixture during this work.

## Remaining limits and Git safety

- Existing legacy metadata has no corresponding managed files. Lecturers must upload real resources to make those records downloadable.
- Human interaction with login, native FileChooser, and real lecturer upload UI remains manual acceptance work.
- Cross-computer storage is not configured or tested by this change.
- No database migration, reset, deletion, fake file insertion, or unrelated module change was made.
- Branch: `tg2122`; changes are left unstaged and uncommitted for review.
- Final Git status: 10 modified tracked files and 12 new untracked files. `git diff --check` passed.
