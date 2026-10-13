
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO {

    // Retrieve all users
    public List<User> getAllUsers() throws SQLException {

        List<User> users = new ArrayList<>();

        String sql = """
                SELECT user_id, username, first_name,
                       last_name, email, role, is_active
                FROM users
                ORDER BY user_id
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(mapUser(rs));
            }
        }

        return users;
    }

    // Find user by ID
    public User findUserById(int userId) throws SQLException {

        String sql = """
                SELECT user_id, username, first_name,
                       last_name, email, role, is_active
                FROM users
                WHERE user_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }

        return null;
    }

    // Login credentials record
    public record UserCredentials(
            User user,
            String passwordHash
    ) {
    }

    // Find user by username for Login
    public Optional<UserCredentials> findByUsername(
            String username) throws SQLException {

        String sql = """
                SELECT user_id, username, first_name,
                       last_name, email, role,
                       is_active, password_hash
                FROM users
                WHERE username = ?
                LIMIT 1
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                User user = mapUser(rs);

                String passwordHash =
                        rs.getString("password_hash");

                return Optional.of(
                        new UserCredentials(
                                user,
                                passwordHash
                        )
                );
            }
        }
    }

    // Add new user
    public void addUser(
            User user,
            String passwordHash) throws SQLException {

        String sql = """
                INSERT INTO users
                (username, password_hash, role,
                 first_name, last_name, email, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        String[] names = splitName(user.getFullName());

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, passwordHash);
            ps.setString(3, user.getRole());
            ps.setString(4, names[0]);
            ps.setString(5, names[1]);
            ps.setString(6, user.getEmail());
            ps.setBoolean(7, user.isActive());

            ps.executeUpdate();
        }
    }

    // Update existing user
    public void updateUser(User user) throws SQLException {

        String sql = """
                UPDATE users
                SET username = ?,
                    first_name = ?,
                    last_name = ?,
                    email = ?,
                    role = ?,
                    is_active = ?
                WHERE user_id = ?
                """;

        String[] names = splitName(user.getFullName());

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, names[0]);
            ps.setString(3, names[1]);
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getRole());
            ps.setBoolean(6, user.isActive());
            ps.setInt(7, user.getUserId());

            ps.executeUpdate();
        }
    }

    // Deactivate user
    public void deactivateUser(int userId) throws SQLException {

        String sql = """
                UPDATE users
                SET is_active = FALSE
                WHERE user_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    // Convert ResultSet row into User object
    private User mapUser(ResultSet rs) throws SQLException {

        String fullName = rs.getString("first_name")
                + " " + rs.getString("last_name");

        return new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                fullName,
                rs.getString("email"),
                rs.getString("role"),
                rs.getBoolean("is_active")
        );
    }

    // Split full name into first and last name
    private String[] splitName(String fullName) {

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException(
                    "Full name cannot be empty"
            );
        }

        String[] parts = fullName.trim().split("\\s+", 2);

        String firstName = parts[0];

        String lastName =
                parts.length > 1 ? parts[1] : "";

        return new String[]{firstName, lastName};
    }
}
