package lk.ac.ruhuna.ftms.dao;

public class EnrollmentDAO {
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
