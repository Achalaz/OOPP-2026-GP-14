
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

public class StudentEligibilityDAO {

    // Read-only eligibility data
    public record StudentEligibility(
            int enrollmentId,
            int offeringId,
            String courseCode,
            String courseName,
            Double caPercentage,
            Double attendancePercentage,
            Boolean overallEligible,
            String reason,
            boolean saved
    ) {

        public String getDecision() {

            if (!saved || overallEligible == null) {
                return "PENDING";
            }

            return overallEligible
                    ? "ELIGIBLE"
                    : "NOT ELIGIBLE";
        }
    }

    // Load saved eligibility for one student
    public List<StudentEligibility> findByStudentId(
            int studentId) throws SQLException {

        if (studentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid Student ID."
            );
        }

        String sql = """
                SELECT
                    e.enrollment_id,
                    e.offering_id,
                    c.course_code,
                    c.course_name,
                    r.eligibility_id,
                    r.ca_score,
                    r.attendance_percentage,
                    r.overall_eligible,
                    r.reason

                FROM enrollments e

                JOIN course_offerings o
                    ON e.offering_id = o.offering_id

                JOIN courses c
                    ON o.course_id = c.course_id

                LEFT JOIN eligibility_records r
                    ON r.enrollment_id = e.enrollment_id

                WHERE e.student_id = ?

                ORDER BY
                    c.course_code,
                    e.enrollment_id
                """;

        List<StudentEligibility> results =
                new ArrayList<>();

        try (
                Connection connection =
                        DBConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(1, studentId);

            try (ResultSet rs =
                         statement.executeQuery()) {

                while (rs.next()) {

                    Double caPercentage =
                            nullableDouble(
                                    rs,
                                    "ca_score"
                            );

                    Double attendancePercentage =
                            nullableDouble(
                                    rs,
                                    "attendance_percentage"
                            );

                    Boolean overallEligible =
                            nullableBoolean(
                                    rs,
                                    "overall_eligible"
                            );

                    boolean saved =
                            rs.getObject(
                                    "eligibility_id"
                            ) != null;

                    results.add(
                            new StudentEligibility(
                                    rs.getInt(
                                            "enrollment_id"
                                    ),
                                    rs.getInt(
                                            "offering_id"
                                    ),
                                    rs.getString(
                                            "course_code"
                                    ),
                                    rs.getString(
                                            "course_name"
                                    ),
                                    caPercentage,
                                    attendancePercentage,
                                    overallEligible,
                                    rs.getString("reason"),
                                    saved
                            )
                    );
                }
            }
        }

        return results;
    }

    // Handle nullable percentages
    private static Double nullableDouble(
            ResultSet rs,
            String column) throws SQLException {

        double value = rs.getDouble(column);

        return rs.wasNull()
                ? null
                : value;
    }

    // Handle nullable eligibility decisions
    private static Boolean nullableBoolean(
            ResultSet rs,
            String column) throws SQLException {

        boolean value = rs.getBoolean(column);

        return rs.wasNull()
                ? null
                : value;
    }
}
