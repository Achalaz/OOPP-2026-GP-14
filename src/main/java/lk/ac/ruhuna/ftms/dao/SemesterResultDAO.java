package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.model.SemesterResult;
import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class SemesterResultDAO {

    public void saveOrUpdateSemesterResult(SemesterResult result) throws SQLException {

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

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, result.getStudentId());
            statement.setInt(2, result.getSemesterId());
            statement.setDouble(3, result.getTotalRegisteredCredits());
            statement.setDouble(4, result.getTotalEarnedCredits());
            statement.setDouble(5, result.getTotalQualityPoints());

            if (result.getSgpa() != null) {
                statement.setDouble(6, result.getSgpa());
            } else {
                statement.setNull(6, java.sql.Types.DECIMAL);
            }

            if (result.getCgpa() != null) {
                statement.setDouble(7, result.getCgpa());
            } else {
                statement.setNull(7, java.sql.Types.DECIMAL);
            }

            statement.executeUpdate();
        }
    }

    public SemesterResult getByStudentAndSemester(
            int studentId,
            int semesterId) throws SQLException {

        String sql = """
                SELECT *
                FROM semester_results
                WHERE student_id = ?
                  AND semester_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);
            statement.setInt(2, semesterId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return mapSemesterResult(resultSet);
                }
            }
        }

        return null;
    }

    public List<SemesterResult> getSemesterResultsByStudent(
            int studentId) throws SQLException {

        String sql = """
                SELECT *
                FROM semester_results
                WHERE student_id = ?
                ORDER BY semester_id
                """;

        List<SemesterResult> results = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    results.add(mapSemesterResult(resultSet));
                }
            }
        }

        return results;
    }

    private SemesterResult mapSemesterResult(ResultSet resultSet)
            throws SQLException {

        SemesterResult result = new SemesterResult();

        result.setSemesterResultId(
                resultSet.getInt("semester_result_id")
        );

        result.setStudentId(
                resultSet.getInt("student_id")
        );

        result.setSemesterId(
                resultSet.getInt("semester_id")
        );

        result.setTotalRegisteredCredits(
                resultSet.getDouble("total_registered_credits")
        );

        result.setTotalEarnedCredits(
                resultSet.getDouble("total_earned_credits")
        );

        result.setTotalQualityPoints(
                resultSet.getDouble("total_quality_points")
        );

        double sgpa = resultSet.getDouble("sgpa");
        result.setSgpa(
                resultSet.wasNull() ? null : sgpa
        );

        double cgpa = resultSet.getDouble("cgpa");
        result.setCgpa(
                resultSet.wasNull() ? null : cgpa
        );

        Timestamp calculatedAt =
                resultSet.getTimestamp("calculated_at");

        if (calculatedAt != null) {
            result.setCalculatedAt(
                    calculatedAt.toLocalDateTime()
            );
        }

        return result;
    }
}