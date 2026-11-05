package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.TimetableDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Read-only timetable service for the currently signed-in undergraduate. */
public final class StudentTimetableService {

    public record TimetableData(int enrolledOfferingCount, List<TimetableEntry> entries) {}

    private final TimetableDAO timetableDAO;
    private final SessionIdentity loginIdentity;
    private final ConnectionProvider connections;

    @FunctionalInterface
    interface ConnectionProvider { Connection open() throws SQLException; }

    public StudentTimetableService() {
        this(new TimetableDAO());
    }

    public StudentTimetableService(TimetableDAO timetableDAO) {
        this(timetableDAO, DBConnection::getConnection);
    }

    StudentTimetableService(TimetableDAO timetableDAO, ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
        this.timetableDAO = Objects.requireNonNull(
                timetableDAO, "TimetableDAO is required.");
        this.loginIdentity = SessionManager.requireSession();
        requireStudent();
    }

    /** Only entries belonging to the student's enrolled/repeat offerings. */
    public List<TimetableEntry> getMyTimetable() {
        return getMyTimetableData().entries();
    }

    /** Enrollment and schedule are read in the same authorized transaction for accurate empty states. */
    public TimetableData getMyTimetableData() {
        int studentId = requireStudent();

        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                requireActiveStudentInDatabase(connection, studentId);
                int enrolledOfferings;
                try (var statement = connection.prepareStatement("""
                        SELECT COUNT(DISTINCT offering_id) FROM enrollments
                        WHERE student_id = ? AND status IN ('ENROLLED', 'REPEAT')
                        """)) {
                    statement.setInt(1, studentId);
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        enrolledOfferings = rows.getInt(1);
                    }
                }
                List<TimetableEntry> entries =
                        timetableDAO.findForStudent(connection, studentId);
                requireStudent();
                connection.commit();
                return new TimetableData(enrolledOfferings, List.copyOf(entries));
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
                    "Could not load your timetable. Check the database connection.",
                    error);
        }
    }

    private int requireStudent() {
        SessionManager.requireSession(loginIdentity);
        User user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive()
                || !"STUDENT".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {
            throw new SecurityException("An active student login is required.");
        }
        return loginIdentity.userId();
    }

    private static void requireActiveStudentInDatabase(
            Connection connection, int studentId) throws SQLException {
        String sql = """
                SELECT 1
                FROM users u
                JOIN students s ON s.student_id = u.user_id
                WHERE u.user_id = ?
                  AND u.role = 'STUDENT'
                  AND u.is_active = 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SecurityException(
                            "Your undergraduate account is not active.");
                }
            }
        }
    }
}
