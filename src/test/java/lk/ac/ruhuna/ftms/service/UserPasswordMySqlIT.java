package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.PasswordUtil;

import java.sql.SQLException;
import java.util.List;

/** Real MySQL and login; all changes, including the inactive ID 2 fixture, use temporary tables. */
public class UserPasswordMySqlIT extends TestCase {
    private static final String OLD_PASSWORD = "Old-fixture-password!";
    private static final String NEW_PASSWORD = "New-fixture-password!";
    private AcademicDatabaseFixture db;
    private final UserDAO dao = new UserDAO();
    private final UserService service = new UserService();
    private final AuthService auth = new AuthService();

    protected void setUp() throws Exception {
        db = new AcademicDatabaseFixture();
        try {
            db.login(5, "ADMIN");
            try (var statement = db.connection.prepareStatement("UPDATE users SET password_hash=?,email=CONCAT('fixture',user_id,'@example.edu') WHERE user_id IN (1,2)")) {
                statement.setString(1, PasswordUtil.hashPassword(OLD_PASSWORD.toCharArray()));
                statement.executeUpdate();
            }
            db.execute("UPDATE users SET username='Lec2',is_active=FALSE WHERE user_id=2");
        } catch (Exception error) {
            db.close();
            throw error;
        }
    }
    protected void tearDown() throws Exception { db.close(); }

    public void testBlankPasswordPreservesExactCredentialsAndOldLogin() throws Exception {
        String originalHash = hash(1);
        for (String password : new String[]{null, "", " \t\n"}) {
            var user = edited(1);
            assertFalse(service.updateUser(user, password));
            assertTrue("Blank input must preserve the stored hash", originalHash.equals(hash(1)));
            assertEquals("Updated Lecturer", dao.findUserById(1).getFullName());
            assertTrue(auth.authenticate(user.getUsername(), OLD_PASSWORD.toCharArray()).isPresent());
        }
    }

    public void testNewPasswordAndDetailsCommitAndLoginUsesNewPassword() throws Exception {
        var user = edited(1);
        user.setUsername("renamed-lecturer");
        String otherHash = hash(2);
        assertTrue(service.updateUser(user, NEW_PASSWORD));
        assertTrue(auth.authenticate("renamed-lecturer", NEW_PASSWORD.toCharArray()).isPresent());
        assertTrue(auth.authenticate("renamed-lecturer", OLD_PASSWORD.toCharArray()).isEmpty());
        assertTrue(auth.authenticate("academic-test-1", NEW_PASSWORD.toCharArray()).isEmpty());
        assertEquals("Updated Lecturer", dao.findUserById(1).getFullName());
        assertTrue(hash(1).startsWith("210000:"));
        assertFalse("Plaintext must never be persisted", NEW_PASSWORD.equals(hash(1)));
        assertTrue("Other accounts must retain their passwords", otherHash.equals(hash(2)));
        assertFalse(dao.findUserById(2).isActive());
    }

    public void testShortPasswordDoesNotSaveDetailsOrPassword() throws Exception {
        var before = snapshot(1);
        try { service.updateUser(edited(1), "short"); fail("Short password accepted"); }
        catch (IllegalArgumentException expected) { }
        assertTrue("Rejected input must not change the account", before.equals(snapshot(1)));
        assertTrue(auth.authenticate("academic-test-1", OLD_PASSWORD.toCharArray()).isPresent());
    }

    public void testPasswordResetNeverReactivatesInactiveLec2EvenWithStaleRow() throws Exception {
        var stale = edited(2);
        stale.setActive(true);
        assertTrue(service.updateUser(stale, NEW_PASSWORD));
        assertFalse("Password reset must not reactivate Lec2", dao.findUserById(2).isActive());
        assertTrue(PasswordUtil.verifyPassword(NEW_PASSWORD.toCharArray(), hash(2)));
        assertTrue("Inactive account must still be denied login", auth.authenticate("Lec2", NEW_PASSWORD.toCharArray()).isEmpty());
        String resetHash = hash(2);
        service.updateUser(stale, "");
        assertFalse(dao.findUserById(2).isActive());
        assertTrue(resetHash.equals(hash(2)));
    }

    public void testFailureAfterEitherWriteRollsBackDetailsAndPassword() throws Exception {
        var before = snapshot(1);
        for (String fragment : List.of("username = ?", "SET password_hash")) {
            db.failAfterWrite(fragment);
            try { service.updateUser(edited(1), NEW_PASSWORD); fail("Injected failure ignored"); }
            catch (SQLException expected) { }
            assertTrue("Both updates must roll back", before.equals(snapshot(1)));
            assertTrue(auth.authenticate("academic-test-1", OLD_PASSWORD.toCharArray()).isPresent());
            assertTrue(auth.authenticate("academic-test-1", NEW_PASSWORD.toCharArray()).isEmpty());
        }
    }

    public void testDetailConstraintFailureKeepsPasswordUnchanged() throws Exception {
        var before = snapshot(1);
        var user = edited(1);
        user.setUsername("Lec2");
        try { service.updateUser(user, NEW_PASSWORD); fail("Duplicate username accepted"); }
        catch (SQLException expected) { }
        assertTrue(before.equals(snapshot(1)));
    }

    public void testDatabaseRoleAndStatusAreCheckedBeforeReset() throws Exception {
        var before = snapshot(1);
        db.execute("UPDATE users SET is_active=FALSE WHERE user_id=5");
        StudentAccessTest.denied(() -> service.updateUser(edited(1), NEW_PASSWORD));
        db.execute("UPDATE users SET is_active=TRUE,role='LECTURER' WHERE user_id=5");
        StudentAccessTest.denied(() -> service.updateUser(edited(1), NEW_PASSWORD));
        assertTrue(before.equals(snapshot(1)));
    }

    public void testSessionReplacementBeforeCommitRollsBackBothChanges() throws Exception {
        var before = snapshot(1);
        db.afterWrite(() -> db.login(3, "STUDENT"));
        StudentAccessTest.denied(() -> service.updateUser(edited(1), NEW_PASSWORD));
        assertTrue(before.equals(snapshot(1)));
    }

    public void testDeletedSelectionIsNotReportedAsSuccess() throws Exception {
        var user = edited(1);
        db.execute("DELETE FROM users WHERE user_id=1");
        try { service.updateUser(user, NEW_PASSWORD); fail("Deleted user accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private User edited(int id) throws SQLException {
        var user = dao.findUserById(id);
        user.setFullName("Updated Lecturer");
        user.setEmail("updated@example.edu");
        return user;
    }
    private String hash(int id) throws SQLException {
        return (String) db.scalar("SELECT password_hash FROM users WHERE user_id=" + id);
    }
    private List<Object> snapshot(int id) throws SQLException {
        var user = dao.findUserById(id);
        return List.of(user.getUsername(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive(), hash(id));
    }
}
