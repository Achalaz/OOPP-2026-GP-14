# Lecturer Course Materials completion — 23 October 2026

## Implementation and reuse

Both existing Lecturer Dashboard buttons already call `handleCourseMaterials()` and open the same `LecturerWorkspaceController` materials mode. Dashboard styling and handlers were retained. Assigned choices still come from `LecturerViewService` and `LecturerCourseAssignmentDAO`, with course code, course name, offering ID and group. `LecturerAccessService` and `SessionManager` revalidate the authenticated lecturer and assignment for each operation.

The existing `CourseMaterialService`, `CourseMaterialDAO`, `CourseMaterial` and workspace remain the primary file workflow. No new Java classes were added. Upload uses `chooseFile()` and `saveMaterial()`; list, save, download and export use the existing service methods.

The overlapping plural `CourseMaterialsController`, `CourseMaterialsService`, `CourseMaterialsDAO`, `CourseMaterials` and `course_materials.fxml` were inspected along with repository callers/tests. There were no other application callers of the plural service/DAO. The old FXML now includes the primary workspace through its retained controller. The plural service/DAO are compatibility facades for authorized reads and owner-only metadata edits. Arbitrary resource-path insertion/replacement and deletion are no longer supported by those APIs. Existing models, class names and database records remain.

## Completed behavior

- Upload: generated managed filename, nonempty regular file, supported extension, maximum 20 MiB, title 1–180 characters, description maximum 10,000 characters, supported file material type, active session and assigned offering validation.
- List: all materials in the authorized selected offering, with title, type, description, uploader, upload date and file availability. Search filters title, type, description and uploader. Refresh clears stale records and reloads. Empty assignment state is exactly: `No assigned courses. Contact the administrator.`
- Save: asynchronous work, duplicate-click prevention, success confirmation followed by refreshed list. If saving succeeds but refresh fails, the message explicitly says the save succeeded and asks for Refresh instead of another upload.
- Download: authorized selected record, existing Save File dialog, bounded managed-file reads and byte-preserving export. Legacy paths remain metadata and fail with an uploader re-upload message. Missing/empty/oversized files and invalid paths fail without claiming success.
- Edit: assigned lecturers may view/download each other's materials. Only the original uploader may change metadata or replace the file. Both service and SQL enforce ownership. SQL also requires the original offering and current assignment, preserves `material_id`, and never moves a material to another offering. UI read-only controls supplement the service and SQL checks.
- Back uses the same JavaFX scene/window and preserves login; logout invalidates the session. Database/storage work stays in background Tasks. Expired task completions clear material data and show a session-expiry message.

## Storage

Default: `<user-home>/.ftms/course-materials`. Override with JVM property `-Dftms.materials.dir=<directory>`. Rows store generated UUID filenames. PDF, TXT, PNG, JPG, JPEG, DOCX, PPTX, XLSX and ZIP remain supported. LINK is retained as legacy metadata; new file uploads use LECTURE_NOTE, PRACTICAL, ASSIGNMENT or OTHER.

If lecturer and student installations run on different machines, configure them to access the same physical shared directory with appropriate filesystem permissions. Identical path strings on separate local disks do not share files. No cloud service, web server, automatic migration, managed-file movement or managed-file deletion was introduced. Replaced files and uncertain database-outcome files are retained.

Managed storage rejects redirected roots and symlink file resources; generated basenames prevent path traversal. Export rejects redirected destination parents, symlink targets and destinations inside managed storage. It writes a temporary sibling and atomically replaces the destination only after successful writing and authorization revalidation. Filesystems that cannot perform the atomic move report failure; no unsafe truncating fallback is used. Only export temporary files are cleaned up. Keep storage and its parent directories protected from untrusted local filesystem modifications: path checks are not a sandbox against a local attacker concurrently replacing directory components. Availability is a point-in-time check, revalidated on download.

## Validation actually executed

Java 25.0.4.1, JavaFX 25, Maven 3.9.16 from the existing NetBeans installation. Maven is not on PATH; commands used `C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd` with `-Dmaven.repo.local=C:/Users/achal/.m2/repository` because the default repository was inaccessible.

