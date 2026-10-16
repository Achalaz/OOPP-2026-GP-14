
package lk.ac.ruhuna.ftms.model;

import java.time.LocalDate;

public class Medical {

    private int medicalId;
    private int studentId;
    private LocalDate medicalDate;
    private String supportingDocumentPath;
    private String status;
    private Integer recordedBy;

    public Medical() {
    }

    public Medical(
            int studentId,
            LocalDate medicalDate,
            String supportingDocumentPath,
            String status,
            Integer recordedBy) {

        this.studentId = studentId;
        this.medicalDate = medicalDate;
        this.supportingDocumentPath = supportingDocumentPath;
        this.status = status;
        this.recordedBy = recordedBy;
    }

    public Medical(
            int medicalId,
            int studentId,
            LocalDate medicalDate,
            String supportingDocumentPath,
            String status,
            Integer recordedBy) {

        this(
                studentId,
                medicalDate,
                supportingDocumentPath,
                status,
                recordedBy
        );

        this.medicalId = medicalId;
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

    public LocalDate getMedicalDate() {
        return medicalDate;
    }

    public void setMedicalDate(LocalDate medicalDate) {
        this.medicalDate = medicalDate;
    }

    public String getSupportingDocumentPath() {
        return supportingDocumentPath;
    }

    public void setSupportingDocumentPath(
            String supportingDocumentPath) {

        this.supportingDocumentPath = supportingDocumentPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(Integer recordedBy) {
        this.recordedBy = recordedBy;
    }
}
