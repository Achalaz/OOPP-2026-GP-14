
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;
import lk.ac.ruhuna.ftms.util.ProfilePictureStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class ProfileDAO {

    // Load user profile using user ID
    public User getProfile(int userId) throws SQLException {
        return getProfile(requireOwner(userId));
    }

    public User getProfile(SessionIdentity identity) throws SQLException {
        SessionManager.requireSession(identity);

        try (Connection con = openConnection()) {
            String pictureColumn = hasPictureColumn(con) ? "profile_picture" : "NULL AS profile_picture";
            String sql = """
                    SELECT user_id, username,
                           first_name, last_name,
                           email, role, is_active, %s
                    FROM users
                    WHERE user_id = ?
                      AND is_active = TRUE
                    """.formatted(pictureColumn);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, identity.userId());
                ps.setQueryTimeout(15);
                SessionManager.requireSession(identity);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }

                    SessionManager.requireSession(identity);
                    String fullName = rs.getString("first_name") + " " + rs.getString("last_name");
                    User profile = new User(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            fullName.trim(),
                            rs.getString("email"),
                            rs.getString("role"),
                            rs.getBoolean("is_active")
                    );
                    profile.setProfilePicture(rs.getString("profile_picture"));
                    return profile;
                }
            }
        }
    }

    // Update personal details only
    public boolean updateProfile(
            int userId,
            String fullName,
            String email) throws SQLException {
        return updateProfile(requireOwner(userId), fullName, email);
    }

    public boolean updateProfile(
            SessionIdentity identity, String fullName, String email) throws SQLException {
        SessionManager.requireSession(identity);
        if (fullName == null || fullName.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Full name and email are required.");
        }

        String[] names = fullName.trim().split("\\s+", 2);

        String firstName = names[0];
        String lastName =
                names.length > 1 ? names[1] : "";

        String sql = """
                UPDATE users
                SET first_name = ?,
                    last_name = ?,
                    email = ?
                WHERE user_id = ?
                  AND is_active = TRUE
                """;

        try (Connection con = openConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, email);
            ps.setInt(4, identity.userId());
            ps.setQueryTimeout(15);

            // Recheck after connection acquisition; never use an ID supplied by a form.
            SessionManager.requireSession(identity);
            return ps.executeUpdate() == 1;
        }
    }

    protected Connection openConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    /** Optimistic comparison prevents a stale upload/removal from deleting a newer picture. */
    public boolean updateProfilePicture(SessionIdentity identity, String expectedFilename, String filename)
            throws SQLException {
        SessionManager.requireSession(identity);
        if (filename != null && !ProfilePictureStore.isManagedFilename(identity.userId(), filename)) {
            throw new IllegalArgumentException("Invalid profile picture filename.");
        }
        String sql = """
                UPDATE users SET profile_picture = ?
                WHERE user_id = ? AND is_active = TRUE AND profile_picture <=> ?
                """;
        try (Connection con = openConnection()) {
            if (!hasPictureColumn(con)) {
                throw new SQLException("Profile pictures require the reviewed add_user_profile_picture.sql migration.", "42S22", 1054);
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (filename == null) { ps.setNull(1, Types.VARCHAR); }
            else { ps.setString(1, filename); }
            ps.setInt(2, identity.userId());
            if (expectedFilename == null) { ps.setNull(3, Types.VARCHAR); }
            else { ps.setString(3, expectedFilename); }
            ps.setQueryTimeout(15);
            SessionManager.requireSession(identity);
            return ps.executeUpdate() == 1;
            }
        }
    }

    private boolean hasPictureColumn(Connection connection) throws SQLException {
        String sql = """
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "users");
            statement.setString(2, "profile_picture");
            statement.setQueryTimeout(15);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private SessionIdentity requireOwner(int userId) {
        SessionIdentity identity = SessionManager.requireSession();
        if (identity.userId() != userId) {
            throw new SecurityException("You can only access your own profile.");
        }
        return identity;
    }
}
