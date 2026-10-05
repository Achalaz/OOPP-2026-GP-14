package lk.ac.ruhuna.ftms.model;

import java.time.LocalDateTime;

public class AttendanceRecord {

    private int attendanceRecordId;
    private int sessionId;
    private int enrollmentId;
    private String attendanceStatus;
    private Integer markedBy;
    private LocalDateTime markedAt;

    public AttendanceRecord() {
    }

    public AttendanceRecord(int sessionId,
                            int enrollmentId,
                            String attendanceStatus,
                            Integer markedBy) {

        this.sessionId = sessionId;
        this.enrollmentId = enrollmentId;
        this.attendanceStatus = attendanceStatus;
        this.markedBy = markedBy;
    }

    public AttendanceRecord(int attendanceRecordId,
                            int sessionId,
                            int enrollmentId,
                            String attendanceStatus,
                            Integer markedBy,
                            LocalDateTime markedAt) {

        this.attendanceRecordId = attendanceRecordId;
        this.sessionId = sessionId;
        this.enrollmentId = enrollmentId;
        this.attendanceStatus = attendanceStatus;
        this.markedBy = markedBy;
        this.markedAt = markedAt;
    }

    public int getAttendanceRecordId() {
        return attendanceRecordId;
    }

    public void setAttendanceRecordId(int attendanceRecordId) {
        this.attendanceRecordId = attendanceRecordId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public Integer getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(Integer markedBy) {
        this.markedBy = markedBy;
    }

    public LocalDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = markedAt;
    }

//method over write
    @Override
    public String toString() {
        return "Enrollment " + enrollmentId + " - " + attendanceStatus;
    }
}

