package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AttendanceSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttendanceSessionDAO {

    // Create attendance session
    public void save(AttendanceSession session) throws SQLException {

        String sql = """
                INSERT INTO attendance_sessions
                (offering_id, session_date, start_time, end_time,
                 session_type, topic, conducted_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, session.getOfferingId());
            statement.setDate(2, Date.valueOf(session.getSessionDate()));
            statement.setTime(3, Time.valueOf(session.getStartTime()));
            statement.setTime(4, Time.valueOf(session.getEndTime()));
            statement.setString(5, session.getSessionType());
            statement.setString(6, session.getTopic());

            if (session.getConductedBy() == null) {
                statement.setNull(7, Types.INTEGER);
            } else {
                statement.setInt(7, session.getConductedBy());
            }

            statement.executeUpdate();

            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    session.setSessionId(resultSet.getInt(1));
                }
            }
        }
    }

    // Update attendance session
    public void update(AttendanceSession session) throws SQLException {

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

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, session.getOfferingId());
            statement.setDate(2, Date.valueOf(session.getSessionDate()));
            statement.setTime(3, Time.valueOf(session.getStartTime()));
            statement.setTime(4, Time.valueOf(session.getEndTime()));
            statement.setString(5, session.getSessionType());
            statement.setString(6, session.getTopic());

            if (session.getConductedBy() == null) {
                statement.setNull(7, Types.INTEGER);
            } else {
                statement.setInt(7, session.getConductedBy());
            }

            statement.setInt(8, session.getSessionId());

            statement.executeUpdate();
        }
    }

    // Find one session
    public AttendanceSession findById(int sessionId) throws SQLException {

        String sql = """
                SELECT *
                FROM attendance_sessions
                WHERE session_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
            }
        }

        return null;
    }

    // Get all sessions for one course offering
    public List<AttendanceSession> findByOfferingId(int offeringId)
            throws SQLException {

        List<AttendanceSession> sessions = new ArrayList<>();

        String sql = """
                SELECT *
                FROM attendance_sessions
                WHERE offering_id = ?
                ORDER BY session_date DESC, start_time DESC
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, offeringId);

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    sessions.add(mapRow(resultSet));
                }
            }
        }

        return sessions;
    }

    // Existing method - keep this
    public int countSessionsByEnrollment(int enrollmentId)
            throws SQLException {

        String sql = """
                SELECT COUNT(s.session_id) AS total_sessions
                FROM attendance_sessions s
                JOIN enrollments e
                    ON e.offering_id = s.offering_id
                WHERE e.enrollment_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, enrollmentId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt("total_sessions");
                }
            }
        }

        return 0;
    }

    // Delete session
    public void delete(int sessionId) throws SQLException {

        String sql = """
                DELETE FROM attendance_sessions
                WHERE session_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);
            statement.executeUpdate();
        }
    }

    // Convert DB row into AttendanceSession object
    private AttendanceSession mapRow(ResultSet resultSet)
            throws SQLException {

        Timestamp createdAt = resultSet.getTimestamp("created_at");

        return new AttendanceSession(
                resultSet.getInt("session_id"),
                resultSet.getInt("offering_id"),
                resultSet.getDate("session_date").toLocalDate(),
                resultSet.getTime("start_time").toLocalTime(),
                resultSet.getTime("end_time").toLocalTime(),
                resultSet.getString("session_type"),
                resultSet.getString("topic"),
                (Integer) resultSet.getObject("conducted_by"),
                createdAt == null
                        ? null
                        : createdAt.toLocalDateTime()
        );
    }
}