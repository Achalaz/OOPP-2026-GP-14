package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.ProfileDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.Connection;
import java.sql.SQLException;

/** Dependency-free security and validation regression runner. No database access. */
public final class ProfileServiceTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try {
            SessionManager.logout();
            expect(SecurityException.class, ProfileService::new);
            expect(SecurityException.class, () -> new ProfileDAO().getProfile(7));
            expect(SecurityException.class, () -> new ProfileDAO().updateProfile(7, "Name", "a@example.com"));
            expect(IllegalArgumentException.class, () -> SessionManager.startSession(user(0, "ADMIN")));

            for (String role : new String[]{"ADMIN", "LECTURER", "TECHNICAL_OFFICER", "STUDENT"}) {
                User original = user(7, role);
                SessionManager.startSession(original);
                original.setUserId(99);
                SessionManager.getCurrentUser().setUserId(99);
                equal(7, SessionManager.getCurrentUserIdOrNull(), "session identity cannot be changed through a User");
                MemoryDAO dao = new MemoryDAO(role);
                ProfileService service = new ProfileService(dao);
                equal(7, service.getMyProfile().getUserId(), "load authenticated owner for " + role);
                User updated = service.updateMyProfile("  Amara   Perera Silva  ", " amara@example.com ");
                equal("Amara Perera Silva", updated.getFullName(), "normalize name");
                equal("amara@example.com", updated.getEmail(), "trim email");
                equal(7, dao.owner, "DAO receives authenticated ID");
                equal(role, updated.getRole(), "role preserved");
                equal("owner", updated.getUsername(), "username preserved");
                equal("Amara Perera Silva", SessionManager.getCurrentUser().getFullName(), "dashboard session refreshed");
                updated.setUserId(99);
                equal(7, SessionManager.getCurrentUserIdOrNull(), "returned profile cannot mutate session");
            }

            SessionManager.startSession(user(7, "LECTURER"));
            MemoryDAO dao = new MemoryDAO("LECTURER");
            ProfileService service = new ProfileService(dao);
            for (String badName : new String[]{null, "", "   ", "1234", "Name\nOther", "A".repeat(81),
                    "First " + "B".repeat(81)}) {
                expect(IllegalArgumentException.class, () -> service.updateMyProfile(badName, "a@example.com"));
            }
            for (String badEmail : new String[]{null, "", "bad", "a@b", "a..b@example.com", ".a@example.com",
                    "a.@example.com", "a@-example.com", "a@example-.com", "a@ex..com", "a b@example.com",
                    "a\nb@example.com", "a".repeat(139) + "@example.com"}) {
                expect(IllegalArgumentException.class, () -> service.updateMyProfile("Valid Name", badEmail));
            }
            equal(0, dao.writes, "invalid input does not write");
            service.updateMyProfile("A".repeat(80) + " " + "B".repeat(80), "a".repeat(138) + "@example.com");
            service.updateMyProfile("නිමල් O'Connor-Silva", "nimal+faculty@example.edu.lk");
            service.updateMyProfile("Amara", "amara@example.com");
            equal("Amara", dao.profile.getFullName(), "single names supported by existing split convention");

            dao.failure = new SQLException("Synthetic duplicate", "23000", 1062);
            String previousName = SessionManager.getCurrentUser().getFullName();
            expect(SQLException.class, () -> service.updateMyProfile("Changed Name", "other@example.com"));
            equal(previousName, SessionManager.getCurrentUser().getFullName(), "failed writes do not alter session");
            dao.failure = null;
            dao.available = false;
            expect(SecurityException.class, service::getMyProfile);
            expect(SecurityException.class, () -> service.updateMyProfile("Name", "a@example.com"));
            dao.available = true;
            dao.writeSucceeds = false;
            expect(SecurityException.class, () -> service.updateMyProfile("Name", "a@example.com"));

            expect(SecurityException.class, () -> new ProfileDAO().getProfile(8));
            expect(SecurityException.class, () -> new ProfileDAO().updateProfile(8, "Other", "other@example.com"));
            expect(SecurityException.class, () -> new ProfileDAO().updateProfile(
                    new SessionIdentity(-1, 7), "Other", "other@example.com"));
            SessionIdentity oldIdentity = SessionManager.requireSession();
            SessionManager.logout();
            SessionManager.startSession(user(7, "LECTURER"));
            expect(SecurityException.class, service::getMyProfile);
            expect(SecurityException.class, () -> service.updateMyProfile("Stale", "stale@example.com"));
            expect(SecurityException.class, () -> SessionManager.updateProfile(oldIdentity, "Stale", "stale@example.com"));
            expect(SecurityException.class, () -> new ProfileDAO().getProfile(oldIdentity));
            SessionManager.startSession(user(8, "STUDENT"));
            expect(SecurityException.class, () -> service.updateMyProfile("Other", "other@example.com"));
            equal("Original Name", SessionManager.getCurrentUser().getFullName(), "new login is unchanged");

            // Force a login switch while a database read is in progress.
            SessionManager.startSession(user(7, "ADMIN"));
            ProfileService switching = new ProfileService(new MemoryDAO("ADMIN") {
                @Override public User getProfile(SessionIdentity identity) {
                    SessionManager.startSession(user(8, "STUDENT"));
                    return user(7, "ADMIN");
                }
            });
            expect(SecurityException.class, switching::getMyProfile);
            equal(8, SessionManager.getCurrentUserIdOrNull(), "late read cannot overwrite a new login");
            SessionManager.logout();
            expect(SecurityException.class, () -> service.updateMyProfile("Name", "a@example.com"));
            System.out.println("Profile service/security checks passed: " + checks);
        } finally {
            SessionManager.logout();
        }
    }

    private static User user(int id, String role) {
        return new User(id, "owner", "Original Name", "owner@example.com", role, true);
    }

    private static void equal(Object expected, Object actual, String message) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) { throw new AssertionError(message); }
    }

    private static void expect(Class<? extends Throwable> type, Work action) throws Exception {
        checks++;
        try { action.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) { return; }
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + error, error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private interface Work { void run() throws Exception; }

    private static class MemoryDAO extends ProfileDAO {
        private User profile;
        private int owner;
        private int writes;
        private boolean available = true;
        private boolean writeSucceeds = true;
        private SQLException failure;

        MemoryDAO(String role) { profile = user(7, role); }

        @Override public User getProfile(SessionIdentity identity) {
            owner = identity.userId();
            return available ? user(7, profile.getRole()) : null;
        }

        @Override public boolean updateProfile(SessionIdentity identity, String name, String email) throws SQLException {
            if (failure != null) { throw failure; }
            owner = identity.userId();
            writes++;
            profile.setFullName(name);
            profile.setEmail(email);
            return writeSucceeds;
        }

        @Override protected Connection openConnection() {
            throw new AssertionError("Unit test must not connect to a database");
        }
    }
}
