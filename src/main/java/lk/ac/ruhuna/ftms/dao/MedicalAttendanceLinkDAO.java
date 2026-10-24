package lk.ac.ruhuna.ftms.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MedicalAttendanceLinkDAO {

    public record MedicalPeriod(
            int studentId,
            Integer offeringId,
            LocalDate fromDate,
            LocalDate toDate
    ) {}

    public record AbsenceRow(
            int attendanceRecordId,
            int sessionId,
            int offeringId,
            LocalDate sessionDate,
            String sessionType,
            String attendanceStatus,
            Integer linkedMedicalId
    ) {}

    public MedicalPeriod requireMedical(
            Connection connection,
            int medicalId) throws SQLException {

        String sql = """
                SELECT student_id, offering_id,
                       from_date, to_date
                FROM medicals
                WHERE medical_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, medicalId);

            try (ResultSet rows = statement.executeQuery()) {

                if (!rows.next()) {
                    throw new IllegalArgumentException(
                            "Medical record not found.");
                }

                int offering = rows.getInt("offering_id");

                Integer offeringId =
                        rows.wasNull() ? null : offering;

                return new MedicalPeriod(
                        rows.getInt("student_id"),
                        offeringId,
                        rows.getDate("from_date").toLocalDate(),
                        rows.getDate("to_date").toLocalDate()
                );
            }
        }
    }

    public List<AbsenceRow> findCandidates(
            Connection connection,
            int medicalId,
            MedicalPeriod medical,
            int departmentId) throws SQLException {

        String sql = """
                SELECT ar.attendance_record_id,
                       ar.session_id,
                       s.offering_id,
                       s.session_date,
                       s.session_type,
                       ar.attendance_status,
                       aml.medical_id AS linked_medical_id

                FROM attendance_records ar

                JOIN attendance_sessions s
                    ON s.session_id = ar.session_id

                JOIN enrollments e
                    ON e.enrollment_id = ar.enrollment_id
                   AND e.offering_id = s.offering_id

                JOIN course_offerings o
                    ON o.offering_id = s.offering_id

                JOIN courses c
                    ON c.course_id = o.course_id

                LEFT JOIN attendance_medical_links aml
                    ON aml.attendance_record_id =
                       ar.attendance_record_id

                WHERE e.student_id = ?
                  AND c.department_id = ?
                  AND s.session_date BETWEEN ? AND ?
                  AND (? IS NULL OR s.offering_id = ?)
                  AND (
                      ar.attendance_status IN ('ABSENT', 'MEDICAL')
                      OR aml.medical_id = ?
                  )

                ORDER BY s.session_date,
                         s.session_id,
                         ar.attendance_record_id
                """;

        List<AbsenceRow> result = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, medical.studentId());
            statement.setInt(2, departmentId);
            statement.setDate(3, Date.valueOf(medical.fromDate()));
            statement.setDate(4, Date.valueOf(medical.toDate()));

            if (medical.offeringId() == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
                statement.setNull(6, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, medical.offeringId());
                statement.setInt(6, medical.offeringId());
            }

            statement.setInt(7, medicalId);

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    int linked =
                            rows.getInt("linked_medical_id");

                    result.add(new AbsenceRow(
                            rows.getInt("attendance_record_id"),
                            rows.getInt("session_id"),
                            rows.getInt("offering_id"),
                            rows.getDate("session_date").toLocalDate(),
                            rows.getString("session_type"),
                            rows.getString("attendance_status"),
                            rows.wasNull() ? null : linked
                    ));
                }
            }
        }

        return List.copyOf(result);
    }

    public void lockAttendanceRecord(
            Connection connection,
            int attendanceRecordId) throws SQLException {

        String sql = """
                SELECT attendance_record_id
                FROM attendance_records
                WHERE attendance_record_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, attendanceRecordId);

            try (ResultSet rows = statement.executeQuery()) {

                if (!rows.next()) {
                    throw new IllegalArgumentException(
                            "Attendance record not found.");
                }
            }
        }
    }

    public void insertLink(
            Connection connection,
            int medicalId,
            int attendanceRecordId,
            int officerUserId) throws SQLException {

        String sql = """
                INSERT INTO attendance_medical_links
                (medical_id, attendance_record_id, linked_by)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, medicalId);
            statement.setInt(2, attendanceRecordId);
            statement.setInt(3, officerUserId);

            statement.executeUpdate();
        }
    }

    public boolean removeLink(
            Connection connection,
            int medicalId,
            int attendanceRecordId) throws SQLException {

        String sql = """
                DELETE FROM attendance_medical_links
                WHERE medical_id = ?
                  AND attendance_record_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, medicalId);
            statement.setInt(2, attendanceRecordId);

            return statement.executeUpdate() == 1;
        }
    }

    public boolean hasLinks(int medicalId)
            throws SQLException {

        try (
                Connection connection =
                        lk.ac.ruhuna.ftms.database.DBConnection
                                .getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT 1 FROM attendance_medical_links "
                                        + "WHERE medical_id = ? LIMIT 1")
        ) {

            statement.setInt(1, medicalId);

            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