- `mvn clean test`: BUILD SUCCESS; 39 tests, zero failures/errors/skips. The existing regression bridge executes the main-method file tests.
- `mvn package`: BUILD SUCCESS; creates `target/OOPP_Group_14-1.0-SNAPSHOT.jar`.
- `CourseMaterialServiceTest`: 45 checks, real temporary files with an in-memory DAO. Covers PDF upload/export byte equality, managed file existence, metadata/replacement editing with stable ID, validation, unavailable records/files, traversal/legacy rejection, owner-only updates, assigned co-lecturer download, unauthorized offering denial, expired/replaced sessions, and preservation of an existing destination after failed export.
- `LecturerViewSmokeTest`: 81 real-FXML/controller checks with service fixtures, including async work, search, metadata columns, owner controls, duplicate-save prevention, confirmation, refresh, compatibility FXML, back/session preservation and logout. Existing workspace modes also pass.
- `LecturerNavigationSmokeTest --materials-only`: 53 automated checks using live read-only MySQL and an invisible JavaFX window. Both Course Materials and Manage Materials open the workspace; Back works at four viewport sizes, preserves the session, and Logout opens Login.
- `CourseMaterialServiceTest --mysql-read`: 40 live read-only MySQL checks. Queries against `ftms_group14` succeeded with all eight existing material columns. Before/after snapshots of all eight columns confirm all **7 existing material rows unchanged**. Assigned listings, upload timestamps, legacy download rejection, authorization and compatibility scope passed. No schema migration needed or executed.
- No production INSERT, UPDATE, DELETE, schema changes, fabricated documents or managed storage mutations were used for validation.

Reproduce the opt-in MySQL check after Maven compilation:

```powershell
$repo = 'C:/Users/achal/.m2/repository'
$cp = "target/test-classes;target/classes;$repo/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
java -cp $cp lk.ac.ruhuna.ftms.service.CourseMaterialServiceTest --mysql-read
$fx = @('base','graphics','controls','fxml') | ForEach-Object { "$repo/org/openjfx/javafx-$_/25/javafx-$_-25-win.jar" }
java '-Djavafx.cachedir=target/javafx-cache' --module-path ($fx -join ';') --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $cp lk.ac.ruhuna.ftms.controller.LecturerViewSmokeTest
java '-Djavafx.cachedir=target/javafx-cache' --module-path ($fx -join ';') --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $cp lk.ac.ruhuna.ftms.controller.LecturerNavigationSmokeTest --materials-only
```

## Remaining acceptance work

Manual credential-entry login, native Choose/Save File dialog interaction, committed MySQL upload/edit persistence and restart verification were not executed. Service file round trips and SQL ownership predicates were verified separately; this is not a claim of a complete live database write integration test. Cross-machine shared-storage behavior, hostile concurrent filesystem replacement and native symlink creation were not tested. Demo paths without actual managed files remain unavailable until their uploader supplies the real file. Student Dashboard, Marks, GPA, Results, Attendance and unrelated production modules were not modified.

## Exact changed files and Git

Branch `tg2122` preserved. The initial tree was clean. No staging, commits, pushes, resets or branch changes.

1. `src/main/java/lk/ac/ruhuna/ftms/controller/CourseMaterialsController.java`
2. `src/main/java/lk/ac/ruhuna/ftms/controller/LecturerWorkspaceController.java`
3. `src/main/java/lk/ac/ruhuna/ftms/dao/CourseMaterialDAO.java`
4. `src/main/java/lk/ac/ruhuna/ftms/dao/CourseMaterialsDAO.java`
5. `src/main/java/lk/ac/ruhuna/ftms/model/CourseMaterial.java`
6. `src/main/java/lk/ac/ruhuna/ftms/service/CourseMaterialService.java`
7. `src/main/java/lk/ac/ruhuna/ftms/service/CourseMaterialsService.java`
8. `src/main/resources/fxml/course_materials.fxml`
9. `src/main/resources/fxml/lecturer_workspace.fxml`
10. `src/test/java/lk/ac/ruhuna/ftms/controller/LecturerNavigationSmokeTest.java`
11. `src/test/java/lk/ac/ruhuna/ftms/controller/LecturerViewSmokeTest.java`
12. `src/test/java/lk/ac/ruhuna/ftms/service/CourseMaterialServiceTest.java`
13. `docs/lecturer-course-materials.md` (new report)
