package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.model.AttendanceEnrollment;
import lk.ac.ruhuna.ftms.model.AttendanceOffering;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Scope queries only. Attendance writes and calculations remain in their existing DAOs.
 * Called inside the service transaction; shared locks keep authorization valid until commit.
 */
public class AttendanceAccessDAO {
    public int requireOfficerDepartment(Connection connection, int userId) throws SQLException {
        String sql = """
                SELECT u.department_id FROM users u
                JOIN technical_officers t ON t.technical_officer_id = u.user_id
                JOIN departments d ON d.department_id = u.department_id
                WHERE u.user_id = ? AND u.role = 'TECHNICAL_OFFICER' AND u.is_active = 1
                FOR SHARE
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException(
                        "An active Technical Officer account with an assigned department is required.");
                return rows.getInt("department_id");
            }
        }
    }

    public void requireOffering(Connection connection, int departmentId, int offeringId) throws SQLException {
        String sql = """
                SELECT o.offering_id FROM course_offerings o
                JOIN courses c ON c.course_id = o.course_id
                WHERE o.offering_id = ? AND c.department_id = ? FOR SHARE
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offeringId);
            statement.setInt(2, departmentId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException("This course offering is outside your authorized department.");
            }
        }
    }

    public int sessionOffering(Connection connection, int sessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT offering_id FROM attendance_sessions WHERE session_id = ? FOR SHARE")) {
            statement.setInt(1, sessionId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException("The attendance session is not available.");
                return rows.getInt(1);
            }
        }
    }

    public int enrollmentOffering(Connection connection, int enrollmentId) throws SQLException {
        String sql = """
                SELECT e.offering_id FROM enrollments e
                JOIN students s ON s.student_id = e.student_id
                JOIN users u ON u.user_id = s.student_id
                WHERE e.enrollment_id = ? AND u.role = 'STUDENT' FOR SHARE
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enrollmentId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException("The undergraduate enrollment is not available.");
                return rows.getInt(1);
            }
        }
    }

    public List<AttendanceOffering> findOfferings(Connection connection, int departmentId) throws SQLException {
        String sql = """
                SELECT o.offering_id, c.course_code, c.course_name, o.semester_id, o.offering_group
                FROM course_offerings o JOIN courses c ON c.course_id = o.course_id
                WHERE c.department_id = ? AND o.status = 'ACTIVE'
                ORDER BY c.course_code, o.semester_id DESC, o.offering_group
                FOR SHARE
                """;
        List<AttendanceOffering> choices = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, departmentId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) choices.add(new AttendanceOffering(rows.getInt(1), rows.getString(2),
                        rows.getString(3), rows.getInt(4), rows.getString(5)));
            }
        }
        return choices;
    }

    public List<AttendanceEnrollment> findEnrollments(Connection connection, int offeringId) throws SQLException {
        String sql = """
                SELECT e.enrollment_id, s.registration_no, CONCAT(u.first_name, ' ', u.last_name)
                FROM enrollments e JOIN students s ON s.student_id = e.student_id
                JOIN users u ON u.user_id = s.student_id
                WHERE e.offering_id = ? AND u.role = 'STUDENT' ORDER BY s.registration_no FOR SHARE
                """;
        List<AttendanceEnrollment> choices = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offeringId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) choices.add(new AttendanceEnrollment(rows.getInt(1), rows.getString(2), rows.getString(3)));
            }
        }
        return choices;
    }
}
