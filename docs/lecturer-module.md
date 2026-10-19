# Lecturer module handoff

## Audit and implementation

The existing entry point stripped the controller and action handlers from lecturer FXML for a visual preview. Login routed every account to the generic dashboard. LecturerCourseAssignmentDAO, CourseMaterialDAO and CourseMaterial were empty. Marks had range validation but no lecturer assignment checks. Existing editable academic screens exposed broad student lists and draft results. The profile module already protected account fields and supported optional pictures.

The live MySQL column metadata was inspected read-only. It confirms lecturer_course_assignments(lecturer_id, offering_id), course_materials, enrollments, assessments, publication flags in course_results, and users.profile_picture. No schema changes, seed data, or production record writes were made during this implementation.

Required changes were: restore login startup and role routing; connect profile/back/logout; implement assignment-scoped materials and marks; add scoped read-only student/academic views; protect legacy service entry points; compile and execute regression runners. Existing lecturer FXML/CSS were reused rather than replaced.

## Delivered behavior

- MainApp opens login with real controllers/actions. Active lecturers go to the lecturer dashboard; admins go to the admin dashboard. Profile returns to the correct dashboard. Lecturer/admin/profile logout invalidates the session before navigation.
- The existing lecturer sidebar, welcome banner, lecturer identity, feature cards and stylesheet are retained. Every requested feature links to the lecturer workspace. Notices says Coming Soon; no notices view or course-offering CRUD was implemented.
- Profile reuses ProfileService/ProfileDAO and profile.fxml. Only name/email/picture can be changed. Picture reads tolerate a missing column; picture updates explicitly check it and return the existing migration error code.
- Materials support list/view, file upload, metadata edit, replacement and download for assigned offerings. Title, description, type, size and resource paths are validated. SQL writes include active lecturer and assignment predicates; arbitrary resource paths are never downloaded.
- Marks lists the assigned offering's actual assessment names, types, maxima and enrollment attempts. Select a row to enter/update marks. Blank means PENDING. NaN, infinity, negative values, excessive marks, withdrawn enrollments, mismatched assessments/attempts and unauthorized courses are rejected. Assignment/account/maxima rows are checked under transaction locks before lecturer writes. entered_by records the lecturer. The affected course result is unpublished in the same transaction; its official recalculation/publication workflow must run again.
- Undergraduate details are read-only, course-filtered and searchable, with registration, name, email, intake, level and enrollment details. No account/password editing controls exist.
- Eligibility uses the existing saved decisions and nullable CA/attendance percentages; it does not calculate or change decisions. Attendance uses the existing summary calculation, including unmarked counts and unknown percentages.
- Results reuse report labels and ResultService's saved values. Only published results are returned to lecturer callers. No grading or repeat formulas changed.
- GPA displays stored SGPA/CGPA for students in the assigned offering. Since semester_results has no publication flag, totals are withheld if any nonwithdrawn enrollment has a missing/unpublished course result or a result calculated after the GPA. This intentionally conservative rule can withhold older totals when a student has newly enrolled courses. No GPA is recalculated or saved by the lecturer screen.
- Medical views select only registration, dates and status for medicals explicitly linked to the assigned offering and its students. Reasons, documents, review notes and general medicals with no offering are withheld.
- New services bind to a session generation and recheck the current database account/assignment. Legacy editing and broad-read service methods now reject lecturer calls where applicable. Other roles' existing business rules are preserved. The workspace performs database and file work on daemon Tasks, rejects stale completions and clears records on logout.

## Storage and dependencies

Materials default to ${user.home}/.ftms/course-materials. Configure -Dftms.materials.dir=<shared-directory> when installations need shared files. Database rows store generated UUID filenames, not source paths. Back up this directory with MySQL; this is filesystem storage, not a remote upload server. Files are limited to 20 MiB and PDF/TXT/PNG/JPG/JPEG/PPTX/DOCX/XLSX/ZIP. Downloads save files without opening or executing them. Redirected storage directories and symlink resources are rejected.

Existing external/legacy resource_path values are displayed as material metadata but cannot be downloaded as arbitrary paths or URLs. Re-upload those files into managed storage. LINK creation is not included in this file-upload workflow. Old/replaced files and files from uncertain database outcomes are retained to avoid deleting possibly referenced content; administrative orphan cleanup is not automated.

Missing lecturer assignments give an empty course list and a dependency message. Missing assignment tables/schema or unavailable MySQL fail closed. No guessed assignments are created. No new migration is required on the inspected database. Installations without profile_picture may review the existing docs/database/add_user_profile_picture.sql; it was not executed.

## Verification and remaining acceptance checks

Java 25 and JavaFX 25 are installed. Maven is available through the existing 3.8.5 wrapper distribution (not PATH). Maven compile/test succeeds using the existing dependency cache outside the sandbox; the sandbox initially denied access to a cached JavaFX JAR. Surefire reports zero tests because this repository's tests are main-method runners. Run them explicitly after test compilation.

Executed runners and final counts are recorded below. Database integration runners are read-only; material file tests use temporary workspace files and an in-memory DAO. FXML/controller tests use in-memory services, not production authentication bypasses. JavaFX emits native cache warnings under the sandbox; the runners still load and exercise real FXML.

