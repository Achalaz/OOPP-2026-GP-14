package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AttendanceRecordDAO {

    public int countPresentByEnrollment(int enrollmentId) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS attended_sessions
                FROM attendance_records
                WHERE enrollment_id = ?
                  AND attendance_status = 'PRESENT'
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, enrollmentId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt("attended_sessions");
                }
            }
        }

        return 0;
    }
}