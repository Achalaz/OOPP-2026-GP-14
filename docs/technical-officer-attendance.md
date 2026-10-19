# Technical Officer Attendance integration

Both Attendance Management buttons open the existing `attendance.fxml`. Choose a
department course offering, create or select a session, select an undergraduate,
and save PRESENT, ABSENT, LATE, EXCUSED, or MEDICAL. Select a saved record to update
it. Load Attendance reloads saved records; Load Batch Summary uses the existing
theory/practical/combined calculations and individual details.

Back to Dashboard preserves the login and window size. Logout clears the session
and opens login. My Profile retains its existing workflow. Medical, Notices, and
Department Timetable remain Coming Soon. MainApp still starts at login.fxml.

## Audit and integration decisions

- Starting branch: `Tecnical-offecr-dashboard`; starting HEAD: `c25393b`.
  The working tree and index were clean. No branch switch was performed.
- No optional `FTMS_Technical_Officer_Attendance_Integration.zip` or extracted
  `README_INTEGRATION.md` was found in the project, C:/Myproject, Downloads, or
  Desktop. No patch files were applied.
- Attendance already had session CRUD, record upsert, and summary queries.
  `AttendanceDAO` is split into `AttendanceSessionDAO`, `AttendanceRecordDAO`,
  and `AttendanceSummaryDAO`. These implementations are reused.
- CourseOffering and Enrollment are empty models. New immutable attendance choice
  records are read-only projections; no course/enrollment CRUD has been added.
- Live schema inspection confirmed the five attendance statuses, four session
  types, and unique attendance key `(session_id, enrollment_id)`.
- Department scope follows `users.department_id -> departments` and
  `course_offerings.course_id -> courses.department_id`. A matching
  `technical_officers.technical_officer_id = users.user_id` is also required.
- No explicit administrator attendance-management permission was found. Management
  is restricted to Technical Officers. Lecturers retain their existing assigned
  course summary access through the lecturer workspace; students have no new access.

## Authorization and persistence

AttendanceService binds to one login generation. Every management read/write
revalidates the session, active database role, officer subtype, and department.
Account/scope rows are locked with `FOR SHARE` until the transaction completes.
Existing DAOs now accept the transaction's connection while preserving their original
entry points. Statements use bound parameters; no form input becomes SQL.

Session and enrollment must belong to the same authorized offering. A saved session
cannot be moved to another offering. `marked_by` always comes from the authenticated
session, overriding caller input. This workflow does not assign a session conductor;
new sessions leave the nullable `conducted_by` unset, and existing values are preserved.
Missing/changed mappings, inactive accounts, other departments, and stale sessions
are rejected. A login change before commit rolls back the write. Session deletion
is not enabled by this integration.

Record reads exclude inconsistent session/enrollment offering pairs. Existing
attendance calculations and summary SQL are retained: only PRESENT counts as
attended; the existing distinctions between combined and per-enrollment percentages
are unchanged. No Marks, Eligibility, Results, GPA, Medical, or authentication logic
was modified. This integration grants no academic editing or publishing permission.

Database work runs outside the JavaFX application thread. Controls are disabled
while requests run. Delayed results cannot populate a different screen/login.
The form uses wrapping input groups and a ScrollPane; navigation keeps the native
window size and decorations.

## Files

Created:

- `src/main/java/lk/ac/ruhuna/ftms/dao/AttendanceAccessDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/model/AttendanceOffering.java`
- `src/main/java/lk/ac/ruhuna/ftms/model/AttendanceEnrollment.java`
- `src/test/java/lk/ac/ruhuna/ftms/service/AttendanceDatabaseFixture.java`
- `src/test/java/lk/ac/ruhuna/ftms/service/AttendanceIntegrationTest.java`
- `src/test/java/lk/ac/ruhuna/ftms/controller/AttendanceViewSmokeTest.java`
- `docs/technical-officer-attendance.md`

Modified:

