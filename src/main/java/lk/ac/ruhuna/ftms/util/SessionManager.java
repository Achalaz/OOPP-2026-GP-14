
package lk.ac.ruhuna.ftms.util;

import lk.ac.ruhuna.ftms.model.User;

public final class SessionManager {

    private static User currentUser;

    private SessionManager() {
    }

    public static void startSession(User user) {

        if (user == null || !user.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot start an invalid session"
            );
        }

        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static Integer getCurrentUserIdOrNull() {
        return currentUser == null
                ? null
                : currentUser.getUserId();
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static void logout() {
        currentUser = null;
    }
}
