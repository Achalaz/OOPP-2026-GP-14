package lk.ac.ruhuna.ftms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AttendanceSession {

    private int sessionId;
    private int offeringId;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String sessionType;
    private String topic;
    private Integer conductedBy;
    private LocalDateTime createdAt;

    public AttendanceSession() {
    }

    public AttendanceSession(
            int offeringId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String sessionType,
            String topic,
            Integer conductedBy) {

        this.offeringId = offeringId;
        this.sessionDate = sessionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sessionType = sessionType;
        this.topic = topic;
        this.conductedBy = conductedBy;
    }

    public AttendanceSession(
            int sessionId,
            int offeringId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String sessionType,
            String topic,
            Integer conductedBy,
            LocalDateTime createdAt) {

        this.sessionId = sessionId;
        this.offeringId = offeringId;
        this.sessionDate = sessionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sessionType = sessionType;
        this.topic = topic;
        this.conductedBy = conductedBy;
        this.createdAt = createdAt;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public int getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(int offeringId) {
        this.offeringId = offeringId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getConductedBy() {
        return conductedBy;
    }

    public void setConductedBy(Integer conductedBy) {
        this.conductedBy = conductedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}