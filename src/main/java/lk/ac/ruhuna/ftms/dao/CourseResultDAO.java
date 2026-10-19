package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.ResultRow;
import lk.ac.ruhuna.ftms.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class CourseResultDAO {
    /** Read projections for calculation; all assessment rows are included, even without marks. */
    public record AssessmentInput(int assessmentId, String assessmentType, BigDecimal maxMark,
                                  BigDecimal weightPercentage, BigDecimal marksObtained, String markStatus) { }

    public record CalculationData(int enrollmentId, String enrollmentStatus, Boolean overallEligible,
                                  boolean mismatchedMarks, List<AssessmentInput> assessments) { }

    public CourseResult saveOrUpdate(CourseResult result) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CourseResult saved = saveOrUpdate(connection, result);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                throw error;
            }
        }
    }

    /** Draft-only upsert. The caller owns the transaction. Null grades clear any previous grade. */
    public CourseResult saveOrUpdate(Connection connection, CourseResult result) throws SQLException {
        String sql = """
                INSERT INTO course_results
                    (enrollment_id, ca_mark, final_exam_mark, overall_mark, grade_rule_id,
                     grade_letter, grade_point, result_status, published, calculated_at,
                     published_at, calculated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP, NULL, ?)
                ON DUPLICATE KEY UPDATE
                    ca_mark = VALUES(ca_mark), final_exam_mark = VALUES(final_exam_mark),
                    overall_mark = VALUES(overall_mark), grade_rule_id = VALUES(grade_rule_id),
                    grade_letter = VALUES(grade_letter), grade_point = VALUES(grade_point),
                    result_status = VALUES(result_status), published = VALUES(published),
                    calculated_at = CURRENT_TIMESTAMP, published_at = VALUES(published_at),
                    calculated_by = VALUES(calculated_by)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, result.getEnrollmentId());
            statement.setBigDecimal(2, result.getCaMark());
            statement.setBigDecimal(3, result.getFinalExamMark());
            statement.setBigDecimal(4, result.getOverallMark());
            nullableInt(statement, 5, result.getGradeRuleId());
            statement.setString(6, result.getGradeLetter());
            statement.setBigDecimal(7, result.getGradePoint());
            statement.setString(8, result.getResultStatus().name());
            nullableInt(statement, 9, result.getCalculatedBy());
            statement.executeUpdate();
        }
        return getByEnrollmentId(connection, result.getEnrollmentId());
    }

    public CourseResult getByEnrollmentId(int enrollmentId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return getByEnrollmentId(connection, enrollmentId);
        }
    }

    public CourseResult getByEnrollmentId(Connection connection, int enrollmentId) throws SQLException {
        return getByEnrollmentId(connection, enrollmentId, false);
    }

    public CourseResult getByEnrollmentId(Connection connection, int enrollmentId, boolean lock) throws SQLException {
        String sql = "SELECT * FROM course_results WHERE enrollment_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enrollmentId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    /** Locks the selected enrollment/offering together; never trusts an offering supplied by the UI. */
    public boolean lockPublicationContext(Connection connection, int enrollmentId, int offeringId) throws SQLException {
        String sql = """
                SELECT e.status AS enrollment_status, o.status AS offering_status
                FROM enrollments e JOIN course_offerings o ON o.offering_id = e.offering_id
                WHERE e.enrollment_id = ? AND o.offering_id = ? FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enrollmentId);
            statement.setInt(2, offeringId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && !"WITHDRAWN".equals(rows.getString("enrollment_status"))
                        && !"CANCELLED".equals(rows.getString("offering_status"));
            }
        }
    }

    /** Read account status/role under the same transaction as the result mutation. */
    public User lockActiveActor(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT role, is_active FROM users WHERE user_id = ? FOR SHARE")) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean("is_active")) { return null; }
                return new User(userId, null, null, null, rows.getString("role"), true);
            }
        }
    }

    public boolean lockLecturerAssignment(Connection connection, int userId, int offeringId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM lecturer_course_assignments
                WHERE lecturer_id = ? AND offering_id = ? FOR SHARE
                """)) {
            statement.setInt(1, userId);
            statement.setInt(2, offeringId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    /** Persistence primitive; ResultService must authorize and validate in this same transaction. */
    public CourseResult publishDraft(Connection connection, int enrollmentId, int resultId) throws SQLException {
        if (connection.getAutoCommit()) { throw new SQLException("Publication requires a transaction."); }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE course_results SET published = TRUE, published_at = CURRENT_TIMESTAMP
                WHERE enrollment_id = ? AND result_id = ? AND published = FALSE AND published_at IS NULL
                  AND result_status IN ('PASS', 'FAIL') AND overall_mark IS NOT NULL
                  AND grade_rule_id IS NOT NULL AND grade_letter IS NOT NULL AND grade_point IS NOT NULL
                  AND calculated_by IS NOT NULL
                """)) {
            statement.setInt(1, enrollmentId);
            statement.setInt(2, resultId);
            if (statement.executeUpdate() != 1) { throw new SQLException("The draft changed before publication."); }
        }
        return getByEnrollmentId(connection, enrollmentId);
    }

    public List<CourseResult> findByOfferingId(int offeringId) throws SQLException {
        String sql = """
                SELECT r.* FROM course_results r
                JOIN enrollments e ON e.enrollment_id = r.enrollment_id
                WHERE e.offering_id = ? ORDER BY e.enrollment_id
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offeringId);
            try (ResultSet rows = statement.executeQuery()) {
                List<CourseResult> results = new ArrayList<>();
                while (rows.next()) { results.add(map(rows)); }
                return results;
            }
        }
    }

    public List<ResultRow> findEnrollmentRowsByOfferingId(int offeringId) throws SQLException {
        String sql = """
                SELECT e.enrollment_id AS selected_enrollment_id, e.attempt_no,
                       s.registration_no, CONCAT(u.first_name, ' ', u.last_name) AS student_name, r.*
                FROM enrollments e
                JOIN students s ON s.student_id = e.student_id
                JOIN users u ON u.user_id = s.student_id
                LEFT JOIN course_results r ON r.enrollment_id = e.enrollment_id
                WHERE e.offering_id = ? ORDER BY s.registration_no, e.attempt_no, e.enrollment_id
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offeringId);
            try (ResultSet rows = statement.executeQuery()) {
                List<ResultRow> results = new ArrayList<>();
                while (rows.next()) {
                    results.add(new ResultRow(rows.getInt("selected_enrollment_id"),
                            rows.getString("registration_no"), rows.getString("student_name"),
                            rows.getInt("attempt_no"), rows.getObject("result_id") == null ? null : map(rows)));
                }
                return results;
            }
        }
    }

    /** Missing eligibility remains null. Saving serializes calculations for this enrollment. */
    public CalculationData loadCalculationData(Connection connection, int enrollmentId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT e.status, r.overall_eligible FROM enrollments e
                LEFT JOIN eligibility_records r ON r.enrollment_id = e.enrollment_id
                WHERE e.enrollment_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        String enrollmentStatus;
        Boolean eligible;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enrollmentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) { return null; }
                enrollmentStatus = rows.getString("status");
                boolean value = rows.getBoolean("overall_eligible");
                eligible = rows.wasNull() ? null : value;
            }
        }
        return new CalculationData(enrollmentId, enrollmentStatus, eligible,
                hasMismatchedMarks(connection, enrollmentId), findAssessmentInputs(connection, enrollmentId));
    }

    private List<AssessmentInput> findAssessmentInputs(Connection connection, int enrollmentId) throws SQLException {
        String sql = """
                SELECT a.assessment_id, a.assessment_type, a.max_mark, a.weight_percentage,
                       m.marks_obtained, m.mark_status
                FROM enrollments e
                JOIN assessments a ON a.offering_id = e.offering_id
                LEFT JOIN marks m ON m.assessment_id = a.assessment_id AND m.enrollment_id = e.enrollment_id
                WHERE e.enrollment_id = ? ORDER BY a.assessment_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enrollmentId);
            try (ResultSet rows = statement.executeQuery()) {
                List<AssessmentInput> inputs = new ArrayList<>();
                while (rows.next()) {
                    inputs.add(new AssessmentInput(rows.getInt("assessment_id"), rows.getString("assessment_type"),
                            rows.getBigDecimal("max_mark"), rows.getBigDecimal("weight_percentage"),
                            rows.getBigDecimal("marks_obtained"), rows.getString("mark_status")));
                }
                return inputs;
            }
        }
    }

    private boolean hasMismatchedMarks(Connection connection, int enrollmentId) throws SQLException {
        String sql = """
                SELECT 1 FROM marks m
                JOIN enrollments e ON e.enrollment_id = m.enrollment_id
                JOIN assessments a ON a.assessment_id = m.assessment_id
                WHERE m.enrollment_id = ? AND a.offering_id <> e.offering_id LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enrollmentId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private CourseResult map(ResultSet row) throws SQLException {
        CourseResult result = new CourseResult();
        result.setResultId(row.getInt("result_id"));
        result.setEnrollmentId(row.getInt("enrollment_id"));
        result.setCaMark(row.getBigDecimal("ca_mark"));
        result.setFinalExamMark(row.getBigDecimal("final_exam_mark"));
        result.setOverallMark(row.getBigDecimal("overall_mark"));
        result.setGradeRuleId((Integer) row.getObject("grade_rule_id"));
        result.setGradeLetter(row.getString("grade_letter"));
        result.setGradePoint(row.getBigDecimal("grade_point"));
        result.setResultStatus(CourseResult.ResultStatus.valueOf(row.getString("result_status")));
        result.setPublished(row.getBoolean("published"));
        Timestamp calculatedAt = row.getTimestamp("calculated_at");
        Timestamp publishedAt = row.getTimestamp("published_at");
        result.setCalculatedAt(calculatedAt == null ? null : calculatedAt.toLocalDateTime());
        result.setPublishedAt(publishedAt == null ? null : publishedAt.toLocalDateTime());
        result.setCalculatedBy((Integer) row.getObject("calculated_by"));
        return result;
    }

    private void nullableInt(PreparedStatement statement, int parameter, Integer value) throws SQLException {
        if (value == null) { statement.setNull(parameter, Types.INTEGER); }
        else { statement.setInt(parameter, value); }
    }
}