Not yet verified end-to-end: committed material/mark writes and persistence after application restart, native file chooser interaction, real credential login across all roles, and visual checks at different Windows scaling settings. No committed production write was used as a test. The module must not be described as fully acceptance-tested until these checks pass on a suitable test database. Result/eligibility recalculation after changed marks remains the responsibility of the existing academic workflow. General medical access and less conservative GPA publication policy require an explicit project policy before being broadened.

Example explicit runners (Windows classpath):

```powershell
$cp = 'target/test-classes;target/classes'
java -cp $cp lk.ac.ruhuna.ftms.service.CourseMaterialServiceTest
java -cp $cp lk.ac.ruhuna.ftms.service.ProfileServiceTest
java -cp $cp lk.ac.ruhuna.ftms.service.ProfilePictureServiceTest
java -cp $cp lk.ac.ruhuna.ftms.service.ResultServiceTest
$repo = "$env:USERPROFILE/.m2/repository"
java -cp "$cp;$repo/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar" lk.ac.ruhuna.ftms.service.LecturerAccessSmokeTest
$fx = (Get-ChildItem "$repo/org/openjfx/*/25/*-25-win.jar").FullName -join ';'
java --module-path $fx --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $cp lk.ac.ruhuna.ftms.controller.LecturerViewSmokeTest
java --module-path $fx --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $cp lk.ac.ruhuna.ftms.controller.ProfileViewSmokeTest
```

## File inventory

- M src/main/java/lk/ac/ruhuna/ftms/MainApp.java
- M src/main/java/lk/ac/ruhuna/ftms/controller/AdminDashboardController.java
- M src/main/java/lk/ac/ruhuna/ftms/controller/LecturerDashboardController.java
- M src/main/java/lk/ac/ruhuna/ftms/controller/LoginController.java
- M src/main/java/lk/ac/ruhuna/ftms/controller/ProfileController.java
- M src/main/java/lk/ac/ruhuna/ftms/dao/CourseMaterialDAO.java
- M src/main/java/lk/ac/ruhuna/ftms/dao/LecturerCourseAssignmentDAO.java
- M src/main/java/lk/ac/ruhuna/ftms/dao/MarkDAO.java
- M src/main/java/lk/ac/ruhuna/ftms/dao/ProfileDAO.java
- M src/main/java/lk/ac/ruhuna/ftms/model/CourseMaterial.java
- M src/main/java/lk/ac/ruhuna/ftms/service/AttendanceService.java
- M src/main/java/lk/ac/ruhuna/ftms/service/EligibilityService.java
- M src/main/java/lk/ac/ruhuna/ftms/service/GPAService.java
- M src/main/java/lk/ac/ruhuna/ftms/service/MarkService.java
- M src/main/java/lk/ac/ruhuna/ftms/service/MedicalService.java
- M src/main/java/lk/ac/ruhuna/ftms/service/ReportService.java
- M src/main/java/lk/ac/ruhuna/ftms/service/ResultService.java
- M src/main/java/lk/ac/ruhuna/ftms/util/NavigationUtil.java
- M src/main/resources/fxml/admin_dashboard.fxml
- M src/main/resources/fxml/lecturer_dashboard.fxml
- M src/test/java/lk/ac/ruhuna/ftms/controller/ProfileViewSmokeTest.java
- ?? docs/lecturer-module.md
- ?? src/main/java/lk/ac/ruhuna/ftms/controller/LecturerWorkspaceController.java
- ?? src/main/java/lk/ac/ruhuna/ftms/dao/LecturerViewDAO.java
- ?? src/main/java/lk/ac/ruhuna/ftms/model/LecturerTable.java
- ?? src/main/java/lk/ac/ruhuna/ftms/service/CourseMaterialService.java
- ?? src/main/java/lk/ac/ruhuna/ftms/service/LecturerAccessService.java
- ?? src/main/java/lk/ac/ruhuna/ftms/service/LecturerViewService.java
- ?? src/main/resources/fxml/lecturer_workspace.fxml
- ?? src/test/java/lk/ac/ruhuna/ftms/controller/LecturerViewSmokeTest.java
- ?? src/test/java/lk/ac/ruhuna/ftms/service/CourseMaterialServiceTest.java
- ?? src/test/java/lk/ac/ruhuna/ftms/service/LecturerAccessSmokeTest.java

## Executed results

- Maven test (includes compile/test-compile), Java 25: BUILD SUCCESS; Surefire executed 0 tests.
- LecturerAccessSmokeTest: 437 checks, read-only live database, including cross-course and legacy-service denials.
- LecturerViewSmokeTest: 63 real-FXML/async-controller checks.
- CourseMaterialServiceTest: 21 real-file/in-memory-DAO checks.
- ProfileServiceTest: 79 checks.
- ProfilePictureServiceTest: 93 checks.
- ProfileViewSmokeTest: 241 checks across all four roles, including role-specific dashboard return/logout.
- ResultServiceTest: 89 checks.
- ResultsViewSmokeTest: passed.
- git diff --check: passed.
