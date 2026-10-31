package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.PasswordUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

public class UserPasswordUpdateTest extends TestCase {
    private final RecordingDAO dao = new RecordingDAO();
    private final UserService service = new UserService(dao);

    protected void setUp() { login("ADMIN"); }
    protected void tearDown() { SessionManager.logout(); }

    public void testBlankPasswordUpdatesOnlyDetails() throws Exception {
        for (String password : new String[]{null, "", " \t\n        "}) {
            assertFalse(service.updateUser(user(), password));
            assertNull(dao.hash);
            assertEquals("lecturer", dao.user.getUsername());
            assertFalse(dao.user.isActive());
        }
        assertEquals(3, dao.updates);
    }

    public void testNewPasswordUsesExistingSaltedLoginFormatWithoutTrimming() throws Exception {
        String password = "  Fixture-password!  ";
        assertTrue(service.updateUser(user(), password));
        assertEquals(1, dao.updates);
        assertFalse("DAO must never receive plaintext", password.equals(dao.hash));
        assertTrue(dao.hash.startsWith("210000:"));
        assertTrue(PasswordUtil.verifyPassword(password.toCharArray(), dao.hash));
        assertFalse(PasswordUtil.verifyPassword(password.trim().toCharArray(), dao.hash));
        String firstHash = dao.hash;
        service.updateUser(user(), password);
        assertFalse("Each reset must use a fresh salt", firstHash.equals(dao.hash));
    }

    public void testShortPasswordRejectsEntireUpdate() throws Exception {
        try {
            service.updateUser(user(), "short");
            fail("Short password accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("Password must contain at least 8 characters", expected.getMessage());
        }
        assertEquals(0, dao.updates);
    }

    public void testInvalidDetailsPreventPasswordWrite() throws Exception {
        var user = user();
        user.setEmail("invalid");
        try { service.updateUser(user, "Valid-fixture-password"); fail("Invalid email accepted"); }
        catch (IllegalArgumentException expected) { }
        assertEquals(0, dao.updates);
    }

    public void testOnlyAdminCanRequestUserPasswordUpdates() throws Exception {
        for (String role : new String[]{"LECTURER", "STUDENT", "TECHNICAL_OFFICER"}) {
            login(role);
            StudentAccessTest.denied(() -> service.updateUser(user(), "Valid-fixture-password"));
        }
        SessionManager.logout();
        StudentAccessTest.denied(() -> service.updateUser(user(), ""));
        assertEquals(0, dao.updates);
    }

    public void testMissingSelectionCannotWrite() throws Exception {
        try { service.updateUser(null, "Valid-fixture-password"); fail("Missing selection accepted"); }
        catch (IllegalArgumentException expected) { }
        assertEquals(0, dao.updates);
    }

    private static User user() { return new User(2, " lecturer ", "Updated Lecturer", "lecturer@example.edu", "LECTURER", false); }
    private static void login(String role) { SessionManager.startSession(new User(5, "admin", "Admin", null, role, true)); }

    private static final class RecordingDAO extends UserDAO {
        User user;
        String hash;
        int updates;
        @Override public void updateUser(User user, String hash) {
            this.user = user;
            this.hash = hash;
            updates++;
        }
    }
}
