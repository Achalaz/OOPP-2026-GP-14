package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AttendanceSessionDAO {

    public int countSessionsByEnrollment(int enrollmentId) throws SQLException {

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
}