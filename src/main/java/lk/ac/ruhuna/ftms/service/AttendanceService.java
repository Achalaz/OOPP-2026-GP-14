
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceAccessDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceRecordDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceSessionDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceSummaryDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AttendanceService {

    public static final int MAX_SESSIONS_PER_TYPE = 15;

    private static final Set<String> VALID_STATUSES =
            Set.of("PRESENT", "ABSENT", "LATE",
                    "EXCUSED", "MEDICAL");

    private final AttendanceSessionDAO sessionDAO =
            new AttendanceSessionDAO();

    private final AttendanceRecordDAO recordDAO =
            new AttendanceRecordDAO();

    private final AttendanceSummaryDAO summaryDAO =
            new AttendanceSummaryDAO();

    private final AttendanceAccessDAO accessDAO =
            new AttendanceAccessDAO();

    private final SessionManager.SessionIdentity identity;
    private final ConnectionProvider connections;

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    public AttendanceService() {
        this(DBConnection::getConnection);
    }

    AttendanceService(ConnectionProvider connections) {
        this.identity = SessionManager.requireSession();
        this.connections = connections;
    }

    // TECHNICAL OFFICER AUTHORIZATION
    public void requireManagementSession() {

        SessionManager.requireSession(identity);

        var user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole())) {

            throw new SecurityException(
                    "Active Technical Officer access "
                            + "is required for attendance management."
            );
        }
    }

    // STUDENT ATTENDANCE
    public record StudentSummary(
            lk.ac.ruhuna.ftms.dao.EnrollmentDAO.StudentCourse course,
            AttendanceSummary attendance) {
    }

    public record StudentSession(
            String courseCode,
            String courseName,
            String semester,
            int attempt,
            LocalDate date,
            String type,
            String topic,
            String status) {
    }

    public record StudentAttendance(
            List<StudentSummary> summaries,
            List<StudentSession> sessions) {
    }

    public StudentAttendance getMyAttendance()
            throws SQLException {

        return new StudentAccessService(identity)
                .read((connection, studentId) -> {

                    var summaries =
                            new ArrayList<StudentSummary>();

                    for (var course :
                            new lk.ac.ruhuna.ftms.dao.EnrollmentDAO()
                                    .findByStudent(
                                            connection, studentId)) {

                        summaries.add(
                                new StudentSummary(
                                        course,
                                        summaryDAO.findByEnrollmentId(
                                                connection,
                                                course.enrollmentId()
                                        )
                                )
                        );
                    }

                    return new StudentAttendance(
                            List.copyOf(summaries),
                            recordDAO.findStudentSessions(
                                    connection, studentId
                            )
                    );
                });
    }

    // DEPARTMENT-SCOPED DATABASE TRANSACTIONS
    @FunctionalInterface
    private interface DepartmentOperation<T> {

        T run(
                Connection connection,
                int departmentId
        ) throws SQLException;
    }

    private <T> T inDepartment(
            DepartmentOperation<T> operation) {

        requireManagementSession();

        try (Connection connection = connections.open()) {

            connection.setAutoCommit(false);

            try {

                int departmentId =
                        accessDAO.requireOfficerDepartment(
                                connection,
                                identity.userId()
                        );

                T result = operation.run(
                        connection, departmentId
                );

                requireManagementSession();

                connection.commit();

                return result;

            } catch (SQLException | RuntimeException error) {

                try {
                    connection.rollback();

                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }

                throw error;
            }

        } catch (SQLException error) {

            throw new IllegalStateException(
                    "Attendance could not be accessed. "
                            + "Check the database connection.",
                    error
            );
        }
    }

    // AUTHORIZED COURSE OFFERINGS
    public List<AttendanceOffering> getAuthorizedOfferings() {

        return inDepartment(accessDAO::findOfferings);
    }

    public List<AttendanceEnrollment> getEnrollmentsByOffering(
            int offeringId) {

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection, departmentId, offeringId
            );

            return accessDAO.findEnrollments(
                    connection, offeringId
            );
        });
    }

    // STEP 19 - SAVE SESSION WITH MAXIMUM LIMIT
    public void saveSession(AttendanceSession session) {

        requireManagementSession();
        validateSession(session);

        if (session.getTopic() != null
                && session.getTopic().length() > 255) {

            throw new IllegalArgumentException(
                    "Session topic must be 255 characters or fewer."
            );
        }

        int originalId = session.getSessionId();

        try {

            inDepartment((connection, departmentId) -> {

                // Lock the authorized course offering
                lockAuthorizedOffering(
                        connection,
                        departmentId,
                        session.getOfferingId()
                );

                // Maximum 15 Theory and 15 Practical
                requireSessionSlot(connection, session);

                if (session.getSessionId() > 0) {

                    int savedOffering =
                            accessDAO.sessionOffering(
                                    connection,
                                    session.getSessionId()
                            );

                    if (savedOffering
                            != session.getOfferingId()) {

                        throw new SecurityException(
                                "Attendance session cannot "
                                        + "be moved to another offering."
                        );
                    }

                    AttendanceSession existing =
                            sessionDAO.findById(
                                    connection,
                                    session.getSessionId()
                            );

                    session.setConductedBy(
                            existing.getConductedBy()
                    );

                    sessionDAO.update(connection, session);

                } else {

                    session.setConductedBy(null);

                    sessionDAO.save(connection, session);
                }

                return null;
            });

        } catch (RuntimeException error) {

            session.setSessionId(originalId);
            throw error;
        }
    }

    // STEP 19 - SESSION PROGRESS
    public record SessionProgress(
            int theory,
            int practical) {

        public boolean isComplete() {

            return theory == MAX_SESSIONS_PER_TYPE
                    && practical == MAX_SESSIONS_PER_TYPE;
        }
    }

    public SessionProgress getSessionProgress(
            int offeringId) {

        if (offeringId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid course offering ID."
            );
        }

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection, departmentId, offeringId
            );

            String sql = """
                    SELECT
                        COALESCE(
                            SUM(CASE
                                WHEN session_type = 'THEORY'
                                THEN 1 ELSE 0 END), 0
                        ) AS theory_count,

                        COALESCE(
                            SUM(CASE
                                WHEN session_type = 'PRACTICAL'
                                THEN 1 ELSE 0 END), 0
                        ) AS practical_count

                    FROM attendance_sessions
                    WHERE offering_id = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setInt(1, offeringId);

                try (ResultSet results =
                             statement.executeQuery()) {

                    results.next();

                    return new SessionProgress(
                            results.getInt("theory_count"),
                            results.getInt("practical_count")
                    );
                }
            }
        });
    }

    // LOCK COURSE OFFERING FOR SAFE SESSION CREATION
    private static void lockAuthorizedOffering(
            Connection connection,
            int departmentId,
            int offeringId) throws SQLException {

        String sql = """
                SELECT o.offering_id
                FROM course_offerings o
                JOIN courses c
                    ON c.course_id = o.course_id
                WHERE o.offering_id = ?
                  AND c.department_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, offeringId);
            statement.setInt(2, departmentId);

            try (ResultSet results =
                         statement.executeQuery()) {

                if (!results.next()) {

                    throw new SecurityException(
                            "This course offering is outside "
                                    + "your authorized department."
                    );
                }
            }
        }
    }

    // CHECK THE 15-SESSION LIMIT
    private static void requireSessionSlot(
            Connection connection,
            AttendanceSession session) throws SQLException {

        String type = session.getSessionType();

        if (!"THEORY".equals(type)
                && !"PRACTICAL".equals(type)) {
            return;
        }

        String sql = """
                SELECT COUNT(*)
                FROM attendance_sessions
                WHERE offering_id = ?
                  AND session_type = ?
                  AND session_id <> ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1, session.getOfferingId()
            );

            statement.setString(2, type);

            // Exclude the current session when editing
            statement.setInt(
                    3, session.getSessionId()
            );

            try (ResultSet results =
                         statement.executeQuery()) {

                results.next();

                if (results.getInt(1)
                        >= MAX_SESSIONS_PER_TYPE) {

                    throw new IllegalArgumentException(
                            "Maximum " + MAX_SESSIONS_PER_TYPE
                                    + " " + type
                                    + " sessions allowed per course offering."
                    );
                }
            }
        }
    }

    // VIEW COURSE SESSIONS
    public List<AttendanceSession> getSessionsByOffering(
            int offeringId) {

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection, departmentId, offeringId
            );

            return sessionDAO.findByOfferingId(
                    connection, offeringId
            );
        });
    }

    // SESSION DELETION IS NOT ENABLED
    public void deleteSession(int sessionId) {

        throw new SecurityException(
                "Attendance session deletion is not enabled."
        );
    }

    // SAVE OR UPDATE STUDENT ATTENDANCE
    public void saveAttendance(AttendanceRecord record) {

        requireManagementSession();
        validateAttendanceRecord(record);

        record.setMarkedBy(identity.userId());

        inDepartment((connection, departmentId) -> {

            int offering = accessDAO.sessionOffering(
                    connection,
                    record.getSessionId()
            );

            accessDAO.requireOffering(
                    connection, departmentId, offering
            );

            if (accessDAO.enrollmentOffering(
                    connection,
                    record.getEnrollmentId()
            ) != offering) {

                throw new SecurityException(
                        "Student enrollment does not belong "
                                + "to this session."
                );
            }

            recordDAO.saveOrUpdate(connection, record);

            return null;
        });
    }

    // VIEW ATTENDANCE BY SESSION
    public List<AttendanceRecord> getAttendanceBySession(
            int sessionId) {

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection,
                    departmentId,
                    accessDAO.sessionOffering(
                            connection, sessionId
                    )
            );

            return recordDAO.findBySessionId(
                    connection, sessionId
            );
        });
    }

    // VIEW ATTENDANCE BY ENROLLMENT
    public List<AttendanceRecord> getAttendanceByEnrollment(
            int enrollmentId) {

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection,
                    departmentId,
                    accessDAO.enrollmentOffering(
                            connection, enrollmentId
                    )
            );

            return recordDAO.findByEnrollmentId(
                    connection, enrollmentId
            );
        });
    }

    // LECTURER / TECHNICAL OFFICER BATCH SUMMARY
    public List<AttendanceSummary> getSummariesByOffering(
            int offeringId) {

        SessionManager.requireSession(identity);

        var user = SessionManager.getCurrentUser();

        if (user != null
                && "LECTURER".equals(user.getRole())) {

            try {

                var access = new LecturerAccessService();

                access.requireOffering(offeringId);

                var rows = summaryDAO.findByOfferingId(
                        offeringId
                );

                access.requireOffering(offeringId);

                SessionManager.requireSession(identity);

                return rows;

            } catch (SQLException error) {

                throw new IllegalStateException(
                        "Failed to load attendance summaries.",
                        error
                );
            }
        }

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection, departmentId, offeringId
            );

            return summaryDAO.findByOfferingId(
                    connection, offeringId
            );
        });
    }

    // INDIVIDUAL ATTENDANCE SUMMARY
    public AttendanceSummary getSummaryByEnrollment(
            int enrollmentId) {

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection,
                    departmentId,
                    accessDAO.enrollmentOffering(
                            connection, enrollmentId
                    )
            );

            return summaryDAO.findByEnrollmentId(
                    connection, enrollmentId
            );
        });
    }

    // ATTENDANCE PERCENTAGE
    public double calculateAttendancePercentage(
            int enrollmentId) {

        return inDepartment((connection, departmentId) -> {

            accessDAO.requireOffering(
                    connection,
                    departmentId,
                    accessDAO.enrollmentOffering(
                            connection, enrollmentId
                    )
            );

            int totalSessions =
                    sessionDAO.countSessionsByEnrollment(
                            connection, enrollmentId
                    );

            if (totalSessions == 0) {
                return 0.0;
            }

            int presentSessions =
                    recordDAO.countPresentByEnrollment(
                            connection, enrollmentId
                    );

            double percentage =
                    ((double) presentSessions / totalSessions)
                            * 100.0;

            return Math.round(
                    percentage * 100.0
            ) / 100.0;
        });
    }

    // SESSION VALIDATION
    private void validateSession(
            AttendanceSession session) {

        if (session == null) {
            throw new IllegalArgumentException(
                    "Attendance session cannot be null."
            );
        }

        if (session.getOfferingId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid course offering."
            );
        }

        if (session.getSessionDate() == null) {
            throw new IllegalArgumentException(
                    "Session date is required."
            );
        }

        if (session.getStartTime() == null) {
            throw new IllegalArgumentException(
                    "Start time is required."
            );
        }

        if (session.getEndTime() == null) {
            throw new IllegalArgumentException(
                    "End time is required."
            );
        }

        if (!session.getEndTime()
                .isAfter(session.getStartTime())) {

            throw new IllegalArgumentException(
                    "End time must be after start time."
            );
        }

        if (session.getSessionType() == null
                || !Set.of(
                "THEORY",
                "PRACTICAL",
                "TUTORIAL",
                "OTHER"
        ).contains(session.getSessionType())) {

            throw new IllegalArgumentException(
                    "Choose a valid session type."
            );
        }

        // THEORY and PRACTICAL must be exactly 2 hours
        if (("THEORY".equals(session.getSessionType())
                || "PRACTICAL".equals(session.getSessionType()))
                && !Duration.between(
                session.getStartTime(),
                session.getEndTime()
        ).equals(Duration.ofHours(2))) {

            throw new IllegalArgumentException(
                    "Theory and Practical sessions "
                            + "must be exactly 2 hours."
            );
        }
    }

    // ATTENDANCE RECORD VALIDATION
    private void validateAttendanceRecord(
            AttendanceRecord record) {

        if (record == null) {
            throw new IllegalArgumentException(
                    "Attendance record cannot be null."
            );
        }

        if (record.getSessionId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid session ID."
            );
        }

        if (record.getEnrollmentId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid enrollment ID."
            );
        }

        if (record.getAttendanceStatus() == null
                || !VALID_STATUSES.contains(
                record.getAttendanceStatus()
                        .toUpperCase(Locale.ROOT)
        )) {

            throw new IllegalArgumentException(
                    "Invalid attendance status."
            );
        }

        record.setAttendanceStatus(
                record.getAttendanceStatus()
                        .toUpperCase(Locale.ROOT)
        );
    }
}
