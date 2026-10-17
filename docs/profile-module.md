# My Profile module

One `/fxml/profile.fxml` screen serves ADMIN, LECTURER, TECHNICAL_OFFICER and
STUDENT. Start the application normally: Login -> Dashboard -> My Profile ->
Dashboard -> Logout. The profile sidebar also offers logout. `MainApp` now opens
the existing login screen instead of the user-management screen.

## Changed files and responsibilities

- `ProfileDAO.java`: extends the existing local implementation, preserving
  `getProfile(int)` and `updateProfile(int, String, String)`. Both enforce that the
  requested ID belongs to the current session. Session-aware overloads also reject
  work originating from an earlier login. SQL updates only `first_name`,
  `last_name`, and `email`, with `user_id` and `is_active` predicates. All JDBC
  resources use try-with-resources and prepared statements; queries have a
  15-second statement timeout. Picture updates separately change only
  `profile_picture`, with an owner check and a comparison against the previously
  loaded filename to reject stale writes.
- `ProfileService.java`: exposes `getMyProfile()` and
  `updateMyProfile(fullName, email)` with no caller-selected ID. It validates
  input, rejects unavailable accounts, and refreshes the session after successful
  reads/saves. It reuses `UserService.validateUser` and adds profile-specific
  length, control-character, name and email checks.
- `ProfileController.java`, `profile.fxml`, `profile.css`: navy/white layout with
  header, sidebar, initials summary, read-only username/role, name/email inputs,
  reset, refresh and save controls, plus picture upload/removal. Cards stack in smaller windows, and content
  scrolls vertically. Database reads/writes run in `Task`; only FX callbacks
  update controls. Busy controls prevent overlapping requests. Detached screens
  cancel their worker and discard stale completions. Validation, duplicate email,
  session and connection failures receive visible messages.
- `SessionManager.java`: retains all existing method signatures; publishes a
  thread-safe session snapshot, returns defensive `User` copies, identifies each
  login separately, and permits refreshing name/email and the picture reference for that login.
  Caller mutations of a returned `User` cannot change the authenticated ID.
- `dashboard.fxml`, `DashboardController.java`: add My Profile navigation and
  visible navigation-error messages.
- `MainApp.java`: changes only the startup FXML resource to `/fxml/login.fxml`.
- `User.java`: adds a nullable `profilePicture` property, retaining its existing
  constructor and methods. `ProfilePictureStore.java`: validates, copies, reads
  and deletes managed pictures.
- Five explicit profile regression runners under `src/test/java`, an optional
  migration under `docs/database`, and this guide.

`UserDAO`, `DBConnection`, `AuthService`, `PasswordUtil`, `LoginController`,
`NavigationUtil`, the Maven POM and unrelated modules remain unchanged. There are
no profile password fields/actions for any role, including LECTURER. No database
schema changes were executed and no seed files were changed.

## Existing schema and input rules

The local `ftms_group14.users` table was inspected using `SHOW CREATE TABLE`.
The implementation uses the existing `User` model and these columns only:
`user_id`, `username`, `first_name`, `last_name`, `email`, `role`, `is_active`,
`profile_picture`.

Full name and email are required when saving. Name whitespace is normalized;
the first word becomes `first_name`, and the rest becomes `last_name`, matching
the existing `UserDAO` convention. A single name stores an empty last name.
Each part allows 80 Unicode code points. Names must contain a letter and cannot
contain control characters. Email is trimmed, validated and limited to 150
characters. Existing NULL emails can be displayed and must be supplied to save.
The database's unique email constraint handles conflicts, including concurrent
updates and the database's case-insensitive comparison.

The live schema also contains phone and department columns; this module neither
exposes nor updates them. No password hash is selected or updated by the profile DAO.

## Profile pictures

The re-inspected local schema already contains nullable `profile_picture
VARCHAR(255)`. The optional, idempotent
`docs/database/add_user_profile_picture.sql` is supplied for installations missing
the column. Select the intended database and apply it manually if needed; the
application never executes a migration. Reads fall back to a NULL picture when
the column is absent, keeping name/email editing available. Attempting an upload
without the column produces a migration message.

The JavaFX FileChooser filters JPG/JPEG/PNG. Upload and removal save immediately
without submitting or discarding unsaved name/email edits. File validation,
copying, decoding and database writes run off the FX thread; the resulting image
is installed by the FX callback. A built-in vector avatar appears for NULL,
missing, invalid or inaccessible pictures. Removal remains available for an
unavailable stored picture.

The persistent directory is `${user.home}/.ftms/profile-pictures`, normally
`C:\Users\<Windows user>\.ftms\profile-pictures`. It is outside the source tree
and survives rebuilds/restarts. Only generated names such as
`u7-01234567-89ab-cdef-0123-456789abcdef.png` are stored in MySQL. Source filenames
and absolute paths are never stored. Original selected files are never modified.
Back up this directory with the database; separate computers/OS accounts need
their own copies of the managed images. There is no remote image server.

Validation checks the actual decoded JPG/PNG format, matching extension, complete
file terminator, and maximum size of **2,097,152 bytes (2 MiB)**. A bounded read
also rejects a file that grows during reading. To bound decoding memory, images
are limited to 8192 pixels per side and 16 million pixels. Corrupt/truncated,
renamed non-images and mismatched formats are rejected before any managed copy.

Stored names must match the generated filename pattern and authenticated owner's
prefix. Paths, URLs, drive letters, separators and another owner's filenames are
rejected; reads do not follow file symlinks, and the managed directory cannot be
a symlink or redirected directory. Cleanup never follows an arbitrary database
path or deletes a user's selected original.

