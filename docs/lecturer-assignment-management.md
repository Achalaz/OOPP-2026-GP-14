Lecturer Assignment Management verification - 22 October 2026

Implemented inside the existing Admin Course Offering screen on branch `tg2075-course-offering`. The earlier uncommitted Course Offering integration changes were preserved. No branch switch, staging, commit or push was performed.

1. Files modified for this feature

| File | Change |
| --- | --- |
| `src/main/java/lk/ac/ruhuna/ftms/controller/CourseOfferingController.java` | Assignment selection, service injection, lecturer loading, assignment actions, confirmation and error handling. |
| `src/main/java/lk/ac/ruhuna/ftms/dao/LecturerCourseAssignmentDAO.java` | Extend the existing DAO with transactional Admin assignment management and history protection; preserve existing Lecturer read methods. |
| `src/main/java/lk/ac/ruhuna/ftms/model/LecturerCourseAssignment.java` | Populate the existing empty model with assignment/display fields and the supported type enum. |
| `src/main/java/lk/ac/ruhuna/ftms/service/LecturerAssignmentService.java` | New session-bound Admin service with validation and dynamic authorization. |
| `src/main/java/lk/ac/ruhuna/ftms/util/NavigationUtil.java` | Inject the assignment service once after FXML loading; allow the existing Lecturer dashboard's academic routes. |
| `src/main/resources/fxml/course_offerings.fxml` | Add the Assigned Lecturers section using existing dashboard styles. |
| `src/test/java/lk/ac/ruhuna/ftms/service/LecturerAssignmentServiceTest.java` | Five regular Maven tests covering service validation, delegation and service/DAO authorization. |
| `src/test/java/lk/ac/ruhuna/ftms/service/LecturerAssignmentMySqlIT.java` | Six opt-in MySQL integration tests for assignments, history, academic authorization and rollback. |
| `src/test/java/lk/ac/ruhuna/ftms/dao/LecturerAssignmentLiveFixture.java` | Own and clean up a unique live offering; compare all original database rows before/after. |
| `src/test/java/lk/ac/ruhuna/ftms/controller/LecturerAssignmentWorkflowSmokeTest.java` | Exercise real JavaFX actions, live persistence, Lecturer navigation, history protection and layout. |
| `scripts/test-lecturer-assignments.ps1` | Reproducible Maven, optional MySQL and optional live JavaFX checks. |
| `docs/lecturer-assignment-management.md` | This implementation and verification report. |

Existing uncommitted changes in `AdminDashboardController.java`, `CourseOfferingDAO.java`, `admin_dashboard.fxml`, and the earlier Course Offering tests/report/script remain intact. The Course Offering controller, FXML and NavigationUtil contain both that earlier integration and this feature. The feature review diff compares these shared files against copies captured before assignment work, so it does not reattribute the earlier work.

2. UI features

Select an offering in the existing table to enable assignment management. The section shows its course, group, semester and year; loads active lecturers from MySQL; offers PRIMARY, ASSISTANT and PRACTICAL; and provides Assign Lecturer, Update Assignment Type, Remove Assignment and Refresh Assignments actions. Its table displays lecturer name, staff number, assignment type, course code, group and semester/year.

Selecting an assigned lecturer fills the lecturer/type controls. Changes operate on the selected assignment, and creating another assignment uses the lecturer dropdown. Duplicate lecturer/offering pairs are rejected even when a different type is selected. Multiple distinct lecturers are supported. Type changes and removals require confirmation when permitted. Historical assignments on cancelled offerings remain visible but cannot be changed. Clearing/searching/refreshing offerings clears stale assignment context. Refresh Assignments also reloads eligible lecturers.

The existing Course Offering Add/Update/Delete/Search/Refresh/Clear and Back actions remain in place. The assignment section reuses the existing scrollable screen, dashboard card styles and wrapping button layout. Runtime layout checks and screenshots covered 800x600, 1024x650 and 1366x700; the table can scroll horizontally at smaller widths.

3. DAO and service methods

`LecturerAssignmentService` exposes:

- `getEligibleLecturers()`
- `getAssignmentsByOffering(int offeringId)`
- `assignLecturer(int offeringId, int lecturerId, AssignmentType type)`
- `updateAssignmentType(int offeringId, int assignmentId, AssignmentType type)`
- `removeAssignment(int offeringId, int assignmentId)`
- `getAssignmentChangeBlocker(int offeringId, int assignmentId)`

The existing `LecturerCourseAssignmentDAO` provides matching methods with a captured `SessionIdentity` argument. It also provides a `LecturerOption` record for dropdown labels. Existing `isAssigned()`, `findAssigned()` and both `enrollmentOffering()` methods are unchanged.

