
package lk.ac.ruhuna.ftms.service;

public class UserService {

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