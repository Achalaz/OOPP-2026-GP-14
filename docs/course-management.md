# Admin Course Management integration

The existing module is connected through `AdminDashboardController.handleCourses()`
and `NavigationUtil.navigate()`. Both dashboard course buttons use that route.
Navigation loads one FXML/controller, injects `CourseService` with `CourseDAO` and
`CourseService::isCurrentUserAdmin`, then replaces the existing scene root.

The authorization supplier reads the current authenticated session on every
service operation. Navigation and direct FXML/controller entry also require an
active ADMIN. The existing BooleanSupplier service contract is retained. Back to
Dashboard preserves the exact login session and the current application window.
Admin login navigation also retains MainApp's screen-bounded window dimensions.

The original handler never displayed its loaded root or injected the service.
Additionally, `courses.fxml` had a blank line before the XML declaration, which
prevented it from loading. Both issues are fixed without a duplicate screen.

## Schema and CRUD

Inspected the live `ftms_group14.courses` table on 2026-10-21:

- Unique `course_code VARCHAR(30)`; `course_name VARCHAR(180)`.
- Positive `credits DECIMAL(4,1)`; existing service precision/range retained.
- `department_id` references `departments`.
- Type enum: CORE, ELECTIVE, OPTIONAL; boolean active status.
- `course_offerings.course_id` references courses with `ON DELETE RESTRICT`.

The existing DAO SQL is unchanged. A protected connection factory supports safe
transactional integration tests. MySQL remains the authority for duplicate codes
and department existence, including concurrent writes. The controller translates
constraint failures into useful messages and retains the form for correction.
Deletion still requires confirmation and cannot cascade through the verified
schema. No migrations or offering/enrollment CRUD were added.

## Executed verification

Environment: Java 25.0.4.1, JavaFX 25, Maven 3.8.5, local MySQL.

| Check | Result |
| --- | --- |
| Maven `verify` | BUILD SUCCESS, including JAR packaging |
| `CourseServiceTest` | 111 checks passed |
| `CourseDatabaseSmokeTest --safe-write` | 23 checks passed |
| `CourseViewSmokeTest` | 133 checks passed |
| Existing `TechnicalOfficerDashboardSmokeTest` | 336 checks passed |
| Existing `LecturerNavigationSmokeTest` | Fails its existing student `#nameLabel` assertion |

The lecturer failure was reproduced using a separately compiled copy of the
original HEAD `NavigationUtil`. The current student dashboard has `studentRoot`
and no `nameLabel`; those files and the existing test are outside this change.

Course UI checks execute both dashboard buttons, initial service/data loading,
selection, add/search/update, delete cancellation/confirmation, database error
messages, clear/refresh, Back, logout, FXML mappings, and unauthorized entry.
Screenshots at 800x600, 1024x600 and 1366x700 are saved under
`target/course-verification`. The course table and action/form controls fit these
windows, and the table scrolls. Credential-entry authentication was not automated;
navigation tests establish sessions using existing active database users.

The database test rolls back constraint checks, then commits/reloads/updates and
removes one uniquely named temporary course. It verifies the original course and
offering rows are unchanged. AUTO_INCREMENT values can advance during these tests.

These tests follow the repository's dependency-free main-method runner convention.
Surefire reports zero executed tests; run the runners explicitly after compilation.
Maven initially encountered an obsolete compiled `StudentAccessTest` with no source
on this branch. Its two class files were preserved in
`target/course-verification/stale-classes`, outside the active test output folder.
Missing Maven packaging dependencies were downloaded; the POM was not changed.

## Rerun from PowerShell

```powershell
mvn verify
$courseCp = 'target/test-classes;target/classes'
$courseRepo = "$env:USERPROFILE/.m2/repository"
$courseMysql = "$courseRepo/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
$courseFx = (Get-ChildItem "$courseRepo/org/openjfx/javafx-*/25/*-25-win.jar").FullName -join ';'
java -cp $courseCp lk.ac.ruhuna.ftms.service.CourseServiceTest
java -cp "$courseCp;$courseMysql" lk.ac.ruhuna.ftms.dao.CourseDatabaseSmokeTest --safe-write
java --module-path $courseFx --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp "$courseCp;$courseMysql" lk.ac.ruhuna.ftms.controller.CourseViewSmokeTest
```

Maven is not on this machine's PATH. The installed executable is
`C:/Users/achal/.m2/wrapper/dists/apache-maven-3.8.5-bin/5i5jha092a3i37g0paqnfr15e0/apache-maven-3.8.5/bin/mvn.cmd`.
The verified command uses `-Dmaven.repo.local=C:/Users/achal/.m2/repository`.