- `src/main/java/lk/ac/ruhuna/ftms/controller/AttendanceController.java`
- `src/main/java/lk/ac/ruhuna/ftms/controller/TechnicalOfficerDashboardController.java`
- `src/main/java/lk/ac/ruhuna/ftms/dao/AttendanceSessionDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/dao/AttendanceRecordDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/dao/AttendanceSummaryDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/service/AttendanceService.java`
- `src/main/java/lk/ac/ruhuna/ftms/util/NavigationUtil.java`
- `src/main/resources/fxml/attendance.fxml`
- `src/main/resources/fxml/technical_officer_dashboard.fxml`
- `src/test/java/lk/ac/ruhuna/ftms/controller/TechnicalOfficerDashboardSmokeTest.java`
- `docs/technical-officer-dashboard.md`

## Verification

Executed with Java 25.0.4.1, JavaFX 25, and Maven 3.8.5 on 2026-10-18:

| Check | Result |
| --- | --- |
| Maven offline test-compile | Passed; 98 production and 17 test source files compiled |
| AttendanceIntegrationTest --live-read | 61 checks passed |
| AttendanceViewSmokeTest --snapshot --live-read | 75 checks passed |
| TechnicalOfficerDashboardSmokeTest --live-profile | 362 checks passed |
| LecturerAccessSmokeTest | 413 checks passed; live reads only |

The JDBC tests include all supported statuses, upsert/reload, summary percentages,
same-department but wrong-offering enrollment denial, other-department denial,
invalid/missing mappings, account deactivation/role changes, stale sessions, and
rollback after a login change between insert and commit. JavaFX tests exercise FXML
injection, both dashboard entry points, session creation, PRESENT/ABSENT saves,
update/reload, summaries, direct-screen denial, Back, Attendance Logout, and resizing.
The existing dashboard runner checks ADMIN/LECTURER/STUDENT routing and the remaining
Coming Soon dialogs. The lecturer suite verifies assigned attendance-summary access.

Tests are explicit main runners, following the existing project convention.
Maven compiles them but does not automatically execute these checks.

```powershell
mvn -o test-compile
$attendanceModules = (Get-ChildItem "$env:USERPROFILE/.m2/repository/org/openjfx/*/25/*-25-win.jar").FullName -join ';'
$attendanceCp = 'target/classes;target/test-classes;' + "$env:USERPROFILE/.m2/repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
java -cp $attendanceCp lk.ac.ruhuna.ftms.service.AttendanceIntegrationTest --live-read
java --module-path $attendanceModules --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $attendanceCp lk.ac.ruhuna.ftms.controller.AttendanceViewSmokeTest --snapshot --live-read
java --module-path $attendanceModules --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $attendanceCp lk.ac.ruhuna.ftms.controller.TechnicalOfficerDashboardSmokeTest --live-profile
java -cp $attendanceCp lk.ac.ruhuna.ftms.service.LecturerAccessSmokeTest
```

Maven is not on this machine's PATH. Its cached executable is
`C:/Users/achal/.m2/wrapper/dists/apache-maven-3.8.5-bin/5i5jha092a3i37g0paqnfr15e0/apache-maven-3.8.5/bin/mvn.cmd`.

The JDBC fixture reads schema definitions, creates connection-local temporary tables
with the same columns/indexes, and writes synthetic test rows only into those tables.
It executes real MySQL commits, reloads, updates, and rollback checks. Closing the
connection removes these temporary tables. MySQL temporary tables do not support
foreign keys, so production FK enforcement and committed writes to permanent academic
tables are **not** tested. No production schema/data migration, deletion, or update
was executed. The tests require permission to create temporary tables.

Credential-entry login has **not** been tested. Runtime checks use fixture or existing
account sessions inside test runners; no application authorization bypass was added.
The unchanged login controller routes through NavigationUtil, whose four role routes
are exercised by the dashboard regression test.

Live read-only checks found one mapped Technical Officer account and one active
account missing a valid officer/department mapping. That account is denied access.
An administrator must correct the existing account mapping before it can manage
attendance; this task does not alter accounts or grant cross-department access.

## Preview and manual acceptance

Run the normal application and sign in with a mapped active Technical Officer.
Use Attendance Management, select a course, and verify the allowed offerings with
your department's administrator. Any manual saves affect real academic records.

For a static preview, open `src/main/resources/fxml/attendance.fxml` in Scene Builder.
Do not remove its controller/security checks. Test-runner screenshots containing
only synthetic temporary-table fixtures are saved in `target/attendance-ui-check/`
at 1100x720, 900x600, and 640x480 window sizes.

No commit or push is part of this implementation. Review the dedicated staged diff
and approve a commit separately.
