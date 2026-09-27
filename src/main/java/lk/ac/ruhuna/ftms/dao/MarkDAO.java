package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Mark;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MarkDAO {

    public void saveOrUpdate(Mark mark) throws SQLException {

        String sql = """
                INSERT INTO marks
                (enrollment_id, assessment_id, marks_obtained, attempt_no)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    marks_obtained = VALUES(marks_obtained)
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, mark.getEnrollmentId());
            ps.setInt(2, mark.getAssessmentId());

            if (mark.getMarksObtained() == null) {
                ps.setNull(3, Types.DECIMAL);
            } else {
                ps.setDouble(3, mark.getMarksObtained());
            }

            ps.setInt(4, mark.getAttemptNo());

            ps.executeUpdate();
        }
    }

    public List<Mark> findAll() throws SQLException {

        List<Mark> marks = new ArrayList<>();

        String sql = """
                SELECT *
                FROM marks
                ORDER BY mark_id
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                Double value =
                        rs.getObject("marks_obtained") == null
                                ? null
                                : rs.getDouble("marks_obtained");

                marks.add(new Mark(
                        rs.getInt("mark_id"),
                        rs.getInt("enrollment_id"),
                        rs.getInt("assessment_id"),
                        value,
                        rs.getInt("attempt_no")
                ));
            }
        }

        return marks;
    }

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