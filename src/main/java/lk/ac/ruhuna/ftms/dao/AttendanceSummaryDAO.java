
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AttendanceSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AttendanceSummaryDAO {

    private static final String BASE_SQL = """
            SELECT
                e.enrollment_id,
                st.registration_no,
                CONCAT(u.first_name, ' ', u.last_name)
                    AS student_name,

                SUM(CASE
                    WHEN sess.session_type = 'THEORY'
                    THEN 1 ELSE 0
                END) AS theory_sessions,

                SUM(CASE
                    WHEN sess.session_type = 'THEORY'
                     AND ar.attendance_status = 'PRESENT'
                    THEN 1 ELSE 0
                END) AS theory_present,

                SUM(CASE
                    WHEN sess.session_type = 'PRACTICAL'
                    THEN 1 ELSE 0
                END) AS practical_sessions,

                SUM(CASE
                    WHEN sess.session_type = 'PRACTICAL'
                     AND ar.attendance_status = 'PRESENT'
                    THEN 1 ELSE 0
                END) AS practical_present,

                SUM(CASE
                    WHEN sess.session_type IN
                         ('THEORY', 'PRACTICAL')
                     AND ar.attendance_status = 'LATE'
                    THEN 1 ELSE 0
                END) AS late_count,

                SUM(CASE
                    WHEN sess.session_type IN
                         ('THEORY', 'PRACTICAL')
                     AND ar.attendance_status = 'EXCUSED'
                    THEN 1 ELSE 0
                END) AS excused_count,

                SUM(CASE
                    WHEN sess.session_type IN
                         ('THEORY', 'PRACTICAL')
                     AND ar.attendance_status = 'MEDICAL'
                    THEN 1 ELSE 0
                END) AS medical_count,

                SUM(CASE
                    WHEN sess.session_type IN
                         ('THEORY', 'PRACTICAL')
                     AND ar.attendance_status IS NULL
                    THEN 1 ELSE 0
                END) AS unmarked_count

            FROM enrollments e

            JOIN students st
                ON st.student_id = e.student_id

            JOIN users u
                ON u.user_id = st.student_id

            LEFT JOIN attendance_sessions sess
                ON sess.offering_id = e.offering_id

            LEFT JOIN attendance_records ar
                ON ar.session_id = sess.session_id
               AND ar.enrollment_id = e.enrollment_id
            """;

    private static final String END_SQL = """
            GROUP BY
                e.enrollment_id,
                st.registration_no,
                u.first_name,
                u.last_name

            ORDER BY st.registration_no
            """;

    // Get attendance summaries of all students
    // enrolled in a specific course offering.

    public List<AttendanceSummary> findByOfferingId(
            int offeringId) throws SQLException {

        try (Connection connection = DBConnection.getConnection()) {
            return findByOfferingId(connection, offeringId);
        }
    }

    public List<AttendanceSummary> findByOfferingId(Connection connection, int offeringId) throws SQLException {
        return findWithFilter(connection, "WHERE e.offering_id = ?", offeringId);
    }

    // Get attendance summary of one enrollment.

    public AttendanceSummary findByEnrollmentId(
            int enrollmentId) throws SQLException {

        try (Connection connection = DBConnection.getConnection()) {
            return findByEnrollmentId(connection, enrollmentId);
        }
    }

    public AttendanceSummary findByEnrollmentId(Connection connection, int enrollmentId) throws SQLException {
        List<AttendanceSummary> summaries = findWithFilter(connection, "WHERE e.enrollment_id = ?", enrollmentId);

        return summaries.isEmpty()
                ? null
                : summaries.get(0);
    }

    // Execute the summary query.

    private List<AttendanceSummary> findWithFilter(
            Connection connection, String filter,
            int id) throws SQLException {

        String sql = BASE_SQL
                + filter
                + "\n"
                + END_SQL;

        List<AttendanceSummary> rows =
                new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet rs =
                         statement.executeQuery()) {

                while (rs.next()) {

                    AttendanceSummary summary =
                            new AttendanceSummary(

                                    rs.getInt("enrollment_id"),

                                    rs.getString("registration_no"),

                                    rs.getString("student_name"),

                                    rs.getInt("theory_sessions"),

                                    rs.getInt("theory_present"),

                                    rs.getInt("practical_sessions"),

                                    rs.getInt("practical_present"),

                                    rs.getInt("late_count"),

                                    rs.getInt("excused_count"),

                                    rs.getInt("medical_count"),

                                    rs.getInt("unmarked_count")
                            );

                    rows.add(summary);
                }
            }
        }

        return rows;
    }
}
