# Notices module: existing-schema integration

Verified on 2026-10-18 against local MySQL database `ftms_group14`.
Branch: `Testing-notices`. No branch switch, staging, commit or push was performed.

## Final implementation

The existing Notice model, DAO, service, controllers and FXML are reused.
The module now uses the actual table columns:

```text
notice_id     INT NOT NULL AUTO_INCREMENT PRIMARY KEY
title         VARCHAR(180) NOT NULL
content       TEXT NOT NULL
audience      ENUM('ALL','STUDENTS','LECTURERS','TECHNICAL_OFFICERS','ADMINS')
created_by    INT NOT NULL, foreign key to users.user_id
published_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
expires_at    TIMESTAMP NULL
is_active     TINYINT(1) NOT NULL DEFAULT 1
```

The incorrect `is_published`, `created_at` and `updated_at` mappings are removed.
The earlier untracked migration proposal was deleted; **no migration is needed
or was applied**. The real table was never dropped, truncated, recreated or altered.

Admin INSERT supplies `created_by` from the authenticated Admin session.
An input object's creator cannot override this value. Editing preserves the
existing notice ID and original creator, including notices created by another
account. JDBC parameters and try-with-resources are retained.

The Admin form supports title, full content, all five audiences, an optional
publication start for new notices, optional expiry, draft/active state, save,
edit, confirmed delete, publish/unpublish, search and refresh. Selecting a record
populates all editable fields. A blank publication start on a new notice uses
MySQL CURRENT_TIMESTAMP. Existing notice start dates must be retained or explicitly
changed. Dates accept `yyyy-MM-dd HH:mm:ss`; invalid calendar dates and an explicit
expiry at/before the publication start are rejected. Titles are limited to 180
Unicode code points and TEXT content to 65,535 UTF-8 bytes.

The viewer shows full selected content, actual publication start and expiry,
an empty state and refresh. It has no write controls. A refresh clears notices
and details that become inactive, future-dated, expired or audience-inaccessible.

## Publication and visibility semantics

- `is_active = 0`: draft/inactive, regardless of its stored dates.
- `is_active = 1` with a future publication start: scheduled.
- `is_active = 1` with expiry at/before now: expired.
- A published notice is active, has reached its start, and has not expired.
- Publish enables `is_active`; Unpublish disables it. Neither action rewrites
  the publication start or expiry. Re-enabling a future notice never publishes
  it early; an expired notice stays hidden until its expiry is explicitly edited.
- Visibility also requires ALL or the authenticated user's audience. The service
  derives the audience from the session; the viewer cannot supply another role.

The actual viewer SQL predicate is:

```sql
is_active = 1
AND published_at <= CURRENT_TIMESTAMP
AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
AND (audience = 'ALL' OR audience = ?)
```

MySQL's clock is authoritative for visibility. The start boundary is inclusive;
the expiry boundary is exclusive.

| Session role | Matching audience |
|---|---|
| ADMIN | ADMINS |
| LECTURER | LECTURERS |
| STUDENT | STUDENTS |
| TECHNICAL_OFFICER | TECHNICAL_OFFICERS |

All roles also see ALL notices that satisfy the date/active rules.
Only an authenticated active Admin can retrieve all notices/search drafts,
create, edit, delete, publish or unpublish. Service/controller calls from ended
or replaced sessions are rejected. Invalid, inactive and unsupported sessions
are rejected. The Admin management screen includes all four existing records,
including scheduled Results Processing. The Admin's read-only viewer uses the
same visibility rules as the other roles.

## Exact real-table results

The live database time during SELECT verification was **2026-10-18 23:06:08**.
The database session timezone was SYSTEM, matching the local test environment.

| Role | Actual visible titles |
|---|---|
| Student | Welcome to FTMS; OOP Assignment 01 |
| Technical Officer | Welcome to FTMS; Attendance Update |
| Lecturer | Welcome to FTMS |
| Admin read-only viewer | Welcome to FTMS |

Results Processing remains stored with `published_at = 2026-12-20 10:00:00`
and is absent from all read-only viewers on the verification date.
The real table contains exactly **4 records**.

A digest covering SHOW CREATE TABLE and every column of every real notice was
identical before and after the MySQL tests, using a fresh connection. Existing
notice content, authors, audiences, dates, flags, IDs and schema were preserved.

## Dashboard and FXML verification

The already integrated dashboard handlers and NavigationUtil restrictions were
retained and tested. Both sidebar and notice card buttons on Lecturer, Student
and Technical Officer dashboards open `/fxml/view_notices.fxml`. Admin buttons
open the existing management screen. Lecturer/Technical Officer allowlists
permit only the viewer; management stays Admin-only.

Both notice screens use `NavigationUtil.dashboardResource()` for Back. Tests
checked the exact session identity survived every round trip. Logout remains
separate and opens the existing login FXML after clearing the session.

FXML injection and event handlers were executed with real FXMLLoader instances.
Scrolling layouts were checked at 1100 x 700, 900 x 600 and 800 x 600, including
reaching the last control. Both 900 x 600 renders were visually inspected.
Screenshots are in ignored `target/notices-admin.png` and
`target/notices-viewer.png`. No unrelated dashboards were redesigned.

