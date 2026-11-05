package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.TimetableDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Read-only access to timetable entries assigned to one authenticated lecturer login. */
public final class LecturerTimetableService {
    private final TimetableDAO timetableDAO;
    private final ConnectionProvider connections;
    private final SessionIdentity loginIdentity;

    @FunctionalInterface
    interface ConnectionProvider { Connection open() throws SQLException; }

    public LecturerTimetableService() {
        this(new TimetableDAO(), DBConnection::getConnection);
    }

    LecturerTimetableService(TimetableDAO timetableDAO, ConnectionProvider connections) {
        this.timetableDAO = Objects.requireNonNull(timetableDAO);
        this.connections = Objects.requireNonNull(connections);
        loginIdentity = SessionManager.requireSession();
        requireLecturer();
    }

    /** No caller-supplied user ID: the DAO filter always comes from the captured login. */
    public List<TimetableEntry> getMyTimetable() {
        int lecturerId = requireLecturer();
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                requireActiveLecturerInDatabase(connection, lecturerId);
                List<TimetableEntry> entries = timetableDAO.findForLecturer(connection, lecturerId);
                requireLecturer();
                connection.commit();
                return List.copyOf(entries);
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Could not load your timetable. Check the database connection.", error);
        }
    }

    private int requireLecturer() {
        SessionManager.requireSession(loginIdentity);
        User user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"LECTURER".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {
            throw new SecurityException("An active lecturer login is required.");
        }
        return loginIdentity.userId();
    }

    private static void requireActiveLecturerInDatabase(
            Connection connection, int lecturerId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1
                FROM users u
                JOIN lecturers l ON l.lecturer_id = u.user_id
                WHERE u.user_id = ? AND u.role = 'LECTURER' AND u.is_active = 1
                """)) {
            statement.setInt(1, lecturerId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SecurityException("Your lecturer account is not active.");
                }
            }
        }
    }
}
