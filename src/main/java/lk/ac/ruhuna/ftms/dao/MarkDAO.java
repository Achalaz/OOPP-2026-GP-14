package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AssessmentMark;
import lk.ac.ruhuna.ftms.model.Mark;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MarkDAO {

    // ---------------------------------------------------------
    // SAVE OR UPDATE MARK
    // ---------------------------------------------------------
    public void saveOrUpdate(Mark mark) throws SQLException {

        String sql = """
                INSERT INTO marks
                (enrollment_id, assessment_id, marks_obtained, mark_status)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    marks_obtained = VALUES(marks_obtained),
                    mark_status = VALUES(mark_status)
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, mark.getEnrollmentId());
            ps.setInt(2, mark.getAssessmentId());

            if (mark.getMarksObtained() == null) {
                ps.setNull(3, Types.DECIMAL);
                ps.setString(4, "PENDING");
            } else {
                ps.setDouble(3, mark.getMarksObtained());
                ps.setString(4, "PRESENT");
            }

            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------
    // GET ALL MARKS
    // ---------------------------------------------------------
    public List<Mark> findAll() throws SQLException {

        List<Mark> marks = new ArrayList<>();

        String sql = """
                SELECT
                    m.mark_id,
                    m.enrollment_id,
                    m.assessment_id,
                    m.marks_obtained,
                    e.attempt_no
                FROM marks m
                JOIN enrollments e
                    ON m.enrollment_id = e.enrollment_id
                ORDER BY m.mark_id
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                Double value =
                        rs.getObject("marks_obtained") == null
                                ? null
                                : rs.getDouble("marks_obtained");

                Mark mark = new Mark(
                        rs.getInt("mark_id"),
                        rs.getInt("enrollment_id"),
                        rs.getInt("assessment_id"),
                        value,
                        rs.getInt("attempt_no")
                );

                marks.add(mark);
            }
        }

        return marks;
    }

    // ---------------------------------------------------------
    // GET CA MARKS FOR ONE ENROLLMENT
    // FINAL_EXAM excluded
    // ---------------------------------------------------------
    public List<AssessmentMark> findCAMarksByEnrollment(
            int enrollmentId) throws SQLException {

        List<AssessmentMark> marks = new ArrayList<>();

        String sql = """
                SELECT
                    m.marks_obtained,
                    a.max_mark,
                    a.weight_percentage
                FROM marks m
                JOIN assessments a
                    ON m.assessment_id = a.assessment_id
                WHERE m.enrollment_id = ?
                  AND a.assessment_type <> 'FINAL_EXAM'
                  AND m.mark_status = 'PRESENT'
                ORDER BY a.assessment_id
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, enrollmentId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    Double value =
                            rs.getObject("marks_obtained") == null
                                    ? null
                                    : rs.getDouble("marks_obtained");

                    AssessmentMark assessmentMark =
                            new AssessmentMark(
                                    value,
                                    rs.getDouble("max_mark"),
                                    rs.getDouble("weight_percentage")
                            );

                    marks.add(assessmentMark);
                }
            }
        }

        return marks;
    }

    // ---------------------------------------------------------
    // DELETE MARK
    // ---------------------------------------------------------
    public void delete(int markId) throws SQLException {

        String sql =
                "DELETE FROM marks WHERE mark_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, markId);

            ps.executeUpdate();
        }
    }
}