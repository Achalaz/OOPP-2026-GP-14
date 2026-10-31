package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AttendanceSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttendanceSessionDAO {

    // STEP 19: Maximum 15-session support

    // Lock offering while checking and saving sessions.
    // Must be called inside the same database transaction.
    public void lockOfferingForSessionWrite(
            Connection connection,
            int offeringId) throws SQLException {

        String sql = """
                SELECT offering_id
                FROM course_offerings
                WHERE offering_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, offeringId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Course offering not found.");
                }
            }
        }
    }

    // Count sessions of a specific type.
    // Exclude the current session when editing.
    public int countByOfferingAndTypeExcludingSession(
            Connection connection,
            int offeringId,
            String sessionType,
            int excludedSessionId) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS total
                FROM attendance_sessions
                WHERE offering_id = ?
                  AND session_type = ?
                  AND session_id <> ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, offeringId);
            statement.setString(2, sessionType);
            statement.setInt(3, excludedSessionId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt("total");
                }
            }
        }

        return 0;
    }

    // Create attendance session
    public void save(AttendanceSession session)
            throws SQLException {

        try (Connection connection =
                     DBConnection.getConnection()) {

            save(connection, session);
        }
    }

    public void save(
            Connection connection,
            AttendanceSession session) throws SQLException {

        String sql = """
                INSERT INTO attendance_sessions
                (offering_id, session_date, start_time,
                 end_time, session_type, topic, conducted_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, session.getOfferingId());

            statement.setDate(
                    2,
                    Date.valueOf(session.getSessionDate()));

            statement.setTime(
                    3,
                    Time.valueOf(session.getStartTime()));

            statement.setTime(
                    4,
                    Time.valueOf(session.getEndTime()));

            statement.setString(
                    5, session.getSessionType());

            statement.setString(
                    6, session.getTopic());

            if (session.getConductedBy() == null) {

                statement.setNull(
                        7, Types.INTEGER);

            } else {

                statement.setInt(
                        7, session.getConductedBy());
            }

            statement.executeUpdate();

            try (ResultSet resultSet =
                         statement.getGeneratedKeys()) {

                if (resultSet.next()) {

                    session.setSessionId(
                            resultSet.getInt(1));
                }
            }
        }
    }

    // Update attendance session
    public void update(AttendanceSession session)
            throws SQLException {

        try (Connection connection =
                     DBConnection.getConnection()) {

            update(connection, session);
        }
    }

    public void update(
            Connection connection,
            AttendanceSession session) throws SQLException {

        String sql = """
                UPDATE attendance_sessions
                SET offering_id = ?,
                    session_date = ?,
                    start_time = ?,
                    end_time = ?,
                    session_type = ?,
                    topic = ?,
                    conducted_by = ?
                WHERE session_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1, session.getOfferingId());

            statement.setDate(
                    2,
                    Date.valueOf(session.getSessionDate()));

            statement.setTime(
                    3,
                    Time.valueOf(session.getStartTime()));

            statement.setTime(
                    4,
                    Time.valueOf(session.getEndTime()));

            statement.setString(
                    5, session.getSessionType());

            statement.setString(
                    6, session.getTopic());

            if (session.getConductedBy() == null) {

                statement.setNull(
                        7, Types.INTEGER);

            } else {

                statement.setInt(
                        7, session.getConductedBy());
            }

            statement.setInt(
                    8, session.getSessionId());

            statement.executeUpdate();
        }
    }

    // Find one attendance session
    public AttendanceSession findById(int sessionId)
            throws SQLException {

        try (Connection connection =
                     DBConnection.getConnection()) {

            return findById(connection, sessionId);
        }
    }

    public AttendanceSession findById(
            Connection connection,
            int sessionId) throws SQLException {

        String sql = """
                SELECT *
                FROM attendance_sessions
                WHERE session_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {

                    return mapRow(resultSet);
                }
            }
        }

        return null;
    }

    // Get all sessions for one course offering
    public List<AttendanceSession> findByOfferingId(
            int offeringId) throws SQLException {

        try (Connection connection =
                     DBConnection.getConnection()) {

            return findByOfferingId(
                    connection, offeringId);
        }
    }

    public List<AttendanceSession> findByOfferingId(
            Connection connection,
            int offeringId) throws SQLException {

        List<AttendanceSession> sessions =
                new ArrayList<>();

        String sql = """
                SELECT *
                FROM attendance_sessions
                WHERE offering_id = ?
                ORDER BY session_date DESC, start_time DESC
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, offeringId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {

                    sessions.add(mapRow(resultSet));
                }
            }
        }

        return sessions;
    }

    // Count sessions by enrollment
    public int countSessionsByEnrollment(
            int enrollmentId) throws SQLException {

        try (Connection connection =
                     DBConnection.getConnection()) {

            return countSessionsByEnrollment(
                    connection, enrollmentId);
        }
    }

    public int countSessionsByEnrollment(
            Connection connection,
            int enrollmentId) throws SQLException {

        String sql = """
                SELECT COUNT(s.session_id) AS total_sessions
                FROM attendance_sessions s
                JOIN enrollments e
                    ON e.offering_id = s.offering_id
                WHERE e.enrollment_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, enrollmentId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {

                    return resultSet.getInt(
                            "total_sessions");
                }
            }
        }

        return 0;
    }

    // Delete attendance session
    public void delete(int sessionId)
            throws SQLException {

        try (Connection connection =
                     DBConnection.getConnection()) {

            delete(connection, sessionId);
        }
    }

    public void delete(
            Connection connection,
            int sessionId) throws SQLException {

        String sql = """
                DELETE FROM attendance_sessions
                WHERE session_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);
            statement.executeUpdate();
        }
    }

    // Convert database row into AttendanceSession object
    private AttendanceSession mapRow(
            ResultSet resultSet) throws SQLException {

        Timestamp createdAt =
                resultSet.getTimestamp("created_at");

        return new AttendanceSession(

                resultSet.getInt("session_id"),

                resultSet.getInt("offering_id"),

                resultSet.getDate("session_date")
                        .toLocalDate(),

                resultSet.getTime("start_time") == null
                        ? null
                        : resultSet.getTime("start_time")
                        .toLocalTime(),

                resultSet.getTime("end_time") == null
                        ? null
                        : resultSet.getTime("end_time")
                        .toLocalTime(),

                resultSet.getString("session_type"),

                resultSet.getString("topic"),

                (Integer) resultSet.getObject(
                        "conducted_by"),

                createdAt == null
                        ? null
                        : createdAt.toLocalDateTime()
        );
    }
}
