
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Medical;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MedicalDAO {

    public void save(Medical medical) throws SQLException {

        String sql = """
                INSERT INTO medicals
                    (student_id, offering_id, from_date, to_date,
                     reason, document_path)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, medical.getStudentId());
            setNullableInt(ps, 2, medical.getOfferingId());
            ps.setDate(3, Date.valueOf(medical.getFromDate()));
            ps.setDate(4, Date.valueOf(medical.getToDate()));
            ps.setString(5, medical.getReason());
            ps.setString(6, medical.getDocumentPath());

            ps.executeUpdate();
            medical.setStatus("PENDING");

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    medical.setMedicalId(keys.getInt(1));
                }
            }
        }
    }

    // Update only pending medical records
    public boolean update(Medical medical) throws SQLException {

        String sql = """
                UPDATE medicals
                SET offering_id = ?,
                    from_date = ?,
                    to_date = ?,
                    reason = ?,
                    document_path = ?
                WHERE medical_id = ?
                  AND status = 'PENDING'
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            setNullableInt(ps, 1, medical.getOfferingId());
            ps.setDate(2, Date.valueOf(medical.getFromDate()));
            ps.setDate(3, Date.valueOf(medical.getToDate()));
            ps.setString(4, medical.getReason());
            ps.setString(5, medical.getDocumentPath());
            ps.setInt(6, medical.getMedicalId());

            return ps.executeUpdate() > 0;
        }
    }

    // Approve or reject a medical record
    public boolean updateStatus(
            int medicalId,
            String status,
            int reviewedBy,
            String reviewNote) throws SQLException {

        String normalized = status == null
                ? ""
                : status.trim().toUpperCase(Locale.ROOT);

        if (!normalized.equals("APPROVED")
                && !normalized.equals("REJECTED")) {

            throw new IllegalArgumentException(
                    "Review status must be APPROVED or REJECTED."
            );
        }

        if (reviewedBy <= 0) {
            throw new IllegalArgumentException(
                    "Valid reviewer ID required."
            );
        }

        String sql = """
                UPDATE medicals
                SET status = ?,
                    reviewed_by = ?,
                    reviewed_at = CURRENT_TIMESTAMP,
                    review_note = ?
                WHERE medical_id = ?
                  AND status = 'PENDING'
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, normalized);
            ps.setInt(2, reviewedBy);
            ps.setString(3, reviewNote);
            ps.setInt(4, medicalId);

            return ps.executeUpdate() > 0;
        }
    }

    // Find medical by ID
    public Medical findById(int medicalId) throws SQLException {

        String sql =
                "SELECT * FROM medicals WHERE medical_id = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, medicalId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // Find all medical records for a student
    public List<Medical> findByStudentId(int studentId)
            throws SQLException {

        String sql = """
                SELECT * FROM medicals
                WHERE student_id = ?
                ORDER BY submitted_at DESC, medical_id DESC
                """;

        List<Medical> records = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRow(rs));
                }
            }
        }

        return records;
    }

    // Find all medical records
    public List<Medical> findAll() throws SQLException {

        String sql = """
                SELECT * FROM medicals
                ORDER BY submitted_at DESC, medical_id DESC
                """;

        List<Medical> records = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                records.add(mapRow(rs));
            }
        }

        return records;
    }

    // Delete pending records only
    public boolean delete(int medicalId) throws SQLException {

        String sql = """
                DELETE FROM medicals
                WHERE medical_id = ?
                  AND status = 'PENDING'
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, medicalId);
            return ps.executeUpdate() > 0;
        }
    }

    private static void setNullableInt(
            PreparedStatement ps,
            int index,
            Integer value) throws SQLException {

        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static Integer getNullableInt(
            ResultSet rs,
            String column) throws SQLException {

        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    // Convert database result into Medical object
    private Medical mapRow(ResultSet rs) throws SQLException {

        Medical medical = new Medical();

        medical.setMedicalId(rs.getInt("medical_id"));
        medical.setStudentId(rs.getInt("student_id"));
        medical.setOfferingId(
                getNullableInt(rs, "offering_id")
        );

        medical.setFromDate(
                rs.getDate("from_date").toLocalDate()
        );

        medical.setToDate(
                rs.getDate("to_date").toLocalDate()
        );

        medical.setReason(rs.getString("reason"));
        medical.setDocumentPath(
                rs.getString("document_path")
        );

        medical.setStatus(rs.getString("status"));
        medical.setReviewedBy(
                getNullableInt(rs, "reviewed_by")
        );

        medical.setReviewNote(
                rs.getString("review_note")
        );

        Timestamp submitted =
                rs.getTimestamp("submitted_at");

        medical.setSubmittedAt(
                submitted == null
                        ? null
                        : submitted.toLocalDateTime()
        );

        Timestamp reviewed =
                rs.getTimestamp("reviewed_at");

        medical.setReviewedAt(
                reviewed == null
                        ? null
                        : reviewed.toLocalDateTime()
        );

        return medical;
    }
}
