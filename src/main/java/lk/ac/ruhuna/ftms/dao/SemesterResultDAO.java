package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.SemesterResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class SemesterResultDAO {

    // Data required for GPA calculation and GPA table display
    public record GpaCourseRow(
            int enrollmentId,
            int semesterId,
            String courseCode,
            String courseName,
            double credits,
            String enrollmentStatus,
            String gradeLetter,
            Double gradePoint,
            String resultStatus,
            boolean passed,
            boolean published
    ) {
    }

    // Save new semester result or update existing result
    public void saveOrUpdateSemesterResult(
            SemesterResult result
    ) throws SQLException {

        String sql = """
                INSERT INTO semester_results
                (
                    student_id,
                    semester_id,
                    total_registered_credits,
                    total_earned_credits,
                    total_quality_points,
                    sgpa,
                    cgpa
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_registered_credits = VALUES(total_registered_credits),
                    total_earned_credits = VALUES(total_earned_credits),
                    total_quality_points = VALUES(total_quality_points),
                    sgpa = VALUES(sgpa),
                    cgpa = VALUES(cgpa),
                    calculated_at = CURRENT_TIMESTAMP
                """;

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    result.getStudentId()
            );

            statement.setInt(
                    2,
                    result.getSemesterId()
            );

            statement.setDouble(
                    3,
                    result.getTotalRegisteredCredits()
            );

            statement.setDouble(
                    4,
                    result.getTotalEarnedCredits()
            );

            statement.setDouble(
                    5,
                    result.getTotalQualityPoints()
            );

            if (result.getSgpa() != null) {

                statement.setDouble(
                        6,
                        result.getSgpa()
                );

            } else {

                statement.setNull(
                        6,
                        java.sql.Types.DECIMAL
                );
            }

            if (result.getCgpa() != null) {

                statement.setDouble(
                        7,
                        result.getCgpa()
                );

            } else {

                statement.setNull(
                        7,
                        java.sql.Types.DECIMAL
                );
            }

            statement.executeUpdate();
        }
    }

    // Get GPA result for one student and one semester
    public SemesterResult getByStudentAndSemester(
            int studentId,
            int semesterId
    ) throws SQLException {

        String sql = """
                SELECT *
                FROM semester_results
                WHERE student_id = ?
                  AND semester_id = ?
                """;

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    studentId
            );

            statement.setInt(
                    2,
                    semesterId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {

                    return mapSemesterResult(
                            resultSet
                    );
                }
            }
        }

        return null;
    }

    // Get all stored semester GPA results for a student
    public List<SemesterResult> getSemesterResultsByStudent(
            int studentId
    ) throws SQLException {

        String sql = """
                SELECT *
                FROM semester_results
                WHERE student_id = ?
                ORDER BY semester_id
                """;

        List<SemesterResult> results =
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

                    results.add(
                            mapSemesterResult(
                                    resultSet
                            )
                    );
                }
            }
        }

        return results;
    }

    // Get course/result details used to calculate SGPA
    public List<GpaCourseRow> getCourseRowsForSemester(
            int studentId,
            int semesterId
    ) throws SQLException {

        String sql = """
                SELECT
                    e.enrollment_id,
                    co.semester_id,
                    c.course_code,
                    c.course_name,
                    c.credits,
                    e.status AS enrollment_status,
                    cr.grade_letter,
                    cr.grade_point,
                    cr.result_status,
                    COALESCE(gr.is_pass, FALSE) AS is_pass,
                    COALESCE(cr.published, FALSE) AS published
                FROM enrollments e
                JOIN course_offerings co
                    ON co.offering_id = e.offering_id
                JOIN courses c
                    ON c.course_id = co.course_id
                LEFT JOIN course_results cr
                    ON cr.enrollment_id = e.enrollment_id
                LEFT JOIN grade_rules gr
                    ON gr.grade_rule_id = cr.grade_rule_id
                WHERE e.student_id = ?
                  AND co.semester_id = ?
                ORDER BY c.course_code, e.attempt_no
                """;

        List<GpaCourseRow> rows =
                new ArrayList<>();

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    studentId
            );

            statement.setInt(
                    2,
                    semesterId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {

                    double gradePointValue =
                            resultSet.getDouble(
                                    "grade_point"
                            );

                    Double gradePoint =
                            resultSet.wasNull()
                                    ? null
                                    : gradePointValue;

                    GpaCourseRow row =
                            new GpaCourseRow(

                                    resultSet.getInt(
                                            "enrollment_id"
                                    ),

                                    resultSet.getInt(
                                            "semester_id"
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

                                    resultSet.getString(
                                            "enrollment_status"
                                    ),

                                    resultSet.getString(
                                            "grade_letter"
                                    ),

                                    gradePoint,

                                    resultSet.getString(
                                            "result_status"
                                    ),

                                    resultSet.getBoolean(
                                            "is_pass"
                                    ),

                                    resultSet.getBoolean(
                                            "published"
                                    )
                            );

                    rows.add(row);
                }
            }
        }

        return rows;
    }

    // Get all semesters in which the student has enrollments
    // Used for CGPA calculation
    public List<Integer> getSemesterIdsByStudent(
            int studentId
    ) throws SQLException {

        String sql = """
                SELECT DISTINCT co.semester_id
                FROM enrollments e
                JOIN course_offerings co
                    ON co.offering_id = e.offering_id
                WHERE e.student_id = ?
                ORDER BY co.semester_id
                """;

        List<Integer> semesterIds =
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

                    semesterIds.add(
                            resultSet.getInt(
                                    "semester_id"
                            )
                    );
                }
            }
        }

        return semesterIds;
    }

    // Convert ResultSet row into SemesterResult object
    private SemesterResult mapSemesterResult(
            ResultSet resultSet
    ) throws SQLException {

        SemesterResult result =
                new SemesterResult();

        result.setSemesterResultId(
                resultSet.getInt(
                        "semester_result_id"
                )
        );

        result.setStudentId(
                resultSet.getInt(
                        "student_id"
                )
        );

        result.setSemesterId(
                resultSet.getInt(
                        "semester_id"
                )
        );

        result.setTotalRegisteredCredits(
                resultSet.getDouble(
                        "total_registered_credits"
                )
        );

        result.setTotalEarnedCredits(
                resultSet.getDouble(
                        "total_earned_credits"
                )
        );

        result.setTotalQualityPoints(
                resultSet.getDouble(
                        "total_quality_points"
                )
        );

        double sgpa =
                resultSet.getDouble(
                        "sgpa"
                );

        if (resultSet.wasNull()) {

            result.setSgpa(null);

        } else {

            result.setSgpa(sgpa);
        }

        double cgpa =
                resultSet.getDouble(
                        "cgpa"
                );

        if (resultSet.wasNull()) {

            result.setCgpa(null);

        } else {

            result.setCgpa(cgpa);
        }

        Timestamp calculatedAt =
                resultSet.getTimestamp(
                        "calculated_at"
                );

        if (calculatedAt != null) {

            result.setCalculatedAt(
                    calculatedAt.toLocalDateTime()
            );
        }

        return result;
    }
}