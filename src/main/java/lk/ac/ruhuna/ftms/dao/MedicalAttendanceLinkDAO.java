
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MedicalAttendanceLinkDAO {

    // =========================================
    // MEDICAL PERIOD RECORD
    // =========================================

    public record MedicalPeriod(
            int studentId,
            Integer offeringId,
            LocalDate fromDate,
            LocalDate toDate
    ) {}

    // =========================================
    // ATTENDANCE ABSENCE RECORD
    // =========================================

    public record AbsenceRow(
            int attendanceRecordId,
            int sessionId,
            int offeringId,
            LocalDate sessionDate,
            String sessionType,
            String attendanceStatus,
            Integer linkedMedicalId
    ) {}

    // =========================================
    // FIND AND LOCK MEDICAL RECORD
    // =========================================

    public MedicalPeriod requireMedical(
            Connection connection,
            int medicalId) throws SQLException {

        String sql = """
                SELECT student_id,
                       offering_id,
                       from_date,
                       to_date
                FROM medicals
                WHERE medical_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, medicalId);

            try (ResultSet rows =
                         statement.executeQuery()) {

                if (!rows.next()) {

                    throw new IllegalArgumentException(
                            "Medical record not found."
                    );
                }

                int offering =
                        rows.getInt("offering_id");

                Integer offeringId =
                        rows.wasNull() ? null : offering;

                LocalDate fromDate =
                        rows.getDate("from_date")
                                .toLocalDate();

                LocalDate toDate =
                        rows.getDate("to_date")
                                .toLocalDate();

                return new MedicalPeriod(
                        rows.getInt("student_id"),
                        offeringId,
                        fromDate,
                        toDate
                );
            }
        }
    }

    // =========================================
    // FIND MATCHING ABSENCE RECORDS
    // =========================================

    public List<AbsenceRow> findCandidates(
            Connection connection,
            int medicalId,
            MedicalPeriod medical,
            int departmentId) throws SQLException {

        String sql = """
                SELECT
                    ar.attendance_record_id,
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

                  AND (
                      ? IS NULL
                      OR s.offering_id = ?
                  )

                  AND (
                      ar.attendance_status IN (
                          'ABSENT',
                          'MEDICAL'
                      )
                      OR aml.medical_id = ?
                  )

                ORDER BY
                    s.session_date,
                    s.session_id,
                    ar.attendance_record_id
                """;

        List<AbsenceRow> result =
                new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            // Student ID
            statement.setInt(
                    1,
                    medical.studentId()
            );

            // Technical Officer department
            statement.setInt(
                    2,
                    departmentId
            );

            // Medical start date
            statement.setDate(
                    3,
                    Date.valueOf(medical.fromDate())
            );

            // Medical end date
            statement.setDate(
                    4,
                    Date.valueOf(medical.toDate())
            );

            // Optional offering ID
            if (medical.offeringId() == null) {

                statement.setNull(
                        5,
                        Types.INTEGER
                );

                statement.setNull(
                        6,
                        Types.INTEGER
                );

            } else {

                statement.setInt(
                        5,
                        medical.offeringId()
                );

                statement.setInt(
                        6,
                        medical.offeringId()
                );
            }

            // Current medical ID
            statement.setInt(
                    7,
                    medicalId
            );

            try (ResultSet rows =
                         statement.executeQuery()) {

                while (rows.next()) {

                    // =================================
                    // FIX: CORRECT NULL HANDLING
                    // =================================

                    int linked =
                            rows.getInt("linked_medical_id");

                    // Check NULL immediately after getInt()
                    Integer linkedMedicalId =
                            rows.wasNull()
                                    ? null
                                    : linked;

                    // Read remaining attendance details
                    int attendanceRecordId =
                            rows.getInt("attendance_record_id");

                    int sessionId =
                            rows.getInt("session_id");

                    int offeringId =
                            rows.getInt("offering_id");

                    LocalDate sessionDate =
                            rows.getDate("session_date")
                                    .toLocalDate();

                    String sessionType =
                            rows.getString("session_type");

                    String attendanceStatus =
                            rows.getString("attendance_status");

                    // Add attendance row
                    result.add(
                            new AbsenceRow(
                                    attendanceRecordId,
                                    sessionId,
                                    offeringId,
                                    sessionDate,
                                    sessionType,
                                    attendanceStatus,
                                    linkedMedicalId
                            )
                    );
                }
            }
        }

        return List.copyOf(result);
    }

    // =========================================
    // LOCK ATTENDANCE RECORD
    // =========================================

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

            statement.setInt(
                    1,
                    attendanceRecordId
            );

            try (ResultSet rows =
                         statement.executeQuery()) {

                if (!rows.next()) {

                    throw new IllegalArgumentException(
                            "Attendance record not found."
                    );
                }
            }
        }
    }

    // =========================================
    // LINK MEDICAL TO ATTENDANCE
    // =========================================

    public void insertLink(
            Connection connection,
            int medicalId,
            int attendanceRecordId,
            int officerUserId) throws SQLException {

        String sql = """
                INSERT INTO attendance_medical_links
                (
                    medical_id,
                    attendance_record_id,
                    linked_by
                )
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    medicalId
            );

            statement.setInt(
                    2,
                    attendanceRecordId
            );

            statement.setInt(
                    3,
                    officerUserId
            );

            statement.executeUpdate();
        }
    }

    // =========================================
    // UNLINK MEDICAL FROM ATTENDANCE
    // =========================================

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

            statement.setInt(
                    1,
                    medicalId
            );

            statement.setInt(
                    2,
                    attendanceRecordId
            );

            return statement.executeUpdate() == 1;
        }
    }

    // =========================================
    // CHECK IF MEDICAL HAS ATTENDANCE LINKS
    // =========================================

    public boolean hasLinks(int medicalId)
            throws SQLException {

        String sql = """
                SELECT 1
                FROM attendance_medical_links
                WHERE medical_id = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DBConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    medicalId
            );

            try (ResultSet rows =
                         statement.executeQuery()) {

                return rows.next();
            }
        }
    }
}
