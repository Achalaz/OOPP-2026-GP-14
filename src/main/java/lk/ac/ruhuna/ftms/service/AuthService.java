
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.PasswordUtil;

import java.sql.SQLException;
import java.util.Optional;

public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    public Optional<User> authenticate(
            String username,
            char[] password) throws SQLException {

        // Validate inputs
        if (username == null || username.isBlank()
                || password == null || password.length == 0) {
            return Optional.empty();
        }

        // Find user
        Optional<UserDAO.UserCredentials> result =
                userDAO.findByUsername(username.trim());

        if (result.isEmpty()) {
            return Optional.empty();
        }

        UserDAO.UserCredentials credentials = result.get();

        // Reject inactive accounts
        if (!credentials.user().isActive()) {
            return Optional.empty();
        }

        // Verify the password hash
        boolean valid = PasswordUtil.verifyPassword(
                password,
                credentials.passwordHash()
        );

        if (!valid) {
            return Optional.empty();
        }

        return Optional.of(credentials.user());
    }
}
