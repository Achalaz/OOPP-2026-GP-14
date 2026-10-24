package lk.ac.ruhuna.ftms.dao;

import java.sql.*;
import java.util.*;
import lk.ac.ruhuna.ftms.model.Enrollment;
import lk.ac.ruhuna.ftms.model.CourseOffering;

public class EnrollmentDAO {
    public record EligibleStudent(int studentId, String registrationNo, String username, String name) {
        @Override public String toString() {
            return studentId + " | " + registrationNo + " | " + name + " (" + username + ")";
        }
    }

    private static final String ENROLLMENTS = """
            SELECT e.enrollment_id,e.student_id,st.registration_no,u.username,
                   CONCAT_WS(' ',u.first_name,u.last_name) AS student_name,
                   o.offering_id,c.course_code,c.course_name,
                   COALESCE(NULLIF(s.semester_name,''),CONCAT('Semester ',s.semester_no)) AS semester,
                   ay.academic_year_name,o.offering_group,o.status AS offering_status,
                   e.attempt_no,e.status,e.enrolled_at
            FROM enrollments e JOIN students st ON st.student_id=e.student_id
            JOIN users u ON u.user_id=st.student_id JOIN course_offerings o ON o.offering_id=e.offering_id
            JOIN courses c ON c.course_id=o.course_id JOIN semesters s ON s.semester_id=o.semester_id
            JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
            """;

    public List<Enrollment> findAllEnrollments(Connection connection) throws SQLException {
        return searchEnrollments(connection, "");
    }

