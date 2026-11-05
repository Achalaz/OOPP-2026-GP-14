# Lecturer My Timetable

Lecturers can open **My Timetable** from the Lecturer Dashboard sidebar or its Teaching and Student Support card. The screen reuses `lecturer_dashboard.css` and displays day, start time, end time, course code/name, group, session type, room and effective dates. Refresh reloads the schedule; Back to Dashboard preserves the authenticated session and window size. Empty schedules show: "You have no assigned timetable entries. Contact your department or click Refresh later."

The service captures `SessionManager.SessionIdentity`, checks it before and after each read, and verifies an active `LECTURER` user with a lecturer profile in MySQL. Its public read method accepts no user ID. `TimetableDAO.findForLecturer` binds the authenticated ID to `WHERE t.lecturer_id = ?` and reuses the existing row mapper and Monday-to-Sunday/time ordering. Course assignment alone does not expose other lecturers' sessions on the same offering. Unassigned sessions are excluded; assigned historical entries remain visible with their effective dates.

Reads use a read-only transaction. The screen has no ID input or editing actions. Existing Admin service guards continue to control Add, Update and Delete. The existing background task runner handles loading, cancellation and stale-session results. No database migration or persistent data edits are required.

## Changed files

| Status | File | Purpose |
| --- | --- | --- |
| Added | `src/main/java/lk/ac/ruhuna/ftms/controller/LecturerTimetableController.java` | Read-only display, background refresh, empty/error states and return navigation |
| Added | `src/main/java/lk/ac/ruhuna/ftms/service/LecturerTimetableService.java` | Session-bound reads and database account authorization |
| Added | `src/main/resources/fxml/lecturer_timetable.fxml` | Lecturer timetable layout using existing CSS |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/controller/LecturerDashboardController.java` | My Timetable navigation handler |
| Modified | `src/main/resources/fxml/lecturer_dashboard.fxml` | Sidebar button and timetable card |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/dao/TimetableDAO.java` | Parameterized lecturer assignment filter |
| Modified | `src/main/java/lk/ac/ruhuna/ftms/util/NavigationUtil.java` | Lecturer route permission and window-size preservation |
| Added | `src/test/java/lk/ac/ruhuna/ftms/service/LecturerTimetableServiceTest.java` | Authorization, stale logins, denied Admin writes and FXML bindings |
| Added | `src/test/java/lk/ac/ruhuna/ftms/service/LecturerTimetableMySqlIT.java` | Real MySQL filtering, display data, empty schedules, reassignment, account revocation and read failures |
| Added | `src/test/java/lk/ac/ruhuna/ftms/controller/LecturerTimetableViewSmokeTest.java` | Dashboard entry points, rendering, Refresh, Back, empty states and small windows |
| Modified | `src/test/java/lk/ac/ruhuna/ftms/service/TimetableDatabaseFixture.java` | Lecturer service factory using existing temporary tables |
| Added | `docs/lecturer-timetable.md` | Change inventory and verification instructions |

## Verification

Verified on 2026-11-01 using Java 25.0.5 and JavaFX 25.

| Run | Result |
| --- | --- |
| Maven `package` | 62 tests passed; 0 failures, errors or skips; includes 6 new lecturer tests |
| `LecturerTimetableMySqlIT` | 8 tests passed; 0 failures, errors or skips |
| Existing `TimetableIntegrationTest` main runner | 61 checks passed |
| `LecturerTimetableViewSmokeTest` | 26 checks passed |
| Existing `TimetableViewSmokeTest` | 260 checks passed |
| Existing `TimetableTaskRunnerSmokeTest` | 32 checks passed |
| `git diff --check` | Passed |

Database tests write only connection-local temporary tables copied from the existing schema. Before/after fingerprints confirmed persistent table contents were unchanged. The existing JavaFX regression runner intentionally logs simulated database exceptions while testing error handling; it completed successfully. The initial sandboxed compilation could not access the cached JavaFX graphics JAR; the authorized rerun built successfully.

The JavaFX acceptance runner generates `target/timetable-verification/lecturer-timetable.png`, which was visually inspected. JavaFX smoke tests are excluded from Maven's default test selection; the MySQL test is also explicitly selected below. The existing timetable integration test has a `main` entry point and must be launched separately.

Run from the project directory in PowerShell with the configured local MySQL database available:

```powershell
$maven = 'C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd'
$repository = "$env:USERPROFILE/.m2/repository"
& $maven -o "-Dmaven.repo.local=$repository" package
& $maven -o "-Dmaven.repo.local=$repository" '-Dtest=LecturerTimetableMySqlIT' test

$classpath = "target/test-classes;target/classes;$repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
java -cp $classpath lk.ac.ruhuna.ftms.service.TimetableIntegrationTest

$fxModules = @('base','graphics','controls','fxml') | ForEach-Object {
    "$repository/org/openjfx/javafx-$_/25/javafx-$_-25-win.jar"
}
foreach ($runner in @(
    'lk.ac.ruhuna.ftms.controller.LecturerTimetableViewSmokeTest',
    'lk.ac.ruhuna.ftms.controller.TimetableViewSmokeTest',
    'lk.ac.ruhuna.ftms.util.TimetableTaskRunnerSmokeTest'
)) {
    java '-Djavafx.cachedir=target/javafx-cache' --module-path ($fxModules -join ';') `
        --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics `
        -cp $classpath $runner
    if ($LASTEXITCODE -ne 0) { throw "$runner failed" }
}
```
