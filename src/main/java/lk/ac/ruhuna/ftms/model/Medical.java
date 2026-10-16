
package lk.ac.ruhuna.ftms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Medical {

    private int medicalId;
    private int studentId;
    private Integer offeringId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;
    private String documentPath;
    private String status;
    private LocalDateTime submittedAt;
    private Integer reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;

    public Medical() {
    }

    public Medical(
            int studentId,
            Integer offeringId,
            LocalDate fromDate,
            LocalDate toDate,
            String reason,
            String documentPath) {

        this.studentId = studentId;
        this.offeringId = offeringId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.reason = reason;
        this.documentPath = documentPath;
        this.status = "PENDING";
    }

    public int getMedicalId() {
        return medicalId;
    }

    public void setMedicalId(int medicalId) {
        this.medicalId = medicalId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public Integer getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(Integer offeringId) {
        this.offeringId = offeringId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Integer getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Integer reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }
}
