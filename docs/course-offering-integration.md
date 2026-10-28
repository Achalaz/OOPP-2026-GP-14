Course Offering integration verification — 22 October 2026

Implemented on `tg2075-course-offering`. The working tree was clean before this work. No branch switch, staging, commit or push was performed.

1. Root cause

`course_offerings.fxml` referenced `#handleCourseOfferings`, which does not exist in `CourseOfferingController`; this prevents FXMLLoader from completing. The dashboard's similarly named handler used a separate loader but never injected the service or displayed the loaded root. The dashboard also lacked the card/sidebar entry, and NavigationUtil had no offering initialization or access guard.

The live schema exposed another issue: the existing plain DELETE could cascade into academic records. The incoming offering foreign keys are CASCADE for `assessments`, `lecturer_course_assignments`, `attendance_sessions`, `enrollments`, `course_materials` and `timetable_entries`; `medicals` uses SET NULL.

2. Files changed

| File | Change |
| --- | --- |
| `src/main/java/lk/ac/ruhuna/ftms/controller/AdminDashboardController.java` | Replace incomplete loader with the existing navigation helper; remove unused/duplicate imports. |
| `src/main/java/lk/ac/ruhuna/ftms/controller/CourseOfferingController.java` | Direct-load Admin guard, one-time injection, Back handler, safe selection/reset behavior and readable constraint errors. |
| `src/main/java/lk/ac/ruhuna/ftms/dao/CourseOfferingDAO.java` | Transaction-protected deletion that refuses linked offerings. |
| `src/main/java/lk/ac/ruhuna/ftms/util/NavigationUtil.java` | Admin route guard, centralized service initialization and window-size preservation; restore the Student route restriction required by the existing security test. |
| `src/main/resources/fxml/admin_dashboard.fxml` | Matching Course Offerings card/sidebar button and a scrollable sidebar. |
| `src/main/resources/fxml/course_offerings.fxml` | Remove invalid action, add Back, reuse Admin styles and allow scrolling. |
| `src/test/java/lk/ac/ruhuna/ftms/service/CourseOfferingServiceTest.java` | Five Maven-discoverable authorization, delegation, validation and error-propagation tests. |
| `src/test/java/lk/ac/ruhuna/ftms/dao/CourseOfferingDatabaseSmokeTest.java` | Explicit live MySQL CRUD/constraint/preservation checks with fixture cleanup. |
| `src/test/java/lk/ac/ruhuna/ftms/controller/CourseOfferingViewSmokeTest.java` | Real FXML/actions/navigation/layout tests with memory CRUD and optional live reads. |
| `scripts/test-course-offerings.ps1` | Reproducible Maven, service, optional database and JavaFX checks. |
| `docs/course-offering-integration.md` | This report. |

`CourseOffering`, `CourseOfferingService`, the database schema, courses and semesters were reused. The service already authorizes every public operation, so no service rewrite was necessary.

3. Dashboard integration

Both new entries invoke `AdminDashboardController.handleCourseOfferings()`, which delegates through the existing helper to `NavigationUtil.navigate(..., "/fxml/course_offerings.fxml", ...)`. The new card uses the requested title, description and button text with the existing dashboard style classes. All previous entries remain available. Timetables retains its existing informational placeholder.

4. FXML handlers and navigation

The invalid action was removed. `#handleBackToDashboard` now maps to an annotated controller method using NavigationUtil and `/fxml/admin_dashboard.fxml`. Runtime FXMLLoader checks succeeded, all annotated controller fields were injected, and every offering action was exercised. Back keeps the exact session identity, Scene, Stage and window dimensions. Screens were tested at 800x600, 1024x600 and 1366x700; content/table scrolling accommodates smaller windows.

5. Service initialization and authorization

NavigationUtil loads FXML, obtains its controller, and calls the existing setter once with `new CourseOfferingService(new CourseOfferingDAO(), CourseService::isCurrentUserAdmin)` before installing the root. The setter rejects repeated injection. Navigation and direct FXMLLoader access require an active Admin session. The existing service reevaluates authorization for all reads, dropdown loads, searches, creates, updates and deletes, including after logout or a role/session change.

6. CRUD and data preservation

