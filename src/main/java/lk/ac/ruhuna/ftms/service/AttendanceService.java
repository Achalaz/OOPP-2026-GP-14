package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceAccessDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceRecordDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceSessionDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceSummaryDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class AttendanceService {
    private static final Set<String> VALID_STATUSES = Set.of("PRESENT", "ABSENT", "LATE", "EXCUSED", "MEDICAL");
    private final AttendanceSessionDAO sessionDAO = new AttendanceSessionDAO();
    private final AttendanceRecordDAO recordDAO = new AttendanceRecordDAO();
    private final AttendanceSummaryDAO summaryDAO = new AttendanceSummaryDAO();
    private final AttendanceAccessDAO accessDAO = new AttendanceAccessDAO();
    private final SessionManager.SessionIdentity identity;
    private final ConnectionProvider connections;

    @FunctionalInterface
    interface ConnectionProvider { Connection open() throws SQLException; }

    public AttendanceService() {
        this(DBConnection::getConnection);
    }

    // Package-private connection seam for isolated JDBC integration tests.
    AttendanceService(ConnectionProvider connections) {
        this.identity = SessionManager.requireSession();
        this.connections = connections;
    }

    public void requireManagementSession() {
        SessionManager.requireSession(identity);
        var user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"TECHNICAL_OFFICER".equals(user.getRole())) {
            throw new SecurityException("Active Technical Officer access is required for attendance management.");
        }
    }

    @FunctionalInterface
    private interface DepartmentOperation<T> {
        T run(Connection connection, int departmentId) throws SQLException;
    }

    private <T> T inDepartment(DepartmentOperation<T> operation) {
        requireManagementSession();
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                int departmentId = accessDAO.requireOfficerDepartment(connection, identity.userId());
                T value = operation.run(connection, departmentId);
                requireManagementSession();
                connection.commit();
                return value;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Attendance could not be accessed. Please check the database connection and retry.", exception);
        }
    }

    public List<AttendanceOffering> getAuthorizedOfferings() {
        return inDepartment(accessDAO::findOfferings);
    }

    public List<AttendanceEnrollment> getEnrollmentsByOffering(int offeringId) {
        return inDepartment((connection, departmentId) -> {
            accessDAO.requireOffering(connection, departmentId, offeringId);
            return accessDAO.findEnrollments(connection, offeringId);
        });
    }

    public void saveSession(AttendanceSession session) {
        requireManagementSession();
        validateSession(session);
        if (session.getTopic() != null && session.getTopic().length() > 255)
            throw new IllegalArgumentException("The session topic must be 255 characters or fewer.");
        int originalId = session.getSessionId();
        try {
            inDepartment((connection, departmentId) -> {
                accessDAO.requireOffering(connection, departmentId, session.getOfferingId());
                if (session.getSessionId() > 0) {
                    int savedOffering = accessDAO.sessionOffering(connection, session.getSessionId());
                    if (savedOffering != session.getOfferingId())
                        throw new SecurityException("An attendance session cannot be moved to another course offering.");
                    // This workflow does not assign the lecturer who conducted the session.
                    session.setConductedBy(sessionDAO.findById(connection, session.getSessionId()).getConductedBy());
                    sessionDAO.update(connection, session);
                } else {
                    session.setConductedBy(null);
                    sessionDAO.save(connection, session);
                }
                return null;
            });
        } catch (RuntimeException exception) {
            session.setSessionId(originalId);
            throw exception;
        }
    }

    public List<AttendanceSession> getSessionsByOffering(int offeringId) {
        return inDepartment((connection, departmentId) -> {
            accessDAO.requireOffering(connection, departmentId, offeringId);
            return sessionDAO.findByOfferingId(connection, offeringId);
        });
    }

    public void deleteSession(int sessionId) {
        // No deletion permission is established by this integration.
        throw new SecurityException("Attendance session deletion is not enabled.");
    }

    public void saveAttendance(AttendanceRecord record) {
        requireManagementSession();
        validateAttendanceRecord(record);
        record.setMarkedBy(identity.userId());
        inDepartment((connection, departmentId) -> {
            int offering = accessDAO.sessionOffering(connection, record.getSessionId());
            accessDAO.requireOffering(connection, departmentId, offering);
            if (accessDAO.enrollmentOffering(connection, record.getEnrollmentId()) != offering)
                throw new SecurityException("The student enrollment does not belong to this attendance session's course offering.");
            recordDAO.saveOrUpdate(connection, record);
            return null;
        });
    }

    public List<AttendanceRecord> getAttendanceBySession(int sessionId) {
        return inDepartment((connection, departmentId) -> {
            accessDAO.requireOffering(connection, departmentId, accessDAO.sessionOffering(connection, sessionId));
            return recordDAO.findBySessionId(connection, sessionId);
        });
    }

    public List<AttendanceRecord> getAttendanceByEnrollment(int enrollmentId) {
        return inDepartment((connection, departmentId) -> {
            accessDAO.requireOffering(connection, departmentId, accessDAO.enrollmentOffering(connection, enrollmentId));
            return recordDAO.findByEnrollmentId(connection, enrollmentId);
        });
    }

    public List<AttendanceSummary> getSummariesByOffering(int offeringId) {
        SessionManager.requireSession(identity);
        var user = SessionManager.getCurrentUser();
        if (user != null && "LECTURER".equals(user.getRole())) {
            // Preserve the existing assigned-lecturer read-only summary workflow.
            try {
                var access = new LecturerAccessService();
                access.requireOffering(offeringId);
                var rows = summaryDAO.findByOfferingId(offeringId);
                access.requireOffering(offeringId);
                SessionManager.requireSession(identity);
                return rows;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load attendance summaries.", exception);
            }
        }
        return inDepartment((connection, departmentId) -> {
            accessDAO.requireOffering(connection, departmentId, offeringId);
            return summaryDAO.findByOfferingId(connection, offeringId);
        });
    }

    public AttendanceSummary getSummaryByEnrollment(int enrollmentId) {
        return inDepartment((connection, departmentId) -> {
            accessDAO.requireOffering(connection, departmentId, accessDAO.enrollmentOffering(connection, enrollmentId));
            return summaryDAO.findByEnrollmentId(connection, enrollmentId);
        });
    }

    // Existing attendance percentage calculation; only authorization/transaction handling is added.
    public double calculateAttendancePercentage(int enrollmentId) {
        return inDepartment((connection, departmentId) -> {
            accessDAO.requireOffering(connection, departmentId, accessDAO.enrollmentOffering(connection, enrollmentId));
            int totalSessions = sessionDAO.countSessionsByEnrollment(connection, enrollmentId);
            if (totalSessions == 0) return 0.0;
            int presentSessions = recordDAO.countPresentByEnrollment(connection, enrollmentId);
            double percentage = ((double) presentSessions / totalSessions) * 100.0;
            return Math.round(percentage * 100.0) / 100.0;
        });
    }

    private void validateSession(AttendanceSession session) {

        if (session == null) {
            throw new IllegalArgumentException(
                    "Attendance session cannot be null.");
        }

        if (session.getOfferingId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid course offering.");
        }

        if (session.getSessionDate() == null) {
            throw new IllegalArgumentException(
                    "Session date is required.");
        }

        if (session.getStartTime() == null) {
            throw new IllegalArgumentException(
                    "Start time is required.");
        }

        if (session.getEndTime() == null) {
            throw new IllegalArgumentException(
                    "End time is required.");
        }

        if (!session.getEndTime().isAfter(session.getStartTime())) {
            throw new IllegalArgumentException(
                    "End time must be after start time.");
        }

        if (session.getSessionType() == null
                || !Set.of("THEORY", "PRACTICAL", "TUTORIAL", "OTHER").contains(session.getSessionType())) {
            throw new IllegalArgumentException(
                    "Choose a valid session type.");
        }
    }

    private void validateAttendanceRecord(
            AttendanceRecord record) {

        if (record == null) {
            throw new IllegalArgumentException(
                    "Attendance record cannot be null.");
        }

        if (record.getSessionId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid session ID.");
        }

        if (record.getEnrollmentId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid enrollment ID.");
        }

        if (record.getAttendanceStatus() == null
                || !VALID_STATUSES.contains(
                record.getAttendanceStatus().toUpperCase(java.util.Locale.ROOT))) {

            throw new IllegalArgumentException(
                    "Invalid attendance status.");
        }

        record.setAttendanceStatus(
                record.getAttendanceStatus().toUpperCase(java.util.Locale.ROOT)
        );
    }
}
