package lk.ac.ruhuna.ftms.service;

import java.sql.Connection;
import java.sql.SQLException;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

/** Read operations belong to one authenticated login, never a form-supplied student ID. */
public final class StudentAccessService {
    private final SessionIdentity identity;

    public StudentAccessService() { this(SessionManager.requireSession()); }
    public StudentAccessService(SessionIdentity identity) {
        this.identity = identity;
        requireSession();
    }

    public void requireSession() {
        SessionManager.requireSession(identity);
        var user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"STUDENT".equals(user.getRole()))
            throw new SecurityException("An active student session is required.");
    }

    @FunctionalInterface
    public interface Read<T> { T run(Connection connection, int studentId) throws SQLException; }

    public <T> T read(Read<T> operation) throws SQLException {
        requireSession();
        try (var connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Recheck the database account and subtype; keep authorization valid until commit.
                try (var statement = connection.prepareStatement("""
                        SELECT u.user_id FROM users u JOIN students s ON s.student_id=u.user_id
                        WHERE u.user_id=? AND u.role='STUDENT' AND u.is_active=TRUE FOR SHARE
                        """)) {
                    statement.setInt(1, identity.userId());
                    statement.setQueryTimeout(15);
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SecurityException("Your student account is no longer available.");
                    }
                }
                T result = operation.run(connection, identity.userId());
                requireSession();
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
    }
}