The existing DAO and controller methods are retained. Selection now keeps the actual identity of a course that was subsequently deactivated, avoiding accidental reassignment from stale form state. Successful search/refresh clears stale editing selection, and Refresh reloads dropdowns and clears the search.

The live database confirms a unique key on `(course_id, semester_id, offering_group)`, a 30-character group limit, and exactly `PLANNED`, `ACTIVE`, `COMPLETED`, `CANCELLED`. SQL errors 1062, 1451 and 1452 produce meaningful messages without exposing raw SQL. Deletion locks the offering in an InnoDB transaction, checks all seven referencing tables, and refuses linked rows before executing DELETE. Failure rolls back. No schema migration, cascading cleanup, duplicate course or semester was introduced. The database test asserts that its reference-table coverage matches the live schema; new referencing tables require extending the guard.

7. Executed checks

| Requested workflow | Actual result |
| --- | --- |
| 1. Admin Login -> Dashboard -> Offerings | Both dashboard buttons passed using an active Admin loaded from MySQL and an established SessionManager session. Password entry/authentication itself was not exercised. |
| 2. Actual dropdown data | Passed with production DAO queries for active courses and semesters. |
| 3. Existing offerings | Passed against live MySQL; six offerings existed before the test. |
| 4. Create and persist | Passed with a unique committed MySQL offering, read on a new connection; also exercised through real JavaFX Save using a memory DAO. |
| 5. Edit | Passed in MySQL, including all four statuses; real JavaFX selection/edit/Save passed with a memory DAO. |
| 6. Search, Refresh, Clear | Passed through real FXML controls; live MySQL search also passed. |
| 7. Delete confirmation and FK protection | Real confirmation/cancellation/error/success dialogs passed with a memory DAO. Live linked deletion was rejected; unlinked test rows were deleted. Duplicate create/update and invalid course/semester FKs were rejected. |
| 8. Back/session/window | Passed for both dashboard entry points and after CRUD. |
| 9. Role restrictions | Logged-out, Lecturer, Student and Technical Officer navigation/direct FXML access were rejected. Service operations were denied before DAO access, including after logout. Inactive Admin session creation was rejected. |
| 10. Existing navigation | Courses, Notices, User Profiles, Dashboard, Profile and Logout passed; Timetables showed its existing placeholder. The existing Course Management UI runner also passed. |

Executed totals:

- Java 25.0.4.1, JavaFX 25, Maven 3.9.16: compilation succeeded; Maven ran 25 tests with zero failures/errors/skips, including the five new service tests.
- Course Offering JavaFX runner: 135 checks passed; production navigation/live reads plus memory-backed CRUD.
- Existing Course Management JavaFX runner: 133 checks passed.
- Existing Course Management service runner: 111 checks passed.
- Course Offering live MySQL runner: 29 checks passed. Both unique test offerings were removed. A before/after comparison of every base table confirmed all original row data was unchanged. AUTO_INCREMENT allocation can advance when temporary rows are inserted.
- `git diff --check`: passed.

The successful Maven/UI log is `target/course-offering-ui.log`. The live database result is in `target/course-offering-verification.log`. Screenshots are in `target/course-offering-verification/` and `target/course-verification/`. These generated files are ignored and are not intended for a commit.

To reproduce on this Windows Java 25 installation:

```powershell
./scripts/test-course-offerings.ps1 -Offline -IncludeDatabase -IncludeJavaFx
```

Omit `-IncludeDatabase` to avoid live writes. Its explicit database runner only writes unique offering fixtures and removes them afterwards. `-IncludeJavaFx` uses live read-only queries for navigation and a memory DAO for UI mutations. The default Maven tests do not require database writes. Maven is resolved from PATH or the installed NetBeans Maven distribution; the existing dependency cache must be accessible.

8. Remaining limits and readiness

No known blocker remains for this integration. Password-based login was not executed; verification began with an established active Admin session. UI mutation tests and live JDBC persistence tests ran separately. Concurrent insert/delete contention was not stress-tested. Timetable CRUD remains the pre-existing placeholder and is outside this change.

Ready for commit: YES. Nothing is staged, committed or pushed; user approval is pending.
