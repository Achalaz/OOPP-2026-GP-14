
package lk.ac.ruhuna.ftms.dao;

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

/**
 * JDBC operations used only inside an authorized
 * Technical Officer transaction.
 */
public final class OfficerMedicalDAO {

    private static final String SCOPE = """
            FROM medicals m
            JOIN students s
                ON s.student_id = m.student_id
            JOIN users u
                ON u.user_id = s.student_id
            WHERE u.role = 'STUDENT'
              AND u.is_active = 1
              AND u.department_id = ?
              AND (
                  m.offering_id IS NULL
                  OR EXISTS (
                      SELECT 1
                      FROM course_offerings o
                      JOIN courses c
                          ON c.course_id = o.course_id
                      WHERE o.offering_id = m.offering_id
                        AND c.department_id = ?
                  )
              )
            """;

    // ==========================================
    // VIEW DEPARTMENT MEDICALS
    // ==========================================

    public List<Medical> findByDepartment(
            Connection connection,
            int departmentId) throws SQLException {

        String sql = "SELECT m.* " + SCOPE
                + " ORDER BY m.submitted_at DESC, "
                + "m.medical_id DESC";

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            ps.setInt(1, departmentId);
            ps.setInt(2, departmentId);

            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    // ==========================================
    // VIEW STUDENT MEDICALS
    // ==========================================

    public List<Medical> findByStudent(
            Connection connection,
            int departmentId,
            int studentId) throws SQLException {

        String sql = "SELECT m.* " + SCOPE
                + " AND m.student_id = ?"
                + " ORDER BY m.submitted_at DESC, "
                + "m.medical_id DESC";

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            ps.setInt(1, departmentId);
            ps.setInt(2, departmentId);
            ps.setInt(3, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    // ==========================================
    // FIND AUTHORIZED MEDICAL
    // ==========================================

    public Medical findOwned(
            Connection connection,
            int departmentId,
            int medicalId,
            boolean forUpdate) throws SQLException {

        String sql = "SELECT m.* " + SCOPE
                + " AND m.medical_id = ? "
                + (forUpdate
                ? "FOR UPDATE"
                : "FOR SHARE");

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            ps.setInt(1, departmentId);
            ps.setInt(2, departmentId);
            ps.setInt(3, medicalId);

            try (ResultSet rs = ps.executeQuery()) {

                return rs.next()
                        ? mapRow(rs)
                        : null;
            }
        }
    }

    // ==========================================
    // CREATE MEDICAL
    // ==========================================

    public int insert(
            Connection connection,
            Medical medical) throws SQLException {

        String sql = """
                INSERT INTO medicals
                    (
                        student_id,
                        offering_id,
                        from_date,
                        to_date,
                        reason,
                        document_path,
                        status
                    )
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                """;

        try (PreparedStatement ps =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            ps.setInt(1, medical.getStudentId());

            setNullableInt(
                    ps,
                    2,
                    medical.getOfferingId()
            );

            ps.setDate(
                    3,
                    Date.valueOf(medical.getFromDate())
            );

            ps.setDate(
                    4,
                    Date.valueOf(medical.getToDate())
            );

            ps.setString(
                    5,
                    medical.getReason()
            );

            ps.setString(
                    6,
                    medical.getDocumentPath()
            );

            if (ps.executeUpdate() != 1) {

                throw new SQLException(
                        "Medical record could not be created."
                );
            }

            try (ResultSet keys =
                         ps.getGeneratedKeys()) {

                if (!keys.next()) {

                    throw new SQLException(
                            "Medical insert did not return an ID."
                    );
                }

                return keys.getInt(1);
            }
        }
    }

    // ==========================================
    // UPDATE PENDING MEDICAL
    // ==========================================

    public boolean updatePending(
            Connection connection,
            Medical medical) throws SQLException {

        String sql = """
                UPDATE medicals
                SET offering_id = ?,
                    from_date = ?,
                    to_date = ?,
                    reason = ?,
                    document_path = ?
                WHERE medical_id = ?
                  AND student_id = ?
                  AND status = 'PENDING'
                """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            setNullableInt(
                    ps,
                    1,
                    medical.getOfferingId()
            );

            ps.setDate(
                    2,
                    Date.valueOf(medical.getFromDate())
            );

            ps.setDate(
                    3,
                    Date.valueOf(medical.getToDate())
            );

            ps.setString(
                    4,
                    medical.getReason()
            );

            ps.setString(
                    5,
                    medical.getDocumentPath()
            );

            ps.setInt(
                    6,
                    medical.getMedicalId()
            );

            ps.setInt(
                    7,
                    medical.getStudentId()
            );

            return ps.executeUpdate() == 1;
        }
    }

    // ==========================================
    // APPROVE OR REJECT MEDICAL
    // ==========================================

    public boolean reviewPending(
            Connection connection,
            int medicalId,
            String status,
            int officerId,
            String note) throws SQLException {

        String sql = """
                UPDATE medicals
                SET status = ?,
                    reviewed_by = ?,
                    reviewed_at = CURRENT_TIMESTAMP,
                    review_note = ?
                WHERE medical_id = ?
                  AND status = 'PENDING'
                """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, officerId);
            ps.setString(3, note);
            ps.setInt(4, medicalId);

            return ps.executeUpdate() == 1;
        }
    }

    // ==========================================
    // DELETE PENDING MEDICAL
    // ==========================================

    public boolean deletePending(
            Connection connection,
            int medicalId) throws SQLException {

        String sql = """
                DELETE FROM medicals
                WHERE medical_id = ?
                  AND status = 'PENDING'
                """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            ps.setInt(1, medicalId);

            return ps.executeUpdate() == 1;
        }
    }

    // ==========================================
    // DATABASE RESULT MAPPING
    // ==========================================

    private static List<Medical> mapAll(
            ResultSet rs) throws SQLException {

        List<Medical> results = new ArrayList<>();

        while (rs.next()) {
            results.add(mapRow(rs));
        }

        return List.copyOf(results);
    }

    private static Medical mapRow(
            ResultSet rs) throws SQLException {

        Medical medical = new Medical();

        medical.setMedicalId(
                rs.getInt("medical_id")
        );

        medical.setStudentId(
                rs.getInt("student_id")
        );

        medical.setOfferingId(
                nullableInt(rs, "offering_id")
        );

        Date from = rs.getDate("from_date");
        Date to = rs.getDate("to_date");

        medical.setFromDate(
                from == null
                        ? null
                        : from.toLocalDate()
        );

        medical.setToDate(
                to == null
                        ? null
                        : to.toLocalDate()
        );

        medical.setReason(
                rs.getString("reason")
        );

        medical.setDocumentPath(
                rs.getString("document_path")
        );

        medical.setStatus(
                rs.getString("status")
        );

        medical.setReviewedBy(
                nullableInt(rs, "reviewed_by")
        );

        medical.setReviewNote(
                rs.getString("review_note")
        );

        Timestamp submitted =
                rs.getTimestamp("submitted_at");

        Timestamp reviewed =
                rs.getTimestamp("reviewed_at");

        medical.setSubmittedAt(
                submitted == null
                        ? null
                        : submitted.toLocalDateTime()
        );

        medical.setReviewedAt(
                reviewed == null
                        ? null
                        : reviewed.toLocalDateTime()
        );

        return medical;
    }

    // ==========================================
    // NULLABLE SQL VALUES
    // ==========================================

    private static Integer nullableInt(
            ResultSet rs,
            String column) throws SQLException {

        int value = rs.getInt(column);

        return rs.wasNull()
                ? null
                : value;
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
}