NavigationUtil creates and injects the new service exactly once after loading the existing FXML, alongside the existing CourseOfferingService, before displaying the screen. No second production FXML loading path or separate dashboard was added.

4. SQL and database changes

Live MySQL `SHOW CREATE TABLE` and foreign-key metadata were inspected before implementation. Confirmed facts:

- `lecturers.lecturer_id` is the profile primary key and references `users.user_id`; the dropdown uses that profile ID. `staff_no` is present and unique.
- Eligible lecturers require a matching profile, `users.role='LECTURER'` and `users.is_active=1`.
- `assignment_type` is exactly `ENUM('PRIMARY','ASSISTANT','PRACTICAL')`.
- The existing assignment unique key is `(offering_id, lecturer_id, assignment_type)`. The new Admin operations enforce the requested stricter uniqueness of `(offering_id, lecturer_id)` by locking the offering and checking for an existing pair. Existing records are not deduplicated or migrated.
- No table references `assignment_id`. The assignment table has no retired-state/history field; results do not identify the publisher's assignment.

No production schema, foreign key or enum was changed. PreparedStatements perform reads and assignment INSERT/UPDATE/DELETE. JDBC transactions use READ_COMMITTED, revalidate the database actor, lock the relevant offering/assignment and roll back on failure. Only the selected assignment row is deleted. No course, semester, lecturer account, enrollment, mark or result is created/deleted by the assignment feature.

5. Authorization and academic history

Both service and DAO reject unauthenticated, inactive or non-Admin sessions. Services bind to the exact session that opened the screen and reject later logins, logout or role changes. Each DAO transaction also locks/reloads the active database account and checks its current role. Assignment creation/type changes validate the offering, cancellation status, active Lecturer account/profile and supported type. Assignment IDs must belong to the selected offering. Session validity is rechecked before commit.

Before removal or type changes, the UI requests a blocker preview. The DAO repeats the checks inside the mutation transaction. It retains assignments when the offering has assessments, marks, results (published or unpublished), or eligibility records. It also checks course materials uploaded by the lecturer, attendance sessions conducted by the lecturer, timetable entries naming the lecturer, and medical reviews by the lecturer. This deliberately conservative policy accounts for academic records that do not identify an assignment directly. Unreferenced assignments can be changed/removed after confirmation explaining the access/history impact.

There is no automatic historical cleanup or cascade deletion. Supporting changes to historical assignments requires an approved retention/retirement design; the current implementation reports that restriction and preserves the assignment. Existing mark-entry/publication authorization for all three types is unchanged. `LecturerAccessService`, `MarkService`, `ResultService`, and the Marks, Results, Eligibility, GPA and Reports implementations were not rewritten or modified.

6. Lecturer Dashboard integration

The existing assignment DAO queries already expose newly assigned offerings to Lecturer services. Live verification reached the new offering through the existing Lecturer dashboard workspace and Marks screen, and through ResultService. After removal, the offering disappeared from the lecturer's accessible assignments. MySQL integration checks confirmed that an unassigned lecturer cannot edit its marks and that existing type-specific authorization behavior is retained.

The existing Lecturer dashboard also had academic cards whose routes were missing from NavigationUtil's Lecturer allowlist. The existing assessments, results, eligibility, reports and GPA routes were added to that allowlist and exercised. Their controllers/services still enforce their existing academic access checks. Admin Course Offering access remains unavailable to Lecturers, Students and Technical Officers.

7. Executed verification

Java 25.0.4.1, JavaFX 25 and Maven 3.9.16 were used. Compilation succeeded. Actual successful results:

| Check | Result |
| --- | --- |
| Regular Maven tests | 30 tests, 0 failures/errors/skips, including 5 new assignment service tests. |
| Explicit MySQL integration tests | 17 tests, 0 failures/errors/skips: 6 new LecturerAssignmentMySqlIT tests plus 11 existing AcademicMySqlIT regression tests. |
| Live assignment JavaFX workflow | 95 checks passed, including real assignment persistence, duplicate rejection, type change, confirmation/cancellation, safe removal, historical removal rejection, Lecturer access, Back and layout. |
| Existing Course Offering JavaFX regression | 150 checks passed: live database reads/navigation, FXML bindings, and memory-backed Course Offering CRUD. |
| Existing Lecturer FXML/controller regression | 63 checks passed. |
| Git whitespace/error check | `git diff --check` passed. |

Requested workflow coverage:

| Workflow | Actual verification |
| --- | --- |
| 1. Admin selects an offering | Real JavaFX selection enabled the assignment controls; controls were disabled before selection. |
| 2. Active lecturers load | Real MySQL dropdown matched eligible profiles; isolated MySQL tests rejected inactive/non-Lecturer/profile-less users. |
| 3-4. Assign and persist | Real UI assigned an existing lecturer to an owned temporary offering; a new connection read the persisted assignment. |
| 5. Duplicate rejection | Real UI and isolated MySQL rejected the same lecturer/offering pair, including a different assignment type. |
| 6. Multiple lecturers | Passed against MySQL temporary tables using multiple valid profiles; only one eligible lecturer existed in the live database. |
| 7. Type changes | Real UI cancellation/confirmation and persistence passed; MySQL fixtures exercised all three supported types. |
| 8. Removal preserves history | Real historical assignment removal was blocked; an unreferenced assignment was removed. Isolated MySQL tested each of the eight history categories, published-result preservation and rollback. |
| 9. Lecturer sees assigned offering | Existing Lecturer workspace and Marks screen displayed the live assignment; ResultService returned it. |
| 10. Unassigned mark editing denied | Existing MarkService rejected an unassigned lecturer in MySQL integration tests. |
| 11. Non-Admin management denied | Service and direct DAO tests rejected unauthorized/stale sessions; database deactivation was rechecked; Course Offering navigation/direct FXML role checks passed. |
| 12. Course Offering CRUD | Existing real UI actions passed using its memory DAO. The live assignment fixture also created/deleted its unique offering through the existing service. Earlier dedicated live Course Offering CRUD verification is documented separately. |
| 13. Back | Preserved the exact Admin session, Scene, Stage and window dimensions. |
| 14. Existing Lecturer Dashboard | Existing dashboard loaded and its workspace, Marks and academic routes were exercised; its 63-check FXML/controller runner passed. |
| 15. FXML handlers and IDs | FXMLLoader completed, annotated controller fields were injected, new actions were fired, and existing offering actions passed their regression runner. |
| 16. Compile/tests | Java 25 Maven compilation and the relevant suites above passed. |

The MySQL integration suites use connection-local temporary tables cloned from the real schema with foreign keys omitted, while retaining columns/enums/indexes. They verify real SQL and transaction behavior without writing fixture accounts or academic records into base tables; they are not a separate proof of every live FK constraint. The live JavaFX test used the actual base tables and their constraints for the temporary offering/assignment workflow.

The live workflow removed its temporary offering and assignments. A before/after comparison of every base table confirmed that all original database rows were unchanged. AUTO_INCREMENT allocation may advance. Password-entry login was not exercised: sessions were established using actual active users loaded from MySQL. Concurrent contention was not stress-tested.

Evidence is in `target/lecturer-assignment-ui.log`, the two MySQL suites' `target/surefire-reports/` reports, and `target/lecturer-assignment-verification/` screenshots. The integration portion of `target/lecturer-assignment-verification.log` records the 17 passing MySQL tests; its subsequent initial UI launch failed in the runner before the corrected successful UI run recorded in `lecturer-assignment-ui.log`. Earlier failed harness attempts are not counted as passes.

To reproduce on this installation:

```powershell
./scripts/test-lecturer-assignments.ps1 -Offline -IncludeDatabase -IncludeJavaFx
```

The default runs regular Maven tests. `-IncludeDatabase` adds the two isolated MySQL suites. `-IncludeJavaFx` performs live reads and writes a uniquely identified offering/assignment fixture, then removes it and verifies preservation of original rows; it also runs the existing Offering and Lecturer UI checks. These optional runners require the configured MySQL connection and a JavaFX desktop. Maven uses PATH or the installed NetBeans Maven distribution and the existing dependency cache.

8. Remaining blockers and limits

No blocker remains for managing unreferenced assignments. Historical assignment removal/type changes remain intentionally blocked until an explicit retention policy/schema is agreed; the feature does not invent one. Existing duplicate pairs, if any, are left unchanged and cannot be updated into another duplicate. Password-entry login, concurrent stress testing and multiple live lecturer accounts were not exercised; their relevant session/security and multiple-assignment behaviors were covered as described above. Timetables retains its existing dashboard placeholder outside this feature.

9. Ready to commit

YES, for the implemented scope and documented history restriction. User review/approval is pending; nothing is staged, committed or pushed. The exact feature diff is `target/lecturer-assignment-review.diff`; the full current working-tree diff is `target/ftms-working-tree-review.diff`. Generated `target/` files are ignored and must not be included in a commit.