Successful replacement/removal deletes the obsolete managed file. Failed or
conflicting uploads delete the newly staged file when the database confirms it
is unused. A lost connection can leave the commit outcome unknown: in that case
files are retained unless a fresh read proves the new one unreferenced. A process
crash or filesystem permission failure can also leave an orphan. Such uncertain
files are deliberately not swept blindly; after restoring connectivity, refresh
and compare against database references before administrative cleanup. Cleanup
failures are logged without falsely reporting that a committed save failed.

Authorization is checked in both service and DAO, including the retained
integer-ID DAO methods. In-flight SQL remains bound to its original owner, and
completion from an expired login cannot update another login's session. As with
other JDBC applications, closing the application or losing the connection during
a save can leave the outcome uncertain; refresh after reconnecting to confirm.

## Executed verification

Verified on Java 25 and JavaFX 25 with the existing local MySQL configuration:

- Maven `compile` and `test`: BUILD SUCCESS. The initial offline `test` attempt
  failed because Surefire 2.12.4 dependencies were missing; an online retry
  downloaded them successfully. Maven emitted native-access/deprecated-Unsafe
  warnings from its existing libraries, with no Java compilation errors.
- Maven Surefire reports **0 tests**: this repository uses dependency-free
  `main` runners, not JUnit tests. The following runners were executed explicitly.
- `ProfileServiceTest`: 79 validation, ownership, session, and four-role checks.
- `ProfilePictureServiceTest`: 93 actual-image/file checks covering all four roles,
  malformed formats, oversized images, dimensions, traversal/other-owner paths,
  replacement/removal, failed-upload cleanup, stale sessions and uncertain commits.
- `ProfileViewSmokeTest`: 241 checks loading real profile/dashboard/login FXML;
  all injected IDs and event handlers resolve. Covers all roles, read-only
  controls, missing-session rejection, worker-thread database access, responsive
  card layout, loading/saving states, reset, success/errors, refresh retry,
  dashboard session refresh and dashboard logout, plus picture upload/removal,
  default avatar, image refresh and preserving unsaved details during picture changes.
- `ProfileDatabaseSmokeTest --rollback-write`: 125 live MySQL checks across all
  four roles. Covers mapping, saves, unchanged saves, single names, duplicate
  email, inactive accounts, ownership and preservation of other columns/users.
  All writes were rolled back. A fresh connection verified every user row,
  including timestamps and password hashes, matched its original value.
- `ProfilePictureDatabaseSmokeTest --rollback-write`: 613 live MySQL checks across
  all roles, including filename persistence, replacing/removing pictures, stale
  writes, missing files, inactive accounts, and preservation of every other user
  and column. All writes were rolled back and verified unchanged using a fresh
  connection. Images used a temporary directory, removed afterward. No DDL ran.
- Existing `ResultServiceTest`: 89 checks passed.
- Wide (1200 x 760) and narrow (900 x 600) profile renders were visually inspected.

To rerun after compiling with `mvn test` (or `mvn test-compile`), use PowerShell:

```powershell
$testClasspath = 'target/test-classes;target/classes'
java -cp $testClasspath lk.ac.ruhuna.ftms.service.ProfileServiceTest
java -cp $testClasspath lk.ac.ruhuna.ftms.service.ProfilePictureServiceTest
java -cp $testClasspath lk.ac.ruhuna.ftms.service.ResultServiceTest

$repository = Join-Path $env:USERPROFILE '.m2/repository'
$javafxModules = (Get-ChildItem "$repository/org/openjfx/*/25/*-25-win.jar").FullName -join ';'
java --module-path $javafxModules --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp $testClasspath lk.ac.ruhuna.ftms.controller.ProfileViewSmokeTest

$mysqlJar = Join-Path $repository 'com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar'
java -cp "$testClasspath;$mysqlJar" lk.ac.ruhuna.ftms.dao.ProfileDatabaseSmokeTest --rollback-write
java -cp "$testClasspath;$mysqlJar" lk.ac.ruhuna.ftms.dao.ProfilePictureDatabaseSmokeTest --rollback-write
```

On this machine Maven is not on PATH; its existing installation under
`~/.m2/wrapper/dists/apache-maven-3.8.5-bin/` was used with an explicit
`-Dmaven.repo.local` pointing at the existing dependency cache. Adjust the JavaFX
platform classifier if running outside Windows. Add `--snapshot` to the view
runner to write `target/profile-wide.png` and `target/profile-narrow.png`.
The view runner intentionally simulates SQL failures; corresponding console
stack traces are expected when checking error messages.

## Remaining manual runtime checks

With a suitable test account for each role, use the visible application to:

1. Authenticate with real credentials, open My Profile from Dashboard, save a
   valid name/email, return to Dashboard, log out and sign in again. Confirm the
   committed values persist. Automated database writes deliberately used rollback.
2. Exercise the profile sidebar's direct logout and keyboard navigation while
   resizing the actual application window, including Windows display scaling.
3. Exercise a real connection interruption/recovery. Simulated connection errors
   and retry were checked automatically; the MySQL server was not stopped.
4. Open the native FileChooser, select/cancel JPG/PNG files, and confirm that a
   committed upload survives logout, application restart and re-login. Remove it
   and confirm persistence. Automated UI tests used the same upload path with an
   injected selected file; they did not open the native chooser.
5. If needed on a separate database without `profile_picture`, manually review
   and run the supplied migration. The migration and missing-column branch were
   not executed against the live database, because it already has the column.

No git commit or push was performed.
