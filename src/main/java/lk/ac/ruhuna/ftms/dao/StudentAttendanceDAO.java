
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class StudentAttendanceDAO {

    // Course-wise attendance summary
    public record CourseAttendance(
            int enrollmentId,
            int offeringId,
            String courseCode,
            String courseName,
            int theorySessions,
            int theoryPresent,
            int practicalSessions,
            int practicalPresent,
            int absentCount,
            int lateCount,
            int excusedCount,
            int medicalCount,
            int unmarkedCount) {

        public int getTotalSessions() {
            return theorySessions + practicalSessions;
        }

        public int getTotalPresent() {
            return theoryPresent + practicalPresent;
        }

        public Double getTheoryPercentage() {
            return percentage(
                    theoryPresent, theorySessions
            );
        }

        public Double getPracticalPercentage() {
            return percentage(
                    practicalPresent, practicalSessions
            );
        }

        public Double getCombinedPercentage() {
            return percentage(
                    getTotalPresent(), getTotalSessions()
            );
        }

        private static Double percentage(
                int present, int total) {

            if (total == 0) {
                return null;
            }

            return Math.round(
                    present * 10000.0 / total
            ) / 100.0;
        }
    }

    // Individual session attendance details
    public record SessionAttendance(
            int enrollmentId,
            int sessionId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String sessionType,
            String topic,
            String attendanceStatus) {
    }

    // Get course-wise attendance for one student.
    // Student ID must come from the authenticated session.

    public List<CourseAttendance> findCourseAttendance(
            int studentId) throws SQLException {

        requirePositive(studentId, "Student ID");

        String sql = """
                SELECT
                    e.enrollment_id,
                    e.offering_id,
                    c.course_code,
                    c.course_name,

                    COUNT(DISTINCT CASE
                        WHEN s.session_type = 'THEORY'
                        THEN s.session_id
                    END) AS theory_sessions,

                    COUNT(DISTINCT CASE
                        WHEN s.session_type = 'THEORY'
                         AND ar.attendance_status = 'PRESENT'
                        THEN s.session_id
                    END) AS theory_present,

                    COUNT(DISTINCT CASE
                        WHEN s.session_type = 'PRACTICAL'
                        THEN s.session_id
                    END) AS practical_sessions,

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
                    END) AS unmarked_count

                FROM enrollments e

                JOIN course_offerings o
                    ON o.offering_id = e.offering_id

                JOIN courses c
                    ON c.course_id = o.course_id

                LEFT JOIN attendance_sessions s
                    ON s.offering_id = e.offering_id
                   AND s.session_type IN (
                       'THEORY', 'PRACTICAL'
                   )

                LEFT JOIN attendance_records ar
                    ON ar.session_id = s.session_id
                   AND ar.enrollment_id = e.enrollment_id

                WHERE e.student_id = ?

                GROUP BY
                    e.enrollment_id,
                    e.offering_id,
                    c.course_code,
                    c.course_name

                ORDER BY c.course_code, e.enrollment_id
                """;

        List<CourseAttendance> results =
                new ArrayList<>();

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {

                    results.add(new CourseAttendance(
                            rs.getInt("enrollment_id"),
                            rs.getInt("offering_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getInt("theory_sessions"),
                            rs.getInt("theory_present"),
                            rs.getInt("practical_sessions"),
                            rs.getInt("practical_present"),
                            rs.getInt("absent_count"),
                            rs.getInt("late_count"),
                            rs.getInt("excused_count"),
                            rs.getInt("medical_count"),
                            rs.getInt("unmarked_count")
                    ));
                }
            }
        }

        return results;
    }

    // Get individual attendance records.
    // Enrollment must belong to the student.

    public List<SessionAttendance> findSessionAttendance(
            int studentId,
            int enrollmentId) throws SQLException {

        requirePositive(studentId, "Student ID");
        requirePositive(enrollmentId, "Enrollment ID");

        String sql = """
                SELECT
                    e.enrollment_id,
                    s.session_id,
                    s.session_date,
                    s.start_time,
                    s.end_time,
                    s.session_type,
                    s.topic,

                    COALESCE(
                        ar.attendance_status,
                        'UNMARKED'
                    ) AS attendance_status

                FROM enrollments e

                JOIN attendance_sessions s
                    ON s.offering_id = e.offering_id
                   AND s.session_type IN (
                       'THEORY', 'PRACTICAL'
                   )

                LEFT JOIN attendance_records ar
                    ON ar.session_id = s.session_id
                   AND ar.enrollment_id = e.enrollment_id

                WHERE e.student_id = ?
                  AND e.enrollment_id = ?

                ORDER BY
                    s.session_date DESC,
                    s.start_time DESC,
                    s.session_id DESC
                """;

        List<SessionAttendance> results =
                new ArrayList<>();

        try (Connection connection =
                     DBConnection.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);
            statement.setInt(2, enrollmentId);

            try (ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {

                    Date date = rs.getDate("session_date");
                    Time start = rs.getTime("start_time");
                    Time end = rs.getTime("end_time");

                    results.add(new SessionAttendance(
                            rs.getInt("enrollment_id"),
                            rs.getInt("session_id"),

                            date == null
                                    ? null
                                    : date.toLocalDate(),

                            start == null
                                    ? null
                                    : start.toLocalTime(),

                            end == null
                                    ? null
                                    : end.toLocalTime(),

                            rs.getString("session_type"),
                            rs.getString("topic"),
                            rs.getString("attendance_status")
                    ));
                }
            }
        }

        return results;
    }

    // Validate IDs
    private static void requirePositive(
            int id, String label) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    label + " must be positive."
            );
        }
    }
}
