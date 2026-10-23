package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LecturerCourseAssignmentDAO {

    public boolean isAssignedToOffering(
            int lecturerUserId,
            int offeringId) throws SQLException {

        if (lecturerUserId <= 0 || offeringId <= 0) {
            return false;
        }

        String sql = """
            SELECT 1
            FROM users u
            JOIN lecturers l
                ON l.lecturer_id = u.user_id
            JOIN lecturer_course_assignments lca
                ON lca.lecturer_id = l.lecturer_id
            JOIN course_offerings co
                ON co.offering_id = lca.offering_id
            WHERE u.user_id = ?
              AND u.role = 'LECTURER'
              AND u.is_active = 1
              AND co.offering_id = ?
            LIMIT 1
            """;

        try (Connection connection =
                     DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, lecturerUserId);
            statement.setInt(2, offeringId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }
}
