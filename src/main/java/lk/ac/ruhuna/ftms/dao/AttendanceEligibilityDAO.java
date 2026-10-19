package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AttendanceEligibilityDAO {

    // Holds attendance details for one student enrollment
    public record AttendanceCounts(
            int enrollmentId,
            int theorySessions,
            int practicalSessions,
            int theoryPresent,
            int practicalPresent,
            int absentCount,
            int lateCount,
            int excusedCount,
            int medicalCount,
            int unmarkedCount,
            int unknownStatusCount) {

        // Total Theory and Practical sessions
        public int getTotalSessions() {
            return theorySessions + practicalSessions;
        }

        // Total PRESENT sessions
        public int getTotalPresent() {
            return theoryPresent + practicalPresent;
        }

        // Calculate Theory attendance percentage
        public Double getTheoryPercentage() {
            return percentage(theoryPresent, theorySessions);
        }

        // Calculate Practical attendance percentage
        public Double getPracticalPercentage() {
            return percentage(practicalPresent, practicalSessions);
        }

        // Calculate Combined attendance percentage
        public Double getCombinedPercentage() {
            return percentage(
                    getTotalPresent(),
                    getTotalSessions()
            );
        }

        // Check whether special attendance rules are needed
        public boolean requiresPolicyDecision() {

            int totalMarked = getTotalPresent()
                    + absentCount
                    + lateCount
                    + excusedCount
                    + medicalCount
                    + unmarkedCount
                    + unknownStatusCount;

            return getTotalSessions() == 0
                    || lateCount > 0
                    || excusedCount > 0
                    || medicalCount > 0
                    || unmarkedCount > 0
                    || unknownStatusCount > 0
                    || totalMarked != getTotalSessions();
        }

        // Reusable percentage calculation
        private static Double percentage(int present, int total) {

            if (total == 0) {
                return null;
            }

            double result = (present * 100.0) / total;

            return Math.round(result * 100.0) / 100.0;
        }
    }

    // Get attendance counts for a specific enrollment
    public AttendanceCounts findByEnrollmentId(
            int enrollmentId) throws SQLException {

        if (enrollmentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid enrollment ID."
            );
        }

        String sql = """
                SELECT
                    e.enrollment_id,

                    COUNT(DISTINCT CASE
                        WHEN s.session_type = 'THEORY'
                        THEN s.session_id
                    END) AS theory_sessions,

                    COUNT(DISTINCT CASE
                        WHEN s.session_type = 'PRACTICAL'
                        THEN s.session_id
                    END) AS practical_sessions,

                    COUNT(DISTINCT CASE
                        WHEN s.session_type = 'THEORY'
                         AND ar.attendance_status = 'PRESENT'
                        THEN s.session_id
                    END) AS theory_present,

                    COUNT(DISTINCT CASE
                        WHEN s.session_type = 'PRACTICAL'
                         AND ar.attendance_status = 'PRESENT'
                        THEN s.session_id
                    END) AS practical_present,

                    COUNT(DISTINCT CASE
                        WHEN ar.attendance_status = 'ABSENT'
                        THEN s.session_id
                    END) AS absent_count,

                    COUNT(DISTINCT CASE
                        WHEN ar.attendance_status = 'LATE'
                        THEN s.session_id
                    END) AS late_count,

                    COUNT(DISTINCT CASE
                        WHEN ar.attendance_status = 'EXCUSED'
                        THEN s.session_id
                    END) AS excused_count,

                    COUNT(DISTINCT CASE
                        WHEN ar.attendance_status = 'MEDICAL'
                        THEN s.session_id
                    END) AS medical_count,

                    COUNT(DISTINCT CASE
                        WHEN s.session_id IS NOT NULL
                         AND ar.attendance_record_id IS NULL
                        THEN s.session_id
                    END) AS unmarked_count,

                    COUNT(DISTINCT CASE
                        WHEN ar.attendance_status IS NOT NULL
                         AND ar.attendance_status NOT IN (
                             'PRESENT',
                             'ABSENT',
                             'LATE',
                             'EXCUSED',
                             'MEDICAL'
                         )
                        THEN s.session_id
                    END) AS unknown_status_count

                FROM enrollments e

                LEFT JOIN attendance_sessions s
                    ON s.offering_id = e.offering_id
                   AND s.session_type IN (
                       'THEORY',
                       'PRACTICAL'
                   )

                LEFT JOIN attendance_records ar
                    ON ar.session_id = s.session_id
                   AND ar.enrollment_id = e.enrollment_id

                WHERE e.enrollment_id = ?

                GROUP BY e.enrollment_id
                """;

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, enrollmentId);

            try (ResultSet rs = statement.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                return new AttendanceCounts(
                        rs.getInt("enrollment_id"),
                        rs.getInt("theory_sessions"),
                        rs.getInt("practical_sessions"),
                        rs.getInt("theory_present"),
                        rs.getInt("practical_present"),
                        rs.getInt("absent_count"),
                        rs.getInt("late_count"),
                        rs.getInt("excused_count"),
                        rs.getInt("medical_count"),
                        rs.getInt("unmarked_count"),
                        rs.getInt("unknown_status_count")
                );
            }
        }
    }
}
