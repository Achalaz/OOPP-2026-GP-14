
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceRecordDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceSessionDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceSummaryDAO;
import lk.ac.ruhuna.ftms.model.AttendanceRecord;
import lk.ac.ruhuna.ftms.model.AttendanceSession;
import lk.ac.ruhuna.ftms.model.AttendanceSummary;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class AttendanceService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "PRESENT",
            "ABSENT",
            "LATE",
            "EXCUSED",
            "MEDICAL"
    );

    private final AttendanceSessionDAO sessionDAO;
    private final AttendanceRecordDAO recordDAO;
    private final AttendanceSummaryDAO summaryDAO;

    public AttendanceService() {
        this.sessionDAO = new AttendanceSessionDAO();
        this.recordDAO = new AttendanceRecordDAO();
        this.summaryDAO = new AttendanceSummaryDAO();
    }

    public void saveSession(AttendanceSession session) {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateSession(session);

        try {
            if (session.getSessionId() > 0) {
                sessionDAO.update(session);
            } else {
                sessionDAO.save(session);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save attendance session.", e);
        }
    }

    public List<AttendanceSession> getSessionsByOffering(int offeringId) {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        if (offeringId <= 0) {
            throw new IllegalArgumentException("Invalid offering ID.");
        }

        try {
            return sessionDAO.findByOfferingId(offeringId);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load attendance sessions.", e);
        }
    }

    public void deleteSession(int sessionId) {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        if (sessionId <= 0) {
            throw new IllegalArgumentException("Invalid session ID.");
        }

        try {
            sessionDAO.delete(sessionId);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to delete attendance session.", e);
        }
    }

    public void saveAttendance(AttendanceRecord record) {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateAttendanceRecord(record);

        try {
            recordDAO.saveOrUpdate(record);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to save attendance record.", e);
        }
    }

    public List<AttendanceRecord> getAttendanceBySession(int sessionId) {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        if (sessionId <= 0) {
            throw new IllegalArgumentException("Invalid session ID.");
        }

        try {
            return recordDAO.findBySessionId(sessionId);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load attendance records.", e);
        }
    }

    public List<AttendanceRecord> getAttendanceByEnrollment(
            int enrollmentId) {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        if (enrollmentId <= 0) {
            throw new IllegalArgumentException("Invalid enrollment ID.");
        }

        try {
            return recordDAO.findByEnrollmentId(enrollmentId);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load student attendance.", e);
        }
    }

    // NEW: Get all student attendance summaries
    public List<AttendanceSummary> getSummariesByOffering(
            int offeringId) {

        if (offeringId <= 0) {
            throw new IllegalArgumentException("Invalid offering ID.");
        }

        try {
            var user=lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser();
            LecturerAccessService access=user!=null && "LECTURER".equals(user.getRole()) ? new LecturerAccessService() : null;
            if(access!=null) access.requireOffering(offeringId);
            var rows=summaryDAO.findByOfferingId(offeringId);
            if(access!=null) access.requireOffering(offeringId);
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load attendance summaries.", e);
        }
    }

    // NEW: Get individual student attendance summary
    public AttendanceSummary getSummaryByEnrollment(
            int enrollmentId) {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        if (enrollmentId <= 0) {
            throw new IllegalArgumentException("Invalid enrollment ID.");
        }

        try {
            return summaryDAO.findByEnrollmentId(enrollmentId);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load student attendance summary.", e);
        }
    }

    // Existing attendance percentage calculation
    public double calculateAttendancePercentage(int enrollmentId) {

        if (enrollmentId <= 0) {
            throw new IllegalArgumentException("Invalid enrollment ID.");
        }

        try {
            int totalSessions =
                    sessionDAO.countSessionsByEnrollment(enrollmentId);

            if (totalSessions == 0) {
                return 0.0;
            }

            int presentSessions =
                    recordDAO.countPresentByEnrollment(enrollmentId);

            double percentage =
                    ((double) presentSessions / totalSessions) * 100.0;

            return Math.round(percentage * 100.0) / 100.0;

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to calculate attendance percentage.", e);
        }
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
                || session.getSessionType().isBlank()) {
            throw new IllegalArgumentException(
                    "Session type is required.");
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
                record.getAttendanceStatus().toUpperCase())) {

            throw new IllegalArgumentException(
                    "Invalid attendance status.");
        }

        record.setAttendanceStatus(
                record.getAttendanceStatus().toUpperCase()
        );
    }
}
