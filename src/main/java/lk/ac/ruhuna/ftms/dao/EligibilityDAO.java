package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
import lk.ac.ruhuna.ftms.model.EligibilityResult;
import lk.ac.ruhuna.ftms.model.EligibilityRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class EligibilityDAO {

    public List<EligibilityOffering> findOfferings() throws SQLException {
        String sql = """
                SELECT o.offering_id, c.course_code, c.course_name, o.offering_group
                FROM course_offerings o
                JOIN courses c ON c.course_id = o.course_id
                ORDER BY c.course_code, o.offering_id
                """;

        List<EligibilityOffering> offerings = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                offerings.add(new EligibilityOffering(
                        rs.getInt("offering_id"), rs.getString("course_code"),
                        rs.getString("course_name"), rs.getString("offering_group")
                ));
            }
        }
        return offerings;
    }

    /** Includes enrollments with no saved result; missing values stay unknown. */
    public List<EligibilityRow> findByOfferingId(int offeringId) throws SQLException {
        String sql = """
                SELECT e.enrollment_id, r.eligibility_id, r.ca_score, r.ca_eligible,
                       r.attendance_percentage, r.attendance_eligible,
                       r.overall_eligible, r.reason
                FROM enrollments e
                LEFT JOIN eligibility_records r ON r.enrollment_id = e.enrollment_id
                WHERE e.offering_id = ?
                ORDER BY e.enrollment_id
                """;

        List<EligibilityRow> rows = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, offeringId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new EligibilityRow(
                            rs.getInt("enrollment_id"),
                            nullableDouble(rs, "ca_score"),
                            nullableBoolean(rs, "ca_eligible"),
                            nullableDouble(rs, "attendance_percentage"),
                            nullableBoolean(rs, "attendance_eligible"),
                            nullableBoolean(rs, "overall_eligible"),
                            rs.getString("reason"),
                            rs.getObject("eligibility_id") != null
                    ));
                }
            }
        }
        return rows;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    public void saveOrUpdateEligibility(
            EligibilityResult result,
            Integer calculatedBy) throws SQLException {

        try (Connection c = DBConnection.getConnection()) { saveOrUpdateEligibility(c, result, calculatedBy); }
    }

    public void saveOrUpdateEligibility(Connection con, EligibilityResult result, Integer calculatedBy) throws SQLException {
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

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, result.getEnrollmentId());
            // The model's normalized CA percentage is stored in ca_score.
            ps.setObject(2, result.getCaPercentage(), Types.DECIMAL);
            ps.setObject(3, result.isCaEligible(), Types.BOOLEAN);
            ps.setObject(4, result.getAttendancePercentage(), Types.DECIMAL);
            ps.setObject(5, result.isAttendanceEligible(), Types.BOOLEAN);
            ps.setObject(6, result.isOverallEligible(), Types.BOOLEAN);
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
                            nullableDouble(rs, "ca_score"),
                            nullableBoolean(rs, "ca_eligible"),
                            nullableDouble(rs, "attendance_percentage"),
                            nullableBoolean(rs, "attendance_eligible"),
                            nullableBoolean(rs, "overall_eligible"),
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
