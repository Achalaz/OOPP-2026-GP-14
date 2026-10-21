
package lk.ac.ruhuna.ftms.util;

import lk.ac.ruhuna.ftms.model.User;

public final class SessionManager {

    private record Session(SessionIdentity identity, User user) {}

    /** Identifies one login, including when the same account logs in again. */
    public record SessionIdentity(long generation, int userId) {}

    private static volatile Session currentSession;
    private static long generation;

    private SessionManager() {
    }

    public static synchronized void startSession(User user) {

        if (user == null || !user.isActive() || user.getUserId() <= 0) {
            throw new IllegalArgumentException(
                    "Cannot start an invalid session"
            );
        }

        currentSession = new Session(
                new SessionIdentity(++generation, user.getUserId()), copy(user));
    }

    public static User getCurrentUser() {
        Session session = currentSession;
        return session == null ? null : copy(session.user());
    }

    public static Integer getCurrentUserIdOrNull() {
        Session session = currentSession;
        return session == null
                ? null
                : session.identity().userId();
    }

    public static boolean isLoggedIn() {
        return currentSession != null;
    }

    public static synchronized void logout() {
        currentSession = null;
    }

    public static SessionIdentity requireSession() {
        Session session = currentSession;
        if (session == null) {
            throw new SecurityException("Please sign in to view your profile.");
        }
        return session.identity();
    }

    public static boolean isCurrentSession(SessionIdentity identity) {
        Session session = currentSession;
        return session != null && session.identity().equals(identity);
    }

    public static void requireSession(SessionIdentity identity) {
        if (!isCurrentSession(identity)) {
            throw new SecurityException("Your session has ended. Please sign in again.");
        }
    }

    /** Refresh only personal details; never replace the authenticated identity or role. */
    public static synchronized void updateProfile(
            SessionIdentity identity, String fullName, String email) {
        requireSession(identity);
        User updated = copy(currentSession.user());
        updated.setFullName(fullName);
        updated.setEmail(email);
        currentSession = new Session(identity, updated);
    }

    private static User copy(User user) {
        User copy = new User(user.getUserId(), user.getUsername(), user.getFullName(),
                user.getEmail(), user.getRole(), user.isActive());
        copy.setProfilePicture(user.getProfilePicture());
        copy.setPhone(user.getPhone());
        return copy;
    }

    public static synchronized void updateProfilePicture(SessionIdentity identity, String filename) {
        requireSession(identity);
        User updated = copy(currentSession.user());
        updated.setProfilePicture(filename);
        currentSession = new Session(identity, updated);
    }
}
