package lk.ac.ruhuna.ftms.model;

public class Mark {

    private int markId;
    private int enrollmentId;
    private int assessmentId;
    private Double marksObtained;
    private int attemptNo;

    public Mark() {
    }

    public Mark(int enrollmentId, int assessmentId,
                Double marksObtained, int attemptNo) {
        this.enrollmentId = enrollmentId;
        this.assessmentId = assessmentId;
        this.marksObtained = marksObtained;
        this.attemptNo = attemptNo;
    }

    public Mark(int markId, int enrollmentId, int assessmentId,
                Double marksObtained, int attemptNo) {
        this.markId = markId;
        this.enrollmentId = enrollmentId;
        this.assessmentId = assessmentId;
        this.marksObtained = marksObtained;
        this.attemptNo = attemptNo;
    }

    public int getMarkId() {
        return markId;
    }

    public void setMarkId(int markId) {
        this.markId = markId;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public int getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(int assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Double getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(Double marksObtained) {
        this.marksObtained = marksObtained;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(int attemptNo) {
        this.attemptNo = attemptNo;
    }
}