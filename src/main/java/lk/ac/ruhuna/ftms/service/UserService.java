
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.PasswordUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

public class UserService {

    private final UserDAO userDAO;

    public UserService() { this(new UserDAO()); }

    public UserService(UserDAO userDAO) { this.userDAO = Objects.requireNonNull(userDAO); }

    /** A blank password means details only; the DAO commits both changes together. */
    public boolean updateUser(User user, String optionalPassword) throws SQLException {
        var actor = SessionManager.getCurrentUser();
        if (actor == null || !actor.isActive() || !"ADMIN".equals(actor.getRole())) {
            throw new SecurityException("Active administrator access is required to update users.");
        }
        if (user == null || user.getUserId() <= 0) {
            throw new IllegalArgumentException("Please select a user first.");
        }
        validateUser(user.getUsername(), user.getFullName(), user.getEmail(), user.getRole());
        boolean changePassword = optionalPassword != null && !optionalPassword.isBlank();
        char[] passwordChars = null;
        try {
            String passwordHash = null;
            if (changePassword) {
                validatePassword(optionalPassword);
                passwordChars = optionalPassword.toCharArray();
                passwordHash = PasswordUtil.hashPassword(passwordChars);
            }
            User updated = new User(user.getUserId(), user.getUsername().trim(),
                    user.getFullName().trim(), user.getEmail().trim(), user.getRole(), user.isActive());
            userDAO.updateUser(updated, passwordHash);
            return changePassword;
        } finally {
            if (passwordChars != null) Arrays.fill(passwordChars, '\0');
        }
    }

    public void validateUser(String username,
                             String fullName,
                             String email,
                             String role) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty");
        }

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException(
                    "Full name cannot be empty");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email cannot be empty");
        }

        if (!email.matches(
                "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException(
                    "Invalid email address");
        }

        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException(
                    "Please select a user role");
        }
    }

    public void validatePassword(String password) {

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters");
        }
    }
}
