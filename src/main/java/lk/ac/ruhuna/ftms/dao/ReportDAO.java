package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.ResultReportRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReportDAO {

    // Student ComboBox item
    public record StudentOption(
            int studentId,
            String registrationNo,
            String studentName
    ) {
        @Override
        public String toString() {
            return registrationNo + " - " + studentName;
        }
    }

    // Course Offering ComboBox item
    public record OfferingOption(
            int offeringId,
            String courseCode,
            String courseName,
            int semesterId
    ) {
        @Override
        public String toString() {
            return courseCode + " - " + courseName;
        }
    }

    // ---------------------------------------------------------
    // LOAD STUDENTS
    // ---------------------------------------------------------

    public List<StudentOption> getStudents()
            throws SQLException {

        String sql = """
                SELECT
                    s.student_id,
                    s.registration_no,
                    CONCAT(
                        u.first_name,
                        ' ',
                        u.last_name
                    ) AS student_name
                FROM students s
                JOIN users u
                    ON u.user_id = s.student_id
                ORDER BY s.registration_no
                """;

        List<StudentOption> students =
                new ArrayList<>();

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet resultSet =
                     statement.executeQuery()) {

            while (resultSet.next()) {

                students.add(
                        new StudentOption(
                                resultSet.getInt(
                                        "student_id"
                                ),
                                resultSet.getString(
                                        "registration_no"
                                ),
                                resultSet.getString(
                                        "student_name"
                                )
                        )
                );
            }
        }

        return students;
    }

    // ---------------------------------------------------------
    // LOAD COURSE OFFERINGS
    // ---------------------------------------------------------

    public List<OfferingOption> getCourseOfferings()
            throws SQLException {

        String sql = """
                SELECT
                    co.offering_id,
                    co.semester_id,
                    c.course_code,
                    c.course_name
                FROM course_offerings co
                JOIN courses c
                    ON c.course_id = co.course_id
                ORDER BY
                    co.semester_id,
                    c.course_code
                """;

        List<OfferingOption> offerings =
                new ArrayList<>();

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet resultSet =
                     statement.executeQuery()) {

            while (resultSet.next()) {

                offerings.add(
                        new OfferingOption(
                                resultSet.getInt(
                                        "offering_id"
                                ),
                                resultSet.getString(
                                        "course_code"
                                ),
                                resultSet.getString(
                                        "course_name"
                                ),
                                resultSet.getInt(
                                        "semester_id"
                                )
                        )
                );
            }
        }

        return offerings;
    }

    // ---------------------------------------------------------
    // INDIVIDUAL STUDENT RESULT REPORT
    // ---------------------------------------------------------

    public List<ResultReportRow> getStudentResultReport(
            int studentId
    ) throws SQLException {

        String sql = """
                SELECT
                    e.enrollment_id,
                    s.registration_no,

                    CONCAT(
                        u.first_name,
                        ' ',
                        u.last_name
                    ) AS student_name,

                    c.course_code,
                    c.course_name,
                    c.credits,

                    cr.ca_mark,
                    cr.final_exam_mark,
                    cr.overall_mark,
                    cr.grade_letter,
                    cr.grade_point,
                    cr.result_status

                FROM enrollments e

                JOIN students s
                    ON s.student_id = e.student_id

                JOIN users u
                    ON u.user_id = s.student_id

                JOIN course_offerings co
                    ON co.offering_id = e.offering_id

                JOIN courses c
                    ON c.course_id = co.course_id

                LEFT JOIN course_results cr
                    ON cr.enrollment_id = e.enrollment_id

                WHERE e.student_id = ?

                ORDER BY
                    co.semester_id,
                    c.course_code,
                    e.attempt_no
                """;

        List<ResultReportRow> rows =
                new ArrayList<>();

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    studentId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {

                    rows.add(
                            mapReportRow(
                                    resultSet
                            )
                    );
                }
            }
        }

        return rows;
    }

    // ---------------------------------------------------------
    // BATCH / COURSE OFFERING RESULT REPORT
    // ---------------------------------------------------------

    public List<ResultReportRow> getOfferingResultReport(
            int offeringId
    ) throws SQLException {

        String sql = """
                SELECT
                    e.enrollment_id,
                    s.registration_no,

                    CONCAT(
                        u.first_name,
                        ' ',
                        u.last_name
                    ) AS student_name,

                    c.course_code,
                    c.course_name,
                    c.credits,

                    cr.ca_mark,
                    cr.final_exam_mark,
                    cr.overall_mark,
                    cr.grade_letter,
                    cr.grade_point,
                    cr.result_status

                FROM enrollments e

                JOIN students s
                    ON s.student_id = e.student_id

                JOIN users u
                    ON u.user_id = s.student_id

                JOIN course_offerings co
                    ON co.offering_id = e.offering_id

                JOIN courses c
                    ON c.course_id = co.course_id

                LEFT JOIN course_results cr
                    ON cr.enrollment_id = e.enrollment_id

                WHERE e.offering_id = ?

                ORDER BY
                    s.registration_no,
                    e.attempt_no
                """;

        List<ResultReportRow> rows =
                new ArrayList<>();

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    offeringId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {

                    rows.add(
                            mapReportRow(
                                    resultSet
                            )
                    );
                }
            }
        }

        return rows;
    }

    // ---------------------------------------------------------
    // RESULTSET -> REPORT ROW
    // ---------------------------------------------------------

    private ResultReportRow mapReportRow(
            ResultSet resultSet
    ) throws SQLException {

        return new ResultReportRow(
                resultSet.getInt(
                        "enrollment_id"
                ),

                resultSet.getString(
                        "registration_no"
                ),

                resultSet.getString(
                        "student_name"
                ),

                resultSet.getString(
                        "course_code"
                ),

                resultSet.getString(
                        "course_name"
                ),

                resultSet.getDouble(
                        "credits"
                ),

                getNullableDouble(
                        resultSet,
                        "ca_mark"
                ),

                getNullableDouble(
                        resultSet,
                        "final_exam_mark"
                ),

                getNullableDouble(
                        resultSet,
                        "overall_mark"
                ),

                resultSet.getString(
                        "grade_letter"
                ),

                getNullableDouble(
                        resultSet,
                        "grade_point"
                ),

                resultSet.getString(
                        "result_status"
                )
        );
    }

    // ---------------------------------------------------------
    // NULLABLE DECIMAL HELPER
    // ---------------------------------------------------------

    private Double getNullableDouble(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {

        double value =
                resultSet.getDouble(
                        columnName
                );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }
}