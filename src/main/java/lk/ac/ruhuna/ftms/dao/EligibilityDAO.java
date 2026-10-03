package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.EligibilityResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class EligibilityDAO {

    public void saveOrUpdateEligibility(
            EligibilityResult result,
            Integer calculatedBy) throws SQLException {

        String sql = """
                INSERT INTO eligibility_records
                (enrollment_id, ca_score, ca_eligible, attendance_percentage,
                 attendance_eligible, overall_eligible, reason, calculated_at,
                 calculated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                ON DUPLICATE KEY UPDATE
                    ca_score = VALUES(ca_score),
                    ca_eligible = VALUES(ca_eligible),
                    attendance_percentage = VALUES(attendance_percentage),
                    attendance_eligible = VALUES(attendance_eligible),
                    overall_eligible = VALUES(overall_eligible),
                    reason = VALUES(reason),
                    calculated_at = CURRENT_TIMESTAMP,
                    calculated_by = VALUES(calculated_by)
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, result.getEnrollmentId());
            // The model's normalized CA percentage is stored in ca_score.
            ps.setDouble(2, result.getCaPercentage());
            ps.setBoolean(3, result.isCaEligible());
            ps.setDouble(4, result.getAttendancePercentage());
            ps.setBoolean(5, result.isAttendanceEligible());
            ps.setBoolean(6, result.isOverallEligible());
            ps.setString(7, result.getReason());

            if (calculatedBy == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, calculatedBy);
            }

            ps.executeUpdate();
        }
    }

    /** Returns the saved result, or null if the enrollment has no saved result. */
    public EligibilityResult getByEnrollmentId(int enrollmentId) throws SQLException {

        String sql = """
                SELECT enrollment_id, ca_score, ca_eligible, attendance_percentage,
                       attendance_eligible, overall_eligible, reason
                FROM eligibility_records
                WHERE enrollment_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, enrollmentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new EligibilityResult(
                            rs.getInt("enrollment_id"),
                            rs.getDouble("ca_score"),
                            rs.getBoolean("ca_eligible"),
                            rs.getDouble("attendance_percentage"),
                            rs.getBoolean("attendance_eligible"),
                            rs.getBoolean("overall_eligible"),
                            rs.getString("reason")
                    );
                }
            }
        }

        return null;
    }

    public void deleteByEnrollmentId(int enrollmentId) throws SQLException {

        String sql = "DELETE FROM eligibility_records WHERE enrollment_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, enrollmentId);
            ps.executeUpdate();
        }
    }
}