    public List<Enrollment> searchEnrollments(Connection connection, String keyword) throws SQLException {
        var result = new ArrayList<Enrollment>();
        try (var statement = connection.prepareStatement(ENROLLMENTS + """
                WHERE CONCAT_WS(' ',e.student_id,st.registration_no,u.username,u.first_name,u.last_name,
                                c.course_code,c.course_name,ay.academic_year_name,e.status) LIKE ?
                ORDER BY e.enrollment_id DESC
                """)) {
            statement.setString(1, search(keyword));
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) { while (rows.next()) result.add(map(rows)); }
        }
        return List.copyOf(result);
    }

    public Enrollment findEnrollmentById(Connection connection, int id, boolean lock) throws SQLException {
        try (var statement = connection.prepareStatement(ENROLLMENTS + " WHERE e.enrollment_id=?"
                + (lock ? " FOR UPDATE" : ""))) {
            statement.setInt(1, id);
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) { return rows.next() ? map(rows) : null; }
        }
    }

    public List<EligibleStudent> findEligibleStudents(Connection connection, String keyword) throws SQLException {
        var result = new ArrayList<EligibleStudent>();
        try (var statement = connection.prepareStatement("""
                SELECT u.user_id,s.registration_no,u.username,CONCAT_WS(' ',u.first_name,u.last_name)
                FROM users u JOIN students s ON s.student_id=u.user_id
                WHERE u.role='STUDENT' AND u.is_active=TRUE AND
                      CONCAT_WS(' ',u.user_id,s.registration_no,u.username,u.first_name,u.last_name) LIKE ?
                ORDER BY s.registration_no
                """)) {
            statement.setString(1, search(keyword));
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(new EligibleStudent(rows.getInt(1), rows.getString(2),
                        rows.getString(3), rows.getString(4)));
            }
        }
        return List.copyOf(result);
    }

    public List<CourseOffering> findAvailableOfferings(Connection connection, String keyword) throws SQLException {
        return new CourseOfferingDAO().findAvailableOfferings(connection, keyword);
    }

    public void requireEligibleStudent(Connection connection, int id) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT u.role,u.is_active,s.student_id FROM users u
                LEFT JOIN students s ON s.student_id=u.user_id WHERE u.user_id=? FOR SHARE
                """)) {
            statement.setInt(1, id);
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !"STUDENT".equals(rows.getString(1)) || !rows.getBoolean(2))
                    throw new IllegalArgumentException("Select an existing active Student account.");
                if (rows.getObject(3) == null)
                    throw new IllegalArgumentException("Student profile is missing. Complete the student's profile in User Profiles before enrolling.");
            }
        }
    }

    public String lockOfferingStatus(Connection connection, int offering) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT status FROM course_offerings WHERE offering_id=? FOR UPDATE")) {
            statement.setInt(1, offering);
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Select an existing course offering.");
                return rows.getString(1);
            }
        }
    }

    public int enrollStudent(Connection connection, int student, int offering, int attempt, String status) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO enrollments(student_id,offering_id,attempt_no,status) VALUES(?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, student); statement.setInt(2, offering);
            statement.setInt(3, attempt); statement.setString(4, status);
            statement.setQueryTimeout(15);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Enrollment ID was not returned.");
                return keys.getInt(1);
            }
        }
    }

    public void updateEnrollmentStatus(Connection connection, int id, String status) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE enrollments SET status=? WHERE enrollment_id=?")) {
            statement.setString(1, status); statement.setInt(2, id);
            statement.setQueryTimeout(15);
            if (statement.executeUpdate() != 1) throw new SQLException("Enrollment is no longer available. Refresh and try again.");
        }
    }

    /** Locks permission rows for the full material read/export transaction. Completed courses remain readable. */
    public void requireMaterialAccess(Connection connection, int student, int offering) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT e.enrollment_id FROM enrollments e
                JOIN course_offerings o ON o.offering_id=e.offering_id
                WHERE e.student_id=? AND e.offering_id=?
                  AND e.status IN ('ENROLLED','REPEAT','COMPLETED')
                  AND o.status IN ('PLANNED','ACTIVE','COMPLETED') FOR SHARE
                """)) {
            statement.setInt(1, student); statement.setInt(2, offering);
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException("You do not have an accessible enrollment for this course offering.");
            }
        }
    }

    private static String search(String keyword) { return "%" + Objects.toString(keyword, "").strip() + "%"; }
    private static Enrollment map(ResultSet r) throws SQLException {
        var date = r.getTimestamp("enrolled_at");
        return new Enrollment(r.getInt("enrollment_id"),r.getInt("student_id"),r.getString("registration_no"),
                r.getString("username"),r.getString("student_name"),r.getInt("offering_id"),r.getString("course_code"),
                r.getString("course_name"),r.getString("semester"),r.getString("academic_year_name"),
                r.getString("offering_group"),r.getString("offering_status"),r.getInt("attempt_no"),
                r.getString("status"),date == null ? null : date.toLocalDateTime());
    }

    public record StudentCourse(int enrollmentId, int offeringId, String courseCode, String courseName,
                                String semester, double credits, int attempt, String status) {
        public String toString() { return courseCode + " - " + courseName; }
    }

    /** The service supplies the authenticated student ID within its authorized transaction. */
    public java.util.List<StudentCourse> findByStudent(java.sql.Connection connection, int studentId)
            throws java.sql.SQLException {
        var courses = new java.util.ArrayList<StudentCourse>();
        try (var statement = connection.prepareStatement("""
                SELECT e.enrollment_id,o.offering_id,c.course_code,c.course_name,
                       CONCAT(ay.academic_year_name,' - ',COALESCE(s.semester_name,CONCAT('Semester ',s.semester_no))),
                       c.credits,e.attempt_no,e.status
                FROM enrollments e JOIN course_offerings o ON o.offering_id=e.offering_id
                JOIN courses c ON c.course_id=o.course_id JOIN semesters s ON s.semester_id=o.semester_id
                JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
                WHERE e.student_id=? AND o.status<>'CANCELLED'
                ORDER BY ay.start_date,s.semester_no,c.course_code,e.attempt_no
                """)) {
            statement.setInt(1, studentId);
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) courses.add(new StudentCourse(rows.getInt(1),rows.getInt(2),
                        rows.getString(3),rows.getString(4),rows.getString(5),rows.getDouble(6),
                        rows.getInt(7),rows.getString(8)));
            }
        }
        return java.util.List.copyOf(courses);
    }
}
