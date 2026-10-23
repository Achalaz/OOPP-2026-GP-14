
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
                WHERE u.user_id = ?
                  AND u.role = 'LECTURER'
                  AND u.is_active = 1
                  AND lca.offering_id = ?
                LIMIT 1
                """;

        try (Connection connection =
                     DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, lecturerUserId);
            statement.setInt(2, offeringId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Integer> getAssignedOfferingIds(
            int lecturerUserId) throws SQLException {

        List<Integer> offeringIds = new ArrayList<>();

        if (lecturerUserId <= 0) {
            return offeringIds;
        }

        String sql = """
                SELECT DISTINCT lca.offering_id
                FROM users u
                JOIN lecturers l
                    ON l.lecturer_id = u.user_id
                JOIN lecturer_course_assignments lca
                    ON lca.lecturer_id = l.lecturer_id
                WHERE u.user_id = ?
                  AND u.role = 'LECTURER'
                  AND u.is_active = 1
                ORDER BY lca.offering_id DESC
                """;

        try (Connection connection =
                     DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, lecturerUserId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    offeringIds.add(rs.getInt("offering_id"));
                }
            }
        }

        return offeringIds;
    }
}
