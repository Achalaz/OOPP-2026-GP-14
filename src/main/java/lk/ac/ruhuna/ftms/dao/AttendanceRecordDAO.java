package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AttendanceRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttendanceRecordDAO {

    public void saveOrUpdate(AttendanceRecord record) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            saveOrUpdate(connection, record);
        }
    }

    public void saveOrUpdate(Connection connection, AttendanceRecord record) throws SQLException {

        String sql = """
                INSERT INTO attendance_records
                (session_id, enrollment_id, attendance_status, marked_by)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    attendance_status = VALUES(attendance_status),
                    marked_by = VALUES(marked_by),
                    marked_at = CURRENT_TIMESTAMP
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, record.getSessionId());
            statement.setInt(2, record.getEnrollmentId());
            statement.setString(3, record.getAttendanceStatus());

            if (record.getMarkedBy() == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, record.getMarkedBy());
            }

            statement.executeUpdate();
        }
    }

    public List<AttendanceRecord> findBySessionId(int sessionId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findBySessionId(connection, sessionId);
        }
    }

    public List<AttendanceRecord> findBySessionId(Connection connection, int sessionId) throws SQLException {

        List<AttendanceRecord> records = new ArrayList<>();

        String sql = """
                SELECT ar.*
                FROM attendance_records ar
                JOIN attendance_sessions s ON s.session_id = ar.session_id
                JOIN enrollments e ON e.enrollment_id = ar.enrollment_id AND e.offering_id = s.offering_id
                WHERE ar.session_id = ?
                ORDER BY ar.enrollment_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    records.add(mapRow(resultSet));
                }
            }
        }

        return records;
    }

    public List<AttendanceRecord> findByEnrollmentId(int enrollmentId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findByEnrollmentId(connection, enrollmentId);
        }
    }

    public List<AttendanceRecord> findByEnrollmentId(Connection connection, int enrollmentId) throws SQLException {

        List<AttendanceRecord> records = new ArrayList<>();

        String sql = """
                SELECT ar.*
                FROM attendance_records ar
                JOIN attendance_sessions s ON s.session_id = ar.session_id
                JOIN enrollments e ON e.enrollment_id = ar.enrollment_id AND e.offering_id = s.offering_id
                WHERE ar.enrollment_id = ?
                ORDER BY marked_at DESC
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, enrollmentId);

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    records.add(mapRow(resultSet));
                }
            }
        }

        return records;
    }

    // Existing method - keep this
    public int countPresentByEnrollment(int enrollmentId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return countPresentByEnrollment(connection, enrollmentId);
        }
    }

    public int countPresentByEnrollment(Connection connection, int enrollmentId) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS attended_sessions
                FROM attendance_records
                WHERE enrollment_id = ?
                  AND attendance_status = 'PRESENT'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, enrollmentId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt("attended_sessions");
                }
            }
        }

        return 0;
    }

    public AttendanceRecord findBySessionAndEnrollment(int sessionId, int enrollmentId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findBySessionAndEnrollment(connection, sessionId, enrollmentId);
        }
    }

    public AttendanceRecord findBySessionAndEnrollment(Connection connection, int sessionId, int enrollmentId) throws SQLException {

        String sql = """
                SELECT ar.*
                FROM attendance_records ar
                JOIN attendance_sessions s ON s.session_id = ar.session_id
                JOIN enrollments e ON e.enrollment_id = ar.enrollment_id AND e.offering_id = s.offering_id
                WHERE ar.session_id = ?
                  AND ar.enrollment_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);
            statement.setInt(2, enrollmentId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
            }
        }

        return null;
    }

    public void delete(int attendanceRecordId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            delete(connection, attendanceRecordId);
        }
    }

    public void delete(Connection connection, int attendanceRecordId) throws SQLException {

        String sql = """
                DELETE FROM attendance_records
                WHERE attendance_record_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, attendanceRecordId);
            statement.executeUpdate();
        }
    }

    private AttendanceRecord mapRow(ResultSet resultSet)
            throws SQLException {

        Timestamp markedAt = resultSet.getTimestamp("marked_at");

        return new AttendanceRecord(
                resultSet.getInt("attendance_record_id"),
                resultSet.getInt("session_id"),
                resultSet.getInt("enrollment_id"),
                resultSet.getString("attendance_status"),
                (Integer) resultSet.getObject("marked_by"),
                markedAt == null ? null : markedAt.toLocalDateTime()
        );
    }
}