## Tests actually executed

- Existing Maven configuration, Java 25 / JavaFX 25: `clean test` **BUILD SUCCESS**.
  All 101 main and 20 test source files compiled from scratch. The final test
  harness adjustment was then compiled with `test-compile`, also successful.
- Maven Surefire reports **0 tests** because the repository uses explicit
  dependency-free `main` runners rather than JUnit. These runners were run directly:
  - `NoticeServiceTest`: **105 checks passed**.
  - `NoticeDatabaseSmokeTest --temporary-table`: **150 checks passed**.
  - `NoticeViewSmokeTest --snapshot --live-navigation`: **263 checks passed**.
  - Existing `ProfileServiceTest`: **79 checks passed**.
  - Existing `ResultServiceTest`: **89 checks passed**.
- `git diff --check` passed.

Service checks cover role/audience mappings, validation, drafts, all write
denials, stale sessions, publication/expiry boundaries and creator assignment.
JavaFX checks cover Admin create/edit/delete confirmation, audience selection
and edits, date parsing/preservation, publish/unpublish, search, full content,
refresh after eligibility changes, empty/error states, read-only controls,
direct-load denials, all dashboard entry points, Back and Logout.

The MySQL runner performs real SELECTs through the production DAO/service against
the four existing records and checks the exact expected titles. It also verifies
those records as of 2026-10-18 using a parameterized read-only query.

INSERT/UPDATE/DELETE tests execute the real DAO SQL against a **connection-local
temporary copy of the actual schema**. No fake production notices are inserted.
The tests verify generated IDs, required creator, Unicode content, title/content
search, same-ID editing, creator preservation, all audiences, publication toggles,
schedules, expiry, audience edits and deletion. They freeze/advance only that
test connection's CURRENT_TIMESTAMP to check exact boundaries without delays.
Closing the connection removes its temporary tables. The real table is not
modified and no DROP/TRUNCATE statements are executed.

## Rerun

From the project root in PowerShell, compile with `mvn test` or `mvn test-compile`,
then execute the explicit runners:

```powershell
$noticeClasspath = 'target/test-classes;target/classes'
java -cp $noticeClasspath lk.ac.ruhuna.ftms.service.NoticeServiceTest

$noticeRepository = Join-Path $env:USERPROFILE '.m2/repository'
$noticeMysql = Join-Path $noticeRepository 'com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar'
java -cp "$noticeClasspath;$noticeMysql" lk.ac.ruhuna.ftms.dao.NoticeDatabaseSmokeTest --temporary-table

$noticeJavafx = (Get-ChildItem "$noticeRepository/org/openjfx/*/25/*-25-win.jar").FullName -join ';'
java --module-path $noticeJavafx --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp "$noticeClasspath;$noticeMysql" lk.ac.ruhuna.ftms.controller.NoticeViewSmokeTest --snapshot --live-navigation
```

Omit `--live-navigation` to run JavaFX with in-memory notice fixtures.
Live runners require an existing active account for each role; they do not
create accounts or change credentials. The MySQL test requires permission
to create temporary tables. Dates in the expected live fixture checks are
explicitly scoped to 2026-10-18; separate as-of assertions remain reproducible.

Maven was invoked from the machine's existing Maven 3.8.5 installation and cache.
Nonfatal native-access/Unsafe warnings come from the existing Maven libraries.
No dependencies or credentials were added.

## Changed files

Modified production files:

- `src/main/java/lk/ac/ruhuna/ftms/model/Notice.java`
- `src/main/java/lk/ac/ruhuna/ftms/dao/NoticeDAO.java`
- `src/main/java/lk/ac/ruhuna/ftms/service/NoticeService.java`
- `src/main/java/lk/ac/ruhuna/ftms/controller/NoticeController.java`
- `src/main/java/lk/ac/ruhuna/ftms/controller/ViewNoticesController.java`
- `src/main/resources/fxml/notices.fxml`
- `src/main/resources/fxml/view_notices.fxml`

Updated untracked test/documentation files from the preceding work:

- `src/test/java/lk/ac/ruhuna/ftms/service/NoticeServiceTest.java`
- `src/test/java/lk/ac/ruhuna/ftms/dao/NoticeDatabaseSmokeTest.java`
- `src/test/java/lk/ac/ruhuna/ftms/controller/NoticeViewSmokeTest.java`
- `docs/notices-module.md`

Removed the untracked `docs/database/complete_notices.sql` proposal.
Existing dashboard/navigation changes were already present in the branch and
were preserved. DBConnection, SessionManager, authentication and other modules
were not changed in this correction.

## Limits and commit status

Credential-entry login was not automated: UI tests used test sessions, and live
navigation used existing active database accounts through SessionManager.
No production CRUD writes were performed; SQL writes were verified on temporary
tables, and UI writes used a memory DAO. Manual login and a committed save on an
approved test database remain useful acceptance checks.

Ready for commit: **YES**. No migration or implementation blocker remains.
No files have been staged, committed or pushed; Git approval is still required.
